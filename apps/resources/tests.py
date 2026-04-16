"""
Unit tests for the diversified / difficulty-aware YouTube client:
  - B1: query variants parametrized by difficulty, deterministic per (user, node)
  - B2: _pick_best_video scoring against beginner/advanced markers
  - B4: cross-node dedup (hard video_id block, soft channel penalty)
  - B6: retry logic on transient errors, fail-fast on quota

All tests stub out the google-api-python-client — no network traffic, no keys.
"""
from unittest.mock import patch

from django.test import SimpleTestCase as TestCase

from . import youtube_client as yt


def _video(vid_id, title, channel, duration=15):
    return {
        'youtube_video_id': vid_id,
        'title': title,
        'youtube_channel': channel,
        'thumbnail_url': '',
        'description': '',
        'url': f'https://www.youtube.com/watch?v={vid_id}',
        'resource_type': 'youtube_video',
        'is_free': True,
        'language': 'en',
        'duration_minutes': duration,
        'view_count': 100000,
    }


class BuildQueryVariantsTests(TestCase):
    def test_beginner_suffixes_for_difficulty_1(self):
        variants = yt._build_query_variants('React Hooks', '', difficulty=1, user_id=1, node_id=1)
        joined = ' '.join(variants).lower()
        # Every variant must start with the node title.
        for v in variants:
            self.assertTrue(v.lower().startswith('react hooks'))
        # Difficulty-1 suffix pool: tutorial / for beginners / crash course / intro / explained simply
        self.assertTrue(
            any(kw in joined for kw in ('tutorial', 'beginner', 'crash course', 'intro', 'explained'))
        )

    def test_advanced_suffixes_for_difficulty_5(self):
        variants = yt._build_query_variants('React Hooks', '', difficulty=5, user_id=1, node_id=1)
        joined = ' '.join(variants).lower()
        self.assertTrue(
            any(kw in joined for kw in ('advanced', 'internals', 'masterclass',
                                         'production', 'performance', 'source code', 'at scale'))
        )
        # Beginner markers should not dominate — no "for beginners" / "intro" at D5.
        self.assertNotIn('for beginners', joined)
        self.assertNotIn('intro', joined)

    def test_deterministic_per_user_node(self):
        a = yt._build_query_variants('Django', '', difficulty=3, user_id=7, node_id=42)
        b = yt._build_query_variants('Django', '', difficulty=3, user_id=7, node_id=42)
        self.assertEqual(a, b)

    def test_different_users_get_different_variants_statistically(self):
        """Different users should mostly see different variant tuples."""
        distinct = set()
        for uid in range(30):
            distinct.add(tuple(
                yt._build_query_variants('Django', '', difficulty=3, user_id=uid, node_id=1)
            ))
        # With a pool of 5 picking 2 → 20 ordered combos possible; 30 users should hit many.
        self.assertGreaterEqual(len(distinct), 3)

    def test_empty_title_still_returns_variant(self):
        variants = yt._build_query_variants('', '', difficulty=2, user_id=1, node_id=1)
        self.assertTrue(len(variants) >= 1)
        self.assertTrue(all(v for v in variants))

    def test_description_appended_when_meaningful(self):
        variants = yt._build_query_variants(
            'Django', 'REST APIs with Django and DRF best practices',
            difficulty=3, user_id=1, node_id=1,
        )
        joined = ' '.join(variants)
        self.assertIn('REST APIs', joined)


class PickBestVideoDifficultyTests(TestCase):
    def test_advanced_node_prefers_advanced_title(self):
        candidates = [
            _video('a', 'React Hooks for Beginners', 'Some Channel'),
            _video('b', 'React Hooks Advanced Patterns', 'Another Channel'),
        ]
        picked = yt._pick_best_video(candidates, topic_keyword='react hooks',
                                     search_query='react hooks advanced',
                                     difficulty=4)
        self.assertEqual(picked['youtube_video_id'], 'b')

    def test_beginner_node_does_not_pick_advanced_title(self):
        candidates = [
            _video('a', 'React Hooks Tutorial for Beginners', 'Channel A'),
            _video('b', 'React Hooks Production Patterns at Scale', 'Channel B'),
        ]
        picked = yt._pick_best_video(candidates, topic_keyword='react hooks',
                                     search_query='react hooks tutorial',
                                     difficulty=1)
        self.assertEqual(picked['youtube_video_id'], 'a')

    def test_hard_block_already_used_video(self):
        candidates = [
            _video('a', 'Django Advanced Patterns', 'Some Channel'),
            _video('b', 'Django Deep Dive', 'Other Channel'),
        ]
        picked = yt._pick_best_video(
            candidates, topic_keyword='django', search_query='django advanced',
            difficulty=4, used_video_ids={'a'},
        )
        self.assertEqual(picked['youtube_video_id'], 'b')

    def test_soft_penalty_reused_channel(self):
        # Both candidates are advanced; Channel A is already used → Channel B should win.
        candidates = [
            _video('a', 'Django Advanced Patterns', 'Channel A'),
            _video('b', 'Django Advanced Patterns', 'Channel B'),
        ]
        picked = yt._pick_best_video(
            candidates, topic_keyword='django', search_query='django advanced',
            difficulty=4, used_channels={'channel a'},
        )
        self.assertEqual(picked['youtube_video_id'], 'b')

    def test_meta_clickbait_titles_penalised(self):
        candidates = [
            _video('a', 'React is Dead in 2026', 'Opinion Channel'),
            _video('b', 'React Hooks Walkthrough', 'Teaching Channel'),
        ]
        picked = yt._pick_best_video(candidates, topic_keyword='react',
                                     search_query='react walkthrough',
                                     difficulty=2)
        self.assertEqual(picked['youtube_video_id'], 'b')

    def test_empty_list_returns_none(self):
        self.assertIsNone(yt._pick_best_video([], 'x', 'x', difficulty=2))

    def test_returns_none_when_all_videos_already_used(self):
        candidates = [_video('a', 'Title', 'Chan')]
        self.assertIsNone(
            yt._pick_best_video(candidates, 'x', 'x', difficulty=2, used_video_ids={'a'})
        )


class TitleRelevanceTests(TestCase):
    def test_level_words_now_participate_in_matching(self):
        # Previously 'beginner'/'tutorial' were stopwords; level words
        # should now be matchable.
        self.assertTrue(
            yt._title_is_relevant(
                'Python Tutorial for Beginners',
                search_query='python tutorial',
                topic_keyword='python',
            )
        )

    def test_title_unrelated_is_rejected(self):
        # 'Cooking' shares no significant keyword with 'Django REST API guide'
        # (3+ char words: django, rest, api, guide — none appear in title).
        self.assertFalse(
            yt._title_is_relevant(
                'Cooking Pasta at Home',
                search_query='django rest api guide',
                topic_keyword='django',
            )
        )


class RetryBackoffTests(TestCase):
    def _make_http_error(self, status):
        class FakeResp:
            def __init__(self, status):
                self.status = status

        class FakeHttpError(Exception):
            def __init__(self, status):
                super().__init__(f'HTTP {status}')
                self.resp = FakeResp(status)

        return FakeHttpError(status)

    def test_retries_on_429_then_succeeds(self):
        calls = {'n': 0}

        def flaky():
            calls['n'] += 1
            if calls['n'] < 3:
                raise self._make_http_error(429)
            return {'ok': True}

        with patch.object(yt.time, 'sleep', return_value=None):
            result = yt._retry_youtube(flaky, attempts=3, label='test')
        self.assertEqual(result, {'ok': True})
        self.assertEqual(calls['n'], 3)

    def test_fails_fast_on_403_quota(self):
        calls = {'n': 0}

        def quota_exhausted():
            calls['n'] += 1
            raise self._make_http_error(403)

        with patch.object(yt.time, 'sleep', return_value=None):
            with self.assertRaises(Exception):
                yt._retry_youtube(quota_exhausted, attempts=3, label='test')
        # Exactly one attempt — no retry on quota/auth errors.
        self.assertEqual(calls['n'], 1)

    def test_non_retriable_4xx_raises_immediately(self):
        calls = {'n': 0}

        def bad_request():
            calls['n'] += 1
            raise self._make_http_error(400)

        with patch.object(yt.time, 'sleep', return_value=None):
            with self.assertRaises(Exception):
                yt._retry_youtube(bad_request, attempts=3, label='test')
        self.assertEqual(calls['n'], 1)

    def test_exhausts_retries_on_persistent_5xx(self):
        calls = {'n': 0}

        def always_503():
            calls['n'] += 1
            raise self._make_http_error(503)

        with patch.object(yt.time, 'sleep', return_value=None):
            with self.assertRaises(Exception):
                yt._retry_youtube(always_503, attempts=3, label='test')
        self.assertEqual(calls['n'], 3)

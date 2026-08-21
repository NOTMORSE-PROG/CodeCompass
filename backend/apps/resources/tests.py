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


def _video(vid_id, title, channel, duration=15, published_year=2024,
           embeddable=True, audio_lang='en'):
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
        'published_year': published_year,
        'embeddable': embeddable,
        'audio_lang': audio_lang,
    }


class BuildQueryVariantsTests(TestCase):
    def test_beginner_suffixes_for_difficulty_1(self):
        variants = yt._build_query_variants('React Hooks', '', difficulty=1, user_id=1, node_id=1)
        joined = ' '.join(variants).lower()
        # Every variant must start with the node title (optionally phrase-quoted).
        for v in variants:
            self.assertTrue(v.lower().lstrip('"').startswith('react hooks'))
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


class TrackAwareRecencyTests(TestCase):
    """G8b refinement: evergreen topics get no age signal; fast-moving get a stronger one."""

    def test_evergreen_old_video_beats_recent_clickbait(self):
        # Classic algorithms lecture from a preferred channel; 2015 but canonical.
        old = _video(
            'old', 'Introduction to Algorithms - Lecture 1',
            'MIT OpenCourseWare', duration=45, published_year=2015,
        )
        new = _video(
            'new', 'Top 10 Algorithms You Need to Know in 1 Day',
            'Clickbait Channel', duration=8, published_year=2024,
        )
        picked = yt._pick_best_video(
            [old, new], topic_keyword='algorithms',
            search_query='algorithms data structures tutorial', difficulty=2,
        )
        self.assertEqual(picked['youtube_video_id'], 'old')

    def test_fast_moving_prefers_recent_even_equal_channels(self):
        # React is fast-moving — a 2016 tutorial loses to a 2024 tutorial even
        # from the same-tier channel.
        old = _video(
            'old', 'React Tutorial for Beginners',
            'Traversy Media', duration=15, published_year=2016,
        )
        new = _video(
            'new', 'React Tutorial for Beginners',
            'Traversy Media', duration=15, published_year=2024,
        )
        picked = yt._pick_best_video(
            [old, new], topic_keyword='react',
            search_query='react tutorial', difficulty=1,
        )
        self.assertEqual(picked['youtube_video_id'], 'new')

    def test_default_bucket_keeps_mild_recency_preference(self):
        # A topic in neither set (e.g. "django") — newer still wins by +1.
        old = _video(
            'old', 'Django Tutorial',
            'Corey Schafer', duration=15, published_year=2018,
        )
        new = _video(
            'new', 'Django Tutorial',
            'Corey Schafer', duration=15, published_year=2024,
        )
        picked = yt._pick_best_video(
            [old, new], topic_keyword='django',
            search_query='django tutorial', difficulty=2,
        )
        self.assertEqual(picked['youtube_video_id'], 'new')

    def test_min_score_floor_returns_none_on_bad_batch(self):
        # All three candidates are clickbait with no offsetting signals.
        bad = [
            _video('a', 'React is Dead - My Opinion',
                   'Random', duration=0, published_year=2024),
            _video('b', 'Vlog: React Tier List Ranked',
                   'Random', duration=0, published_year=2024),
            _video('c', 'Unpopular Opinion: Why I Quit React',
                   'Random', duration=0, published_year=2024),
        ]
        picked = yt._pick_best_video(
            bad, topic_keyword='cooking',  # deliberately off-topic to zero the topic bonus
            search_query='cooking pasta', difficulty=2,
        )
        self.assertIsNone(picked)

    def test_min_score_floor_overridable(self):
        # Operators can relax the floor via YOUTUBE_MIN_SCORE without a deploy;
        # patch the resolved module-level constant to simulate that.
        bad = [
            _video('a', 'React is Dead - My Opinion',
                   'Random', duration=0, published_year=2024),
        ]
        with patch.object(yt, '_MIN_VIDEO_SCORE', -99):
            picked = yt._pick_best_video(
                bad, topic_keyword='cooking',
                search_query='cooking pasta', difficulty=2,
            )
        self.assertIsNotNone(picked)


class ScoreLoggingTests(TestCase):
    def test_pick_emits_info_log_line(self):
        candidates = [_video('a', 'Django Tutorial', 'Corey Schafer')]
        with self.assertLogs('apps.resources.youtube_client', level='INFO') as cm:
            yt._pick_best_video(
                candidates, topic_keyword='django',
                search_query='django tutorial', difficulty=2,
            )
        self.assertTrue(any('picked video=' in msg for msg in cm.output))


class HallucinationGuardTests(TestCase):
    """Root-cause guards against the three reported failure modes:
    topic drift (ChatGPT on web project), non-English/Tagalog content,
    and embed-disabled videos."""

    # ── F1: topic-drift prevention ─────────────────────────────────────────
    def test_F1a_phrase_quoting_multi_word_title(self):
        variants = yt._build_query_variants(
            'Personal Website Project', '', 2, 1, 1,
        )
        self.assertTrue(variants[0].startswith('"Personal Website Project"'))

    def test_F1b_single_word_title_not_quoted(self):
        variants = yt._build_query_variants('Kubernetes', '', 2, 1, 1)
        self.assertTrue(variants[0].startswith('Kubernetes'))
        self.assertFalse(variants[0].startswith('"'))

    def test_F1c_drift_topic_rejected_when_query_unrelated(self):
        self.assertFalse(yt._title_is_relevant(
            'Build a Portfolio with ChatGPT',
            search_query='personal website portfolio tutorial',
            topic_keyword='personal website',
        ))

    def test_F1d_drift_topic_allowed_when_query_mentions_it(self):
        self.assertTrue(yt._title_is_relevant(
            'ChatGPT Clone in Python',
            search_query='chatgpt clone python tutorial',
            topic_keyword='chatgpt clone',
        ))

    def test_F1e_noise_only_match_rejected(self):
        # Only 'project' overlaps, and 'project' is noise now.
        self.assertFalse(yt._title_is_relevant(
            '10 JavaScript Project Ideas',
            search_query='personal website project',
            topic_keyword='personal website',
        ))

    def test_F1f_two_substantive_matches_still_relevant(self):
        self.assertTrue(yt._title_is_relevant(
            'Personal Portfolio Website Tutorial',
            search_query='personal website portfolio',
            topic_keyword='personal website',
        ))

    def test_F1g_single_substantive_word_fallback(self):
        # substantive={kubernetes}; min(2,1)=1; single match suffices.
        self.assertTrue(yt._title_is_relevant(
            'Kubernetes Deep Dive',
            search_query='kubernetes tutorial',
            topic_keyword='kubernetes',
        ))

    # ── F2: language guarantees (English + Tagalog/Filipino accepted) ──────
    def test_F2a_devanagari_detected(self):
        self.assertTrue(yt._title_appears_unsupported('React सीखें Tutorial'))

    def test_F2b_bengali_detected(self):
        self.assertTrue(yt._title_appears_unsupported('Web Dev কোর্স'))

    def test_F2c_cjk_detected(self):
        self.assertTrue(yt._title_appears_unsupported('Learn React チュートリアル'))

    def test_F2d_english_with_punctuation_passes(self):
        self.assertFalse(yt._title_appears_unsupported('React & TypeScript — 2024 Guide'))

    def test_F2e_language_name_phrase_rejected(self):
        self.assertTrue(yt._title_appears_unsupported(
            'Web Development Best Practices (Hindi)'
        ))

    def test_F2f_tagalog_filipino_titles_pass(self):
        self.assertFalse(yt._title_appears_unsupported('Web Dev Tagalog Tutorial'))
        self.assertFalse(yt._title_appears_unsupported('Filipino React Course'))

    def test_F2g_audio_lang_accepted_variants(self):
        for ok in ('en', 'en-US', 'en-GB', 'tl', 'tl-PH', 'fil', ''):
            self.assertTrue(yt._audio_lang_is_accepted(ok), f'expected {ok!r} accepted')
        for bad in ('hi', 'ja', 'es', 'zh-CN'):
            self.assertFalse(yt._audio_lang_is_accepted(bad), f'expected {bad!r} rejected')

    # ── F3: embeddability ──────────────────────────────────────────────────
    def test_F3_non_embeddable_filtered_from_search_results(self):
        """Integration-style: monkey-patch the two YouTube client calls and
        assert the post-enrichment filter drops the non-embeddable video."""
        from django.core.cache import cache as django_cache

        fake_search_response = {
            'items': [
                {
                    'id': {'videoId': 'ok_vid'},
                    'snippet': {
                        'title': 'Django Tutorial',
                        'channelTitle': 'Corey Schafer',
                        'thumbnails': {'medium': {'url': ''}},
                        'description': '',
                        'publishedAt': '2024-01-01T00:00:00Z',
                    },
                },
                {
                    'id': {'videoId': 'bad_vid'},
                    'snippet': {
                        'title': 'Django Deep Dive',
                        'channelTitle': 'Some Channel',
                        'thumbnails': {'medium': {'url': ''}},
                        'description': '',
                        'publishedAt': '2024-01-01T00:00:00Z',
                    },
                },
            ],
        }

        class _FakeRequest:
            def execute(self_inner):
                return fake_search_response

        class _FakeSearch:
            def list(self_inner, **kwargs):
                return _FakeRequest()

        class _FakeClient:
            def search(self_inner):
                return _FakeSearch()

        fake_details = {
            'ok_vid':  {'duration_minutes': 15, 'view_count': 100, 'embeddable': True,  'audio_lang': 'en'},
            'bad_vid': {'duration_minutes': 15, 'view_count': 100, 'embeddable': False, 'audio_lang': 'en'},
        }

        # Flush any stale cache entry for this query
        django_cache.delete_pattern('yt:search:*') if hasattr(django_cache, 'delete_pattern') else None

        with patch.object(yt, '_get_youtube_client', return_value=_FakeClient()), \
             patch.object(yt, '_fetch_video_details', return_value=fake_details), \
             patch.object(yt.settings, 'YOUTUBE_API_KEY', 'test-key'):
            # Use a unique query so it doesn't hit a warm cache from earlier tests.
            videos = yt.search_youtube('pytest-fixture-django-query')

        ids = [v['youtube_video_id'] for v in videos]
        self.assertIn('ok_vid', ids)
        self.assertNotIn('bad_vid', ids)

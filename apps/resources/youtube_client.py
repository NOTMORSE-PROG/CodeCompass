"""
YouTube Data API v3 client.
API key read from Django settings (from .env) — never hardcoded.
Free quota: 10,000 units/day. Search = 100 units. Video details = 1 unit.

Diversity + difficulty awareness (see modular-foraging-cat plan, Part B):
- _build_query_variants — deterministic per (user, node), produces different
  queries for different users + different nodes so the same top-5 isn't
  returned for every learner on the same roadmap.
- difficulty-aware suffix pool — beginner nodes get "for beginners", expert
  nodes get "advanced / internals / masterclass" etc. Previously every node
  searched "{title} tutorial" regardless of level — which is why expert nodes
  kept returning beginner tutorials.
- parameter rotation — search order + videoDuration varied per (user, node).
- cross-node dedup — soft channel penalty, hard video-id block so the same
  channel/video can't fill multiple nodes in one roadmap (MMR-inspired).
- retry with jitter on transient 429/5xx; Django-cached responses (Redis) so
  repeat queries across users don't burn quota.
"""
import hashlib
import logging
import random
import re
import time

from django.conf import settings
from django.core.cache import cache

logger = logging.getLogger(__name__)

_youtube_client = None

# Feature flag for the diversified pipeline. Falls back to legacy behaviour
# (single "{title} tutorial" query, no dedup) when False — so rollout can be
# gated per environment.
_DIVERSIFY_ENABLED = getattr(settings, 'YOUTUBE_DIVERSIFY', True)

# Response cache TTL (24 h) — each cache hit saves 100 quota units.
_CACHE_TTL_SECONDS = 24 * 60 * 60

# ---------------------------------------------------------------------------
# Credible educational channels — research-backed list organized by CCS track
# Each entry is the lowercase channel name as it appears on YouTube.
# Score: +2 if channel matches (preferred), +1 if topic keyword in title.
# Sources: Pesto Tech, DEV Community, Feedspot, KDnuggets, PlacementPreparation
# ---------------------------------------------------------------------------
PREFERRED_CHANNELS = {
    # ── Web Development ──────────────────────────────────────────────────────
    'freecodecamp.org',          # 8.26M subs — nonprofit, full free courses
    'traversy media',            # ~2M subs — longest-running web dev channel
    'fireship',                  # 2M+ — high signal, always current
    'the net ninja',             # 1M+ — clean structured playlists
    'net ninja',                 # alternate display name
    'web dev simplified',        # 1M+ — clear JS/CSS/React deep dives
    'academind',                 # 841K — React, Vue, Angular, Node.js
    'kevin powell',              # CSS authority — best CSS educator on YouTube
    'programming with mosh',     # Clean beginner-friendly full courses

    # ── Data Science / ML / AI ───────────────────────────────────────────────
    'statquest with josh starmer',  # Ex-UNC researcher, math/ML visuals
    '3blue1brown',               # 5M+ — best math intuition channel
    'sentdex',                   # Python, ML, NLP, finance projects
    'krish naik',                # 800K — ML/deep learning, popular in SEA
    'corey schafer',             # 1M+ — Python, Django, Pandas
    'pydata',                    # NumFOCUS official, conference-grade content
    'andrej karpathy',           # Ex-Tesla AI Director, GPT from scratch

    # ── Cybersecurity ────────────────────────────────────────────────────────
    'the cyber mentor',          # TCM Security, ethical hacking, OSCP prep
    'hackersploit',              # 918K — pentesting, Linux security, CTFs
    'john hammond',              # Active CTF competitor, Huntress researcher
    'ippsec',                    # Gold standard for Hack The Box walkthroughs
    'liveoverflow',              # Low-level exploit/reverse engineering
    'networkchuck',              # 5.1M — CCNA-certified, great entry point
    'null byte',                 # Ethical hacking, Kali Linux
    'sans internet stormcast',   # SANS Institute — top infosec org

    # ── Mobile Development ───────────────────────────────────────────────────
    'flutter',                   # Google's official Flutter channel
    'reso coder',                # Flutter/Riverpod, Clean Architecture
    'codewithchris',             # Swift/iOS for beginners
    'let\'s build that app',     # iOS, SwiftUI, UIKit

    # ── Game Development ─────────────────────────────────────────────────────
    'unity',                     # Official Unity Technologies
    'unreal engine',             # Official Epic Games
    'brackeys',                  # Most-subscribed Unity archive (still relevant)
    'gdquest',                   # Official Godot partner, open-source
    'heartbeast',                # Godot, Game Maker — beginner friendly
    'gamefromscratch',           # Multi-engine reviews and tutorials
    'sebastian lague',           # Procedural generation, creative CS

    # ── Backend Development ───────────────────────────────────────────────────
    'amigoscode',                # Spring Boot, Java, microservices, Docker
    'in28minutes - ranga karanam',  # Spring Boot, Spring Cloud, REST APIs
    'telusko',                   # Java, Spring Boot — large beginner base
    'the net ninja',             # Node.js, Express, MongoDB, GraphQL

    # ── DevOps / Cloud ────────────────────────────────────────────────────────
    'techworld with nana',       # 900K — best DevOps from-scratch channel
    'aws',                       # Official Amazon Web Services
    'devops directive',          # Kubernetes, Helm, Terraform, GitOps
    'kodekloud',                 # Kubernetes, CKA, Ansible — cert prep
    'a cloud guru',              # AWS, Azure, GCP certification

    # ── Algorithms / Data Structures / CS Theory ─────────────────────────────
    'mit opencourseware',        # MIT official — university-level rigour
    'abdul bari',                # 70+ algorithm topics, cited in uni syllabi
    'williamfiset',              # Graph theory, network flow, DSA with Java
    'back to back swe',          # LeetCode, FAANG interview DSA
    'neetcode',                  # 500K — patterns-based LeetCode roadmaps
    'gaurav sen',                # Ex-Google, system design + DSA
    'cs50',                      # Harvard CS50 — gold standard free CS course
    'mycodeschool',              # Linked lists, trees — timeless visual DSA
    'cs50 – computer science courses for everyone',  # alt name

    # ── UI/UX Design ─────────────────────────────────────────────────────────
    'figma',                     # Official Figma channel
    'designcourse',              # UI design, UX, CSS — veteran instructor
    'flux academy',              # UX strategy, Figma, product design
    'aj&smart',                  # Google Design Sprint partners

    # ── Networking / Systems / Linux ─────────────────────────────────────────
    'david bombal',              # CCNA/CCNP, GNS3, network automation
    'jeremy\'s it lab',          # Full free CCNA course
    'professor messer',          # CompTIA A+, Network+, Security+ cert prep
    'chris titus tech',          # 602K — Linux, sysadmin, scripting
    'the linux foundation',      # Official Linux Foundation channel
    'cbt nuggets',               # Professional IT certification training

    # ── Filipino / Tagalog educators (PH-focused — aligns with CCS audience) ─
    'sdpt solutions',            # PH — Java/Python/MySQL/mobile in Tagalog
    'tech course ph',            # PH — simplified IT lectures
    'learn computer today',      # PH (Michael Tan) — CS/IT for Filipinos
    'pinoyfreecoder',            # PH — full-stack tutorials in Filipino
    'john carlo franco',         # PH — programming tips in Tagalog

    # ── Deeper DSA / Algorithms ──────────────────────────────────────────────
    'tushar roy - coding made simple',   # DSA, system design, FAANG prep
    'takeuforward',              # Striver's DSA sheet — competitive
    'geeksforgeeks',             # Official GfG — wide coverage

    # ── Systems / low-level / compilers ──────────────────────────────────────
    'tsoding daily',             # live coding, compiler internals, C/Rust
    'jon gjengset',              # Rust advanced — "Crust of Rust"
    'low level learning',        # C, OS internals, security
    'computerphile',             # CS theory — Nottingham academics

    # ── AI / ML / LLM ────────────────────────────────────────────────────────
    'tech with tim',             # Python + AI projects
    'two minute papers',         # AI research summaries
    'yannic kilcher',            # LLM paper deep-dives
    'codebasics',                # data science + ML in Python

    # ── Backend / system design ──────────────────────────────────────────────
    'arjan codes',               # Python clean architecture + patterns
    'hussein nasser',            # backend, DB internals, networking
    'hitesh choudhary',          # backend, JS, Python (South Asia audience)
    'code with john',            # Java, OOP, Spring

    # ── Cloud / DevOps (expand beyond Nana) ──────────────────────────────────
    'anthony sistilli',          # DevOps, cloud, real-world infra
    'marcel dempers (that devops guy)',  # Kubernetes deep dives
    'cbtnuggets',                # alt slug

    # ── Frontend / UI specific ───────────────────────────────────────────────
    'josh tried coding',         # Josh Comeau — frontend, animations
    'theo - t3.gg',              # modern full-stack JS/TS
    'jack herrington',           # React patterns, Next.js, TS
}


def _parse_iso_duration(duration_str: str) -> int:
    """Parse a YouTube ISO 8601 duration string (e.g. 'PT1H30M20S') into total minutes."""
    match = re.match(r'PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?', duration_str or '')
    if not match:
        return 0
    h = int(match.group(1) or 0)
    m = int(match.group(2) or 0)
    s = int(match.group(3) or 0)
    return h * 60 + m + (1 if s >= 30 else 0)


def _fetch_video_details(video_ids: list) -> dict:
    """
    Fetch contentDetails and statistics for a list of video IDs.
    Returns a dict keyed by videoId: {'duration_minutes': int, 'view_count': int}.
    Retried with backoff on transient YouTube errors.
    """
    if not video_ids:
        return {}
    try:
        youtube = _get_youtube_client()
        request = youtube.videos().list(
            part='contentDetails,statistics',
            id=','.join(video_ids),
        )
        response = _retry_youtube(request.execute, label='videos.list')
        result = {}
        for item in response.get('items', []):
            vid = item['id']
            duration_str = item.get('contentDetails', {}).get('duration', '')
            view_count = int(item.get('statistics', {}).get('viewCount', 0) or 0)
            result[vid] = {
                'duration_minutes': _parse_iso_duration(duration_str),
                'view_count': view_count,
            }
        return result
    except Exception as e:
        logger.error('YouTube video details fetch failed: %s', e)
        return {}


# Minimum video length for educational content (filters out intros, clips, YouTube Shorts)
# Research: Udemy avg lecture = 8–15 min; videos < 10 min are typically too shallow for a lesson
MIN_VIDEO_MINUTES = 10

# Titles matching these patterns are opinion/meta/clickbait videos — not actual lessons.
# Score them -3 so they only win if there are literally no other options.
_META_TITLE_PATTERNS = [
    'has changed', 'is dead', 'is dying', 'you should stop', 'stop using',
    'nobody talks about', 'every developer should', 'this will change',
    'changed everything', 'honest opinion', 'my opinion', 'the truth about',
    'why i quit', 'why i left', 'i was wrong', 'unpopular opinion',
    'reaction to', 'responding to', 'what nobody tells you', 'you need to know',
    'tier list', 'ranked', 'vlog', 'q&a', 'ama', 'best of', 'top 10', 'top 5',
    'roadmap 2024', 'roadmap 2025', 'roadmap 2026', 'roadmap 2027',
]

# ---------------------------------------------------------------------------
# Difficulty-aware query construction — fixes the "expert nodes still get
# beginner tutorials" bug. RoadmapNode.difficulty is 1-5; for years every
# search was "{title} tutorial" regardless of level. Now each difficulty tier
# has its own suffix pool, and _pick_best_video scores titles by level match.
# ---------------------------------------------------------------------------
_DIFFICULTY_SUFFIX_POOL = {
    1: ['tutorial', 'for beginners', 'crash course', 'explained simply', 'intro'],
    2: ['tutorial', 'walkthrough', 'guide', 'crash course', 'step by step'],
    3: ['intermediate guide', 'deep dive', 'best practices', 'in depth', 'real world examples'],
    4: ['advanced', 'internals', 'deep dive', 'under the hood', 'architecture', 'patterns'],
    5: ['advanced masterclass', 'internals', 'source code walkthrough',
        'production patterns', 'at scale', 'performance optimization'],
}

# Title substrings that strongly signal beginner content. For intermediate+
# nodes (difficulty >= 3) these incur a score penalty.
_BEGINNER_MARKERS = (
    'beginner', 'beginners', 'intro to', 'introduction to',
    'basics', 'getting started', '101', 'for dummies',
)

# Title substrings that signal advanced / production-grade content. For
# advanced nodes these earn a bonus; for beginner nodes (difficulty <= 2)
# they incur a penalty (so we don't throw expert talks at newbies).
_ADVANCED_MARKERS = (
    'advanced', 'deep dive', 'internals', 'masterclass',
    'in depth', 'under the hood', 'at scale', 'production',
)

# Varied YouTube search parameters — rotating these gives cross-user and
# cross-node diversity without changing the query text.
_ORDER_POOL = ('relevance', 'viewCount', 'rating')
_DURATION_POOL = ('medium', 'long', None)  # avoid 'short' (< 4 min = too shallow)


def _deterministic_rng(user_id, node_id) -> random.Random:
    """
    Stable per-(user, node) PRNG. Same (user, node) always produces the same
    variants so re-running populate doesn't flap. Different users on the same
    node get different variants — the core diversity lever.

    Uses SHA-1 instead of Python's builtin hash() because the latter is salted
    per-process, which would make results non-reproducible across restarts.
    """
    key = f'{user_id or 0}:{node_id or 0}'.encode()
    seed = int.from_bytes(hashlib.sha1(key).digest()[:8], 'big')
    return random.Random(seed)


def _clamp_difficulty(d) -> int:
    try:
        return max(1, min(5, int(d)))
    except (TypeError, ValueError):
        return 2


def _build_query_variants(node_title: str, node_description: str,
                          difficulty: int, user_id, node_id) -> list:
    """
    Build 2-3 search query variants for a node, parametrized by difficulty
    and deterministically chosen per (user, node). Returns at least one
    variant even when node_title is a single word.
    """
    d = _clamp_difficulty(difficulty)
    pool = _DIFFICULTY_SUFFIX_POOL[d]
    rng = _deterministic_rng(user_id, node_id)
    picked = rng.sample(pool, k=min(2, len(pool)))
    base = (node_title or '').strip()
    variants = [f'{base} {suffix}'.strip() for suffix in picked if base]
    if node_description and len(node_description.split()) >= 4:
        variants.append(node_description.strip()[:80])
    if not variants:
        variants = [base or 'programming tutorial']
    return variants


def _rotate(pool, user_id, node_id, salt: int = 0):
    """Pick one element from `pool` deterministically per (user, node, salt)."""
    rng = _deterministic_rng(f'{user_id}-{salt}', node_id)
    return rng.choice(tuple(pool))


# ---------------------------------------------------------------------------
# Resilience — retry transient YouTube errors with exponential backoff + jitter.
# Research (johal.in, 2026): exp backoff + jitter improves API success rate
# 68% → 94% on transient failures. We retry 429 / 5xx, fail fast on quota /
# auth errors (retrying those just wastes budget and pressure).
# ---------------------------------------------------------------------------
_RETRIABLE_HTTP_STATUS = frozenset({429, 500, 502, 503, 504})
_BACKOFF_BASE_SECONDS = 1.0
_BACKOFF_MAX_ATTEMPTS = 3


def _http_status(exc) -> int | None:
    """Best-effort status extraction from google-api-python-client HttpError."""
    status = getattr(getattr(exc, 'resp', None), 'status', None)
    try:
        return int(status) if status is not None else None
    except (TypeError, ValueError):
        return None


def _is_quota_or_auth_error(exc) -> bool:
    """403 from YouTube is either quotaExceeded or keyInvalid — both are fatal."""
    return _http_status(exc) == 403


def _retry_youtube(call, *, attempts: int = _BACKOFF_MAX_ATTEMPTS, label: str = 'search'):
    """
    Execute `call()` with retry on transient failures.
    `call` is a zero-arg callable (a `request.execute` thunk).
    """
    last_exc = None
    for attempt in range(1, attempts + 1):
        try:
            return call()
        except Exception as exc:   # noqa: BLE001 — googleapiclient raises HttpError subclasses
            last_exc = exc
            status = _http_status(exc)
            if _is_quota_or_auth_error(exc):
                logger.warning('[YT] %s quota/key error — aborting (attempt %d): %s', label, attempt, exc)
                raise
            if status is not None and status not in _RETRIABLE_HTTP_STATUS:
                # Non-retriable (4xx other than 429) — surface immediately.
                raise
            if attempt >= attempts:
                break
            delay = _BACKOFF_BASE_SECONDS * (2 ** (attempt - 1))
            delay *= 0.8 + random.random() * 0.4   # ±20% jitter
            logger.info('[YT] %s retry attempt=%d delay=%.2fs status=%s',
                        label, attempt, delay, status)
            time.sleep(delay)
    # Exhausted retries.
    raise last_exc if last_exc else RuntimeError('retry loop exited without error')


def _cache_key(*parts) -> str:
    raw = '|'.join(str(p) for p in parts)
    return 'yt:search:' + hashlib.sha1(raw.encode()).hexdigest()


def _title_is_relevant(title: str, search_query: str, topic_keyword: str) -> bool:
    """
    Check if a video title is topically relevant to the lesson.
    Requires at least one significant keyword from the search query or node title to appear in the title.

    Stopword set intentionally narrow: previously stripped 'tutorial', 'course',
    'beginner(s)', 'guide' too, which discarded the level signals we now use
    for difficulty-aware scoring. Keep only true filler words.
    """
    title_lower = title.lower()
    # Extract meaningful words (3+ chars) from search query and topic keyword
    query_words = set(
        w for w in re.split(r'[\s\-_/]+', (search_query + ' ' + topic_keyword).lower())
        if len(w) >= 3 and w not in {'for', 'the', 'and', 'with', 'how', 'what', 'that', 'this',
                                      'from', 'into', 'using', 'learn', 'full', 'free'}
    )
    return any(w in title_lower for w in query_words)


def _pick_best_video(videos: list, topic_keyword: str, search_query: str = '',
                     difficulty: int = 2, used_video_ids: set | None = None,
                     used_channels: set | None = None) -> dict | None:
    """
    Pick the most credible video from a list by scoring channel + title
    relevance + duration, with difficulty awareness and cross-node dedup.

    difficulty    — 1..5 from RoadmapNode.difficulty; biases scoring toward
                    beginner-marked or advanced-marked titles accordingly.
                    Prior to this change every search ignored difficulty and
                    returned "X Tutorial for Beginners" for every node.

    used_video_ids / used_channels — set of already-picked resources in this
                    roadmap. Same video_id is a hard block; same channel is a
                    soft penalty so one channel can't fill every node (MMR
                    cross-node diversification).
    """
    if not videos:
        return None

    used_video_ids = used_video_ids or set()
    used_channels = {c.lower() for c in (used_channels or set()) if c}
    d = _clamp_difficulty(difficulty)

    # Hard-filter: already used in this roadmap — never return the same video twice.
    videos = [v for v in videos if v['youtube_video_id'] not in used_video_ids]
    if not videos:
        return None

    # Exclude videos confirmed shorter than minimum (duration=0 means unknown → keep)
    eligible = [v for v in videos if not (0 < v.get('duration_minutes', 0) < MIN_VIDEO_MINUTES)]
    candidates = eligible if eligible else videos  # fall back to all if every video is too short

    def score(v):
        s = 0
        title_lower = v['title'].lower()
        channel_lower = v['youtube_channel'].lower()

        # Penalise meta/opinion/clickbait titles — they're not lessons
        if any(pat in title_lower for pat in _META_TITLE_PATTERNS):
            s -= 3

        # Channel bonus ONLY applies when the title is actually about the topic.
        # This prevents a famous channel from promoting an off-topic video over
        # a more relevant video from an unknown channel.
        title_relevant = _title_is_relevant(v['title'], search_query, topic_keyword)
        if channel_lower in PREFERRED_CHANNELS and title_relevant:
            s += 2

        if title_relevant:
            s += 1   # title directly covers the topic

        # Difficulty-aware level matching — the part that fixes
        # "intermediate nodes still suggest basics."
        has_beginner_marker = any(m in title_lower for m in _BEGINNER_MARKERS)
        has_advanced_marker = any(m in title_lower for m in _ADVANCED_MARKERS)
        if d >= 3 and has_beginner_marker:
            s -= 3   # intermediate+ nodes shouldn't show "for beginners"
        if d >= 4 and has_advanced_marker:
            s += 2   # reward advanced framing for hard nodes
        if d <= 2 and has_advanced_marker and not has_beginner_marker:
            s -= 2   # don't drop expert talks on beginner nodes either

        dur = v.get('duration_minutes', 0)
        if MIN_VIDEO_MINUTES <= dur <= 20:
            s += 2   # strongly prefer focused lessons (Udemy avg: 8-15 min)
        elif 0 < dur <= 35:
            s += 1   # acceptable — longer but still reasonable
        if d >= 4 and dur >= 20:
            s += 1   # advanced nodes benefit from longer-form coverage

        # Cross-node diversity: soft penalty for reusing a channel that's
        # already supplying videos elsewhere in this roadmap.
        if channel_lower in used_channels:
            s -= 2
        return s

    return max(candidates, key=score)


def _get_youtube_client():
    """Return a cached YouTube API client (avoids re-fetching discovery doc on every call)."""
    global _youtube_client
    if _youtube_client is None:
        from googleapiclient.discovery import build
        _youtube_client = build('youtube', 'v3', developerKey=settings.YOUTUBE_API_KEY)
    return _youtube_client


def search_youtube(query: str, max_results: int = 5, language: str = 'en',
                   order: str = 'relevance', video_duration: str | None = None) -> list:
    """
    Search YouTube for videos matching the query.
    Returns a list of video dicts with title, videoId, thumbnail, channel.

    order         — 'relevance' | 'viewCount' | 'rating' | 'date' | 'title'
                    Rotated per (user, node) in the caller to diversify results.
    video_duration — 'short' | 'medium' | 'long' | None
                    When set, YouTube filters by duration bucket. 'long' is
                    preferred for advanced nodes (deeper coverage).

    Responses are cached in Django cache (Redis backend) keyed by the full
    parameter tuple, so repeat queries across users cost 0 quota units.
    """
    if not settings.YOUTUBE_API_KEY:
        return []

    ckey = _cache_key('q', query, order, video_duration or '-', language, max_results)
    cached = cache.get(ckey)
    if cached is not None:
        logger.debug('[YT] cache hit query=%r order=%s dur=%s', query, order, video_duration)
        return cached

    try:
        youtube = _get_youtube_client()

        list_kwargs = dict(
            part='snippet',
            q=query,
            type='video',
            maxResults=max_results,
            relevanceLanguage=language,
            order=order,
        )
        if video_duration in ('short', 'medium', 'long'):
            list_kwargs['videoDuration'] = video_duration

        request = youtube.search().list(**list_kwargs)
        response = _retry_youtube(request.execute, label='search.list')

        results = []
        for item in response.get('items', []):
            snippet = item['snippet']
            results.append({
                'youtube_video_id': item['id']['videoId'],
                'title': snippet['title'],
                'youtube_channel': snippet['channelTitle'],
                'thumbnail_url': snippet['thumbnails'].get('medium', {}).get('url', ''),
                'description': snippet['description'][:300],
                'url': f'https://www.youtube.com/watch?v={item["id"]["videoId"]}',
                'resource_type': 'youtube_video',
                'is_free': True,
                'language': language,
            })

        # Enrich results with duration and view count from video details endpoint
        video_ids = [r['youtube_video_id'] for r in results]
        details = _fetch_video_details(video_ids)
        for r in results:
            vid_details = details.get(r['youtube_video_id'], {})
            r['duration_minutes'] = vid_details.get('duration_minutes', 0)
            r['view_count'] = vid_details.get('view_count', 0)

        try:
            cache.set(ckey, results, _CACHE_TTL_SECONDS)
        except Exception as cache_err:
            logger.debug('[YT] cache set failed (non-fatal): %s', cache_err)

        return results
    except Exception as e:
        logger.error('YouTube search failed for query %r: %s', query, e)
        return []


def _video_passes_quality(title: str) -> bool:
    """Return False if the video title matches known meta/opinion/clickbait patterns."""
    t = title.lower()
    return not any(pat in t for pat in _META_TITLE_PATTERNS)


def _search_for_node(node, used_video_ids: set, used_channels: set,
                     extra_exclude: set | None = None) -> dict | None:
    """
    Diversified search for a single node. Builds 2-3 query variants (difficulty-
    parametrized, deterministic per user+node), rotates order + videoDuration,
    aggregates all candidates, then picks the best with difficulty + dedup scoring.

    Falls back to legacy "{title} tutorial" when _DIVERSIFY_ENABLED is False.
    """
    user_id = getattr(getattr(node, 'roadmap', None), 'user_id', None)
    node_id = getattr(node, 'pk', None) or getattr(node, 'id', None)
    difficulty = _clamp_difficulty(getattr(node, 'difficulty', 2))

    if not _DIVERSIFY_ENABLED:
        videos = search_youtube(f'{node.title} tutorial', max_results=5)
        if extra_exclude:
            videos = [v for v in videos if v['youtube_video_id'] not in extra_exclude]
        return _pick_best_video(
            videos, node.title, search_query=f'{node.title} tutorial',
            difficulty=difficulty, used_video_ids=used_video_ids,
            used_channels=used_channels,
        )

    variants = _build_query_variants(
        node.title, getattr(node, 'description', ''), difficulty, user_id, node_id,
    )
    # Rotate parameters deterministically — same (user, node) → stable picks,
    # different users → different picks. Bias advanced nodes toward 'long'.
    order = _rotate(_ORDER_POOL, user_id, node_id, salt=1)
    base_duration_pool = ('long', 'medium', None) if difficulty >= 4 else _DURATION_POOL
    video_duration = _rotate(base_duration_pool, user_id, node_id, salt=2)

    aggregated = {}
    for q in variants:
        for v in search_youtube(
            q, max_results=5, order=order, video_duration=video_duration,
        ):
            aggregated[v['youtube_video_id']] = v   # de-dupe across variants

    if extra_exclude:
        for vid_id in extra_exclude:
            aggregated.pop(vid_id, None)

    candidates = list(aggregated.values())
    if not candidates:
        return None
    # Use the first variant as the search-query context for scoring — it's the
    # cleanest reflection of the node title + level suffix.
    return _pick_best_video(
        candidates, node.title, search_query=variants[0],
        difficulty=difficulty, used_video_ids=used_video_ids,
        used_channels=used_channels,
    )


def populate_node_resources(node) -> int:
    """
    For a RoadmapNode:
    1. Fill placeholder resources (url='') with real YouTube videos.
    2. Re-evaluate already-stored videos — if the stored title fails quality check,
       search for a better replacement. This handles bad videos saved before the
       quality filter existed.

    Diversification: queries are difficulty-parametrized and deterministic per
    (user, node), so different users get different videos on the same node and
    re-runs are stable. Cross-node dedup prevents the same channel/video from
    dominating one roadmap.

    Returns count of resources populated or replaced.
    """
    from apps.roadmaps.models import NodeResource

    populated = 0

    # Pull every already-populated (video_id, channel) for this roadmap so
    # _pick_best_video can soft/hard penalize collisions. Done once per call.
    if _DIVERSIFY_ENABLED and getattr(node, 'roadmap_id', None):
        used = NodeResource.objects.filter(
            node__roadmap_id=node.roadmap_id,
            resource_type='youtube_video',
        ).exclude(url__in=['', 'yt:unavailable']).values_list(
            'youtube_video_id', 'youtube_channel',
        )
        used_video_ids = {v for v, _ in used if v}
        used_channels = {c for _, c in used if c}
    else:
        used_video_ids = set()
        used_channels = set()

    # ── Pass 1: fill blank placeholders ────────────────────────────────────────
    placeholders = NodeResource.objects.filter(
        node=node,
        resource_type='youtube_video',
        url='',
    )
    for placeholder in placeholders:
        video = _search_for_node(node, used_video_ids, used_channels)
        if video:
            placeholder.url = video['url']
            placeholder.youtube_video_id = video['youtube_video_id']
            placeholder.youtube_channel = video['youtube_channel']
            placeholder.thumbnail_url = video['thumbnail_url']
            placeholder.title = video['title']
            placeholder.description = ''
            placeholder.duration_minutes = video.get('duration_minutes') or None
            placeholder.save()
            # Accumulate as used for subsequent placeholders on this node.
            used_video_ids.add(video['youtube_video_id'])
            if video.get('youtube_channel'):
                used_channels.add(video['youtube_channel'])
            populated += 1
        else:
            placeholder.url = 'yt:unavailable'
            placeholder.save(update_fields=['url'])

    # ── Pass 2: dynamically replace low-quality existing videos ────────────────
    existing = NodeResource.objects.filter(
        node=node,
        resource_type='youtube_video',
    ).exclude(url__in=['', 'yt:unavailable'])

    for resource in existing:
        if _video_passes_quality(resource.title):
            continue  # already a good video — leave it alone
        # This video's title looks like meta/opinion content — find a better one.
        # Exclude the current bad video from candidates.
        old_title = resource.title
        video = _search_for_node(
            node, used_video_ids, used_channels,
            extra_exclude={resource.youtube_video_id},
        )
        if video:
            resource.url = video['url']
            resource.youtube_video_id = video['youtube_video_id']
            resource.youtube_channel = video['youtube_channel']
            resource.thumbnail_url = video['thumbnail_url']
            resource.title = video['title']
            resource.duration_minutes = video.get('duration_minutes') or None
            resource.save()
            logger.info(
                'Replaced low-quality video "%s" with "%s" for node "%s"',
                old_title, video['title'], node.title,
            )
            used_video_ids.add(video['youtube_video_id'])
            if video.get('youtube_channel'):
                used_channels.add(video['youtube_channel'])
            populated += 1

    return populated

"""
YouTube Data API v3 client.
API key read from Django settings (from .env) — never hardcoded.
Free quota: 10,000 units/day. Search = 100 units. Video details = 1 unit.
"""
import logging
import re

from django.conf import settings

logger = logging.getLogger(__name__)

_youtube_client = None

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
    """
    if not video_ids:
        return {}
    try:
        youtube = _get_youtube_client()
        request = youtube.videos().list(
            part='contentDetails,statistics',
            id=','.join(video_ids),
        )
        response = request.execute()
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
    'roadmap 2024', 'roadmap 2025', 'roadmap 2026',
]


def _title_is_relevant(title: str, search_query: str, topic_keyword: str) -> bool:
    """
    Check if a video title is topically relevant to the lesson.
    Requires at least one significant keyword from the search query or node title to appear in the title.
    """
    title_lower = title.lower()
    # Extract meaningful words (3+ chars) from search query and topic keyword
    query_words = set(
        w for w in re.split(r'[\s\-_/]+', (search_query + ' ' + topic_keyword).lower())
        if len(w) >= 3 and w not in {'for', 'the', 'and', 'with', 'how', 'what', 'that', 'this',
                                      'from', 'into', 'using', 'learn', 'tutorial', 'course',
                                      'beginners', 'beginner', 'full', 'free', 'guide'}
    )
    return any(w in title_lower for w in query_words)


def _pick_best_video(videos: list, topic_keyword: str, search_query: str = '') -> dict | None:
    """Pick the most credible video from a list by scoring channel + title relevance + duration."""
    if not videos:
        return None

    # Exclude videos confirmed shorter than minimum (duration=0 means unknown → keep)
    eligible = [v for v in videos if not (0 < v.get('duration_minutes', 0) < MIN_VIDEO_MINUTES)]
    candidates = eligible if eligible else videos  # fall back to all if every video is too short

    def score(v):
        s = 0
        title_lower = v['title'].lower()

        # Penalise meta/opinion/clickbait titles — they're not lessons
        if any(pat in title_lower for pat in _META_TITLE_PATTERNS):
            s -= 3

        # Channel bonus ONLY applies when the title is actually about the topic.
        # This prevents a famous channel from promoting an off-topic video over
        # a more relevant video from an unknown channel.
        title_relevant = _title_is_relevant(v['title'], search_query, topic_keyword)
        if v['youtube_channel'].lower() in PREFERRED_CHANNELS and title_relevant:
            s += 2
        elif v['youtube_channel'].lower() in PREFERRED_CHANNELS:
            s += 0   # channel bonus withheld — title doesn't match topic

        if title_relevant:
            s += 1   # title directly covers the topic

        dur = v.get('duration_minutes', 0)
        if MIN_VIDEO_MINUTES <= dur <= 20:
            s += 2   # strongly prefer focused lessons (Udemy avg: 8-15 min)
        elif 0 < dur <= 35:
            s += 1   # acceptable — longer but still reasonable
        return s

    return max(candidates, key=score)


def _get_youtube_client():
    """Return a cached YouTube API client (avoids re-fetching discovery doc on every call)."""
    global _youtube_client
    if _youtube_client is None:
        from googleapiclient.discovery import build
        _youtube_client = build('youtube', 'v3', developerKey=settings.YOUTUBE_API_KEY)
    return _youtube_client


def search_youtube(query: str, max_results: int = 5, language: str = 'en') -> list:
    """
    Search YouTube for videos matching the query.
    Returns a list of video dicts with title, videoId, thumbnail, channel.
    """
    if not settings.YOUTUBE_API_KEY:
        return []

    try:
        youtube = _get_youtube_client()

        request = youtube.search().list(
            part='snippet',
            q=query,
            type='video',
            maxResults=max_results,
            relevanceLanguage=language,
            order='relevance',
        )
        response = request.execute()

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

        return results
    except Exception as e:
        logger.error('YouTube search failed for query %r: %s', query, e)
        return []


def _video_passes_quality(title: str) -> bool:
    """Return False if the video title matches known meta/opinion/clickbait patterns."""
    t = title.lower()
    return not any(pat in t for pat in _META_TITLE_PATTERNS)


def populate_node_resources(node) -> int:
    """
    For a RoadmapNode:
    1. Fill placeholder resources (url='') with real YouTube videos.
    2. Re-evaluate already-stored videos — if the stored title fails quality check,
       search for a better replacement. This handles bad videos saved before the
       quality filter existed.
    Returns count of resources populated or replaced.
    """
    from apps.roadmaps.models import NodeResource

    populated = 0

    # ── Pass 1: fill blank placeholders ────────────────────────────────────────
    placeholders = NodeResource.objects.filter(
        node=node,
        resource_type='youtube_video',
        url='',
    )
    for placeholder in placeholders:
        search_query = placeholder.description or f'{node.title} tutorial'
        videos = search_youtube(search_query, max_results=5)
        video = _pick_best_video(videos, node.title, search_query=search_query)
        if video:
            placeholder.url = video['url']
            placeholder.youtube_video_id = video['youtube_video_id']
            placeholder.youtube_channel = video['youtube_channel']
            placeholder.thumbnail_url = video['thumbnail_url']
            placeholder.title = video['title']
            placeholder.description = ''
            placeholder.duration_minutes = video.get('duration_minutes') or None
            placeholder.save()
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
        # This video's title looks like meta/opinion content — find a better one
        search_query = f'{node.title} tutorial'
        videos = search_youtube(search_query, max_results=5)
        # Exclude the current bad video from candidates
        videos = [v for v in videos if v['youtube_video_id'] != resource.youtube_video_id]
        video = _pick_best_video(videos, node.title, search_query=search_query)
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
                resource.title, video['title'], node.title,
            )
            populated += 1

    return populated

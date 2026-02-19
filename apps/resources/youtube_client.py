"""
YouTube Data API v3 client.
API key read from Django settings (from .env) — never hardcoded.
Free quota: 10,000 units/day. Search = 100 units. Video details = 1 unit.
"""
from django.conf import settings


def search_youtube(query: str, max_results: int = 5, language: str = 'en') -> list:
    """
    Search YouTube for videos matching the query.
    Returns a list of video dicts with title, videoId, thumbnail, channel.
    """
    if not settings.YOUTUBE_API_KEY:
        return []

    try:
        from googleapiclient.discovery import build
        youtube = build('youtube', 'v3', developerKey=settings.YOUTUBE_API_KEY)

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
        return results
    except Exception:
        return []


def populate_node_resources(node) -> int:
    """
    For a RoadmapNode with placeholder resources (url=''),
    search YouTube and fill in actual video data.
    Returns count of resources populated.
    """
    from apps.roadmaps.models import NodeResource

    populated = 0
    placeholders = NodeResource.objects.filter(
        node=node,
        resource_type='youtube_video',
        url='',
    )

    for placeholder in placeholders:
        # The search_query was stored temporarily in description field
        search_query = placeholder.description or f'{node.title} tutorial'
        videos = search_youtube(search_query, max_results=1)

        if videos:
            video = videos[0]
            placeholder.url = video['url']
            placeholder.youtube_video_id = video['youtube_video_id']
            placeholder.youtube_channel = video['youtube_channel']
            placeholder.thumbnail_url = video['thumbnail_url']
            placeholder.title = video['title']
            placeholder.description = ''
            placeholder.save()
            populated += 1

    return populated

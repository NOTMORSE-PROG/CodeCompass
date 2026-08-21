from rest_framework import generics, permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from .models import Resource
from .serializers import ResourceSerializer


class ResourceListView(generics.ListAPIView):
    """GET /api/resources/ — Browse resources."""
    serializer_class = ResourceSerializer
    permission_classes = [permissions.IsAuthenticated]
    search_fields = ['title', 'skill_slug', 'youtube_channel']
    filterset_fields = ['resource_type', 'is_free', 'language']
    queryset = Resource.objects.all()


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def youtube_search(request):
    """
    POST /api/resources/youtube-search/
    Body: {"query": "python django tutorial", "language": "en"}
    Returns YouTube search results without saving to DB.
    """
    from .youtube_client import search_youtube
    query = request.data.get('query', '')
    language = request.data.get('language', 'en')
    if not query:
        return Response({'detail': 'query is required.'}, status=400)

    results = search_youtube(query, max_results=5, language=language)
    return Response(results)

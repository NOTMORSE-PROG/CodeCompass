from rest_framework import generics, permissions
from .models import University
from .serializers import UniversitySerializer


class UniversityListView(generics.ListAPIView):
    """GET /api/universities/ — Browse and search universities."""
    serializer_class = UniversitySerializer
    permission_classes = [permissions.IsAuthenticated]
    search_fields = ['name', 'abbreviation', 'city', 'region']
    filterset_fields = ['university_type', 'region', 'ched_coe', 'ched_cod']

    def get_queryset(self):
        return University.objects.filter(is_active=True).prefetch_related('programs')


class UniversityDetailView(generics.RetrieveAPIView):
    """GET /api/universities/{id}/ — University detail with programs."""
    serializer_class = UniversitySerializer
    permission_classes = [permissions.IsAuthenticated]
    queryset = University.objects.filter(is_active=True).prefetch_related('programs')

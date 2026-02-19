from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from .models import JobListing, SavedJob
from .serializers import JobListingSerializer, SavedJobSerializer


class JobListView(generics.ListAPIView):
    """GET /api/jobs/ — Browse job listings with optional keyword filter."""
    serializer_class = JobListingSerializer
    permission_classes = [permissions.IsAuthenticated]
    search_fields = ['title', 'company', 'location', 'description']
    filterset_fields = ['job_type', 'experience_level', 'is_philippines_based']

    def get_queryset(self):
        return JobListing.objects.filter(is_active=True)


class SavedJobListView(generics.ListAPIView):
    """GET /api/jobs/saved/ — User's saved jobs."""
    serializer_class = SavedJobSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return SavedJob.objects.filter(user=self.request.user).select_related('job')


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def save_job(request, pk):
    """POST /api/jobs/{pk}/save/"""
    try:
        job = JobListing.objects.get(pk=pk, is_active=True)
    except JobListing.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    _, created = SavedJob.objects.get_or_create(user=request.user, job=job)
    if created:
        return Response({'detail': 'Job saved.'}, status=status.HTTP_201_CREATED)
    return Response({'detail': 'Already saved.'})


@api_view(['DELETE'])
@permission_classes([permissions.IsAuthenticated])
def unsave_job(request, pk):
    """DELETE /api/jobs/{pk}/save/"""
    SavedJob.objects.filter(user=request.user, job_id=pk).delete()
    return Response(status=status.HTTP_204_NO_CONTENT)

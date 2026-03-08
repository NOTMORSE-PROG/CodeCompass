"""
Admin-only views for the custom admin panel.
These are separate from Django's built-in admin and are consumed by the React frontend.
"""
from django.utils import timezone
from rest_framework import generics, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from .models import CustomUser, MentorProfile
from .permissions import IsAdminUser
from .serializers import UserSerializer, MentorProfileSerializer


class AdminUserListView(generics.ListAPIView):
    """GET /api/admin-panel/users/ — List all users with search and role filter."""
    serializer_class = UserSerializer
    permission_classes = [IsAdminUser]
    search_fields = ['email', 'first_name', 'last_name']
    filterset_fields = ['role', 'is_active', 'is_onboarded']

    def get_queryset(self):
        return CustomUser.objects.all().order_by('-created_at')


class AdminUserDetailView(generics.RetrieveUpdateAPIView):
    """GET/PATCH /api/admin-panel/users/{id}/ — View or update any user (role change, ban)."""
    serializer_class = UserSerializer
    permission_classes = [IsAdminUser]
    queryset = CustomUser.objects.all()


class MentorApplicationListView(generics.ListAPIView):
    """GET /api/admin-panel/mentor-applications/ — Pending mentor approvals."""
    serializer_class = MentorProfileSerializer
    permission_classes = [IsAdminUser]

    def get_queryset(self):
        return MentorProfile.objects.filter(is_verified=False).select_related('user')


@api_view(['POST'])
@permission_classes([IsAdminUser])
def approve_mentor(request, pk):
    """POST /api/admin-panel/mentor-applications/{pk}/approve/"""
    try:
        profile = MentorProfile.objects.get(pk=pk)
    except MentorProfile.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    profile.is_verified = True
    profile.verified_at = timezone.now()
    profile.save()
    return Response({'detail': f'Mentor {profile.user.get_full_name()} approved.'})


@api_view(['POST'])
@permission_classes([IsAdminUser])
def reject_mentor(request, pk):
    """POST /api/admin-panel/mentor-applications/{pk}/reject/"""
    try:
        profile = MentorProfile.objects.get(pk=pk)
    except MentorProfile.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    # reason = request.data.get('reason', '')  # reserved for future rejection email
    # For now, just delete the mentor profile (they can re-apply)
    # In a future iteration, send rejection email here
    profile.delete()
    return Response({'detail': 'Mentor application rejected.'})


class SystemStatsView(generics.GenericAPIView):
    """GET /api/admin-panel/stats/ — System-wide statistics for the admin dashboard."""
    permission_classes = [IsAdminUser]

    def get(self, request):
        from apps.roadmaps.models import Roadmap
        from apps.mentorship.models import MentorshipRequest

        stats = {
            'total_users': CustomUser.objects.count(),
            'total_students': CustomUser.objects.filter(
                role__in=['incoming_student', 'undergraduate']
            ).count(),
            'total_mentors': CustomUser.objects.filter(role='mentor').count(),
            'verified_mentors': MentorProfile.objects.filter(is_verified=True).count(),
            'pending_mentor_applications': MentorProfile.objects.filter(is_verified=False).count(),
            'total_roadmaps': Roadmap.objects.count(),
            'active_mentorships': MentorshipRequest.objects.filter(status='accepted').count(),
        }
        return Response(stats)

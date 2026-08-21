"""
Admin-only views for the custom admin panel.
These are separate from Django's built-in admin and are consumed by the React frontend.
"""
from rest_framework import generics
from rest_framework.response import Response

from .models import CustomUser
from .permissions import IsAdminUser
from .serializers import UserSerializer


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


class SystemStatsView(generics.GenericAPIView):
    """GET /api/admin-panel/stats/ — System-wide statistics for the admin dashboard."""
    permission_classes = [IsAdminUser]

    def get(self, request):
        from apps.roadmaps.models import Roadmap

        stats = {
            'total_users': CustomUser.objects.count(),
            'total_students': CustomUser.objects.filter(
                role__in=['incoming_student', 'undergraduate']
            ).count(),
            'total_roadmaps': Roadmap.objects.count(),
        }
        return Response(stats)

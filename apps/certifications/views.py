from rest_framework import generics, permissions
from rest_framework.filters import SearchFilter
from django_filters.rest_framework import DjangoFilterBackend
from .models import Certification, UserCertification
from .serializers import CertificationSerializer, UserCertificationSerializer


class CertificationListView(generics.ListAPIView):
    """GET /api/certifications/ — Browse all certifications."""
    serializer_class = CertificationSerializer
    permission_classes = [permissions.IsAuthenticated]
    filter_backends = [DjangoFilterBackend, SearchFilter]
    filterset_fields = ['provider', 'level', 'track', 'is_free']
    search_fields = ['name', 'abbreviation', 'description']

    def get_queryset(self):
        return Certification.objects.filter(is_active=True)


class UserCertificationListCreateView(generics.ListCreateAPIView):
    """GET/POST /api/certifications/my/ — User's certification tracker."""
    serializer_class = UserCertificationSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return UserCertification.objects.filter(user=self.request.user).select_related('certification')

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class UserCertificationDetailView(generics.RetrieveUpdateDestroyAPIView):
    """GET/PATCH/DELETE /api/certifications/my/{id}/"""
    serializer_class = UserCertificationSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return UserCertification.objects.filter(user=self.request.user)

    def perform_update(self, serializer):
        old_status = serializer.instance.status  # Use already-loaded instance, not extra DB query
        instance = serializer.save()
        # Award XP when user marks a cert as passed for the first time
        if instance.status == 'passed' and old_status != 'passed':
            try:
                from apps.gamification.engine import award_xp
                from apps.gamification.models import XPEvent as _XPEvent
                if not _XPEvent.objects.filter(
                    user=self.request.user,
                    event_type='cert_earned',
                    reference_id=instance.certification_id,
                ).exists():
                    award_xp(
                        self.request.user,
                        'cert_earned',
                        reference_id=instance.certification_id,
                        description=f'Earned certification: {instance.certification.name}',
                    )
            except Exception:
                pass  # Never block the update if XP fails

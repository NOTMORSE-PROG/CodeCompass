from rest_framework import generics, permissions
from .models import Certification, UserCertification
from .serializers import CertificationSerializer, UserCertificationSerializer


class CertificationListView(generics.ListAPIView):
    """GET /api/certifications/ — Browse all certifications."""
    serializer_class = CertificationSerializer
    permission_classes = [permissions.IsAuthenticated]
    search_fields = ['name', 'abbreviation', 'description']
    filterset_fields = ['provider', 'level', 'is_free']

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

from rest_framework import generics, permissions
from rest_framework.response import Response
from rest_framework.views import APIView
from django.utils import timezone
from datetime import timedelta

from .models import Badge, UserBadge, XPEvent, Leaderboard
from .serializers import BadgeSerializer, UserBadgeSerializer, XPEventSerializer, LeaderboardSerializer


class GamificationProfileView(APIView):
    """GET /api/gamification/profile/ — User's XP, level, badges, streak summary."""
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        user = request.user
        profile = getattr(user, 'student_profile', None)
        badges_earned = UserBadge.objects.filter(user=user).select_related('badge')

        return Response({
            'xp_total': profile.xp_total if profile else 0,
            'streak_count': profile.streak_count if profile else 0,
            'last_active_date': profile.last_active_date if profile else None,
            'badges_earned_count': badges_earned.count(),
            'recent_badges': UserBadgeSerializer(badges_earned[:5], many=True).data,
        })


class BadgeListView(generics.ListAPIView):
    """GET /api/gamification/badges/ — All available badges."""
    serializer_class = BadgeSerializer
    permission_classes = [permissions.IsAuthenticated]
    queryset = Badge.objects.all().order_by('category')


class EarnedBadgesView(generics.ListAPIView):
    """GET /api/gamification/badges/earned/ — Badges this user has earned."""
    serializer_class = UserBadgeSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return UserBadge.objects.filter(user=self.request.user).select_related('badge')


class XPHistoryView(generics.ListAPIView):
    """GET /api/gamification/xp-history/ — Paginated XP event log."""
    serializer_class = XPEventSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return XPEvent.objects.filter(user=self.request.user)


class LeaderboardView(generics.ListAPIView):
    """GET /api/gamification/leaderboard/?period=weekly — Current leaderboard."""
    serializer_class = LeaderboardSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        period = self.request.query_params.get('period', 'weekly')
        today = timezone.now().date()

        if period == 'weekly':
            period_start = today - timedelta(days=today.weekday())
        elif period == 'monthly':
            period_start = today.replace(day=1)
        else:
            period_start = today.replace(year=2024, month=1, day=1)

        return Leaderboard.objects.filter(
            period=period,
            period_start=period_start,
        ).select_related('user').order_by('rank')[:50]

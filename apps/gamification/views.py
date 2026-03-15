from rest_framework import generics, permissions
from rest_framework.response import Response
from rest_framework.views import APIView
from django.db.models import Sum
from django.utils import timezone
from datetime import timedelta

from .models import Badge, UserBadge, XPEvent
from .serializers import BadgeSerializer, UserBadgeSerializer, XPEventSerializer


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
    """GET /api/gamification/xp-history/ — Full XP event log (no pagination)."""
    serializer_class = XPEventSerializer
    permission_classes = [permissions.IsAuthenticated]
    pagination_class = None  # Return all events — history is naturally bounded per user

    def get_queryset(self):
        return XPEvent.objects.filter(user=self.request.user)


class LeaderboardView(generics.GenericAPIView):
    """GET /api/gamification/leaderboard/?period=weekly — Live leaderboard from XPEvent aggregation."""
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        period = request.query_params.get('period', 'weekly')
        today = timezone.now().date()

        if period == 'weekly':
            start = today - timedelta(days=today.weekday())  # Monday of current week
        elif period == 'monthly':
            start = today.replace(day=1)
        else:
            start = None  # all_time: no date filter

        qs = XPEvent.objects.all()
        if start:
            qs = qs.filter(created_at__date__gte=start)

        entries = (
            qs.values('user__id', 'user__first_name', 'user__last_name', 'user__email')
              .annotate(total_xp=Sum('xp_earned'))
              .order_by('-total_xp')[:50]
        )

        results = []
        for rank, entry in enumerate(entries, start=1):
            first = entry['user__first_name'] or ''
            last = entry['user__last_name'] or ''
            full_name = f'{first} {last}'.strip() or entry['user__email']
            results.append({
                'rank': rank,
                'user': {'full_name': full_name},
                'xp_earned': entry['total_xp'],
                'period': period,
            })
        return Response(results)

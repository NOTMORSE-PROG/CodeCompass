from django.urls import path
from . import views

urlpatterns = [
    path('profile/', views.GamificationProfileView.as_view(), name='gamification-profile'),
    path('badges/', views.BadgeListView.as_view(), name='badge-list'),
    path('badges/earned/', views.EarnedBadgesView.as_view(), name='badges-earned'),
    path('xp-history/', views.XPHistoryView.as_view(), name='xp-history'),
    path('leaderboard/', views.LeaderboardView.as_view(), name='leaderboard'),
]

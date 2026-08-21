"""Admin panel URL routes (separate from Django's built-in admin)."""
from django.urls import path
from . import admin_views

urlpatterns = [
    path('users/', admin_views.AdminUserListView.as_view(), name='admin-users'),
    path('users/<int:pk>/', admin_views.AdminUserDetailView.as_view(), name='admin-user-detail'),
    path('stats/', admin_views.SystemStatsView.as_view(), name='admin-stats'),
]

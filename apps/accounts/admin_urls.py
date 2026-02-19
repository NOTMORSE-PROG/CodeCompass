"""Admin panel URL routes (separate from Django's built-in admin)."""
from django.urls import path
from . import admin_views

urlpatterns = [
    path('users/', admin_views.AdminUserListView.as_view(), name='admin-users'),
    path('users/<int:pk>/', admin_views.AdminUserDetailView.as_view(), name='admin-user-detail'),
    path('mentor-applications/', admin_views.MentorApplicationListView.as_view(), name='admin-mentor-applications'),
    path('mentor-applications/<int:pk>/approve/', admin_views.approve_mentor, name='admin-mentor-approve'),
    path('mentor-applications/<int:pk>/reject/', admin_views.reject_mentor, name='admin-mentor-reject'),
    path('stats/', admin_views.SystemStatsView.as_view(), name='admin-stats'),
]

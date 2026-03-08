from django.contrib import admin
from .models import OnboardingSession


@admin.register(OnboardingSession)
class OnboardingSessionAdmin(admin.ModelAdmin):
    list_display = ['user', 'status', 'started_at', 'completed_at']
    list_filter = ['status']
    readonly_fields = ['user', 'status', 'quiz_summary', 'started_at', 'completed_at']

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

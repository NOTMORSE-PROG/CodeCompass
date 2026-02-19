from django.contrib import admin
from django.contrib.auth.admin import UserAdmin

from .models import CustomUser, StudentProfile, MentorProfile


@admin.register(CustomUser)
class CustomUserAdmin(UserAdmin):
    list_display = ['email', 'first_name', 'last_name', 'role', 'is_onboarded', 'is_active']
    list_filter = ['role', 'is_onboarded', 'is_active']
    search_fields = ['email', 'first_name', 'last_name']
    ordering = ['-date_joined']
    fieldsets = UserAdmin.fieldsets + (
        ('CodeCompass', {'fields': ('role', 'is_onboarded', 'avatar')}),
    )


@admin.register(StudentProfile)
class StudentProfileAdmin(admin.ModelAdmin):
    list_display = ['user', 'year_level', 'program', 'xp_total', 'streak_count']
    list_filter = ['year_level', 'program']
    search_fields = ['user__email', 'user__first_name', 'user__last_name']


@admin.register(MentorProfile)
class MentorProfileAdmin(admin.ModelAdmin):
    list_display = ['user', 'mentor_type', 'headline', 'is_verified', 'is_available', 'avg_rating']
    list_filter = ['mentor_type', 'is_verified', 'is_available']
    search_fields = ['user__email', 'headline', 'company']
    actions = ['verify_mentors']

    def verify_mentors(self, request, queryset):
        from django.utils import timezone
        queryset.update(is_verified=True, verified_at=timezone.now())
    verify_mentors.short_description = 'Approve selected mentors'

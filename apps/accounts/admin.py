from django.contrib import admin
from django.contrib.auth.admin import UserAdmin

from .models import CustomUser, StudentProfile


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

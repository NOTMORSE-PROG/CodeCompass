from django.contrib import admin
from .models import Badge, UserBadge, XPEvent, Leaderboard


@admin.register(Badge)
class BadgeAdmin(admin.ModelAdmin):
    list_display = ['name', 'slug', 'category', 'trigger_type', 'trigger_value', 'xp_bonus']
    list_filter = ['category']
    search_fields = ['name', 'slug', 'description']
    prepopulated_fields = {'slug': ('name',)}


@admin.register(UserBadge)
class UserBadgeAdmin(admin.ModelAdmin):
    list_display = ['user', 'badge', 'earned_at']
    list_filter = ['badge__category']
    search_fields = ['user__email', 'badge__name']
    raw_id_fields = ['user', 'badge']


@admin.register(XPEvent)
class XPEventAdmin(admin.ModelAdmin):
    list_display = ['user', 'event_type', 'xp_earned', 'description', 'created_at']
    list_filter = ['event_type']
    search_fields = ['user__email', 'description']
    readonly_fields = ['user', 'event_type', 'xp_earned', 'description', 'reference_id', 'created_at']

    def has_add_permission(self, request):
        return False


@admin.register(Leaderboard)
class LeaderboardAdmin(admin.ModelAdmin):
    list_display = ['rank', 'user', 'period', 'period_start', 'xp_earned']
    list_filter = ['period']
    ordering = ['period', 'rank']

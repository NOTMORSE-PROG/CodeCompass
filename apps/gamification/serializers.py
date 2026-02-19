from rest_framework import serializers
from .models import Badge, UserBadge, XPEvent, Leaderboard


class BadgeSerializer(serializers.ModelSerializer):
    class Meta:
        model = Badge
        fields = ['id', 'name', 'slug', 'description', 'category',
                  'icon_name', 'icon_color', 'xp_bonus']


class UserBadgeSerializer(serializers.ModelSerializer):
    badge = BadgeSerializer(read_only=True)

    class Meta:
        model = UserBadge
        fields = ['id', 'badge', 'earned_at']


class XPEventSerializer(serializers.ModelSerializer):
    class Meta:
        model = XPEvent
        fields = ['id', 'event_type', 'xp_earned', 'description', 'created_at']


class LeaderboardSerializer(serializers.ModelSerializer):
    user_name = serializers.SerializerMethodField()
    user_avatar = serializers.SerializerMethodField()

    class Meta:
        model = Leaderboard
        fields = ['rank', 'user_name', 'user_avatar', 'xp_earned', 'period', 'period_start']

    def get_user_name(self, obj):
        return obj.user.get_full_name() or obj.user.email

    def get_user_avatar(self, obj):
        if obj.user.avatar:
            return obj.user.avatar.url
        return None

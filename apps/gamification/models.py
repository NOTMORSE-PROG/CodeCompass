"""XP, badges, streaks, and leaderboard models."""
from django.db import models


class Badge(models.Model):
    class Category(models.TextChoices):
        ONBOARDING = 'onboarding', 'Onboarding'
        LEARNING = 'learning', 'Learning'
        STREAK = 'streak', 'Streak'
        SOCIAL = 'social', 'Social'
        CERTIFICATION = 'certification', 'Certification'
        COMPLETION = 'completion', 'Roadmap Completion'
        SPECIAL = 'special', 'Special'

    name = models.CharField(max_length=100)
    slug = models.SlugField(unique=True)
    description = models.CharField(max_length=300)
    category = models.CharField(max_length=15, choices=Category.choices)
    icon_name = models.CharField(max_length=50, default='star')
    icon_color = models.CharField(max_length=7, default='#FFC300')
    xp_bonus = models.PositiveSmallIntegerField(default=0)
    trigger_type = models.CharField(max_length=50)
    trigger_value = models.PositiveIntegerField(default=1)

    def __str__(self):
        return self.name


class UserBadge(models.Model):
    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='badges')
    badge = models.ForeignKey(Badge, on_delete=models.CASCADE)
    earned_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('user', 'badge')

    def __str__(self):
        return f'{self.user.email} — {self.badge.name}'


class XPEvent(models.Model):
    class EventType(models.TextChoices):
        NODE_COMPLETED = 'node_completed', 'Completed Roadmap Node'
        ROADMAP_GENERATED = 'roadmap_generated', 'Generated First Roadmap'
        DAILY_LOGIN = 'daily_login', 'Daily Login'
        BADGE_EARNED = 'badge_earned', 'Earned Badge'
        CERT_EARNED = 'cert_earned', 'Earned Certification'
        PROFILE_COMPLETED = 'profile_completed', 'Completed Profile'
        ONBOARDING_COMPLETED = 'onboarding_completed', 'Completed Onboarding'

    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='xp_events')
    event_type = models.CharField(max_length=25, choices=EventType.choices)
    xp_earned = models.PositiveSmallIntegerField()
    description = models.CharField(max_length=200)
    reference_id = models.PositiveIntegerField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']


class Leaderboard(models.Model):
    class Period(models.TextChoices):
        WEEKLY = 'weekly', 'Weekly'
        MONTHLY = 'monthly', 'Monthly'
        ALL_TIME = 'all_time', 'All Time'

    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE)
    period = models.CharField(max_length=10, choices=Period.choices)
    period_start = models.DateField()
    xp_earned = models.PositiveIntegerField(default=0)
    rank = models.PositiveIntegerField()

    class Meta:
        unique_together = ('user', 'period', 'period_start')
        ordering = ['rank']

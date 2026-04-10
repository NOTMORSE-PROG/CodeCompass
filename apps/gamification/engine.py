"""
XP and badge award engine.
Called from signals and views when XP-earning events occur.
"""
from django.db import transaction
from django.db.models import F
from django.utils import timezone

# XP amounts per event type
XP_AMOUNTS = {
    'node_completed': 50,       # Base; overridden by node.xp_reward
    'roadmap_generated': 100,
    'daily_login': 10,
    'badge_earned': 25,
    'cert_earned': 200,
    'profile_completed': 50,
    'onboarding_completed': 75,
}

# Trigger types and their thresholds for badge awarding
BADGE_TRIGGERS = {
    'onboarding_completed': 1,
    'roadmap_generated': 1,
    'node_completed': [1, 10],      # First node, then 10 nodes
    'streak_days': [7, 30],
    'profile_completed': 1,
}


def award_xp(user, event_type: str, reference_id=None, description: str = '', xp_override: int = None, unique_per_user: bool = False):
    """
    Award XP to a user for a specific event.
    Updates StudentProfile.xp_total and creates an XPEvent record.

    unique_per_user=True: use get_or_create so the event is only recorded once
    per user regardless of concurrent requests (e.g. roadmap_generated,
    onboarding_completed). Returns early without updating XP if already exists.
    """
    from .models import XPEvent

    xp = xp_override if xp_override is not None else XP_AMOUNTS.get(event_type, 10)

    if unique_per_user:
        _, created = XPEvent.objects.get_or_create(
            user=user,
            event_type=event_type,
            defaults={
                'xp_earned': xp,
                'description': description or event_type.replace('_', ' ').title(),
                'reference_id': reference_id,
            }
        )
        if not created:
            return  # Already awarded — skip XP update and badge check
    else:
        XPEvent.objects.create(
            user=user,
            event_type=event_type,
            xp_earned=xp,
            description=description or event_type.replace('_', ' ').title(),
            reference_id=reference_id,
        )

    # Update total XP atomically to prevent race conditions
    from apps.accounts.models import StudentProfile
    StudentProfile.objects.filter(user=user).update(xp_total=F('xp_total') + xp)

    # Check and award badges after XP event
    _check_badges(user, event_type, reference_id)


def update_streak(user):
    """
    Update the student's daily streak.
    Call this on daily login. Returns current streak count.
    Uses select_for_update to prevent race conditions on concurrent logins.
    """
    if not hasattr(user, 'student_profile'):
        return 0

    today = timezone.now().date()

    with transaction.atomic():
        from apps.accounts.models import StudentProfile
        profile = StudentProfile.objects.select_for_update().get(user=user)

        if profile.last_active_date == today:
            return profile.streak_count  # Already updated today

        if profile.last_active_date is None or (today - profile.last_active_date).days > 1:
            profile.streak_count = 1     # First login or streak broken
        else:
            profile.streak_count += 1   # Consecutive day

        profile.last_active_date = today
        profile.save(update_fields=['streak_count', 'last_active_date'])

    award_xp(user, 'daily_login', description='Daily login streak!')
    _check_badges(user, 'streak_days', None)

    return profile.streak_count


def _check_badges(user, event_type: str, reference_id):
    """Check and award any badges triggered by this event."""
    from .models import Badge, UserBadge, XPEvent

    candidate_badges = Badge.objects.filter(trigger_type=event_type)
    already_earned = set(UserBadge.objects.filter(user=user).values_list('badge_id', flat=True))

    for badge in candidate_badges:
        if badge.id in already_earned:
            continue

        should_award = False

        if event_type == 'node_completed':
            count = XPEvent.objects.filter(user=user, event_type='node_completed').count()
            should_award = count >= badge.trigger_value

        elif event_type == 'streak_days':
            streak = getattr(getattr(user, 'student_profile', None), 'streak_count', 0)
            should_award = streak >= badge.trigger_value

        else:
            # Simple: this event type occurring once is enough
            should_award = True

        if should_award:
            _, badge_created = UserBadge.objects.get_or_create(user=user, badge=badge)
            # Award bonus XP only if the badge was just earned (guards against concurrent requests)
            if badge_created and badge.xp_bonus:
                award_xp(user, 'badge_earned', badge.id, f'Earned badge: {badge.name}',
                          xp_override=badge.xp_bonus)

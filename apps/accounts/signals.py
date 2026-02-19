"""
Auto-create StudentProfile or MentorProfile when a CustomUser is created.
This ensures every user always has their role-specific profile ready.
"""
from django.db.models.signals import post_save
from django.dispatch import receiver

from .models import CustomUser, StudentProfile, MentorProfile


@receiver(post_save, sender=CustomUser)
def create_user_profile(sender, instance, created, **kwargs):
    """Create the appropriate profile when a new user is registered."""
    if not created:
        return

    if instance.role in (CustomUser.Role.INCOMING_STUDENT, CustomUser.Role.UNDERGRADUATE):
        StudentProfile.objects.get_or_create(
            user=instance,
            defaults={
                'year_level': (
                    'incoming'
                    if instance.role == CustomUser.Role.INCOMING_STUDENT
                    else '1st'
                )
            },
        )

    elif instance.role == CustomUser.Role.MENTOR:
        MentorProfile.objects.get_or_create(user=instance)

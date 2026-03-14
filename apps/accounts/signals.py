"""
Auto-create StudentProfile when a CustomUser is created.
"""
from django.db.models.signals import post_save
from django.dispatch import receiver

from .models import CustomUser, StudentProfile


@receiver(post_save, sender=CustomUser)
def create_user_profile(sender, instance, created, **kwargs):
    """Create a StudentProfile when a new user is registered."""
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

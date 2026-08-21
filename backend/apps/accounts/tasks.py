"""
Celery tasks for async email dispatch in the accounts app.
Autodiscovered via config/celery.py — no extra wiring needed.
Each task retries up to 3 times with a 60-second backoff on SMTP errors.
"""
from celery import shared_task

from .emails import send_password_reset_email, send_verification_email, send_welcome_email


@shared_task(bind=True, max_retries=3, default_retry_delay=60)
def task_send_verification_email(self, user_id):
    from .models import CustomUser
    try:
        user = CustomUser.objects.get(pk=user_id)
        send_verification_email(user)
    except CustomUser.DoesNotExist:
        pass
    except Exception as exc:
        raise self.retry(exc=exc)


@shared_task(bind=True, max_retries=3, default_retry_delay=60)
def task_send_welcome_email(self, user_id):
    from .models import CustomUser
    try:
        user = CustomUser.objects.get(pk=user_id)
        send_welcome_email(user)
    except CustomUser.DoesNotExist:
        pass
    except Exception as exc:
        raise self.retry(exc=exc)


@shared_task(bind=True, max_retries=3, default_retry_delay=60)
def task_send_password_reset_email(self, user_id):
    from .models import CustomUser
    try:
        user = CustomUser.objects.get(pk=user_id)
        send_password_reset_email(user)
    except CustomUser.DoesNotExist:
        pass
    except Exception as exc:
        raise self.retry(exc=exc)

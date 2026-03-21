"""
Email sending utilities for the accounts app.
Each function builds and dispatches one type of transactional email.
Called by Celery tasks — never directly from views.
"""
from django.conf import settings
from django.contrib.auth.tokens import default_token_generator
from django.core import signing
from django.core.mail import send_mail
from django.template.loader import render_to_string
from django.utils.encoding import force_bytes
from django.utils.http import urlsafe_base64_encode


def _send(subject, template_prefix, context, recipient_email):
    """Render HTML + plain-text templates and dispatch via send_mail."""
    html_body = render_to_string(f'accounts/emails/{template_prefix}.html', context)
    plain_body = render_to_string(f'accounts/emails/{template_prefix}.txt', context)
    send_mail(
        subject=subject,
        message=plain_body,
        from_email=settings.DEFAULT_FROM_EMAIL,
        recipient_list=[recipient_email],
        html_message=html_body,
        fail_silently=False,
    )


def send_verification_email(user):
    """Send email address verification link (expires in 24 hours)."""
    token = signing.dumps(user.pk, salt='email-verification')
    verify_url = f'{settings.FRONTEND_URL}/verify-email/{token}'
    _send(
        subject='Verify your CodeCompass email',
        template_prefix='verify_email',
        context={'user': user, 'verify_url': verify_url},
        recipient_email=user.email,
    )


def send_welcome_email(user):
    """Send a welcome email after successful registration."""
    _send(
        subject='Welcome to CodeCompass!',
        template_prefix='welcome',
        context={'user': user, 'app_url': settings.FRONTEND_URL},
        recipient_email=user.email,
    )


def send_password_reset_email(user):
    """Send password reset link using Django's built-in token generator."""
    uidb64 = urlsafe_base64_encode(force_bytes(user.pk))
    token = default_token_generator.make_token(user)
    reset_url = f'{settings.FRONTEND_URL}/reset-password/{uidb64}/{token}'
    _send(
        subject='Reset your CodeCompass password',
        template_prefix='password_reset',
        context={'user': user, 'reset_url': reset_url},
        recipient_email=user.email,
    )

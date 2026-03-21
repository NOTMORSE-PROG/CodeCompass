"""
Auth and profile views for the accounts app.
"""
import logging
import random
import re
import threading

from django.core.cache import cache

from django.conf import settings
from django.contrib.auth.tokens import default_token_generator
from django.core import signing
from django.utils.encoding import force_str
from django.utils.http import urlsafe_base64_decode
from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests

from .emails import (
    send_change_password_otp,
    send_password_reset_email,
    send_verification_email,
    send_welcome_email,
)
from .models import CustomUser, StudentProfile
from .permissions import IsStudent
from .serializers import (
    ChangePasswordSerializer,
    ForgotPasswordSerializer,
    RegisterSerializer,
    ResendVerificationSerializer,
    ResetPasswordSerializer,
    RoleTokenObtainPairSerializer,
    StudentProfileSerializer,
    UserSerializer,
)

logger = logging.getLogger(__name__)


def _email_in_thread(fn, *args):
    """Run an email function in a daemon thread — non-blocking, no Celery worker required."""
    def _run():
        try:
            fn(*args)
        except Exception:
            logger.exception('Email send failed: %s(%s)', fn.__name__, args)
    threading.Thread(target=_run, daemon=True).start()


def _generate_username(email):
    """Generate a unique username from an email address."""
    base = re.sub(r'[^\w]', '', email.split('@')[0]).lower() or 'user'
    username = base
    counter = 1
    while CustomUser.objects.filter(username=username).exists():
        username = f'{base}{counter}'
        counter += 1
    return username


def _issue_tokens(user):
    """Issue simplejwt tokens with custom claims for a user."""
    refresh = RefreshToken.for_user(user)
    refresh['role'] = user.role
    refresh['email'] = user.email
    refresh['full_name'] = user.get_full_name()
    refresh['is_onboarded'] = user.is_onboarded
    refresh['has_password'] = user.has_usable_password()
    refresh['google_connected'] = bool(user.google_id)
    refresh['email_verified'] = user.email_verified
    return str(refresh.access_token), str(refresh)


class RegisterRateThrottle(AnonRateThrottle):
    scope = 'register'


class LoginRateThrottle(AnonRateThrottle):
    scope = 'login'


class LoginView(TokenObtainPairView):
    """
    POST /api/auth/login/
    Returns access + refresh JWT tokens with role, email embedded in payload.
    Also updates the daily login streak.
    """
    serializer_class = RoleTokenObtainPairSerializer
    throttle_classes = [LoginRateThrottle]

    def post(self, request, *args, **kwargs):
        response = super().post(request, *args, **kwargs)
        if response.status_code == 200:
            from apps.gamification.engine import update_streak
            email = request.data.get('email', '').lower().strip()
            try:
                user = CustomUser.objects.get(email=email)
                update_streak(user)
            except CustomUser.DoesNotExist:
                pass
        return response


class RegisterView(generics.CreateAPIView):
    """
    POST /api/auth/register/
    Registers a new user. Signal auto-creates profile based on role.
    """
    queryset = CustomUser.objects.all()
    serializer_class = RegisterSerializer
    permission_classes = [permissions.AllowAny]
    throttle_classes = [RegisterRateThrottle]

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        _email_in_thread(send_verification_email, user)
        _email_in_thread(send_welcome_email, user)

        access, refresh = _issue_tokens(user)
        return Response(
            {
                'user': UserSerializer(user).data,
                'access': access,
                'refresh': refresh,
            },
            status=status.HTTP_201_CREATED,
        )


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def logout_view(request):
    """
    POST /api/auth/logout/
    Blacklists the refresh token so it can no longer be used.
    """
    try:
        refresh_token = request.data.get('refresh')
        token = RefreshToken(refresh_token)
        token.blacklist()
        return Response({'detail': 'Successfully logged out.'}, status=status.HTTP_200_OK)
    except Exception:
        return Response({'detail': 'Invalid token.'}, status=status.HTTP_400_BAD_REQUEST)


class MeView(generics.RetrieveUpdateAPIView):
    """
    GET  /api/auth/me/ — Get current user info
    PUT  /api/auth/me/ — Update name, username, avatar
    """
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        return self.request.user


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def send_change_password_otp_view(request):
    """
    POST /api/auth/send-change-password-otp/
    Generates a 6-digit OTP, caches it for 10 minutes, and emails it to the user.
    Only available to users with a usable password (not pure Google accounts).
    """
    user = request.user
    if not user.has_usable_password():
        return Response(
            {'detail': 'Password change is not available for Google-only accounts.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    otp = str(random.randint(100000, 999999))
    cache_key = f'change_pw_otp_{user.pk}'
    cache.set(cache_key, otp, timeout=600)  # 10 minutes

    _email_in_thread(send_change_password_otp, user, otp)
    return Response({'detail': 'OTP sent to your email.'}, status=status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def change_password_view(request):
    """
    POST /api/auth/change-password/
    Verifies the OTP sent via send-change-password-otp, then changes the password.
    Returns a fresh token pair so the client session stays valid.
    """
    user = request.user
    if not user.has_usable_password():
        return Response(
            {'detail': 'Password change is not available for Google-only accounts.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    serializer = ChangePasswordSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    # Verify OTP
    cache_key = f'change_pw_otp_{user.pk}'
    stored_otp = cache.get(cache_key)
    if not stored_otp or stored_otp != serializer.validated_data['otp']:
        return Response(
            {'otp': ['Invalid or expired code. Request a new one.']},
            status=status.HTTP_400_BAD_REQUEST,
        )

    cache.delete(cache_key)  # One-time use

    user.set_password(serializer.validated_data['new_password'])
    user.save()

    # Blacklist the old refresh token and issue a fresh pair.
    old_refresh_token = request.data.get('refresh')
    if old_refresh_token:
        try:
            RefreshToken(old_refresh_token).blacklist()
        except Exception:
            pass

    access, refresh = _issue_tokens(user)
    return Response({
        'detail': 'Password changed successfully.',
        'access': access,
        'refresh': refresh,
    })


# ---------------------------------------------------------------------------
# Student Profile
# ---------------------------------------------------------------------------

class StudentProfileView(generics.RetrieveUpdateAPIView):
    """
    GET  /api/profiles/student/me/ — Get own student profile
    PUT  /api/profiles/student/me/ — Update student profile
    """
    serializer_class = StudentProfileSerializer
    permission_classes = [IsStudent]

    def get_object(self):
        return self.request.user.student_profile


# ---------------------------------------------------------------------------
# Google OAuth
# ---------------------------------------------------------------------------

class GoogleOAuthView(APIView):
    """
    POST /api/auth/google/
    Accepts a Google ID token (credential) from @react-oauth/google on the frontend.
    Verifies it with Google, then creates or retrieves the user and issues JWT tokens.
    Returns is_new_user=True when the account was just created so the frontend
    can redirect to /auth/google-setup for role selection.
    """
    permission_classes = [permissions.AllowAny]
    throttle_classes = [RegisterRateThrottle]

    def post(self, request):
        credential = request.data.get('credential')
        if not credential:
            return Response(
                {'detail': 'Google credential is required.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if not settings.GOOGLE_CLIENT_ID:
            return Response(
                {'detail': 'Google OAuth is not configured on this server.'},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        try:
            idinfo = id_token.verify_oauth2_token(
                credential,
                google_requests.Request(),
                settings.GOOGLE_CLIENT_ID,
            )
        except ValueError:
            return Response(
                {'detail': 'Invalid or expired Google token.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        except Exception:
            return Response(
                {'detail': 'Google authentication failed. Please try again.'},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        email = idinfo.get('email', '').lower().strip()
        if not email:
            return Response(
                {'detail': 'Email not provided by Google.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        google_sub = idinfo.get('sub', '').strip()
        user, created = CustomUser.objects.get_or_create(
            email=email,
            defaults={
                'first_name': idinfo.get('given_name', ''),
                'last_name': idinfo.get('family_name', ''),
                'username': _generate_username(email),
                # All users start as undergraduate; onboarding AI upgrades to
                # incoming_student if they reveal they are pre-college
                'role': CustomUser.Role.UNDERGRADUATE,
                'is_active': True,
            },
        )

        user_changed = False

        # Mark new Google users as having no password and store their Google sub
        if created:
            user.set_unusable_password()
            user.email_verified = True  # Google already verified the email
            user_changed = True
            StudentProfile.objects.get_or_create(user=user, defaults={'year_level': '1st'})
            _email_in_thread(send_welcome_email, user)

        # Store google_id for all Google logins (enables "Google Connected" tracking)
        if not user.google_id and google_sub:
            user.google_id = google_sub
            user_changed = True

        # Backfill email_verified for existing Google users on next login
        if not user.email_verified and user.google_id:
            user.email_verified = True
            user_changed = True

        if user_changed:
            user.save()

        access, refresh = _issue_tokens(user)
        from apps.gamification.engine import update_streak
        try:
            update_streak(user)
        except Exception:
            pass  # Never block login if streak update fails
        return Response({
            'user': UserSerializer(user).data,
            'access': access,
            'refresh': refresh,
            'is_new_user': created,
        })


@api_view(['DELETE'])
@permission_classes([permissions.IsAuthenticated])
def delete_account_view(request):
    """
    DELETE /api/auth/delete-account/
    Permanently deletes the authenticated user's account and all associated data.
    Blacklists the refresh token if provided so it cannot be reused.
    """
    user = request.user
    refresh_token = request.data.get('refresh')
    if refresh_token:
        try:
            RefreshToken(refresh_token).blacklist()
        except Exception:
            pass
    user.delete()
    return Response({'detail': 'Account deleted successfully.'}, status=status.HTTP_200_OK)


# ---------------------------------------------------------------------------
# Email Verification
# ---------------------------------------------------------------------------

class VerifyEmailView(APIView):
    """
    GET /api/auth/verify-email/<token>/
    Validates a signed verification token and marks the user's email as verified.
    Token expires after 24 hours.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request, token):
        try:
            user_pk = signing.loads(token, salt='email-verification', max_age=86400)
        except signing.SignatureExpired:
            return Response(
                {'detail': 'Verification link has expired. Please request a new one.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        except signing.BadSignature:
            return Response(
                {'detail': 'Invalid verification link.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            user = CustomUser.objects.get(pk=user_pk)
        except CustomUser.DoesNotExist:
            return Response({'detail': 'Invalid verification link.'}, status=status.HTTP_400_BAD_REQUEST)

        user.email_verified = True
        user.save(update_fields=['email_verified'])
        # Issue fresh tokens so the frontend immediately gets email_verified=true in the JWT,
        # even if the user clicked the link from a different device/browser.
        access, refresh = _issue_tokens(user)
        return Response({
            'detail': 'Email verified successfully.',
            'access': access,
            'refresh': refresh,
        }, status=status.HTTP_200_OK)


class ResendVerificationView(APIView):
    """
    POST /api/auth/resend-verification/
    Re-sends the email verification link. Always returns 200 regardless of whether
    the email exists, to avoid leaking account information.
    """
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        serializer = ResendVerificationSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        email = serializer.validated_data['email'].lower().strip()

        try:
            user = CustomUser.objects.get(email=email)
            if not user.email_verified:
                _email_in_thread(send_verification_email, user)
        except CustomUser.DoesNotExist:
            pass  # Never reveal whether the email exists

        return Response(
            {'detail': 'If that email is registered and unverified, a new verification link has been sent.'},
            status=status.HTTP_200_OK,
        )


# ---------------------------------------------------------------------------
# Password Reset
# ---------------------------------------------------------------------------

class PasswordResetRateThrottle(AnonRateThrottle):
    scope = 'password_reset'


class ForgotPasswordView(APIView):
    """
    POST /api/auth/forgot-password/
    Sends a password reset email. Always returns 200 to avoid leaking account info.
    Skips Google-only accounts (no usable password to reset).
    """
    permission_classes = [permissions.AllowAny]
    throttle_classes = [PasswordResetRateThrottle]

    def post(self, request):
        serializer = ForgotPasswordSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        email = serializer.validated_data['email'].lower().strip()

        try:
            user = CustomUser.objects.get(email=email)
            if user.has_usable_password():
                _email_in_thread(send_password_reset_email, user)
        except CustomUser.DoesNotExist:
            pass  # Never reveal whether the email exists

        return Response(
            {'detail': 'If that email has an account, a password reset link has been sent.'},
            status=status.HTTP_200_OK,
        )


class ResetPasswordView(APIView):
    """
    POST /api/auth/reset-password/
    Validates the uid + token from the reset email and sets a new password.
    """
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        serializer = ResetPasswordSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            uid = force_str(urlsafe_base64_decode(serializer.validated_data['uidb64']))
            user = CustomUser.objects.get(pk=uid)
        except (TypeError, ValueError, OverflowError, CustomUser.DoesNotExist):
            return Response(
                {'detail': 'Invalid or expired reset link.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if not default_token_generator.check_token(user, serializer.validated_data['token']):
            return Response(
                {'detail': 'Invalid or expired reset link.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        user.set_password(serializer.validated_data['new_password'])
        user.save()
        return Response({'detail': 'Password reset successfully.'}, status=status.HTTP_200_OK)


class ConnectGoogleView(APIView):
    """
    POST /api/auth/connect-google/
    Allows an email-registered user to link their Google account.
    Verifies the Google credential, ensures the email matches, then stores the Google sub.
    Re-issues JWT tokens so the frontend gets updated google_connected=True claims.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        credential = request.data.get('credential')
        if not credential:
            return Response(
                {'detail': 'Google credential is required.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if not settings.GOOGLE_CLIENT_ID:
            return Response(
                {'detail': 'Google OAuth is not configured on this server.'},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        try:
            idinfo = id_token.verify_oauth2_token(
                credential,
                google_requests.Request(),
                settings.GOOGLE_CLIENT_ID,
            )
        except ValueError:
            return Response(
                {'detail': 'Invalid or expired Google token.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        except Exception:
            return Response(
                {'detail': 'Google authentication failed. Please try again.'},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        # Bug fix #1: validate google_sub is non-empty before saving
        google_sub = idinfo.get('sub', '').strip()
        if not google_sub:
            return Response(
                {'detail': 'Google token is missing required subject claim.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        user = request.user
        google_email = idinfo.get('email', '').lower().strip()

        # Prevent linking a Google account whose sub is already owned by another account
        if CustomUser.objects.filter(google_id=google_sub).exclude(pk=user.pk).exists():
            return Response(
                {'detail': 'This Google account is already connected to a different CodeCompass account.'},
                status=status.HTTP_409_CONFLICT,
            )

        # Prevent linking a Google account whose email is already a separate CodeCompass account
        if CustomUser.objects.filter(email=google_email).exclude(pk=user.pk).exists():
            return Response(
                {'detail': 'This Google account\'s email is already registered as a separate CodeCompass account.'},
                status=status.HTTP_409_CONFLICT,
            )

        if google_email != user.email.lower():
            return Response(
                {'detail': 'The Google account email does not match your account email.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if user.google_id:
            return Response(
                {'detail': 'A Google account is already connected.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        user.google_id = google_sub
        user.save(update_fields=['google_id'])  # Bug fix #6: targeted save

        access, refresh = _issue_tokens(user)
        return Response({'access': access, 'refresh': refresh})

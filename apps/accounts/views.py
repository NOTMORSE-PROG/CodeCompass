"""
Auth and profile views for the accounts app.
"""
import re
from django.conf import settings
from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests

from .models import CustomUser, StudentProfile
from .permissions import IsStudent
from .serializers import (
    RegisterSerializer,
    UserSerializer,
    StudentProfileSerializer,
    ChangePasswordSerializer,
    RoleTokenObtainPairSerializer,
)


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
    return str(refresh.access_token), str(refresh)


class LoginView(TokenObtainPairView):
    """
    POST /api/auth/login/
    Returns access + refresh JWT tokens with role, email embedded in payload.
    """
    serializer_class = RoleTokenObtainPairSerializer


class RegisterView(generics.CreateAPIView):
    """
    POST /api/auth/register/
    Registers a new user. Signal auto-creates profile based on role.
    """
    queryset = CustomUser.objects.all()
    serializer_class = RegisterSerializer
    permission_classes = [permissions.AllowAny]

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

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
def change_password_view(request):
    """POST /api/auth/change-password/"""
    serializer = ChangePasswordSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    user = request.user
    if not user.check_password(serializer.validated_data['old_password']):
        return Response(
            {'old_password': 'Incorrect current password.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    user.set_password(serializer.validated_data['new_password'])
    user.save()
    return Response({'detail': 'Password changed successfully.'})


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

        email = idinfo.get('email')
        if not email:
            return Response(
                {'detail': 'Email not provided by Google.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

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

        # Ensure StudentProfile exists for new Google users
        if created:
            StudentProfile.objects.get_or_create(user=user, defaults={'year_level': '1st'})

        access, refresh = _issue_tokens(user)
        return Response({
            'user': UserSerializer(user).data,
            'access': access,
            'refresh': refresh,
            'is_new_user': created,
        })

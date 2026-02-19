"""
Auth and profile views for the accounts app.
"""
import re
from django.conf import settings
from django.utils import timezone
from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests

from .models import CustomUser, StudentProfile, MentorProfile
from .permissions import IsStudent, IsMentor, IsAdminUser
from .serializers import (
    RegisterSerializer,
    UserSerializer,
    StudentProfileSerializer,
    MentorProfileSerializer,
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
# Mentor Profiles
# ---------------------------------------------------------------------------

class MentorListView(generics.ListAPIView):
    """
    GET /api/profiles/mentors/ — Browse available verified mentors.
    Supports ?expertise=python&mentor_type=industry filtering.
    """
    serializer_class = MentorProfileSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        qs = MentorProfile.objects.filter(is_verified=True, is_available=True).select_related('user')
        mentor_type = self.request.query_params.get('mentor_type')
        if mentor_type:
            qs = qs.filter(mentor_type=mentor_type)
        return qs


class MentorDetailView(generics.RetrieveAPIView):
    """GET /api/profiles/mentors/{id}/ — Public mentor profile."""
    serializer_class = MentorProfileSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return MentorProfile.objects.filter(is_verified=True).select_related('user')


class OwnMentorProfileView(generics.RetrieveUpdateAPIView):
    """
    GET  /api/profiles/mentor/me/ — Mentor views their own profile
    PUT  /api/profiles/mentor/me/ — Mentor updates their profile
    """
    serializer_class = MentorProfileSerializer
    permission_classes = [IsMentor]

    def get_object(self):
        return self.request.user.mentor_profile


@api_view(['POST'])
@permission_classes([IsMentor])
def mentor_apply_view(request):
    """
    POST /api/profiles/mentor/apply/
    Mentor fills in their profile details — sets status to pending admin approval.
    """
    profile = request.user.mentor_profile
    serializer = MentorProfileSerializer(profile, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response({'detail': 'Application submitted. Awaiting admin approval.'})


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
                # Default role — new users will be prompted to select in /auth/google-setup
                'role': CustomUser.Role.INCOMING_STUDENT,
                'is_active': True,
            },
        )

        access, refresh = _issue_tokens(user)
        return Response({
            'user': UserSerializer(user).data,
            'access': access,
            'refresh': refresh,
            'is_new_user': created,
        })


class SetRoleView(APIView):
    """
    POST /api/auth/set-role/
    Allows a newly registered Google OAuth user to set their role before onboarding.
    Only works while is_onboarded=False. Returns fresh JWT tokens with updated role claim.
    Also ensures the correct profile (StudentProfile / MentorProfile) exists for the role.
    """
    VALID_ROLES = [
        CustomUser.Role.INCOMING_STUDENT,
        CustomUser.Role.UNDERGRADUATE,
        CustomUser.Role.MENTOR,
    ]

    def post(self, request):
        if request.user.is_onboarded:
            return Response(
                {'detail': 'Role cannot be changed after onboarding is complete.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        role = request.data.get('role')
        if role not in self.VALID_ROLES:
            return Response(
                {'detail': f'Invalid role. Choose from: {", ".join(self.VALID_ROLES)}'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        user = request.user
        user.role = role
        user.save(update_fields=['role'])

        # Ensure the correct profile exists (signal only runs on creation, not role update)
        if role in (CustomUser.Role.INCOMING_STUDENT, CustomUser.Role.UNDERGRADUATE):
            StudentProfile.objects.get_or_create(
                user=user,
                defaults={'year_level': 'incoming' if role == CustomUser.Role.INCOMING_STUDENT else '1st'},
            )
        elif role == CustomUser.Role.MENTOR:
            MentorProfile.objects.get_or_create(user=user)

        access, refresh = _issue_tokens(user)
        return Response({
            'user': UserSerializer(user).data,
            'access': access,
            'refresh': refresh,
        })

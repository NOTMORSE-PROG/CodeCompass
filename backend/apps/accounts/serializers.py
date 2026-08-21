"""
Serializers for accounts app.
RoleTokenObtainPairSerializer adds role + email to JWT claims.
"""
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import CustomUser, StudentProfile


class RoleTokenObtainPairSerializer(TokenObtainPairSerializer):
    """
    Extends the default JWT serializer to embed role and email in the token.
    React frontend can decode the JWT to get the user's role without extra API calls.
    """

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        # Custom claims embedded in the JWT payload
        token['role'] = user.role
        token['email'] = user.email
        token['full_name'] = user.get_full_name()
        token['is_onboarded'] = user.is_onboarded
        token['has_password'] = user.has_usable_password()
        token['google_connected'] = bool(user.google_id)
        token['email_verified'] = user.email_verified
        return token


class RegisterSerializer(serializers.ModelSerializer):
    """User registration — creates CustomUser + triggers profile signal."""

    password = serializers.CharField(
        write_only=True,
        required=True,
        validators=[validate_password],
    )
    password_confirm = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = CustomUser
        fields = [
            'id', 'email', 'username', 'first_name', 'last_name',
            'password', 'password_confirm',
        ]
        extra_kwargs = {
            'first_name': {'required': True},
            'last_name': {'required': True},
        }

    def validate_email(self, value):
        normalized = value.lower().strip()
        if CustomUser.objects.filter(email=normalized).exists():
            raise serializers.ValidationError('A user with this email already exists.')
        return normalized

    def validate(self, attrs):
        if attrs['password'] != attrs['password_confirm']:
            raise serializers.ValidationError({'password_confirm': 'Passwords do not match.'})
        return attrs

    def create(self, validated_data):
        validated_data.pop('password_confirm')
        password = validated_data.pop('password')
        # All new users start as undergraduate; onboarding AI will upgrade to
        # incoming_student if the user reveals they are pre-college
        user = CustomUser(**validated_data)
        user.role = CustomUser.Role.UNDERGRADUATE
        user.set_password(password)
        user.save()
        # Signal auto-creates StudentProfile
        return user


class UserSerializer(serializers.ModelSerializer):
    """Read-only summary of the current user."""

    has_password = serializers.SerializerMethodField()
    google_connected = serializers.SerializerMethodField()

    def get_has_password(self, obj):
        return obj.has_usable_password()

    def get_google_connected(self, obj):
        return bool(obj.google_id)

    class Meta:
        model = CustomUser
        fields = [
            'id', 'email', 'username', 'first_name', 'last_name',
            'role', 'is_onboarded', 'email_verified', 'avatar', 'created_at',
            'has_password', 'google_connected',
        ]
        read_only_fields = ['id', 'email', 'role', 'created_at', 'email_verified', 'has_password', 'google_connected']


class StudentProfileSerializer(serializers.ModelSerializer):
    """Student profile read/update."""

    user = UserSerializer(read_only=True)

    class Meta:
        model = StudentProfile
        fields = [
            'id', 'user', 'year_level', 'program', 'university',
            'bio', 'linkedin_url', 'github_url',
            'current_skills', 'target_career',
            'xp_total', 'streak_count', 'last_active_date',
            'created_at', 'updated_at',
        ]
        read_only_fields = ['id', 'user', 'xp_total', 'streak_count', 'created_at', 'updated_at']


class ChangePasswordSerializer(serializers.Serializer):
    otp = serializers.CharField(required=True, min_length=6, max_length=6)
    new_password = serializers.CharField(required=True, validators=[validate_password])
    new_password_confirm = serializers.CharField(required=True)

    def validate(self, attrs):
        if attrs['new_password'] != attrs['new_password_confirm']:
            raise serializers.ValidationError({'new_password_confirm': 'Passwords do not match.'})
        return attrs


class ForgotPasswordSerializer(serializers.Serializer):
    email = serializers.EmailField()


class ResetPasswordSerializer(serializers.Serializer):
    uidb64 = serializers.CharField()
    token = serializers.CharField()
    new_password = serializers.CharField(validators=[validate_password])
    new_password_confirm = serializers.CharField()

    def validate(self, attrs):
        if attrs['new_password'] != attrs['new_password_confirm']:
            raise serializers.ValidationError({'new_password_confirm': 'Passwords do not match.'})
        return attrs


class ResendVerificationSerializer(serializers.Serializer):
    email = serializers.EmailField()

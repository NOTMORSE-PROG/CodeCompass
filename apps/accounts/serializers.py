"""
Serializers for accounts app.
RoleTokenObtainPairSerializer adds role + email to JWT claims.
"""
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import CustomUser, StudentProfile, MentorProfile


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
            'role', 'password', 'password_confirm',
        ]
        extra_kwargs = {
            'first_name': {'required': True},
            'last_name': {'required': True},
        }

    def validate(self, attrs):
        if attrs['password'] != attrs['password_confirm']:
            raise serializers.ValidationError({'password_confirm': 'Passwords do not match.'})
        return attrs

    def create(self, validated_data):
        validated_data.pop('password_confirm')
        password = validated_data.pop('password')
        user = CustomUser(**validated_data)
        user.set_password(password)
        # Mentors skip onboarding — they go straight to the app
        if user.role == 'mentor':
            user.is_onboarded = True
        user.save()
        # Signal auto-creates StudentProfile or MentorProfile
        return user


class UserSerializer(serializers.ModelSerializer):
    """Read-only summary of the current user."""

    class Meta:
        model = CustomUser
        fields = [
            'id', 'email', 'username', 'first_name', 'last_name',
            'role', 'is_onboarded', 'avatar', 'created_at',
        ]
        read_only_fields = ['id', 'email', 'role', 'created_at']


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


class MentorProfileSerializer(serializers.ModelSerializer):
    """Mentor profile — used for mentor discovery page."""

    user = UserSerializer(read_only=True)

    class Meta:
        model = MentorProfile
        fields = [
            'id', 'user', 'mentor_type', 'headline', 'bio',
            'company', 'position', 'years_experience',
            'expertise_areas', 'is_available', 'max_mentees', 'current_mentees_count',
            'linkedin_url', 'github_url', 'portfolio_url',
            'is_verified', 'avg_rating', 'total_reviews',
            'created_at', 'updated_at',
        ]
        read_only_fields = [
            'id', 'user', 'is_verified', 'verified_at',
            'avg_rating', 'total_reviews', 'created_at', 'updated_at',
        ]


class ChangePasswordSerializer(serializers.Serializer):
    old_password = serializers.CharField(required=True)
    new_password = serializers.CharField(required=True, validators=[validate_password])
    new_password_confirm = serializers.CharField(required=True)

    def validate(self, attrs):
        if attrs['new_password'] != attrs['new_password_confirm']:
            raise serializers.ValidationError({'new_password_confirm': 'Passwords do not match.'})
        return attrs

from rest_framework import serializers
from .models import OnboardingSession


class OnboardingSessionSerializer(serializers.ModelSerializer):
    class Meta:
        model = OnboardingSession
        fields = ['id', 'status', 'onboarding_summary', 'started_at', 'completed_at']
        read_only_fields = ['id', 'onboarding_summary', 'started_at', 'completed_at']

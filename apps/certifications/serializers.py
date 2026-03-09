from rest_framework import serializers
from .models import Certification, UserCertification


class CertificationSerializer(serializers.ModelSerializer):
    track_display = serializers.CharField(source='get_track_display', read_only=True)
    level_display = serializers.CharField(source='get_level_display', read_only=True)

    class Meta:
        model = Certification
        fields = [
            'id', 'name', 'abbreviation', 'provider', 'level', 'level_display',
            'track', 'track_display', 'description', 'relevant_skills', 'career_paths',
            'exam_url', 'study_guide_url', 'tesda_nc_level', 'is_free',
            'optional_paid_upgrade', 'estimated_cost_php', 'estimated_study_hours',
        ]


class UserCertificationSerializer(serializers.ModelSerializer):
    certification = CertificationSerializer(read_only=True)
    certification_id = serializers.PrimaryKeyRelatedField(
        queryset=Certification.objects.all(), source='certification', write_only=True
    )

    class Meta:
        model = UserCertification
        fields = [
            'id', 'certification', 'certification_id', 'status',
            'started_studying_at', 'earned_at', 'expires_at',
            'certificate_url', 'notes',
        ]
        read_only_fields = ['id']

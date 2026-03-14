from rest_framework import serializers
from .models import Resume


class ResumeSerializer(serializers.ModelSerializer):
    class Meta:
        model = Resume
        fields = ['id', 'title', 'template_name', 'content', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']


class ResumeListSerializer(serializers.ModelSerializer):
    """Lightweight serializer for list views — excludes full content."""
    class Meta:
        model = Resume
        fields = ['id', 'title', 'template_name', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']

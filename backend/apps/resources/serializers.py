from rest_framework import serializers
from .models import Resource


class ResourceSerializer(serializers.ModelSerializer):
    class Meta:
        model = Resource
        fields = ['id', 'resource_type', 'title', 'url', 'description',
                  'thumbnail_url', 'duration_minutes', 'is_free', 'language',
                  'skill_slug', 'youtube_video_id', 'youtube_channel', 'view_count']

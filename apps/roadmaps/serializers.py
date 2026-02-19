from rest_framework import serializers
from .models import Roadmap, RoadmapNode, NodeResource, NodeProgress


class NodeResourceSerializer(serializers.ModelSerializer):
    class Meta:
        model = NodeResource
        fields = ['id', 'resource_type', 'title', 'url', 'description',
                  'thumbnail_url', 'duration_minutes', 'is_free', 'language',
                  'youtube_video_id', 'youtube_channel', 'order']


class RoadmapNodeSerializer(serializers.ModelSerializer):
    resources = NodeResourceSerializer(many=True, read_only=True)

    class Meta:
        model = RoadmapNode
        fields = ['id', 'parent_node', 'node_type', 'title', 'description',
                  'skill_slug', 'position_x', 'position_y', 'node_order',
                  'estimated_hours', 'difficulty', 'status', 'xp_reward',
                  'completed_at', 'resources']
        read_only_fields = ['id', 'completed_at']


class RoadmapSerializer(serializers.ModelSerializer):
    nodes = RoadmapNodeSerializer(many=True, read_only=True)

    class Meta:
        model = Roadmap
        fields = ['id', 'title', 'career_path', 'description', 'status',
                  'ai_model_used', 'flow_layout', 'completion_percentage',
                  'estimated_weeks', 'is_primary', 'generated_at',
                  'nodes', 'created_at', 'updated_at']
        read_only_fields = ['id', 'status', 'ai_model_used', 'completion_percentage',
                             'generated_at', 'created_at', 'updated_at']


class RoadmapListSerializer(serializers.ModelSerializer):
    """Lightweight — no nodes — for list view."""
    class Meta:
        model = Roadmap
        fields = ['id', 'title', 'career_path', 'status',
                  'completion_percentage', 'estimated_weeks', 'is_primary', 'created_at']


class NodeProgressSerializer(serializers.ModelSerializer):
    class Meta:
        model = NodeProgress
        fields = ['id', 'node', 'started_at', 'completed_at', 'notes', 'time_spent_minutes']
        read_only_fields = ['id', 'started_at']

from rest_framework import serializers
from .models import ChatSession, ChatMessage


class ChatMessageSerializer(serializers.ModelSerializer):
    class Meta:
        model = ChatMessage
        fields = ['id', 'role', 'content', 'tokens_used', 'model_used', 'created_at']
        read_only_fields = ['id', 'tokens_used', 'model_used', 'created_at']


class ChatSessionSerializer(serializers.ModelSerializer):
    messages = ChatMessageSerializer(many=True, read_only=True)

    class Meta:
        model = ChatSession
        fields = ['id', 'session_id', 'context_type', 'title', 'is_active',
                  'total_tokens_used', 'messages', 'created_at', 'updated_at']
        read_only_fields = ['id', 'session_id', 'total_tokens_used', 'created_at', 'updated_at']


class ChatSessionListSerializer(serializers.ModelSerializer):
    """Lightweight serializer for session list (no messages)."""
    class Meta:
        model = ChatSession
        fields = ['id', 'session_id', 'context_type', 'title', 'is_active',
                  'total_tokens_used', 'created_at', 'updated_at']

"""REST endpoints for chat session management (history, list, create)."""
from django.core.cache import cache
from django.db.models import Count
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from .models import ChatSession, ChatMessage
from .serializers import ChatSessionSerializer, ChatSessionListSerializer, ChatMessageSerializer


class ChatSessionListCreateView(generics.ListCreateAPIView):
    """
    GET  /api/chat/sessions/     - List user's chat sessions
    POST /api/chat/sessions/     - Create a new session
    """
    permission_classes = [permissions.IsAuthenticated]

    def get_serializer_class(self):
        if self.request.method == 'POST':
            return ChatSessionSerializer
        return ChatSessionListSerializer

    def get_queryset(self):
        return (
            ChatSession.objects
            .filter(user=self.request.user, is_active=True)
            .exclude(context_type='onboarding')
            .annotate(message_count=Count('messages'))
            .filter(message_count__gt=0)
            .order_by('-updated_at')
        )

    def perform_create(self, serializer):
        # Starting a new onboarding session — hard-delete all previous ones so
        # incomplete onboarding chats never accumulate in the database.
        context_type = serializer.validated_data.get('context_type', 'general')
        if context_type == 'onboarding':
            # Soft-deactivate prior onboarding sessions instead of hard-deleting,
            # so any in-flight WebSocket still holding a reference can finish
            # writing its greeting message without an FK violation.
            ChatSession.objects.filter(
                user=self.request.user,
                context_type='onboarding',
                is_active=True,
            ).update(is_active=False)
        serializer.save(user=self.request.user)


class ChatSessionDetailView(generics.RetrieveUpdateDestroyAPIView):
    """
    GET    /api/chat/sessions/{session_id}/ - Get session with all messages
    PATCH  /api/chat/sessions/{session_id}/ - Update session title
    DELETE /api/chat/sessions/{session_id}/ - Soft-delete (deactivate) session
    """
    serializer_class = ChatSessionSerializer
    permission_classes = [permissions.IsAuthenticated]
    lookup_field = 'session_id'
    http_method_names = ['get', 'patch', 'delete', 'head', 'options']

    def get_queryset(self):
        return ChatSession.objects.filter(user=self.request.user)

    def partial_update(self, request, *args, **kwargs):
        # Only allow updating the title field
        allowed = {k: v for k, v in request.data.items() if k == 'title'}
        serializer = self.get_serializer(self.get_object(), data=allowed, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data)

    def destroy(self, request, *args, **kwargs):
        session = self.get_object()
        session.is_active = False
        session.save()
        return Response(status=status.HTTP_204_NO_CONTENT)


class ChatMessageListView(generics.ListAPIView):
    """GET /api/chat/sessions/{session_pk}/messages/ - Paginated messages."""
    serializer_class = ChatMessageSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return ChatMessage.objects.filter(
            session__session_id=self.kwargs['session_id'],
            session__user=self.request.user,
        )


@api_view(['GET'])
@permission_classes([permissions.IsAdminUser])
def ai_health(request):
    """
    GET /api/chat/admin/ai-health/
    Admin-only view returning AI telemetry counters.
    Counters are incremented in consumers.py when the LLM emits malformed tags.
    Requires Redis cache backend (standard for this project).
    """
    tag_keys = [
        'ai.tag.dropped.roadmap_edit',
        'ai.tag.dropped.roadmap_switch',
        'ai.tag.dropped.roadmap_upskill',
    ]
    dropped_counts = {key.split('.')[-1]: cache.get(key, 0) for key in tag_keys}
    roadmap_retry_count = cache.get('ai.roadmap_generation.retry_total', 0)

    return Response({
        'dropped_tags': dropped_counts,
        'roadmap_generation_retries': roadmap_retry_count,
    })

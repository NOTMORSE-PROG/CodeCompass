"""REST endpoints for chat session management (history, list, create)."""
from rest_framework import generics, permissions, status
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
        return ChatSession.objects.filter(user=self.request.user, is_active=True)

    def perform_create(self, serializer):
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

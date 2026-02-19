"""
WebSocket consumer for AI chat streaming.

Flow:
1. Client connects to WS /ws/chat/{session_id}/
2. Client sends JSON: {"message": "Paano ako magiging fullstack developer?"}
3. Consumer authenticates via JWT in query string or cookie
4. Loads chat history for context
5. Calls Groq stream=True
6. Streams each chunk back: {"type": "stream_chunk", "content": "..."}
7. On finish: {"type": "stream_end", "message_id": 123, "tokens_used": 450}
8. Saves complete assistant message to DB
"""
import json
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser


class ChatConsumer(AsyncWebsocketConsumer):

    async def connect(self):
        """Authenticate user and load the chat session."""
        self.session_id = self.scope['url_route']['kwargs']['session_id']
        self.user = self.scope.get('user')

        # Reject unauthenticated connections
        if not self.user or isinstance(self.user, AnonymousUser):
            await self.close(code=4001)
            return

        # Verify the session belongs to this user
        self.chat_session = await self.get_session()
        if self.chat_session is None:
            await self.close(code=4004)
            return

        await self.accept()

    async def disconnect(self, close_code):
        pass

    async def receive(self, text_data):
        """Handle incoming message from the client."""
        try:
            data = json.loads(text_data)
        except json.JSONDecodeError:
            return

        user_message_text = data.get('message', '').strip()
        if not user_message_text:
            return

        # Save user message
        user_msg = await self.save_message('user', user_message_text)

        # Build message history for context (last 20 messages)
        history = await self.get_message_history()

        # Stream AI response
        full_response = ''
        try:
            from .groq_client import stream_chat
            for chunk in stream_chat(history, self.user.role):
                full_response += chunk
                await self.send(text_data=json.dumps({
                    'type': 'stream_chunk',
                    'content': chunk,
                }))
        except Exception as e:
            await self.send(text_data=json.dumps({
                'type': 'stream_error',
                'error': str(e),
            }))
            return

        # Save complete assistant message
        assistant_msg = await self.save_message('assistant', full_response)

        # Signal stream complete
        await self.send(text_data=json.dumps({
            'type': 'stream_end',
            'message_id': assistant_msg.id,
        }))

    # -------------------------------------------------------------------------
    # Database helpers (sync-to-async wrappers)
    # -------------------------------------------------------------------------

    @database_sync_to_async
    def get_session(self):
        from .models import ChatSession
        try:
            return ChatSession.objects.get(
                session_id=self.session_id,
                user=self.user,
                is_active=True,
            )
        except ChatSession.DoesNotExist:
            return None

    @database_sync_to_async
    def save_message(self, role: str, content: str):
        from .models import ChatMessage
        return ChatMessage.objects.create(
            session=self.chat_session,
            role=role,
            content=content,
            model_used='llama-3.3-70b-versatile' if role == 'assistant' else '',
        )

    @database_sync_to_async
    def get_message_history(self) -> list:
        """Return the last 20 messages formatted for the Groq API."""
        from .models import ChatMessage
        messages = ChatMessage.objects.filter(
            session=self.chat_session,
            role__in=['user', 'assistant'],
        ).order_by('-created_at')[:20]

        return [
            {'role': msg.role, 'content': msg.content}
            for msg in reversed(list(messages))
        ]

"""
WebSocket consumer for AI chat streaming.

Flow:
1. Client connects to WS /ws/chat/{session_id}/
2. Client sends JSON: {"message": "Paano ako magiging fullstack developer?"}
3. Consumer authenticates via JWT in query string or cookie
4. Loads chat history for context
5. Calls Groq stream=True
6. Streams each chunk back: {"type": "stream_chunk", "content": "..."}
7. On finish: {"type": "stream_end", "message_id": 123}
8. Saves complete assistant message to DB

Onboarding sessions (context_type='onboarding'):
- Use ONBOARDING_SYSTEM_PROMPT instead of the career mentor prompt
- Automatically stream a greeting message on connect (no user trigger needed)
"""
import json
import re
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser


def _extract_suggestions(text: str):
    """
    Parse [SUGGESTIONS: opt1 | opt2 | opt3] from an AI response.
    Returns (clean_text, suggestions_list).
    """
    match = re.search(r'\[SUGGESTIONS:\s*(.+?)\]', text, re.IGNORECASE)
    if not match:
        return text, []
    suggestions = [s.strip() for s in match.group(1).split('|') if s.strip()]
    clean = re.sub(r'\s*\[SUGGESTIONS:.*?\]', '', text, flags=re.IGNORECASE).strip()
    return clean, suggestions


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

        # For onboarding sessions, immediately stream an AI greeting
        if self.chat_session.context_type == 'onboarding':
            await self._send_greeting()

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
        await self.save_message('user', user_message_text)

        # Build message history for context (last 20 messages)
        history = await self.get_message_history()

        # Pick system prompt based on session context
        from .groq_client import stream_chat, ONBOARDING_SYSTEM_PROMPT
        system_prompt = ONBOARDING_SYSTEM_PROMPT if self.chat_session.context_type == 'onboarding' else None

        # Stream AI response
        full_response = ''
        stream_failed = False
        try:
            for chunk in stream_chat(history, self.user.role, system_prompt=system_prompt):
                full_response += chunk
                await self.send(text_data=json.dumps({
                    'type': 'stream_chunk',
                    'content': chunk,
                }))
        except Exception as e:
            stream_failed = True
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_error',
                    'error': str(e),
                }))
            except Exception:
                pass  # Client already disconnected — ignore
            return

        if stream_failed or not full_response:
            return

        # Strip suggestions tag from saved content
        clean_response, suggestions = _extract_suggestions(full_response)

        try:
            # Save clean assistant message
            assistant_msg = await self.save_message('assistant', clean_response)

            # Signal stream complete with suggestions and clean content
            await self.send(text_data=json.dumps({
                'type': 'stream_end',
                'message_id': assistant_msg.id,
                'clean_content': clean_response,
                'suggestions': suggestions,
            }))
        except Exception:
            pass  # Client disconnected before stream_end could be sent

    async def _send_greeting(self):
        """Stream the AI's opening onboarding question to the client."""
        from .groq_client import stream_chat, ONBOARDING_SYSTEM_PROMPT

        trigger = [{'role': 'user', 'content': '__start__'}]
        full_response = ''
        try:
            for chunk in stream_chat(trigger, self.user.role, system_prompt=ONBOARDING_SYSTEM_PROMPT):
                full_response += chunk
                await self.send(text_data=json.dumps({
                    'type': 'stream_chunk',
                    'content': chunk,
                }))
        except Exception as e:
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_error',
                    'error': str(e),
                }))
            except Exception:
                pass  # Client already disconnected — ignore
            return

        if not full_response:
            return

        clean_response, suggestions = _extract_suggestions(full_response)
        try:
            await self.save_message('assistant', clean_response)
            await self.send(text_data=json.dumps({
                'type': 'stream_end',
                'message_id': None,
                'clean_content': clean_response,
                'suggestions': suggestions,
            }))
        except Exception:
            pass  # Client disconnected before greeting completed

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

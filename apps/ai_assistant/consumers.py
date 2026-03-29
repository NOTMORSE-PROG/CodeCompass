"""
WebSocket consumer for AI chat streaming.

Flow:
1. Client connects to WS /ws/chat/{session_id}/
2. Client sends JSON: {"message": "Paano ako magiging fullstack developer?"}
3. Consumer authenticates via JWT in query string or cookie
4. Loads chat history for context + user profile for personalization
5. Calls Groq stream=True with personalized system prompt
6. Streams each chunk back: {"type": "stream_chunk", "content": "..."}
7. On finish: {"type": "stream_end", "message_id": 123}
8. Saves complete assistant message to DB

Onboarding sessions (context_type='onboarding'):
- Use ONBOARDING_SYSTEM_PROMPT instead of the career mentor prompt
- Automatically stream a greeting message on connect (no user trigger needed)
"""
import asyncio
import json
import logging
import re
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser

logger = logging.getLogger('ai_assistant.consumers')

# ---------------------------------------------------------------------------
# Input pre-screening — blocks obvious jailbreak attempts before calling Groq
# ---------------------------------------------------------------------------
_JAILBREAK_RE = re.compile(
    r'\bignore\b.{0,30}\b(previous|above|all|your)\b.{0,30}\b(instructions?|rules?|prompt|guidelines?)\b'
    r'|\bforget\b.{0,30}\b(you are|your role|everything|all|previous)\b'
    r'|\bpretend\b.{0,20}\b(you (are|have no)|there (are|is) no)\b'
    r'|\byou are now\b'
    r'|\bact as\b.{0,30}\b(dan|jailbreak|unrestricted|evil|unfiltered|free)\b'
    r'|\bdan mode\b'
    r'|\bdeveloper mode\b'
    r'|\bjailbreak\b'
    r'|\bno restrictions?\b'
    r'|\bno (ethical |safety |content )?filters?\b'
    r'|\bunrestricted (ai|mode|version)\b'
    r'|\byour (true|real|hidden|actual) (self|purpose|identity|instructions?)\b'
    r'|\boverride\b.{0,20}\b(safety|filter|restriction|guideline|system)\b'
    r'|\bbypass\b.{0,20}\b(filter|restriction|safety|content)\b'
    r'|\bdo anything now\b',
    re.IGNORECASE,
)


def _check_jailbreak(text: str) -> bool:
    """Return True if the message matches a known jailbreak pattern."""
    return bool(_JAILBREAK_RE.search(text[:500]))


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


def _extract_resources(text: str):
    """
    Parse [RESOURCES: Title1|url1|Title2|url2] from an AI response.
    Returns (clean_text, resources_list).
    Each resource is {'title': str, 'url': str}. Only includes pairs where url starts with 'http'.
    """
    match = re.search(r'\[RESOURCES:\s*(.+?)\]', text, re.IGNORECASE)
    if not match:
        return text, []
    parts = [p.strip() for p in match.group(1).split('|') if p.strip()]
    resources = []
    for i in range(0, len(parts) - 1, 2):
        url = parts[i + 1]
        if url.startswith('http'):
            resources.append({'title': parts[i], 'url': url})
    clean = re.sub(r'\s*\[RESOURCES:.*?\]', '', text, flags=re.IGNORECASE).strip()
    return clean, resources


def _extract_roadmap_switch(text: str):
    """
    Parse [ROADMAP_SWITCH: {...}] from an AI response.
    Returns (clean_text, switch_proposal | None).
    Returns None if tag is missing or invalid.
    """
    match = re.search(r'\[ROADMAP_SWITCH:\s*(\{.*?\})\s*\]', text, re.IGNORECASE | re.DOTALL)
    clean = re.sub(r'\s*\[ROADMAP_SWITCH:.*?\]', '', text, flags=re.IGNORECASE | re.DOTALL).strip()
    if not match:
        return clean, None
    try:
        proposal = json.loads(match.group(1))
    except (json.JSONDecodeError, ValueError):
        return clean, None
    rid = proposal.get('roadmap_id')
    new_path = proposal.get('new_path', '').strip()
    career_goal = proposal.get('career_goal', '').strip()
    summary = proposal.get('summary', '').strip()
    if not all([rid, new_path, career_goal, summary]):
        return clean, None
    try:
        if int(str(rid)) <= 0:
            return clean, None
    except (ValueError, TypeError):
        return clean, None
    return clean, proposal


def _extract_edit_proposals(text: str):
    """
    Parse all [ROADMAP_EDIT: {...}] tags from an AI response.
    Returns (clean_text, list_of_valid_proposals).
    Invalid/incomplete proposals are stripped from the text but excluded from the list.
    """
    matches = re.findall(r'\[ROADMAP_EDIT:\s*(\{.*?\})\s*\]', text, re.IGNORECASE | re.DOTALL)
    # Always strip all tags from text
    clean = re.sub(r'\s*\[ROADMAP_EDIT:.*?\]', '', text, flags=re.IGNORECASE | re.DOTALL).strip()
    valid = []
    for raw in matches:
        try:
            proposal = json.loads(raw)
        except (json.JSONDecodeError, ValueError):
            continue
        if _is_valid_proposal(proposal):
            valid.append(proposal)
    return clean, valid


_ALLOWED_ACTIONS = {'edit_node', 'edit_roadmap', 'add_node', 'remove_node'}


def _is_valid_proposal(proposal: dict) -> bool:
    """
    Returns True only if the proposal has all required fields with real values.
    Catches AI-generated placeholders: '?', null, empty strings, '<id>' templates.
    """
    def _is_placeholder(val):
        if val is None:
            return True
        s = str(val).strip()
        return s in ('', '?', 'null', 'undefined') or s.startswith('<')

    action = proposal.get('action', '')
    if action not in _ALLOWED_ACTIONS:
        return False

    rid = proposal.get('roadmap_id')
    if _is_placeholder(rid) or not str(rid).lstrip('-').isdigit() or int(str(rid)) <= 0:
        return False

    if action in ('edit_node', 'remove_node'):
        nid = proposal.get('node_id')
        if _is_placeholder(nid) or not str(nid).lstrip('-').isdigit() or int(str(nid)) <= 0:
            return False

    if action in ('edit_node', 'edit_roadmap', 'add_node'):
        changes = proposal.get('changes')
        if not isinstance(changes, dict) or not changes:
            return False
        if any(_is_placeholder(v) for v in changes.values()):
            return False

    if _is_placeholder(proposal.get('summary', '')):
        return False

    return True


class ChatConsumer(AsyncWebsocketConsumer):

    async def connect(self):
        """Accept the WebSocket immediately, then resolve user and session.

        Security model:
        - Middleware validates the JWT signature/expiry cryptographically
          (instant, no DB) and stores the payload in scope['jwt_payload'].
        - accept() is called first so Render's reverse proxy doesn't drop
          the connection while DB queries are in flight.
        - User is fetched from DB here, after the handshake is complete.
          If the token is invalid/missing or the session doesn't belong to
          this user, we close immediately with a specific code (4001/4004).
        """
        self.session_id = self.scope['url_route']['kwargs']['session_id']

        # Complete the HTTP 101 handshake immediately.
        await self.accept()

        # Resolve JWT payload set by middleware (crypto-only, no DB hit).
        payload = self.scope.get('jwt_payload')
        if not payload:
            logger.warning('[WS-CONNECT] Rejected — no valid JWT for session %s', self.session_id)
            await self.close(code=4001)
            return

        # Now fetch the User from DB (safe to do after accept).
        self.user = await self.get_user_from_payload(payload)
        if self.user is None or isinstance(self.user, AnonymousUser):
            logger.warning('[WS-CONNECT] Rejected — user not found for session %s', self.session_id)
            await self.close(code=4001)
            return

        logger.info('[WS-CONNECT] session_id=%s user=%s', self.session_id, self.user)

        # Verify the session belongs to this user.
        self.chat_session = await self.get_session()
        if self.chat_session is None:
            logger.warning('[WS-CONNECT] Rejected — session %s not found for user %s', self.session_id, self.user.id)
            await self.close(code=4004)
            return

        logger.info('[WS-CONNECT] Accepted session %s for user %s', self.session_id, self.user.id)

        # Load personalized user context for system prompt injection.
        self.user_context = await self.load_user_context()

        # For onboarding sessions, stream the AI greeting only on the FIRST connect.
        # If the user refreshes/reconnects, the session already has messages — skip the greeting
        # to avoid a duplicate "Hello!" being saved and streamed again.
        if self.chat_session.context_type == 'onboarding':
            if not await self._session_has_messages():
                await self._send_greeting()

    async def disconnect(self, close_code):
        logger.debug('[WS] Client disconnected: session=%s code=%s', getattr(self, 'session_id', '?'), close_code)

    async def receive(self, text_data):
        """Handle incoming message from the client."""
        try:
            data = json.loads(text_data)
        except json.JSONDecodeError:
            return

        user_message_text = data.get('message', '').strip()
        if not user_message_text:
            return
        language = data.get('language', 'english')

        # Block jailbreak attempts before any DB write or Groq call
        if self.chat_session.context_type != 'onboarding' and _check_jailbreak(user_message_text):
            logger.warning('[WS] Blocked jailbreak attempt: session=%s user=%s', self.session_id, self.user.id)
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_end',
                    'message_id': None,
                    'clean_content': (
                        "I'm CodeCompass — your CCS career mentor. I can't help with that, "
                        "but I'm here for anything about IT careers, learning paths, or tech jobs in the Philippines!"
                    ),
                    'suggestions': ['My career options', 'My learning roadmap', 'Job search tips'],
                    'edit_proposals': None,
                    'resources': None,
                    'roadmap_switch': None,
                }))
            except Exception:
                pass
            return

        # Save user message
        await self.save_message('user', user_message_text)

        # Auto-generate session title from first user message
        if not self.chat_session.title:
            raw = user_message_text[:60]
            # Truncate at last word boundary to avoid cutting mid-word
            title = raw.rsplit(' ', 1)[0] if len(user_message_text) > 60 else raw
            await self.update_session_title(title)
            try:
                await self.send(text_data=json.dumps({
                    'type': 'session_title_updated',
                    'title': title,
                }))
            except Exception as e:
                logger.debug('[WS] Could not send title update (client likely disconnected): %s', e)

        # Build message history for context (last 20 messages)
        history = await self.get_message_history()

        # Pick system prompt based on session context + personalized user data
        from .groq_client import stream_chat, get_onboarding_system_prompt, build_career_mentor_prompt
        if self.chat_session.context_type == 'onboarding':
            system_prompt = get_onboarding_system_prompt(self.user.role)
        else:
            system_prompt = build_career_mentor_prompt(
                user_context=self.user_context,
                mode=self.chat_session.context_type,
                language=language,
            )

        # Stream AI response (60 s hard timeout — prevents indefinite hang if Groq stalls)
        full_response = ''
        stream_failed = False
        try:
            async with asyncio.timeout(60):
                for chunk in stream_chat(history, self.user.role, system_prompt=system_prompt):
                    full_response += chunk
                    await self.send(text_data=json.dumps({
                        'type': 'stream_chunk',
                        'content': chunk,
                    }))
        except TimeoutError:
            stream_failed = True
            logger.warning('[WS] Groq stream timed out for session %s', getattr(self, 'session_id', '?'))
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_error',
                    'error': 'Response timed out. Please try again.',
                }))
            except Exception as send_err:
                logger.debug('[WS] Could not send timeout error (client disconnected): %s', send_err)
            return
        except Exception:
            stream_failed = True
            logger.exception('[WS] Groq stream error for session %s', getattr(self, 'session_id', '?'))
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_error',
                    'error': 'AI service temporarily unavailable. Please try again.',
                }))
            except Exception as send_err:
                logger.debug('[WS] Could not send stream_error (client disconnected): %s', send_err)
            return

        if stream_failed or not full_response:
            return

        # Strip all structured tags from saved content
        clean_response, suggestions = _extract_suggestions(full_response)
        clean_response, edit_proposals = _extract_edit_proposals(clean_response)
        clean_response, resources = _extract_resources(clean_response)
        clean_response, roadmap_switch = _extract_roadmap_switch(clean_response)

        try:
            # Save clean assistant message
            assistant_msg = await self.save_message('assistant', clean_response)

            # Signal stream complete with suggestions, clean content, optional edit proposals, resources, and switch
            await self.send(text_data=json.dumps({
                'type': 'stream_end',
                'message_id': assistant_msg.id,
                'clean_content': clean_response,
                'suggestions': suggestions,
                'edit_proposals': edit_proposals if edit_proposals else None,
                'resources': resources if resources else None,
                'roadmap_switch': roadmap_switch,
            }))
        except Exception as e:
            logger.exception('[WS] Failed to save/send stream_end for session %s: %s', getattr(self, 'session_id', '?'), e)

    async def _send_greeting(self):
        """Stream the AI's opening onboarding question to the client."""
        from .groq_client import stream_chat, get_onboarding_system_prompt

        trigger = [{'role': 'user', 'content': '__start__'}]
        full_response = ''
        try:
            async with asyncio.timeout(60):
                for chunk in stream_chat(trigger, self.user.role, system_prompt=get_onboarding_system_prompt(self.user.role)):
                    full_response += chunk
                    await self.send(text_data=json.dumps({
                        'type': 'stream_chunk',
                        'content': chunk,
                    }))
        except TimeoutError:
            logger.warning('[WS] Greeting stream timed out for session %s', getattr(self, 'session_id', '?'))
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_error',
                    'error': 'AI service temporarily unavailable. Please try again.',
                }))
            except Exception as send_err:
                logger.debug('[WS] Could not send greeting timeout error (client disconnected): %s', send_err)
            return
        except Exception:
            logger.exception('[WS] Greeting stream error for session %s', getattr(self, 'session_id', '?'))
            try:
                await self.send(text_data=json.dumps({
                    'type': 'stream_error',
                    'error': 'AI service temporarily unavailable. Please try again.',
                }))
            except Exception as send_err:
                logger.debug('[WS] Could not send greeting error (client disconnected): %s', send_err)
            return

        if not full_response:
            return

        clean_response, suggestions = _extract_suggestions(full_response)
        clean_response, _ = _extract_edit_proposals(clean_response)
        clean_response, resources = _extract_resources(clean_response)
        clean_response, _ = _extract_roadmap_switch(clean_response)
        try:
            greeting_msg = await self.save_message('assistant', clean_response)
            await self.send(text_data=json.dumps({
                'type': 'stream_end',
                'message_id': greeting_msg.id,
                'clean_content': clean_response,
                'suggestions': suggestions,
                'resources': resources if resources else None,
                'roadmap_switch': None,
            }))
        except Exception as e:
            logger.exception('[WS] Failed to save/send greeting stream_end for session %s: %s', getattr(self, 'session_id', '?'), e)

    # -------------------------------------------------------------------------
    # Database helpers (sync-to-async wrappers)
    # -------------------------------------------------------------------------

    @database_sync_to_async
    def get_user_from_payload(self, payload):
        """Fetch the User row from DB using the user_id from a validated JWT payload."""
        from django.contrib.auth import get_user_model
        User = get_user_model()
        try:
            return User.objects.get(id=payload['user_id'])
        except (User.DoesNotExist, KeyError):
            return None

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
    def load_user_context(self) -> dict:
        """Load personalized student context for system prompt injection."""
        ctx = {
            'name': getattr(self.user, 'full_name', '') or '',
            'role': self.user.role or '',
        }
        # StudentProfile fields
        try:
            sp = self.user.student_profile
            ctx.update({
                'year_level': sp.year_level or '',
                'program': sp.program or '',
                'target_career': sp.target_career or '',
                'current_skills': sp.current_skills or [],
            })
        except Exception as e:
            logger.debug('[WS] load_user_context: student_profile unavailable for user %s: %s', self.user.pk, e)
        # Onboarding summary
        try:
            summary = self.user.onboarding_session.onboarding_summary or {}
            ctx.update({
                'background': summary.get('background', ''),
                'interests': summary.get('interests', []),
                'career_goal': summary.get('career_goal', '') or ctx.get('target_career', ''),
                'learning_style': summary.get('learning_style', ''),
                'recommended_path': summary.get('recommended_path', ''),
            })
        except Exception as e:
            logger.debug('[WS] load_user_context: onboarding_summary unavailable for user %s: %s', self.user.pk, e)
        # Primary roadmap progress
        try:
            from apps.roadmaps.models import Roadmap
            roadmap = (
                Roadmap.objects.filter(user=self.user, is_primary=True).first()
                or Roadmap.objects.filter(user=self.user).order_by('-created_at').first()
            )
            if roadmap:
                nodes = list(roadmap.nodes.order_by('position_y').values('id', 'title', 'status'))
                ctx['roadmap_id'] = roadmap.id
                ctx['roadmap_title'] = roadmap.title
                ctx['roadmap_path'] = roadmap.career_path
                ctx['roadmap_pct'] = roadmap.completion_percentage
                ctx['completed_nodes'] = [n['title'] for n in nodes if n['status'] == 'completed']
                ctx['current_node'] = next(
                    (n['title'] for n in nodes if n['status'] == 'in_progress'), None
                )
                ctx['full_node_list'] = [
                    {'id': n['id'], 'title': n['title'], 'status': n['status']}
                    for n in nodes
                ]
        except Exception as e:
            logger.debug('[WS] load_user_context: roadmap unavailable for user %s: %s', self.user.pk, e)
        return ctx

    @database_sync_to_async
    def update_session_title(self, title: str):
        self.chat_session.title = title
        self.chat_session.save(update_fields=['title'])

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
    def _session_has_messages(self) -> bool:
        """Return True if this session already has at least one saved message."""
        from .models import ChatMessage
        return ChatMessage.objects.filter(session=self.chat_session).exists()

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

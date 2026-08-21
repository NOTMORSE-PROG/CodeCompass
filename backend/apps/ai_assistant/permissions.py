"""
Custom DRF permissions for the AI-assistant flow.

FromRoadmapScopedSession — gate roadmap-mutating endpoints on the
origin chat session's scope. Clients must send X-Chat-Session-Id
(or body 'originating_session_id') pointing to a ChatSession whose
context_type is 'roadmap'. Missing header = pass-through (admin,
webhooks, non-chat callers); this preserves backward compatibility
with pre-upgrade clients and with direct API use.

Defense-in-depth: this is the API-boundary layer. Prompt scoping
(groq_client._SCOPE_FENCES) and server-side tag stripping
(consumers.ALLOWED_TAGS_BY_SCOPE) form the upstream layers.
"""
import logging

from django.conf import settings
from rest_framework import permissions

logger = logging.getLogger(__name__)


class FromRoadmapScopedSession(permissions.BasePermission):
    """
    Permit roadmap mutations only when either
    (a) no chat-session header is present (non-chat caller), OR
    (b) the X-Chat-Session-Id header (or body 'originating_session_id')
        points to an active ChatSession owned by the request user whose
        context_type == 'roadmap'.

    Staff/superusers bypass entirely — admin tooling and support flows
    stay unaffected.

    Enforcement can be toggled at deploy time by setting
    settings.ROADMAP_SCOPE_ENFORCE = False — in that case, we still log
    violations but allow the request. Useful for the rollout window where
    older clients haven't yet shipped the header.
    """
    message = 'Roadmap mutations must originate from the Roadmap chat tab.'

    def has_permission(self, request, view):
        if request.user.is_authenticated and request.user.is_staff:
            return True

        sid = (
            request.headers.get('X-Chat-Session-Id')
            or (request.data.get('originating_session_id') if hasattr(request, 'data') else None)
        )
        if not sid:
            # Backward compat: callers that don't declare a session
            # (admin, webhook, older mobile builds) still pass. Ownership
            # and rate-limit checks on the view handle abuse.
            return True

        # Lazy import — avoids circular import if this permission is
        # re-used anywhere that's imported by apps.ai_assistant.apps.
        from apps.ai_assistant.models import ChatSession

        try:
            sess = ChatSession.objects.only(
                'user_id', 'context_type', 'is_active'
            ).get(session_id=sid)
        except ChatSession.DoesNotExist:
            self._log_violation(request, sid, reason='session_not_found')
            return self._enforce(allow=False)

        ok = (
            sess.user_id == request.user.id
            and sess.context_type == 'roadmap'
            and sess.is_active
        )
        if not ok:
            self._log_violation(
                request, sid,
                reason=f'owner={sess.user_id == request.user.id} '
                       f'scope={sess.context_type} active={sess.is_active}',
            )
        return self._enforce(allow=ok)

    def _log_violation(self, request, sid, reason):
        logger.warning(
            '[API-SCOPE] %s user=%s path=%s session=%s reason=%s',
            'DENY' if getattr(settings, 'ROADMAP_SCOPE_ENFORCE', True) else 'LOG-ONLY',
            getattr(request.user, 'id', None),
            request.path,
            sid,
            reason,
        )

    def _enforce(self, allow):
        if allow:
            return True
        # Feature-flag escape hatch for the rollout window:
        # when ROADMAP_SCOPE_ENFORCE=False, we've already logged the
        # violation above — let it through so older clients work.
        return not bool(getattr(settings, 'ROADMAP_SCOPE_ENFORCE', True))

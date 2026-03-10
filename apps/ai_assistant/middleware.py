"""JWT authentication middleware for Django Channels WebSocket connections."""
import logging
import urllib.parse
from channels.middleware import BaseMiddleware
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser

logger = logging.getLogger('ai_assistant.middleware')


@database_sync_to_async
def get_user_from_token(token_string):
    from rest_framework_simplejwt.tokens import AccessToken
    from django.contrib.auth import get_user_model
    User = get_user_model()
    try:
        token = AccessToken(token_string)
        user = User.objects.get(id=token['user_id'])
        logger.debug('[WS-AUTH] Token valid → user_id=%s role=%s', user.id, getattr(user, 'role', '?'))
        return user
    except Exception as exc:
        logger.warning('[WS-AUTH] Token validation failed: %s', exc)
        return AnonymousUser()


class JwtAuthMiddleware(BaseMiddleware):
    """Reads ?token=<jwt> from the WebSocket query string and authenticates the user."""

    async def __call__(self, scope, receive, send):
        query_string = scope.get('query_string', b'').decode()
        params = urllib.parse.parse_qs(query_string)
        token_list = params.get('token', [])

        logger.debug(
            '[WS-AUTH] Incoming scope type=%s path=%s token_present=%s',
            scope.get('type'),
            scope.get('path'),
            bool(token_list),
        )

        if token_list:
            scope['user'] = await get_user_from_token(token_list[0])
        else:
            scope['user'] = AnonymousUser()
            logger.warning('[WS-AUTH] No token in query string — AnonymousUser assigned')

        return await super().__call__(scope, receive, send)

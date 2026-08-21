"""JWT authentication middleware for Django Channels WebSocket connections."""
import logging
import urllib.parse
from channels.middleware import BaseMiddleware
from django.contrib.auth.models import AnonymousUser

logger = logging.getLogger('ai_assistant.middleware')


def _decode_token(token_string):
    """
    Validate JWT signature and expiry only — no DB call.
    Returns the payload dict on success, or None if invalid/expired.
    This keeps middleware fast so accept() can be reached without delay.
    The actual User object is fetched in the consumer after accept().
    """
    from rest_framework_simplejwt.tokens import AccessToken
    try:
        token = AccessToken(token_string)
        return token.payload
    except Exception as exc:
        logger.warning('[WS-AUTH] Invalid token: %s', exc)
        return None


class JwtAuthMiddleware(BaseMiddleware):
    """
    Reads ?token=<jwt> from the WebSocket query string.
    Validates the JWT cryptographically (instant, no DB hit) and stores
    the payload in scope['jwt_payload']. The consumer fetches the User
    object from the DB after completing the handshake via accept().
    """

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
            payload = _decode_token(token_list[0])
            if payload:
                logger.debug('[WS-AUTH] Token valid → user_id=%s', payload.get('user_id'))
                scope['jwt_payload'] = payload
                scope['user'] = None  # resolved in consumer after accept()
            else:
                scope['jwt_payload'] = None
                scope['user'] = AnonymousUser()
        else:
            scope['jwt_payload'] = None
            scope['user'] = AnonymousUser()
            logger.warning('[WS-AUTH] No token in query string — AnonymousUser assigned')

        return await super().__call__(scope, receive, send)

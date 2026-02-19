"""
Development settings.
Uses Neon PostgreSQL (via DATABASE_URL in .env) even in development.
No local PostgreSQL or Redis installation needed — in-memory layers used for dev.
"""
from .base import *

DEBUG = True

# Show emails in console during development
EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'

# Allow all hosts in development
ALLOWED_HOSTS = ['*']

# Looser CORS for development
CORS_ALLOW_ALL_ORIGINS = True

# ---------------------------------------------------------------------------
# In-memory channel layer — no Redis required for local dev.
# WebSocket / AI streaming still works; just no cross-process broadcast.
# Switch back to channels_redis in production (Upstash).
# ---------------------------------------------------------------------------
CHANNEL_LAYERS = {
    'default': {
        'BACKEND': 'channels.layers.InMemoryChannelLayer',
    },
}

# In-memory cache for development (no Redis needed)
CACHES = {
    'default': {
        'BACKEND': 'django.core.cache.backends.locmem.LocMemCache',
    }
}

"""
Production settings for Render deployment.
All secrets must be set as environment variables in the Render dashboard.
"""
from .base import *

DEBUG = False

# Security hardening
SECURE_SSL_REDIRECT = True
SECURE_HSTS_SECONDS = 31536000
SECURE_HSTS_INCLUDE_SUBDOMAINS = True
SECURE_HSTS_PRELOAD = True
SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True

# Only allow Vercel frontend in production
# CORS_ALLOWED_ORIGINS is set in base.py from CORS_ALLOWED_ORIGINS env var
# Render automatically sets PORT; Daphne uses it via Procfile

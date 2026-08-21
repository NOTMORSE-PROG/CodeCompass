"""
WSGI config for CodeCompass (fallback; Daphne uses ASGI in production).
"""
import os

from django.core.wsgi import get_wsgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings.development')

application = get_wsgi_application()

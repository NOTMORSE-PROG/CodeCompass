"""
Root URL configuration for CodeCompass API.
All routes prefixed with /api/ for clean separation from the React frontend.
"""
from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static

urlpatterns = [
    # Django admin (for superuser use only)
    path('django-admin/', admin.site.urls),

    # Auth endpoints
    path('api/auth/', include('apps.accounts.urls')),

    # Feature endpoints
    path('api/onboarding/', include('apps.onboarding.urls')),
    path('api/roadmaps/', include('apps.roadmaps.urls')),
    path('api/chat/', include('apps.ai_assistant.urls')),
    path('api/resumes/', include('apps.resumes.urls')),
    path('api/resources/', include('apps.resources.urls')),
    path('api/jobs/', include('apps.jobs.urls')),
    path('api/universities/', include('apps.universities.urls')),
    path('api/certifications/', include('apps.certifications.urls')),
    path('api/gamification/', include('apps.gamification.urls')),

    # Admin panel (custom, non-Django-admin)
    path('api/admin-panel/', include('apps.accounts.admin_urls')),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)

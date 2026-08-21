from django.urls import path
from . import views

urlpatterns = [
    path('complete-from-chat/', views.complete_from_chat, name='onboarding-complete-from-chat'),
    path('status/', views.onboarding_status, name='onboarding-status'),
]

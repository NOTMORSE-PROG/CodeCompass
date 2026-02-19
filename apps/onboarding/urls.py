from django.urls import path
from . import views

urlpatterns = [
    path('questions/', views.QuizQuestionsView.as_view(), name='quiz-questions'),
    path('start/', views.start_onboarding, name='onboarding-start'),
    path('responses/', views.submit_responses, name='onboarding-responses'),
    path('complete/', views.complete_onboarding, name='onboarding-complete'),
    path('status/', views.onboarding_status, name='onboarding-status'),
]

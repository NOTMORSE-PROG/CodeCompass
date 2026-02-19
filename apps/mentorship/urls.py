from django.urls import path
from . import views

urlpatterns = [
    path('requests/', views.MentorshipRequestListCreateView.as_view(), name='mentorship-requests'),
    path('requests/<int:pk>/', views.respond_to_request, name='mentorship-respond'),
    path('sessions/', views.SessionListCreateView.as_view(), name='mentorship-sessions'),
    path('sessions/<int:session_pk>/review/', views.submit_review, name='mentorship-review'),
]

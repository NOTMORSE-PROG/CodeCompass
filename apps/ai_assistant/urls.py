from django.urls import path
from . import views

urlpatterns = [
    path('sessions/', views.ChatSessionListCreateView.as_view(), name='chat-session-list'),
    path('sessions/<uuid:session_id>/', views.ChatSessionDetailView.as_view(), name='chat-session-detail'),
    path('sessions/<uuid:session_id>/messages/', views.ChatMessageListView.as_view(), name='chat-messages'),
]

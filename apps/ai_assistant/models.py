"""Chat session and message models for the AI assistant."""
import uuid
from django.db import models


class ChatSession(models.Model):
    class ContextType(models.TextChoices):
        GENERAL = 'general', 'General Career Advice'
        ROADMAP = 'roadmap', 'Roadmap Discussion'
        MENTOR = 'mentor', 'Mentor Recommendation'
        JOB = 'job', 'Job Search'
        UNIVERSITY = 'university', 'University Selection'

    user = models.ForeignKey(
        'accounts.CustomUser',
        on_delete=models.CASCADE,
        related_name='chat_sessions',
    )
    session_id = models.UUIDField(default=uuid.uuid4, unique=True, editable=False)
    context_type = models.CharField(
        max_length=15,
        choices=ContextType.choices,
        default=ContextType.GENERAL,
    )
    roadmap = models.ForeignKey(
        'roadmaps.Roadmap',
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name='chat_sessions',
    )
    title = models.CharField(max_length=200, blank=True)
    is_active = models.BooleanField(default=True)
    total_tokens_used = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']

    def __str__(self):
        return f'ChatSession({self.user.email}, {self.session_id})'


class ChatMessage(models.Model):
    class Role(models.TextChoices):
        USER = 'user', 'User'
        ASSISTANT = 'assistant', 'AI Assistant'
        SYSTEM = 'system', 'System'

    session = models.ForeignKey(ChatSession, on_delete=models.CASCADE, related_name='messages')
    role = models.CharField(max_length=10, choices=Role.choices)
    content = models.TextField()
    tokens_used = models.PositiveIntegerField(default=0)
    model_used = models.CharField(max_length=50, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f'{self.role}: {self.content[:60]}...'

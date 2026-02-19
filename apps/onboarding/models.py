"""Onboarding quiz models."""
from django.db import models


class QuizQuestion(models.Model):
    class Audience(models.TextChoices):
        INCOMING = 'incoming', 'Incoming Students Only'
        UNDERGRADUATE = 'undergraduate', 'Undergraduate Students Only'
        BOTH = 'both', 'Both'

    class QuestionType(models.TextChoices):
        SINGLE_CHOICE = 'single_choice', 'Single Choice'
        MULTI_CHOICE = 'multi_choice', 'Multiple Choice'
        SCALE = 'scale', 'Likert Scale (1-5)'
        TEXT = 'text', 'Free Text'

    class Category(models.TextChoices):
        INTEREST = 'interest', 'Interest / Passion'
        APTITUDE = 'aptitude', 'Aptitude / Skill Level'
        GOAL = 'goal', 'Career Goal'
        LEARNING_STYLE = 'learning_style', 'Learning Style'
        BACKGROUND = 'background', 'Background / Experience'

    audience = models.CharField(max_length=15, choices=Audience.choices)
    category = models.CharField(max_length=20, choices=Category.choices)
    question_type = models.CharField(max_length=15, choices=QuestionType.choices)
    question_text = models.TextField()
    question_text_tagalog = models.TextField(blank=True)
    options = models.JSONField(default=list)
    order = models.PositiveSmallIntegerField(default=0)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ['order']

    def __str__(self):
        return f'[{self.audience}] {self.question_text[:60]}'


class OnboardingSession(models.Model):
    class Status(models.TextChoices):
        IN_PROGRESS = 'in_progress', 'In Progress'
        COMPLETED = 'completed', 'Completed'
        ROADMAP_GENERATED = 'roadmap_generated', 'Roadmap Generated'

    user = models.OneToOneField(
        'accounts.CustomUser',
        on_delete=models.CASCADE,
        related_name='onboarding_session',
    )
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.IN_PROGRESS)
    quiz_summary = models.JSONField(default=dict)
    started_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f'OnboardingSession({self.user.email}, {self.status})'


class QuizResponse(models.Model):
    session = models.ForeignKey(OnboardingSession, on_delete=models.CASCADE, related_name='responses')
    question = models.ForeignKey(QuizQuestion, on_delete=models.CASCADE)
    answer_value = models.JSONField()
    answered_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('session', 'question')

from rest_framework import serializers
from .models import QuizQuestion, OnboardingSession, QuizResponse


class QuizQuestionSerializer(serializers.ModelSerializer):
    class Meta:
        model = QuizQuestion
        fields = ['id', 'category', 'question_type', 'question_text',
                  'question_text_tagalog', 'options', 'order']


class QuizResponseSerializer(serializers.ModelSerializer):
    class Meta:
        model = QuizResponse
        fields = ['question', 'answer_value']


class OnboardingSessionSerializer(serializers.ModelSerializer):
    responses = QuizResponseSerializer(many=True, read_only=True)

    class Meta:
        model = OnboardingSession
        fields = ['id', 'status', 'quiz_summary', 'started_at', 'completed_at', 'responses']
        read_only_fields = ['id', 'quiz_summary', 'started_at', 'completed_at']

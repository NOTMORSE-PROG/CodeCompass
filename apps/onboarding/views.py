"""Onboarding quiz views."""
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.accounts.permissions import IsStudent
from apps.gamification.engine import award_xp
from .models import QuizQuestion, OnboardingSession, QuizResponse
from .serializers import QuizQuestionSerializer, OnboardingSessionSerializer


class QuizQuestionsView(generics.ListAPIView):
    """GET /api/onboarding/questions/ — Questions for this user's role."""
    serializer_class = QuizQuestionSerializer
    permission_classes = [IsStudent]

    def get_queryset(self):
        role = self.request.user.role
        audience = 'incoming' if role == 'incoming_student' else 'undergraduate'
        return QuizQuestion.objects.filter(
            is_active=True,
            audience__in=[audience, 'both'],
        )


@api_view(['POST'])
@permission_classes([IsStudent])
def start_onboarding(request):
    """POST /api/onboarding/start/ — Create or resume an onboarding session."""
    session, created = OnboardingSession.objects.get_or_create(user=request.user)
    return Response(OnboardingSessionSerializer(session).data,
                    status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([IsStudent])
def submit_responses(request):
    """
    POST /api/onboarding/responses/
    Body: {"responses": [{"question": 1, "answer_value": "web_dev"}, ...]}
    Saves quiz answers in batch.
    """
    try:
        session = request.user.onboarding_session
    except OnboardingSession.DoesNotExist:
        return Response({'detail': 'Start onboarding first.'}, status=status.HTTP_400_BAD_REQUEST)

    responses_data = request.data.get('responses', [])
    for item in responses_data:
        try:
            question = QuizQuestion.objects.get(pk=item['question'])
            QuizResponse.objects.update_or_create(
                session=session,
                question=question,
                defaults={'answer_value': item['answer_value']},
            )
        except (QuizQuestion.DoesNotExist, KeyError):
            continue

    return Response({'detail': f'Saved {len(responses_data)} responses.'})


@api_view(['POST'])
@permission_classes([IsStudent])
def complete_onboarding(request):
    """
    POST /api/onboarding/complete/
    Finalizes the quiz, builds quiz_summary JSON, marks user as onboarded.
    """
    try:
        session = request.user.onboarding_session
    except OnboardingSession.DoesNotExist:
        return Response({'detail': 'Start onboarding first.'}, status=400)

    # Build quiz_summary from all responses
    summary = {
        'user_role': request.user.role,
        'responses': {},
    }
    for response in session.responses.select_related('question'):
        summary['responses'][response.question.category] = {
            'question': response.question.question_text,
            'answer': response.answer_value,
        }

    session.quiz_summary = summary
    session.status = OnboardingSession.Status.COMPLETED
    session.completed_at = timezone.now()
    session.save()

    # Mark user as onboarded
    request.user.is_onboarded = True
    request.user.save(update_fields=['is_onboarded'])

    # Award XP for completing onboarding
    award_xp(request.user, 'quiz_completed', session.id, 'Completed the onboarding quiz!')

    return Response({
        'detail': 'Onboarding complete. You can now generate your roadmap!',
        'quiz_summary': summary,
    })


@api_view(['GET'])
@permission_classes([permissions.IsAuthenticated])
def onboarding_status(request):
    """GET /api/onboarding/status/"""
    try:
        session = request.user.onboarding_session
        return Response({
            'status': session.status,
            'is_onboarded': request.user.is_onboarded,
        })
    except OnboardingSession.DoesNotExist:
        return Response({'status': 'not_started', 'is_onboarded': False})

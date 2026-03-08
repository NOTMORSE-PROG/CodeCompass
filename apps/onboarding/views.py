"""Onboarding views — chat-based onboarding only."""
from django.utils import timezone
from rest_framework import permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.gamification.engine import award_xp
from .models import OnboardingSession


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def complete_from_chat(request):
    """
    POST /api/onboarding/complete-from-chat/
    Extracts a structured student profile from an onboarding chat session via AI,
    saves it as quiz_summary in OnboardingSession, and marks the user as onboarded.
    Body: {"chat_session_id": "<uuid>"}
    """
    from apps.ai_assistant.models import ChatSession, ChatMessage
    from apps.ai_assistant.groq_client import extract_profile_from_chat

    chat_session_id = request.data.get('chat_session_id')
    if not chat_session_id:
        return Response({'detail': 'chat_session_id is required.'}, status=status.HTTP_400_BAD_REQUEST)

    try:
        chat_session = ChatSession.objects.get(
            session_id=chat_session_id,
            user=request.user,
            context_type='onboarding',
        )
    except ChatSession.DoesNotExist:
        return Response({'detail': 'Onboarding chat session not found.'}, status=status.HTTP_400_BAD_REQUEST)

    messages = list(
        ChatMessage.objects.filter(session=chat_session)
        .order_by('created_at')
        .values('role', 'content')
    )
    profile = extract_profile_from_chat(messages, request.user.role)

    session, _ = OnboardingSession.objects.get_or_create(user=request.user)
    session.quiz_summary = profile
    session.status = OnboardingSession.Status.COMPLETED
    session.completed_at = timezone.now()
    session.save()

    # Upgrade role to incoming_student if the chat reveals they're a fresh SHS / pre-college student
    background = (profile.get('background') or '').lower()
    incoming_keywords = ('shs', 'senior high', 'incoming', 'pre-college', 'pre college', 'fresh grad', 'bagong grad')
    update_fields = ['is_onboarded']
    if any(kw in background for kw in incoming_keywords) and request.user.role == 'undergraduate':
        request.user.role = 'incoming_student'
        update_fields.append('role')

    request.user.is_onboarded = True
    request.user.save(update_fields=update_fields)

    award_xp(request.user, 'quiz_completed', session.id, 'Completed the onboarding chat!')

    # Issue fresh tokens with all custom claims so the frontend JWT reflects is_onboarded=True
    from apps.accounts.views import _issue_tokens
    access, refresh = _issue_tokens(request.user)

    return Response({
        'detail': 'Onboarding complete!',
        'quiz_summary': profile,
        'access': access,
        'refresh': refresh,
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

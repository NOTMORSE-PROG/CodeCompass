"""Onboarding views — chat-based onboarding only."""
import logging
from django.db import transaction
from django.utils import timezone
from rest_framework import permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from apps.gamification.engine import award_xp
from .models import OnboardingSession

logger = logging.getLogger('onboarding.views')


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def complete_from_chat(request):
    """
    POST /api/onboarding/complete-from-chat/
    Extracts a structured student profile from an onboarding chat session via AI,
    saves it as onboarding_summary in OnboardingSession, and marks the user as onboarded.
    Also syncs year_level and program to StudentProfile for undergrads.
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

    # Guard: already completed — return cached data without re-running AI
    existing, _ = OnboardingSession.objects.get_or_create(user=request.user)
    if existing.status == OnboardingSession.Status.COMPLETED and request.user.is_onboarded:
        from apps.accounts.views import _issue_tokens
        access, refresh = _issue_tokens(request.user)
        return Response({
            'detail': 'Already completed.',
            'onboarding_summary': existing.onboarding_summary,
            'access': access,
            'refresh': refresh,
        })

    messages = list(
        ChatMessage.objects.filter(session=chat_session)
        .order_by('created_at')
        .values('role', 'content')
    )
    profile = extract_profile_from_chat(messages, request.user.role)
    if not profile or not isinstance(profile, dict):
        return Response(
            {'detail': 'Could not extract a profile from your chat. Please try again.'},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )

    # Guard: if key fields are still "unknown" the profile is too sparse to generate a good
    # roadmap. Return a targeted follow-up question so the frontend can re-prompt the user
    # instead of saving an incomplete profile.
    _REQUIRED_FIELDS = ['recommended_path', 'career_goal', 'experience_level']
    _BAD_VALUES = {'unknown', 'not specified', 'undecided', ''}
    _FOLLOWUP_QUESTIONS = {
        'recommended_path': (
            'What career path interests you most? '
            '(e.g. Web Development, Data Science, Cybersecurity, Mobile App Development)'
        ),
        'career_goal': (
            "What's your goal — what kind of job do you want after graduating?"
        ),
        'experience_level': (
            'How would you describe your programming experience so far? '
            '(beginner, basic, intermediate, or experienced)'
        ),
    }
    incomplete_fields = [
        f for f in _REQUIRED_FIELDS
        if not profile.get(f) or str(profile[f]).lower().strip() in _BAD_VALUES
    ]
    if incomplete_fields:
        first_missing = incomplete_fields[0]
        return Response(
            {
                'error': 'profile_incomplete',
                'missing_fields': incomplete_fields,
                'followup_question': _FOLLOWUP_QUESTIONS[first_missing],
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    with transaction.atomic():
        existing.onboarding_summary = profile
        existing.status = OnboardingSession.Status.COMPLETED
        existing.completed_at = timezone.now()
        existing.save()

        # Upgrade role to incoming_student if chat reveals a fresh SHS / pre-college student
        background = (profile.get('background') or '').lower()
        year_level_extracted = (profile.get('year_level') or '').lower()
        incoming_keywords = (
            'shs', 'senior high', 'incoming', 'pre-college', 'pre college',
            'fresh grad', 'bagong grad', 'grade 11', 'grade 12', 'gr. 11', 'gr. 12',
            'g11', 'g12', 'currently in shs', 'still in shs', 'still studying in shs',
        )
        update_fields = ['is_onboarded']
        if (
            any(kw in background for kw in incoming_keywords) or year_level_extracted == 'incoming'
        ) and request.user.role == 'undergraduate':
            request.user.role = 'incoming_student'
            update_fields.append('role')

        request.user.is_onboarded = True
        request.user.save(update_fields=update_fields)

        # Sync year_level and program from the extracted profile to StudentProfile.
        # Only meaningful for undergrads — incoming students are always 'undecided' on program.
        if request.user.role == 'undergraduate':
            try:
                sp = request.user.student_profile
                sp_fields = []
                raw_year = (profile.get('year_level') or '').strip()
                year_map = {
                    '1st': '1st', 'first': '1st', '1': '1st',
                    '2nd': '2nd', 'second': '2nd', '2': '2nd',
                    '3rd': '3rd', 'third': '3rd', '3': '3rd',
                    '4th': '4th', 'fourth': '4th', '4': '4th',
                    '5th': '5th', 'fifth': '5th', '5': '5th',
                    'shifter': '1st',
                    'transferee': '1st',
                }
                mapped_year = year_map.get(raw_year.lower())
                if mapped_year:
                    sp.year_level = mapped_year
                    sp_fields.append('year_level')
                raw_program = (profile.get('program') or '').strip().upper()
                if raw_program in {'BSCS', 'BSIT', 'BSIS', 'BSCE', 'ACT'}:
                    sp.program = raw_program
                    sp_fields.append('program')
                if sp_fields:
                    sp.save(update_fields=sp_fields)
            except Exception:
                logger.exception('Failed to sync StudentProfile for user %s', request.user.pk)

    award_xp(request.user, 'onboarding_completed', existing.id, 'Completed the onboarding chat!', unique_per_user=True)

    # Issue fresh tokens with all custom claims so the frontend JWT reflects is_onboarded=True
    from apps.accounts.views import _issue_tokens
    access, refresh = _issue_tokens(request.user)

    return Response({
        'detail': 'Onboarding complete!',
        'onboarding_summary': profile,
        'access': access,
        'refresh': refresh,
        'xp_earned': 75,
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
            'onboarding_summary': session.onboarding_summary or {},
        })
    except OnboardingSession.DoesNotExist:
        return Response({'status': 'not_started', 'is_onboarded': False, 'onboarding_summary': {}})

"""Mentorship request, session, and review views."""
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.accounts.permissions import IsStudent, IsMentor
from apps.gamification.engine import award_xp
from .matching import compute_compatibility
from .models import MentorshipRequest, MentorshipSession, MentorReview
from .serializers import MentorshipRequestSerializer, MentorshipSessionSerializer, MentorReviewSerializer


class MentorshipRequestListCreateView(generics.ListCreateAPIView):
    """
    GET  /api/mentorship/requests/ — List requests (student sees sent, mentor sees received)
    POST /api/mentorship/requests/ — Student sends a mentorship request
    """
    serializer_class = MentorshipRequestSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        if user.role == 'mentor':
            return MentorshipRequest.objects.filter(mentor=user)
        return MentorshipRequest.objects.filter(student=user)

    def perform_create(self, serializer):
        student = self.request.user
        mentor_user = serializer.validated_data['mentor']

        # Compute compatibility score
        try:
            mentor_profile = mentor_user.mentor_profile
            student_profile = student.student_profile
            score = compute_compatibility(student_profile, mentor_profile)
        except Exception:
            score = 0.0

        serializer.save(student=student, compatibility_score=score)
        award_xp(student, 'mentor_requests', description='Sent a mentorship request!')


@api_view(['PATCH'])
@permission_classes([IsMentor])
def respond_to_request(request, pk):
    """PATCH /api/mentorship/requests/{pk}/ — Mentor accepts or declines."""
    try:
        req = MentorshipRequest.objects.get(pk=pk, mentor=request.user, status='pending')
    except MentorshipRequest.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    new_status = request.data.get('status')
    if new_status not in ('accepted', 'declined'):
        return Response({'detail': 'Status must be "accepted" or "declined".'}, status=400)

    req.status = new_status
    req.mentor_response = request.data.get('mentor_response', '')
    req.responded_at = timezone.now()
    req.save()

    if new_status == 'accepted':
        # Update mentor's mentee count
        try:
            mentor_profile = request.user.mentor_profile
            mentor_profile.current_mentees_count += 1
            mentor_profile.save(update_fields=['current_mentees_count'])
        except Exception:
            pass

    return Response(MentorshipRequestSerializer(req).data)


class SessionListCreateView(generics.ListCreateAPIView):
    """GET/POST /api/mentorship/sessions/"""
    serializer_class = MentorshipSessionSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        if user.role == 'mentor':
            return MentorshipSession.objects.filter(request__mentor=user)
        return MentorshipSession.objects.filter(request__student=user)


@api_view(['POST'])
@permission_classes([IsStudent])
def submit_review(request, session_pk):
    """POST /api/mentorship/sessions/{pk}/review/"""
    try:
        session = MentorshipSession.objects.get(pk=session_pk, request__student=request.user)
    except MentorshipSession.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    serializer = MentorReviewSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    review = serializer.save(session=session, reviewer=request.user)

    # Update mentor's average rating
    mentor_user = session.request.mentor
    try:
        mentor_profile = mentor_user.mentor_profile
        all_reviews = MentorReview.objects.filter(session__request__mentor=mentor_user)
        total = all_reviews.count()
        avg = sum(r.rating for r in all_reviews) / total if total else 0
        mentor_profile.avg_rating = round(avg, 2)
        mentor_profile.total_reviews = total
        mentor_profile.save(update_fields=['avg_rating', 'total_reviews'])
    except Exception:
        pass

    # Award XP for completing a mentorship session
    award_xp(request.user, 'mentor_session', session.id, 'Completed a mentorship session!')

    return Response(MentorReviewSerializer(review).data, status=status.HTTP_201_CREATED)

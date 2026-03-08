"""Roadmap views — generation, retrieval, node progress."""
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.accounts.permissions import IsStudent
from apps.gamification.engine import award_xp
from .models import Roadmap, RoadmapNode
from .serializers import RoadmapSerializer, RoadmapListSerializer, RoadmapNodeSerializer


class RoadmapListView(generics.ListAPIView):
    """GET /api/roadmaps/ — List user's roadmaps."""
    serializer_class = RoadmapListSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Roadmap.objects.filter(user=self.request.user)


class RoadmapDetailView(generics.RetrieveAPIView):
    """GET /api/roadmaps/{id}/ — Full roadmap with all nodes and resources."""
    serializer_class = RoadmapSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Roadmap.objects.filter(user=self.request.user).prefetch_related(
            'nodes__resources'
        )


@api_view(['POST'])
@permission_classes([IsStudent])
def generate_roadmap(request):
    """
    POST /api/roadmaps/generate/
    Triggers AI roadmap generation using the student's onboarding quiz summary.
    """
    from apps.ai_assistant.groq_client import generate_roadmap as ai_generate
    from .generators import save_roadmap_from_ai

    user = request.user

    # Get the onboarding quiz summary
    try:
        quiz_summary = user.onboarding_session.quiz_summary
        if not quiz_summary:
            return Response(
                {'detail': 'Please complete the onboarding quiz first.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
    except Exception:
        return Response(
            {'detail': 'Onboarding session not found. Please complete the quiz first.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    # Create a placeholder roadmap with 'generating' status
    roadmap = Roadmap.objects.create(
        user=user,
        title='Generating your personalized roadmap...',
        career_path='pending',
        status=Roadmap.Status.GENERATING,
    )

    try:
        ai_data = ai_generate(quiz_summary)
        save_roadmap_from_ai(roadmap, ai_data)
        award_xp(user, 'roadmap_generated', roadmap.id, 'Generated your first personalized roadmap!')
    except Exception as e:
        roadmap.delete()
        return Response(
            {'detail': f'Roadmap generation failed: {str(e)}'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    return Response(RoadmapSerializer(roadmap).data, status=status.HTTP_201_CREATED)


@api_view(['PATCH'])
@permission_classes([permissions.IsAuthenticated])
def update_node_status(request, roadmap_pk, node_pk):
    """
    PATCH /api/roadmaps/{id}/nodes/{nid}/
    Update a node's status (e.g., mark as in_progress or completed).
    """
    try:
        node = RoadmapNode.objects.get(
            pk=node_pk,
            roadmap__pk=roadmap_pk,
            roadmap__user=request.user,
        )
    except RoadmapNode.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    new_status = request.data.get('status')
    valid_statuses = [s.value for s in RoadmapNode.Status]
    if new_status not in valid_statuses:
        return Response({'detail': f'Invalid status. Must be one of: {valid_statuses}'}, status=400)

    node.status = new_status
    if new_status == 'completed' and not node.completed_at:
        node.completed_at = timezone.now()
        # Award XP for completing the node
        award_xp(
            request.user,
            'node_completed',
            node.id,
            f'Completed: {node.title}',
            xp_override=node.xp_reward,
        )
        # Update roadmap completion %
        node.roadmap.recalculate_completion()

    node.save()
    return Response(RoadmapNodeSerializer(node).data)

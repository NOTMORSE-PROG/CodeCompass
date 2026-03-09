"""Roadmap views — generation, retrieval, node progress."""
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.accounts.permissions import IsStudent
from apps.gamification.engine import award_xp
from .models import Roadmap, RoadmapNode, NodeResource, AssessmentSession
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
    Triggers AI roadmap generation using the student's onboarding summary.
    """
    from apps.ai_assistant.groq_client import generate_roadmap as ai_generate
    from .generators import save_roadmap_from_ai

    user = request.user

    # Get the onboarding summary (captured from the onboarding chat)
    try:
        onboarding_summary = user.onboarding_session.onboarding_summary
        if not onboarding_summary:
            return Response(
                {'detail': 'Please complete the onboarding first.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
    except Exception:
        return Response(
            {'detail': 'Onboarding session not found. Please complete the onboarding first.'},
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
        ai_data = ai_generate(onboarding_summary)
        save_roadmap_from_ai(roadmap, ai_data)
        award_xp(user, 'roadmap_generated', roadmap.id, 'Generated your first personalized roadmap!')
    except Exception as e:
        roadmap.delete()
        return Response(
            {'detail': f'Roadmap generation failed: {str(e)}'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    return Response(RoadmapSerializer(roadmap).data, status=status.HTTP_201_CREATED)


def _unlock_children(node):
    """
    Unlock locked children of a completed node.
    Milestone children are auto-completed (they're phase markers, not tasks)
    and their children are unlocked recursively.
    """
    children = RoadmapNode.objects.filter(parent_node=node, status='locked')
    for child in children:
        if child.node_type == 'milestone':
            child.status = 'completed'
            child.completed_at = timezone.now()
            child.save(update_fields=['status', 'completed_at'])
            _unlock_children(child)
        else:
            child.status = 'available'
            child.save(update_fields=['status'])


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def repair_roadmap(request, roadmap_pk):
    """
    POST /api/roadmaps/{id}/repair/
    Fixes existing roadmaps where all nodes ended up locked.
    Auto-completes milestone nodes and ensures the first skill node is available.
    """
    try:
        roadmap = Roadmap.objects.get(pk=roadmap_pk, user=request.user)
    except Roadmap.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    nodes = list(roadmap.nodes.order_by('node_order'))
    if not nodes:
        return Response({'detail': 'No nodes found.'}, status=status.HTTP_400_BAD_REQUEST)

    now = timezone.now()
    # Only fix the very first node (Phase 1 milestone header)
    first_node = nodes[0]
    if first_node.node_type == 'milestone' and first_node.status in ('locked', 'available'):
        first_node.status = 'completed'
        first_node.completed_at = now
        first_node.save(update_fields=['status', 'completed_at'])

    # Ensure the first non-milestone node is available
    first_skill = next((n for n in nodes if n.node_type != 'milestone'), None)
    if first_skill and first_skill.status == 'locked':
        first_skill.status = 'available'
        first_skill.save(update_fields=['status'])

    roadmap.recalculate_completion()
    from .serializers import RoadmapSerializer
    return Response(RoadmapSerializer(roadmap).data)


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def fetch_node_resources(request, roadmap_pk, node_pk):
    """
    POST /api/roadmaps/{id}/nodes/{nid}/fetch-resources/
    Lazy-populate YouTube video IDs for a node's placeholder resources.
    Called when the student expands a node and resources have no video ID.
    """
    try:
        node = RoadmapNode.objects.get(
            pk=node_pk,
            roadmap__pk=roadmap_pk,
            roadmap__user=request.user,
        )
    except RoadmapNode.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    from apps.resources.youtube_client import populate_node_resources
    from .serializers import NodeResourceSerializer
    populate_node_resources(node)
    resources = node.resources.all()
    return Response(NodeResourceSerializer(resources, many=True).data)


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

    # Gate: require passing all video quizzes before marking completed
    if new_status == 'completed':
        youtube_resources = node.resources.filter(
            resource_type='youtube_video'
        ).exclude(url__in=['', 'yt:unavailable'])
        if youtube_resources.exists():
            passed_ids = AssessmentSession.objects.filter(
                user=request.user,
                resource__in=youtube_resources,
                passed=True,
            ).values_list('resource_id', flat=True)
            unpassed = youtube_resources.exclude(id__in=passed_ids)
            if unpassed.exists():
                return Response(
                    {'detail': 'Pass all video quizzes first.'},
                    status=status.HTTP_400_BAD_REQUEST,
                )

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
        # Unlock child nodes, auto-completing any milestone children
        _unlock_children(node)

    node.save()
    node.refresh_from_db()
    return Response({
        'node': RoadmapNodeSerializer(node).data,
        'completionPercentage': str(node.roadmap.completion_percentage),
    })


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def start_assessment(request, roadmap_pk, node_pk, resource_pk):
    """
    POST /api/roadmaps/{id}/nodes/{nid}/resources/{rid}/assessment/
    Generate a fresh set of 4 MCQ questions for a YouTube resource.
    Returns session_id and questions (without correct answers).
    """
    try:
        resource = NodeResource.objects.get(
            pk=resource_pk,
            node__pk=node_pk,
            node__roadmap__pk=roadmap_pk,
            node__roadmap__user=request.user,
        )
    except NodeResource.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    node = resource.node
    user_role = getattr(request.user, 'role', 'undergraduate')

    # Program and year_level are only meaningful for enrolled undergrads.
    # Incoming students haven't chosen a program yet — treat as undecided.
    program = 'undecided'
    year_level = ''
    if user_role == 'undergraduate':
        try:
            sp = request.user.student_profile
            program = sp.program or 'undecided'
            year_level = sp.year_level or ''
        except Exception:
            pass

    from apps.ai_assistant.groq_client import generate_video_assessment
    try:
        questions = generate_video_assessment(
            resource.title, node.title, node.description,
            difficulty=node.difficulty or 2,
            user_role=user_role,
            career_path=node.roadmap.career_path,
            program=program,
            year_level=year_level,
            youtube_video_id=resource.youtube_video_id or '',
        )
    except Exception as e:
        return Response(
            {'detail': f'Failed to generate quiz: {str(e)}'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    session = AssessmentSession.objects.create(
        user=request.user,
        resource=resource,
        questions_json=questions,
    )

    # Strip correct answers before returning to client
    client_questions = [
        {
            'question': q['question'],
            'options': q['options'],
        }
        for q in questions
    ]

    return Response({'sessionId': session.id, 'questions': client_questions})


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def submit_assessment(request, roadmap_pk, node_pk, resource_pk, session_pk):
    """
    POST /api/roadmaps/{id}/nodes/{nid}/resources/{rid}/assessment/{session_id}/submit/
    Score submitted answers against the stored session.
    Returns pass/fail, score, and per-question explanations.
    """
    try:
        session = AssessmentSession.objects.get(
            pk=session_pk,
            user=request.user,
            resource__pk=resource_pk,
            resource__node__pk=node_pk,
            resource__node__roadmap__pk=roadmap_pk,
        )
    except AssessmentSession.DoesNotExist:
        return Response({'detail': 'Session not found.'}, status=status.HTTP_404_NOT_FOUND)

    if session.passed is not None:
        return Response({'detail': 'Session already submitted.'}, status=status.HTTP_400_BAD_REQUEST)

    answers = request.data.get('answers', {})
    questions = session.questions_json
    correct_count = 0
    results = []

    for i, q in enumerate(questions):
        submitted = answers.get(str(i))
        is_correct = submitted == q['correct']
        if is_correct:
            correct_count += 1
        results.append({
            'correct': is_correct,
            'yourAnswer': submitted,
            'correctAnswer': q['correct'],
            'explanation': q.get('explanation', ''),
        })

    total = len(questions)
    score = round((correct_count / total) * 100) if total else 0

    # Research-backed pass thresholds by topic and difficulty:
    # - NTNU mastery learning (introductory programming): 75%
    # - PMC systematic review (formative e-learning): 70–75% standard
    # - ACM TOCE / Stankov 2023: lower threshold for high-cognitive-load topics
    # - MDPI 2021: 75% for security/networking (high-stakes domain)
    node = session.resource.node
    title_lower = node.title.lower()
    difficulty = node.difficulty or 2
    user_role = getattr(session.user, 'role', 'undergraduate')

    if any(k in title_lower for k in (
        'automata', 'formal language', 'compiler', 'computability', 'turing machine',
        'complexity theory', 'np-complete', 'lambda calculus',
    )):
        # Abstract/theoretical CS — highest cognitive load; partial mastery is meaningful
        base_threshold = 62
    elif any(k in title_lower for k in (
        'machine learning', 'deep learning', 'neural network', 'natural language processing',
        'computer vision', 'reinforcement learning', 'data science', 'statistics',
        'probability', 'linear regression', 'logistic regression',
    )):
        # Data science / ML — statistical ambiguity makes 100% correctness unrealistic
        base_threshold = 65
    elif any(k in title_lower for k in (
        'dynamic programming', 'graph algorithm', 'network flow', 'minimum spanning tree',
        'advanced algorithm', 'computational geometry',
    )):
        # Advanced DSA — complex reasoning required
        base_threshold = 68
    elif any(k in title_lower for k in (
        'security', 'network', 'hacking', 'cyber', 'penetration', 'firewall',
        'threat', 'exploit', 'malware', 'forensic', 'cryptography', 'ethical hacking',
    )):
        # Security / networking — scenario-based; real-world consequences
        base_threshold = 72
    else:
        # Standard topics: web dev, databases, mobile, game dev, devops, backend, etc.
        base_threshold = 75

    # Incoming students: diagnostic tool, not a gatekeeping mechanism — much lower bar
    if user_role == 'incoming_student':
        base_threshold = max(60, base_threshold - 12)
    else:
        # Year 1 students are still in foundation phase — drop threshold slightly
        try:
            yl = session.user.student_profile.year_level or ''
            if yl in ('1st', 'incoming'):
                base_threshold = max(62, base_threshold - 5)
        except Exception:
            pass
        # High difficulty nodes (4–5) use Evaluate-level questions — apply tolerance
        if difficulty >= 4:
            base_threshold = max(62, base_threshold - 5)

    pass_threshold = base_threshold
    passed = score >= pass_threshold

    session.passed = passed
    session.score = score
    session.save(update_fields=['passed', 'score'])

    return Response({
        'passed': passed,
        'score': score,
        'passThreshold': pass_threshold,
        'correctCount': correct_count,
        'totalQuestions': len(questions),
        'results': results,
    })

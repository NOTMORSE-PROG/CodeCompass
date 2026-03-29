"""Roadmap views — generation, retrieval, node progress."""
from django.db import models, transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from apps.accounts.permissions import IsStudent
from apps.gamification.engine import award_xp
from .models import Roadmap, RoadmapNode, NodeResource, AssessmentSession, VideoWatchUnlock
from .serializers import RoadmapSerializer, RoadmapListSerializer, RoadmapNodeSerializer

# ---------------------------------------------------------------------------
# Coercion helpers — used when AI sends string difficulty words or non-int values
# ---------------------------------------------------------------------------
_DIFFICULTY_WORDS = {
    'very easy': 1, 'easy': 1, 'beginner': 1, 'low': 1, 'novice': 1,
    'medium': 2, 'moderate': 2, 'normal': 2,
    'intermediate': 3, 'average': 3,
    'hard': 4, 'difficult': 4, 'challenging': 4,
    'very hard': 5, 'expert': 5, 'advanced': 5, 'extreme': 5,
}


def _to_int(val, default, min_val=1, max_val=None):
    """Coerce val to int. Maps difficulty word strings. Clamps to [min_val, max_val]."""
    if val is None:
        return default
    if isinstance(val, str):
        mapped = _DIFFICULTY_WORDS.get(val.lower().strip())
        if mapped is not None:
            val = mapped
        else:
            try:
                val = int(val)
            except ValueError:
                return default
    try:
        result = int(val)
        if max_val is not None:
            result = max(min_val, min(max_val, result))
        else:
            result = max(min_val, result)
        return result
    except (ValueError, TypeError):
        return default


_VALID_NODE_TYPES = {'skill', 'assessment', 'project', 'certification'}
_NODE_TYPE_ALIASES = {
    'core_skill': 'skill', 'hard_skill': 'skill', 'knowledge': 'skill',
    'topic': 'skill', 'lesson': 'skill', 'skill_node': 'skill',
    'hands_on': 'project', 'build': 'project', 'capstone': 'project',
    'cert': 'certification', 'certificate': 'certification',
    'quiz': 'assessment', 'test': 'assessment',
}


def _coerce_node_type(val):
    """Coerce AI-generated node_type strings to valid model choices. Defaults to 'skill'."""
    if not val:
        return 'skill'
    v = str(val).lower().strip().replace(' ', '_').replace('-', '_')
    if v in _VALID_NODE_TYPES:
        return v
    return _NODE_TYPE_ALIASES.get(v, 'skill')


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

    # Block if user already has a live roadmap
    if Roadmap.objects.filter(user=user, status=Roadmap.Status.ACTIVE).exists():
        return Response(
            {'detail': 'You already have an active roadmap.'},
            status=status.HTTP_400_BAD_REQUEST,
        )
    # Clean up any stuck GENERATING roadmaps (e.g., from a previous failed attempt)
    Roadmap.objects.filter(user=user, status=Roadmap.Status.GENERATING).delete()

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
        award_xp(user, 'roadmap_generated', roadmap.id, 'Generated your first personalized roadmap!', unique_per_user=True)
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
def fix_structure(request, roadmap_pk):
    """
    POST /api/roadmaps/{id}/fix-structure/
    Enforces phase purity: cert phases → certs only, project phases → projects only.
    Dynamic: works for any number of phases — no hardcoded phase names or positions.
    Idempotent: calling on an already-correct roadmap changes nothing.
    """
    try:
        roadmap = Roadmap.objects.get(pk=roadmap_pk, user=request.user)
    except Roadmap.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    nodes = list(roadmap.nodes.order_by('node_order'))
    phases, current = [], {'milestone': None, 'nodes': []}
    for node in nodes:
        if node.node_type == 'milestone':
            phases.append(current)
            current = {'milestone': node, 'nodes': []}
        else:
            current['nodes'].append(node)
    phases.append(current)

    TYPE_ORDER = {'skill': 0, 'assessment': 0, 'project': 1, 'certification': 2}

    # Work backwards so cascading fixes resolve in a single pass
    for i in range(len(phases) - 1, 0, -1):
        phase_nodes = phases[i]['nodes']
        has_cert    = any(n.node_type == 'certification' for n in phase_nodes)
        has_project = any(n.node_type == 'project' for n in phase_nodes)

        if has_cert:
            stray = [n for n in phase_nodes if n.node_type != 'certification']
            if stray:
                phases[i - 1]['nodes'].extend(stray)
                phases[i]['nodes'] = [n for n in phase_nodes if n.node_type == 'certification']
        elif has_project:
            stray = [n for n in phase_nodes if n.node_type in ('skill', 'assessment')]
            if stray:
                phases[i - 1]['nodes'].extend(stray)
                phases[i]['nodes'] = [n for n in phase_nodes if n.node_type not in ('skill', 'assessment')]

    for phase in phases:
        phase['nodes'].sort(key=lambda n: TYPE_ORDER.get(n.node_type, 0))

    flat = []
    for phase in phases:
        if phase['milestone']:
            flat.append(phase['milestone'])
        flat.extend(phase['nodes'])

    for i, node in enumerate(flat):
        if node.node_order != i:
            node.node_order = i
            node.save(update_fields=['node_order'])

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
    new_status = request.data.get('status')
    valid_statuses = [s.value for s in RoadmapNode.Status]
    if new_status not in valid_statuses:
        return Response({'detail': f'Invalid status. Must be one of: {valid_statuses}'}, status=400)

    xp_awarded = False

    with transaction.atomic():
        # Lock the node row so concurrent requests queue behind this one instead of racing.
        try:
            node = RoadmapNode.objects.select_for_update().get(
                pk=node_pk,
                roadmap__pk=roadmap_pk,
                roadmap__user=request.user,
            )
        except RoadmapNode.DoesNotExist:
            return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

        # Validate state transition — only available/in_progress nodes can be advanced.
        # Locked nodes cannot be interacted with, and completed nodes cannot be reverted.
        _VALID_TRANSITIONS = {
            'available': {'in_progress', 'completed'},
            'in_progress': {'in_progress', 'completed'},
        }
        if node.status not in _VALID_TRANSITIONS or new_status not in _VALID_TRANSITIONS[node.status]:
            return Response(
                {'detail': f'Cannot transition node from "{node.status}" to "{new_status}".'},
                status=status.HTTP_400_BAD_REQUEST,
            )

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
            # Save first so the second concurrent request (waiting on the lock) sees
            # completed_at already set and skips the XP block entirely.
            node.save()
            from apps.gamification.models import XPEvent as _XPEvent
            if not _XPEvent.objects.filter(
                user=request.user, event_type='node_completed', reference_id=node.id
            ).exists():
                award_xp(
                    request.user,
                    'node_completed',
                    node.id,
                    f'Completed: {node.title}',
                    xp_override=node.xp_reward,
                )
                xp_awarded = True
            # Update roadmap completion %
            node.roadmap.recalculate_completion()
            # Unlock child nodes, auto-completing any milestone children
            _unlock_children(node)
        else:
            node.save()

    node.refresh_from_db()
    return Response({
        'node': RoadmapNodeSerializer(node).data,
        'completionPercentage': str(node.roadmap.completion_percentage),
        'xpAwarded': xp_awarded,
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


# ---------------------------------------------------------------------------
# AI-assisted roadmap content editing
# ---------------------------------------------------------------------------

@api_view(['PATCH'])
@permission_classes([permissions.IsAuthenticated])
def edit_roadmap_meta(request, roadmap_pk):
    """
    PATCH /api/roadmaps/{id}/edit/
    Edit roadmap metadata: title, description, estimated_weeks.
    Called when the AI proposes a roadmap-level change and the user clicks Apply.
    """
    roadmap = get_object_or_404(Roadmap, pk=roadmap_pk, user=request.user)
    allowed = {'title', 'description', 'estimated_weeks'}
    for field in allowed & set(request.data.keys()):
        setattr(roadmap, field, request.data[field])
    roadmap.save()
    return Response(RoadmapSerializer(roadmap).data)


@api_view(['PATCH'])
@permission_classes([permissions.IsAuthenticated])
def edit_node_content(request, roadmap_pk, node_pk):
    """
    PATCH /api/roadmaps/{id}/nodes/{nid}/edit/
    Edit node content: title, description, estimated_hours, difficulty.
    Does NOT touch node status or progress — only textual/structural metadata.
    """
    node = get_object_or_404(
        RoadmapNode, pk=node_pk, roadmap__pk=roadmap_pk, roadmap__user=request.user
    )
    allowed = {'title', 'description', 'estimated_hours', 'difficulty'}
    # node_type excluded — changing type would break phase structure
    COERCE_INT = {
        'estimated_hours': lambda v: _to_int(v, default=5, min_val=1),
        'difficulty':      lambda v: _to_int(v, default=2, min_val=1, max_val=5),
    }
    for field in allowed & set(request.data.keys()):
        val = request.data[field]
        setattr(node, field, COERCE_INT[field](val) if field in COERCE_INT else val)
    node.save()
    return Response(RoadmapNodeSerializer(node).data)


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def add_roadmap_node(request, roadmap_pk):
    """
    POST /api/roadmaps/{id}/nodes/add/
    Insert a new node into the roadmap. New nodes are locked by default.
    Optionally set parent_node to after_node_id for ordering context.
    """
    roadmap = get_object_or_404(Roadmap, pk=roadmap_pk, user=request.user)
    last_order = roadmap.nodes.aggregate(m=models.Max('node_order'))['m'] or 0
    node = RoadmapNode.objects.create(
        roadmap=roadmap,
        title=request.data.get('title', 'New Node'),
        description=request.data.get('description', ''),
        node_type=_coerce_node_type(request.data.get('node_type')),
        estimated_hours=_to_int(request.data.get('estimated_hours'), default=5, min_val=1),
        difficulty=_to_int(request.data.get('difficulty'), default=2, min_val=1, max_val=5),
        status=RoadmapNode.Status.LOCKED,
        node_order=last_order + 1,
    )
    after_id = request.data.get('after_node_id')
    if after_id:
        try:
            anchor = roadmap.nodes.get(pk=after_id)
            node.parent_node = anchor
            node.save(update_fields=['parent_node'])
        except RoadmapNode.DoesNotExist:
            pass
    roadmap.recalculate_completion()
    return Response(RoadmapNodeSerializer(node).data, status=status.HTTP_201_CREATED)


@api_view(['DELETE'])
@permission_classes([permissions.IsAuthenticated])
def remove_roadmap_node(request, roadmap_pk, node_pk):
    """
    DELETE /api/roadmaps/{id}/nodes/{nid}/remove/
    Remove a node from the roadmap and recalculate completion %.
    """
    node = get_object_or_404(
        RoadmapNode, pk=node_pk, roadmap__pk=roadmap_pk, roadmap__user=request.user
    )
    if node.node_type == 'milestone':
        return Response({'detail': 'Cannot remove phase milestone nodes.'}, status=status.HTTP_400_BAD_REQUEST)
    if node.status == 'completed':
        return Response({'detail': 'Cannot remove a completed node.'}, status=status.HTTP_400_BAD_REQUEST)
    node.delete()
    roadmap = Roadmap.objects.get(pk=roadmap_pk)
    roadmap.recalculate_completion()
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def unlock_video_watch(request, roadmap_pk, node_pk, resource_pk):
    """
    POST /api/roadmaps/{id}/nodes/{nid}/resources/{rid}/unlock/
    Record that the user has watched enough video (≥5 min) to unlock the quiz.
    Idempotent — safe to call multiple times.
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

    VideoWatchUnlock.objects.get_or_create(user=request.user, resource=resource)
    return Response({'unlocked': True})


@api_view(['POST'])
@permission_classes([IsStudent])
def switch_roadmap(request):
    """
    POST /api/roadmaps/switch/
    Archives the current active roadmap and generates a new one for the given path/goal.
    Body: { "roadmap_id": <int>, "new_path": "Data Science", "career_goal": "data analyst" }
    Rate-limited to one switch per calendar day (PH timezone).
    """
    from apps.ai_assistant.groq_client import generate_roadmap as ai_generate
    from .generators import save_roadmap_from_ai
    from slugify import slugify
    from django.utils import timezone as tz
    import zoneinfo

    user = request.user
    roadmap_id = request.data.get('roadmap_id')
    new_path = (request.data.get('new_path') or '').strip()
    career_goal = (request.data.get('career_goal') or '').strip()

    if not all([roadmap_id, new_path, career_goal]):
        return Response({'detail': 'roadmap_id, new_path, and career_goal are required.'}, status=400)

    # Verify ownership and active status
    try:
        old_roadmap = Roadmap.objects.get(pk=roadmap_id, user=user, status=Roadmap.Status.ACTIVE)
    except Roadmap.DoesNotExist:
        return Response({'detail': 'Active roadmap not found.'}, status=404)

    # Rate limit: one switch per calendar day (PH timezone = UTC+8)
    ph_tz = zoneinfo.ZoneInfo('Asia/Manila')
    today_ph = tz.now().astimezone(ph_tz).date()
    already_switched_today = Roadmap.objects.filter(
        user=user,
        status=Roadmap.Status.ARCHIVED,
        updated_at__date=today_ph,
    ).exists()
    if already_switched_today:
        return Response(
            {'detail': 'You can only switch your learning path once per day. Please try again tomorrow.'},
            status=429,
        )

    # Build updated summary from existing onboarding data
    try:
        summary = dict(user.onboarding_session.onboarding_summary or {})
    except Exception:
        return Response({'detail': 'Onboarding session not found.'}, status=400)

    summary['recommended_path'] = new_path
    summary['career_goal'] = career_goal
    summary['recommended_path_slug'] = slugify(new_path, separator='_')

    # Archive the old roadmap
    # NOTE: 'updated_at' must be explicitly included — auto_now does NOT fire when using update_fields
    old_roadmap.status = Roadmap.Status.ARCHIVED
    old_roadmap.is_primary = False
    old_roadmap.updated_at = tz.now()
    old_roadmap.save(update_fields=['status', 'is_primary', 'updated_at'])

    # Clean up any stuck GENERATING roadmaps
    Roadmap.objects.filter(user=user, status=Roadmap.Status.GENERATING).delete()

    # Create placeholder for new roadmap
    new_roadmap = Roadmap.objects.create(
        user=user,
        title='Generating your new roadmap...',
        career_path='pending',
        status=Roadmap.Status.GENERATING,
        is_primary=True,
    )

    try:
        ai_data = ai_generate(summary)
        save_roadmap_from_ai(new_roadmap, ai_data)
        # Persist updated summary so future generations use the new path
        user.onboarding_session.onboarding_summary = summary
        user.onboarding_session.save(update_fields=['onboarding_summary'])
    except Exception as e:
        new_roadmap.delete()
        # Restore old roadmap — also reset updated_at so the rate-limit counter isn't consumed on failure
        old_roadmap.status = Roadmap.Status.ACTIVE
        old_roadmap.is_primary = True
        old_roadmap.updated_at = tz.now()
        old_roadmap.save(update_fields=['status', 'is_primary', 'updated_at'])
        return Response({'detail': f'Roadmap generation failed: {str(e)}'}, status=500)

    return Response(RoadmapSerializer(new_roadmap).data, status=201)

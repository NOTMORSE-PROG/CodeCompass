"""
Parses the AI-generated roadmap JSON and saves it to the database.
Called after a successful Groq API response.
"""
from django.utils import timezone

from .models import Roadmap, RoadmapNode, NodeResource


_DIFF_WORDS = {
    'very easy': 1, 'easy': 1, 'beginner': 1, 'low': 1, 'novice': 1,
    'medium': 2, 'moderate': 2, 'normal': 2,
    'intermediate': 3, 'average': 3,
    'hard': 4, 'difficult': 4, 'challenging': 4,
    'very hard': 5, 'expert': 5, 'advanced': 5, 'extreme': 5,
}


def _coerce_difficulty(v):
    """Coerce AI-generated difficulty to int 1-5. Handles strings like 'hard', 'medium'."""
    if isinstance(v, (int, float)):
        return max(1, min(5, int(v)))
    try:
        return max(1, min(5, int(v)))
    except (ValueError, TypeError):
        return max(1, min(5, _DIFF_WORDS.get(str(v).lower().strip(), 1)))


_TYPE_ORDER = {'skill': 0, 'assessment': 0, 'project': 1, 'certification': 2}


def _fix_phase_structure(nodes_data):
    """
    Enforces phase purity: cert phases contain ONLY certifications,
    project phases contain ONLY projects.
    Works backwards so cascading fixes resolve in a single pass
    (e.g. skill in Phase 4 → Phase 3 → Phase 2 without needing multiple runs).
    """
    phases, current = [], {'milestone': None, 'nodes': []}
    for node in nodes_data:
        if node.get('node_type') == 'milestone':
            phases.append(current)
            current = {'milestone': node, 'nodes': []}
        else:
            current['nodes'].append(node)
    phases.append(current)

    for i in range(len(phases) - 1, 0, -1):
        phase_nodes = phases[i]['nodes']
        has_cert    = any(n.get('node_type') == 'certification' for n in phase_nodes)
        has_project = any(n.get('node_type') == 'project' for n in phase_nodes)

        if has_cert:
            # Cert phase — move every non-cert node to the previous phase
            stray = [n for n in phase_nodes if n.get('node_type') != 'certification']
            if stray:
                phases[i - 1]['nodes'].extend(stray)
                phases[i]['nodes'] = [n for n in phase_nodes if n.get('node_type') == 'certification']
        elif has_project:
            # Project phase — move any skill/assessment nodes to the previous phase
            stray = [n for n in phase_nodes if n.get('node_type') in ('skill', 'assessment')]
            if stray:
                phases[i - 1]['nodes'].extend(stray)
                phases[i]['nodes'] = [n for n in phase_nodes if n.get('node_type') not in ('skill', 'assessment')]

    for phase in phases:
        phase['nodes'].sort(key=lambda n: _TYPE_ORDER.get(n.get('node_type', 'skill'), 0))

    result = []
    for phase in phases:
        if phase['milestone']:
            result.append(phase['milestone'])
        result.extend(phase['nodes'])
    return result


def save_roadmap_from_ai(roadmap: Roadmap, ai_data: dict) -> Roadmap:
    """
    Takes an existing (generating) Roadmap and the AI JSON dict,
    creates all RoadmapNode and NodeResource objects, then marks active.
    """
    roadmap.title = ai_data.get('title', 'My Learning Roadmap')
    roadmap.career_path = ai_data.get('career_path', 'general')
    roadmap.description = ai_data.get('description', '')
    roadmap.estimated_weeks = ai_data.get('estimated_weeks', 12)
    roadmap.status = Roadmap.Status.ACTIVE
    roadmap.generated_at = timezone.now()
    roadmap.save()

    nodes_data = _fix_phase_structure(ai_data.get('nodes', []))

    # Determine which indices start as 'available'.
    # The AI always leads with a milestone (Phase 1), which has no UI action.
    # We auto-complete milestones in the unlock chain, so we need the first
    # non-milestone node to be 'available' at creation time too.
    first_non_milestone = next(
        (i for i, n in enumerate(nodes_data) if n.get('node_type') != 'milestone'),
        None,
    )
    initially_available = {0, first_non_milestone} - {None}

    # First pass: create all nodes (without parent links)
    node_id_map = {}  # AI id string -> DB RoadmapNode pk
    for i, node_data in enumerate(nodes_data):
        node_type = node_data.get('node_type', 'skill')
        # Milestones that are initially available are marked as completed
        # immediately (they're phase markers, not tasks).
        if i in initially_available and node_type == 'milestone':
            initial_status = 'completed'
        elif i in initially_available:
            initial_status = 'available'
        else:
            initial_status = 'locked'

        node = RoadmapNode.objects.create(
            roadmap=roadmap,
            node_type=node_type,
            title=node_data.get('title', f'Step {i + 1}'),
            description=node_data.get('description', ''),
            skill_slug=node_data.get('skill_slug', ''),
            position_x=node_data.get('position_x', 0),
            position_y=node_data.get('position_y', i * 150),
            node_order=i,
            estimated_hours=node_data.get('estimated_hours', 5),
            difficulty=_coerce_difficulty(node_data.get('difficulty', 1)),
            xp_reward=node_data.get('xp_reward', 50),
            status=initial_status,
        )
        node_id_map[node_data.get('id', '')] = node

        # Create placeholder resources; YouTube API fills in real URLs below
        for res_data in node_data.get('suggested_resources', []):
            search_query = res_data.get('search_query', '')
            resource_type = res_data.get('resource_type', 'youtube_video')
            NodeResource.objects.create(
                node=node,
                resource_type=resource_type,
                title=res_data.get('title', 'Resource'),
                url='',  # populated by populate_node_resources() below
                description=search_query,  # used as YouTube search query
            )

    # Populate YouTube resources with real video IDs via YouTube API
    from apps.resources.youtube_client import populate_node_resources
    for node in node_id_map.values():
        populate_node_resources(node)

    # Second pass: link parent nodes
    for node_data in nodes_data:
        parent_ai_id = node_data.get('parent_id')
        if parent_ai_id and parent_ai_id in node_id_map:
            child_node = node_id_map.get(node_data.get('id', ''))
            if child_node:
                child_node.parent_node = node_id_map[parent_ai_id]
                child_node.save(update_fields=['parent_node'])

    roadmap.recalculate_completion()
    return roadmap

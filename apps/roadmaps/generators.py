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


_TYPE_ORDER = {'skill': 0, 'assessment': 0, 'project': 1, 'final_assessment': 2, 'certification': 3}

# Node types that live globally after all phase milestones — they must not be
# wrapped inside a phase, and _fix_phase_structure should not relocate them.
_TRAILING_GLOBAL_TYPES = {'final_assessment', 'certification'}


def _fix_phase_structure(nodes_data):
    """
    Enforces phase purity for the three real phases (Foundations, Core Skills, Build):
    project phases contain ONLY projects; skill phases contain ONLY skills.
    final_assessment and certification nodes are treated as global trailing nodes —
    they are collected out of any phase groupings and emitted at the end in a fixed order
    (final_assessment first, then certifications), so the AI is forgiven for mis-placing them.
    """
    # First pass: pull out all trailing global nodes in document order
    trailing = [n for n in nodes_data if n.get('node_type') in _TRAILING_GLOBAL_TYPES]
    phase_nodes_all = [n for n in nodes_data if n.get('node_type') not in _TRAILING_GLOBAL_TYPES]

    # Build phase groups from what's left (skills, projects, milestones, assessments)
    phases, current = [], {'milestone': None, 'nodes': []}
    for node in phase_nodes_all:
        if node.get('node_type') == 'milestone':
            phases.append(current)
            current = {'milestone': node, 'nodes': []}
        else:
            current['nodes'].append(node)
    phases.append(current)

    # Move any project nodes that the AI placed in earlier phases (e.g. Core Skills)
    # into the LAST phase — that's the designated "Build" phase. This catches the
    # common case where the AI emits a project with parent_id = Phase 3 milestone
    # but a node_order that sorts it before the milestone itself, causing it to
    # render visually under the wrong phase header.
    if len(phases) >= 2:
        last_phase = phases[-1]
        for i in range(len(phases) - 1):
            stray_projects = [n for n in phases[i]['nodes'] if n.get('node_type') == 'project']
            if stray_projects:
                phases[i]['nodes'] = [n for n in phases[i]['nodes'] if n.get('node_type') != 'project']
                last_phase['nodes'].extend(stray_projects)

    # Conversely, move any stray skills/assessments out of the project phase
    # (the LAST phase) back to an earlier skills phase.
    if len(phases) >= 2:
        last_phase = phases[-1]
        stray_skills = [n for n in last_phase['nodes'] if n.get('node_type') in ('skill', 'assessment')]
        if stray_skills:
            phases[-2]['nodes'].extend(stray_skills)
            last_phase['nodes'] = [n for n in last_phase['nodes'] if n.get('node_type') not in ('skill', 'assessment')]

    for phase in phases:
        phase['nodes'].sort(key=lambda n: _TYPE_ORDER.get(n.get('node_type', 'skill'), 0))

    result = []
    for phase in phases:
        if phase['milestone']:
            result.append(phase['milestone'])
        result.extend(phase['nodes'])

    # Sort trailing global nodes: final_assessment(s) first, then certifications,
    # preserving relative order within each type.
    trailing_sorted = sorted(
        trailing, key=lambda n: _TYPE_ORDER.get(n.get('node_type', 'skill'), 99)
    )
    result.extend(trailing_sorted)
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

    # Defensive check: the validator already ran inside generate_roadmap(), but
    # save_roadmap_from_ai() can also be called with externally-supplied ai_data
    # (e.g. tests, admin tooling). A second pass here prevents bad data reaching
    # the DB regardless of the call path.
    from apps.ai_assistant.validators import validate_generated_roadmap
    recommended_path = ai_data.get('career_path', '')
    ok, errors = validate_generated_roadmap(nodes_data, recommended_path)
    if not ok:
        raise ValueError(
            f'Roadmap data failed validation before saving: {"; ".join(errors[:3])}'
        )

    # Determine which indices start as 'available'.
    # The AI always leads with a milestone (Phase 1), which has no UI action.
    # We auto-complete milestones in the unlock chain, so we need the first
    # non-milestone node to be 'available' at creation time too.
    # Skip final_assessment and certification when picking the "first non-milestone"
    # so those stay locked until their prerequisites are met.
    first_non_milestone = next(
        (i for i, n in enumerate(nodes_data)
         if n.get('node_type') not in ('milestone', 'final_assessment', 'certification')),
        None,
    )
    initially_available = {0, first_non_milestone} - {None}

    # First pass: create all nodes (without parent links)
    node_id_map = {}  # AI id string -> DB RoadmapNode pk
    for i, node_data in enumerate(nodes_data):
        node_type = node_data.get('node_type', 'skill')
        # Milestones that are initially available are marked as completed
        # immediately (they're phase markers, not tasks).
        # Certifications and final_assessment always start locked — they unlock
        # based on the Final Assessment pass result, not by sequence position.
        if node_type in ('certification', 'final_assessment'):
            initial_status = 'locked'
        elif i in initially_available and node_type == 'milestone':
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

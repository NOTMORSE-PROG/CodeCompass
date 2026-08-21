"""
Unit tests for the scope-enforcement pieces of the AI assistant:
  - A1: build_career_mentor_prompt injects scope fences in non-roadmap modes
        and drops roadmap IDs from the context block.
  - A3: _extract_tags_for_scope strips disallowed tags per scope.

These are pure-function tests — no DB, no Groq, no WebSocket.
Permission-class tests that need ChatSession rows are deferred to an
integration pass (they require migrations + a test DB).
"""
from django.test import SimpleTestCase as TestCase

from .consumers import (
    _extract_tags_for_scope,
    ALLOWED_TAGS_BY_SCOPE,
)
from .groq_client import (
    _build_context_block,
    build_career_mentor_prompt,
    _SCOPE_FENCES,
)


class BuildContextBlockScopingTests(TestCase):
    """Non-roadmap modes must NOT leak roadmap_id / full_node_list / cert state."""

    def _full_ctx(self):
        return {
            'name': 'Juan',
            'role': 'student',
            'year_level': '3rd',
            'program': 'BSCS',
            'career_goal': 'backend engineer',
            'roadmap_id': 42,
            'roadmap_title': 'Backend Dev Path',
            'roadmap_pct': 50,
            'full_node_list': [
                {'id': 1, 'title': 'HTTP basics', 'status': 'locked',
                 'node_type': 'skill'},
                {'id': 2, 'title': 'Django intro', 'status': 'in_progress',
                 'node_type': 'skill'},
            ],
            'completed_cert_nodes': ['freeCodeCamp'],
            'active_cert_node': 'AWS Cloud Practitioner',
        }

    def test_roadmap_mode_includes_ids(self):
        block = _build_context_block(self._full_ctx(), mode='roadmap')
        self.assertIn('Roadmap ID: 42', block)
        self.assertIn('HTTP basics', block)
        self.assertIn('Completed Certification Goals', block)

    def test_job_mode_strips_roadmap_ids(self):
        block = _build_context_block(self._full_ctx(), mode='job')
        self.assertNotIn('Roadmap ID:', block)
        self.assertNotIn('HTTP basics', block)
        self.assertNotIn('Completed Certification Goals', block)
        # High-level context is still included:
        self.assertIn('Backend Dev Path', block)
        self.assertIn('backend engineer', block)

    def test_general_mode_strips_roadmap_ids(self):
        block = _build_context_block(self._full_ctx(), mode='general')
        self.assertNotIn('Roadmap ID:', block)
        self.assertNotIn('HTTP basics', block)

    def test_university_mode_strips_roadmap_ids(self):
        block = _build_context_block(self._full_ctx(), mode='university')
        self.assertNotIn('Roadmap ID:', block)


class BuildCareerMentorPromptScopingTests(TestCase):
    """ROADMAP_SWITCH / UPSKILL blocks are roadmap-only. Non-roadmap modes get a scope fence."""

    _CTX_WITH_ROADMAP = {
        'name': 'Juan',
        'role': 'student',
        'career_goal': 'backend engineer',
        'roadmap_id': 42,
        'roadmap_title': 'Backend Path',
        'roadmap_pct': 10,
        'completed_cert_nodes': ['CS50x'],
    }

    def test_roadmap_mode_has_action_blocks(self):
        prompt = build_career_mentor_prompt(
            user_context=self._CTX_WITH_ROADMAP, mode='roadmap', language='english',
        )
        self.assertIn('ROADMAP_SWITCH', prompt)
        self.assertIn('ROADMAP PATH SWITCHING', prompt)
        # Cert upskill block should also be present when cert context exists.
        self.assertIn('ROADMAP_UPSKILL', prompt)
        self.assertNotIn('SCOPE FENCE', prompt)

    def test_job_mode_has_scope_fence_no_action_blocks(self):
        prompt = build_career_mentor_prompt(
            user_context=self._CTX_WITH_ROADMAP, mode='job', language='english',
        )
        self.assertIn('SCOPE FENCE', prompt)
        self.assertIn('Jobs tab', prompt)
        self.assertNotIn('ROADMAP PATH SWITCHING', prompt)

    def test_general_mode_has_scope_fence(self):
        prompt = build_career_mentor_prompt(
            user_context=self._CTX_WITH_ROADMAP, mode='general', language='english',
        )
        self.assertIn('SCOPE FENCE', prompt)
        self.assertIn('General tab', prompt)

    def test_university_mode_has_scope_fence(self):
        prompt = build_career_mentor_prompt(
            user_context={'name': 'Juan', 'role': 'student'},
            mode='university', language='english',
        )
        self.assertIn('SCOPE FENCE', prompt)
        self.assertIn('University tab', prompt)

    def test_scope_fence_templates_cover_all_non_roadmap_modes(self):
        self.assertEqual(
            set(_SCOPE_FENCES.keys()),
            {'general', 'job', 'university'},
        )


class ExtractTagsForScopeTests(TestCase):
    """A3: output-layer allowlist — disallowed tags are nulled and logged."""

    _RESP_WITH_SWITCH = (
        "Okay! I'll switch your roadmap to Data Science.\n"
        '[ROADMAP_SWITCH: {"roadmap_id": 1, "new_path": "Data Science", '
        '"career_goal": "data analyst", "summary": "Switch to DS"}]'
    )
    _RESP_WITH_EDIT = (
        'Making that change now.\n'
        '[ROADMAP_EDIT: {"action": "edit_node", "roadmap_id": 1, "node_id": 5, '
        '"changes": {"title": "New"}, "summary": "Retitle"}]'
    )
    _RESP_WITH_RESOURCES = 'Here are some links.\n[RESOURCES: CS50|https://cs50.harvard.edu]'
    _RESP_WITH_SUGGESTIONS = 'Done.\n[SUGGESTIONS: A | B | C]'

    def test_roadmap_scope_preserves_switch(self):
        clean, suggestions, edits, resources, switch, upskill = _extract_tags_for_scope(
            self._RESP_WITH_SWITCH, scope='roadmap'
        )
        self.assertIsNotNone(switch)
        self.assertEqual(switch['new_path'], 'Data Science')
        self.assertNotIn('[ROADMAP_SWITCH', clean)

    def test_job_scope_strips_switch(self):
        with self.assertLogs('ai_assistant.consumers', level='WARNING') as log:
            clean, suggestions, edits, resources, switch, upskill = _extract_tags_for_scope(
                self._RESP_WITH_SWITCH, scope='job'
            )
        self.assertIsNone(switch)
        self.assertNotIn('[ROADMAP_SWITCH', clean)
        self.assertTrue(any('roadmap_switch' in msg for msg in log.output))

    def test_general_scope_strips_edit_proposals(self):
        with self.assertLogs('ai_assistant.consumers', level='WARNING') as log:
            clean, suggestions, edits, resources, switch, upskill = _extract_tags_for_scope(
                self._RESP_WITH_EDIT, scope='general'
            )
        self.assertEqual(edits, [])
        self.assertNotIn('[ROADMAP_EDIT', clean)
        self.assertTrue(any('edit_proposals' in msg for msg in log.output))

    def test_university_scope_keeps_resources_and_suggestions(self):
        combined = self._RESP_WITH_RESOURCES + '\n' + self._RESP_WITH_SUGGESTIONS
        clean, suggestions, edits, resources, switch, upskill = _extract_tags_for_scope(
            combined, scope='university'
        )
        self.assertEqual(resources, [{'title': 'CS50', 'url': 'https://cs50.harvard.edu'}])
        self.assertEqual(suggestions, ['A', 'B', 'C'])

    def test_allowlist_shape(self):
        # Regression guard: onboarding must be the most restrictive scope.
        self.assertEqual(ALLOWED_TAGS_BY_SCOPE['onboarding'], {'suggestions'})
        self.assertIn('edit_proposals', ALLOWED_TAGS_BY_SCOPE['roadmap'])
        self.assertNotIn('edit_proposals', ALLOWED_TAGS_BY_SCOPE['job'])

"""Groq-powered AI helpers for the jobs app."""
import json
from apps.ai_assistant.groq_client import _call_groq_with_rotation

MODEL = 'llama-3.3-70b-versatile'


def extract_resume_skills(resume_text: str) -> dict:
    """
    Extract skills, roles, and keywords from resume text for job matching.
    Returns: {"skills": [...], "roles": [...], "keywords": [...]}
    Falls back to empty lists on any failure.
    """
    truncated = resume_text[:3000]
    prompt = (
        'Extract job-matching information from this resume text.\n'
        'Return ONLY valid JSON with exactly these keys:\n'
        '- "skills": list of technical skills (e.g. Python, React, SQL, AWS)\n'
        '- "roles": list of job roles or titles mentioned or implied\n'
        '- "keywords": list of general keywords useful for job matching\n\n'
        f'Resume text:\n{truncated}'
    )
    try:
        response = _call_groq_with_rotation(
            model=MODEL,
            messages=[
                {
                    'role': 'system',
                    'content': (
                        'You are a resume parser. '
                        'Always respond with valid JSON only — no markdown, no explanation.'
                    ),
                },
                {'role': 'user', 'content': prompt},
            ],
            temperature=0.3,
            max_tokens=512,
        )
        raw = response.choices[0].message.content.strip()
        # Strip markdown code fences if present
        if raw.startswith('```'):
            parts = raw.split('```')
            raw = parts[1] if len(parts) > 1 else parts[0]
            if raw.startswith('json'):
                raw = raw[4:]
        return json.loads(raw.strip())
    except Exception:
        return {'skills': [], 'roles': [], 'keywords': []}

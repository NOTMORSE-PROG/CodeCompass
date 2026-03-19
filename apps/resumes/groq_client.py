"""
Groq-powered AI helpers for the resume creator.
All prompts return JSON so they can be parsed and sent directly to the frontend.
"""
import json
from apps.ai_assistant.groq_client import _call_groq_with_rotation

MODEL = 'llama-3.3-70b-versatile'


def _call_groq(prompt: str) -> str:
    """Make a synchronous Groq chat completion and return the message content."""
    response = _call_groq_with_rotation(
        model=MODEL,
        messages=[
            {
                'role': 'system',
                'content': (
                    'You are a professional resume writing assistant. '
                    'Always respond with valid JSON only — no markdown, no explanation.'
                ),
            },
            {'role': 'user', 'content': prompt},
        ],
        temperature=0.7,
        max_tokens=1024,
    )
    return response.choices[0].message.content.strip()


def generate_bullet_points(job_title: str, achievement: str) -> list[str]:
    """
    Generate 4 impactful resume bullet points for a given job title and achievement.
    Returns a list of strings.
    """
    prompt = f"""Generate exactly 4 impactful resume bullet points for a "{job_title}" role.
Achievement context: {achievement}

Rules:
- Format: [Strong Action Verb] + [Task/Responsibility] + [Quantifiable Result if possible]
- Each bullet: 1 sentence, 10-18 words, ATS-friendly, no buzzwords
- Vary the action verbs across the 4 bullets
- If no metric is given, make the impact clear without fabricating numbers

Return ONLY a JSON array of 4 strings, e.g.:
["Developed RESTful APIs...", "Led a team of...", ...]"""

    raw = _call_groq(prompt)
    try:
        result = json.loads(raw)
        if isinstance(result, list):
            return [str(b) for b in result[:4]]
    except (json.JSONDecodeError, ValueError):
        pass
    # Fallback: split by newlines if JSON parse fails
    lines = [ln.strip().lstrip('-•').strip() for ln in raw.split('\n') if ln.strip()]
    return lines[:4]


def generate_summary(target_role: str, strengths: list[str], years_exp: str) -> list[dict]:
    """
    Generate 3 professional summary variations.
    Returns a list of {text, tone} dicts.
    """
    strengths_str = ', '.join(strengths) if strengths else 'technical skills and problem-solving'
    prompt = f"""Write 3 professional resume summary variations for a "{target_role}" role.
Experience: {years_exp}
Key strengths: {strengths_str}

Variation tones:
1. "formal" — polished, traditional, third-person style
2. "confident" — punchy, first-person, action-oriented
3. "skills_focused" — leads with technical skills and tools

Rules:
- Each summary: 2-3 sentences, under 60 words
- No filler phrases like "results-driven" or "dynamic"
- Tailored to the role

Return ONLY a JSON array:
[
  {{"tone": "formal", "text": "..."}},
  {{"tone": "confident", "text": "..."}},
  {{"tone": "skills_focused", "text": "..."}}
]"""

    raw = _call_groq(prompt)
    try:
        result = json.loads(raw)
        if isinstance(result, list):
            return result[:3]
    except (json.JSONDecodeError, ValueError):
        pass
    return [{'tone': 'generated', 'text': raw}]


def parse_job_description(job_description: str) -> dict:
    """
    Extract structured data from a job description.
    Returns {requiredSkills, niceToHaveSkills, keywords, experienceLevel, responsibilities}.
    """
    prompt = f"""Extract key information from this job description:

---
{job_description[:3000]}
---

Return ONLY a JSON object:
{{
  "requiredSkills": ["skill1", "skill2"],
  "niceToHaveSkills": ["skill3"],
  "keywords": ["keyword1", "keyword2"],
  "experienceLevel": "junior|mid|senior|lead",
  "responsibilities": ["responsibility1", "responsibility2"]
}}

Rules:
- requiredSkills: hard technical skills that are explicitly required
- niceToHaveSkills: preferred/optional skills
- keywords: important phrases/tools that an ATS would scan for
- experienceLevel: infer from years required or seniority words
- responsibilities: top 5 key duties in short phrases"""

    raw = _call_groq(prompt)
    try:
        result = json.loads(raw)
        return result
    except (json.JSONDecodeError, ValueError):
        return {
            'requiredSkills': [],
            'niceToHaveSkills': [],
            'keywords': [],
            'experienceLevel': 'unknown',
            'responsibilities': [],
            'raw': raw,
        }


def get_ats_suggestions(missing_keywords: list[str]) -> list[str]:
    """
    Given a list of missing ATS keywords, return actionable suggestions on how to
    naturally incorporate them into a resume.
    """
    if not missing_keywords:
        return []

    keywords_str = ', '.join(missing_keywords[:15])
    prompt = f"""A resume is missing these important keywords: {keywords_str}

Generate specific, actionable suggestions for how to naturally incorporate each keyword
into a resume (experience bullets, skills section, or summary) without keyword stuffing.

Return ONLY a JSON array of suggestion strings (one per keyword group), e.g.:
[
  "Add 'Docker' to your Skills > Tools section and mention containerization in your project bullets.",
  "Incorporate 'CI/CD' by describing your use of GitHub Actions or Jenkins in experience bullets."
]

Keep each suggestion under 25 words."""

    raw = _call_groq(prompt)
    try:
        result = json.loads(raw)
        if isinstance(result, list):
            return [str(s) for s in result]
    except (json.JSONDecodeError, ValueError):
        pass
    return [raw]

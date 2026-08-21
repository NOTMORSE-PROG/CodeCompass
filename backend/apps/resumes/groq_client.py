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


def generate_summary(target_role: str, strengths: list[str], years_exp: str, resume_content: dict = None) -> list[dict]:
    """
    Generate 5 professional summary variations using resume content for context.
    Returns a list of {tone, text} dicts.
    """
    strengths_str = ', '.join(strengths) if strengths else 'technical skills and problem-solving'

    # Build context block from actual resume data
    context_parts = []
    if resume_content:
        exp = resume_content.get('experience', [])
        if exp:
            titles = [e.get('title', '') for e in exp if e.get('title')]
            companies = [e.get('company', '') for e in exp if e.get('company')]
            if titles:
                context_parts.append(f"Experience: {', '.join(titles[:3])}" + (f" at {', '.join(companies[:3])}" if companies else ''))

        edu = resume_content.get('education', [])
        if edu:
            degrees = []
            for e in edu[:2]:
                parts = [e.get('degree', ''), e.get('field', '')]
                school = e.get('school', '')
                deg = ' '.join(p for p in parts if p)
                if school:
                    deg += f' from {school}'
                if deg.strip():
                    degrees.append(deg.strip())
            if degrees:
                context_parts.append(f"Education: {'; '.join(degrees)}")

        skills = resume_content.get('skills', {})
        all_skills = skills.get('technical', []) + skills.get('tools', [])
        if all_skills:
            context_parts.append(f"Top skills: {', '.join(all_skills[:10])}")

        projects = resume_content.get('projects', [])
        if projects:
            proj_names = [p.get('name', '') for p in projects[:3] if p.get('name')]
            if proj_names:
                context_parts.append(f"Notable projects: {', '.join(proj_names)}")

    context_block = '\n'.join(context_parts) if context_parts else 'No additional resume context provided.'

    prompt = f"""You are writing a professional resume summary for a "{target_role}" position.

CANDIDATE PROFILE:
{context_block}
Target role: {target_role}
Experience level: {years_exp}
Key strengths highlighted by candidate: {strengths_str}

Write 5 professional summary variations, each with a clearly distinct tone and approach. Make each feel genuinely different — not just rearranged sentences.

Tones:
1. "formal" — polished, third-person, traditional corporate style. Reads like a senior professional wrote it.
2. "confident" — punchy first-person, strong action verbs, direct and self-assured. No fluff.
3. "skills_focused" — leads with technical skills and tools. Great for ATS. Quantify where possible.
4. "achievement_led" — opens with a key accomplishment or impact, then provides role context. Grabs attention immediately.
5. "narrative" — tells a brief career story: background, what drives the candidate, where they're headed.

STRICT RULES (violating any = failure):
- MINIMUM 3 full sentences per variation. MINIMUM 70 words. Target 80-120 words.
- Do NOT produce a single sentence. Do NOT produce two sentences. THREE or more.
- Do NOT start with "I am", "Experienced", or "Results-driven"
- Banned words (use NONE of these): "results-driven", "dynamic", "passionate", "synergy", "leverage", "innovative", "motivated", "dedicated", "hardworking"
- Include at least one concrete skill or technology from the candidate profile in every variation
- Suitable for PH tech industry (CCS graduates, software/IT roles in the Philippines)
- ATS-optimized: naturally embed role-relevant keywords without stuffing
- If resume has experience data, reference actual job titles or companies naturally
- If entry-level or fresh grad, emphasize academic projects, coursework, personal projects, and growth mindset

EXAMPLE of a good "confident" variation (use this length and depth as a benchmark):
"Full-stack developer specializing in React and Django, with hands-on experience building scalable web applications from API design to pixel-perfect UI. Over two years of shipping production code across multiple projects, I've developed a sharp eye for performance bottlenecks and clean architecture. Currently seeking a senior-level role where I can contribute to a cross-functional team while continuing to grow in cloud infrastructure and DevOps practices."

Write at that level of depth and specificity for ALL 5 variations.

Return ONLY a valid JSON array (no markdown, no explanation):
[
  {{"tone": "formal", "text": "..."}},
  {{"tone": "confident", "text": "..."}},
  {{"tone": "skills_focused", "text": "..."}},
  {{"tone": "achievement_led", "text": "..."}},
  {{"tone": "narrative", "text": "..."}}
]"""

    response = _call_groq_with_rotation(
        model=MODEL,
        messages=[
            {
                'role': 'system',
                'content': (
                    'You are a professional resume writing assistant specializing in tech and IT roles. '
                    'Always respond with valid JSON only — no markdown, no explanation.'
                ),
            },
            {'role': 'user', 'content': prompt},
        ],
        temperature=0.8,
        max_tokens=1500,
    )
    raw = response.choices[0].message.content.strip()

    try:
        result = json.loads(raw)
        if isinstance(result, list) and result:
            # Filter out any variation under 50 words (model being lazy)
            valid = [item for item in result if isinstance(item, dict) and len(item.get('text', '').split()) >= 50]
            if valid:
                return valid[:5]
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


def get_ats_suggestions(missing_keywords: list[str]) -> list[dict]:
    """
    Given a tagged list of missing ATS keywords (prefixed with [REQUIRED]/[PREFERRED]/[GENERAL]),
    return structured, actionable suggestions with priority, section, keyword, text, and example.
    Returns a list of dicts.
    """
    if not missing_keywords:
        return []

    keywords_str = '\n'.join(f'- {k}' for k in missing_keywords[:17])
    prompt = f"""A resume is missing these keywords (priority tagged):
{keywords_str}

For each keyword, generate a structured, actionable suggestion.

Rules:
- priority: "high" for [REQUIRED], "medium" for [PREFERRED], "low" for [GENERAL]
- section: best place to add it — one of: "skills", "summary", "experience", "title"
- keyword: the keyword only (strip the [REQUIRED]/[PREFERRED]/[GENERAL] prefix)
- text: ONE specific sentence (max 20 words) telling exactly HOW to add it
- example: a concrete phrase or bullet fragment to copy-paste (max 15 words)

Return ONLY a valid JSON array:
[
  {{"priority": "high", "section": "skills", "keyword": "Docker", "text": "Add Docker to your Technical Skills section under Tools.", "example": "Docker, Docker Compose, container orchestration"}},
  {{"priority": "high", "section": "experience", "keyword": "CI/CD", "text": "Add a bullet describing your use of CI/CD pipelines in a project.", "example": "Implemented CI/CD pipeline using GitHub Actions, reducing deploy time by 40%"}},
  {{"priority": "medium", "section": "summary", "keyword": "Agile", "text": "Mention Agile methodology in your professional summary.", "example": "experienced with Agile/Scrum workflows in cross-functional teams"}}
]"""

    raw = _call_groq(prompt)
    try:
        result = json.loads(raw)
        if isinstance(result, list):
            return result[:17]
    except (json.JSONDecodeError, ValueError):
        pass
    return []

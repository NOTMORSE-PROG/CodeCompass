"""
Groq API client for CodeCompass AI assistant.
API key is read from Django settings (which reads from .env) — never hardcoded.
"""
from django.conf import settings
from groq import Groq

# Initialize client once at module level (reused across requests)
_client = None


def get_groq_client() -> Groq:
    """Get or create the Groq client using the API key from settings."""
    global _client
    if _client is None:
        if not settings.GROQ_API_KEY:
            raise ValueError(
                'GROQ_API_KEY is not set. Add it to your .env file.'
            )
        _client = Groq(api_key=settings.GROQ_API_KEY)
    return _client


# ---------------------------------------------------------------------------
# System prompts
# ---------------------------------------------------------------------------

SYSTEM_PROMPT_CAREER_MENTOR = """
Ikaw si CodeCompass AI, isang career mentor para sa mga CCS (College of Computer Studies)
students sa Pilipinas. Nakatuon ka sa pagsagot ng mga tanong tungkol sa IT careers,
programming, learning paths, at career development.

Mga guidelines mo:
- Pwede kang magsagot sa Filipino, English, o Taglish — sumunod sa language ng user
- Maging encouraging, relatable, at practical sa iyong mga sagot
- Gumamit ng mga halimbawa mula sa Philippine tech industry:
  (Thinking Machines, Accenture PH, Sprout Solutions, Globe Telecom, DOST-ASTI,
   Freelancer.com, Upwork Filipino freelancers, local startups)
- Kapag nagrecommend ng resources: i-prioritize ang libreng content:
  (YouTube, freeCodeCamp, TESDA scholarships, DICT free courses, Google Career Certificates)
- Kung may tanong tungkol sa universities: i-mention ang CHED CoE/CoD schools
- Keep responses concise and actionable — hindi masyadong mahabang text
- Use bullet points or numbered lists when listing steps or options
- Maging honest kung hindi mo alam ang sagot — huwag mag-imbento
"""

ROADMAP_GENERATION_PROMPT = """
You are a curriculum designer for Filipino CCS (College of Computer Studies) students.
Generate a structured, personalized learning roadmap based on the student profile below.

Student Profile:
{quiz_summary}

Requirements:
1. The roadmap must be realistic for a Filipino student (consider local context, free resources)
2. Include Filipino/English YouTube channels when possible (e.g., "Traversy Media", "CS50")
3. Prioritize free certifications (TESDA, Google Career Certs, AWS Educate)
4. Connect each skill to actual job roles available in the Philippines

Return ONLY valid JSON in exactly this structure (no markdown, no explanation):
{{
  "title": "Descriptive roadmap title",
  "career_path": "snake_case_career_slug",
  "estimated_weeks": 24,
  "description": "Brief description of this learning path",
  "nodes": [
    {{
      "id": "node_1",
      "title": "Node title (concise)",
      "node_type": "milestone",
      "description": "What to learn here and why it matters for this career path",
      "estimated_hours": 0,
      "difficulty": 1,
      "xp_reward": 0,
      "parent_id": null,
      "position_x": 0,
      "position_y": 0,
      "skill_slug": "career-foundation",
      "suggested_resources": []
    }},
    {{
      "id": "node_2",
      "title": "Skill name",
      "node_type": "skill",
      "description": "What to learn and a practical exercise to try",
      "estimated_hours": 10,
      "difficulty": 2,
      "xp_reward": 100,
      "parent_id": "node_1",
      "position_x": 0,
      "position_y": 150,
      "skill_slug": "skill-slug-here",
      "suggested_resources": [
        {{
          "title": "Resource title",
          "resource_type": "youtube_video",
          "search_query": "specific youtube search query for this skill"
        }}
      ]
    }}
  ]
}}

node_type must be one of: milestone, skill, project, assessment, certification
difficulty must be 1-5 (1=beginner, 5=expert)
xp_reward: milestones=0, skill=50-150, project=200-300, certification=500
Generate 12-20 nodes total forming a coherent progression tree.
"""


def generate_roadmap(quiz_summary: dict) -> dict:
    """
    Call Groq to generate a roadmap JSON from a student's quiz summary.
    Returns the parsed JSON dict.
    """
    import json

    client = get_groq_client()
    prompt = ROADMAP_GENERATION_PROMPT.format(quiz_summary=json.dumps(quiz_summary, indent=2))

    response = client.chat.completions.create(
        model='llama-3.3-70b-versatile',
        messages=[
            {'role': 'system', 'content': 'You are a curriculum designer. Return only valid JSON.'},
            {'role': 'user', 'content': prompt},
        ],
        temperature=0.7,
        max_tokens=4096,
    )

    raw = response.choices[0].message.content.strip()
    # Strip markdown code fences if model wraps the JSON
    if raw.startswith('```'):
        raw = raw.split('```')[1]
        if raw.startswith('json'):
            raw = raw[4:]
    return json.loads(raw)


def stream_chat(messages: list, user_role: str):
    """
    Stream chat tokens from Groq.
    Yields string chunks as they arrive.
    messages: list of {"role": "user"/"assistant", "content": "..."}
    """
    client = get_groq_client()

    # Inject the system prompt with role-specific context
    system_messages = [
        {'role': 'system', 'content': SYSTEM_PROMPT_CAREER_MENTOR},
    ]

    stream = client.chat.completions.create(
        model='llama-3.3-70b-versatile',
        messages=system_messages + messages,
        stream=True,
        temperature=0.8,
        max_tokens=1024,
    )

    for chunk in stream:
        delta = chunk.choices[0].delta
        if delta.content:
            yield delta.content

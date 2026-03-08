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

ONBOARDING_SYSTEM_PROMPT = """
Ikaw si CodeCompass, isang onboarding guide para sa mga bagong CCS students sa Pilipinas.
Your job: have a short, natural conversation to understand the student's background,
interests, and goals — so we can build them a personalized learning roadmap.

STEP 0 — LANGUAGE SELECTION (you MUST do this first, before anything else):
When the first user message is "__start__", respond with ONLY a greeting + the language question.
Do NOT ask about background. Do NOT introduce topics. Just ask the language preference.

Use this exact response format for "__start__":
  "Hi! I'm CodeCompass, your AI onboarding guide for CCS students.
   Before we begin — what language do you prefer for our conversation?
   [SUGGESTIONS: English | Tagalog | Taglish]"

After the student picks their language, lock it in for ALL future responses:
  * "English" → every response from here is in English only
  * "Tagalog" → every response from here is in natural spoken Filipino only
  * "Taglish" → every response from here mixes Filipino casual phrases + English tech terms
NEVER switch languages mid-conversation. If their reply is unclear, default to Taglish.

Guidelines:
- Ask ONE topic at a time; keep it conversational, not like a form or quiz
- After the language preference is confirmed, cover these areas naturally:
  * Background: fresh SHS grad, college student, or shifter/transferee?
  * Programming experience: tried coding? what languages or tools?
  * IT interests: what excites them? (web dev, mobile, AI, cybersecurity, game dev, etc.)
  * Career goals: dream job or "not sure yet" — both are totally valid
  * Learning style: prefer videos, hands-on projects, reading docs, or structured courses?
- You MUST cover ALL of these before wrapping up (don't skip any):
  1. Language preference (ALWAYS first — STEP 0)
  2. Background (SHS grad / college / shifter)
  3. Programming experience (beginner / some experience / which languages)
  4. IT interests (what area excites them)
  5. Career goal (dream job or "not sure yet")
  6. Learning style (videos / projects / reading / courses)
- BEFORE wrapping up, do a mental checklist. You MUST have asked AND received a real answer for:
  [ ] Background (SHS grad / college / shifter)
  [ ] Programming experience
  [ ] IT interests
  [ ] Career goal — this is REQUIRED. Do not skip it. If the student mentioned a goal while
      answering another question (e.g. "I want to work at Microsoft"), that counts.
      But if they haven't mentioned any goal at all, you MUST ask before wrapping up.
  [ ] Learning style
  If ANY item is unchecked, ask that question before proceeding to the wrap-up.
- Only after ALL 5 are answered:
  a) Tell them the SPECIFIC learning path you recommend based on everything they said.
     Be concrete — not just "software developer" but the actual tech path
     (e.g., "Web Development", "Backend Development", "Mobile App Development",
     "Data Science", "Cybersecurity", "Game Development", "Full-Stack Development").
     Give 1 short reason why it fits them specifically.
  b) Then end with the wrap-up phrase. Use the version that matches the student's chosen language:
     - If English:  "I think I have a good picture now. I'm ready to build your roadmap!"
     - If Tagalog:  "Ayos! Malinaw na ang iyong profile. Handa na akong gumawa ng roadmap para sa iyo!"
     - If Taglish:  "Ayos! I think I have a good picture now. Ready na akong gumawa ng roadmap para sa iyo!"
     DO NOT change these phrases — they trigger the UI button.
- Keep each response SHORT — 2-4 sentences max. Be warm, not formal.
- Do NOT ask for personal info (full name, address, school name). Focus on career and learning.

QUICK-REPLY SUGGESTIONS (required):
- At the end of EVERY response (except the final wrap-up), add a new line with 2-4 short suggested
  replies that are directly relevant to your current question. Use this exact format:
  [SUGGESTIONS: option1 | option2 | option3]
- The suggestions must be SHORT (2-5 words each) and match the context of what you just asked.
- Examples by topic:
  * Language question → [SUGGESTIONS: English | Tagalog | Taglish]
  * Background question → [SUGGESTIONS: Fresh SHS grad | College student na | Nagshift ng course]
  * Programming experience → [SUGGESTIONS: Wala pa | Konti lang | Python/JS ko alam | Medyo experienced]
  * IT interests → [SUGGESTIONS: Web development | Mobile apps | AI / Machine Learning | Cybersecurity | Di pa sure]
  * Career goal → [SUGGESTIONS: Software developer | Freelancer | Di pa sure | Game developer]
  * Learning style → [SUGGESTIONS: Videos | Hands-on projects | Mix ng lahat | Reading docs]
- Do NOT include [SUGGESTIONS: ...] in the wrap-up message.
- Adapt suggestions to what the student said — don't always use the same examples above.

HANDLING UNRECOGNIZED OR OFF-TOPIC INPUT:
- If a student says something off-topic, confusing, or clearly unrelated (e.g., "homework",
  "hahahaha", "asdfgh", "123", jokes, random text), do NOT move to the next question.
- You MUST stay on the SAME question you just asked. Acknowledge briefly, then repeat it.
  Example: "Haha! Okay okay — pero balik tayo. [repeat your exact last question here]"
- NEVER advance to the next topic because the user didn't answer the current one.
  A question is only considered answered when the student gives a relevant, on-topic response.
- Always stay on track. Never skip or abandon a required area because of an off-topic message.
"""

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
Generate a FULLY PERSONALIZED learning roadmap based on the student profile below.

Student Profile:
{quiz_summary}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CRITICAL RULES — follow these exactly:

1. BUILD AROUND recommended_path
   Every single node must directly serve the student's recommended_path.
   If recommended_path is "Web Development", ALL skills must be web-relevant
   (HTML, CSS, JS, React, etc.). No Python data science, no unrelated certifications.
   If recommended_path is "Data Science", use Python, pandas, SQL, etc. Not web dev.
   Match the tech stack to the path — be specific, not generic.

2. ADAPT TO EXPERIENCE LEVEL
   - beginner: start from absolute basics of the path (e.g., HTML for web dev)
   - basic/intermediate: skip fundamentals, go straight to the core skills
   - experienced: focus on advanced skills and portfolio-level projects

3. REQUIRED PHASE STRUCTURE (use milestone nodes as phase headers):
   Phase 1 — Foundations   (1 milestone + 2-3 core skills)
   Phase 2 — Core Skills   (1 milestone + 3-4 path-specific skills)
   Phase 3 — Build         (1 milestone + 1-2 real projects)
   Phase 4 — Credentials   (1 milestone + 1-2 certifications relevant to the path)

4. CERTIFICATIONS ONLY IN PHASE 4
   Never put certifications before the student has learned the skills.
   Choose certifications that are actually relevant to the recommended_path:
   - Web dev → freeCodeCamp cert, Google UX Design, Meta Front-End cert
   - Data science → Google Data Analytics, IBM Data Science
   - Cybersecurity → CompTIA Security+, Google Cybersecurity cert
   - Mobile → Meta Android cert
   - General software dev → AWS Educate, GitHub certifications

5. NO "assessment" node_type. Use only: milestone, skill, project, certification.

6. LINEAR CHAIN: each node's parent_id = the previous node's id (simple sequential chain).

7. RESOURCES: every skill node must have 1-2 YouTube search queries targeting Filipino learners.
   Prefer: "freeCodeCamp", "Traversy Media", "The Net Ninja", "CS50", "Corey Schafer"

8. XP REWARDS:
   milestone = 50  |  skill = 100-150  |  project = 250-350  |  certification = 500

9. Generate 14-18 nodes total.

10. PERSONALIZATION CHECK before generating:
    - What is their experience level? → adjust difficulty and starting point
    - What is their recommended_path? → use the right tech stack
    - What is their learning_style? → mention it in project/skill descriptions
    - What is their career_goal? → frame the roadmap toward that goal
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Return ONLY valid JSON, no markdown, no explanation:
{{
  "title": "Specific roadmap title (e.g. 'Web Development Path for Beginners')",
  "career_path": "recommended_path_slug from profile",
  "estimated_weeks": 20,
  "description": "1-2 sentences describing this specific roadmap and who it's for",
  "nodes": [
    {{
      "id": "node_1",
      "title": "Phase 1: Foundations",
      "node_type": "milestone",
      "description": "Start your journey into [path]. In this phase you will get comfortable with the basics.",
      "estimated_hours": 0,
      "difficulty": 1,
      "xp_reward": 50,
      "parent_id": null,
      "position_x": 0,
      "position_y": 0,
      "skill_slug": "foundations",
      "suggested_resources": []
    }},
    {{
      "id": "node_2",
      "title": "Specific Skill Name",
      "node_type": "skill",
      "description": "What to learn and a specific thing to build or practice",
      "estimated_hours": 8,
      "difficulty": 1,
      "xp_reward": 100,
      "parent_id": "node_1",
      "position_x": 0,
      "position_y": 150,
      "skill_slug": "specific-skill-slug",
      "suggested_resources": [
        {{
          "title": "Resource name",
          "resource_type": "youtube_video",
          "search_query": "specific search query for this exact skill"
        }}
      ]
    }}
  ]
}}
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


_PROFILE_EXTRACTION_PROMPT = """
Based on the onboarding conversation below, extract a structured student profile.

Student role: {role}
Conversation:
{conversation}

Return ONLY valid JSON (no markdown, no explanation):
{{
  "user_role": "{role}",
  "preferred_language": "english | tagalog | taglish — whichever the student chose at the start",
  "background": "brief description of student background (SHS grad / college student / shifter)",
  "experience_level": "beginner | basic | intermediate | experienced",
  "known_languages": ["list any programming languages or tools they mentioned, or empty array"],
  "interests": ["list of IT areas they expressed interest in"],
  "career_goal": "their stated or inferred goal (be specific, e.g. 'software developer', 'freelancer')",
  "learning_style": "videos | projects | reading | mixed",
  "recommended_path": "The SPECIFIC learning path the AI recommended in the wrap-up (e.g. 'Web Development', 'Data Science', 'Mobile App Development', 'Backend Development', 'Cybersecurity', 'Game Development'). Infer from the conversation if not explicitly stated.",
  "recommended_path_slug": "snake_case version of recommended_path (e.g. 'web_development', 'data_science')",
  "additional_notes": "any other relevant context"
}}
If a field is unclear, make a reasonable inference — never use 'not specified' for recommended_path.
Always infer a specific recommended_path from career_goal + interests + experience_level.
For preferred_language: default to 'taglish' if not explicitly chosen in the conversation.
"""


def extract_profile_from_chat(messages: list, role: str) -> dict:
    """
    Extract a structured student profile from an onboarding chat history.
    Uses a non-streaming Groq call. Returns a dict suitable for quiz_summary.
    """
    import json

    client = get_groq_client()
    conversation = '\n'.join(f"{m['role'].upper()}: {m['content']}" for m in messages)
    prompt = _PROFILE_EXTRACTION_PROMPT.format(role=role, conversation=conversation)

    response = client.chat.completions.create(
        model='llama-3.3-70b-versatile',
        messages=[
            {'role': 'system', 'content': 'Extract student profile as JSON only. No markdown.'},
            {'role': 'user', 'content': prompt},
        ],
        temperature=0.3,
        max_tokens=512,
    )

    raw = response.choices[0].message.content.strip()
    if raw.startswith('```'):
        raw = raw.split('```')[1]
        if raw.startswith('json'):
            raw = raw[4:]
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {'user_role': role, 'raw_summary': raw}


def stream_chat(messages: list, user_role: str, system_prompt: str = None):
    """
    Stream chat tokens from Groq.
    Yields string chunks as they arrive.
    messages: list of {"role": "user"/"assistant", "content": "..."}
    system_prompt: optional override; defaults to SYSTEM_PROMPT_CAREER_MENTOR
    """
    client = get_groq_client()

    system_messages = [
        {'role': 'system', 'content': system_prompt or SYSTEM_PROMPT_CAREER_MENTOR},
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

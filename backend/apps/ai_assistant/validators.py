"""
Semantic validation for AI-generated roadmaps.
Plain Python, stdlib only — no pydantic or external deps.
"""

# ── Deprecated / EOL tech deny-list ─────────────────────────────────────────
DEPRECATED_TECH = frozenset({
    "flash", "actionscript", "angularjs 1", "angularjs v1", "coffeescript",
    "bower", "backbone.js", "backbone", "silverlight", "php 5", "php5",
    "internet explorer", "vb6", "visual basic 6", "grunt",
})

# ── Path keywords for skill-drift detection ──────────────────────────────────
# Each entry covers the slug variants the AI might produce (spaced or underscored).
PATH_KEYWORDS: dict[str, frozenset] = {
    "web_development": frozenset({
        "html", "css", "js", "javascript", "react", "node", "tailwind",
        "next", "web", "frontend", "backend", "fullstack", "rest", "api",
        "express", "vue", "angular", "typescript",
    }),
    "web development": frozenset({
        "html", "css", "js", "javascript", "react", "node", "web",
        "frontend", "backend", "api",
    }),
    "data_science": frozenset({
        "python", "pandas", "numpy", "sql", "data", "machine", "learning",
        "ml", "ai", "jupyter", "statistics", "scikit", "tensorflow",
        "pytorch", "analysis", "visualization",
    }),
    "data science": frozenset({
        "python", "pandas", "sql", "data", "machine", "learning", "ml", "ai",
    }),
    "mobile_app_development": frozenset({
        "android", "ios", "flutter", "kotlin", "swift", "mobile",
        "react", "native", "firebase", "jetpack", "compose",
    }),
    "mobile app development": frozenset({
        "android", "ios", "flutter", "kotlin", "swift", "mobile",
    }),
    "backend_development": frozenset({
        "python", "java", "c#", "dotnet", "node", "api", "rest",
        "database", "postgresql", "mongodb", "backend", "server",
        "microservice", "django", "spring",
    }),
    "backend development": frozenset({
        "python", "java", "api", "rest", "database", "backend",
    }),
    "cybersecurity": frozenset({
        "network", "security", "linux", "cryptography", "firewall",
        "ethical", "hacking", "penetration", "vulnerability", "cisco",
        "kali", "wireshark", "threat", "malware",
    }),
    "game_development": frozenset({
        "unity", "unreal", "game", "c#", "c++", "physics", "rendering",
        "graphics", "opengl", "godot", "shader",
    }),
    "game development": frozenset({
        "unity", "unreal", "game", "c#", "godot",
    }),
    "it_fundamentals": frozenset({
        "computer", "hardware", "networking", "os", "operating", "system",
        "linux", "windows", "troubleshoot", "support", "helpdesk",
    }),
    "it fundamentals": frozenset({
        "computer", "hardware", "networking", "os", "linux",
    }),
}

# ── Cert allow-lists by tier ─────────────────────────────────────────────────
_TIER1_CERTS = [
    "freeCodeCamp Responsive Web Design",
    "freeCodeCamp JavaScript Algorithms Data Structures",
    "Harvard CS50x Introduction to Computer Science",
    "Microsoft Learn Foundations of Coding",
    "GitHub Foundations",
    "Cisco Intro to Cybersecurity",
    "Cisco Networking Basics",
    "Cisco NDG Linux Unhatched",
    "Fortinet NSE 1 Threat Landscape",
    "Fortinet NSE 2 Evolution of Cybersecurity",
    "CS50 Cybersecurity Harvard",
    "IBM SkillsBuild AI Fundamentals",
    "Kaggle Python Course",
    "Kaggle Pandas Course",
    "Kaggle Intro to Machine Learning",
    "Kaggle Data Visualization Seaborn",
    "TESDA NC II Computer Systems Servicing",
    "Google Fundamentals of Digital Marketing",
    "HubSpot Marketing Software",
]

_TIER2_CERTS = _TIER1_CERTS + [
    "freeCodeCamp Back End Development APIs",
    "freeCodeCamp Front End Development Libraries",
    "freeCodeCamp Quality Assurance",
    "freeCodeCamp Foundational C# Microsoft",
    "MongoDB University M001 Basics",
    "MongoDB Aggregation Skill Badge",
    "Postman API Fundamentals Student Expert",
    "CS50W Web Programming Python JavaScript",
    "Oracle Academy Java Foundations",
    "HackerRank Python SQL JavaScript",
    "freeCodeCamp Data Analysis Python",
    "freeCodeCamp Machine Learning Python",
    "CS50AI Introduction AI Python",
    "CS50SQL Introduction Databases SQL",
    "Kaggle Intermediate Machine Learning",
    "Kaggle Feature Engineering",
    "Kaggle Advanced SQL",
    "Kaggle Intro to Deep Learning",
    "IBM SkillsBuild Python Data Science",
    "AWS Educate Machine Learning Foundations",
    "GitHub Actions Learning Path",
    "IBM Introduction to DevOps",
    "IBM Introduction Containers Kubernetes OpenShift",
    "AWS Knowledge Cloud Essentials",
    "AWS Cloud Practitioner Essentials",
    "Microsoft Azure Fundamentals",
    "Cisco Cybersecurity Essentials",
    "Cisco Junior Cybersecurity Analyst",
    "Cisco Linux Essentials",
    "Fortinet NSE 3 Information Security Awareness",
    "Cisco DevNet Associate",
    "SCRUMstudy Scrum Fundamentals Certified",
]

_TIER3_CERTS = _TIER2_CERTS + [
    "Meta Front-End Developer",
    "Meta Back-End Developer",
    "Google Associate Android Developer",
    "freeCodeCamp Relational Database PostgreSQL",
    "Google Data Analytics",
    "freeCodeCamp Scientific Computing Python",
    "freeCodeCamp Data Visualization D3",
    "IBM SkillsBuild Data Fundamentals",
    "Oracle Cloud Data Management Foundations",
    "Oracle OCI AI Foundations",
    "Google Cybersecurity",
    "Cisco CyberOps Associate",
    "Linux Foundation Intro to Linux",
    "Harvard CS50P Introduction Programming Python",
    "Harvard CS50W Web Programming Python JavaScript",
    "Oracle OCI Foundations Associate",
    "Deep Learning Specialization",
    "freeCodeCamp Machine Learning with Python",
]

CERT_ALLOWLIST_BY_TIER: dict[int, list] = {
    1: _TIER1_CERTS,
    2: _TIER2_CERTS,
    3: _TIER3_CERTS,
}

_YEAR_LEVEL_TO_TIER = {
    'incoming': 1, 'pre-college': 1, 'shs': 1,
    '1st': 2, '2nd': 2,
    '3rd': 3, '4th': 3, '5th': 3, 'shifter': 3, 'transferee': 3,
}

_EXPERIENCE_TO_TIER = {
    'beginner': 1,
    'basic': 2,
    'intermediate': 3,
    'experienced': 3,
}

VALID_NODE_TYPES = frozenset({
    'milestone', 'skill', 'project', 'certification', 'final_assessment',
})

NODE_COUNT_MIN = 14
NODE_COUNT_MAX = 18


def _token_similarity(a: str, b: str) -> float:
    """Jaccard similarity on lowercased word tokens."""
    a_toks = frozenset(a.lower().split())
    b_toks = frozenset(b.lower().split())
    if not a_toks or not b_toks:
        return 0.0
    return len(a_toks & b_toks) / len(a_toks | b_toks)


def _cert_in_allowlist(cert_title: str, allowed_certs: list) -> bool:
    """True if cert_title fuzzy-matches any entry (token Jaccard ≥ 0.7)."""
    norm = cert_title.lower()
    for allowed in allowed_certs:
        if _token_similarity(norm, allowed.lower()) >= 0.7:
            return True
    return False


def _get_student_tier(year_level: str, experience_level: str = '') -> int:
    """Return the student's cert tier (1, 2, or 3). Defaults to 2 if unknown."""
    tier_y = _YEAR_LEVEL_TO_TIER.get((year_level or '').lower().strip(), 0)
    tier_e = _EXPERIENCE_TO_TIER.get((experience_level or '').lower().strip(), 0)
    return max(tier_y, tier_e, 2)


def validate_generated_roadmap(
    nodes: list,
    recommended_path: str,
    year_level: str = '',
    experience_level: str = '',
) -> tuple:
    """
    Validate a post-_fix_phase_structure node list for semantic correctness.
    Returns (ok: bool, errors: list[str]). Non-mutating.
    """
    errors = []

    # 1. Node count
    count = len(nodes)
    if not (NODE_COUNT_MIN <= count <= NODE_COUNT_MAX):
        errors.append(
            f'Node count {count} is outside allowed range '
            f'{NODE_COUNT_MIN}–{NODE_COUNT_MAX}.'
        )

    tier = _get_student_tier(year_level, experience_level)
    allowed_certs = CERT_ALLOWLIST_BY_TIER[min(tier, 3)]
    path_key = (recommended_path or '').lower().strip()
    path_keywords = PATH_KEYWORDS.get(path_key, frozenset())

    for i, node in enumerate(nodes):
        title = (node.get('title') or '').strip()
        desc = (node.get('description') or '').strip()
        node_type = node.get('node_type', '')
        difficulty = node.get('difficulty')
        label = f"Node {i + 1} ({title!r})"

        # Node type
        if node_type not in VALID_NODE_TYPES:
            errors.append(f'{label}: invalid node_type {node_type!r}.')

        # Non-empty title
        if not title:
            errors.append(f'{label}: title is empty.')
        elif len(title) > 300:
            errors.append(f'{label}: title too long ({len(title)} chars).')

        # Difficulty range
        if difficulty is not None:
            try:
                d = int(difficulty)
                if not (1 <= d <= 5):
                    errors.append(f'{label}: difficulty {d} is outside 1–5.')
            except (TypeError, ValueError):
                errors.append(
                    f'{label}: difficulty {difficulty!r} is not an integer.'
                )

        # Deprecated tech in title or description
        combined = f'{title} {desc}'.lower()
        for term in DEPRECATED_TECH:
            if term in combined:
                errors.append(
                    f'{label}: deprecated technology {term!r} detected — '
                    'replace with a currently maintained alternative.'
                )
                break

        # Cert allow-list
        if node_type == 'certification' and title:
            if not _cert_in_allowlist(title, allowed_certs):
                errors.append(
                    f'{label}: certification not in the allow-list for Tier {tier}. '
                    'Use a free cert appropriate for the student\'s level.'
                )

        # Path-drift (skill nodes only)
        if node_type == 'skill' and path_keywords:
            node_tokens = frozenset(f'{title} {desc}'.lower().split())
            if not (node_tokens & path_keywords):
                errors.append(
                    f'{label}: skill node has no keyword overlap with '
                    f"path '{recommended_path}' — possible content drift."
                )

    return len(errors) == 0, errors

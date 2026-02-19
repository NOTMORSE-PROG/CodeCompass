"""
Mentor-student compatibility scoring algorithm.
Returns a score from 0-100.
"""


def compute_compatibility(student_profile, mentor_profile) -> float:
    """
    Compute compatibility score between a student and mentor.

    Scoring breakdown (max 100):
    - Skill Overlap:        40 pts
    - Career Alignment:     30 pts
    - Mentor Availability:  20 pts
    - Mentor Rating Bonus:  10 pts
    """
    score = 0.0

    # 1. Skill overlap (40 pts)
    student_skills = set(student_profile.current_skills)
    mentor_expertise = set(mentor_profile.expertise_areas)
    overlap = student_skills & mentor_expertise

    if overlap:
        # Primary match = first overlapping skill
        skill_score = min(40, len(overlap) * 10)
        score += skill_score

    # 2. Career path alignment (30 pts)
    target = (student_profile.target_career or '').lower()
    mentor_headline = (mentor_profile.headline or '').lower()
    mentor_position = (mentor_profile.position or '').lower()

    # Adjacent career keywords
    career_keywords = {
        'web developer': ['frontend', 'backend', 'fullstack', 'web', 'django', 'react'],
        'data scientist': ['data', 'machine learning', 'ml', 'ai', 'analytics'],
        'cybersecurity': ['security', 'network', 'ethical hacking', 'infosec'],
        'mobile developer': ['android', 'ios', 'flutter', 'react native', 'mobile'],
        'devops': ['cloud', 'aws', 'azure', 'kubernetes', 'docker', 'infrastructure'],
    }

    for career, keywords in career_keywords.items():
        if target in career or any(k in target for k in keywords):
            if any(k in mentor_headline or k in mentor_position for k in keywords):
                score += 30
                break
            elif any(k in mentor_headline or k in mentor_position for k in ['developer', 'engineer', 'it']):
                score += 15
                break

    # 3. Availability (20 pts)
    if mentor_profile.is_available:
        capacity_ratio = (mentor_profile.current_mentees_count / max(mentor_profile.max_mentees, 1))
        if capacity_ratio < 0.8:
            score += 20
        elif capacity_ratio < 1.0:
            score += 10

    # 4. Rating bonus (10 pts)
    rating = float(mentor_profile.avg_rating)
    if rating >= 4.5:
        score += 10
    elif rating >= 4.0:
        score += 7
    elif rating >= 3.5:
        score += 4

    return round(min(score, 100), 2)

from rest_framework import generics, permissions
from rest_framework.response import Response
from .models import University
from .serializers import UniversitySerializer

# Career goal keyword → preferred CCS program abbreviations
CAREER_TO_PROGRAM = {
    'software engineer': ['BSCS', 'BSIT'],
    'software developer': ['BSCS', 'BSIT'],
    'web developer': ['BSCS', 'BSIT'],
    'frontend': ['BSCS', 'BSIT'],
    'backend': ['BSCS', 'BSIT'],
    'fullstack': ['BSCS', 'BSIT'],
    'data scientist': ['BSCS'],
    'data analyst': ['BSCS', 'BSIS'],
    'machine learning': ['BSCS'],
    'artificial intelligence': ['BSCS'],
    'network engineer': ['BSIT', 'BSCE'],
    'network administrator': ['BSIT'],
    'cybersecurity': ['BSIT', 'BSCS'],
    'information security': ['BSIT', 'BSCS'],
    'it support': ['BSIT', 'BSIS'],
    'systems analyst': ['BSIS', 'BSIT'],
    'business analyst': ['BSIS'],
    'database': ['BSCS', 'BSIS'],
    'mobile developer': ['BSCS', 'BSIT'],
    'game developer': ['BSCS'],
    'devops': ['BSCS', 'BSIT'],
    'cloud': ['BSCS', 'BSIT'],
    'embedded': ['BSCE'],
    'hardware': ['BSCE'],
    'computer engineer': ['BSCE'],
}


def compute_max_achievable_score(student_profile):
    """Return the highest raw score ANY university could earn for this profile.
    Used to normalize displayed percentages so they reflect fit quality,
    not how much of the profile is filled in.
    """
    student_program = getattr(student_profile, 'program', None)
    career_lower = (getattr(student_profile, 'target_career', '') or '').lower()
    year_level = getattr(student_profile, 'year_level', None)
    student_uni = getattr(student_profile, 'university', None)

    max_score = 0
    # Factor 1 — best case for this profile
    if student_program and student_program not in ('undecided', '', None):
        max_score += 40
    elif career_lower:
        max_score += 35
    # Factor 2 — CoE always achievable
    max_score += 20
    # Factor 3 — state affordability (state always > local in pts)
    max_score += 15 if year_level == 'incoming' else 8
    # Factor 4 — level 5 max accreditation
    max_score += 10
    # Factor 5 — region only if student has an enrolled university
    if student_uni and getattr(student_uni, 'region', None):
        max_score += 10
    # Factor 6 — variety
    max_score += 5
    return max_score


def compute_university_score(student_profile, university):
    """Score a university for a given student profile. Returns (score: int, reasons: list[str]).

    Max possible: 40 (program) + 20 (CHED) + 15 (affordability) + 10 (accreditation) + 10 (region) + 5 (variety) = 100
    Rebalanced so schools produce visibly different scores even for fresh/empty profiles.
    """
    score = 0
    reasons = []
    uni_programs = [p.abbreviation for p in university.programs.all()]
    career_lower = (getattr(student_profile, 'target_career', '') or '').lower()

    # ── Factor 1: Program / career match (up to 40 pts) ────────────────────
    student_program = getattr(student_profile, 'program', None)
    if student_program and student_program not in ('undecided', '', None):
        if student_program in uni_programs:
            score += 40
            reasons.append(f'Offers {student_program}')
    elif career_lower:
        for keyword, preferred in CAREER_TO_PROGRAM.items():
            if keyword in career_lower:
                matched = [pp for pp in preferred if pp in uni_programs]
                if matched:
                    score += 35
                    reasons.append(f'Offers {matched[0]} (matches your career goal)')
                break

    # ── Factor 2: CHED recognition (up to 20 pts) ──────────────────────────
    if university.ched_coe:
        score += 20
        reasons.append('CHED Center of Excellence')
    elif university.ched_cod:
        score += 10
        reasons.append('CHED Center of Development')

    # ── Factor 3: Affordability (up to 15 pts) ─────────────────────────────
    year_level = getattr(student_profile, 'year_level', None)
    uni_type = university.university_type
    if uni_type == 'state':
        pts = 15 if year_level == 'incoming' else 8
        score += pts
        reasons.append('Free tuition (RA 10931)')
    elif uni_type == 'local':
        pts = 12 if year_level == 'incoming' else 6
        score += pts
        reasons.append('City/local-funded — affordable')

    # ── Factor 4: Accreditation level (up to 10 pts) ───────────────────────
    level = university.accreditation_level or 0
    acc_pts = min(level * 2, 10)
    if acc_pts > 0:
        score += acc_pts
        reasons.append(f'Level {level} Accredited')

    # ── Factor 5: Same-region as student's enrolled university (10 pts) ────
    student_uni = getattr(student_profile, 'university', None)
    if student_uni and getattr(student_uni, 'region', None):
        if university.region == student_uni.region and university.pk != student_uni.pk:
            score += 10
            reasons.append(f'Located in {university.region}')

    # ── Factor 6: Program variety — ≥3 CCS programs offered (5 pts) ────────
    if len(uni_programs) >= 3:
        score += 5
        reasons.append(f'Offers {len(uni_programs)} CCS programs')

    return min(score, 100), reasons


class UniversityListView(generics.ListAPIView):
    """GET /api/universities/ — Browse and search universities."""
    serializer_class = UniversitySerializer
    permission_classes = [permissions.IsAuthenticated]
    search_fields = ['name', 'abbreviation', 'city', 'region']
    filterset_fields = ['university_type', 'region', 'ched_coe', 'ched_cod']

    def get_queryset(self):
        return University.objects.filter(is_active=True).prefetch_related('programs')


class UniversityRecommendationsView(generics.GenericAPIView):
    """GET /api/universities/recommendations/ — Top 5 universities matched to the student's profile."""
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        try:
            profile = request.user.student_profile
        except Exception:
            # No student profile — return best schools ordered by CoE → CoD → accreditation → NCR
            unis = (
                University.objects.filter(is_active=True)
                .prefetch_related('programs')
                .order_by('-ched_coe', '-ched_cod', '-accreditation_level', 'name')[:6]
            )
            return Response(UniversitySerializer(unis, many=True).data)

        universities = University.objects.filter(is_active=True).prefetch_related('programs')
        scored = []
        for uni in universities:
            score, reasons = compute_university_score(profile, uni)
            if score > 0:
                scored.append((score, reasons, uni))

        # Sort: score DESC → state first → NCR first → alpha
        scored.sort(key=lambda x: (
            -x[0],
            0 if x[2].university_type == 'state' else 1,
            0 if x[2].region == 'NCR' else 1,
            x[2].name,
        ))

        max_achievable = max(compute_max_achievable_score(profile), 1)
        data = []
        for score, reasons, uni in scored[:6]:
            normalized = min(round(score / max_achievable * 100), 100)
            uni_data = UniversitySerializer(uni).data
            uni_data['matchScore'] = normalized
            uni_data['matchReasons'] = reasons
            data.append(uni_data)

        return Response(data)


class UniversityDetailView(generics.RetrieveAPIView):
    """GET /api/universities/{id}/ — University detail with programs."""
    serializer_class = UniversitySerializer
    permission_classes = [permissions.IsAuthenticated]
    queryset = University.objects.filter(is_active=True).prefetch_related('programs')

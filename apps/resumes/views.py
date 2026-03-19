"""
Resume CRUD and AI-powered generation endpoints.
"""
import logging
from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from .models import Resume
from .serializers import ResumeSerializer, ResumeListSerializer
from . import groq_client

logger = logging.getLogger('resumes.views')


class ResumeListCreateView(generics.ListCreateAPIView):
    """
    GET  /api/resumes/         — List current user's resumes (lightweight)
    POST /api/resumes/         — Create a new resume
    """
    permission_classes = [permissions.IsAuthenticated]

    def get_serializer_class(self):
        if self.request.method == 'GET':
            return ResumeListSerializer
        return ResumeSerializer

    def get_queryset(self):
        return Resume.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class ResumeDetailView(generics.RetrieveUpdateDestroyAPIView):
    """
    GET    /api/resumes/{id}/  — Get full resume with content
    PATCH  /api/resumes/{id}/  — Update resume (partial update)
    DELETE /api/resumes/{id}/  — Delete resume
    """
    serializer_class = ResumeSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Resume.objects.filter(user=self.request.user)


# ---------------------------------------------------------------------------
# AI Generation Endpoints
# ---------------------------------------------------------------------------

@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def generate_bullets(request, pk):
    """
    POST /api/resumes/{id}/generate-bullets/
    Body: { jobTitle, achievement }
    Returns: { bullets: ["...", ...] }
    """
    try:
        Resume.objects.get(pk=pk, user=request.user)
    except Resume.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    job_title = request.data.get('job_title') or request.data.get('jobTitle', '')
    achievement = request.data.get('achievement', '')

    if not job_title or not achievement:
        return Response(
            {'detail': 'jobTitle and achievement are required.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        bullets = groq_client.generate_bullet_points(job_title, achievement)
        return Response({'bullets': bullets})
    except Exception:
        logger.exception('generate_bullets failed for user %s', request.user.pk)
        return Response(
            {'detail': 'AI service temporarily unavailable. Please try again.'},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def generate_summary(request, pk):
    """
    POST /api/resumes/{id}/generate-summary/
    Body: { targetRole, strengths[], yearsExp }
    Returns: { summaries: [{tone, text}, ...] }
    """
    try:
        resume = Resume.objects.get(pk=pk, user=request.user)
    except Resume.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    target_role = request.data.get('target_role') or request.data.get('targetRole', '')
    strengths = request.data.get('strengths', [])
    years_exp = request.data.get('years_exp') or request.data.get('yearsExp', 'entry-level')

    if not target_role:
        return Response(
            {'detail': 'targetRole is required.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        resume_content = resume.content
        summaries = groq_client.generate_summary(target_role, strengths, years_exp, resume_content)
        return Response({'summaries': summaries})
    except Exception:
        logger.exception('generate_summary failed for user %s', request.user.pk)
        return Response(
            {'detail': 'AI service temporarily unavailable. Please try again.'},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def parse_job_description(request):
    """
    POST /api/resumes/parse-job/
    Body: { jobDescription }
    Returns: { requiredSkills, niceToHaveSkills, keywords, experienceLevel, responsibilities }
    """
    job_description = request.data.get('job_description') or request.data.get('jobDescription', '')
    if not job_description or len(job_description) < 50:
        return Response(
            {'detail': 'jobDescription must be at least 50 characters.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        result = groq_client.parse_job_description(job_description)
        return Response(result)
    except Exception:
        logger.exception('parse_job_description failed for user %s', request.user.pk)
        return Response(
            {'detail': 'AI service temporarily unavailable. Please try again.'},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )


@api_view(['POST'])
@permission_classes([permissions.IsAuthenticated])
def score_ats(request, pk):
    """
    POST /api/resumes/{id}/score-ats/
    Body: { parsedJob: { jobTitle, requiredSkills, niceToHaveSkills, keywords } }
          OR legacy: { jobKeywords[] }
    Returns: { score, breakdown, matchedKeywords, missingRequired, missingPreferred,
               missingGeneral, suggestions, scoreLabel }
    """
    try:
        resume = Resume.objects.get(pk=pk, user=request.user)
    except Resume.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    parsed_job = request.data.get('parsedJob') or request.data.get('parsed_job', {})

    if parsed_job:
        job_title = parsed_job.get('jobTitle') or parsed_job.get('job_title', '')
        required = parsed_job.get('requiredSkills', [])
        preferred = parsed_job.get('niceToHaveSkills', [])
        general = parsed_job.get('keywords', [])
    else:
        # Backward compat
        job_title = ''
        flat = request.data.get('job_keywords') or request.data.get('jobKeywords', [])
        required = flat
        preferred = []
        general = []

    if not required and not preferred and not general:
        return Response(
            {'detail': 'parsedJob or jobKeywords is required.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    content = resume.content
    resume_text = _extract_resume_text(content).lower()

    # 3-tier weighted keyword scoring
    keyword_score, req_matched, pref_matched, gen_matched = _keyword_score(
        required, preferred, general, resume_text
    )

    # Title relevance score
    title_score = _title_score(job_title, content)

    # Section quality score
    structure_score = _structure_score(content)

    # Weighted total: keywords 50%, title 25%, structure 25%
    total_score = round(keyword_score * 0.50 + title_score * 0.25 + structure_score * 0.25)

    missing_required = [k for k in required if k not in req_matched]
    missing_preferred = [k for k in preferred if k not in pref_matched]
    missing_general = [k for k in general if k not in gen_matched]

    suggestions = []
    tagged_missing = (
        [f'[REQUIRED] {k}' for k in missing_required[:8]] +
        [f'[PREFERRED] {k}' for k in missing_preferred[:5]] +
        [f'[GENERAL] {k}' for k in missing_general[:4]]
    )
    if tagged_missing:
        try:
            suggestions = groq_client.get_ats_suggestions(tagged_missing)
        except Exception:
            pass

    return Response({
        'score': total_score,
        'breakdown': {
            'keywordScore': keyword_score,
            'titleScore': title_score,
            'structureScore': structure_score,
        },
        'matchedKeywords': req_matched + pref_matched + gen_matched,
        'missingRequired': missing_required,
        'missingPreferred': missing_preferred,
        'missingGeneral': missing_general,
        'suggestions': suggestions,
        'scoreLabel': _score_label(total_score),
    })


def _keyword_score(required, preferred, general, resume_text):
    req_matched = [k for k in required if k.lower() in resume_text]
    pref_matched = [k for k in preferred if k.lower() in resume_text]
    gen_matched = [k for k in general if k.lower() in resume_text]
    total_weight = len(required) * 3 + len(preferred) * 2 + len(general) * 1
    matched_weight = len(req_matched) * 3 + len(pref_matched) * 2 + len(gen_matched) * 1
    score = round(matched_weight / total_weight * 100) if total_weight else 0
    return score, req_matched, pref_matched, gen_matched


def _title_score(job_title, resume_content):
    if not job_title:
        return 50
    job_words = set(w for w in job_title.lower().split() if len(w) > 2)
    resume_title_text = (
        resume_content.get('summary', '') + ' ' +
        ' '.join(e.get('title', '') for e in resume_content.get('experience', [])[:2])
    ).lower()
    matched = sum(1 for w in job_words if w in resume_title_text)
    return round(matched / len(job_words) * 100) if job_words else 50


def _structure_score(content):
    checks = [
        bool(content.get('summary', '').strip()),
        len(content.get('skills', {}).get('technical', [])) >= 3,
        bool(content.get('experience', [])),
        any(e.get('bullets') for e in content.get('experience', [])),
        bool(content.get('education', [])),
    ]
    return round(sum(checks) / len(checks) * 100)


def _score_label(score):
    if score >= 85:
        return 'Excellent Match'
    if score >= 70:
        return 'Good Match'
    if score >= 55:
        return 'Fair Match'
    return 'Weak Match'


def _extract_resume_text(content: dict) -> str:
    """Flatten all resume content fields into a single searchable text string."""
    parts = []

    if content.get('summary'):
        parts.append(content['summary'])

    for exp in content.get('experience', []):
        parts.append(exp.get('title', ''))
        parts.append(exp.get('company', ''))
        parts.extend(exp.get('bullets', []))

    for edu in content.get('education', []):
        parts.append(edu.get('degree', ''))
        parts.append(edu.get('field', ''))
        parts.append(edu.get('school', ''))

    skills = content.get('skills', {})
    parts.extend(skills.get('technical', []))
    parts.extend(skills.get('soft', []))
    parts.extend(skills.get('tools', []))

    for proj in content.get('projects', []):
        parts.append(proj.get('name', ''))
        parts.append(proj.get('description', ''))
        parts.extend(proj.get('tech', []))

    for cert in content.get('certifications', []):
        parts.append(cert.get('name', ''))
        parts.append(cert.get('issuer', ''))

    return ' '.join(filter(None, parts))

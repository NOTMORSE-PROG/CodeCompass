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

    job_title = request.data.get('jobTitle', '')
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
        Resume.objects.get(pk=pk, user=request.user)
    except Resume.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    target_role = request.data.get('targetRole', '')
    strengths = request.data.get('strengths', [])
    years_exp = request.data.get('yearsExp', 'entry-level')

    if not target_role:
        return Response(
            {'detail': 'targetRole is required.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        summaries = groq_client.generate_summary(target_role, strengths, years_exp)
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
    job_description = request.data.get('jobDescription', '')
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
    Body: { jobKeywords[] }
    Returns: { score, matchedKeywords[], missingKeywords[], suggestions[] }
    """
    try:
        resume = Resume.objects.get(pk=pk, user=request.user)
    except Resume.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    job_keywords = request.data.get('jobKeywords', [])
    if not job_keywords:
        return Response(
            {'detail': 'jobKeywords array is required.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    # Build a flat text blob from the resume content for keyword matching
    content = resume.content
    resume_text = _extract_resume_text(content).lower()

    matched = []
    missing = []
    for keyword in job_keywords:
        if keyword.lower() in resume_text:
            matched.append(keyword)
        else:
            missing.append(keyword)

    total = len(job_keywords)
    score = round((len(matched) / total) * 100) if total > 0 else 0

    # Get AI suggestions for missing keywords
    suggestions = []
    if missing:
        try:
            suggestions = groq_client.get_ats_suggestions(missing)
        except Exception:
            pass

    return Response({
        'score': score,
        'matchedKeywords': matched,
        'missingKeywords': missing,
        'suggestions': suggestions,
    })


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

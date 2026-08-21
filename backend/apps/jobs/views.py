import logging
import time
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import APIView
from .models import JobListing, SavedJob
from .serializers import JobListingSerializer, SavedJobSerializer

logger = logging.getLogger(__name__)

# Cooldown: don't retry the sync within 5 minutes of a failed attempt
_last_sync_attempt: float = 0
_SYNC_COOLDOWN = 300  # seconds


def _ensure_jobs_seeded():
    """Auto-sync jobs from external APIs if DB has no active, non-expired listings."""
    global _last_sync_attempt

    has_jobs = JobListing.objects.filter(
        is_active=True,
        expires_at__gt=timezone.now(),
    ).exists()
    if has_jobs:
        return

    now = time.monotonic()
    if now - _last_sync_attempt < _SYNC_COOLDOWN:
        return  # Still in cooldown from last failed attempt

    _last_sync_attempt = now
    from .jobs_client import sync_jobs
    # Fetch a diverse set of PH IT roles so the recommended engine has variety to match against
    for kw in ['software developer', 'web developer', 'data analyst', 'mobile developer', 'IT support']:
        sync_jobs(keywords=kw)


class JobListView(generics.ListAPIView):
    """GET /api/jobs/ — Browse job listings with optional keyword filter."""
    serializer_class = JobListingSerializer
    permission_classes = [permissions.IsAuthenticated]
    search_fields = ['title', 'company', 'location', 'description']
    filterset_fields = ['job_type', 'experience_level', 'is_philippines_based']

    def get_queryset(self):
        _ensure_jobs_seeded()
        return JobListing.objects.filter(is_active=True)


class RecommendedJobsView(APIView):
    """GET /api/jobs/recommended/ — Top 6 jobs matched to the user's profile."""
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        # Don't trigger a second sync here — JobListView handles seeding.
        # Recommended just queries whatever is already in the DB.

        keywords = []

        # Pull career goal from StudentProfile
        try:
            profile = request.user.student_profile
            if profile.target_career:
                keywords.append(profile.target_career)
            if profile.current_skills:
                keywords.extend(profile.current_skills[:3])
        except Exception:
            pass

        # Pull career path from the user's latest roadmap
        try:
            roadmap = request.user.roadmaps.order_by('-created_at').first()
            if roadmap and roadmap.career_path:
                keywords.append(roadmap.career_path)
        except Exception:
            pass

        base_qs = JobListing.objects.filter(is_active=True)

        if keywords:
            from django.db.models import Q
            # Tokenise: split multi-word phrases so "Full Stack Developer"
            # matches jobs containing "Developer" or "Stack" individually.
            tokens = set()
            for kw in keywords:
                for word in kw.split():
                    w = word.strip()
                    if len(w) >= 3:  # skip very short noise words
                        tokens.add(w)

            query = Q()
            for token in tokens:
                query |= Q(title__icontains=token) | Q(description__icontains=token)

            jobs = base_qs.filter(query)[:6]
            # Fall back to latest jobs if no match at all
            if not jobs.exists():
                jobs = base_qs[:6]
        else:
            jobs = base_qs[:6]

        serializer = JobListingSerializer(jobs, many=True)
        return Response(serializer.data)


def _match_jobs_from_resume_text(resume_text: str) -> list:
    """
    Shared pipeline: Groq skill extraction → keyword tokenisation → JobListing
    query → serialized list of up to 8 jobs, each annotated with match_terms.
    Callers are responsible for validating the length contract appropriate to
    their source.
    """
    # Ensure there are jobs in the DB to match against
    _ensure_jobs_seeded()

    from .groq_client import extract_resume_skills
    extracted = extract_resume_skills(resume_text)

    keywords = (
        extracted.get('skills', []) +
        extracted.get('roles', []) +
        extracted.get('keywords', [])
    )

    base_qs = JobListing.objects.filter(is_active=True)

    if keywords:
        from django.db.models import Q
        tokens = set()
        for kw in keywords:
            for word in str(kw).split():
                w = word.strip()
                if len(w) >= 3:
                    tokens.add(w)

        query = Q()
        for token in tokens:
            query |= (
                Q(title__icontains=token) |
                Q(description__icontains=token) |
                Q(required_skills__icontains=token)
            )

        jobs = list(base_qs.filter(query)[:8])
        if not jobs:
            jobs = list(base_qs.order_by('-fetched_at')[:8])
    else:
        jobs = list(base_qs.order_by('-fetched_at')[:8])

    # Compute which user keywords actually appear in each job (for UI explanations)
    def matched_terms_for(job):
        haystack = ' '.join([
            job.title or '',
            job.description or '',
            str(job.required_skills or ''),
        ]).lower()
        seen = []
        for kw in keywords:
            kw_str = str(kw).strip()
            if kw_str and kw_str.lower() in haystack and kw_str not in seen:
                seen.append(kw_str)
        return seen[:6]  # cap at 6 to keep UI clean

    serializer = JobListingSerializer(jobs, many=True)
    results = []
    for job_data, job_obj in zip(serializer.data, jobs):
        results.append({**job_data, 'match_terms': matched_terms_for(job_obj)})
    return results


class RecommendFromResumeView(APIView):
    """POST /api/jobs/recommend-from-resume/ — Top 8 jobs matched to an uploaded resume PDF."""
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        resume_text = request.data.get('resume_text', '').strip()
        if len(resume_text) < 100:
            return Response(
                {'detail': 'Resume text too short. Please upload a valid resume PDF.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        return Response(_match_jobs_from_resume_text(resume_text))


class RecommendFromResumeIdView(APIView):
    """
    POST /api/jobs/recommend-from-resume-id/
    Body: { "resume_id": int }
    Returns the same shape as RecommendFromResumeView but uses a stored resume
    as the source — the resume's structured content is flattened to text via
    the resumes app's _extract_resume_text helper, then fed through the
    shared matching pipeline.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        raw_id = request.data.get('resume_id')
        try:
            resume_id = int(raw_id)
        except (TypeError, ValueError):
            return Response(
                {'detail': 'resume_id is required and must be an integer.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        from apps.resumes.models import Resume
        from apps.resumes.views import _extract_resume_text

        # Queryset scoped to request.user — an unowned or non-existent id
        # returns the same 404 (don't leak existence of other users' resumes).
        try:
            resume = Resume.objects.get(pk=resume_id, user=request.user)
        except Resume.DoesNotExist:
            return Response(
                {'detail': 'Resume not found.'},
                status=status.HTTP_404_NOT_FOUND,
            )

        resume_text = _extract_resume_text(resume.content or {})
        if len(resume_text) < 50:
            return Response(
                {'detail': 'This resume has too little content to match jobs. '
                           'Add experience, skills, or a summary first.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            return Response(_match_jobs_from_resume_text(resume_text))
        except Exception:
            logger.exception(
                'recommend-from-resume-id failed for resume %s', resume_id,
            )
            return Response(
                {'detail': 'Matching failed. Please try again.'},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )


class SavedJobListView(generics.ListAPIView):
    """GET /api/jobs/saved/ — User's saved jobs."""
    serializer_class = SavedJobSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return SavedJob.objects.filter(user=self.request.user).select_related('job')


@api_view(['POST', 'DELETE'])
@permission_classes([permissions.IsAuthenticated])
def save_job(request, pk):
    """POST /api/jobs/{pk}/save/ — save. DELETE /api/jobs/{pk}/save/ — unsave."""
    if request.method == 'DELETE':
        SavedJob.objects.filter(user=request.user, job_id=pk).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

    try:
        job = JobListing.objects.get(pk=pk, is_active=True)
    except JobListing.DoesNotExist:
        return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)

    _, created = SavedJob.objects.get_or_create(user=request.user, job=job)
    if created:
        return Response({'detail': 'Job saved.'}, status=status.HTTP_201_CREATED)
    return Response({'detail': 'Already saved.'})

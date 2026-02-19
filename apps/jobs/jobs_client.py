"""
Philippine job listings client.
Primary source:  Careerjet API  (en_PH locale — dedicated PH job coverage)
                 Uses the official careerjet-api Python SDK.
Fallback source: JSearch / RapidAPI (aggregates Google for Jobs PH listings)

API keys are read from Django settings (from .env) — never hardcoded.
24-hour caching strategy: only call external APIs when cache/DB is stale.
"""
import logging
import requests
from datetime import timedelta
from django.conf import settings
from django.utils import timezone

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Careerjet (primary) — official Python SDK
# ---------------------------------------------------------------------------

def _fetch_careerjet(keywords: str, location: str, count: int, user_ip: str, user_agent: str) -> list:
    """
    Fetch jobs using the official careerjet-api Python client.
    locale_code 'en_PH' targets careerjet.ph — dedicated Philippines listings.
    """
    from careerjet_api import CareerjetAPIClient

    affid = getattr(settings, 'CAREERJET_AFFID', '')
    if not affid:
        logger.warning('CAREERJET_AFFID not set — skipping Careerjet fetch')
        return []

    frontend_url = getattr(settings, 'FRONTEND_URL', 'http://localhost:5173')

    try:
        cj = CareerjetAPIClient('en_PH')
        result = cj.search({
            'affid':      affid,
            'keywords':   keywords,
            'location':   location,
            'pagesize':   count,
            'page':       1,
            'sort':       'date',       # most recent first
            'user_ip':    user_ip,
            'url':        frontend_url + '/jobs',
            'user_agent': user_agent,
        })

        if result.get('type') != 'JOBS':
            return []

        jobs = []
        for job in result.get('jobs', []):
            # Use URL tail as a stable external ID (unique per listing)
            url = job.get('url', '')
            ext_id = url[-100:] if url else ''
            jobs.append({
                'external_id':  ext_id,
                'title':        job.get('title', '')[:300],
                'company':      job.get('company', '')[:200],
                'location':     job.get('locations', '')[:200],
                'salary_range': job.get('salary', '')[:100],
                'description':  job.get('description', ''),
                'apply_url':    url,
                'job_type':     '',     # Careerjet doesn't return a structured job type
                'source':       'careerjet',
            })
        return jobs

    except Exception as exc:
        logger.error('Careerjet fetch failed: %s', exc)
        return []


# ---------------------------------------------------------------------------
# JSearch / RapidAPI (fallback)
# ---------------------------------------------------------------------------
JSEARCH_API_URL = 'https://jsearch.p.rapidapi.com/search'


def _fetch_jsearch(keywords: str, location: str, count: int) -> list:
    """
    Fetch jobs from JSearch (aggregates Google for Jobs PH data).
    200 free requests/month on RapidAPI free tier.
    """
    rapidapi_key = getattr(settings, 'JSEARCH_RAPIDAPI_KEY', '')
    if not rapidapi_key:
        logger.warning('JSEARCH_RAPIDAPI_KEY not set — skipping JSearch fetch')
        return []

    headers = {
        'X-RapidAPI-Key':  rapidapi_key,
        'X-RapidAPI-Host': 'jsearch.p.rapidapi.com',
    }
    params = {
        'query':      f'{keywords} in {location}',
        'page':       '1',
        'num_pages':  '1',
        'country':    'ph',           # Philippines country filter
        'date_posted': 'month',
    }

    try:
        response = requests.get(JSEARCH_API_URL, headers=headers, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()

        jobs = []
        for job in data.get('data', [])[:count]:
            city = job.get('job_city', '')
            country = job.get('job_country', '')
            loc = f'{city}, {country}'.strip(', ')

            jobs.append({
                'external_id':  job.get('job_id', '')[:100],
                'title':        job.get('job_title', '')[:300],
                'company':      job.get('employer_name', '')[:200],
                'location':     loc[:200],
                'salary_range': _format_jsearch_salary(job),
                'description':  job.get('job_description', '')[:2000],
                'apply_url':    job.get('job_apply_link', ''),
                'job_type':     _map_jsearch_employment(job.get('job_employment_type', '')),
                'source':       'jsearch',
            })
        return jobs

    except Exception as exc:
        logger.error('JSearch fetch failed: %s', exc)
        return []


def _format_jsearch_salary(job: dict) -> str:
    min_s = job.get('job_min_salary')
    max_s = job.get('job_max_salary')
    period = job.get('job_salary_period', '').lower()
    currency = job.get('job_salary_currency', 'PHP')
    if min_s and max_s:
        return f'{currency} {int(min_s):,} – {int(max_s):,}/{period}'
    return ''


def _map_jsearch_employment(employment_type: str) -> str:
    mapping = {
        'FULLTIME':   'full_time',
        'PARTTIME':   'part_time',
        'INTERN':     'internship',
        'CONTRACTOR': 'freelance',
    }
    return mapping.get(employment_type.upper(), '')


# ---------------------------------------------------------------------------
# Public interface
# ---------------------------------------------------------------------------

def fetch_jobs(
    keywords: str = 'software engineer',
    location: str = 'Philippines',
    count: int = 20,
    user_ip: str = '127.0.0.1',
    user_agent: str = 'CodeCompass/1.0',
) -> list:
    """
    Fetch PH job listings.
    Tries Careerjet first; falls back to JSearch if Careerjet is unconfigured
    or returns no results.
    """
    jobs = _fetch_careerjet(keywords, location, count, user_ip, user_agent)
    if not jobs:
        jobs = _fetch_jsearch(keywords, location, count)
    return jobs


def sync_jobs(
    keywords: str = 'software developer',
    location: str = 'Philippines',
    user_ip: str = '127.0.0.1',
    user_agent: str = 'CodeCompass/1.0',
) -> int:
    """
    Fetch from Careerjet (+ JSearch fallback) and upsert into JobListing table.
    Called by a Celery beat task every 24 hours.
    Returns count of jobs saved/updated.
    """
    from .models import JobListing

    raw_jobs = fetch_jobs(keywords=keywords, location=location, user_ip=user_ip, user_agent=user_agent)
    saved = 0
    expiry = timezone.now() + timedelta(hours=24)

    for job in raw_jobs:
        ext_id = job.get('external_id', '')
        if not ext_id:
            continue

        defaults = {
            'title':        job.get('title', '')[:300],
            'company':      job.get('company', '')[:200],
            'location':     job.get('location', '')[:200],
            'salary_range': job.get('salary_range', '')[:100],
            'description':  job.get('description', ''),
            'apply_url':    job.get('apply_url', ''),
            'expires_at':   expiry,
            'is_active':    True,
        }
        if job.get('job_type'):
            defaults['job_type'] = job['job_type']

        JobListing.objects.update_or_create(external_id=ext_id, defaults=defaults)
        saved += 1

    return saved

"""
Philippine job listings client.
Primary source:  Careerjet HTTP API  (en_PH locale — dedicated PH job coverage)
                 Calls search.api.careerjet.net directly — no broken SDK dependency.
Fallback 1:      Jooble API (free key, global aggregator, PH location search)
Fallback 2:      JSearch / RapidAPI (200 free req/month — last resort)

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
# Careerjet (primary) — direct HTTP API, no SDK required
# ---------------------------------------------------------------------------
CAREERJET_API_URL = 'https://search.api.careerjet.net/v4/query'


def _fetch_careerjet(keywords: str, location: str, count: int, user_ip: str, user_agent: str) -> list:
    """
    Fetch jobs from Careerjet HTTP API directly (bypasses the broken careerjet_api SDK).
    locale_code 'en_PH' targets careerjet.ph — dedicated Philippines listings.
    """
    affid = getattr(settings, 'CAREERJET_AFFID', '')
    if not affid:
        logger.warning('CAREERJET_AFFID not set — skipping Careerjet fetch')
        return []

    frontend_url = getattr(settings, 'FRONTEND_URL', 'http://localhost:5173')

    try:
        params = {
            'affid':       affid,
            'keywords':    keywords,
            'location':    location,
            'locale_code': 'en_PH',
            'sort':        'date',
            'pagesize':    count,
            'page':        1,
            'user_ip':     user_ip,
            'url':         frontend_url + '/jobs',
            'user_agent':  user_agent,
        }
        response = requests.get(CAREERJET_API_URL, params=params, timeout=10)
        response.raise_for_status()
        result = response.json()

        if result.get('type') != 'JOBS':
            return []

        jobs = []
        for job in result.get('jobs', []):
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
                'job_type':     '',
                'source':       'careerjet',
            })
        return jobs

    except Exception as exc:
        logger.error('Careerjet fetch failed: %s', exc)
        return []


# ---------------------------------------------------------------------------
# Jooble (fallback 1) — free API key, global aggregator with PH coverage
# Register at: https://jooble.org/api/about  (self-service, free)
# ---------------------------------------------------------------------------
JOOBLE_API_BASE = 'https://jooble.org/api'


def _fetch_jooble(keywords: str, location: str, count: int) -> list:
    """
    Fetch jobs from Jooble — free API, aggregates PH job boards globally.
    Requires JOOBLE_API_KEY in settings (get free key at jooble.org/api/about).
    """
    api_key = getattr(settings, 'JOOBLE_API_KEY', '')
    if not api_key:
        logger.warning('JOOBLE_API_KEY not set — skipping Jooble fetch')
        return []

    try:
        response = requests.post(
            f'{JOOBLE_API_BASE}/{api_key}',
            json={'keywords': keywords, 'location': location, 'page': '1'},
            timeout=10,
        )
        response.raise_for_status()
        data = response.json()

        jobs = []
        for job in data.get('jobs', [])[:count]:
            link = job.get('link', '')
            ext_id = link[-100:] if link else ''
            if not ext_id:
                continue
            jobs.append({
                'external_id':  ext_id,
                'title':        job.get('title', '')[:300],
                'company':      job.get('company', '')[:200],
                'location':     job.get('location', '')[:200],
                'salary_range': job.get('salary', '')[:100],
                'description':  job.get('snippet', ''),
                'apply_url':    link,
                'job_type':     _map_jooble_type(job.get('type', '')),
                'source':       'jooble',
            })
        return jobs

    except Exception as exc:
        logger.error('Jooble fetch failed: %s', exc)
        return []


def _map_jooble_type(job_type: str) -> str:
    mapping = {
        'Full-time':  'full_time',
        'Part-time':  'part_time',
        'Internship': 'internship',
        'Contract':   'freelance',
        'Remote':     'remote',
    }
    return mapping.get(job_type, '')


# ---------------------------------------------------------------------------
# JSearch / RapidAPI (fallback 2 — last resort, 200 free req/month)
# ---------------------------------------------------------------------------
JSEARCH_API_URL = 'https://jsearch.p.rapidapi.com/search'


def _fetch_jsearch(keywords: str, location: str, count: int) -> list:
    """
    Fetch jobs from JSearch (aggregates Google for Jobs PH data).
    200 free requests/month on RapidAPI free tier — use sparingly.
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
        'query':       f'{keywords} in {location}',
        'page':        '1',
        'num_pages':   '1',
        'country':     'ph',
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
                'description':  job.get('job_description', ''),
                'apply_url':    job.get('job_apply_link', ''),
                'job_type':     _map_jsearch_employment(job.get('job_employment_type', '') or ''),
                'source':       'jsearch',
            })
        return jobs

    except Exception as exc:
        logger.error('JSearch fetch failed: %s', exc)
        return []


def _format_jsearch_salary(job: dict) -> str:
    min_s = job.get('job_min_salary')
    max_s = job.get('job_max_salary')
    period = (job.get('job_salary_period') or '').lower()
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
    Priority: Careerjet HTTP → Jooble → JSearch
    """
    jobs = _fetch_careerjet(keywords, location, count, user_ip, user_agent)
    if not jobs:
        jobs = _fetch_jooble(keywords, location, count)
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
    Fetch from APIs and upsert into JobListing table.
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

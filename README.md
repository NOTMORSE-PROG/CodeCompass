# CodeCompass — Backend

Django REST API and real-time WebSocket server for CodeCompass, an AI-driven career guide and personalized learning roadmap platform built for CCS students in the Philippines.

---

## Features

**AI Career Guidance**
- Real-time streaming AI chat powered by Groq (`llama-3.3-70b-versatile`)
- Personalized system prompts built from each student's profile, roadmap progress, and onboarding summary
- Multiple chat modes: general career advice, roadmap coaching, job search guidance, resume help, interview prep
- Chat session history with auto-generated titles from the first message

**AI Onboarding**
- Conversational onboarding via WebSocket — the AI collects the student's year level, program, goals, and interests through natural dialogue
- Automatically upgrades the user role to `incoming_student` if the AI detects a pre-college user
- Structured profile extracted and saved at completion (goals, track, interests, year, program)

**Personalized Roadmaps**
- AI generates a full learning roadmap tailored to the student's career goal and profile
- Roadmap nodes include type (skill, project, assessment, certification), difficulty, estimated hours, and YouTube resources
- Students track node progress (locked → available → in progress → completed)
- In-app node editor: add, edit, remove nodes without regenerating the entire roadmap
- Completion percentage excludes milestone nodes so it reflects only real student work

**Resume Builder**
- Full resume CRUD with JSON-structured content (sections: personal info, experience, education, skills, projects, certifications)
- AI-powered bullet point generation from job title + responsibilities
- AI professional summary generation
- Job description parser — extracts requirements from a job posting
- ATS compatibility scorer with improvement suggestions

**Gamification**
- XP awarded for completing roadmap nodes, finishing onboarding, earning certifications, and other actions
- Badge system with automatic award logic in the XP engine
- Leaderboard ranked by total XP
- Streak tracking

**Jobs**
- Live job listings aggregated from Careerjet (PH locale) and JSearch via RapidAPI
- Save and unsave job listings per user

**Certifications**
- Catalog of industry certifications (TESDA, Google, AWS, and others)
- Per-user tracking with status (in progress, completed) and completion date

**Universities**
- Philippine university and CCS program catalog for reference during onboarding and profile building

**Resources**
- YouTube video resources automatically attached to roadmap nodes via YouTube Data API v3

**Authentication**
- Email/password registration and login with JWT (access + refresh tokens)
- Google OAuth — sign in or register with a Google account
- Link a Google account to an existing email/password account
- Role-based access control (`undergraduate`, `incoming_student`, `admin`)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Django 5.1 + Django REST Framework |
| Real-time | Django Channels 4 + Daphne (ASGI) |
| Database | PostgreSQL via Neon (SSL) |
| Cache / Broker | Redis |
| Task queue | Celery + django-celery-beat |
| AI | Groq API |
| Auth | SimpleJWT + Google OAuth |
| Job data | Careerjet API + JSearch (RapidAPI) |
| Video resources | YouTube Data API v3 |

---

## Local Setup

### Prerequisites

- Python 3.11+
- PostgreSQL (or a [Neon](https://neon.tech) connection string)
- Redis

### Steps

```bash
git clone https://github.com/NOTMORSE-PROG/CodeCompass_Backend.git
cd CodeCompass_Backend

python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate

pip install -r requirements.txt
```

Create a `.env` file in the project root:

```env
SECRET_KEY=your-django-secret-key
DEBUG=True
ALLOWED_HOSTS=localhost,127.0.0.1

DATABASE_URL=postgresql://user:password@host/dbname?sslmode=require
REDIS_URL=redis://localhost:6379/0

GROQ_API_KEY=your-groq-api-key
GOOGLE_CLIENT_ID=your-google-oauth-client-id
YOUTUBE_API_KEY=your-youtube-api-key
RAPIDAPI_KEY=your-rapidapi-key

CORS_ALLOWED_ORIGINS=http://localhost:5173
```

```bash
python manage.py migrate
python manage.py createsuperuser   # optional

# Seed the badge catalog
python manage.py seed_badges

# Start the ASGI server (HTTP + WebSocket)
daphne config.asgi:application --port 8000 --bind 0.0.0.0

# Separate terminal — background task worker
celery -A config worker --loglevel=info
```

---

## Environment Variables

| Variable | Description |
|---|---|
| `SECRET_KEY` | Django secret key |
| `DEBUG` | `True` locally, `False` in production |
| `DATABASE_URL` | PostgreSQL connection string |
| `REDIS_URL` | Redis connection URL |
| `GROQ_API_KEY` | Groq API key |
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 client ID |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key |
| `RAPIDAPI_KEY` | RapidAPI key for job listings |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins |

---

## Deployment

Deployed on **Render**. Database on **Neon PostgreSQL**. Cache and Channels layer on **Redis**.

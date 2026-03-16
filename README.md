# CodeCompass — Backend API

Django REST API + Django Channels WebSocket server powering CodeCompass, an AI-driven career guide and personalized learning roadmap platform for CCS students in the Philippines.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Django 5.1 + Django REST Framework |
| Real-time | Django Channels 4 + Daphne (ASGI) |
| Database | PostgreSQL via Neon (SSL) |
| Cache / Broker | Redis + django-redis |
| Task queue | Celery + django-celery-beat |
| AI | Groq API — `llama-3.3-70b-versatile` |
| Auth | JWT (SimpleJWT) + Google OAuth |
| Job listings | Careerjet API + JSearch (RapidAPI) |
| Video resources | YouTube Data API v3 |

---

## Project Structure

```
backend/
  apps/
    accounts/        — CustomUser, StudentProfile, JWT auth, Google OAuth
    ai_assistant/    — WebSocket chat consumer, Groq streaming, chat sessions
    onboarding/      — AI-guided onboarding, student profiling
    roadmaps/        — AI roadmap generation, node progress, assessment sessions
    resumes/         — Resume CRUD, AI bullet/summary generation, ATS scoring
    gamification/    — XP engine, badges, leaderboard, streak tracking
    certifications/  — TESDA / Google / AWS cert catalog + user tracking
    universities/    — Philippine universities + CCS program catalog
    jobs/            — Job listings via Careerjet + JSearch
    resources/       — YouTube resource integration per roadmap node
  config/
    settings/        — base.py (env-driven), production overrides
    urls.py          — root URL conf
    asgi.py          — ASGI app + Channels routing
    celery.py        — Celery app config
```

---

## API Endpoints

| Prefix | Description |
|---|---|
| `POST /api/auth/register/` | Register a new account |
| `POST /api/auth/login/` | Obtain JWT tokens |
| `POST /api/auth/refresh/` | Refresh access token |
| `POST /api/auth/google/` | Google OAuth login / register |
| `POST /api/auth/connect-google/` | Link Google to existing account |
| `GET/PATCH /api/auth/profile/` | Student profile |
| `GET/POST /api/onboarding/` | Onboarding session management |
| `POST /api/onboarding/complete-from-chat/` | Finalize onboarding from AI chat |
| `GET /api/roadmaps/` | List user roadmaps |
| `POST /api/roadmaps/generate/` | Generate a new AI roadmap |
| `PATCH /api/roadmaps/{id}/nodes/{nid}/status/` | Update node progress |
| `POST /api/roadmaps/{id}/nodes/add/` | Add a node |
| `PATCH /api/roadmaps/{id}/nodes/{nid}/edit/` | Edit node content |
| `DELETE /api/roadmaps/{id}/nodes/{nid}/remove/` | Remove a node |
| `POST /api/roadmaps/{id}/fix-structure/` | Auto-correct node order |
| `GET/POST /api/chat/sessions/` | List / create chat sessions |
| `GET/PATCH/DELETE /api/chat/sessions/{id}/` | Retrieve / rename / delete session |
| `WS /ws/chat/{session_id}/` | WebSocket streaming chat |
| `WS /ws/onboarding/{session_id}/` | WebSocket onboarding chat |
| `GET/POST /api/resumes/` | List / create resumes |
| `GET/PUT/DELETE /api/resumes/{id}/` | Resume detail |
| `POST /api/resumes/{id}/generate-bullets/` | AI bullet point generation |
| `POST /api/resumes/{id}/generate-summary/` | AI summary generation |
| `POST /api/resumes/parse-job/` | Parse job description |
| `POST /api/resumes/{id}/score-ats/` | ATS compatibility score |
| `GET /api/gamification/profile/` | XP, level, badges, streak |
| `GET /api/gamification/badges/` | All available badges |
| `GET /api/gamification/leaderboard/` | Top users by XP |
| `GET /api/certifications/` | Full cert catalog |
| `GET /api/certifications/my/` | User's tracked certs |
| `GET /api/universities/` | University + program listing |
| `GET /api/jobs/` | Job listings |

---

## Authentication

All protected endpoints require a Bearer JWT in the `Authorization` header.

```
Authorization: Bearer <access_token>
```

The JWT payload includes: `user_id`, `email`, `role`, `full_name`, `is_onboarded`, `has_password`, `google_connected`.

WebSocket connections authenticate via `?token=<access_token>` query parameter. The Channels middleware validates the JWT signature cryptographically (no DB hit) before the handshake completes; the user DB row is fetched after `accept()`.

### Rate limits

| Endpoint | Limit |
|---|---|
| Register | 5 / hour |
| Login | 10 / hour |
| Google OAuth | 5 / hour |
| Authenticated users | 1000 / day |
| Anonymous | 100 / day |

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

# Seed badge catalog
python manage.py seed_badges

# Run the ASGI server (supports both HTTP and WebSocket)
daphne config.asgi:application --port 8000 --bind 0.0.0.0

# In a separate terminal — Celery worker (for background tasks)
celery -A config worker --loglevel=info
```

---

## Environment Variables

| Variable | Description |
|---|---|
| `SECRET_KEY` | Django secret key |
| `DEBUG` | `True` for local, `False` in production |
| `DATABASE_URL` | PostgreSQL connection string |
| `REDIS_URL` | Redis connection URL |
| `GROQ_API_KEY` | Groq API key for LLM access |
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 client ID |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key |
| `RAPIDAPI_KEY` | RapidAPI key for JSearch job listings |
| `CAREERJET_AFFID` | Careerjet affiliate ID (optional) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins |

---

## Deployment

The app is deployed on **Render** using Daphne as the ASGI server. The `Procfile` defines three process types:

```
web:    daphne config.asgi:application --port $PORT --bind 0.0.0.0 -v2
worker: celery -A config worker --loglevel=info
beat:   celery -A config beat --loglevel=info
```

Database: Neon PostgreSQL (serverless, SSL required).
Cache + Channels layer: Redis (Render Redis or Upstash).

---

## User Roles

| Role | Description |
|---|---|
| `undergraduate` | Default role assigned at registration |
| `incoming_student` | Auto-upgraded by onboarding AI if user reveals they are pre-college |
| `admin` | Access to custom admin panel at `/api/admin-panel/` |

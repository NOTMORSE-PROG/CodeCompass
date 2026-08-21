# CodeCompass monorepo

This branch consolidates the CodeCompass repositories while keeping every
existing URL and deployment untouched.

## Layout

- `/` — Vite frontend, originally from `CodeCompass_Frontend`
- `backend/` — Django backend, imported from `CodeCompass_Backend`
- `android/` — native Android app, imported from `CodeCompass_Android`

The original backend and Android repositories remain the authoritative
histories and rollback sources. This branch imports their latest committed
trees as a clean synchronization snapshot.

## Stable external mappings

- Canonical GitHub URL: https://github.com/NOTMORSE-PROG/CodeCompass_Frontend
- Frontend Vercel project: `code-compass-frontend`
- Frontend production alias: https://code-compass-ccs.vercel.app
- Backend Render service: `codecompass-backend`

The Vercel project remains rooted at `/`. Render must remain connected to the
existing service and use `backend/` as its root directory only during the
eventual source-repository cutover. Do not create replacement deployment
projects or domains.

The original backend and Android repositories remain unchanged.

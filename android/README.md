# CodeCompass

**Navigate Your Tech Career**

CodeCompass is an AI-driven career guide and personalized learning platform built for Computer Science students in the Philippines. It combines interactive roadmaps, gamification, job discovery, certification tracking, and an AI-powered chat coach — all in one Android app.

---

## Features

| Screen | Description |
|--------|-------------|
| **Dashboard** | XP tracker, streak counter, roadmap progress, and quick-access tiles |
| **AI Roadmap** | Personalized, AI-generated learning path with nodes for skills, projects, assessments, and certifications |
| **Video Learning** | YouTube video player integrated with roadmap resources |
| **Quiz / Assessment** | Node-based quizzes to unlock XP and progress |
| **AI Chat** | Multi-session AI career coach (advice, resume help, interview prep, roadmap edits) |
| **Certifications** | Browse and track TESDA, Google, AWS, and other industry certifications |
| **Jobs** | Live job listings with search, filters, save/unsave, and resume-based AI recommendations |
| **Universities** | Philippine university catalog with CCS programs, CHED CoE/CoD badges, and match scoring |
| **Achievements** | Badges, XP history, leaderboard, and streak tracking |
| **Profile** | View and edit personal info, change password, delete account |

---

## Tech Stack

**Android**
- Min SDK 31 · Target SDK 36 · Java 11
- AndroidX AppCompat, Activity, ConstraintLayout, RecyclerView, SwipeRefreshLayout
- Material Design 3
- AndroidX SplashScreen, Browser (Chrome Custom Tabs)

**Networking**
- Retrofit 2 + OkHttp 4 (REST)
- WebSocket (real-time AI onboarding & chat)
- Gson (JSON)

**Auth & Security**
- Google OAuth 2.0 via AndroidX Credentials API
- JWT tokens stored with AndroidX Security Crypto (EncryptedSharedPreferences)

**Other**
- PDFBox Android (PDF handling)
- MVVM + Repository pattern

---

## Architecture

```
app/
├── api/          # Retrofit service, API client, token interceptor
├── model/        # Data classes / POJOs
├── repository/   # Data access abstraction
├── ui/           # Activities, Adapters, BottomSheets
├── viewmodel/    # ViewModels (MVVM)
└── util/         # JWT parser, helpers
```

---

## Backend

The app connects to the Django REST and Django Channels service maintained in [`../backend/`](../backend/). The former Render service was intentionally deleted, so there is no active production backend URL.

Configure both URLs in `local.properties`. For an Android emulator with the backend running locally on port 8000, use:

```properties
BASE_URL=http://10.0.2.2:8000/api/
WS_BASE_URL=ws://10.0.2.2:8000/ws/
GOOGLE_WEB_CLIENT_ID=your-google-client-id
```

For a physical device, replace `10.0.2.2` with the development computer's reachable LAN address. For any future production service, use its verified HTTPS and WSS URLs.

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11+
- Android device or emulator running Android 12 (API 31)+

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/NOTMORSE-PROG/CodeCompass.git
   cd CodeCompass/android
   ```

2. Create `local.properties` in the project root (if it doesn't exist) and add your secrets:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   BASE_URL=http://10.0.2.2:8000/api/
   WS_BASE_URL=ws://10.0.2.2:8000/ws/
   GOOGLE_WEB_CLIENT_ID=your-google-web-client-id
   ```

3. Open the project in Android Studio and let Gradle sync.

4. Run on a device or emulator:
   ```bash
   ./gradlew installDebug
   ```

---

## CI / Code Quality

The project uses GitHub Actions for continuous integration:

- **`code-quality.yml`** — Runs `./gradlew lintDebug` on every push to `main`/`develop`. The lint report is uploaded as a build artifact. Zero new issues are expected outside the baseline.
- **`build-apk.yml`** — Builds a debug APK on every push.

---

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | API calls and WebSocket connections |
| `ACCESS_NETWORK_STATE` | Check connectivity before requests |

---

## License

This project is for academic and personal portfolio purposes.

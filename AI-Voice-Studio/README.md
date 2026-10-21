# AI Voice Studio

Native Android (Java + XML) text-to-speech app with a secure backend that talks to the real **ElevenLabs** API.
Developer: **Asif Qureshi** · Package: `com.asifqureshi.aivoicestudio` · Logo: supplied by the owner (`app/src/main/res/drawable-nodpi/brand_logo.png`, `ic_launcher_foreground.png`).

> **Honest status.** The backend is implemented and its automated tests pass. The Android app is fully written but
> **has not been compiled yet** (the authoring environment had no Android SDK / Gradle / internet). The first
> GitHub Actions run is the build verification. Real speech generation has **not** been exercised against live
> ElevenLabs servers (no API key was available); the integration is covered by tests against a local stand-in server.

## What is implemented

| Area | Status |
|---|---|
| Splash (logo + animated bars), 3-screen onboarding (persisted), routing by auth state | Implemented |
| Sign up, login, forgot/reset password, email verification, session refresh + expiry, logout, delete account | Implemented (email delivery needs a mail webhook, see below) |
| Bottom navigation: Home, Create, Voices, Library, Profile (state preserved per tab) | Implemented |
| Home: greeting, usage (real data), quick actions, favourites, recent audio, empty states | Implemented |
| Text to speech: editor, paste/select/clear, .txt import, drafts, counter + limits, model/language/format pickers, optional voice-setting overrides (stability, similarity, style, speed, speaker boost - only shown when the model supports them), cancel, retry, quota/error messages | Implemented |
| Voice library: search, category filter, sort, paging, preview, favourites, "use voice" | Implemented |
| Audio player (Media3): play/pause, ±10 s, seek bar, speed, audio focus, headphone-unplug pause | Implemented (foreground playback only) |
| Download via Storage Access Framework, share sheet, file size/duration | Implemented |
| History (search, replay, download, share, regenerate, delete), projects (save, open, rename, duplicate, favourite, delete) | Implemented |
| Profile (edit name, change password, stats), Settings (theme, default voice/model/format, server address, cache, legal, licences, version) | Implemented |
| Backend: auth, rate limiting, usage limits, ElevenLabs proxy, private audio storage, history, projects, favourites, admin API + web dashboard (`/admin`) | Implemented + tested |
| GitHub Actions: backend tests, static checks, unit tests, debug APK artifact | Configured, **unverified until it runs** |

## Not implemented yet (deliberately not faked)

Long-script chunking and merging, multi-speaker dialogue, audio editor / background music / mixing, waveform from real audio
(the player shows a seek bar only), voice design, voice cloning, speech-to-text, AI dubbing, subscriptions / billing / restore purchases,
push notifications, Google sign-in, in-app help/FAQ, background (service-based) playback, a Gradle wrapper JAR (see below),
Android instrumented/UI tests, and basic text formatting in the editor. The backend schema already has room for projects of
type `longform`, `dialogue` and `studio`.

## Architecture

* Android: Java, XML layouts, Material 3, MVVM (ViewModels + LiveData) + Repository pattern, Retrofit/OkHttp, AndroidX Media3.
  Tokens are encrypted with an AES-GCM key in the Android Keystore. HTTPS only (HTTP allowed solely for `localhost`, `10.0.2.2`, `127.0.0.1`).
* Backend: Node.js ≥ 22.13, **zero npm dependencies** (built-in `node:sqlite`, `crypto`, `http`, `fetch`). One coherent auth system:
  email + password (scrypt) → short-lived JWT access token + rotating refresh token with reuse detection.
* The ElevenLabs API key lives only in the backend environment. The app never sees it.

## Quick start

### 1. Backend
```bash
cd backend
cp ../.env.example .env          # edit it; set JWT_SECRET and ELEVENLABS_API_KEY
export $(grep -v '^#' .env | xargs)
npm test                         # optional: run the test-suite
npm start
```
Put it behind HTTPS (reverse proxy such as Caddy/nginx, or a platform with TLS). A `backend/Dockerfile` is included
(`docker build -t avs-backend backend && docker run -p 8080:8080 -v avs-data:/data --env-file .env avs-backend`).
Check `GET /healthz`. Database migrations in `backend/migrations` are applied automatically at start-up.

Create an administrator: sign up in the app, then on the server run `npm run make-admin -- you@example.com`
and open `https://your-backend/admin`.

Email: the backend POSTs `{to, subject, text}` JSON to `MAIL_WEBHOOK_URL` (your own function in front of any mail provider).
Without it, verification/reset emails are **not sent** and the app tells the user so. Keep `REQUIRE_EMAIL_VERIFICATION=false` until it is set up.

### 2. Android app (Android Studio)
1. Install Android Studio (JDK 17, Android SDK 35).
2. Open this folder. Generate the wrapper once: `gradle wrapper --gradle-version 8.9`.
3. Build **Run ▶** or `./gradlew assembleDebug`.
4. In the app: Login → **Server settings** → enter your backend HTTPS URL. (Or bake a default in: `-PbackendUrl=https://api.example.com/`.)

### 3. GitHub Actions APK
1. Push this repository to GitHub (branch `main` or `master`).
2. Open the **Actions** tab → **Android CI** → the latest run. (Run it manually via *Run workflow* if needed.)
3. When the `Build debug APK` job is green, scroll to **Artifacts** and download **AI-Voice-Studio-debug-apk** (a zip containing `app-debug.apk`).
4. Optional: add a repository *variable* (not a secret) `BACKEND_URL` to preset the server address.
5. If the build fails, open the failed step; reports are uploaded as the `android-reports` artifact.

The workflow installs Gradle 8.9 itself, so a missing wrapper JAR does not break CI. It produces an unsigned **debug** APK.
Release signing is not configured; if you add it, store keystore and passwords only in GitHub *Secrets*.

## Configuration reference (backend environment)
See `.env.example`. Key variables: `JWT_SECRET` (required, ≥ 32 chars), `ELEVENLABS_API_KEY`, `PORT`, `DB_PATH`, `AUDIO_DIR`,
`MAX_TEXT_CHARS`, `FREE_MONTHLY_CHARS`, `PLAN_LIMITS_JSON`, `MAIL_WEBHOOK_URL`, `MAIL_WEBHOOK_SECRET`, `REQUIRE_EMAIL_VERIFICATION`,
`PUBLIC_BASE_URL`, `TRUST_PROXY`, rate-limit settings. Default limits are placeholders, not pricing: set what fits your business.
Application usage limits are separate from your ElevenLabs account billing.

## Project structure
```
app/                     Android module (Java, XML, resources, unit tests)
backend/                 Node.js API, migrations, admin dashboard (public/), tests, Dockerfile
.github/workflows/       android.yml
tools/check_project.py   Offline checker for resource references, imports, manifest and Java syntax
.env.example             Backend variable names with placeholder values
```

## Testing
* `cd backend && npm test` - 14 integration tests (auth, token rotation, validation, quotas, provider-error mapping, authorization, deletion, admin).
* `python3 tools/check_project.py` - consistency checks for the Android sources (not a substitute for compiling).
* `./gradlew testDebugUnitTest` - JVM unit tests (validators, formatting, API-error parsing).

## Troubleshooting
* *"No server address is set"* - set it under Login → Server settings.
* *"provider_not_configured"* - set `ELEVENLABS_API_KEY` on the backend and restart.
* *Verification/reset email never arrives* - configure `MAIL_WEBHOOK_URL` (see above).
* *HTTP 402 usage_limit_exceeded* - monthly app limit reached; raise `FREE_MONTHLY_CHARS` or assign a plan in `/admin`.
* *Emulator cannot reach local backend* - use `http://10.0.2.2:8080/`.
* *Gradle: SDK licence / platform missing* - install SDK Platform 35 in Android Studio's SDK Manager.

## Security & privacy notes
Provider keys are server-side only; passwords are hashed with scrypt; refresh tokens are stored hashed and rotated; every record is
scoped to the authenticated user; audio is stored privately and served only to its owner; logs avoid request bodies and tokens;
account deletion removes database rows and stored audio. The Terms and Privacy Policy shown in the app are **templates** that need legal review.
The backend stores each submitted script with its history entry (so it can be regenerated); users can delete entries or their whole account.
Voice-clone and impersonation features are intentionally absent.

## Known limitations
SQLite single-node storage; in-memory rate limiter (per process); usage accounting is approximate under heavy concurrent requests
by the same user; generated audio duration is reported by the app after first playback; history lists show model/char counts but not voice names.

## Licensing
Application source: © Asif Qureshi, all rights reserved unless you add a licence file. Third-party libraries (AndroidX, Material Components,
Media3, Retrofit, OkHttp, Gson) are Apache-2.0. ElevenLabs is a trademark of its owner; this project is independent and unaffiliated.
You are responsible for ensuring you hold the rights to the supplied logo and to every voice you use.

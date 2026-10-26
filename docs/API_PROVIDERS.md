# AI voice providers - setup and testing

## Which providers the code really supports

| Provider | Supported | Voices | Models | Settings that actually work | Output |
|---|---|---|---|---|---|
| **ElevenLabs** | Yes (`backend/src/providers/elevenlabs.js`) | Loaded live from your ElevenLabs account (search, categories, previews) | Loaded live (TTS-capable models) | stability, similarity, style, speed 0.7-1.2, speaker boost (style/boost only for models that support them) | MP3 and Opus variants |
| **OpenAI** | Yes (`backend/src/providers/openai.js`) | Fixed list of documented OpenAI voices (alloy, ash, ballad, coral, echo, fable, nova, onyx, sage, shimmer, verse) | gpt-4o-mini-tts, tts-1, tts-1-hd | speed 0.25-4.0 | MP3, Opus |
| **Google Cloud Text-to-Speech** | Yes, with an **API key** (`backend/src/providers/google.js`) | Loaded live (hundreds of voices, searchable by name or language such as `hi-IN`) | one entry ("Cloud Text-to-Speech"); the voice name picks Standard/WaveNet/Neural2/Studio/Chirp3-HD | speed 0.25-4.0 | MP3, Opus (OGG) |

**Not supported** (no code exists): Microsoft Azure, Amazon Polly, IBM Watson, PlayHT, Coqui, local/offline engines, Google service-account (OAuth/JSON key) login.
The app only shows controls a provider supports; the server rejects unsupported settings with a clear error instead of ignoring them.

All providers are called **only from the backend**. The Android app never sees any key.

## Step 1 - get an API key (do this privately; never paste a key into chat, screenshots, GitHub or the app)

Menu names on provider websites change from time to time; the goal is the same.

* **ElevenLabs** - sign in at https://elevenlabs.io, open your account/developer settings and the **API Keys** page, create a key.
* **OpenAI** - sign in at https://platform.openai.com/api-keys, **Create new secret key** (the account needs billing/credit). Copy it once; it is not shown again.
* **Google Cloud** -
  1. Open https://console.cloud.google.com and select or create a project (billing must be enabled; Google has a free tier).
  2. **APIs & Services > Library**, search **Cloud Text-to-Speech API**, click **Enable**.
  3. **APIs & Services > Credentials > Create credentials > API key**.
  4. Open the key, under **API restrictions** choose *Restrict key* > **Cloud Text-to-Speech API**, save.

## Step 2 - put the key on YOUR server (exact file and variable)

1. Open a terminal in the project folder and create your private config file:
   ```bash
   cp .env.example backend/.env
   ```
   The file is **`backend/.env`** (full path inside the project: `AI-Voice-Studio/backend/.env`). It is listed in `.gitignore`, so Git will not upload it.
2. Open `backend/.env` in a text editor and fill in the line(s) for the provider(s) you use. Write the key right after the `=` sign, no spaces, no quotes:

   | Provider | Exact line in `backend/.env` |
   |---|---|
   | ElevenLabs | `ELEVENLABS_API_KEY=` *(paste key here)* |
   | OpenAI | `OPENAI_API_KEY=` *(paste key here)* |
   | Google Cloud | `GOOGLE_TTS_API_KEY=` *(paste key here)* |

   Also set `JWT_SECRET=` to a long random text (32+ characters; `openssl rand -hex 32` makes one).
3. On a hosting platform (Render, Railway, Fly.io, Docker, ...) do not upload the file: add the **same variable names** in the platform's *Environment variables* screen.
   With Docker: `docker run --env-file backend/.env ...` (the `.dockerignore` keeps the file out of the image).
4. Restart the backend after any change: stop it (Ctrl+C) and run `npm start` again.

## Step 3 - choose the provider

* **Server default** - set `TTS_PROVIDER=` in `backend/.env` to `elevenlabs`, `openai` or `google`. If you leave it empty, the first provider that has a key is used (order: elevenlabs, openai, google).
* **Per user, inside the app** - **Profile > Settings > Voice provider**. The list shows only providers that have a key on the server. Choosing one reloads voices/models, clears the default voice (voice IDs differ per provider) and is used for generation.
* **Directly via the API** - add `provider` to the request (`GET /v1/voices?provider=openai`, `POST /v1/tts` with `"provider":"google"`).

You can configure all three keys at once; users (or you) switch between them.

## Step 4 - tell the Android app where the backend is

The backend must be reachable over **HTTPS** (a phone cannot use plain `http://` except for the emulator).

1. **Inside the app (easiest, no rebuild):** open the app > on the Login screen tap **Server settings** > enter e.g. `https://your-backend.example.com/` > Save. It can also be changed later under **Profile > Settings > Server address**.
2. **Built into the APK:** edit the line `backendUrl=` in **`gradle.properties`** (project root), e.g. `backendUrl=https://your-backend.example.com/`. The URL is public information, not a secret.
3. **GitHub Actions:** repository **Settings > Secrets and variables > Actions > Variables** > new *repository variable* named `BACKEND_URL`.
4. **Android emulator talking to a backend on your computer:** use `http://10.0.2.2:8080/`.
5. **A real phone with a backend on your computer:** use a tunnel that gives you an HTTPS address (for example Cloudflare Tunnel or ngrok) and enter that address.

## Step 5 - test that it works

1. **Check the keys (no cost):**
   ```bash
   cd backend
   npm run check-providers
   ```
   Each provider shows `NOT CONFIGURED`, `OK (key accepted)` or `CONFIGURED but FAILED: <reason>`. Keys are never printed.
   Optional real audio test (uses about 5 characters of your quota): `npm run check-providers -- --synthesize`
2. **Start the backend:** `npm start` - the first line lists the configured providers. Open `http://localhost:8080/healthz`; it should show `"providerConfigured":true`.
3. **Admin dashboard:** make your account an admin (`npm run make-admin -- you@example.com`), open `https://<your-backend>/admin`, sign in. The **Provider** card shows each provider as healthy / unhealthy / not configured.
4. **In the app:** Create tab > choose a voice (Voices tab) > type text > **Generate speech** > play. A successful play means the whole chain worked.
5. **With curl (optional):**
   ```bash
   curl -s -X POST http://localhost:8080/v1/auth/signup -H "Content-Type: application/json" \
     -d '{"name":"Test","email":"me@example.com","password":"Passw0rd1","acceptTerms":true}'
   # copy data.session.accessToken from the answer, then:
   curl -s -H "Authorization: Bearer TOKEN" "http://localhost:8080/v1/voices?provider=openai"
   curl -s -X POST http://localhost:8080/v1/tts -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
     -d '{"provider":"openai","text":"Hello from AI Voice Studio","voiceId":"alloy"}' -o test.mp3
   ```
   (If email verification is switched on, sign-up returns no token; sign in after verifying.)

## What has and has not been tested

* Automated tests (`cd backend && npm test`) exercise all three adapters against local stand-in servers: exact request bodies/headers sent to each provider, audio bytes returned, voice lists, paging/filtering, and error mapping (bad key, quota, rate limit, rejected request).
* **No real provider account was available**, so nothing has been generated with live ElevenLabs, OpenAI or Google servers yet. Run `npm run check-providers -- --synthesize` with your own keys to verify that on your side.

## Costs and limits

Provider charges come from your own provider accounts. The app's character limits (`FREE_MONTHLY_CHARS`, `MAX_TEXT_CHARS`, plans) are separate, server-enforced app limits.
Per-request text limits: OpenAI 4096 characters, Google 5000 bytes, ElevenLabs depends on the model (the app shows the limit of the selected model).

# Troubleshooting

Messages appear in the app as a red message or a snackbar with a **Retry** button when retrying makes sense.
`code` is the machine-readable error from the backend (visible in the admin dashboard error table and in `curl` output).

## Missing API key

| What you see | `code` | Cause | Fix |
|---|---|---|---|
| "The "OpenAI" provider is not configured on the server. An administrator must set OPENAI_API_KEY." (same for ElevenLabs / Google) | `provider_not_configured` (HTTP 503) | The variable is empty or the backend was not restarted | Put the key in **`backend/.env`** (`ELEVENLABS_API_KEY=`, `OPENAI_API_KEY=` or `GOOGLE_TTS_API_KEY=`), then restart `npm start`. Run `npm run check-providers` to confirm. |
| Settings > Voice provider says no provider is configured | - | No key on the server at all | Same as above |
| Voices tab empty with a provider message | `provider_not_configured` | The selected provider has no key | Choose another provider in Settings, or add the key |
| The key is in `.env` but nothing changes | - | File is in the wrong place or named wrongly (`.env.txt`, `backend/.env.example`) | The file must be exactly `backend/.env` next to `backend/package.json`. Real environment variables override the file. |

## Invalid credentials

| What you see | `code` | Cause | Fix |
|---|---|---|---|
| "The server could not authenticate with the voice provider..." | `provider_auth_failed` (HTTP 502) | Key mistyped, extra spaces/quotes, revoked, wrong provider's key in the wrong variable; for Google also: API not enabled, key restricted to another API, or billing disabled | Re-copy the key from the provider site, remove spaces and quotes, check the variable name, enable **Cloud Text-to-Speech API** (Google), restart. `npm run check-providers` shows `rejected the key`. |
| "Incorrect email or password." | `invalid_credentials` (401) | That is the **app account** login, not an API key | Reset the password from Login > Forgot password |
| "Sign in required or session expired." | `unauthenticated` (401) | App session ended | Sign in again (the app does this automatically when the refresh token expires) |
| Login says email not verified | `email_not_verified` (403) | `REQUIRE_EMAIL_VERIFICATION=true` but no mail webhook | Set `MAIL_WEBHOOK_URL` or set `REQUIRE_EMAIL_VERIFICATION=false` |

## Quota and limits

| What you see | `code` | Cause | Fix |
|---|---|---|---|
| "The voice provider account has run out of quota or credit." | `provider_quota_exceeded` (402) | Your ElevenLabs/OpenAI/Google account has no credit or hit its quota | Add credit/upgrade the provider plan, or switch provider in Settings |
| "This request would exceed your monthly character limit." | `usage_limit_exceeded` (402) | The **app's** monthly limit for your plan | Raise `FREE_MONTHLY_CHARS` / `PLAN_LIMITS_JSON`, or assign a plan to the user in `/admin` |
| "The voice provider is rate limiting requests." / "Too many requests" | `provider_rate_limited` / `rate_limited` (429) | Too many requests per minute | Wait the number of seconds shown and retry; adjust `TTS_RATE_LIMIT_PER_MIN` if it is your own limit |
| "Text is limited to N characters per request." | `text_too_long` (400) | Provider/model limit (OpenAI 4096, Google 5000 bytes) | Shorten the text (long-script splitting is not implemented yet) |

## Network errors

| What you see | Where | Cause | Fix |
|---|---|---|---|
| "Can't reach the server. Check your internet connection and the server address." | App | No internet, wrong URL, backend not running | Open the backend `/healthz` URL in the phone's browser. Fix the address in **Login > Server settings** (must start with `https://`) |
| "No server address is set." | App | First run | Enter the backend URL (see API_PROVIDERS.md, Step 4) |
| "A secure connection to the server could not be established." | App | Bad/expired HTTPS certificate, or the server only speaks HTTP | Use a valid HTTPS certificate (reverse proxy / hosting platform TLS) |
| "The server took too long to respond." | App | Slow network or very long text | Retry; try a shorter text |
| "Could not reach the voice provider." / "The voice provider took too long to respond." | `provider_unreachable` (502) / `provider_timeout` (504) | The backend cannot reach ElevenLabs/OpenAI/Google (firewall, outage, DNS) | Check the server's internet access and the provider's status page; raise `PROVIDER_TIMEOUT_MS` if needed |
| Emulator cannot reach a backend on your PC | App | `localhost` inside the emulator is the emulator itself | Use `http://10.0.2.2:8080/` |
| A real phone cannot use `http://192.168.x.x:8080/` | App | Android blocks plain HTTP except for the emulator | Use HTTPS (tunnel such as Cloudflare Tunnel/ngrok, or deploy the backend) |

## Other generation errors

| What you see | `code` | Fix |
|---|---|---|
| "The provider rejected the request (unsupported voice, model, language or setting)." plus the provider's text | `provider_rejected_request` (422) | Pick a voice that belongs to the selected provider; check language; reset voice settings |
| "Unknown OpenAI voice." | `unknown_voice` | Use a voice from the Voices tab |
| "... does not support the "stability" setting." | `unsupported_voice_setting` | Turn off "Override the voice's default settings" or use only supported sliders |
| "Unsupported output format for ..." | `unsupported_output_format` | Pick a format from the dropdown (formats differ per provider) |
| "The audio file is no longer available on the server." | `audio_missing` (410) | Server storage was cleared; regenerate |

## Build and GitHub Actions

* **Backend job fails**: run `cd backend && npm test` locally; Node 22.13 or newer is required.
* **"Static project checks" step fails**: run `python3 tools/check_project.py` and read the listed missing resource/import.
* **Gradle step fails**: open the failed step in the *Actions* tab; download the `android-reports` artifact; send me the first "error:" lines.
* **No APK artifact**: the *Build debug APK* job must be green; the file is under **Artifacts > AI-Voice-Studio-debug-apk** on the run page.
* **Launcher icon did not change**: uninstall the old app, install the new APK.

## Still stuck?

Run these and read the output (no secrets are printed): `cd backend && npm run check-providers` and `curl https://<your-backend>/healthz`.

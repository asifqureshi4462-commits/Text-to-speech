# AI Voice Studio backend

Zero-dependency Node.js (≥ 22.13) API. See the root `README.md` for setup.

* `npm start` / `npm test` / `npm run make-admin -- user@example.com`
* All routes are under `/v1` (JSON envelope `{ok, data}` or `{ok:false, error:{code,message,details?}}`), plus `/healthz`, `/verify-email`, `/admin`.
* Authenticated routes need `Authorization: Bearer <access token>`; admin routes additionally need the `admin` role (checked on every request).

| Area | Routes |
|---|---|
| Auth | `POST /v1/auth/{signup,login,refresh,logout,forgot-password,reset-password,verify-email,resend-verification,change-password}` |
| Account | `GET/PATCH /v1/me`, `DELETE /v1/account` |
| Provider | `GET /v1/voices`, `GET /v1/models` |
| Speech | `POST /v1/tts` (returns audio bytes; headers `X-Generation-Id`, `X-Audio-Asset-Id`, `X-Usage-Remaining`), `GET /v1/usage` |
| Audio | `GET /v1/audio/:id` (Range supported), `PATCH /v1/audio/:id`, `DELETE /v1/audio/:id` |
| History | `GET /v1/generations`, `GET|DELETE /v1/generations/:id` |
| Projects | `GET|POST /v1/projects`, `GET|PATCH|DELETE /v1/projects/:id`, `POST /v1/projects/:id/duplicate` |
| Favourites | `GET /v1/favorites/voices`, `PUT|DELETE /v1/favorites/voices/:voiceId` |
| Admin | `GET /v1/admin/{stats,users,audit,provider}`, `PATCH /v1/admin/users/:id` |

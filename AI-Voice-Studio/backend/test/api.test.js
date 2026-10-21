'use strict';
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { load } = require('../src/config');
const { createApp } = require('../src/app');

// A local stand-in for the provider so request construction / error mapping can be tested offline.
// It is test infrastructure only: the app never ships or returns canned audio.
let stub, stubUrl, lastTts, stubMode = 'ok';
const FAKE_BYTES = Buffer.concat([Buffer.from('ID3'), Buffer.alloc(2000, 7)]);
let app, base, tmp, mailBox = [], mailServer, mailUrl;

async function api(method, p, { body, token, headers } = {}) {
  const res = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined });
  const ct = res.headers.get('content-type') || '';
  return { status: res.status, headers: res.headers, json: ct.includes('json') ? await res.json() : null, buf: ct.includes('json') ? null : Buffer.from(await res.arrayBuffer()) };
}
async function signup(email, name = 'Test User') {
  const r = await api('POST', '/v1/auth/signup', { body: { name, email, password: 'Passw0rd1', acceptTerms: true } });
  return r;
}

before(async () => {
  stub = http.createServer((req, res) => {
    const chunks = []; req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      if (req.headers['xi-api-key'] !== 'test-key') { res.writeHead(401, { 'content-type': 'application/json' }); return res.end('{"detail":{"status":"invalid_api_key","message":"bad key"}}'); }
      if (req.url.startsWith('/v1/text-to-speech/')) {
        lastTts = { url: req.url, body: JSON.parse(Buffer.concat(chunks).toString()) };
        if (stubMode === 'quota') { res.writeHead(401, { 'content-type': 'application/json' }); return res.end('{"detail":{"status":"quota_exceeded","message":"quota"}}'); }
        if (stubMode === '429') { res.writeHead(429, { 'retry-after': '7' }); return res.end('{}'); }
        if (stubMode === '422') { res.writeHead(422, { 'content-type': 'application/json' }); return res.end('{"detail":{"message":"language not supported"}}'); }
        if (stubMode === '500') { res.writeHead(500); return res.end('boom'); }
        res.writeHead(200, { 'content-type': 'audio/mpeg' }); return res.end(FAKE_BYTES);
      }
      if (req.url.startsWith('/v2/voices')) { res.writeHead(200, { 'content-type': 'application/json' }); return res.end(JSON.stringify({ voices: [{ voice_id: 'abc123def456', name: 'Provider Voice', category: 'premade', labels: { accent: 'x' }, verified_languages: [{ language: 'en', locale: 'en-US', model_id: 'eleven_multilingual_v2' }], preview_url: 'https://example.invalid/p.mp3' }], has_more: false, total_count: 1 })); }
      if (req.url.startsWith('/v1/models')) { res.writeHead(200, { 'content-type': 'application/json' }); return res.end(JSON.stringify([{ model_id: 'eleven_multilingual_v2', name: 'M2', can_do_text_to_speech: true, maximum_text_length_per_request: 10000, can_use_style: true, languages: [{ language_id: 'en', name: 'English' }] }, { model_id: 'sts_only', name: 'STS', can_do_text_to_speech: false }])); }
      res.writeHead(404); res.end();
    });
  });
  await new Promise((r) => stub.listen(0, r)); stubUrl = `http://127.0.0.1:${stub.address().port}`;
  mailServer = http.createServer((req, res) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => { mailBox.push(JSON.parse(Buffer.concat(c))); res.writeHead(200); res.end(); }); });
  await new Promise((r) => mailServer.listen(0, r)); mailUrl = `http://127.0.0.1:${mailServer.address().port}`;
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'avs-'));
  const config = load({ JWT_SECRET: 'j'.repeat(40), DB_PATH: ':memory:', AUDIO_DIR: path.join(tmp, 'audio'), ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: stubUrl,
    FREE_MONTHLY_CHARS: '300', AUTH_RATE_LIMIT_PER_MIN: '1000', MAX_TEXT_CHARS: '200', PLAN_LIMITS_JSON: '{"pro":100000}', MAIL_WEBHOOK_URL: mailUrl, REQUIRE_EMAIL_VERIFICATION: 'true' });
  app = createApp(config);
  await new Promise((r) => app.server.listen(0, r)); base = `http://127.0.0.1:${app.server.address().port}`;
});
after(async () => { await app.close(); stub.close(); mailServer.close(); fs.rmSync(tmp, { recursive: true, force: true }); });

let tokenA, userA, tokenB;
async function verifiedLogin(email) {
  const s = await signup(email);
  assert.equal(s.status, 201); assert.equal(s.json.data.verification.required, true); assert.equal(s.json.data.verification.emailDelivered, true);
  const mail = mailBox.at(-1); const code = /Your AI Voice Studio verification code: (\S+)/.exec(mail.text)[1];
  assert.equal((await api('POST', '/v1/auth/login', { body: { email, password: 'Passw0rd1' } })).json.error.code, 'email_not_verified');
  assert.equal((await api('POST', '/v1/auth/verify-email', { body: { token: code } })).status, 200);
  assert.equal((await api('POST', '/v1/auth/verify-email', { body: { token: code } })).status, 400, 'verification token is single-use');
  const l = await api('POST', '/v1/auth/login', { body: { email, password: 'Passw0rd1' } });
  assert.equal(l.status, 200); return l.json.data;
}

test('auth: validation, duplicates, verification, login, refresh rotation + reuse detection', async () => {
  assert.equal((await api('POST', '/v1/auth/signup', { body: { name: 'A', email: 'bad', password: 'Passw0rd1', acceptTerms: true } })).json.error.code, 'invalid_email');
  assert.equal((await api('POST', '/v1/auth/signup', { body: { name: 'A', email: 'a@x.io', password: 'short', acceptTerms: true } })).json.error.code, 'weak_password');
  assert.equal((await api('POST', '/v1/auth/signup', { body: { name: 'A', email: 'a@x.io', password: 'Passw0rd1' } })).json.error.code, 'terms_required');
  const s = await verifiedLogin('alice@example.com'); tokenA = s.accessToken; userA = s.user;
  assert.equal((await signup('ALICE@example.com')).json.error.code, 'email_taken');
  assert.equal((await api('POST', '/v1/auth/login', { body: { email: 'alice@example.com', password: 'wrongpass1' } })).json.error.code, 'invalid_credentials');
  assert.equal((await api('POST', '/v1/auth/login', { body: { email: 'nobody@example.com', password: 'wrongpass1' } })).json.error.code, 'invalid_credentials');
  const r1 = await api('POST', '/v1/auth/refresh', { body: { refreshToken: s.refreshToken } });
  assert.equal(r1.status, 200);
  const reuse = await api('POST', '/v1/auth/refresh', { body: { refreshToken: s.refreshToken } });
  assert.equal(reuse.status, 401);
  assert.equal((await api('POST', '/v1/auth/refresh', { body: { refreshToken: r1.json.data.refreshToken } })).status, 401, 'reuse revokes the whole token family');
  tokenA = (await api('POST', '/v1/auth/login', { body: { email: 'alice@example.com', password: 'Passw0rd1' } })).json.data.accessToken;
  assert.equal((await api('GET', '/v1/me')).status, 401);
  assert.equal((await api('GET', '/v1/me', { token: tokenA + 'x' })).status, 401);
  const me = await api('GET', '/v1/me', { token: tokenA });
  assert.equal(me.json.data.user.email, 'alice@example.com');
  assert.equal(JSON.stringify(me.json).includes('password'), false, 'no password material in responses');
});

test('password reset flow revokes sessions and token is single-use', async () => {
  mailBox.length = 0;
  assert.equal((await api('POST', '/v1/auth/forgot-password', { body: { email: 'ghost@example.com' } })).status, 200);
  assert.equal(mailBox.length, 0, 'no mail for unknown account, same response');
  assert.equal((await api('POST', '/v1/auth/forgot-password', { body: { email: 'alice@example.com' } })).status, 200);
  const code = /valid for 1 hour\):\s+(\S+)/.exec(mailBox.at(-1).text)[1];
  assert.equal((await api('POST', '/v1/auth/reset-password', { body: { token: code, password: 'weak' } })).json.error.code, 'weak_password');
  assert.equal((await api('POST', '/v1/auth/reset-password', { body: { token: code, password: 'NewPassw0rd2' } })).status, 200);
  assert.equal((await api('POST', '/v1/auth/reset-password', { body: { token: code, password: 'Another0ne3' } })).status, 400);
  assert.equal((await api('POST', '/v1/auth/login', { body: { email: 'alice@example.com', password: 'Passw0rd1' } })).status, 401);
  const l = await api('POST', '/v1/auth/login', { body: { email: 'alice@example.com', password: 'NewPassw0rd2' } });
  assert.equal(l.status, 200); tokenA = l.json.data.accessToken;
});

test('voices and models are proxied; provider credentials never reach the client', async () => {
  const v = await api('GET', '/v1/voices?search=x', { token: tokenA });
  assert.equal(v.json.data.voices[0].voiceId, 'abc123def456');
  assert.equal(v.json.data.voices[0].languages[0].locale, 'en-US');
  const m = await api('GET', '/v1/models', { token: tokenA });
  assert.deepEqual(m.json.data.models.map((x) => x.modelId), ['eleven_multilingual_v2'], 'non-TTS models filtered out');
  assert.equal(JSON.stringify([v.json, m.json]).includes('test-key'), false);
  assert.equal((await api('GET', '/v1/voices')).status, 401);
});

test('TTS: request construction, real bytes returned, stored privately, history + usage recorded', async () => {
  const r = await api('POST', '/v1/tts', { token: tokenA, body: { text: 'Hello world.', voiceId: 'abc123def456', modelId: 'eleven_multilingual_v2', outputFormat: 'mp3_44100_64', languageCode: 'en', voiceSettings: { stability: 0.4, similarityBoost: 0.8, style: 0.2, speed: 1.1, useSpeakerBoost: true } } });
  assert.equal(r.status, 200); assert.equal(r.headers.get('content-type'), 'audio/mpeg');
  assert.ok(r.buf.equals(FAKE_BYTES), 'bytes returned are exactly what the provider produced');
  assert.equal(lastTts.url, '/v1/text-to-speech/abc123def456?output_format=mp3_44100_64');
  assert.deepEqual(lastTts.body, { text: 'Hello world.', model_id: 'eleven_multilingual_v2', language_code: 'en', voice_settings: { stability: 0.4, similarity_boost: 0.8, style: 0.2, speed: 1.1, use_speaker_boost: true } });
  const assetId = r.headers.get('x-audio-asset-id'), genId = r.headers.get('x-generation-id');
  const usage = await api('GET', '/v1/usage', { token: tokenA });
  assert.equal(usage.json.data.usedChars, 12); assert.equal(usage.json.data.limitChars, 300);
  const hist = await api('GET', '/v1/generations?q=hello', { token: tokenA });
  assert.equal(hist.json.data.total, 1); assert.equal(hist.json.data.generations[0].status, 'completed'); assert.equal(hist.json.data.generations[0].id, genId);
  const dl = await api('GET', `/v1/audio/${assetId}`, { token: tokenA });
  assert.ok(dl.buf.equals(FAKE_BYTES));
  const part = await api('GET', `/v1/audio/${assetId}`, { token: tokenA, headers: { Range: 'bytes=0-9' } });
  assert.equal(part.status, 206); assert.equal(part.buf.length, 10); assert.equal(part.headers.get('content-range'), `bytes 0-9/${FAKE_BYTES.length}`);
  assert.equal((await api('PATCH', `/v1/audio/${assetId}`, { token: tokenA, body: { durationMs: 1500 } })).status, 200);
  assert.equal((await api('GET', `/v1/generations/${genId}`, { token: tokenA })).json.data.generation.durationMs, 1500);
  const one = (await api('GET', `/v1/generations/${genId}`, { token: tokenA })).json.data.generation;
  assert.equal(one.text, 'Hello world.', 'full text is available for exact regeneration');
  assert.equal(hist.json.data.generations[0].text, undefined, 'list responses stay light (no full text)');
});

test('TTS validation and provider error mapping (failed attempts are logged, never charged)', async () => {
  const t = (body) => api('POST', '/v1/tts', { token: tokenA, body: { voiceId: 'abc123def456', ...body } });
  assert.equal((await t({ text: '   ' })).json.error.code, 'empty_text');
  assert.equal((await t({ text: 'x'.repeat(201) })).json.error.code, 'text_too_long');
  assert.equal((await t({ text: 'hi', outputFormat: 'wav_999' })).json.error.code, 'unsupported_output_format');
  assert.equal((await t({ text: 'hi', voiceSettings: { stability: 3 } })).json.error.code, 'invalid_field');
  assert.equal((await t({ text: 'hi', voiceId: '../etc' })).json.error.code, 'invalid_field');
  assert.equal((await t({ text: 'hi', projectId: 'doesnotexist1' })).status, 404);
  const before = (await api('GET', '/v1/usage', { token: tokenA })).json.data.usedChars;
  stubMode = 'quota'; assert.equal((await t({ text: 'hi' })).json.error.code, 'provider_quota_exceeded');
  stubMode = '429'; const r429 = await t({ text: 'hi' }); assert.equal(r429.status, 429); assert.equal(r429.headers.get('retry-after'), '7');
  stubMode = '422'; const r422 = await t({ text: 'hi' }); assert.equal(r422.status, 422); assert.match(r422.json.error.message, /language not supported/);
  stubMode = '500'; assert.equal((await t({ text: 'hi' })).json.error.code, 'provider_error');
  stubMode = 'ok';
  assert.equal((await api('GET', '/v1/usage', { token: tokenA })).json.data.usedChars, before, 'failures do not consume quota');
  const failed = await api('GET', '/v1/generations?status=failed', { token: tokenA });
  assert.equal(failed.json.data.total, 4);
});

test('usage limit is enforced server-side', async () => {
  const text = 'y'.repeat(200);
  assert.equal((await api('POST', '/v1/tts', { token: tokenA, body: { text, voiceId: 'abc123def456' } })).status, 200); // 12+200
  const over = await api('POST', '/v1/tts', { token: tokenA, body: { text, voiceId: 'abc123def456' } });
  assert.equal(over.status, 402); assert.equal(over.json.error.code, 'usage_limit_exceeded'); assert.equal(over.json.error.details.limit, 300);
});

test('authorization: users cannot read or modify each other\'s data; admin routes are server-enforced', async () => {
  const b = await verifiedLogin('bob@example.com'); tokenB = b.accessToken;
  const proj = await api('POST', '/v1/projects', { token: tokenA, body: { name: 'Alice project', type: 'tts', content: { text: 'secret' } } });
  assert.equal(proj.status, 201); const pid = proj.json.data.project.id;
  assert.equal((await api('GET', `/v1/projects/${pid}`, { token: tokenB })).status, 404);
  assert.equal((await api('PATCH', `/v1/projects/${pid}`, { token: tokenB, body: { name: 'hax' } })).status, 404);
  assert.equal((await api('DELETE', `/v1/projects/${pid}`, { token: tokenB })).status, 404);
  assert.equal((await api('POST', '/v1/tts', { token: tokenB, body: { text: 'hi', voiceId: 'abc123def456', projectId: pid } })).status, 404);
  const gen = (await api('GET', '/v1/generations', { token: tokenA })).json.data.generations.find((g) => g.audioAssetId);
  assert.equal((await api('GET', `/v1/audio/${gen.audioAssetId}`, { token: tokenB })).status, 404);
  assert.equal((await api('DELETE', `/v1/generations/${gen.id}`, { token: tokenB })).status, 404);
  assert.equal((await api('GET', '/v1/generations', { token: tokenB })).json.data.total, 0);
  assert.equal((await api('GET', '/v1/admin/users', { token: tokenA })).status, 403);
  assert.equal((await api('GET', '/v1/admin/stats')).status, 401);
});

test('projects CRUD, search, duplicate, favourites', async () => {
  const p = (await api('POST', '/v1/projects', { token: tokenA, body: { name: 'Podcast intro', type: 'longform', content: { chunks: ['a', 'b'] }, settings: { voiceId: 'abc123def456' } } })).json.data.project;
  assert.deepEqual(p.content.chunks, ['a', 'b']);
  assert.equal((await api('POST', '/v1/projects', { token: tokenA, body: { name: 'x', type: 'nope' } })).json.error.code, 'invalid_field');
  const up = await api('PATCH', `/v1/projects/${p.id}`, { token: tokenA, body: { name: 'Renamed', isFavorite: true, isDraft: false } });
  assert.equal(up.json.data.project.name, 'Renamed'); assert.equal(up.json.data.project.isFavorite, true);
  assert.equal((await api('POST', `/v1/projects/${p.id}/duplicate`, { token: tokenA })).json.data.project.name, 'Renamed (copy)');
  assert.equal((await api('GET', '/v1/projects?q=renam&favorite=true', { token: tokenA })).json.data.total, 1);
  assert.equal((await api('GET', '/v1/projects?q=100%25', { token: tokenA })).json.data.total, 0, 'LIKE wildcards are escaped');
  assert.equal((await api('PUT', '/v1/favorites/voices/abc123def456', { token: tokenA, body: { name: 'Provider Voice' } })).status, 200);
  assert.equal((await api('GET', '/v1/favorites/voices', { token: tokenA })).json.data.voices[0].voiceId, 'abc123def456');
  assert.equal((await api('DELETE', '/v1/favorites/voices/abc123def456', { token: tokenA })).status, 200);
  assert.equal((await api('GET', '/v1/favorites/voices', { token: tokenA })).json.data.voices.length, 0);
  assert.equal((await api('DELETE', `/v1/projects/${p.id}`, { token: tokenA })).status, 200);
});

test('admin: promoted user can manage plans; disabled users lose access immediately', async () => {
  const { execFileSync } = require('node:child_process');
  app.db.prepare("UPDATE users SET role='admin' WHERE email='bob@example.com'").run();
  const stats = await api('GET', '/v1/admin/stats', { token: tokenB });
  assert.equal(stats.status, 200); assert.equal(stats.json.data.users, 2);
  assert.equal((await api('GET', '/v1/admin/provider', { token: tokenB })).json.data.healthy, true);
  assert.equal((await api('PATCH', `/v1/admin/users/${userA.id}`, { token: tokenB, body: { plan: 'enterprise' } })).json.error.code, 'unknown_plan');
  assert.equal((await api('PATCH', `/v1/admin/users/${userA.id}`, { token: tokenB, body: { plan: 'pro' } })).status, 200);
  assert.equal((await api('GET', '/v1/usage', { token: tokenA })).json.data.limitChars, 100000);
  assert.equal((await api('PATCH', `/v1/admin/users/${userA.id}`, { token: tokenB, body: { disabled: true } })).status, 200);
  assert.equal((await api('GET', '/v1/me', { token: tokenA })).status, 401);
  assert.ok((await api('GET', '/v1/admin/audit', { token: tokenB })).json.data.entries.length >= 2);
  void execFileSync;
});

test('account deletion removes user data and stored audio', async () => {
  app.db.prepare('UPDATE users SET disabled=0 WHERE email=?').run('alice@example.com');
  const dir = path.join(tmp, 'audio', userA.id);
  assert.ok(fs.existsSync(dir) && fs.readdirSync(dir).length > 0);
  assert.equal((await api('DELETE', '/v1/account', { token: tokenA, body: { password: 'wrong' } })).status, 403);
  assert.equal((await api('DELETE', '/v1/account', { token: tokenA, body: { password: 'NewPassw0rd2' } })).status, 200);
  assert.equal(fs.existsSync(dir), false);
  for (const t of ['projects', 'generations', 'audio_assets', 'favorite_voices', 'usage_records', 'tokens', 'consent_records'])
    assert.equal(app.db.prepare(`SELECT COUNT(*) c FROM ${t} WHERE user_id=?`).get(userA.id).c, 0, t);
  assert.equal((await api('GET', '/v1/me', { token: tokenA })).status, 401);
});

test('misc: 404/405, invalid JSON, oversized body, healthz', async () => {
  assert.equal((await api('GET', '/nope')).status, 404);
  assert.equal((await api('PUT', '/v1/auth/login')).status, 405);
  const bad = await fetch(base + '/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{not json' });
  assert.equal((await bad.json()).error.code, 'invalid_json');
  assert.equal((await api('GET', '/healthz')).json.data.status, 'ok');
});

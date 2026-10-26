'use strict';
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { load } = require('../src/config');
const { createApp } = require('../src/app');
const { parse } = require('../src/dotenv');
const Providers = require('../src/providers');

// Local stand-ins for the OpenAI and Google endpoints (test infrastructure only).
let stub, stubUrl, last = {}, mode = 'ok';
const MP3 = Buffer.concat([Buffer.from('ID3'), Buffer.alloc(500, 3)]);
let app, base, token;

async function api(method, p, { body, tok = token } = {}) {
  const res = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(tok ? { Authorization: `Bearer ${tok}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const ct = res.headers.get('content-type') || '';
  return { status: res.status, headers: res.headers, json: ct.includes('json') ? await res.json() : null, buf: ct.includes('json') ? null : Buffer.from(await res.arrayBuffer()) };
}

before(async () => {
  stub = http.createServer((req, res) => {
    const chunks = []; req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString();
      const json = (code, obj) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };
      if (req.url === '/v1/audio/speech') {
        last.openai = { auth: req.headers.authorization, body: JSON.parse(raw) };
        if (req.headers.authorization !== 'Bearer openai-test-key') return json(401, { error: { message: 'Incorrect API key provided' } });
        if (mode === 'quota') return json(429, { error: { code: 'insufficient_quota', message: 'You exceeded your current quota' } });
        if (mode === 'rate') { res.writeHead(429, { 'retry-after': '3' }); return res.end('{}'); }
        res.writeHead(200, { 'content-type': 'audio/mpeg' }); return res.end(MP3);
      }
      if (req.url === '/v1/models') return req.headers.authorization === 'Bearer openai-test-key' ? json(200, { data: [] }) : json(401, {});
      if (req.url.startsWith('/g/v1/voices')) {
        if (req.headers['x-goog-api-key'] !== 'google-test-key') return json(403, { error: { status: 'PERMISSION_DENIED', message: 'API has not been used' } });
        return json(200, { voices: [
          { name: 'en-US-Neural2-A', languageCodes: ['en-US'], ssmlGender: 'MALE', naturalSampleRateHertz: 24000 },
          { name: 'en-US-Standard-B', languageCodes: ['en-US'], ssmlGender: 'MALE', naturalSampleRateHertz: 24000 },
          { name: 'hi-IN-Wavenet-A', languageCodes: ['hi-IN'], ssmlGender: 'FEMALE', naturalSampleRateHertz: 24000 },
        ] });
      }
      if (req.url === '/g/v1/text:synthesize') {
        last.google = { key: req.headers['x-goog-api-key'], body: JSON.parse(raw) };
        if (req.headers['x-goog-api-key'] !== 'google-test-key') return json(403, { error: { status: 'PERMISSION_DENIED', message: 'denied' } });
        if (mode === 'invalid') return json(400, { error: { status: 'INVALID_ARGUMENT', message: 'Voice does not exist' } });
        if (mode === 'gquota') return json(429, { error: { status: 'RESOURCE_EXHAUSTED', message: 'Quota exceeded for quota metric' } });
        return json(200, { audioContent: MP3.toString('base64') });
      }
      res.writeHead(404); res.end();
    });
  });
  await new Promise((r) => stub.listen(0, r)); stubUrl = `http://127.0.0.1:${stub.address().port}`;
  const config = load({ JWT_SECRET: 'j'.repeat(40), DB_PATH: ':memory:', AUDIO_DIR: path.join(os.tmpdir(), 'avs-prov'), OPENAI_API_KEY: 'openai-test-key', OPENAI_BASE_URL: stubUrl,
    GOOGLE_TTS_API_KEY: 'google-test-key', GOOGLE_TTS_BASE_URL: stubUrl + '/g', FREE_MONTHLY_CHARS: '100000', AUTH_RATE_LIMIT_PER_MIN: '1000' });
  app = createApp(config);
  await new Promise((r) => app.server.listen(0, r)); base = `http://127.0.0.1:${app.server.address().port}`;
  const s = await api('POST', '/v1/auth/signup', { tok: null, body: { name: 'P', email: 'p@example.com', password: 'Passw0rd1', acceptTerms: true } });
  token = s.json.data.session.accessToken;
});
after(async () => { await app.close(); stub.close(); });

test('dotenv parser handles quotes, comments and export', () => {
  const v = parse('# c\nA=1\nexport B="two words"\nC=\'x\'\nD=val # note\n\nE=\n');
  assert.deepEqual(v, { A: '1', B: 'two words', C: 'x', D: 'val', E: '' });
});

test('provider registry: default selection and honest listing', () => {
  assert.equal(Providers.defaultProviderId(load({ JWT_SECRET: 'x'.repeat(32) })), 'elevenlabs');
  assert.equal(Providers.defaultProviderId(load({ JWT_SECRET: 'x'.repeat(32), OPENAI_API_KEY: 'k' })), 'openai');
  assert.equal(Providers.defaultProviderId(load({ JWT_SECRET: 'x'.repeat(32), OPENAI_API_KEY: 'k', TTS_PROVIDER: 'google' })), 'google');
  assert.throws(() => require('../src/config').validate(load({ JWT_SECRET: 'x'.repeat(32), TTS_PROVIDER: 'azure' })), /TTS_PROVIDER/);
});

test('GET /v1/providers lists all three, marking which are configured', async () => {
  const r = await api('GET', '/v1/providers');
  const by = Object.fromEntries(r.json.data.providers.map((p) => [p.id, p]));
  assert.equal(by.openai.configured, true); assert.equal(by.google.configured, true); assert.equal(by.elevenlabs.configured, false);
  assert.equal(r.json.data.defaultProvider, 'openai', 'first configured provider is the default');
  assert.equal(JSON.stringify(r.json).includes('test-key'), false);
});

test('unconfigured / unknown providers are rejected honestly', async () => {
  const r = await api('GET', '/v1/voices?provider=elevenlabs');
  assert.equal(r.status, 503); assert.equal(r.json.error.code, 'provider_not_configured'); assert.match(r.json.error.message, /ELEVENLABS_API_KEY/);
  assert.equal((await api('GET', '/v1/voices?provider=azure')).json.error.code, 'unknown_provider');
});

test('OpenAI: voices, models, request construction, real bytes, quota + rate-limit mapping', async () => {
  const v = await api('GET', '/v1/voices?provider=openai&search=allo');
  assert.deepEqual(v.json.data.voices.map((x) => x.voiceId), ['alloy']);
  const m = await api('GET', '/v1/models?provider=openai');
  assert.equal(m.json.data.models[0].modelId, 'gpt-4o-mini-tts'); assert.equal(m.json.data.capabilities.stability, false); assert.equal(m.json.data.maxTextChars, 4096);
  const ok = await api('POST', '/v1/tts', { body: { provider: 'openai', text: 'Namaste duniya', voiceId: 'coral', outputFormat: 'mp3', voiceSettings: { speed: 1.5 } } });
  assert.equal(ok.status, 200); assert.ok(ok.buf.equals(MP3)); assert.equal(ok.headers.get('x-tts-provider'), 'openai');
  assert.deepEqual(last.openai.body, { model: 'gpt-4o-mini-tts', input: 'Namaste duniya', voice: 'coral', response_format: 'mp3', speed: 1.5 });
  assert.equal(last.openai.auth, 'Bearer openai-test-key');
  assert.equal((await api('POST', '/v1/tts', { body: { provider: 'openai', text: 'hi', voiceId: 'madeupvoice' } })).json.error.code, 'unknown_voice');
  assert.equal((await api('POST', '/v1/tts', { body: { provider: 'openai', text: 'hi', voiceId: 'alloy', voiceSettings: { stability: 0.5 } } })).json.error.code, 'unsupported_voice_setting');
  assert.equal((await api('POST', '/v1/tts', { body: { provider: 'openai', text: 'hi', voiceId: 'alloy', outputFormat: 'mp3_44100_128' } })).json.error.code, 'unsupported_output_format');
  mode = 'quota'; assert.equal((await api('POST', '/v1/tts', { body: { provider: 'openai', text: 'hi', voiceId: 'alloy' } })).json.error.code, 'provider_quota_exceeded');
  mode = 'rate'; const rl = await api('POST', '/v1/tts', { body: { provider: 'openai', text: 'hi', voiceId: 'alloy' } }); assert.equal(rl.status, 429); assert.equal(rl.headers.get('retry-after'), '3');
  mode = 'ok';
  const hist = await api('GET', '/v1/generations');
  assert.ok(hist.json.data.generations.some((g) => g.provider === 'openai' && g.status === 'completed'));
});

test('Google: voice list filtering/paging, request construction, decoded audio, error mapping', async () => {
  const all = await api('GET', '/v1/voices?provider=google&pageSize=2');
  assert.equal(all.json.data.voices.length, 2); assert.equal(all.json.data.hasMore, true); assert.equal(all.json.data.totalCount, 3);
  const page2 = await api('GET', `/v1/voices?provider=google&pageSize=2&pageToken=${all.json.data.nextPageToken}`);
  assert.equal(page2.json.data.voices.length, 1); assert.equal(page2.json.data.hasMore, false);
  const hi = await api('GET', '/v1/voices?provider=google&search=hi-IN');
  assert.deepEqual(hi.json.data.voices.map((x) => x.voiceId), ['hi-IN-Wavenet-A']);
  assert.equal(hi.json.data.voices[0].languages[0].locale, 'hi-IN');
  assert.equal((await api('GET', '/v1/voices?provider=google&category=neural2')).json.data.voices[0].voiceId, 'en-US-Neural2-A');
  const ok = await api('POST', '/v1/tts', { body: { provider: 'google', text: 'Hello there', voiceId: 'en-US-Neural2-A', outputFormat: 'opus', voiceSettings: { speed: 0.9 } } });
  assert.equal(ok.status, 200); assert.ok(ok.buf.equals(MP3)); assert.equal(ok.headers.get('content-type'), 'audio/ogg');
  assert.deepEqual(last.google.body, { input: { text: 'Hello there' }, voice: { languageCode: 'en-US', name: 'en-US-Neural2-A' }, audioConfig: { audioEncoding: 'OGG_OPUS', speakingRate: 0.9 } });
  assert.equal(last.google.key, 'google-test-key');
  assert.equal((await api('POST', '/v1/tts', { body: { provider: 'google', text: 'hi', voiceId: 'not a voice' } })).json.error.code, 'invalid_field');
  mode = 'invalid'; assert.equal((await api('POST', '/v1/tts', { body: { provider: 'google', text: 'hi', voiceId: 'en-US-Neural2-Z' } })).json.error.code, 'provider_rejected_request');
  mode = 'gquota'; assert.equal((await api('POST', '/v1/tts', { body: { provider: 'google', text: 'hi', voiceId: 'en-US-Neural2-A' } })).json.error.code, 'provider_quota_exceeded');
  mode = 'ok';
});

test('favourites are stored per provider', async () => {
  await api('PUT', '/v1/favorites/voices/alloy', { body: { name: 'Alloy', provider: 'openai' } });
  await api('PUT', '/v1/favorites/voices/en-US-Neural2-A', { body: { name: 'Neural2 A', provider: 'google' } });
  assert.deepEqual((await api('GET', '/v1/favorites/voices?provider=openai')).json.data.voices.map((v) => v.voiceId), ['alloy']);
  assert.deepEqual((await api('GET', '/v1/favorites/voices?provider=google')).json.data.voices.map((v) => v.voiceId), ['en-US-Neural2-A']);
});

test('admin provider health reports every provider without exposing keys', async () => {
  app.db.prepare("UPDATE users SET role='admin' WHERE email='p@example.com'").run();
  const r = await api('GET', '/v1/admin/provider');
  const by = Object.fromEntries(r.json.data.providers.map((p) => [p.id, p]));
  assert.equal(by.openai.healthy, true); assert.equal(by.google.healthy, true); assert.equal(by.elevenlabs.configured, false);
  assert.equal(JSON.stringify(r.json).includes('test-key'), false);
});

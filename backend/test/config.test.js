'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { load, validate } = require('../src/config');
const { createApp } = require('../src/app');
const http = require('node:http');

test('server refuses to start without a strong JWT secret', () => {
  assert.throws(() => validate(load({})), /JWT_SECRET/);
  assert.doesNotThrow(() => validate(load({ JWT_SECRET: 'x'.repeat(32) })));
});

test('without a provider key the API says so honestly (no fake audio)', async () => {
  const app = createApp(load({ JWT_SECRET: 'x'.repeat(32), DB_PATH: ':memory:', AUDIO_DIR: require('node:os').tmpdir() + '/avs-nokey' }));
  await new Promise((r) => app.server.listen(0, r));
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const j = (p, o) => fetch(base + p, { ...o, headers: { 'Content-Type': 'application/json', ...(o && o.headers) } }).then(async (r) => ({ s: r.status, j: await r.json() }));
  const su = await j('/v1/auth/signup', { method: 'POST', body: JSON.stringify({ name: 'N', email: 'n@example.com', password: 'Passw0rd1', acceptTerms: true }) });
  assert.equal(su.s, 201); assert.equal(su.j.data.verification.required, false);
  const h = { Authorization: `Bearer ${su.j.data.session.accessToken}` };
  const v = await j('/v1/voices', { headers: h }); assert.equal(v.s, 503); assert.equal(v.j.error.code, 'provider_not_configured');
  const t = await j('/v1/tts', { method: 'POST', headers: h, body: JSON.stringify({ text: 'hi', voiceId: 'abc123def456' }) });
  assert.equal(t.s, 503); assert.equal(t.j.error.code, 'provider_not_configured');
  await app.close(); void http;
});

test('admin dashboard is served with a strict CSP and contains no secrets', async () => {
  const app = createApp(load({ JWT_SECRET: 'x'.repeat(32), DB_PATH: ':memory:', AUDIO_DIR: require('node:os').tmpdir() + '/avs-admin', ELEVENLABS_API_KEY: 'secret-key-value' }));
  await new Promise((r) => app.server.listen(0, r));
  const base = `http://127.0.0.1:${app.server.address().port}`;
  for (const p of ['/admin', '/admin/app.js', '/admin/style.css']) {
    const res = await fetch(base + p);
    assert.equal(res.status, 200, p);
    assert.match(res.headers.get('content-security-policy'), /default-src 'none'/);
    assert.equal((await res.text()).includes('secret-key-value'), false);
  }
  await app.close();
});

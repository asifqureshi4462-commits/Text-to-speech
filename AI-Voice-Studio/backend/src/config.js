'use strict';
const path = require('node:path');

function load(env = process.env) {
  const num = (k, d) => (env[k] !== undefined && env[k] !== '' ? Number(env[k]) : d);
  let planLimits = { free: num('FREE_MONTHLY_CHARS', 10000) };
  if (env.PLAN_LIMITS_JSON) {
    try { planLimits = { ...planLimits, ...JSON.parse(env.PLAN_LIMITS_JSON) }; }
    catch { throw new Error('PLAN_LIMITS_JSON is not valid JSON'); }
  }
  const dataDir = path.join(__dirname, '..', 'data');
  return {
    port: num('PORT', 8080),
    dbPath: env.DB_PATH || path.join(dataDir, 'app.db'),
    audioDir: env.AUDIO_DIR || path.join(dataDir, 'audio'),
    jwtSecret: env.JWT_SECRET || '',
    accessTtlSec: num('ACCESS_TOKEN_TTL_SECONDS', 3600),
    refreshTtlSec: num('REFRESH_TOKEN_TTL_SECONDS', 60 * 60 * 24 * 30),
    elevenKey: env.ELEVENLABS_API_KEY || '',
    elevenBase: (env.ELEVENLABS_BASE_URL || 'https://api.elevenlabs.io').replace(/\/$/, ''),
    providerTimeoutMs: num('PROVIDER_TIMEOUT_MS', 90000),
    maxTextChars: num('MAX_TEXT_CHARS', 5000),
    planLimits,
    requireEmailVerification: env.REQUIRE_EMAIL_VERIFICATION === 'true',
    mailWebhookUrl: env.MAIL_WEBHOOK_URL || '',
    mailWebhookSecret: env.MAIL_WEBHOOK_SECRET || '',
    publicBaseUrl: (env.PUBLIC_BASE_URL || '').replace(/\/$/, ''),
    trustProxy: env.TRUST_PROXY === 'true',
    authRateLimitPerMin: num('AUTH_RATE_LIMIT_PER_MIN', 10),
    ttsRateLimitPerMin: num('TTS_RATE_LIMIT_PER_MIN', 20),
    globalRateLimitPerMin: num('GLOBAL_RATE_LIMIT_PER_MIN', 240),
    maxBodyBytes: num('MAX_BODY_BYTES', 1024 * 1024),
  };
}

function validate(c) {
  if (c.jwtSecret.length < 32) {
    throw new Error('JWT_SECRET must be set and at least 32 characters (e.g. `openssl rand -hex 32`).');
  }
  return c;
}

module.exports = { load, validate };

'use strict';
const { ApiError } = require('./http');

const OUTPUT_FORMATS = {
  mp3_22050_32: { mime: 'audio/mpeg', ext: 'mp3' },
  mp3_44100_32: { mime: 'audio/mpeg', ext: 'mp3' },
  mp3_44100_64: { mime: 'audio/mpeg', ext: 'mp3' },
  mp3_44100_96: { mime: 'audio/mpeg', ext: 'mp3' },
  mp3_44100_128: { mime: 'audio/mpeg', ext: 'mp3' },
  mp3_44100_192: { mime: 'audio/mpeg', ext: 'mp3' },
  opus_48000_32: { mime: 'audio/ogg', ext: 'opus' },
  opus_48000_64: { mime: 'audio/ogg', ext: 'opus' },
  opus_48000_96: { mime: 'audio/ogg', ext: 'opus' },
  opus_48000_128: { mime: 'audio/ogg', ext: 'opus' },
  opus_48000_192: { mime: 'audio/ogg', ext: 'opus' },
};

function requireConfigured(config) {
  if (!config.elevenKey) {
    throw new ApiError(503, 'provider_not_configured',
      'The voice provider is not configured on the server. An administrator must set ELEVENLABS_API_KEY.');
  }
}

async function detail(res) {
  try {
    const j = await res.json();
    const d = j && (j.detail ?? j);
    if (typeof d === 'string') return { message: d, status: undefined };
    if (d && typeof d === 'object') return { message: typeof d.message === 'string' ? d.message : undefined, status: d.status };
  } catch { /* non-JSON error body */ }
  return {};
}

/** Maps a provider HTTP error to an honest, client-safe ApiError. Provider credentials are never revealed. */
async function mapProviderError(res) {
  const d = await detail(res);
  const quota = d.status === 'quota_exceeded' || /quota/i.test(d.message || '');
  if (quota) return new ApiError(402, 'provider_quota_exceeded', 'The voice provider account has run out of quota.');
  if (res.status === 401 || res.status === 403)
    return new ApiError(502, 'provider_auth_failed', 'The server could not authenticate with the voice provider. Contact the administrator.');
  if (res.status === 404) return new ApiError(404, 'provider_not_found', 'The requested voice or model was not found at the provider.');
  if (res.status === 422 || res.status === 400)
    return new ApiError(422, 'provider_rejected_request', d.message || 'The provider rejected the request (unsupported voice, model, language or setting).');
  if (res.status === 429) {
    const ra = Number(res.headers.get('retry-after'));
    return new ApiError(429, 'provider_rate_limited', 'The voice provider is rate limiting requests. Try again shortly.', ra > 0 ? { retryAfter: ra } : undefined);
  }
  return new ApiError(502, 'provider_error', `The voice provider returned an error (HTTP ${res.status}).`);
}

async function providerFetch(config, path, init = {}, signal) {
  requireConfigured(config);
  const timeout = AbortSignal.timeout(config.providerTimeoutMs);
  const sig = signal ? AbortSignal.any([signal, timeout]) : timeout;
  try {
    return await fetch(config.elevenBase + path, {
      ...init,
      headers: { 'xi-api-key': config.elevenKey, ...(init.headers || {}) },
      signal: sig,
    });
  } catch (e) {
    if (signal && signal.aborted) throw new ApiError(499, 'client_cancelled', 'Request cancelled.');
    if (e.name === 'TimeoutError') throw new ApiError(504, 'provider_timeout', 'The voice provider took too long to respond.');
    throw new ApiError(502, 'provider_unreachable', 'Could not reach the voice provider.');
  }
}

module.exports = { OUTPUT_FORMATS, requireConfigured, mapProviderError, providerFetch };

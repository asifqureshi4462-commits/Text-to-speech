'use strict';
const { ApiError } = require('../http');

/** fetch() with a timeout and client-cancel support; maps network failures to honest ApiErrors. */
async function request(config, url, init, signal) {
  const timeout = AbortSignal.timeout(config.providerTimeoutMs);
  const sig = signal ? AbortSignal.any([signal, timeout]) : timeout;
  try {
    return await fetch(url, { ...init, signal: sig });
  } catch (e) {
    if (signal && signal.aborted) throw new ApiError(499, 'client_cancelled', 'Request cancelled.');
    if (e.name === 'TimeoutError') throw new ApiError(504, 'provider_timeout', 'The voice provider took too long to respond.');
    throw new ApiError(502, 'provider_unreachable', 'Could not reach the voice provider.');
  }
}

async function readJson(res) {
  try { return await res.json(); } catch { return null; }
}

/** Shared mapping once a provider-specific adapter has decided what kind of failure it was. */
function failure(kind, message, extra) {
  switch (kind) {
    case 'quota': return new ApiError(402, 'provider_quota_exceeded', 'The voice provider account has run out of quota or credit.');
    case 'auth': return new ApiError(502, 'provider_auth_failed', 'The server could not authenticate with the voice provider. Contact the administrator (API key missing, invalid, or not enabled).');
    case 'notfound': return new ApiError(404, 'provider_not_found', 'The requested voice or model was not found at the provider.');
    case 'rejected': return new ApiError(422, 'provider_rejected_request', message || 'The provider rejected the request (unsupported voice, model, language or setting).');
    case 'ratelimit': return new ApiError(429, 'provider_rate_limited', 'The voice provider is rate limiting requests. Try again shortly.', extra);
    default: return new ApiError(502, 'provider_error', message || 'The voice provider returned an error.');
  }
}

module.exports = { request, readJson, failure };

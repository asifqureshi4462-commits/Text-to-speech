'use strict';
const { ApiError } = require('../http');

const ALL = [require('./elevenlabs'), require('./openai'), require('./google')];
const BY_ID = Object.fromEntries(ALL.map((p) => [p.id, p]));
const ORDER = ALL.map((p) => p.id);

/** The provider used when a request does not name one: TTS_PROVIDER, else the first configured, else ElevenLabs. */
function defaultProviderId(config) {
  if (config.defaultProvider && BY_ID[config.defaultProvider]) return config.defaultProvider;
  const first = ALL.find((p) => p.isConfigured(config));
  return first ? first.id : 'elevenlabs';
}

/** Resolve a (possibly missing) provider id to an adapter; does not require it to be configured. */
function resolve(config, requested) {
  if (requested === undefined || requested === null || requested === '') return BY_ID[defaultProviderId(config)];
  const p = typeof requested === 'string' ? BY_ID[requested.toLowerCase()] : null;
  if (!p) throw new ApiError(400, 'unknown_provider', `Unknown provider. Supported: ${ORDER.join(', ')}.`, { supported: ORDER });
  return p;
}

/** Resolve and require that the server has credentials for it. */
function resolveConfigured(config, requested) {
  const p = resolve(config, requested);
  if (!p.isConfigured(config)) {
    throw new ApiError(503, 'provider_not_configured',
      `The "${p.label}" provider is not configured on the server. An administrator must set ${p.envVar}.`, { provider: p.id });
  }
  return p;
}

function describe(config) {
  const def = defaultProviderId(config);
  return ALL.map((p) => ({
    id: p.id, label: p.label, configured: p.isConfigured(config), isDefault: p.id === def,
    capabilities: p.capabilities, outputFormats: p.formats, maxChars: p.maxChars,
  }));
}

module.exports = { ALL, BY_ID, defaultProviderId, resolve, resolveConfigured, describe };

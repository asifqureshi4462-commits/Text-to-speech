'use strict';
const { ApiError } = require('../http');
const { request, readJson, failure } = require('./common');

// Documented OpenAI text-to-speech voices. Edit this list if OpenAI adds voices you want to expose.
const VOICES = ['alloy', 'ash', 'ballad', 'coral', 'echo', 'fable', 'nova', 'onyx', 'sage', 'shimmer', 'verse'];
const MODELS = [
  { modelId: 'gpt-4o-mini-tts', name: 'GPT-4o mini TTS', description: 'Newest, most natural OpenAI voice model.' },
  { modelId: 'tts-1', name: 'TTS-1', description: 'Lower latency.' },
  { modelId: 'tts-1-hd', name: 'TTS-1 HD', description: 'Higher quality.' },
];
const FORMATS = { mp3: { mime: 'audio/mpeg', ext: 'mp3' }, opus: { mime: 'audio/ogg', ext: 'opus' } };

const auth = (c) => ({ Authorization: `Bearer ${c.openaiKey}` });

async function mapError(res) {
  const j = await readJson(res);
  const e = (j && j.error) || {};
  if (res.status === 401 || res.status === 403) return failure('auth');
  if (res.status === 404) return failure('notfound');
  if (res.status === 429) {
    if (e.code === 'insufficient_quota' || /quota|billing/i.test(e.message || '')) return failure('quota');
    const ra = Number(res.headers.get('retry-after'));
    return failure('ratelimit', null, ra > 0 ? { retryAfter: ra } : undefined);
  }
  if (res.status === 400 || res.status === 422) return failure('rejected', typeof e.message === 'string' ? e.message : null);
  return failure('error', `The voice provider returned an error (HTTP ${res.status}).`);
}

module.exports = {
  id: 'openai',
  label: 'OpenAI',
  envVar: 'OPENAI_API_KEY',
  defaultModel: 'gpt-4o-mini-tts',
  defaultFormat: 'mp3',
  formats: Object.keys(FORMATS),
  capabilities: { stability: false, similarity: false, style: false, speakerBoost: false, speed: true, speedMin: 0.25, speedMax: 4.0 },
  maxChars: 4096,
  isConfigured: (c) => !!c.openaiKey,
  isKnownVoice: (id) => VOICES.includes(id),

  async listVoices(c, q) {
    const needle = (q.search || '').toLowerCase();
    let list = VOICES.filter((v) => !needle || v.includes(needle));
    if (q.sort_direction === 'desc') list = list.slice().reverse();
    return {
      voices: list.map((v) => ({ voiceId: v, name: v[0].toUpperCase() + v.slice(1), category: 'openai', description: null, previewUrl: null, labels: {}, languages: [] })),
      hasMore: false, nextPageToken: null, totalCount: list.length,
    };
  },

  async listModels() {
    return MODELS.map((m) => ({ ...m, maxCharacters: 4096, canUseStyle: false, canUseSpeakerBoost: false, languages: [] }));
  },

  async synthesize(c, r, signal) {
    const body = { model: r.modelId, input: r.text, voice: r.voiceId, response_format: r.outputFormat };
    if (r.voiceSettings && r.voiceSettings.speed !== undefined) body.speed = r.voiceSettings.speed;
    const res = await request(c, `${c.openaiBase}/v1/audio/speech`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...auth(c) }, body: JSON.stringify(body),
    }, signal);
    if (!res.ok) throw await mapError(res);
    const f = FORMATS[r.outputFormat];
    return { audio: Buffer.from(await res.arrayBuffer()), mime: f.mime, ext: f.ext };
  },

  async health(c, signal) {
    const res = await request(c, `${c.openaiBase}/v1/models`, { headers: auth(c) }, signal);
    return { healthy: res.ok, httpStatus: res.status };
  },
};

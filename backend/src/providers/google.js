'use strict';
const { ApiError } = require('../http');
const { request, readJson, failure } = require('./common');

const ENCODINGS = { mp3: { enc: 'MP3', mime: 'audio/mpeg', ext: 'mp3' }, opus: { enc: 'OGG_OPUS', mime: 'audio/ogg', ext: 'opus' } };
const NAME_RE = /^[a-z]{2,3}-[A-Za-z0-9]{2,4}(-[A-Za-z0-9]+)+$/;
const cache = new Map(); // base+lang -> {at, voices}
const TTL_MS = 6 * 3600 * 1000;

const auth = (c) => ({ 'X-Goog-Api-Key': c.googleKey });

async function mapError(res) {
  const j = await readJson(res);
  const e = (j && j.error) || {};
  const msg = typeof e.message === 'string' ? e.message : null;
  if (res.status === 401 || res.status === 403) return failure('auth');
  if (res.status === 404) return failure('notfound');
  if (res.status === 429 || e.status === 'RESOURCE_EXHAUSTED') {
    if (/quota/i.test(msg || '') && !/per minute|rate/i.test(msg || '')) return failure('quota');
    return failure('ratelimit');
  }
  if (res.status === 400) return failure('rejected', msg);
  return failure('error', `The voice provider returned an error (HTTP ${res.status}).`);
}

async function fetchVoices(c, signal) {
  const key = c.googleBase;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < TTL_MS) return hit.voices;
  const res = await request(c, `${c.googleBase}/v1/voices`, { headers: auth(c) }, signal);
  if (!res.ok) throw await mapError(res);
  const j = (await readJson(res)) || {};
  const voices = (j.voices || []).filter((v) => v && typeof v.name === 'string');
  cache.set(key, { at: Date.now(), voices });
  return voices;
}

const categoryOf = (name) => (name.split('-')[2] || 'other').toLowerCase();

module.exports = {
  id: 'google',
  label: 'Google Cloud Text-to-Speech',
  envVar: 'GOOGLE_TTS_API_KEY',
  defaultModel: 'default',
  defaultFormat: 'mp3',
  formats: Object.keys(ENCODINGS),
  capabilities: { stability: false, similarity: false, style: false, speakerBoost: false, speed: true, speedMin: 0.25, speedMax: 4.0 },
  maxChars: 5000,
  maxBytes: 5000,
  isConfigured: (c) => !!c.googleKey,
  isValidVoiceName: (n) => NAME_RE.test(n),

  async listVoices(c, q, signal) {
    let list = await fetchVoices(c, signal);
    const needle = (q.search || '').toLowerCase();
    if (needle) list = list.filter((v) => v.name.toLowerCase().includes(needle) || (v.languageCodes || []).some((l) => l.toLowerCase().includes(needle)));
    if (q.category) list = list.filter((v) => categoryOf(v.name) === q.category.toLowerCase());
    list = list.slice().sort((a, b) => (q.sort_direction === 'desc' ? b.name.localeCompare(a.name) : a.name.localeCompare(b.name)));
    const offset = Math.max(0, Number.parseInt(q.pageToken || '0', 10) || 0);
    const page = list.slice(offset, offset + q.pageSize);
    const next = offset + page.length;
    return {
      voices: page.map((v) => ({
        voiceId: v.name, name: v.name, category: categoryOf(v.name), previewUrl: null,
        description: [v.ssmlGender && v.ssmlGender.toLowerCase(), v.naturalSampleRateHertz && `${v.naturalSampleRateHertz} Hz`].filter(Boolean).join(' · ') || null,
        labels: v.ssmlGender ? { gender: v.ssmlGender.toLowerCase() } : {},
        languages: (v.languageCodes || []).map((l) => ({ language: l, locale: l })),
      })),
      hasMore: next < list.length, nextPageToken: next < list.length ? String(next) : null, totalCount: list.length,
    };
  },

  async listModels() {
    return [{ modelId: 'default', name: 'Cloud Text-to-Speech', description: 'Voice type (Standard, WaveNet, Neural2, Studio, Chirp3-HD...) is part of the voice name.', maxCharacters: 5000, canUseStyle: false, canUseSpeakerBoost: false, languages: [] }];
  },

  async synthesize(c, r, signal) {
    const e = ENCODINGS[r.outputFormat];
    const languageCode = r.languageCode || r.voiceId.split('-').slice(0, 2).join('-');
    const audioConfig = { audioEncoding: e.enc };
    if (r.voiceSettings && r.voiceSettings.speed !== undefined) audioConfig.speakingRate = r.voiceSettings.speed;
    const res = await request(c, `${c.googleBase}/v1/text:synthesize`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...auth(c) },
      body: JSON.stringify({ input: { text: r.text }, voice: { languageCode, name: r.voiceId }, audioConfig }),
    }, signal);
    if (!res.ok) throw await mapError(res);
    const j = await readJson(res);
    const audio = j && typeof j.audioContent === 'string' ? Buffer.from(j.audioContent, 'base64') : Buffer.alloc(0);
    return { audio, mime: e.mime, ext: e.ext };
  },

  async health(c, signal) {
    const res = await request(c, `${c.googleBase}/v1/voices?languageCode=en-US`, { headers: auth(c) }, signal);
    return { healthy: res.ok, httpStatus: res.status };
  },
  _clearCache: () => cache.clear(),
};

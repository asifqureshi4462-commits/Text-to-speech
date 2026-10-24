'use strict';
const { ApiError } = require('../http');
const P = require('../provider');

const capabilities = { stability: true, similarity: true, style: true, speakerBoost: true, speed: true, speedMin: 0.7, speedMax: 1.2 };

module.exports = {
  id: 'elevenlabs',
  label: 'ElevenLabs',
  envVar: 'ELEVENLABS_API_KEY',
  defaultModel: 'eleven_multilingual_v2',
  defaultFormat: 'mp3_44100_128',
  formats: Object.keys(P.OUTPUT_FORMATS),
  capabilities,
  maxChars: null,
  isConfigured: (c) => !!c.elevenKey,

  async listVoices(c, q, signal) {
    const qs = new URLSearchParams();
    if (q.search) qs.set('search', q.search);
    qs.set('page_size', String(q.pageSize));
    if (q.pageToken) qs.set('next_page_token', q.pageToken);
    for (const k of ['category', 'sort', 'sort_direction', 'voice_type']) if (q[k]) qs.set(k, q[k]);
    const res = await P.providerFetch(c, `/v2/voices?${qs}`, {}, signal);
    if (!res.ok) throw await P.mapProviderError(res);
    const j = await res.json();
    return {
      voices: (j.voices || []).map((v) => ({
        voiceId: v.voice_id, name: v.name, category: v.category || null, description: v.description || null,
        previewUrl: v.preview_url || null, labels: v.labels || {},
        languages: (v.verified_languages || []).map((l) => ({ language: l.language, locale: l.locale, accent: l.accent, modelId: l.model_id })),
      })),
      hasMore: !!j.has_more, nextPageToken: j.next_page_token || null, totalCount: j.total_count ?? null,
    };
  },

  async listModels(c, signal) {
    const res = await P.providerFetch(c, '/v1/models', {}, signal);
    if (!res.ok) throw await P.mapProviderError(res);
    const j = await res.json();
    return (Array.isArray(j) ? j : []).filter((m) => m.can_do_text_to_speech).map((m) => ({
      modelId: m.model_id, name: m.name, description: m.description || null,
      maxCharacters: m.maximum_text_length_per_request ?? null,
      canUseStyle: !!m.can_use_style, canUseSpeakerBoost: !!m.can_use_speaker_boost,
      languages: (m.languages || []).map((l) => ({ id: l.language_id, name: l.name })),
    }));
  },

  async synthesize(c, r, signal) {
    const fmt = P.OUTPUT_FORMATS[r.outputFormat];
    const payload = { text: r.text, model_id: r.modelId };
    if (r.languageCode) payload.language_code = r.languageCode;
    const s = r.voiceSettings || {};
    const vs = {};
    if (s.stability !== undefined) vs.stability = s.stability;
    if (s.similarityBoost !== undefined) vs.similarity_boost = s.similarityBoost;
    if (s.style !== undefined) vs.style = s.style;
    if (s.speed !== undefined) vs.speed = s.speed;
    if (s.useSpeakerBoost !== undefined) vs.use_speaker_boost = s.useSpeakerBoost;
    if (Object.keys(vs).length) payload.voice_settings = vs;
    const res = await P.providerFetch(c, `/v1/text-to-speech/${encodeURIComponent(r.voiceId)}?output_format=${r.outputFormat}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Accept: fmt.mime }, body: JSON.stringify(payload),
    }, signal);
    if (!res.ok) throw await P.mapProviderError(res);
    return { audio: Buffer.from(await res.arrayBuffer()), mime: fmt.mime, ext: fmt.ext };
  },

  async health(c, signal) {
    const res = await P.providerFetch(c, '/v1/models', {}, signal);
    return { healthy: res.ok, httpStatus: res.status };
  },
};

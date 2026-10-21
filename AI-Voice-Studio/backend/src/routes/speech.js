'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { ApiError, bad } = require('../http');
const V = require('../validate');
const S = require('../security');
const P = require('../provider');

const ID_RE = /^[A-Za-z0-9_-]{6,64}$/;
const MODEL_RE = /^[a-z0-9_.-]{3,64}$/;
const LANG_RE = /^[a-z]{2,3}(-[A-Za-z]{2,4})?$/;

function periodStart(d = new Date()) { return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1); }
function periodEnd(d = new Date()) { return Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1); }

module.exports = function speechRoutes(r, { db, config }) {
  const limitFor = (user) => config.planLimits[user.plan] ?? config.planLimits.free;
  const usedThisMonth = (userId) =>
    db.prepare("SELECT COALESCE(SUM(chars),0) s FROM usage_records WHERE user_id=? AND kind='tts' AND created_at>=? AND created_at<?").get(userId, periodStart(), periodEnd()).s;

  r.add('GET', '/v1/voices', { auth: true }, async ({ query, signal }) => {
    const qs = new URLSearchParams();
    const search = query.get('search'); if (search) qs.set('search', search.slice(0, 100));
    qs.set('page_size', String(V.intParam(query.get('pageSize'), 30, 1, 100)));
    const tok = query.get('pageToken'); if (tok) qs.set('next_page_token', tok.slice(0, 500));
    for (const k of ['category', 'sort', 'sort_direction', 'voice_type']) { const v = query.get(k); if (v && /^[a-z_]{2,30}$/.test(v)) qs.set(k, v); }
    const res = await P.providerFetch(config, `/v2/voices?${qs}`, {}, signal);
    if (!res.ok) throw await P.mapProviderError(res);
    const j = await res.json();
    return { data: {
      voices: (j.voices || []).map((v) => ({
        voiceId: v.voice_id, name: v.name, category: v.category || null, description: v.description || null,
        previewUrl: v.preview_url || null, labels: v.labels || {},
        languages: (v.verified_languages || []).map((l) => ({ language: l.language, locale: l.locale, accent: l.accent, modelId: l.model_id })),
      })),
      hasMore: !!j.has_more, nextPageToken: j.next_page_token || null, totalCount: j.total_count ?? null,
    } };
  });

  r.add('GET', '/v1/models', { auth: true }, async ({ signal }) => {
    const res = await P.providerFetch(config, '/v1/models', {}, signal);
    if (!res.ok) throw await P.mapProviderError(res);
    const j = await res.json();
    return { data: {
      models: (Array.isArray(j) ? j : []).filter((m) => m.can_do_text_to_speech).map((m) => ({
        modelId: m.model_id, name: m.name, description: m.description || null,
        maxCharacters: m.maximum_text_length_per_request ?? null,
        canUseStyle: !!m.can_use_style, canUseSpeakerBoost: !!m.can_use_speaker_boost,
        languages: (m.languages || []).map((l) => ({ id: l.language_id, name: l.name })),
      })),
      outputFormats: Object.keys(P.OUTPUT_FORMATS),
      maxTextChars: config.maxTextChars,
    } };
  });

  r.add('POST', '/v1/tts', { auth: true, limit: 'tts' }, async ({ body, user, signal }) => {
    P.requireConfigured(config);
    const text = V.str(body.text, 'text', { min: 1, max: config.maxTextChars + 1, trim: false });
    if (!text.trim()) throw bad('empty_text', 'Enter some text to convert.', { field: 'text' });
    if (text.length > config.maxTextChars) throw bad('text_too_long', `Text is limited to ${config.maxTextChars} characters per request. Use Long Script to split longer text.`, { field: 'text', max: config.maxTextChars });
    const voiceId = V.str(body.voiceId, 'voiceId', { max: 64 });
    if (!ID_RE.test(voiceId)) throw bad('invalid_field', 'voiceId is invalid.', { field: 'voiceId' });
    const modelId = body.modelId === undefined ? 'eleven_multilingual_v2' : V.str(body.modelId, 'modelId', { max: 64 });
    if (!MODEL_RE.test(modelId)) throw bad('invalid_field', 'modelId is invalid.', { field: 'modelId' });
    const outputFormat = body.outputFormat === undefined ? 'mp3_44100_128' : body.outputFormat;
    const fmt = P.OUTPUT_FORMATS[outputFormat];
    if (!fmt) throw bad('unsupported_output_format', 'Unsupported output format.', { field: 'outputFormat', supported: Object.keys(P.OUTPUT_FORMATS) });
    const payload = { text, model_id: modelId };
    if (body.languageCode !== undefined) {
      const l = V.str(body.languageCode, 'languageCode', { max: 12 });
      if (!LANG_RE.test(l)) throw bad('invalid_field', 'languageCode is invalid.', { field: 'languageCode' });
      payload.language_code = l;
    }
    const settings = {};
    const vs = body.voiceSettings;
    if (vs !== undefined) {
      if (vs === null || typeof vs !== 'object' || Array.isArray(vs)) throw bad('invalid_field', 'voiceSettings must be an object.', { field: 'voiceSettings' });
      if (vs.stability !== undefined) settings.stability = V.num(vs.stability, 'stability', 0, 1);
      if (vs.similarityBoost !== undefined) settings.similarity_boost = V.num(vs.similarityBoost, 'similarityBoost', 0, 1);
      if (vs.style !== undefined) settings.style = V.num(vs.style, 'style', 0, 1);
      if (vs.speed !== undefined) settings.speed = V.num(vs.speed, 'speed', 0.7, 1.2);
      if (vs.useSpeakerBoost !== undefined) {
        if (typeof vs.useSpeakerBoost !== 'boolean') throw bad('invalid_field', 'useSpeakerBoost must be true or false.', { field: 'useSpeakerBoost' });
        settings.use_speaker_boost = vs.useSpeakerBoost;
      }
      if (Object.keys(settings).length) payload.voice_settings = settings;
    }
    let projectId = null;
    if (body.projectId !== undefined && body.projectId !== null) {
      projectId = V.str(body.projectId, 'projectId', { max: 64 });
      if (!db.prepare('SELECT 1 FROM projects WHERE id=? AND user_id=?').get(projectId, user.id)) throw new ApiError(404, 'not_found', 'Project not found.');
    }

    const used = usedThisMonth(user.id), limit = limitFor(user);
    if (used + text.length > limit)
      throw new ApiError(402, 'usage_limit_exceeded', 'This request would exceed your monthly character limit.', { limit, used, requested: text.length, resetsAt: periodEnd() });

    const genId = S.newId();
    db.prepare(`INSERT INTO generations (id,user_id,project_id,provider,model,voice_id,char_count,text_preview,text,output_format,status,settings,created_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,'processing',?,?)`)
      .run(genId, user.id, projectId, 'elevenlabs', modelId, voiceId, text.length, text.slice(0, 120), text, outputFormat,
        JSON.stringify({ ...settings, languageCode: payload.language_code ?? null }), Date.now());
    const fail = (e) => {
      db.prepare("UPDATE generations SET status='failed', error_code=?, error_message=?, completed_at=? WHERE id=?").run(e.code || 'internal_error', String(e.message).slice(0, 300), Date.now(), genId);
      return e;
    };

    let res;
    try {
      res = await P.providerFetch(config, `/v1/text-to-speech/${encodeURIComponent(voiceId)}?output_format=${outputFormat}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Accept: fmt.mime }, body: JSON.stringify(payload),
      }, signal);
      if (!res.ok) throw await P.mapProviderError(res);
    } catch (e) { throw fail(e instanceof ApiError ? e : new ApiError(502, 'provider_error', 'Speech generation failed.')); }

    const audio = Buffer.from(await res.arrayBuffer());
    if (!audio.length) throw fail(new ApiError(502, 'provider_empty_audio', 'The provider returned no audio.'));

    const assetId = S.newId();
    const fileName = `${assetId}.${fmt.ext}`;
    const dir = path.join(config.audioDir, user.id);
    try {
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, fileName), audio, { mode: 0o600 });
    } catch { throw fail(new ApiError(500, 'storage_failed', 'Could not store the generated audio on the server.')); }
    const t = Date.now();
    db.exec('BEGIN');
    try {
      db.prepare('INSERT INTO audio_assets (id,user_id,project_id,file_name,mime,bytes,created_at) VALUES (?,?,?,?,?,?,?)').run(assetId, user.id, projectId, fileName, fmt.mime, audio.length, t);
      db.prepare("UPDATE generations SET status='completed', audio_asset_id=?, completed_at=? WHERE id=?").run(assetId, t, genId);
      db.prepare("INSERT INTO usage_records (id,user_id,kind,chars,generation_id,created_at) VALUES (?,?,'tts',?,?,?)").run(S.newId(), user.id, text.length, genId, t);
      db.exec('COMMIT');
    } catch (e) { db.exec('ROLLBACK'); throw fail(new ApiError(500, 'database_failed', 'Could not record the generation.')); }

    return { raw: { status: 200, headers: {
      'Content-Type': fmt.mime, 'Content-Length': String(audio.length),
      'X-Generation-Id': genId, 'X-Audio-Asset-Id': assetId, 'X-Characters-Used': String(text.length),
      'X-Usage-Remaining': String(Math.max(0, limit - used - text.length)),
    }, body: audio } };
  });

  r.add('GET', '/v1/usage', { auth: true }, async ({ user }) => {
    const used = usedThisMonth(user.id), limit = limitFor(user);
    const g = db.prepare("SELECT COUNT(*) c FROM generations WHERE user_id=? AND created_at>=? AND created_at<?").get(user.id, periodStart(), periodEnd()).c;
    return { data: { plan: user.plan, limitChars: limit, usedChars: used, remainingChars: Math.max(0, limit - used), generationsThisPeriod: g, periodStart: periodStart(), periodEnd: periodEnd(),
      note: 'Application usage limits are separate from your voice provider account billing.' } };
  });

  // ---- stored audio (private, owner-only, Range-capable for seeking) ----
  const ownedAsset = (id, userId) => {
    const a = db.prepare('SELECT * FROM audio_assets WHERE id=? AND user_id=?').get(id, userId);
    if (!a) throw new ApiError(404, 'not_found', 'Audio not found.');
    return a;
  };
  r.add('GET', '/v1/audio/:id', { auth: true }, async ({ params, user, req }) => {
    const a = ownedAsset(params.id, user.id);
    const file = path.join(config.audioDir, user.id, a.file_name);
    if (!fs.existsSync(file)) throw new ApiError(410, 'audio_missing', 'The audio file is no longer available on the server.');
    const size = fs.statSync(file).size;
    const base = { 'Content-Type': a.mime, 'Accept-Ranges': 'bytes', 'Content-Disposition': `attachment; filename="${a.file_name}"` };
    const m = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || '');
    if (m && (m[1] || m[2])) {
      let start = m[1] ? Number(m[1]) : size - Number(m[2]);
      let end = m[1] && m[2] ? Number(m[2]) : size - 1;
      end = Math.min(end, size - 1); start = Math.max(0, start);
      if (start > end || start >= size) return { raw: { status: 416, headers: { 'Content-Range': `bytes */${size}` }, body: Buffer.alloc(0) } };
      const fd = fs.openSync(file, 'r'); const buf = Buffer.alloc(end - start + 1);
      try { fs.readSync(fd, buf, 0, buf.length, start); } finally { fs.closeSync(fd); }
      return { raw: { status: 206, headers: { ...base, 'Content-Range': `bytes ${start}-${end}/${size}`, 'Content-Length': String(buf.length) }, body: buf } };
    }
    return { raw: { status: 200, headers: { ...base, 'Content-Length': String(size) }, body: fs.readFileSync(file) } };
  });
  r.add('PATCH', '/v1/audio/:id', { auth: true }, async ({ params, body, user }) => {
    ownedAsset(params.id, user.id);
    const ms = V.num(body.durationMs, 'durationMs', 1, 86400000 * 2);
    db.prepare('UPDATE audio_assets SET duration_ms=? WHERE id=?').run(Math.round(ms), params.id);
    return { data: { updated: true } };
  });
  r.add('DELETE', '/v1/audio/:id', { auth: true }, async ({ params, user }) => {
    const a = ownedAsset(params.id, user.id);
    fs.rmSync(path.join(config.audioDir, user.id, a.file_name), { force: true });
    db.prepare('DELETE FROM audio_assets WHERE id=?').run(a.id);
    return { data: { deleted: true } };
  });
};
module.exports.periodStart = periodStart;

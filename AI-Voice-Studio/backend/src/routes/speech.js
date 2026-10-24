'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { ApiError, bad } = require('../http');
const V = require('../validate');
const S = require('../security');
const Providers = require('../providers');

const ID_RE = /^[A-Za-z0-9_-]{2,64}$/;
const MODEL_RE = /^[a-z0-9_.-]{3,64}$/;
const toSnake = (s) => ({ ...(s.stability !== undefined && { stability: s.stability }), ...(s.similarityBoost !== undefined && { similarity_boost: s.similarityBoost }), ...(s.style !== undefined && { style: s.style }), ...(s.speed !== undefined && { speed: s.speed }), ...(s.useSpeakerBoost !== undefined && { use_speaker_boost: s.useSpeakerBoost }) });
const LANG_RE = /^[a-z]{2,3}(-[A-Za-z]{2,4})?$/;

function periodStart(d = new Date()) { return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1); }
function periodEnd(d = new Date()) { return Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1); }

module.exports = function speechRoutes(r, { db, config }) {
  const limitFor = (user) => config.planLimits[user.plan] ?? config.planLimits.free;
  const usedThisMonth = (userId) =>
    db.prepare("SELECT COALESCE(SUM(chars),0) s FROM usage_records WHERE user_id=? AND kind='tts' AND created_at>=? AND created_at<?").get(userId, periodStart(), periodEnd()).s;

  r.add('GET', '/v1/providers', { auth: true }, async () => ({
    data: { providers: Providers.describe(config), defaultProvider: Providers.defaultProviderId(config) },
  }));

  r.add('GET', '/v1/voices', { auth: true }, async ({ query, signal }) => {
    const prov = Providers.resolveConfigured(config, query.get('provider'));
    const q = { pageSize: V.intParam(query.get('pageSize'), 30, 1, 100), pageToken: (query.get('pageToken') || '').slice(0, 500) };
    const search = query.get('search'); if (search) q.search = search.slice(0, 100);
    for (const k of ['category', 'sort', 'sort_direction', 'voice_type']) { const v = query.get(k); if (v && /^[A-Za-z0-9_-]{2,30}$/.test(v)) q[k] = v; }
    const out = await prov.listVoices(config, q, signal);
    return { data: { provider: prov.id, ...out } };
  });

  r.add('GET', '/v1/models', { auth: true }, async ({ query, signal }) => {
    const prov = Providers.resolveConfigured(config, query.get('provider'));
    const models = await prov.listModels(config, signal);
    return { data: { provider: prov.id, models, outputFormats: prov.formats, capabilities: prov.capabilities, maxTextChars: effectiveMax(prov) } };
  });

  const effectiveMax = (prov) => Math.min(config.maxTextChars, prov.maxChars || Infinity);

  r.add('POST', '/v1/tts', { auth: true, limit: 'tts' }, async ({ body, user, signal }) => {
    const prov = Providers.resolveConfigured(config, body.provider);
    const max = effectiveMax(prov);
    const text = V.str(body.text, 'text', { min: 1, max: max + 1, trim: false });
    if (!text.trim()) throw bad('empty_text', 'Enter some text to convert.', { field: 'text' });
    if (text.length > max || (prov.maxBytes && Buffer.byteLength(text) > prov.maxBytes))
      throw bad('text_too_long', `Text is limited to ${max} characters per request. Use Long Script to split longer text.`, { field: 'text', max });
    const voiceId = V.str(body.voiceId, 'voiceId', { max: 64 });
    if (!ID_RE.test(voiceId)) throw bad('invalid_field', 'voiceId is invalid.', { field: 'voiceId' });
    if (prov.isKnownVoice && !prov.isKnownVoice(voiceId)) throw bad('unknown_voice', `Unknown ${prov.label} voice.`, { field: 'voiceId' });
    if (prov.isValidVoiceName && !prov.isValidVoiceName(voiceId)) throw bad('invalid_field', `voiceId is not a valid ${prov.label} voice name.`, { field: 'voiceId' });
    const modelId = body.modelId === undefined ? prov.defaultModel : V.str(body.modelId, 'modelId', { max: 64 });
    if (!MODEL_RE.test(modelId)) throw bad('invalid_field', 'modelId is invalid.', { field: 'modelId' });
    const outputFormat = body.outputFormat === undefined ? prov.defaultFormat : body.outputFormat;
    if (typeof outputFormat !== 'string' || !prov.formats.includes(outputFormat))
      throw bad('unsupported_output_format', `Unsupported output format for ${prov.label}.`, { field: 'outputFormat', supported: prov.formats });
    let languageCode;
    if (body.languageCode !== undefined) {
      languageCode = V.str(body.languageCode, 'languageCode', { max: 12 });
      if (!LANG_RE.test(languageCode)) throw bad('invalid_field', 'languageCode is invalid.', { field: 'languageCode' });
    }
    const settings = {};
    const vs = body.voiceSettings;
    const caps = prov.capabilities;
    const unsupported = (name) => bad('unsupported_voice_setting', `${prov.label} does not support the "${name}" setting.`, { field: name, provider: prov.id });
    if (vs !== undefined) {
      if (vs === null || typeof vs !== 'object' || Array.isArray(vs)) throw bad('invalid_field', 'voiceSettings must be an object.', { field: 'voiceSettings' });
      if (vs.stability !== undefined) { settings.stability = V.num(vs.stability, 'stability', 0, 1); if (!caps.stability) throw unsupported('stability'); }
      if (vs.similarityBoost !== undefined) { settings.similarityBoost = V.num(vs.similarityBoost, 'similarityBoost', 0, 1); if (!caps.similarity) throw unsupported('similarityBoost'); }
      if (vs.style !== undefined) { settings.style = V.num(vs.style, 'style', 0, 1); if (!caps.style) throw unsupported('style'); }
      if (vs.speed !== undefined) { settings.speed = V.num(vs.speed, 'speed', caps.speedMin, caps.speedMax); if (!caps.speed) throw unsupported('speed'); }
      if (vs.useSpeakerBoost !== undefined) {
        if (typeof vs.useSpeakerBoost !== 'boolean') throw bad('invalid_field', 'useSpeakerBoost must be true or false.', { field: 'useSpeakerBoost' });
        if (!caps.speakerBoost) throw unsupported('useSpeakerBoost');
        settings.useSpeakerBoost = vs.useSpeakerBoost;
      }
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
      .run(genId, user.id, projectId, prov.id, modelId, voiceId, text.length, text.slice(0, 120), text, outputFormat,
        JSON.stringify({ ...toSnake(settings), languageCode: languageCode ?? null }), Date.now());
    const fail = (e) => {
      db.prepare("UPDATE generations SET status='failed', error_code=?, error_message=?, completed_at=? WHERE id=?").run(e.code || 'internal_error', String(e.message).slice(0, 300), Date.now(), genId);
      return e;
    };

    let out;
    try {
      out = await prov.synthesize(config, { text, voiceId, modelId, outputFormat, languageCode, voiceSettings: settings }, signal);
    } catch (e) { throw fail(e instanceof ApiError ? e : new ApiError(502, 'provider_error', 'Speech generation failed.')); }
    const audio = out.audio;
    if (!audio || !audio.length) throw fail(new ApiError(502, 'provider_empty_audio', 'The provider returned no audio.'));

    const assetId = S.newId();
    const fileName = `${assetId}.${out.ext}`;
    const dir = path.join(config.audioDir, user.id);
    try {
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, fileName), audio, { mode: 0o600 });
    } catch { throw fail(new ApiError(500, 'storage_failed', 'Could not store the generated audio on the server.')); }
    const t = Date.now();
    db.exec('BEGIN');
    try {
      db.prepare('INSERT INTO audio_assets (id,user_id,project_id,file_name,mime,bytes,created_at) VALUES (?,?,?,?,?,?,?)').run(assetId, user.id, projectId, fileName, out.mime, audio.length, t);
      db.prepare("UPDATE generations SET status='completed', audio_asset_id=?, completed_at=? WHERE id=?").run(assetId, t, genId);
      db.prepare("INSERT INTO usage_records (id,user_id,kind,chars,generation_id,created_at) VALUES (?,?,'tts',?,?,?)").run(S.newId(), user.id, text.length, genId, t);
      db.exec('COMMIT');
    } catch (e) { db.exec('ROLLBACK'); throw fail(new ApiError(500, 'database_failed', 'Could not record the generation.')); }

    return { raw: { status: 200, headers: {
      'Content-Type': out.mime, 'Content-Length': String(audio.length),
      'X-Generation-Id': genId, 'X-Audio-Asset-Id': assetId, 'X-Characters-Used': String(text.length),
      'X-Usage-Remaining': String(Math.max(0, limit - used - text.length)), 'X-TTS-Provider': prov.id,
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

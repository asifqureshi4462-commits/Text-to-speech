'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { ApiError, bad } = require('../http');
const V = require('../validate');
const S = require('../security');

const TYPES = ['tts', 'longform', 'dialogue', 'studio'];
const ID_RE = /^[A-Za-z0-9_-]{6,64}$/;
const like = (s) => `%${s.replace(/[\\%_]/g, (c) => '\\' + c)}%`;

module.exports = function contentRoutes(r, { db, config }) {
  const now = () => Date.now();
  const jsonField = (v, field) => {
    if (v === undefined) return undefined;
    if (v === null || typeof v !== 'object' || Array.isArray(v)) throw bad('invalid_field', `${field} must be an object.`, { field });
    return JSON.stringify(v);
  };
  const projectDto = (p, withContent) => ({
    id: p.id, name: p.name, type: p.type, isFavorite: !!p.is_favorite, isDraft: !!p.is_draft, createdAt: p.created_at, updatedAt: p.updated_at,
    ...(withContent ? { content: JSON.parse(p.content), settings: JSON.parse(p.settings) } : {}),
    ...(p.audio_count !== undefined ? { audioCount: p.audio_count } : {}),
  });
  const owned = (id, userId) => {
    const p = db.prepare('SELECT * FROM projects WHERE id=? AND user_id=?').get(id, userId);
    if (!p) throw new ApiError(404, 'not_found', 'Project not found.');
    return p;
  };

  // ---------- projects ----------
  r.add('GET', '/v1/projects', { auth: true }, async ({ query, user }) => {
    const where = ['p.user_id=?']; const args = [user.id];
    const q = query.get('q'); if (q) { where.push("p.name LIKE ? ESCAPE '\\'"); args.push(like(q.slice(0, 100))); }
    const type = query.get('type'); if (type) { if (!TYPES.includes(type)) throw bad('invalid_field', 'Unknown project type.'); where.push('p.type=?'); args.push(type); }
    if (query.get('favorite') === 'true') where.push('p.is_favorite=1');
    if (query.get('draft') === 'true') where.push('p.is_draft=1');
    const sort = { name: 'p.name COLLATE NOCASE ASC', created: 'p.created_at DESC' }[query.get('sort')] || 'p.updated_at DESC';
    const limit = V.intParam(query.get('limit'), 30, 1, 100), offset = V.intParam(query.get('offset'), 0, 0, 1e6);
    const rows = db.prepare(`SELECT p.*, (SELECT COUNT(*) FROM audio_assets a WHERE a.project_id=p.id) audio_count FROM projects p WHERE ${where.join(' AND ')} ORDER BY ${sort} LIMIT ? OFFSET ?`).all(...args, limit, offset);
    const total = db.prepare(`SELECT COUNT(*) c FROM projects p WHERE ${where.join(' AND ')}`).get(...args).c;
    return { data: { projects: rows.map((p) => projectDto(p, false)), total, limit, offset } };
  });
  r.add('POST', '/v1/projects', { auth: true }, async ({ body, user }) => {
    const name = V.str(body.name, 'name', { max: 120 });
    if (!TYPES.includes(body.type)) throw bad('invalid_field', `type must be one of ${TYPES.join(', ')}.`, { field: 'type' });
    const id = S.newId(), t = now();
    db.prepare('INSERT INTO projects (id,user_id,name,type,content,settings,is_draft,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)')
      .run(id, user.id, name, body.type, jsonField(body.content, 'content') ?? '{}', jsonField(body.settings, 'settings') ?? '{}', body.isDraft === false ? 0 : 1, t, t);
    return { status: 201, data: { project: projectDto(owned(id, user.id), true) } };
  });
  r.add('GET', '/v1/projects/:id', { auth: true }, async ({ params, user }) => ({ data: { project: projectDto(owned(params.id, user.id), true) } }));
  r.add('PATCH', '/v1/projects/:id', { auth: true }, async ({ params, body, user }) => {
    const p = owned(params.id, user.id);
    const name = body.name === undefined ? p.name : V.str(body.name, 'name', { max: 120 });
    const content = jsonField(body.content, 'content') ?? p.content;
    const settings = jsonField(body.settings, 'settings') ?? p.settings;
    const fav = body.isFavorite === undefined ? p.is_favorite : (typeof body.isFavorite === 'boolean' ? +body.isFavorite : (() => { throw bad('invalid_field', 'isFavorite must be boolean.'); })());
    const draft = body.isDraft === undefined ? p.is_draft : (typeof body.isDraft === 'boolean' ? +body.isDraft : (() => { throw bad('invalid_field', 'isDraft must be boolean.'); })());
    db.prepare('UPDATE projects SET name=?, content=?, settings=?, is_favorite=?, is_draft=?, updated_at=? WHERE id=?').run(name, content, settings, fav, draft, now(), p.id);
    return { data: { project: projectDto(owned(p.id, user.id), true) } };
  });
  r.add('POST', '/v1/projects/:id/duplicate', { auth: true }, async ({ params, user }) => {
    const p = owned(params.id, user.id); const id = S.newId(), t = now();
    db.prepare('INSERT INTO projects (id,user_id,name,type,content,settings,is_draft,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)')
      .run(id, user.id, `${p.name} (copy)`.slice(0, 120), p.type, p.content, p.settings, 1, t, t);
    return { status: 201, data: { project: projectDto(owned(id, user.id), true) } };
  });
  r.add('DELETE', '/v1/projects/:id', { auth: true }, async ({ params, user }) => {
    const p = owned(params.id, user.id);
    const assets = db.prepare('SELECT file_name FROM audio_assets WHERE project_id=?').all(p.id);
    for (const a of assets) fs.rmSync(path.join(config.audioDir, user.id, a.file_name), { force: true });
    db.prepare('DELETE FROM audio_assets WHERE project_id=?').run(p.id);
    db.prepare('DELETE FROM projects WHERE id=?').run(p.id);
    return { data: { deleted: true, audioFilesDeleted: assets.length } };
  });

  // ---------- generation history ----------
  const genDto = (g, withText) => ({
    id: g.id, projectId: g.project_id, provider: g.provider, model: g.model, voiceId: g.voice_id, charCount: g.char_count,
    textPreview: g.text_preview, ...(withText ? { text: g.text } : {}), outputFormat: g.output_format, status: g.status, errorCode: g.error_code, errorMessage: g.error_message,
    audioAssetId: g.audio_asset_id, durationMs: g.duration_ms ?? null, bytes: g.bytes ?? null, settings: JSON.parse(g.settings), createdAt: g.created_at, completedAt: g.completed_at,
  });
  r.add('GET', '/v1/generations', { auth: true }, async ({ query, user }) => {
    const where = ['g.user_id=?']; const args = [user.id];
    const q = query.get('q'); if (q) { where.push("g.text_preview LIKE ? ESCAPE '\\'"); args.push(like(q.slice(0, 100))); }
    const st = query.get('status'); if (st) { if (!['processing', 'completed', 'failed'].includes(st)) throw bad('invalid_field', 'Unknown status.'); where.push('g.status=?'); args.push(st); }
    const pid = query.get('projectId'); if (pid) { where.push('g.project_id=?'); args.push(pid); }
    const limit = V.intParam(query.get('limit'), 30, 1, 100), offset = V.intParam(query.get('offset'), 0, 0, 1e6);
    const base = `FROM generations g LEFT JOIN audio_assets a ON a.id=g.audio_asset_id WHERE ${where.join(' AND ')}`;
    const rows = db.prepare(`SELECT g.*, a.duration_ms, a.bytes ${base} ORDER BY g.created_at DESC LIMIT ? OFFSET ?`).all(...args, limit, offset);
    const total = db.prepare(`SELECT COUNT(*) c ${base}`).get(...args).c;
    return { data: { generations: rows.map(genDto), total, limit, offset } };
  });
  r.add('GET', '/v1/generations/:id', { auth: true }, async ({ params, user }) => {
    const g = db.prepare('SELECT g.*, a.duration_ms, a.bytes FROM generations g LEFT JOIN audio_assets a ON a.id=g.audio_asset_id WHERE g.id=? AND g.user_id=?').get(params.id, user.id);
    if (!g) throw new ApiError(404, 'not_found', 'Generation not found.');
    return { data: { generation: genDto(g, true) } };
  });
  r.add('DELETE', '/v1/generations/:id', { auth: true }, async ({ params, user }) => {
    const g = db.prepare('SELECT * FROM generations WHERE id=? AND user_id=?').get(params.id, user.id);
    if (!g) throw new ApiError(404, 'not_found', 'Generation not found.');
    if (g.audio_asset_id) {
      const a = db.prepare('SELECT file_name FROM audio_assets WHERE id=? AND user_id=?').get(g.audio_asset_id, user.id);
      if (a) fs.rmSync(path.join(config.audioDir, user.id, a.file_name), { force: true });
      db.prepare('DELETE FROM audio_assets WHERE id=?').run(g.audio_asset_id);
    }
    db.prepare('DELETE FROM generations WHERE id=?').run(g.id);
    return { data: { deleted: true } };
  });

  // ---------- favourite voices ----------
  r.add('GET', '/v1/favorites/voices', { auth: true }, async ({ user }) => ({
    data: { voices: db.prepare('SELECT voice_id, voice_name, created_at FROM favorite_voices WHERE user_id=? ORDER BY created_at DESC').all(user.id).map((v) => ({ voiceId: v.voice_id, name: v.voice_name, createdAt: v.created_at })) },
  }));
  r.add('PUT', '/v1/favorites/voices/:voiceId', { auth: true }, async ({ params, body, user }) => {
    if (!ID_RE.test(params.voiceId)) throw bad('invalid_field', 'voiceId is invalid.');
    const name = body.name === undefined ? null : V.str(body.name, 'name', { max: 120 });
    db.prepare('INSERT INTO favorite_voices (user_id,voice_id,voice_name,created_at) VALUES (?,?,?,?) ON CONFLICT(user_id,voice_id) DO UPDATE SET voice_name=COALESCE(excluded.voice_name, voice_name)')
      .run(user.id, params.voiceId, name, now());
    return { data: { favorite: true } };
  });
  r.add('DELETE', '/v1/favorites/voices/:voiceId', { auth: true }, async ({ params, user }) => {
    db.prepare('DELETE FROM favorite_voices WHERE user_id=? AND voice_id=?').run(user.id, params.voiceId);
    return { data: { favorite: false } };
  });
};

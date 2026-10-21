'use strict';
const { ApiError, bad } = require('../http');
const V = require('../validate');
const S = require('../security');
const P = require('../provider');
const { periodStart } = require('./speech');

module.exports = function adminRoutes(r, { db, config }) {
  const audit = (actor, action, target) =>
    db.prepare('INSERT INTO audit_log (id,actor_id,action,target,created_at) VALUES (?,?,?,?,?)').run(S.newId(), actor, action, target, Date.now());

  r.add('GET', '/v1/admin/stats', { auth: 'admin' }, async () => {
    const one = (sql, ...a) => db.prepare(sql).get(...a);
    const ps = periodStart();
    return { data: {
      users: one('SELECT COUNT(*) c FROM users').c,
      generations: Object.fromEntries(db.prepare('SELECT status, COUNT(*) c FROM generations GROUP BY status').all().map((x) => [x.status, x.c])),
      charactersThisMonth: one("SELECT COALESCE(SUM(chars),0) c FROM usage_records WHERE kind='tts' AND created_at>=?", ps).c,
      errorsByCode: db.prepare("SELECT error_code code, COUNT(*) c FROM generations WHERE status='failed' GROUP BY error_code ORDER BY c DESC LIMIT 20").all(),
      limits: config.planLimits,
    } };
  });
  r.add('GET', '/v1/admin/users', { auth: 'admin' }, async ({ query }) => {
    const limit = V.intParam(query.get('limit'), 50, 1, 200), offset = V.intParam(query.get('offset'), 0, 0, 1e6);
    const rows = db.prepare('SELECT id,email,name,role,plan,email_verified,disabled,created_at FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?').all(limit, offset);
    return { data: { users: rows.map((u) => ({ id: u.id, email: u.email, name: u.name, role: u.role, plan: u.plan, emailVerified: !!u.email_verified, disabled: !!u.disabled, createdAt: u.created_at })), total: db.prepare('SELECT COUNT(*) c FROM users').get().c } };
  });
  r.add('PATCH', '/v1/admin/users/:id', { auth: 'admin' }, async ({ params, body, user }) => {
    const target = db.prepare('SELECT * FROM users WHERE id=?').get(params.id);
    if (!target) throw new ApiError(404, 'not_found', 'User not found.');
    if (body.plan !== undefined) {
      const plan = V.str(body.plan, 'plan', { max: 40 });
      if (!(plan in config.planLimits)) throw bad('unknown_plan', 'Plan is not defined in server configuration.', { plans: Object.keys(config.planLimits) });
      db.prepare('UPDATE users SET plan=?, updated_at=? WHERE id=?').run(plan, Date.now(), target.id); audit(user.id, `user.plan=${plan}`, target.id);
    }
    if (body.disabled !== undefined) {
      if (typeof body.disabled !== 'boolean') throw bad('invalid_field', 'disabled must be boolean.');
      if (target.id === user.id) throw bad('cannot_disable_self', 'You cannot disable your own account.');
      db.prepare('UPDATE users SET disabled=?, updated_at=? WHERE id=?').run(+body.disabled, Date.now(), target.id); audit(user.id, `user.disabled=${body.disabled}`, target.id);
    }
    return { data: { updated: true } };
  });
  r.add('GET', '/v1/admin/audit', { auth: 'admin' }, async ({ query }) => ({
    data: { entries: db.prepare('SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?').all(V.intParam(query.get('limit'), 100, 1, 500)) },
  }));
  r.add('GET', '/v1/admin/provider', { auth: 'admin' }, async ({ signal }) => {
    if (!config.elevenKey) return { data: { configured: false, healthy: false, message: 'ELEVENLABS_API_KEY is not set.' } };
    try {
      const res = await P.providerFetch(config, '/v1/models', {}, signal);
      return { data: { configured: true, healthy: res.ok, httpStatus: res.status } };
    } catch (e) { return { data: { configured: true, healthy: false, error: e.code || 'unreachable' } }; }
  });
};

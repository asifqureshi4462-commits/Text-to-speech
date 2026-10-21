'use strict';
const http = require('node:http');
const fs = require('node:fs');
const { ApiError, readJson, send, sendJson, Router } = require('./http');
const { verifyJwt, RateLimiter } = require('./security');
const { openDatabase } = require('./db');

function createApp(config) {
  fs.mkdirSync(config.audioDir, { recursive: true });
  const db = openDatabase(config.dbPath);
  const router = new Router();
  const limiters = {
    global: new RateLimiter(config.globalRateLimitPerMin),
    auth: new RateLimiter(config.authRateLimitPerMin),
    tts: new RateLimiter(config.ttsRateLimitPerMin),
  };
  const deps = { db, config };
  require('./routes/auth')(router, deps);
  require('./routes/speech')(router, deps);
  require('./routes/content')(router, deps);
  require('./routes/admin')(router, deps);
  require('./routes/static')(router, deps);
  router.add('GET', '/healthz', {}, async () => ({ data: { status: 'ok', providerConfigured: !!config.elevenKey } }));

  const clientIp = (req) => (config.trustProxy && req.headers['x-forwarded-for'] ? String(req.headers['x-forwarded-for']).split(',')[0].trim() : req.socket.remoteAddress) || 'unknown';

  function authenticate(req) {
    const h = req.headers.authorization || '';
    const m = /^Bearer (.+)$/.exec(h);
    const p = m && verifyJwt(m[1], config.jwtSecret);
    if (!p) throw new ApiError(401, 'unauthenticated', 'Sign in required or session expired.');
    const user = db.prepare('SELECT * FROM users WHERE id=?').get(p.sub);
    if (!user || user.disabled) throw new ApiError(401, 'unauthenticated', 'Sign in required or session expired.');
    return user;
  }

  const server = http.createServer(async (req, res) => {
    const ac = new AbortController();
    res.on('close', () => { if (!res.writableFinished) ac.abort(); });
    try {
      const url = new URL(req.url, 'http://localhost');
      const ip = clientIp(req);
      const wait = limiters.global.check(ip);
      if (wait) throw new ApiError(429, 'rate_limited', 'Too many requests. Slow down.', { retryAfter: wait });
      const { route, params, pathMatched } = router.match(req.method, url.pathname);
      if (!route) throw new ApiError(pathMatched ? 405 : 404, pathMatched ? 'method_not_allowed' : 'not_found', pathMatched ? 'Method not allowed.' : 'Route not found.');
      const { opts } = route;
      if (opts.limit === 'auth') { const w = limiters.auth.check(`${ip}`); if (w) throw new ApiError(429, 'rate_limited', 'Too many attempts. Try again later.', { retryAfter: w }); }
      let user = null;
      if (opts.auth) {
        user = authenticate(req);
        if (opts.auth === 'admin' && user.role !== 'admin') throw new ApiError(403, 'forbidden', 'Administrator access required.');
      }
      if (opts.limit === 'tts') { const w = limiters.tts.check(user.id); if (w) throw new ApiError(429, 'rate_limited', 'Too many generation requests. Try again shortly.', { retryAfter: w }); }
      const body = await readJson(req, config.maxBodyBytes);
      const out = await route.handler({ req, body, params, query: url.searchParams, user, ip, signal: ac.signal });
      if (out.raw) send(res, out.raw.status, out.raw.headers, out.raw.body);
      else sendJson(res, out.status || 200, { ok: true, data: out.data });
    } catch (e) {
      if (e instanceof ApiError) {
        const headers = e.extra && e.extra.retryAfter ? { 'Retry-After': String(e.extra.retryAfter) } : {};
        sendJson(res, e.status === 499 ? 400 : e.status, { ok: false, error: { code: e.code, message: e.message, ...(e.extra ? { details: e.extra } : {}) } }, headers);
      } else {
        console.error('[internal-error]', e && e.stack ? e.stack.split('\n').slice(0, 4).join(' | ') : 'unknown'); // never logs request bodies/tokens
        sendJson(res, 500, { ok: false, error: { code: 'internal_error', message: 'Something went wrong on the server.' } });
      }
    }
  });
  return { server, db, close: () => new Promise((r) => { server.close(() => { db.close(); r(); }); server.closeAllConnections?.(); }) };
}

module.exports = { createApp };

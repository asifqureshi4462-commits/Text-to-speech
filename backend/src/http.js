'use strict';

class ApiError extends Error {
  constructor(status, code, message, extra) { super(message); this.status = status; this.code = code; this.extra = extra; }
}
const bad = (code, msg, extra) => new ApiError(400, code, msg, extra);

function readJson(req, maxBytes) {
  return new Promise((resolve, reject) => {
    if (['GET', 'HEAD'].includes(req.method)) return resolve({});
    const chunks = []; let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > maxBytes) { reject(new ApiError(413, 'payload_too_large', 'Request body is too large.')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        const v = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        if (v === null || typeof v !== 'object' || Array.isArray(v)) throw new Error('not object');
        resolve(v);
      } catch { reject(bad('invalid_json', 'Request body must be a JSON object.')); }
    });
    req.on('error', reject);
  });
}

function send(res, status, headers, body) {
  if (res.headersSent || res.destroyed) return;
  res.writeHead(status, {
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': 'no-store',
    'Referrer-Policy': 'no-referrer',
    ...headers,
  });
  res.end(body);
}
const sendJson = (res, status, obj, headers = {}) =>
  send(res, status, { 'Content-Type': 'application/json; charset=utf-8', ...headers }, JSON.stringify(obj));

class Router {
  constructor() { this.routes = []; }
  add(method, pattern, opts, handler) {
    const keys = [];
    const re = new RegExp('^' + pattern.replace(/:([A-Za-z]+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '/?$');
    this.routes.push({ method, re, keys, opts, handler });
  }
  match(method, path) {
    let pathMatched = false;
    for (const r of this.routes) {
      const m = r.re.exec(path);
      if (!m) continue;
      pathMatched = true;
      if (r.method !== method) continue;
      const params = {};
      r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
      return { route: r, params };
    }
    return { route: null, pathMatched };
  }
}

module.exports = { ApiError, bad, readJson, send, sendJson, Router };

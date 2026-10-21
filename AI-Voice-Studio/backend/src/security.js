'use strict';
const crypto = require('node:crypto');

const b64u = (b) => Buffer.from(b).toString('base64url');
const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const randomToken = (bytes = 32) => crypto.randomBytes(bytes).toString('base64url');
const newId = () => crypto.randomUUID();

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, 64);
  return `scrypt$${salt.toString('base64')}$${hash.toString('base64')}`;
}

function verifyPassword(password, stored) {
  const [alg, saltB64, hashB64] = String(stored).split('$');
  if (alg !== 'scrypt' || !saltB64 || !hashB64) return false;
  const expected = Buffer.from(hashB64, 'base64');
  const actual = crypto.scryptSync(password, Buffer.from(saltB64, 'base64'), expected.length);
  return crypto.timingSafeEqual(expected, actual);
}

// Burn comparable CPU time when the account does not exist (limits user enumeration by timing).
const DUMMY_HASH = hashPassword('dummy-password-for-timing');

function signJwt(payload, secret, ttlSec) {
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64u(JSON.stringify({ ...payload, iat: now, exp: now + ttlSec }));
  const sig = crypto.createHmac('sha256', secret).update(`${head}.${body}`).digest('base64url');
  return `${head}.${body}.${sig}`;
}

function verifyJwt(token, secret) {
  const parts = String(token).split('.');
  if (parts.length !== 3) return null;
  const expected = crypto.createHmac('sha256', secret).update(`${parts[0]}.${parts[1]}`).digest();
  let given;
  try { given = Buffer.from(parts[2], 'base64url'); } catch { return null; }
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return null;
  try {
    const header = JSON.parse(Buffer.from(parts[0], 'base64url').toString());
    if (header.alg !== 'HS256') return null;
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString());
    if (typeof payload.exp !== 'number' || payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch { return null; }
}

class RateLimiter {
  constructor(max, windowMs = 60000) {
    this.max = max; this.windowMs = windowMs; this.hits = new Map();
    this.timer = setInterval(() => this.sweep(), windowMs).unref();
  }
  /** returns 0 if allowed, otherwise seconds until retry */
  check(key) {
    const now = Date.now();
    const arr = (this.hits.get(key) || []).filter((t) => now - t < this.windowMs);
    if (arr.length >= this.max) {
      this.hits.set(key, arr);
      return Math.max(1, Math.ceil((arr[0] + this.windowMs - now) / 1000));
    }
    arr.push(now); this.hits.set(key, arr);
    return 0;
  }
  sweep() {
    const now = Date.now();
    for (const [k, v] of this.hits) {
      const f = v.filter((t) => now - t < this.windowMs);
      if (f.length) this.hits.set(k, f); else this.hits.delete(k);
    }
  }
}

module.exports = { sha256, randomToken, newId, hashPassword, verifyPassword, DUMMY_HASH, signJwt, verifyJwt, RateLimiter };

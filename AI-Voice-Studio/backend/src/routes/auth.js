'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { ApiError, bad } = require('../http');
const V = require('../validate');
const S = require('../security');
const { sendMail } = require('../mailer');

const TERMS_VERSION = '1';

module.exports = function authRoutes(r, { db, config }) {
  const now = () => Date.now();

  function publicUser(u) {
    return { id: u.id, name: u.name, email: u.email, role: u.role, plan: u.plan, emailVerified: !!u.email_verified, createdAt: u.created_at };
  }

  function issueSession(user) {
    const access = S.signJwt({ sub: user.id }, config.jwtSecret, config.accessTtlSec);
    const refresh = S.randomToken(48);
    db.prepare('INSERT INTO tokens (id,user_id,kind,token_hash,expires_at,created_at) VALUES (?,?,?,?,?,?)')
      .run(S.newId(), user.id, 'refresh', S.sha256(refresh), now() + config.refreshTtlSec * 1000, now());
    return { accessToken: access, refreshToken: refresh, expiresIn: config.accessTtlSec, user: publicUser(user) };
  }

  function createOneTimeToken(userId, kind, ttlMs) {
    db.prepare("UPDATE tokens SET used_at=? WHERE user_id=? AND kind=? AND used_at IS NULL").run(now(), userId, kind);
    const raw = S.randomToken(32);
    db.prepare('INSERT INTO tokens (id,user_id,kind,token_hash,expires_at,created_at) VALUES (?,?,?,?,?,?)')
      .run(S.newId(), userId, kind, S.sha256(raw), now() + ttlMs, now());
    return raw;
  }

  function consumeToken(raw, kind) {
    if (typeof raw !== 'string' || raw.length < 20 || raw.length > 200) return null;
    const row = db.prepare('SELECT * FROM tokens WHERE token_hash=? AND kind=?').get(S.sha256(raw), kind);
    if (!row || row.used_at || row.expires_at < now()) return null;
    db.prepare('UPDATE tokens SET used_at=? WHERE id=?').run(now(), row.id);
    return row;
  }

  async function sendVerification(user) {
    const token = createOneTimeToken(user.id, 'verify', 24 * 3600 * 1000);
    const link = config.publicBaseUrl ? `${config.publicBaseUrl}/verify-email?token=${token}` : null;
    const text = link
      ? `Hi ${user.name},\n\nConfirm your email for AI Voice Studio:\n${link}\n\nThis link expires in 24 hours. If you did not sign up, ignore this message.`
      : `Hi ${user.name},\n\nYour AI Voice Studio verification code: ${token}\n\nIt expires in 24 hours.`;
    return sendMail(config, { to: user.email, subject: 'Verify your AI Voice Studio email', text });
  }

  r.add('POST', '/v1/auth/signup', { limit: 'auth' }, async ({ body }) => {
    const name = V.str(body.name, 'name', { max: 80 });
    const email = V.email(body.email);
    const pw = V.password(body.password);
    if (body.acceptTerms !== true) throw bad('terms_required', 'You must accept the Terms and Privacy Policy.', { field: 'acceptTerms' });
    if (db.prepare('SELECT 1 FROM users WHERE email=?').get(email)) throw new ApiError(409, 'email_taken', 'An account with this email already exists.');
    const id = S.newId();
    const verified = config.requireEmailVerification ? 0 : 1;
    try {
      db.prepare('INSERT INTO users (id,email,name,password_hash,email_verified,created_at,updated_at) VALUES (?,?,?,?,?,?,?)')
        .run(id, email, name, S.hashPassword(pw), verified, now(), now());
    } catch (e) {
      if (/UNIQUE/.test(e.message)) throw new ApiError(409, 'email_taken', 'An account with this email already exists.');
      throw e;
    }
    db.prepare('INSERT INTO consent_records (id,user_id,kind,version,created_at) VALUES (?,?,?,?,?)').run(S.newId(), id, 'terms_and_privacy', TERMS_VERSION, now());
    const user = db.prepare('SELECT * FROM users WHERE id=?').get(id);
    if (!config.requireEmailVerification) return { status: 201, data: { verification: { required: false }, session: issueSession(user) } };
    const mail = await sendVerification(user);
    return { status: 201, data: { verification: { required: true, emailDelivered: mail.delivered, reason: mail.reason }, user: publicUser(user) } };
  });

  r.add('POST', '/v1/auth/login', { limit: 'auth' }, async ({ body }) => {
    const email = V.email(body.email);
    const pw = V.str(body.password, 'password', { max: 128, trim: false });
    const user = db.prepare('SELECT * FROM users WHERE email=?').get(email);
    const ok = S.verifyPassword(pw, user ? user.password_hash : S.DUMMY_HASH);
    if (!user || !ok) throw new ApiError(401, 'invalid_credentials', 'Incorrect email or password.');
    if (user.disabled) throw new ApiError(403, 'account_disabled', 'This account has been disabled.');
    if (config.requireEmailVerification && !user.email_verified) throw new ApiError(403, 'email_not_verified', 'Verify your email address before signing in.');
    return { data: issueSession(user) };
  });

  r.add('POST', '/v1/auth/refresh', { limit: 'auth' }, async ({ body }) => {
    const raw = V.str(body.refreshToken, 'refreshToken', { max: 200 });
    const row = db.prepare("SELECT * FROM tokens WHERE token_hash=? AND kind='refresh'").get(S.sha256(raw));
    if (!row) throw new ApiError(401, 'session_expired', 'Session is no longer valid. Please sign in again.');
    if (row.used_at) {
      // Reuse of a rotated refresh token: assume theft, revoke every session for this user.
      db.prepare("UPDATE tokens SET used_at=? WHERE user_id=? AND kind='refresh' AND used_at IS NULL").run(now(), row.user_id);
      throw new ApiError(401, 'session_expired', 'Session is no longer valid. Please sign in again.');
    }
    if (row.expires_at < now()) throw new ApiError(401, 'session_expired', 'Session expired. Please sign in again.');
    const user = db.prepare('SELECT * FROM users WHERE id=?').get(row.user_id);
    if (!user || user.disabled) throw new ApiError(401, 'session_expired', 'Session is no longer valid.');
    db.prepare('UPDATE tokens SET used_at=? WHERE id=?').run(now(), row.id);
    return { data: issueSession(user) };
  });

  r.add('POST', '/v1/auth/logout', { limit: 'auth' }, async ({ body }) => {
    if (typeof body.refreshToken === 'string')
      db.prepare("UPDATE tokens SET used_at=? WHERE token_hash=? AND kind='refresh' AND used_at IS NULL").run(now(), S.sha256(body.refreshToken));
    return { data: { loggedOut: true } };
  });

  r.add('POST', '/v1/auth/forgot-password', { limit: 'auth' }, async ({ body }) => {
    const email = V.email(body.email);
    const user = db.prepare('SELECT * FROM users WHERE email=?').get(email);
    if (user && !user.disabled) {
      const token = createOneTimeToken(user.id, 'reset', 3600 * 1000);
      const m = await sendMail(config, {
        to: user.email, subject: 'Reset your AI Voice Studio password',
        text: `Use this code in the app to reset your password (valid for 1 hour):\n\n${token}\n\nIf you did not request this, ignore this message.`,
      });
      void m; // delivery result intentionally not revealed (no account enumeration)
    }
    // Same response whether or not the account exists (no account enumeration).
    return { data: { requested: true, mailConfigured: !!config.mailWebhookUrl } };
  });

  r.add('POST', '/v1/auth/reset-password', { limit: 'auth' }, async ({ body }) => {
    const pw = V.password(body.password);
    const row = consumeToken(body.token, 'reset');
    if (!row) throw new ApiError(400, 'invalid_or_expired_token', 'This reset code is invalid or has expired.');
    db.prepare('UPDATE users SET password_hash=?, updated_at=? WHERE id=?').run(S.hashPassword(pw), now(), row.user_id);
    db.prepare("UPDATE tokens SET used_at=? WHERE user_id=? AND kind='refresh' AND used_at IS NULL").run(now(), row.user_id);
    return { data: { reset: true } };
  });

  const verifyNow = (raw) => {
    const row = consumeToken(raw, 'verify');
    if (!row) return false;
    db.prepare('UPDATE users SET email_verified=1, updated_at=? WHERE id=?').run(now(), row.user_id);
    return true;
  };
  r.add('POST', '/v1/auth/verify-email', { limit: 'auth' }, async ({ body }) => {
    if (!verifyNow(body.token)) throw new ApiError(400, 'invalid_or_expired_token', 'This verification link is invalid or has expired.');
    return { data: { verified: true } };
  });
  r.add('GET', '/verify-email', { limit: 'auth', html: true }, async ({ query }) => {
    const ok = verifyNow(query.get('token'));
    const msg = ok ? 'Your email is verified. You can return to the AI Voice Studio app and sign in.'
      : 'This verification link is invalid or has expired. Request a new one from the app.';
    return { raw: { status: ok ? 200 : 400, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'" },
      body: Buffer.from(`<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>AI Voice Studio</title><body style="font-family:sans-serif;background:#0b0d12;color:#eaf2ff;display:grid;place-items:center;min-height:100vh;margin:0"><p style="max-width:28em;padding:24px;font-size:18px">${msg}</p>`) } };
  });
  r.add('POST', '/v1/auth/resend-verification', { limit: 'auth' }, async ({ body }) => {
    const email = V.email(body.email);
    const user = db.prepare('SELECT * FROM users WHERE email=?').get(email);
    if (user && !user.email_verified && !user.disabled) await sendVerification(user);
    return { data: { requested: true, mailConfigured: !!config.mailWebhookUrl } };
  });

  r.add('POST', '/v1/auth/change-password', { auth: true, limit: 'auth' }, async ({ body, user }) => {
    const cur = V.str(body.currentPassword, 'currentPassword', { max: 128, trim: false });
    const next = V.password(body.newPassword, 'newPassword');
    if (!S.verifyPassword(cur, user.password_hash)) throw new ApiError(403, 'invalid_credentials', 'Current password is incorrect.');
    db.prepare('UPDATE users SET password_hash=?, updated_at=? WHERE id=?').run(S.hashPassword(next), now(), user.id);
    db.prepare("UPDATE tokens SET used_at=? WHERE user_id=? AND kind='refresh' AND used_at IS NULL").run(now(), user.id);
    return { data: { changed: true, signOutRequired: true } };
  });

  r.add('GET', '/v1/me', { auth: true }, async ({ user }) => {
    const c = (sql) => db.prepare(sql).get(user.id).c;
    return { data: { user: publicUser(user),
      counts: { projects: c('SELECT COUNT(*) c FROM projects WHERE user_id=?'), favoriteVoices: c('SELECT COUNT(*) c FROM favorite_voices WHERE user_id=?'), generations: c('SELECT COUNT(*) c FROM generations WHERE user_id=?') } } };
  });
  r.add('PATCH', '/v1/me', { auth: true }, async ({ body, user }) => {
    const name = V.str(body.name, 'name', { max: 80 });
    db.prepare('UPDATE users SET name=?, updated_at=? WHERE id=?').run(name, now(), user.id);
    return { data: { user: publicUser({ ...user, name }) } };
  });

  r.add('DELETE', '/v1/account', { auth: true, limit: 'auth' }, async ({ body, user }) => {
    const pw = V.str(body.password, 'password', { max: 128, trim: false });
    if (!S.verifyPassword(pw, user.password_hash)) throw new ApiError(403, 'invalid_credentials', 'Password is incorrect.');
    fs.rmSync(path.join(config.audioDir, user.id), { recursive: true, force: true });
    db.prepare('DELETE FROM users WHERE id=?').run(user.id); // cascades to every user-owned table
    db.prepare('INSERT INTO audit_log (id,actor_id,action,target,created_at) VALUES (?,?,?,?,?)').run(S.newId(), user.id, 'account.deleted', user.id, now());
    return { data: { deleted: true, note: 'Account, projects, history, favourites and stored audio were deleted. Audit records keep only an anonymous user ID.' } };
  });
};

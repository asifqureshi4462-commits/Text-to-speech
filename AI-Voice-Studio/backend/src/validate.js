'use strict';
const { bad } = require('./http');

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;

function str(v, field, { min = 1, max = 255, trim = true } = {}) {
  if (typeof v !== 'string') throw bad('invalid_field', `${field} must be a string.`, { field });
  const s = trim ? v.trim() : v;
  if (s.length < min || s.length > max) throw bad('invalid_field', `${field} must be ${min}-${max} characters.`, { field });
  return s;
}
function email(v) {
  const s = str(v, 'email', { max: 320 }).toLowerCase();
  if (!EMAIL_RE.test(s)) throw bad('invalid_email', 'Enter a valid email address.', { field: 'email' });
  return s;
}
function password(v, field = 'password') {
  const s = str(v, field, { min: 1, max: 128, trim: false });
  if (s.length < 8 || !/[A-Za-z]/.test(s) || !/[0-9]/.test(s))
    throw bad('weak_password', 'Password must be at least 8 characters and include a letter and a number.', { field });
  return s;
}
function num(v, field, min, max) {
  if (typeof v !== 'number' || !Number.isFinite(v) || v < min || v > max)
    throw bad('invalid_field', `${field} must be a number between ${min} and ${max}.`, { field });
  return v;
}
function intParam(v, def, min, max) {
  const n = v === null || v === undefined || v === '' ? def : Number(v);
  if (!Number.isInteger(n)) return def;
  return Math.min(max, Math.max(min, n));
}
module.exports = { str, email, password, num, intParam };

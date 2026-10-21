'use strict';
// Usage: node scripts/make-admin.js user@example.com   (run on the server; needs the same env as the backend)
const { load } = require('../src/config');
const { openDatabase } = require('../src/db');
const email = (process.argv[2] || '').trim().toLowerCase();
if (!email) { console.error('Usage: make-admin.js <email>'); process.exit(1); }
const db = openDatabase(load().dbPath);
const r = db.prepare("UPDATE users SET role='admin' WHERE email=?").run(email);
console.log(r.changes ? `${email} is now an administrator.` : 'No user with that email.');
process.exit(r.changes ? 0 : 2);

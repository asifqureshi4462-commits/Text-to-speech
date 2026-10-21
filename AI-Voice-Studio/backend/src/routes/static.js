'use strict';
const fs = require('node:fs');
const path = require('node:path');

const PUBLIC = path.join(__dirname, '..', '..', 'public');
const CSP = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

/** Serves the admin dashboard (static files; every data call it makes is authorised server-side). */
module.exports = function staticRoutes(r) {
  const files = {
    '/admin': ['admin.html', 'text/html; charset=utf-8'],
    '/admin/app.js': ['admin.js', 'text/javascript; charset=utf-8'],
    '/admin/style.css': ['admin.css', 'text/css; charset=utf-8'],
  };
  for (const [route, [file, type]] of Object.entries(files)) {
    r.add('GET', route, {}, async () => ({
      raw: { status: 200, headers: { 'Content-Type': type, 'Content-Security-Policy': CSP, 'X-Frame-Options': 'DENY', 'Cache-Control': 'no-cache' }, body: fs.readFileSync(path.join(PUBLIC, file)) },
    }));
  }
};

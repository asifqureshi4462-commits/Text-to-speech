'use strict';
const fs = require('node:fs');

/** Minimal .env parser (KEY=value, optional quotes, # comments). Real environment variables always win. */
function parse(text) {
  const out = {};
  for (const line of String(text).split(/\r?\n/)) {
    if (/^\s*(#|$)/.test(line)) continue;
    const m = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line);
    if (!m) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"') && v.length >= 2) || (v.startsWith("'") && v.endsWith("'") && v.length >= 2)) v = v.slice(1, -1);
    else v = v.replace(/\s+#.*$/, '');
    out[m[1]] = v;
  }
  return out;
}

function loadEnvFile(file, env = process.env) {
  if (!fs.existsSync(file)) return false;
  for (const [k, v] of Object.entries(parse(fs.readFileSync(file, 'utf8')))) if (env[k] === undefined) env[k] = v;
  return true;
}

module.exports = { parse, loadEnvFile };

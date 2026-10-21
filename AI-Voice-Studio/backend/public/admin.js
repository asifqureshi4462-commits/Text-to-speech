'use strict';
// Admin dashboard client. All privileges are enforced by the server; this page only calls the /v1/admin APIs.
(() => {
  const $ = (id) => document.getElementById(id);
  let token = null;

  async function api(method, path, body) {
    const res = await fetch(path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
    const json = await res.json().catch(() => ({}));
    if (!res.ok || !json.ok) throw new Error((json.error && json.error.message) || 'Request failed (' + res.status + ')');
    return json.data;
  }
  function cell(text) { const td = document.createElement('td'); td.textContent = text == null ? '' : String(text); return td; }
  function fill(tableId, rows) {
    const tb = $(tableId).querySelector('tbody'); tb.replaceChildren();
    for (const r of rows) { const tr = document.createElement('tr'); for (const c of r) tr.appendChild(c instanceof Node ? c : cell(c)); tb.appendChild(tr); }
  }

  async function load() {
    $('dashError').textContent = '';
    try {
      const [stats, users, audit, provider] = await Promise.all([api('GET', '/v1/admin/stats'), api('GET', '/v1/admin/users?limit=100'), api('GET', '/v1/admin/audit?limit=50'), api('GET', '/v1/admin/provider')]);
      $('statUsers').textContent = stats.users;
      $('statChars').textContent = stats.charactersThisMonth.toLocaleString();
      $('statGens').textContent = Object.entries(stats.generations).map(([k, v]) => k + ': ' + v).join(' · ') || 'none yet';
      $('statProvider').textContent = !provider.configured ? 'Not configured' : provider.healthy ? 'Healthy' : 'Unhealthy' + (provider.httpStatus ? ' (HTTP ' + provider.httpStatus + ')' : '');
      fill('errors', stats.errorsByCode.map((e) => [e.code || '(unknown)', e.c]));
      const plans = Object.keys(stats.limits);
      fill('users', users.users.map((u) => {
        const sel = document.createElement('select');
        for (const p of plans) { const o = document.createElement('option'); o.value = p; o.textContent = p; o.selected = p === u.plan; sel.appendChild(o); }
        sel.addEventListener('change', () => act(u.id, { plan: sel.value }));
        const tdPlan = document.createElement('td'); tdPlan.appendChild(sel);
        const btn = document.createElement('button'); btn.textContent = u.disabled ? 'Enable' : 'Disable';
        btn.addEventListener('click', () => act(u.id, { disabled: !u.disabled }));
        const tdAct = document.createElement('td'); tdAct.appendChild(btn);
        return [u.email, u.name, u.role, tdPlan, u.emailVerified ? 'yes' : 'no', u.disabled ? 'yes' : 'no', tdAct];
      }));
      fill('audit', audit.entries.map((a) => [new Date(a.created_at).toLocaleString(), a.actor_id, a.action, a.target]));
    } catch (e) { $('dashError').textContent = e.message; }
  }
  async function act(id, patch) { try { await api('PATCH', '/v1/admin/users/' + encodeURIComponent(id), patch); } catch (e) { $('dashError').textContent = e.message; } load(); }

  $('loginForm').addEventListener('submit', async (ev) => {
    ev.preventDefault(); $('loginError').textContent = '';
    try {
      const s = await api('POST', '/v1/auth/login', { email: $('email').value, password: $('password').value });
      if (!s.user || s.user.role !== 'admin') { await api('POST', '/v1/auth/logout', { refreshToken: s.refreshToken }).catch(() => {}); throw new Error('This account is not an administrator.'); }
      token = s.accessToken; $('password').value = '';
      $('login').hidden = true; $('dash').hidden = false; $('logout').hidden = false;
      load();
    } catch (e) { $('loginError').textContent = e.message; }
  });
  $('logout').addEventListener('click', () => { token = null; $('dash').hidden = true; $('login').hidden = false; $('logout').hidden = true; });
})();

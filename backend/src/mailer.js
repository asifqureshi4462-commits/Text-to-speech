'use strict';
/**
 * Mail delivery goes through an HTTPS webhook you control (e.g. a small function in front of your
 * mail provider). The backend POSTs {to, subject, text} with an optional Bearer secret.
 * If MAIL_WEBHOOK_URL is not configured nothing is sent, and callers are told so honestly.
 */
async function sendMail(config, { to, subject, text }) {
  if (!config.mailWebhookUrl) return { delivered: false, reason: 'mail_not_configured' };
  try {
    const res = await fetch(config.mailWebhookUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(config.mailWebhookSecret ? { Authorization: `Bearer ${config.mailWebhookSecret}` } : {}),
      },
      body: JSON.stringify({ to, subject, text }),
      signal: AbortSignal.timeout(10000),
    });
    return res.ok ? { delivered: true } : { delivered: false, reason: `webhook_status_${res.status}` };
  } catch {
    return { delivered: false, reason: 'webhook_unreachable' };
  }
}
module.exports = { sendMail };

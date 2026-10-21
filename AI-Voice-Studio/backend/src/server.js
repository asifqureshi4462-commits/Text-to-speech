'use strict';
const { load, validate } = require('./config');
const { createApp } = require('./app');

try {
  const config = validate(load());
  const app = createApp(config);
  app.server.listen(config.port, () => {
    console.log(`AI Voice Studio backend listening on :${config.port} (provider ${config.elevenKey ? 'configured' : 'NOT configured'}, mail ${config.mailWebhookUrl ? 'configured' : 'NOT configured'})`);
    console.log('Serve this behind HTTPS (reverse proxy / platform TLS). Never expose plain HTTP publicly.');
  });
  for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => app.close().then(() => process.exit(0)));
} catch (e) { console.error('Startup failed:', e.message); process.exit(1); }

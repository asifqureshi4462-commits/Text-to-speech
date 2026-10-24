'use strict';
/**
 * Checks which TTS providers are configured in backend/.env (or the environment) and whether each one
 * accepts your key. Keys are never printed.
 *
 *   npm run check-providers                 -> configuration + reachability/credential check (no cost)
 *   npm run check-providers -- --synthesize -> also generates ~5 characters of real audio per provider
 *                                              (uses a tiny amount of your provider quota)
 */
const path = require('node:path');
require('../src/dotenv').loadEnvFile(process.env.ENV_FILE || path.join(__dirname, '..', '.env'));
const { load } = require('../src/config');
const Providers = require('../src/providers');

const SAMPLE_VOICE = { openai: 'alloy', google: 'en-US-Neural2-A' };

async function main() {
  const config = load();
  const synth = process.argv.includes('--synthesize');
  const def = Providers.defaultProviderId(config);
  console.log(`Default provider: ${def}${config.defaultProvider ? ' (set by TTS_PROVIDER)' : ' (first configured)'}\n`);
  let anyConfigured = false, anyFailed = false;
  for (const p of Providers.ALL) {
    const label = p.label.padEnd(28);
    if (!p.isConfigured(config)) { console.log(`- ${label} NOT CONFIGURED  (set ${p.envVar} in backend/.env to enable)`); continue; }
    anyConfigured = true;
    try {
      const h = await p.health(config);
      if (!h.healthy) {
        anyFailed = true;
        const why = h.httpStatus === 401 || h.httpStatus === 403 ? 'the provider rejected the key (invalid, revoked, or the API is not enabled for it)' : `HTTP ${h.httpStatus}`;
        console.log(`- ${label} CONFIGURED but FAILED: ${why}`);
        continue;
      }
      let line = `- ${label} OK (key accepted)`;
      if (synth) {
        let voiceId = SAMPLE_VOICE[p.id];
        if (!voiceId) { const v = await p.listVoices(config, { pageSize: 1 }); voiceId = v.voices[0] && v.voices[0].voiceId; }
        const out = await p.synthesize(config, { text: 'Test.', voiceId, modelId: p.defaultModel, outputFormat: p.defaultFormat, voiceSettings: {} });
        line += ` · synthesized ${out.audio.length} bytes of ${out.mime} with voice "${voiceId}"`;
      }
      console.log(line);
    } catch (e) {
      anyFailed = true;
      console.log(`- ${label} CONFIGURED but FAILED: ${e.code || 'error'} - ${e.message}`);
    }
  }
  if (!anyConfigured) console.log('\nNo provider is configured yet. Copy .env.example to backend/.env and fill in at least one API key.');
  process.exit(anyConfigured && !anyFailed ? 0 : 1);
}
main();

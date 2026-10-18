// Capture the same real RC input document on a legacy TS player and on the input-capable player.
// Set RC_PLAYER_BASELINE_JS, RC_PLAYER_JS and PLAYWRIGHT_MODULE. No real HA connection/actions.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE);
const browser = await chromium.launch({ headless: true });
const output = `${root}renders/rc-input-message`;
mkdirSync(output, { recursive: true });
const html = '<meta charset="utf-8"><style>body{font:16px system-ui;background:#f4f4f4;margin:24px}#card{position:relative;width:440px;border:1px solid #ddd;border-radius:16px;background:white;padding:12px}canvas{display:block}h2{font-size:18px}</style><h2>Send to a room</h2><div id="card"><canvas width="440" height="440"></canvas><p id="fallback"></p></div>';
let fixture;
try {
  for (const lane of ['after', 'before']) {
    const page = await browser.newPage({ viewport: { width: 640, height: 900 } });
    await page.setContent(html);
    await page.addScriptTag({ content: readFileSync(lane === 'after' ? process.env.RC_PLAYER_JS : process.env.RC_PLAYER_BASELINE_JS, 'utf8') });
    if (!fixture) {
      fixture = await page.evaluate(() => Array.from(new Uint8Array(RC.inputDocument([
        { key: 'recipient_type', label: 'Recipient type', value: 'room', options: ['room', 'contact'] },
        { key: 'channel', label: 'Channel', value: 'matrix', options: ['matrix', 'telegram'] },
        { key: 'room', label: 'Contact / Room', value: 'Kitchen', options: ['Kitchen', 'Living room'] },
        { key: 'message', label: 'Message', value: 'Dinner is ready', multiline: true, submit: 'fixture:send' },
      ], 440))));
      writeFileSync(`${output}/message-inputs.rc`, Buffer.from(fixture));
    }
    await page.evaluate(async bytes => {
      window.player = new RC.RcdPlayer(document.querySelector('canvas'));
      if (player.configureInputs) player.configureInputs({ editable: true });
      else document.querySelector('#fallback').textContent = 'Editing is unavailable in this player. Open Home Assistant to send a message.';
      await player.loadFromArrayBuffer(new Uint8Array(bytes).buffer);
    }, fixture);
    await page.screenshot({ path: `${output}/${lane}.png` });
    await page.close();
  }
} finally { await browser.close(); }

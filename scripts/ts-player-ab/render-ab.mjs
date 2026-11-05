// A/B render of .rc documents through two TypeScript player bundles in headless Chromium.
// usage: [W=454 H=400] [ALL=1] node render-ab.mjs OLD_BUNDLE NEW_BUNDLE OUTDIR FILE.rc...
// Writes OUTDIR/results.json and, for documents differing by >0.5% (or all with ALL=1),
// OUTDIR/<file>.old.png and OUTDIR/<file>.new.png.
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { basename } from 'node:path';

const [oldB, newB, outDir, ...files] = process.argv.slice(2);
const W = +(process.env.W || 400), H = +(process.env.H || 400);
mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ headless: true, args: ['--enable-unsafe-swiftshader'] });

async function mkPage(bundle) {
  const page = await browser.newPage();
  page.on('pageerror', () => {});
  await page.setContent(`<html><body style="margin:0"><canvas id=c width=${W} height=${H}></canvas></body></html>`);
  await page.addScriptTag({ content: readFileSync(bundle, 'utf8') });
  return page;
}
const pages = [await mkPage(oldB), await mkPage(newB)];

async function render(page, b64) {
  return page.evaluate(async ([b64, W, H]) => {
    const old = document.getElementById('c');
    const c = document.createElement('canvas');
    c.width = W; c.height = H; c.id = 'c';
    old.replaceWith(c);
    if (window.__p) { try { window.__p.destroy(); } catch (e) {} }
    const bin = atob(b64); const u8 = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
    const errs = [];
    const origWarn = console.warn; const origErr = console.error;
    console.warn = (...a) => errs.push('W ' + a.join(' ').slice(0, 160));
    console.error = (...a) => errs.push('E ' + a.join(' ').slice(0, 160));
    try {
      const p = new RC.RcdPlayer(c); window.__p = p;
      await p.loadFromArrayBuffer(u8.buffer);
      await new Promise((r) => setTimeout(r, 120));
      p.stop && p.stop();
    } catch (e) { errs.push('X ' + String(e).slice(0, 200)); }
    console.warn = origWarn; console.error = origErr;
    const d = c.getContext('2d').getImageData(0, 0, W, H).data;
    let s = ''; const CH = 0x8000;
    for (let i = 0; i < d.length; i += CH) s += String.fromCharCode.apply(null, d.subarray(i, i + CH));
    return { px: btoa(s), errs: errs.slice(0, 5), png: c.toDataURL('image/png') };
  }, [b64, W, H]);
}

const results = [];
for (const f of files) {
  const b64 = readFileSync(f).toString('base64');
  const [a, b] = [await render(pages[0], b64), await render(pages[1], b64)];
  const pa = Buffer.from(a.px, 'base64'), pb = Buffer.from(b.px, 'base64');
  let diff = 0, nonblankA = 0, nonblankB = 0;
  for (let i = 0; i < pa.length; i += 4) {
    const d = Math.abs(pa[i] - pb[i]) + Math.abs(pa[i + 1] - pb[i + 1]) + Math.abs(pa[i + 2] - pb[i + 2]) + Math.abs(pa[i + 3] - pb[i + 3]);
    if (d > 24) diff++;
    if (pa[i + 3]) nonblankA++;
    if (pb[i + 3]) nonblankB++;
  }
  const r = { file: basename(f), diffPct: +(100 * diff / (pa.length / 4)).toFixed(3), covA: nonblankA, covB: nonblankB, errsA: a.errs, errsB: b.errs };
  results.push(r);
  if (r.diffPct > 0.5 || process.env.ALL) {
    writeFileSync(`${outDir}/${r.file}.old.png`, Buffer.from(a.png.split(',')[1], 'base64'));
    writeFileSync(`${outDir}/${r.file}.new.png`, Buffer.from(b.png.split(',')[1], 'base64'));
  }
}
writeFileSync(`${outDir}/results.json`, JSON.stringify(results, null, 1));
await browser.close();
results.sort((x, y) => y.diffPct - x.diffPct);
console.log('files', results.length, 'identical', results.filter((r) => r.diffPct === 0).length,
  '>0.5%', results.filter((r) => r.diffPct > 0.5).length);
for (const r of results.slice(0, 40)) console.log(r.diffPct, r.file, r.covA, r.covB, JSON.stringify(r.errsB));

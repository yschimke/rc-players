import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { createServer } from 'node:http';
const out = dirname(fileURLToPath(import.meta.url));
const repo = resolve(out, '../..');
const site = `${repo}/build/ts-empty-font-height`;
const { build } = createRequire(`${repo}/third_party/remote-compose-player/package.json`)('esbuild');
const { chromium } = createRequire(`${repo}/scripts/wasm-smoke/package.json`)(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
await mkdir(site, {recursive:true}); await mkdir(out, {recursive:true});
await copyFile(`${out}/banner.rc`, `${site}/banner.rc`);
for (const variant of ['before', 'after']) {
 await build({ entryPoints: [`${repo}/third_party/remote-compose-player/src/web/main.ts`], bundle: true, format: 'esm', target: 'es2022', outfile: `${site}/${variant}.js`, plugins: variant === 'before' ? [{name:'before-fix',setup(b){b.onLoad({filter:/CanvasPaintContext\.ts$/},async ({path})=>({contents:(await readFile(path,'utf8')).replace(/        \/\/ Empty text has no ink,[\s\S]*?bounds.fill\(0\); return;\n        }/, '        if (!text) { bounds.fill(0); return; }'),loader:'ts'}));}}] : [] });
 await writeFile(`${site}/${variant}.html`, `<!doctype html><style>body{margin:16px;background:white}#player{width:420px;height:160px}</style><div id="player"></div><script type="module">
 import {createPlayer,createHostApi} from './${variant}.js';
 const p=createPlayer(document.querySelector('#player'),{});p.player.setDensity(1);p.resize(420,160);
 await p.loadFromArrayBuffer(await (await fetch('./banner.rc')).arrayBuffer());
 p.resize(420,160);const h=createHostApi(p.player); const vars=h.variables();
 for(const [k,v] of Object.entries({headline:'Spring sale',body:'',cta:'Shop now',accent:'#FF6750A4','theme.surface':'#FFF3EDF7','theme.onSurface':'#FF1D1B20'})) {const name='USER:'+k in vars?'USER:'+k:k;if(name in vars)h.set(name,v);}
 window.player=p;window.ready=true;
 </script>`);
}
const server=createServer(async(req,res)=>{try{const name=req.url==='/'?'before.html':req.url.slice(1);const b=await readFile(`${site}/${name}`);res.setHeader('content-type',name.endsWith('.js')?'text/javascript':name.endsWith('.html')?'text/html':'application/octet-stream');res.end(b);}catch{res.writeHead(404).end();}}).listen(0,'127.0.0.1');
await new Promise(r=>server.once('listening',r));
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE, args:['--disable-gpu']});
try {const page=await browser.newPage({viewport:{width:452,height:192}});for (const variant of ['before','after']){await page.goto(`http://127.0.0.1:${server.address().port}/${variant}.html`);await page.waitForFunction(()=>window.ready);await page.waitForTimeout(1500);const metrics=await page.evaluate(()=>{const seen=new WeakSet(),out=[];function walk(o){if(!o||typeof o!=='object'||seen.has(o))return;seen.add(o);if('mCachedString' in o)out.push({text:o.mCachedString,w:o.mWidth,h:o.mHeight,tw:o.mTextW,th:o.mTextH,layout:o.mComputedTextLayout});for(const v of Object.values(o))walk(v);}walk(window.player.player.getDocument());return out;});
await writeFile(`${out}/${variant}.json`, JSON.stringify(metrics,null,2)+'\n');await page.screenshot({path:`${out}/${variant}.png`});}}
finally{await browser.close();server.close();}

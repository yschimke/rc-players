// Actual RC component inside author-owned chrome; no Home Assistant service is contacted.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE);
const browser=await chromium.launch({headless:true});
const directory=`${root}renders/rc-input-chrome`;
mkdirSync(directory,{recursive:true});
try {
  for(const chrome of ['default','none']) {
    const page=await browser.newPage({viewport:{width:560,height:440}});
    await page.setContent('<meta charset="utf-8"><style>body{margin:28px;background:#e9edf4;font:16px system-ui;color:#202124}.card{padding:24px;background:white;border-radius:20px;box-shadow:0 6px 24px #20305015}h2{font-size:20px;margin:0 0 8px}p{color:#556;margin:0 0 22px}.wrapper{padding:16px;border:2px solid #557ba9;border-radius:14px;background:#edf5ff}.caption{font-weight:600;margin-bottom:12px}#host{position:relative}canvas{display:block}button{font:inherit;color:#23528a}#status{margin-top:12px}</style><div class="card"><h2>Send to a room</h2><p>Contact / Room · Kitchen</p><div class="wrapper"><div class="caption">Message</div><div id="host"><canvas width="410" height="136"></canvas></div></div><div id="status">Author-provided border, label and background</div></div>');
    await page.addScriptTag({content:readFileSync(process.env.RC_PLAYER_JS,'utf8')});
    await page.evaluate(async chrome=>{
      window.player=new RC.RcdPlayer(document.querySelector('canvas'));
      player.configureInputs({editable:true});
      const bytes=RC.inputDocument([{key:'message',label:'Message',value:'Dinner is ready. See you in the kitchen!',multiline:true,submit:'example:send',chrome}],410);
      await player.loadFromArrayBuffer(bytes);
      return Array.from(new Uint8Array(bytes));
    },chrome).then(bytes=>{if(chrome === 'none') writeFileSync(`${root}Tests/RcNativePlayerCoreTests/Fixtures/input-chrome.rc`,Buffer.from(bytes));});
    await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    await page.screenshot({path:`${directory}/${chrome}.png`});
    await page.close();
  }
} finally {await browser.close();}

// Actual old/new player renders of one reusable input-contract document. No service transport.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE);
const browser=await chromium.launch({headless:true});
const directory=`${root}renders/rc-input-controls`;
mkdirSync(directory,{recursive:true});
let fixture;
try {
  for (const lane of ['after','before']) {
    const page=await browser.newPage({viewport:{width:640,height:900}});
    await page.setContent('<meta charset="utf-8"><style>body{font:16px system-ui;background:#f4f4f4;margin:24px}#card{position:relative;width:440px;padding:12px;border:1px solid #ddd;border-radius:16px;background:white}canvas{display:block}h2{font-size:18px}</style><h2>Portable input controls · v1</h2><div id="card"><canvas width="440" height="600"></canvas><p id="fallback"></p></div>');
    await page.addScriptTag({content:readFileSync(lane==='after' ? process.env.RC_PLAYER_JS : process.env.RC_PLAYER_BASELINE_JS,'utf8')});
    if (!fixture) {
      fixture=await page.evaluate(()=>Array.from(new Uint8Array(RC.inputDocument([
        {kind:'text',key:'message',label:'Message',value:'Dinner is ready'},
        {kind:'choice',key:'room',label:'Room',value:'Kitchen',options:['Kitchen','Living room']},
        {kind:'toggle',key:'urgent',label:'Urgent',value:'false'},
        {kind:'number',key:'temperature',label:'Temperature (0–30, step 0.5)',value:'21.5',minimum:0,maximum:30,step:0.5},
        {kind:'range',key:'brightness',label:'Brightness',value:'50',minimum:0,maximum:100,step:1,submit:'fixture:apply',submitLabel:'Apply'},
      ],440))));
      writeFileSync(`${directory}/input-controls.rc`,Buffer.from(fixture));
      writeFileSync(`${root}Tests/RcNativePlayerCoreTests/Fixtures/input-controls.rc`,Buffer.from(fixture));
    }
    await page.evaluate(async bytes=>{
      window.player=new RC.RcdPlayer(document.querySelector('canvas'));
      const required=['text','choice','toggle','number','range'].map(kind=>`rc:input/${kind}:v1`);
      const available=player.inputCapabilities().components;
      const supported=required.every(config=>available.includes(config));
      player.configureInputs({editable:supported});
      if (!supported) document.querySelector('#fallback').textContent='This player supports text and choice only. Update the player or open the app to edit the complete form.';
      await player.loadFromArrayBuffer(new Uint8Array(bytes).buffer);
    },fixture);
    if (lane==='after') {
      await page.getByRole('checkbox',{name:'Urgent'}).check();
      await page.getByRole('textbox',{name:'Temperature (0–30, step 0.5)'}).fill('22.5');
      await page.getByRole('slider',{name:/Brightness/}).focus();
      await page.getByRole('slider',{name:/Brightness/}).press('ArrowRight');
    }
    await page.screenshot({path:`${directory}/${lane}.png`});
    await page.close();
  }
} finally {await browser.close();}

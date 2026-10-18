import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE);

test('input overlays retain sibling defaults, isolate nested documents and obey clip hit testing', async () => {
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage();
    page.on('pageerror', error=>console.error(error.message));
    await page.setContent('<style>body{margin:0}#host{position:relative}canvas{display:block}</style><div id="host"><canvas width="600" height="600"></canvas></div>');
    await page.addScriptTag({content:readFileSync(process.env.RC_INPUT_HARNESS_JS,'utf8')});
    await page.evaluate(() => {
      const {CanvasPaintContext, WebInputComponents, Input} = InputTest;
      window.canvas = document.querySelector('canvas');
      window.pc = new CanvasPaintContext(null, canvas.getContext('2d'));
      window.submissions = [];
      window.inputs = new WebInputComponents(canvas, {editable:true, onSubmit:(action, values) => submissions.push({action,values})});
      window.field = (key, value, submit='') => ({text:kind => ({[Input.KEY]:key,[Input.VALUE]:value,[Input.LABEL]:key,[Input.SUBMIT]:submit})[kind] ?? '',integer:(_id,fallback=0)=>fallback,canReturnText:()=>true,returnText:()=>true});
      window.draw = (id, field, y) => {pc.matrixSave();pc.translate(0,y);inputs.draw(id,Input.TEXT,pc,200,100,field);pc.matrixRestore();};
      inputs.beginFrame();
      draw(1,field('room','Kitchen'),0);
      draw(2,field('message','Hello','send'),100);
      inputs.withDocument('embed-a',()=>draw(1,field('message','A','a'),200));
      inputs.withDocument('embed-b',()=>draw(1,field('message','B','b'),300));
      inputs.endFrame();
    });
    assert.equal(await page.locator('input').count(),4);
    await page.getByRole('textbox',{name:'message',exact:true}).nth(0).fill('Edited 🏡');
    await page.getByRole('button',{name:'Send',exact:true}).nth(0).click();
    await page.getByRole('textbox',{name:'message',exact:true}).nth(1).fill('A edited');
    await page.getByRole('button',{name:'Send',exact:true}).nth(2).click();
    assert.deepEqual(await page.evaluate(()=>submissions),[
      {action:'send',values:{room:'Kitchen',message:'Edited 🏡'}},
      {action:'b',values:{message:'B'}}
    ]);
    await page.evaluate(()=>{
      inputs.beginFrame(); pc.reset();
      pc.matrixSave();pc.clipRect(0,0,100,100);draw(1,field('clipped','value'),0);pc.matrixRestore();
      inputs.endFrame();
    });
    assert.equal(await page.evaluate(()=>document.elementFromPoint(50,40)?.tagName),'INPUT');
    assert.equal(await page.evaluate(()=>document.elementFromPoint(150,40)?.tagName),'CANVAS','clipped portion cannot intercept taps');
    await page.evaluate(()=>{
      inputs.beginFrame();pc.reset();pc.matrixSave();pc.clipRect(300,300,400,400);draw(1,field('clipped','value'),0);pc.matrixRestore();inputs.endFrame();
    });
    assert.equal(await page.locator('input:visible').count(),0);
    await page.evaluate(()=>{
      inputs.beginFrame();pc.reset();pc.matrixSave();pc.roundedClipRect(200,100,40,40,40,40);draw(1,field('rounded','value'),0);pc.matrixRestore();inputs.endFrame();
    });
    assert.equal(await page.locator('input').count(),0,'unrepresentable rounded clipping uses canvas alternative');
  } finally {await browser.close();}
});

test('two actual embedded copies of the same form keep independent edits', async () => {
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage();
    page.on('pageerror', error=>console.error(error.message));
    await page.setContent('<style>body{margin:0}#host{position:relative}canvas{display:block}</style><div id="host"><canvas width="600" height="600"></canvas></div>');
    await page.addScriptTag({content:readFileSync(process.env.RC_INPUT_HARNESS_JS,'utf8')});
    await page.evaluate(() => {
      const {CanvasPaintContext,WebInputComponents,WebCustomHost,inputDocument} = InputTest;
      const canvas=document.querySelector('canvas');
      const pc=new CanvasPaintContext(null,canvas.getContext('2d'));
      const inputs=new WebInputComponents(canvas,{editable:true});
      const host=new WebCustomHost(async()=>inputDocument([{key:'message',label:'Message',value:'Initial'}],200));
      host.inputs=inputs;
      window.paintEmbeds=()=>{
        pc.reset(); inputs.beginFrame();
        for (const [id,y] of [[1,0],[2,120]]) {
          pc.matrixSave();pc.translate(0,y);host.drawCustom(id,'rc:form.rc#native',pc,200,100,0);pc.matrixRestore();
        }
        inputs.endFrame();
      };
      paintEmbeds();
      window.renderTimer=setInterval(paintEmbeds,30);
    });
    await page.getByRole('textbox',{name:'Message'}).nth(1).waitFor();
    await page.getByRole('textbox',{name:'Message'}).nth(0).fill('First copy');
    await page.evaluate(()=>{clearInterval(renderTimer);paintEmbeds();paintEmbeds();});
    assert.deepEqual(await page.getByRole('textbox',{name:'Message'}).evaluateAll(inputs=>inputs.map(input=>input.value)),['First copy','Initial']);
  } finally {await browser.close();}
});


test('live input shape/label updates and public handle teardown remove obsolete controls', async () => {
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage();
    page.on('pageerror', error=>console.error(error.message));
    await page.setContent('<div id="host" style="position:relative;width:360px"></div>');
    await page.addScriptTag({content:readFileSync(process.env.RC_INPUT_HARNESS_JS,'utf8')});
    await page.evaluate(async()=>{
      const {createPlayer,inputDocument} = InputTest;
      window.handle=createPlayer(document.querySelector('#host'));
      handle.player.configureInputs({editable:true});
      await handle.loadFromArrayBuffer(inputDocument([{key:'message',label:'Old label',value:'Initial'}]));
    });
    await page.getByRole('textbox',{name:'Old label'}).fill('Draft survives');
    await page.evaluate(async()=>{
      await handle.loadFromArrayBuffer(InputTest.inputDocument([{key:'message',label:'New label',multiline:true,submit:'send'}]));
    });
    const field=page.getByRole('textbox',{name:'New label'});
    assert.equal(await field.evaluate(el=>el.tagName),'TEXTAREA');
    assert.equal(await field.inputValue(),'Draft survives');
    await page.getByRole('button',{name:'Send'}).waitFor();
    await page.evaluate(()=>handle.destroy());
    assert.equal(await page.locator('input,textarea,select,button,canvas').count(),0);
  } finally {await browser.close();}
});

test('boolean, numeric and bounded-range controls return values and block invalid submits', async () => {
  const browser=await chromium.launch({headless:true});
  try {
    const page=await browser.newPage();
    await page.setContent('<div id="host" style="position:relative;width:360px"></div>');
    await page.addScriptTag({content:readFileSync(process.env.RC_INPUT_HARNESS_JS,'utf8')});
    await page.evaluate(async()=>{
      const {createPlayer,inputDocument}=InputTest;
      window.events=[];window.submissions=[];
      window.handle=createPlayer(document.querySelector('#host'));
      handle.player.configureInputs({editable:true,onChange:(key,value)=>events.push({key,value}),onSubmit:(action,values)=>submissions.push({action,values})});
      window.fields=[
        {kind:'toggle',key:'urgent',label:'Urgent',value:'false'},
        {kind:'number',key:'temperature',label:'Temperature',value:'21.5',minimum:0,maximum:30,step:0.5},
        {kind:'range',key:'brightness',label:'Brightness',value:'50',minimum:0,maximum:100,step:1,submit:'apply'},
      ];
      await handle.loadFromArrayBuffer(inputDocument(fields));
    });
    await page.getByRole('checkbox',{name:'Urgent'}).check();
    const number=page.getByRole('textbox',{name:'Temperature'});
    await number.fill('-');
    await page.getByRole('button',{name:'Send'}).click();
    assert.equal(await page.evaluate(()=>submissions.length),0);
    await number.fill('22.5');
    const slider=page.getByRole('slider',{name:/Brightness/});
    await slider.focus();await slider.press('ArrowRight');
    await page.getByRole('button',{name:'Send'}).click();
    assert.deepEqual(await page.evaluate(()=>submissions),[{action:'apply',values:{urgent:'true',temperature:'22.5',brightness:'51'}}]);
    assert(await page.evaluate(()=>events.some(e=>e.key==='urgent' && e.value==='true')));
    await page.evaluate(async()=>{fields[0].enabled=false;await handle.loadFromArrayBuffer(InputTest.inputDocument(fields));});
    assert.equal(await page.getByRole('checkbox').count(),0);
    await page.getByText(/Unavailable/).waitFor();
    const exported=await page.evaluate(async()=>{
      const data=handle.player.screenshotDataURL();
      const blob=await handle.player.screenshot();
      const image=new Image();image.src=data;await image.decode();
      const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
      const context=canvas.getContext('2d');context.drawImage(image,0,0);
      const pixels=context.getImageData(0,0,canvas.width,canvas.height).data;
      let ink=0;for(let i=0;i<pixels.length;i+=4) if(pixels[i+3] && pixels[i]<150 && pixels[i+1]<150 && pixels[i+2]<150) ink++;
      return {ink,blobSize:blob.size};
    });
    assert(exported.ink > 100,'canvas screenshot APIs include labels, values and control chrome');
    assert(exported.blobSize > 1000);
    await page.evaluate(()=>handle.destroy());
  } finally {await browser.close();}
});


test('unknown input versions use a canvas alternative without fetching an embedded document', async () => {
  const browser=await chromium.launch({headless:true});
  try {
    const page=await browser.newPage();
    await page.setContent('<canvas width="360" height="80"></canvas>');
    await page.addScriptTag({content:readFileSync(process.env.RC_INPUT_HARNESS_JS,'utf8')});
    const result=await page.evaluate(()=>{
      let fetches=0;
      const host=new InputTest.WebCustomHost(async()=>{fetches++;return null;});
      const canvas=document.querySelector('canvas');
      const context=canvas.getContext('2d');
      const painted=host.drawCustom(1,'rc:input/text:v99',new InputTest.CanvasPaintContext(null,context),360,80,0);
      const visible=Array.from(context.getImageData(0,0,360,80).data).some((value,index)=>index%4===3 && value>0);
      return {fetches,painted,visible};
    });
    assert.deepEqual(result,{fetches:0,painted:true,visible:true});
  } finally {await browser.close();}
});

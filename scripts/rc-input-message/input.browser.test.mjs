import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE);

test('input overlays retain sibling defaults, isolate nested documents and obey clip hit testing', async () => {
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage();
    await page.setContent('<style>body{margin:0}#host{position:relative}canvas{display:block}</style><div id="host"><canvas width="600" height="600"></canvas></div>');
    await page.addScriptTag({content:readFileSync(process.env.RC_INPUT_HARNESS_JS,'utf8')});
    await page.evaluate(() => {
      const {CanvasPaintContext, WebInputComponents, Input} = InputTest;
      window.canvas = document.querySelector('canvas');
      window.pc = new CanvasPaintContext(null, canvas.getContext('2d'));
      window.submissions = [];
      window.inputs = new WebInputComponents(canvas, {editable:true, onSubmit:(action, values) => submissions.push({action,values})});
      window.field = (key, value, submit='') => ({text:kind => ({[Input.KEY]:key,[Input.VALUE]:value,[Input.LABEL]:key,[Input.SUBMIT]:submit})[kind] ?? '',integer:()=>0,canReturnText:()=>true,returnText:()=>true});
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

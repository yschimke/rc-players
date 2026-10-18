import assert from 'node:assert/strict';
import { Custom } from './core/operations/layout/managers/Custom';
import type { RemoteContext } from './core/RemoteContext';

const texts = new Map<number, string>([[50, 'Hello'], [51, 'message']]);
let repaints = 0;
const context = { getText: (id: number) => texts.get(id),
    loadText: (id: number, value: string) => texts.set(id, value),
    needsRepaint: () => repaints++ } as unknown as RemoteContext;
const component = new Custom(1, 0, 100, [
    { type: 1, dataType: 2, value: 50 }, { type: 2, dataType: 4, value: 50 },
    { type: 3, dataType: 2, value: 51 }, { type: 5, dataType: 0, value: 1 },
]).component(context);
assert.equal(component.text(1), 'Hello');
assert.equal(component.integer(5), 1);
assert.equal(component.canReturnText(2), true);
assert.equal(component.returnText(2, 'Hello 🏡\n"room"'), true);
assert.equal(component.text(1), 'Hello 🏡\n"room"');
assert.equal(texts.get(50), 'Hello 🏡\n"room"');
assert.equal(repaints, 1);
assert.equal(component.returnText(1, 'undeclared'), false);
assert.equal(component.returnText(99, 'undeclared'), false);
assert.equal(component.text(2, 'fallback'), 'fallback');
assert.equal(component.integer(1, 7), 7);
assert.equal(repaints, 1);
console.log('custom input value/return channel round trips ok');
import { inputDocument } from './web/InputDocument';
import { RemoteComposeBuffer } from './core/RemoteComposeBuffer';
import { CoreDocument } from './core/CoreDocument';
const doc = new CoreDocument();
doc.initFromBuffer(RemoteComposeBuffer.fromArrayBuffer(inputDocument([{ key: 'message', label: 'Message', submit: 'send' }])));
assert.equal(doc.getRootLayoutComponent()?.getList().length, 1);
assert.throws(() => inputDocument([{key:'message',label:'Message'},{key:'message',label:'Duplicate'}]));
console.log('portable input document decode and validation ok');
import { intersectInputClip, mapInputPoint, inverseInputPoint } from './web/InputGeometry';
const square = (left: number, top: number, right: number, bottom: number) => [{x:left,y:top},{x:right,y:top},{x:right,y:bottom},{x:left,y:bottom}];
assert.deepEqual(intersectInputClip(square(0,0,100,100), square(200,0,300,100)), []);
assert.deepEqual(intersectInputClip(square(0,0,100,100), square(0,0,0,100)), []);
for (const clip of [square(20,20,80,80), square(20,20,80,80).reverse()]) {
    const clipped = intersectInputClip(square(0,0,100,100), clip);
    assert.equal(clipped.length, 4);
    assert(clipped.every(p => p.x >= 20 && p.x <= 80 && p.y >= 20 && p.y <= 80));
}
const transform = {a:0,b:2,c:-3,d:0,e:10,f:20};
assert.deepEqual(inverseInputPoint(transform, mapInputPoint(transform, {x:20,y:40})), {x:20,y:40});
console.log('transformed, reflected, disjoint and empty input clips ok');

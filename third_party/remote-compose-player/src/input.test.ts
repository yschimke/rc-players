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

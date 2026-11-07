import assert from 'node:assert/strict';
import { WireBuffer } from './core/WireBuffer';
import { ColumnLayout } from './core/operations/layout/managers/ColumnLayout';
import { RowLayout } from './core/operations/layout/managers/RowLayout';
import { RoundedClipRectModifier } from './core/operations/layout/modifiers/ModifierOperations';
import { ContextMode } from './core/RemoteContext';
import { asNan, floatToRawIntBits } from './core/operations/Utils';

// A layout's `spacedBy` and a rounded clip's radii can be NaN-boxed variable ids: Compose writes dp
// spacing and corners as expressions over the density. Read as raw floats and never resolved, every
// child of such a Row or Column was placed at NaN, so the container had no size, no background and
// no touch area, and no corner was rounded.
const ref = (id: number) => floatToRawIntBits(asNan(id));

/** The wire bytes of a layout manager's fields: two ids, two positionings, then `spacedBy`'s bits. */
function layoutBytes(spacedByBits: number): ArrayBuffer {
    const v = new DataView(new ArrayBuffer(20));
    v.setInt32(0, -5); v.setInt32(4, -1); v.setInt32(8, 1); v.setInt32(12, 4); v.setInt32(16, spacedByBits);
    return v.buffer;
}

const paint = (value: number) => ({ getContext: () => ({ getFloat: (id: number) => (id === 46 ? value : NaN) }), getDensityBehavior: () => 0, getDensity: () => 2 });

for (const Layout of [ColumnLayout, RowLayout]) {
    const ops: any[] = [];
    Layout.read(WireBuffer.fromArrayBuffer(layoutBytes(ref(46))), ops);
    assert.equal(ops[0].spacedByPx(paint(8), ops[0].mSpacedBy), 8, `${Layout.name}: spacing held in a variable is read from it`);
    const literal: any[] = [];
    Layout.read(WireBuffer.fromArrayBuffer(layoutBytes(floatToRawIntBits(6))), literal);
    assert.equal(literal[0].spacedByPx(paint(8), literal[0].mSpacedBy), 6, `${Layout.name}: a literal spacing is used as it is`);
}
console.log('spacedBy variables ok');

const v = new DataView(new ArrayBuffer(16));
for (let i = 0; i < 4; i++) v.setInt32(i * 4, ref(44));
const clips: any[] = [];
RoundedClipRectModifier.read(WireBuffer.fromArrayBuffer(v.buffer), clips);
let radii: number[] = [];
const pc = { roundedClipRect: (_w: number, _h: number, ...r: number[]) => { radii = r; } };
clips[0].layoutDecorator(100, 50);
clips[0].apply({ mMode: ContextMode.PAINT, getPaintContext: () => pc, getFloat: (id: number) => (id === 44 ? 16 : NaN) });
assert.deepEqual(radii, [16, 16, 16, 16], 'radii held in a variable are read from it');
console.log('rounded clip variables ok');

// A click's definitions load with the document; its actions still run on input only. Compose writes an
// action's name once, as a DATA_TEXT inside the first click that uses it, and a later button names it by
// id: with the click's children skipped at load, that button's action had no name until the first one
// had been tapped.
import { CoreDocument } from './core/CoreDocument';
import { TextData } from './core/operations/DataOperations';
import { ClickModifier } from './core/operations/layout/modifiers/ModifierOperations';
import { HostNamedActionOperation } from './core/operations/layout/modifiers/ActionOperations';

const texts = new Map<number, string>();
let ran = 0;
const loading: any = new Proxy({
    loadText: (id: number, text: string) => { texts.set(id, text); },
    getText: (id: number) => texts.get(id),
    getInteger: () => 1, getFloat: () => 1,
    runNamedAction: () => { ran++; }, runAction: () => { ran++; },
}, { get: (t: any, k) => (k in t ? t[k] : () => undefined) });
const click = new ClickModifier();
click.mList.push(new TextData(64, 'setQuantity'), new HostNamedActionOperation(64, 1, 63));
(new CoreDocument() as any).applyOperations(loading, [click]);
assert.equal(texts.get(64), 'setQuantity', "a click's definitions load with the document");
assert.equal(ran, 0, "a click's actions don't run at load");
console.log('click definitions ok');

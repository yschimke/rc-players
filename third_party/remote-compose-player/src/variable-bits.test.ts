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

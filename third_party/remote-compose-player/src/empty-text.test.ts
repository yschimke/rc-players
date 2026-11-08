import assert from 'node:assert/strict';
import { CanvasPaintContext } from './web/CanvasPaintContext';

// The measurements are supplied by a canvas; exercise the actual flag handling independently
// of the host font. A named empty string differs from a name that was never defined.
const context = Object.create(CanvasPaintContext.prototype);
context.textCache = new Map([[1, ''], [2, 'Hello']]);
context.textSize = 14;
context.setFont = () => {};
context.measureTextCached = (text: string) => ({
    width: text.length * 7,
    actualBoundingBoxLeft: 0, actualBoundingBoxRight: text.length * 7,
    actualBoundingBoxAscent: text ? 9 : 0, actualBoundingBoxDescent: text ? 3 : 0,
    fontBoundingBoxAscent: 11, fontBoundingBoxDescent: 4,
});
CanvasPaintContext.mockTextMetrics = false;
const bounds = (id: number, flags: number) => {
    const out = new Float32Array(4);
    context.getTextBounds(id, 0, -1, flags, out);
    return Array.from(out).map((v) => v === 0 ? 0 : v);
};
assert.deepEqual(bounds(1, 2), [0, -11, 0, 4], 'empty font-height text retains ascent/descent');
assert.deepEqual(bounds(1, 6), [0, -11, 0, 4], 'adding the advance flag retains zero width');
assert.deepEqual(bounds(1, 0), [0, 0, 0, 0], 'empty ink bounds stay empty');
assert.deepEqual(bounds(999, 2), [0, 0, 0, 0], 'undefined text still has no bounds');
assert.deepEqual(bounds(2, 2), [0, -11, 35, 4], 'nonempty font-height text is unchanged');
assert.deepEqual(bounds(2, 0), [0, -9, 35, 3], 'nonempty ink metrics are unchanged');
console.log('empty text font height ok');

import assert from 'node:assert/strict';
import { CanvasPaintContext } from './web/CanvasPaintContext';

// Skia's SRC blend replaces the destination inside the drawn shape only. Canvas2D's `copy`
// replaces the whole clip region, so a SRC dot used to wipe everything painted before it (every
// remote-m3 slider drew as a single dot). The shape must be punched out with `destination-out` and
// then drawn `source-over`; `copy` must never reach a fill.
const calls: string[] = [];
let gco = 'source-over';
const target: any = {
    fillStyle: '#000',
    strokeStyle: '#000',
    globalAlpha: 1,
    lineWidth: 1,
    get globalCompositeOperation() { return gco; },
    set globalCompositeOperation(v: string) { gco = v; },
    fill() { calls.push(`fill:${gco}`); },
    stroke() { calls.push(`stroke:${gco}`); },
};
const ctx: any = new Proxy(target, {
    get: (t, k) => (k in t ? t[k] : () => undefined),
    set: (t, k, v) => { t[k] = v; return true; },
});

const paint: any = new CanvasPaintContext(null as any, ctx);
paint.blendMode = 'copy';
paint.style = 0;
paint.drawCircle(10, 10, 4);
assert.deepEqual(calls, ['fill:destination-out', 'fill:source-over'], 'SRC fills punch out then draw');

calls.length = 0;
paint.blendMode = 'source-over';
paint.drawCircle(10, 10, 4);
assert.deepEqual(calls, ['fill:source-over'], 'other blend modes are untouched');

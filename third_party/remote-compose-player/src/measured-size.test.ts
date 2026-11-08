import assert from 'node:assert/strict';
import { RcdPlayer } from './web/main';
import { CoreDocument } from './core/CoreDocument';

// Use the public getter without a canvas: it must report the last layout, without repainting.
const player = Object.create(RcdPlayer.prototype) as RcdPlayer;
assert.equal(player.getMeasuredSize(), null);
const doc = new CoreDocument();
Object.assign(player, { document: doc });
assert.equal(player.getMeasuredSize(), null, 'an authored header is not a measured root');
let width = 420, height = 112;
doc.getRootLayoutComponent = () => ({ getWidth: () => width, getHeight: () => height }) as any;
assert.deepEqual(player.getMeasuredSize(), { width: 420, height: 112 });
width = 280; height = 160;
assert.deepEqual(player.getMeasuredSize(), { width: 280, height: 160 }, 'a new layout is observed without caching');
for (const value of [0, -1, NaN, Infinity]) {
    height = value;
    assert.equal(player.getMeasuredSize(), null, 'unmeasured and invalid sizes cannot size a host');
}
console.log('measured size distinguishes a root layout from the authored header and follows relayout');

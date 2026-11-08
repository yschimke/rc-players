import assert from 'node:assert/strict';
import { RcdPlayer } from './web/main';
import { CoreDocument } from './core/CoreDocument';
import { RootLayoutComponent } from './core/operations/layout/RootLayoutComponent';
import type { MeasurePass } from './core/operations/layout/measure/MeasurePass';
import { Component } from './core/operations/layout/Component';

const player = Object.create(RcdPlayer.prototype) as RcdPlayer;
assert.equal(player.measureSize(), null);
const doc = new CoreDocument();
const context = { mWidth: 420, mHeight: 160, getPaintContext: () => ({}) };
Object.assign(player, { document: doc, remoteContext: context, canvas: { width: 420, height: 160 } });
assert.equal(player.measureSize(), null, 'the authored header is not measured content');
const root = new RootLayoutComponent(1);
const child = new Component(2);
let contentHeight = 112;
Object.assign(child, {
    measure: (_paint: unknown, minW: number, maxW: number, minH: number, maxH: number, pass: MeasurePass) => {
        assert.equal(minW, 0); assert.equal(maxW, 420);
        assert.equal(minH, 0); assert.equal(maxH, Infinity);
        pass.get(child).setW(maxW); pass.get(child).setH(contentHeight);
        child.setWidth(100); child.setHeight(80); // an animation has not reached its measured target
    },
    layout: () => {},
});
root.getList().push(child);
Object.assign(doc, { mRootLayoutComponent: root });
assert.deepEqual(player.measureSize(), { width: 420, height: 112 });
contentHeight = 240;
assert.deepEqual(player.measureSize(), { width: 420, height: 240 }, 'wrap content can exceed the authored viewport');
assert.equal(root.needsMeasure(), true, 'the next paint must measure again for its viewport');
assert.equal(context.mHeight, 160, 'measurement preserves the viewport');
for (const height of [0, -1, NaN, Infinity]) {
    contentHeight = height;
    assert.equal(player.measureSize(), null, 'invalid/fill-height content cannot size a host');
}
console.log('wrap measurement follows content beyond the authored height and preserves the viewport');

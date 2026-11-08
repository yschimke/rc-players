import assert from 'node:assert/strict';
import { RcdPlayer } from './web/main';
import { CoreDocument } from './core/CoreDocument';
import { RootLayoutComponent } from './core/operations/layout/RootLayoutComponent';
import { Component } from './core/operations/layout/Component';
import { BoxLayout } from './core/operations/layout/managers/BoxLayout';
import { WidthModifier, HeightModifier } from './core/operations/layout/modifiers/ModifierOperations';
import { WebRemoteContext } from './web/WebRemoteContext';
import type { MeasurePass } from './core/operations/layout/measure/MeasurePass';

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
    layout: () => { throw new Error('measuring must not apply bounds'); },
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

// A real fill-height layout with animation enabled must survive unbounded sizing. Applying the
// Infinity result to layout used to start an animation with NaN bounds that relayout could not fix.
const paint = { getContext: () => remote } as any;
const remote = new WebRemoteContext(paint);
remote.mWidth = 420; remote.mHeight = 160;
remote.setAnimationEnabled(true);
const fill = new BoxLayout(3, -1, BoxLayout.START, BoxLayout.TOP);
Object.assign(fill, {
    mWidthMod: new WidthModifier(WidthModifier.FILL, 0x7fc00000),
    mHeightMod: new HeightModifier(HeightModifier.FILL, 0x7fc00000),
});
const fillRoot = new RootLayoutComponent(4);
fillRoot.getList().push(fill);
Object.assign(doc, { mRootLayoutComponent: fillRoot });
Object.assign(player, { remoteContext: remote });
fillRoot.layoutTree(remote);
assert.equal(fill.getHeight(), 160);
assert.equal(fillRoot.getHeight(), 160);
assert.equal(player.measureSize(), null, 'fill-height has no finite wrap-content size');
assert.equal(fill.getHeight(), 160, 'unbounded measurement must not change live bounds');
assert.equal(fillRoot.getHeight(), 160, 'measurement must not change root viewport bounds');
assert.equal((fill as any).mAnimateMeasure, null, 'measurement must not create an animation');
fillRoot.layoutTree(remote);
assert.equal(fill.getHeight(), 160, 'viewport relayout remains finite after measuring');
assert.equal((fill as any).mAnimateMeasure, null);
remote.mHeight = 200;
fillRoot.invalidateMeasure();
fillRoot.layoutTree(remote);
const animation = (fill as any).mAnimateMeasure;
assert.ok(animation, 'resizing a painted fill layout starts a real bounds animation');
const animatedHeight = fill.getHeight();
assert.equal(player.measureSize(), null);
assert.equal((fill as any).mAnimateMeasure, animation, 'measurement preserves the active animation');
assert.equal(fill.getHeight(), animatedHeight, 'measurement preserves the animated bounds');
fillRoot.layoutTree(remote);
assert.ok(Number.isFinite(fill.getHeight()), 'viewport layout stays finite with an active animation');
console.log('unbounded measurement preserves live fill-height layout and animation state');

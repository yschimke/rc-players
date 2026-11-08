import assert from 'node:assert/strict';
import { RcdPlayer } from './web/main';
import { CoreDocument } from './core/CoreDocument';
import { RootLayoutComponent } from './core/operations/layout/RootLayoutComponent';
import { Component } from './core/operations/layout/Component';
import { FloatExpression } from './core/operations/FloatExpression';
import { ComponentValue } from './core/operations/ComponentValue';
import { BoxLayout } from './core/operations/layout/managers/BoxLayout';
import { WidthModifier, HeightModifier, ScrollModifier } from './core/operations/layout/modifiers/ModifierOperations';
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
let measureCalls = 0;
Object.assign(child, {
    measure: (_paint: unknown, minW: number, maxW: number, minH: number, maxH: number, pass: MeasurePass) => {
        measureCalls++;
        assert.equal(minW, 0); assert.equal(maxW, 420);
        assert.equal(minH, 0); assert.equal(maxH, context.mHeight);
        pass.get(child).setW(maxW); pass.get(child).setH(contentHeight);
    },
    // Animated live bounds can differ from the target recorded in the MeasurePass.
    layout: () => { child.setWidth(100); child.setHeight(80); },
});
root.getList().push(child);
Object.assign(doc, { mRootLayoutComponent: root });
assert.equal(player.measureSize(), null, 'no content measurement exists before normal layout');
root.layoutTree(context as any);
assert.deepEqual(player.measureSize(), { width: 420, height: 112 });
assert.equal(child.getHeight(), 80, 'the query returns the destination, not animated bounds');
assert.equal(measureCalls, 1, 'querying a measurement never invokes measure again');
const copy = player.measureSize()!; copy.height = 999;
assert.equal(player.measureSize()!.height, 112, 'callers cannot mutate the recorded measurement');
context.mHeight = 320; contentHeight = 240;
root.invalidateMeasure(); root.layoutTree(context as any);
assert.deepEqual(player.measureSize(), { width: 420, height: 240 }, 'the next normal layout refreshes the measurement');
for (const height of [0, -1, NaN, Infinity]) {
    contentHeight = height;
    root.invalidateMeasure(); root.layoutTree(context as any);
    assert.equal(player.measureSize(), null, 'invalid measurements cannot size a host');
}
console.log('content sizing reads the normal layout destination without a speculative measure pass');

// A real fill-height layout with animation enabled must survive unbounded sizing. Applying the
// Infinity result to layout used to start an animation with NaN bounds that relayout could not fix.
const paint = { getContext: () => remote, needsRepaint() {} } as any;
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
console.log('size queries preserve live fill-height layout and animation state');

// Measuring even a nested scroller must not alter its live limits or an in-progress drag.
remote.mHeight = 160;
remote.setAnimationEnabled(false);
const scroll = new ScrollModifier(ScrollModifier.VERTICAL, 0, 0, 0);
const scrollingBox = new BoxLayout(5, -1, BoxLayout.START, BoxLayout.TOP);
const tallContent = new BoxLayout(6, -1, BoxLayout.START, BoxLayout.TOP);
const outer = new BoxLayout(7, -1, BoxLayout.START, BoxLayout.TOP);
for (const box of [scrollingBox, outer]) Object.assign(box, {
    mWidthMod: new WidthModifier(WidthModifier.FILL, 0x7fc00000),
    mHeightMod: new HeightModifier(HeightModifier.FILL, 0x7fc00000),
});
Object.assign(tallContent, {
    mWidthMod: new WidthModifier(WidthModifier.FILL, 0x7fc00000),
    mHeightMod: new HeightModifier(HeightModifier.EXACT, 0x43c80000), // 400f
});
Object.assign(scrollingBox, { mScrollModifier: scroll });
scrollingBox.mChildrenComponents.push(tallContent);
outer.mChildrenComponents.push(scrollingBox);
const scrollRoot = new RootLayoutComponent(8);
scrollRoot.getList().push(outer);
Object.assign(doc, { mRootLayoutComponent: scrollRoot });
scrollRoot.layoutTree(remote);
assert.equal(scrollingBox.getHeight(), 160);
scroll.onTouchDown(remote, 0, 0);
scroll.onTouchDrag(remote, 0, -50);
assert.equal(scroll.getScrollY(), -50);
assert.equal(player.measureSize(), null, 'nested scrolling content uses its authored viewport');
scroll.onTouchDrag(remote, 0, -60);
assert.equal(scroll.getScrollY(), -60, 'measurement preserves the drag and scroll limits');
scrollRoot.layoutTree(remote);
assert.equal(scroll.getScrollY(), -60, 'viewport relayout preserves the current scroll offset');
scroll.onTouchDrag(remote, 0, -1000);
assert.equal(scroll.getScrollY(), -240, 'the 400px content retains its 240px scroll limit');
console.log('measurement preserves nested scrolling limits and an in-progress drag');

// componentHeight() also feeds expression animation state during normal measurement. A size
// query must not evaluate that expression against a speculative Infinity height.
remote.mHeight = 160;
const expression = new FloatExpression(112, new Int32Array([0x7fc00000 | 111]), new Float32Array([0.5]));
expression.registerListening(remote);
fill.addComponentValue(new ComponentValue(ComponentValue.HEIGHT, 3, 111));
Object.assign(fill, { mContentOps: [expression] });
const expressionRoot = new RootLayoutComponent(9);
expressionRoot.getList().push(fill);
Object.assign(doc, { mRootLayoutComponent: expressionRoot });
expressionRoot.layoutTree(remote);
assert.equal(remote.getFloat(112), 160);
assert.equal(player.measureSize(), null);
assert.equal(remote.getFloat(111), 160, 'query preserves the live componentHeight() variable');
assert.equal(remote.getFloat(112), 160, 'query preserves the animated expression value');
assert.equal((expression as any).mFloatAnimation.getTargetValue(), 160);
expressionRoot.invalidateMeasure(); expressionRoot.layoutTree(remote);
remote.setAnimationTime(1); expression.apply(remote);
assert.equal(remote.getFloat(112), 160, 'expression animation remains finite after viewport relayout');
console.log('size queries preserve component-height variables and animated float expressions');

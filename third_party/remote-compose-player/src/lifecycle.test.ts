import assert from 'node:assert/strict';
import { RcdPlayer } from './web/main';

// A controlled browser scheduler makes delayed repaint leaks deterministic.
const timers = new Map<number, () => void>();
const frames = new Map<number, FrameRequestCallback>();
let nextId = 1;
Object.assign(globalThis, {
    window: { devicePixelRatio: 1 },
    setTimeout: (callback: () => void) => { const id = nextId++; timers.set(id, callback); return id; },
    clearTimeout: (id: number) => timers.delete(id),
    requestAnimationFrame: (callback: FrameRequestCallback) => { const id = nextId++; frames.set(id, callback); return id; },
    cancelAnimationFrame: (id: number) => frames.delete(id),
});

function playerWithDelayedRepaint(): RcdPlayer {
    const context = { setTransform() {}, clearRect() {} };
    const player = new RcdPlayer({
        getContext: () => context, addEventListener() {}, removeEventListener() {},
        width: 100, height: 100,
    } as unknown as HTMLCanvasElement);
    // Exercise the real frame scheduler without depending on drawing or a DOM implementation.
    Object.assign(player, {
        document: { paint() {}, needsRepaint: () => 100 },
        remoteContext: { setAnimationTime() {} },
        paintContext: { reset() {}, clearNeedsRepaint() {}, destroy() {} },
    });
    player.setTheme('light');
    const [id, callback] = frames.entries().next().value!;
    frames.delete(id);
    callback(0);
    assert.equal(timers.size, 1, 'the document requests one delayed repaint');
    return player;
}

for (const action of ['stop', 'pause', 'destroy'] as const) {
    const player = playerWithDelayedRepaint();
    player[action]();
    assert.equal(timers.size, 0, `${action} must cancel the delayed repaint`);
    assert.equal(frames.size, 0, `${action} must leave no scheduled frame`);
}

const player = playerWithDelayedRepaint();
player.repaint();
assert.equal(timers.size, 1, 'an explicit repaint must replace the old delay');
player.setTheme('dark');
assert.equal(timers.size, 0, 'an interaction replaces the delayed repaint');
assert.equal(frames.size, 1, 'an interaction schedules one immediate frame');
player.stop();
assert.equal(timers.size + frames.size, 0);

const delayedPlayer = playerWithDelayedRepaint();
const [id, callback] = timers.entries().next().value!;
timers.delete(id);
callback();
assert.equal(frames.size, 1, 'the delay schedules exactly one animation frame');
delayedPlayer.stop();
assert.equal(timers.size + frames.size, 0);
console.log('delayed repaints cancel on stop, pause, destroy and explicit repaint');

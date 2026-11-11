# Creation: a loop's body and what follows it draw with their own paints

`RemoteCanvas` writes a paint as its difference from the last one it wrote. A loop's body is
written once and drawn once per value of the index, so on every pass after the first the player's
paint at the top of the body is the body's last, not the paint before the loop; and when the range
is empty the paint after the loop is still the one before it, not the body's last. The canvas knew
neither: a first draw in the body with the paint that preceded the loop was sent no paint, and so
was a draw after the loop with the body's last paint. The fork's writer now has the first paint in
a loop's body and the first after the loop written in full, as it already did around a conditional
block, an offscreen bitmap, a pattern and a for-each. AndroidX's `RemoteCanvas.loop` is the same
text and its writer forces a full paint only at a component's content start, so it has the bug.

Two 100×40 documents from `RcCanvasLoopPaintPlaybackTest`, captured with the fork and played by the
CMP player, left to right, at 4×. Each draws a red square top left; then a loop whose body draws a
red square in the top row and a blue one under it, one column further right on each pass; then, in
the last column, a blue square in the bottom row and a red one above it.

1. the loop runs three times;
2. the loop's range is empty (its end is a named value the player holds, 0).

| | |
| --- | --- |
| before | ![before](before.png) |
| after | ![after](after.png) |

Before, the top square of the second and third passes is blue, the paint the pass before it ended
with, and after the loop that did not run the bottom square is red, the paint before the loop.
After, every square is the colour it was drawn with.

```sh
RC_LAYOUT_EVIDENCE_DIR=<dir> scripts/agent-gradle.sh :rc-player-embedded:jvmTest \
  --tests '*RcCanvasLoopPaintPlaybackTest*'
```

writes `loop-paint.png`; `before.png` is that file with the fix reverted (the two
`paintResetPending = true` lines of `startLoop` and `endLoop` in
`third_party/remote-creation-writer/…/RemoteDocumentWriter.kt`).

# Creation: the paint after a conditional block or an offscreen bitmap

The creation canvas writes a paint as its difference from the last paint it wrote. Inside a
`drawConditionally` block whose condition is not a constant, and around `drawToOffscreenBitmap`,
the last paint written is not necessarily the paint the player has: the block may not have run,
and AndroidX's player sets the paint's alpha when it switches to a bitmap. AndroidX's `RemoteCanvas`
therefore writes the whole paint for the first draw inside such a block and the first after it. The
fork set the flag for that but never read it. It now does.

Two 60×20 documents from `RcCanvasPaintAfterBlockPlaybackTest`, captured with the fork and played
by the CMP player, left to right, at 4×:

1. a red square, a conditional block (its condition a named boolean that is false) drawing a blue
   square, then a blue square;
2. a red square, an offscreen bitmap filled red with a blue dot, a blue square, then the bitmap
   drawn between the two.

| | |
| --- | --- |
| before | ![before](before.png) |
| after | ![after](after.png) |

Before, the square after the skipped block is red: the canvas had last written blue, inside the
block, so it wrote no paint for the square, and the player still had red. After, it is blue. The
second document is the same before and after in this player, which keeps one paint across a bitmap
switch; it is here to show the bitmap case is not disturbed.

```sh
RC_LAYOUT_EVIDENCE_DIR=<dir> scripts/agent-gradle.sh :rc-player-embedded:jvmTest \
  --tests '*RcCanvasPaintAfterBlockPlaybackTest*'
```

writes `paint-after-block.png`; `before.png` is that file with the fix reverted.

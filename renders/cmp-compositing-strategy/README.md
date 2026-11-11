# CMP: a graphics layer's compositing strategy

`remote-creation-compose` writes `RemoteCompositingStrategy` as the layer's `COMPOSITING_STRATEGY`
attribute: 0 `Auto`, 1 `Offscreen`, 2 `ModulateAlpha`. AndroidX's embedded player maps it onto
Compose's `CompositingStrategy` since androidx-main `28fb380046b`. The CMP player accepted the
attribute and ignored it, so every layer composited as `Auto`; it now maps it the same way. The View
player still ignores it.

Four 60×20 documents from `RcGraphicsLayerCompositingRenderTest`, left to right, at 6× on a
checkerboard:

1. a half-alpha layer drawing an opaque red rectangle and an opaque blue one over its end, `Auto`;
2. the same, `ModulateAlpha`;
3. a full-alpha layer over a blue parent, drawing a red rectangle and clearing its middle with
   `BlendMode.Clear`, `Auto`;
4. the same, `Offscreen`.

| | |
| --- | --- |
| before | ![before](before.png) |
| after | ![after](after.png) |

Before, the second cell is the first and the fourth is the third. After, `ModulateAlpha` fades each
draw on its own, so the red shows through the blue where they overlap, and `Offscreen` keeps the
cleared hole inside the layer, so the parent's blue shows through it instead of being erased.

```sh
RC_LAYOUT_EVIDENCE_DIR=<dir> scripts/agent-gradle.sh :rc-player-compose:jvmTest \
  --tests '*RcGraphicsLayerCompositingRenderTest*'
```

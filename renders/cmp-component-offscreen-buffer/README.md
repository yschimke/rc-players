# cmp-component-offscreen-buffer — alpha22's component offscreen buffers, anywhere they are drawn

remote-core 1.0.0-alpha22 adds `BitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER` (4):
`createOffscreenBitmap(imageId[, componentId])` declares a RAW8888 1 × 1 bitmap whose payload is
empty (the active component) or the component id, big-endian. It has no pixels and no fixed size:
each time it is drawn into, it is sized to a component — the one its payload names, else the one
the `DrawToBitmap` captures (`MODE_COMPONENT_ID`, the colour word naming it), else the component
whose operations are running. `drawComponentToBitmap` redirects into it, paints the component there
with `DrawContent`, and returns; the bitmap then draws like any other.

The CMP player decoded these but had no bitmap for them, so the first `DrawToBitmap` into one threw
`DrawToBitmap references missing bitmap`, taking the whole document down — there is no "before"
frame to show. Each document below draws a red-and-blue component's capture mirrored, so a capture
reads blue-then-red; frames are zoomed 4×.

| render | after |
| --- | --- |
| `capture-by-id` — a 40 px target, then a 60 px canvas whose draw modifier calls a function capturing the target by id into a buffer declared with the target's id, drawn mirrored over its own 60 px: sized to the target, the capture stretches and its halves meet in the middle | ![capture by id](after-capture-by-id.png) |
| `capture-active` — a 40 px box whose draw modifier captures its own content (a red-and-blue canvas) into a buffer declared without a component, and draws only the mirrored capture | ![capture active](after-capture-active.png) |

The custom-visibility case — an exit function capturing the animating component — is in
[`../cmp-custom-visibility-animation/`](../cmp-custom-visibility-animation/README.md).

Regenerate with:

```
RC_LAYOUT_EVIDENCE_DIR=renders ./gradlew :rc-player-compose:jvmTest --tests '*RcComponentOffscreenBufferRenderTest*'
```

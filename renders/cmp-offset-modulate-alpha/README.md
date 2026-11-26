# CMP: an offset child of a `ModulateAlpha` layer

A box with `graphicsLayer(alpha = 0.5f, compositingStrategy = …)`, 220×140, holding a blue 140×140
box and a red 140×140 box moved by `offset(x = 80)`. The documents are written by AndroidX's
creation API (`remote-creation-compose` 1.0.0-alpha24). Each strip shows `Auto`, `Offscreen` and
`ModulateAlpha` from left to right, 300×160 at density 1, over a checkerboard.

The CMP player applied `RcOffsetModifier` as Compose's lambda `Modifier.offset { }`, which places the
child with a layer of its own. On Skiko a `ModulateAlpha` layer fades only its own draw calls
(`SkiaBackedCanvas.alphaMultiplier`) and draws a child layer into the raw canvas at full alpha, so
the offset child came out opaque on the JVM and in the browser. Android's render nodes pass the
alpha on, so AndroidX's player fades it.

The player still places an offset with a layer by default, so an animated offset moves without
redrawing its ancestors. Only an offset drawn into a `ModulateAlpha` layer on Skiko (JVM, Wasm,
Apple) is placed without one, and only while that layer's alpha is below 1 and it has no blur: a
blurred layer composites through a paint that carries its alpha, so a child layer gets the alpha
once there, and a child placed without a layer would get it twice. The player decides this each
time the offset is placed, so an alpha or blur that animates is followed. On Android it always
keeps the layer.

Without that layer, a child placed with an offset inside a fading layer is part of the layer's
recording: if it redraws often (a clock-driven canvas, say), the whole `ModulateAlpha` layer is
recorded again each time.

| lane | strip |
| --- | --- |
| AndroidX embedded player (Robolectric) | ![androidx](androidx-embedded.png) |
| CMP JVM, before | ![cmp before](cmp-before.png) |
| CMP JVM, after | ![cmp after](cmp-after.png) |
| Wasm (Chromium), before | ![wasm before](wasm-before.png) |
| Wasm (Chromium), after | ![wasm after](wasm-after.png) |

Before, only the third cell differs from AndroidX: the red box is opaque. After, it is half
transparent in every cell. Where the two boxes overlap, `ModulateAlpha` now fades each draw on its
own, so the blue shows through the red, as in `renders/cmp-compositing-strategy/`. The Robolectric
lane draws that overlap as `Offscreen` would. That is a separate divergence, and this change does
not touch it.

## Known limitations

Only offsets are fixed. Any other child that gets a layer of its own still misses a `ModulateAlpha`
parent's alpha on Skiko and is drawn opaque:

- a clipped child (`RcClipRectModifier`, `RcRoundedClipRectModifier`);
- a scroll container;
- a nested `RcGraphicsLayerModifier`;
- a component offscreen buffer (`RcComponentOffscreen.kt`);
- a visibility transition (`RcVisibilityTransition.kt`);
- an image, which is drawn inside `clipToBounds()`;
- a component with a `MEASURE` layout compute, which is wrapped in `clipToBounds()`;
- an embedded document, whose player is wrapped in `clipToBounds()` (`RcEmbeddedDocuments.kt`);
- a marquee, because `basicMarquee` places its content with a layer.

`RcGraphicsLayerCompositingRenderTest.aClippedChildOfAModulateAlphaLayerStillMissesItsAlpha` pins
the clip case as it is drawn today, and
`anOffsetChildOfANestedModulateAlphaLayerIsFadedByTheInnerOne` the nested layer: the inner layer
misses the outer alpha, so its offset child is faded by the inner one alone.

With a blur on the `ModulateAlpha` layer, Skiko fades the layer's own draws twice (to a quarter
at half alpha) where AndroidX fades them once. The offset child is faded once, as in AndroidX.
`anOffsetChildOfABlurredModulateAlphaLayerIsFadedOnce` pins both.

All of these come from Compose Multiplatform's Skiko `GraphicsLayer`, not from this player.

Embedded documents (`RcEmbeddedDocuments.kt` wrapping `RcComposePlayerResolved` in `clipToBounds()`)
and the other child layers the player inserts (`RcClipRectModifier`, `RcRoundedClipRectModifier`,
scroll containers, `RcComponentOffscreen` captures, `MEASURE` layout-compute `clipToBounds()`, and
`RcVisibilityTransition`'s `AnimatedVisibility`) all start their descendants with `null` for
`LocalRcModulateAlphaLayer`. Without a blur on the host `ModulateAlpha` layer those child layers
miss the host's alpha, as the list above says, and nothing inside them is faded by it. With a blur,
Skiko composites the host layer through a paint that carries the alpha, and the whole child layer
is faded once. Either way an offset inside keeps its own layer (and an offset written before a clip
or scroll on the same node still sees the enclosing layer so pre-clip drawing is faded as before).
`RcEmbeddedModulateAlphaLayerTest` and `RcPlayerLayersModulateAlphaTest` pin the layer each offset
is drawn into, subtree retention, and the pixels.

## Reproducing

`inputs/` holds the three documents and the harness manifest. They were written on Robolectric
through AndroidX's `captureSingleRemoteDocument` (the `androidxDocument` helper of
`:rc-creation-coexistence-check`) at 300×160 and 160 dpi:

```kotlin
RemoteBox(
  RemoteModifier.graphicsLayer(alpha = 0.5f.rf, compositingStrategy = strategy)
    .size(220.rdp, 140.rdp)
) {
  RemoteBox(RemoteModifier.size(140.rdp).background(Color(0xFF0000FF).rc))
  RemoteBox(RemoteModifier.offset(x = 80.rdp).size(140.rdp).background(Color(0xFFFF0000).rc))
}
```

The AndroidX and CMP lanes read them through the repository's harnesses, given absolute paths:

```sh
./gradlew :third-party-rc-embedded-player:testDebugUnitTest --rerun \
  --tests '*RcEmbeddedRenderHarness*' -Prc.embedded.input=<inputs> -Prc.embedded.output=<dir>
./gradlew :rc-player-compose:jvmTest --rerun \
  --tests '*RcCmpRenderHarness*' -Prc.cmp.input=<inputs> -Prc.cmp.output=<dir>
```

For the Wasm lane, copy the `.rc` files into `:rc-player-wasm:wasmPlayerTestDist`'s output
(`rc-player/wasm/build/wasmTestDist`). Then `tools/wasm-shot.mjs <dist> <out> <id>...` opens
`index.html?src=<id>.rc&handoffDelayMs=0` in Playwright's Chromium and takes a screenshot.
`tools/strip.mjs <out.png> <cell.png>...` lays the cells side by side over the checkerboard. Both
tools use `scripts/wasm-smoke`'s `node_modules` (`npm ci` there first).

The Wasm strips are byte-identical to the CMP ones because the pixels are: both lanes rasterize
with Skia, and every edge here is axis-aligned on whole pixels, so there is nothing for the two
backends to anti-alias differently. Only the PNG encoding of the raw captures differs. The raw
Playwright captures are in `raw/`.

The regression tests are in `RcGraphicsLayerCompositingRenderTest`:
`anOffsetChildIsFadedWithItsParentLayer` covers the fix.
`anOffsetChildOfABlurredModulateAlphaLayerIsFadedOnce` and
`anOffsetChildStaysFadedOnceWhileTheBlurChanges` cover a blurred layer, and
`thePressHandlerOffsetsOfAModulateAlphaChildAreFaded` and
`anOffsetThatMovesAfterTheFirstFrameIsStillFaded` cover the press handlers' offsets and a moving
offset. `anOffsetChildFollowsTheNextDocumentsAlpha` covers a host's next document with an equal
layer. `anOffsetKeepsItsLayerOutsideAModulateAlphaLayer`
checks, through the player, that an offset outside one keeps its layer. `RcModulateAlphaLayerTest`
pins what pixels cannot show: an alpha of 1 keeps the offset's layer, which layer an offset before
or after the component's own is drawn into, and that placement is redone when new readers are bound
or the decision flips, not on every frame of a moving alpha.

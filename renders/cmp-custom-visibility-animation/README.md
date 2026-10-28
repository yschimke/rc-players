# cmp-custom-visibility-animation — a CUSTOM exit runs the document's function

remote-core 1.0.0-alpha22 ("Add custom visibility animation support", androidx `b34eed8a88b`) lets
an `AnimationSpec` name a `FloatFunctionDefine` for a `CUSTOM` enter or exit. The function is called
each frame with (progress, width, height, x, y, component) and draws the component's content
(`DrawContent`) transformed, clipped or faded, plus anything else. The CMP player did not read the
spec's trailing function ids, and a `CUSTOM` exit fell to AndroidX's `ParticleAnimation` branch,
which hides the component at once.

A 120 × 80 card whose exit function scales it toward its centre, wipes it from the right and draws
an orange bar on the wipe's edge, at 0, 75, 150, 225 and 300 ms of a 300 ms linear exit:

| render | before | after |
| --- | --- | --- |
| `custom-exit` | gone after the first frame | scaled, wiped and edged by the function, then gone |

The "before" strip is the `ParticleAnimation` branch the old player took for the unknown animation
value; the old codec also read the spec as seven words and misread its two function-id words as
the next operations.

Regenerate with:

```
RC_LAYOUT_EVIDENCE_DIR=<dir> ./gradlew :rc-player-compose:jvmTest --tests '*RcCustomVisibilityAnimationRenderTest*'
```

## A custom exit that captures its component into an offscreen buffer

alpha22 also lets the function draw the component through a *component offscreen buffer*:
`createOffscreenBitmap(id)` declares a bitmap with no size of its own
(`BitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER`), `drawComponentToBitmap` sizes it to the component
and paints the component into it, and the function then draws that bitmap however it likes. The CMP
player had no bitmap for such a buffer, so running these functions threw
`DrawToBitmap references missing bitmap` on the exit's first frame — there is no "before" frame to
show.

A 30 × 20 red-and-blue component (after a 10 px spacer) whose exit captures it by the function's
component argument and draws the capture mirrored, shrinking toward its centre; the component as
shown, then 0, 75, 150, 225 and 300 ms into a 300 ms linear exit, each frame zoomed 4×:

| render | after |
| --- | --- |
| `offscreen-capture-exit` | ![offscreen capture exit](after-offscreen-capture-exit.png) |

Regenerate with (the test writes into `cmp-custom-visibility-animation/` and
`cmp-component-offscreen-buffer/` under the directory it is given):

```
RC_LAYOUT_EVIDENCE_DIR=renders ./gradlew :rc-player-compose:jvmTest --tests '*RcComponentOffscreenBufferRenderTest*'
```

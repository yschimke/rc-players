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

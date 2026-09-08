# cmp-fontscale — the host font scale reaching both text operations the same way

The before/after for the fix to [#75](https://github.com/yschimke/rc-players/issues/75):
`RcTextLayout` divided a document's wire pixels by density alone on the way back to `sp`, leaving
the host's font scale applied a second time. `CoreText` divided by both and was correct.

One string at a literal 40px wire `fontSize`, density 2.0, rendered through each operation at
`fontScale` 1.0 and 2.0. Ink width in pixels:

| render | before | after |
| --- | --- | --- |
| `coretext-fs1_0` | 349 | 349 |
| `coretext-fs2_0` | 349 | 349 |
| `textlayout-fs1_0` | 349 | 349 |
| `textlayout-fs2_0` | **697** | **349** |

Exactly one render moves, which is the whole claim: `CoreText` was already right at both scales,
`RcTextLayout` was already right at `fontScale = 1` — which is why nothing caught this — and only
`RcTextLayout` at a non-unit scale changes.

349 is the correct answer at every scale here, because the wire carries **pixels**. A player asked
to draw the same document at a larger host font scale draws it identically unless the *document*
asked for the scale, which is what a `RemoteDensity.Host` capture does by computing its sizes from
the player's own `FONT_SIZE` variable. Those sizes already carry the scale, damped by Android's
non-linear sp curve — a 44sp headline is 50.4px at `fontScale` 2.0, not 88 — so re-applying it gave
100.8px on exactly the large text that curve exists to protect.

Regenerate by rendering `RcFontScaleRenderTest`'s two documents through `ImageComposeScene` at
`Density(2f, fontScale)`; the assertions in that test pin the same numbers without the PNGs.

# Edge button label missing on the CMP player's first frame

Evidence for `RcSettledLayout`, which settles `ComponentValue` geometry before the player's first
frame is laid out.

`edgebutton-first-frame.png` shows six of the 64 `edgebutton__*` corpus documents
(`scripts/rc-catalog-corpus/corpus`), each at its manifest size, as three captures:

| column | what it is |
| --- | --- |
| CMP frame 0 before | `RcCmpRenderHarness` (`scene.render(0)`) on `main` |
| CMP frame 0 after | the same harness with this change |
| AndroidX embedded | `RcEmbeddedRenderHarness`, the reference lane |

## What was wrong

The edge button publishes its own measured width as a `ComponentValue` and caps its label with
`widthIn(max = f(width))`. Composition resolves that modifier before anything has been measured,
so on the first frame the width was 0 and the label had no room at all. The value was correct from
frame 1, but the label row then animated open from zero width over the default 300 ms bounds spec,
so the label appeared one glyph at a time. AndroidX re-measures until `ComponentValue` stops moving
before it draws anything, so it never shows either state.

## Numbers

Whole corpus (701 documents rendered), frame 0, `main` vs this change:

- **64 changed, all `edgebutton__*`.** The only other differences were the five indeterminate
  circular progress documents. Their frame 0 differs between two runs of the *same* build, because
  it reads the wall-clock "time since load" system variable, so that is noise and not this change.
- **pixelmatch against AndroidX embedded** (`scripts/design-artifacts/rc-lane-ab-score.mjs`, over the
  64 changed documents): clean 0 → 32, mean mismatch 0.78% → 0.27%, worst 2.28% → 1.26%. No
  document got worse.
- **Frame 0 vs the settled frame** (frames 0–5 at 60 Hz, compared ARGB for ARGB): documents whose
  first frame differed from their settled frame went from 61 to 5 of the 686 that bind a
  `ComponentValue`. The remaining 5 are the progress documents above, which animate.

The remaining differences from AndroidX on the disabled variants come from the label's alpha, which
was hidden while the label was missing. They are not part of this change.

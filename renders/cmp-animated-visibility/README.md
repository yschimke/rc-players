# CMP visibility transitions through `AnimatedVisibility`

`enter-transitions.png` shows a 40×40 component entering a 120×60 box, sampled at 0 to 400 ms after
a click switches it from `GONE` to `VISIBLE`, with a 300 ms `CUBIC_STANDARD` visibility animation.
"before" is the hand-written transition; "after" is Compose's `AnimatedVisibility`.

- **`SLIDE_LEFT`:** the component now slides in from one of its own widths away, which is how
  Compose's `slideInHorizontally` measures a slide. The old transition travelled the parent's width
  (120 px), so it stayed off screen for the first ~120 ms. This is a deliberate divergence; see
  "prefer Compose UI features" in `docs/design/RC_CMP_WASM_PLAYER.md`.
- **`ROTATE`:** the same fade, scale and full turn. The rotation now rides on the enter transition's
  own clock.

The CMP conformance run is unchanged by this: all 361 golds score as before, advisory rasters
included.

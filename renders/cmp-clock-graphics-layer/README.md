# Clock-bound graphics layer

A 10 px red block under a graphics layer whose `TRANSLATION_X` is a float expression reading
`ANIMATION_TIME`, so its left edge is the document time in seconds. Each strip is one
`ImageComposeScene` driven by its frame clock to 0, 10, 20, 30 and 40 s (5x nearest-neighbour).

- `clock_bound_translation-before.png`: the layer resolved its value once, during composition, and
  the component was skipped on every later recomposition, so the block holds its first-frame pose.
- `clock_bound_translation-after.png`: the layer reads the frame time the layout branch publishes
  after `beginFrame`, and draws each frame's value with no lag.

Regenerate with
`RC_LAYOUT_EVIDENCE_DIR=<dir> ./gradlew :rc-player-compose:jvmTest --tests '*RcGraphicsLayerClockRenderTest.writeClockBoundLayerEvidence*' --rerun`
and stitch the five frames.

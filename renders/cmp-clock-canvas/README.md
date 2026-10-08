# Clock-driven layout canvas

A wireframe cube authored with the creation DSL (`RemoteComposeWriter`, remote-creation-core
1.0.0-alpha20): a time-driven `matrixExpression` (`ROT_Y` by `animationTime() * 90`, `ROT_X` 25)
rotates eight vertices through `addMatrixMultiply`, and twelve `drawLine`s join them. The draw list
sits in a canvas inside the layout tree (root → box → canvas). Frames from an `ImageComposeScene`
on its frame clock at 0.2, 0.6 and 1.0 s.

- `cube_rotate_canvas-before.png`: the canvas drew once and held that pose. Its draw block read
  nothing that changed between frames, and a time-only document does not raise
  `invalidationVersion`, so the layout canvas was never redrawn. The same draw list as a raw
  document (no layout) rotated.
- `cube_rotate_canvas-after.png`: the canvas reads the frame time the layout branch publishes
  after `beginFrame`, so it redraws each frame with that frame's values.

The pinning test is `RcCanvasClockRenderTest`.

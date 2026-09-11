# Dynamic dimension constraints

`edge-button-before.png` and `edge-button-after.png` render the same captured 384×112
`RemoteEdgeButton` document through the vendored Compose embedded player at density 2. The document
constrains its label row with an expression derived from `componentWidth()`.

Before the fix, `widthIn` reads the core-resolved output field. Its expression-backed maximum is
still zero when Compose builds the modifier, so the label row collapses while the shaped background
continues to draw. After the fix, the player resolves the raw source expression reactively and the
label remains visible.

| Before | After |
| --- | --- |
| ![EdgeButton without its label](edge-button-before.png) | ![EdgeButton with its label](edge-button-after.png) |

Tracked for retirement against AndroidX in [#98](https://github.com/yschimke/rc-players/issues/98).

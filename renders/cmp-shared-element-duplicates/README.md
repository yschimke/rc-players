# Nested duplicate shared-element ids

Evidence for the CMP fix that selects one component per shared-element animation id before applying
Compose `sharedBounds`.

`after.png` is the initial 200×200 frame of the conformance gold
`layout/state_layout_shared_element_nested_ids`, rendered by `RcCmpRenderHarness`. The three boxes
on the left all declare animation id `700`; AndroidX's last-component-wins collection selects the
innermost box as the shared element.

There is no before raster. Before the fix, Compose loops in `LayoutNode.replace` during initial
measurement, never returns from `setContent`, and eventually exhausts the conformance worker's
heap. GitHub Actions run `35778015450`, attempts 1 and 2, both fail that way at gold 271/364.

The fixed player renders this frame, and the complete 364-gold CMP conformance run finishes.

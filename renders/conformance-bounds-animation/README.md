# Layout bounds animated without the document asking

Evidence for the fix in `RcComposePlayer` that makes `animateRcBounds` apply only when a layout node
declares an animation spec.

All three are the `resize_1` step of the corpus gold `layout/column_basic` — a viewport resize from
300×400 to 600×800, painted for the two frames `CONFORMANCE_FORMAT.md` §3 prescribes.

| file | what it is |
| --- | --- |
| `reference.png` | the gold's own reference image: the document at the full 600×800 viewport |
| `before.png` | the player animating the resize over 300ms, caught 33ms in at 302×403 |
| `after.png` | the player with the fix, matching the reference |

The document declares no animation spec, so `before.png` is the player inventing one. A gold that
*does* declare one — `layout/animation_box_offset` — still interpolates, and is asserted frame by
frame across its resize.

Reproduced by dumping `Observation.Raster` from `CmpEngine` at that step with and without the fix;
the conformance lane itself scores the same frames as 633 raster checks, none of which regressed.

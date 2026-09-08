# Loom macro operation conformance

These are the same AndroidX-authored `PatternDefine` + `PatternInflation` bytes rendered at 96×64
and density 1. The definition paints a green rectangle over the lavender background.

| AndroidX View | Upstream AndroidX embedded | Vendored embedded Android |
| --- | --- | --- |
| ![AndroidX View macro](view.png) | ![Upstream embedded macro](upstream-embedded.png) | ![Vendored embedded Android macro](vendored-embedded.png) |

| Vendored embedded JVM | CMP JVM |
| --- | --- |
| ![Vendored embedded JVM macro](vendored-jvm.png) | ![CMP JVM macro](cmp-jvm.png) |

All four AndroidX-core-backed players parse the experimental-profile stream but leave the macro
body unexpanded in this top-level form. CMP materializes it and paints the rectangle. This is
recorded as an upstream behavioral difference rather than hiding it behind an Android reference
chosen as the sole oracle. The repository's pinned AndroidX snapshot produces the same pixels as
the alpha18 release, so this difference is not fixed on that tracked upstream line.

Regenerate this evidence with the commands in the neighboring
[`bitmap-font`](../bitmap-font/README.md) directory.

# Conformance run — 2026-09-23

Every player in this repository, scored against the AndroidX RemoteCompose conformance corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on `vendor/androidx-rc-conformance`; it is deliberately not in the main line.

Measured at `f0d2a9ad56cbb689513d8eaf882e5c397d820b78` on `agent/appkit-renderer-fixes`.

## Lanes

| lane | core golds | pass rate | extended | raster disagreements | errored |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cmp` | — | — | — | — | *not measured* |
| `androidx-jvm` | — | — | — | — | *not measured* |
| `native-appkit` | — | — | — | — | *not measured* |
| `typescript` | 352 / 353 | 99.7% | 8 / 8 | 174 | 0 |

## `typescript` by subsystem

| subsystem |  | passed | pass rate |
| --- | --- | ---: | ---: |
| layout | `████████████████████` | 178 / 179 | 99.4% |
| animationspec | `████████████████████` | 11 / 11 | 100.0% |
| canvas | `████████████████████` | 12 / 12 | 100.0% |
| clock | `████████████████████` | 10 / 10 | 100.0% |
| colortheme | `████████████████████` | 10 / 10 | 100.0% |
| conditionals | `████████████████████` | 10 / 10 | 100.0% |
| dataoperations | `████████████████████` | 10 / 10 | 100.0% |
| expressions | `████████████████████` | 16 / 16 | 100.0% |
| interactivity | `████████████████████` | 12 / 12 | 100.0% |
| loom | `████████████████████` | 10 / 10 | 100.0% |
| matrixmath | `████████████████████` | 11 / 11 | 100.0% |
| particles | `████████████████████` | 10 / 10 | 100.0% |
| pathoperations | `████████████████████` | 10 / 10 | 100.0% |
| scheduling | `████████████████████` | 10 / 10 | 100.0% |
| semantics | `████████████████████` | 10 / 10 | 100.0% |
| shaders | `████████████████████` | 10 / 10 | 100.0% |
| textoperations | `████████████████████` | 10 / 10 | 100.0% |
| wire | `████████████████████` | 10 / 10 | 100.0% |

## `typescript` by probe

Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can produce dozens. The last column is the runner admitting it has no seam for that channel — reported as a failure on purpose, so a player cannot score well by declining to look.

| probe | diffs | of which the lane cannot observe |
| --- | ---: | ---: |
| `raster` | 174 | — |
| `tree` | 36 | — |

## Excluded as suspicious

Tagged by the corpus itself as disputed reference behaviour. Left out of the rate rather than counted as failures.

- `collapsible_column_scroll` — CollapsibleColumn decides which child to collapse by measuring against the viewport (CollapsibleColumnLayout.java:302-319), but ColumnLayout then positions the survivors against the virtual scroll dimension (ColumnLayout.java:316-325) and centres against it (ColumnLayout.java:412-414). That dimension is the pre-collapse 240px, so the two surviving 80px children are centred with a (240-160)/2 = 40px top offset: the collapse frees no space, it only moves the content down. The collapsed child is recorded at the end cursor (y=200). No children overlap.
- `collapsible_row_scroll` — Horizontal mirror of collapsible_column_scroll: the collapse is decided against the viewport, but RowLayout positions the survivors against the pre-collapse virtual scroll dimension (RowLayout.java:336-347, 472-474), leaving a (240-160)/2 = 40px offset the collapse never reclaims. Separately, the collapsed child is recorded at x=40, the same position as the first surviving child, because RowLayout.java:546-550 assigns x before the isGone check. That is a shared coordinate, not a painted overlap: the child is GONE and is never drawn.
- `flow_max_lines` — FlowLayout.segmentComponents runs several times over a single MeasurePass and never clears the GONE flags an earlier pass set -- contrast CollapsibleRowLayout.java:253, which calls clearVisibilityOverride first -- and it treats a GONE child as 0x0 (FlowLayout.java:233-235). The fifth child, correctly dropped by maxLines=2, is therefore re-segmented into the last row and recorded spanning x=200..300, outside the 260px container. It stays GONE and is never painted, so the defect is confined to the reported geometry.

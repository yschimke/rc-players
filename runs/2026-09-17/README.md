# Conformance run — 2026-09-17

Every player in this repository, scored against the AndroidX RemoteCompose conformance corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on `vendor/androidx-rc-conformance`; it is deliberately not in the main line.

Measured at `5bd9270744d4e9d5e27062c10ffe80b161973e1f` on `agent/reference-lane-tree`.

## Lanes

**A zero here is not a verdict.** `native-appkit` observes only `raster`, and no gold in the corpus asserts raster alone — so such a lane passes none by construction. Its raster column is the number it exists for.

| lane | core golds | pass rate | extended | raster disagreements | errored |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cmp` | 167 / 241 | 69.3% | 0 / 8 | 195 | 2 |
| `androidx-jvm` | 85 / 241 | 35.3% | 0 / 8 | 358 | 1 |
| `native-appkit` | 0 / 241 | 0.0% | 0 / 8 | 368 | 0 |

## Where the subject lane stands alone

Scored above but not compared here: `native-appkit`. A lane only counts as a reference if disagreeing with it is evidence; one that fails nearly every frame would mark every subject failure as shared and leave nothing to act on.

The corpus was generated *by* AndroidX, from its own player. So a gold both lanes fail is most likely the harness failing to observe something, or the reference asserting behaviour no independent player would reproduce — while a gold only the subject lane fails is a finding about the subject lane.

### Golds, against `androidx-jvm`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 84 | Settled. Neither lane disagrees. |
| only `androidx-jvm` passes | **1** | **The work list.** The reference reproduces the gold and the subject does not. |
| only `cmp` passes | 83 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 81 | An expectation that survives neither implementation, or a probe neither lane has. |

The work list, in full:

- `modifier_scroll` — tree ×2

### Frames

| compared against | shared failures | unique to `cmp` |
| --- | ---: | ---: |
| `androidx-jvm` | 194 | 1 |

Unique to `cmp`, in full:

- `canvas_shader_gradient` at `initial`

## `cmp` by subsystem

| subsystem |  | passed | pass rate |
| --- | --- | ---: | ---: |
| layout | `███████████████·····` | 127 / 171 | 74.3% |
| particles | `····················` | 0 / 8 | 0.0% |
| interactivity | `····················` | 0 / 7 | 0.0% |
| canvas | `███████████·········` | 5 / 9 | 55.6% |
| loom | `███████·············` | 2 / 6 | 33.3% |
| animationspec | `····················` | 0 / 3 | 0.0% |
| clock | `····················` | 0 / 3 | 0.0% |
| conditionals | `····················` | 0 / 3 | 0.0% |
| expressions | `█████████████████···` | 12 / 14 | 85.7% |
| pathoperations | `····················` | 0 / 2 | 0.0% |
| dataoperations | `███████████████·····` | 3 / 4 | 75.0% |
| shaders | `█████████████·······` | 2 / 3 | 66.7% |
| colortheme | `████████████████████` | 2 / 2 | 100.0% |
| matrixmath | `████████████████████` | 3 / 3 | 100.0% |
| scheduling | `████████████████████` | 2 / 2 | 100.0% |
| semantics | `████████████████████` | 2 / 2 | 100.0% |
| textoperations | `████████████████████` | 5 / 5 | 100.0% |
| wire | `████████████████████` | 2 / 2 | 100.0% |

## `cmp` by probe

Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can produce dozens. The last column is the runner admitting it has no seam for that channel — reported as a failure on purpose, so a player cannot score well by declining to look.

| probe | diffs | of which the lane cannot observe |
| --- | ---: | ---: |
| `particles` | 660 | — |
| `tree` | 362 | — |
| `raster` | 195 | — |
| `float` | 40 | 5 |
| `trace:handled` | 14 | 14 |
| `int` | 8 | — |
| `ops:counts` | 4 | — |
| `ops:present` | 3 | — |
| `ops:total_glyphs` | 2 | 2 |
| `records:animation_specs` | 2 | 2 |
| `records:component_bindings` | 2 | 2 |
| `records:glyph_runs` | 2 | 2 |
| `trace:branches` | 2 | 2 |
| `float_array:data` | 1 | — |
| `ops:absent` | 1 | — |
| `ops:component_count` | 1 | — |
| `records:anchor_runs` | 1 | 1 |
| `records:paths` | 1 | — |
| `records:tweens` | 1 | — |
| `relation:anchor_runs` | 1 | 1 |
| `trace:host_actions` | 1 | 1 |

## Crashes

A crash is not a conformance gap. These are player bugs on their own terms.

- `image_layout_sizing_options` — ee.schimke.composeai.rcplayer.runtime.RcLayoutException: RcColumnLayout requires LayoutComponentContent
- `path_tween_morph` — java.lang.IllegalArgumentException: Missing path 10

## Excluded as suspicious

Tagged by the corpus itself as disputed reference behaviour. Left out of the rate rather than counted as failures.

- `collapsible_column_scroll`
- `collapsible_row_scroll`
- `flow_max_lines`

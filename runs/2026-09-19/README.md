# Conformance run — 2026-09-19

Every player in this repository, scored against the AndroidX RemoteCompose conformance corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on `vendor/androidx-rc-conformance`; it is deliberately not in the main line.

Measured at `9acfa3906f8e97b3c94c08da44395f74f1a13aef` on `main`.

## Lanes

**A zero here is not a verdict.** `native-appkit` observes only `raster`, and no gold in the corpus asserts raster alone — so such a lane passes none by construction. Its raster column is the number it exists for.

| lane | core golds | pass rate | extended | raster disagreements | errored |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cmp` | 190 / 241 | 78.8% | 0 / 8 | 207 | 0 |
| `androidx-jvm` | 132 / 241 | 54.8% | 0 / 8 | 272 | 1 |
| `native-appkit` | 0 / 241 | 0.0% | 0 / 8 | 364 | 0 |
| `typescript` | 199 / 241 | 82.6% | 6 / 8 | 224 | 0 |

## Where the subject lane stands alone

Scored above but not compared here: `native-appkit`. A lane only counts as a reference if disagreeing with it is evidence; one that fails nearly every frame would mark every subject failure as shared and leave nothing to act on.

The corpus was generated *by* AndroidX, from its own player. So a gold both lanes fail is most likely the harness failing to observe something, or the reference asserting behaviour no independent player would reproduce — while a gold only the subject lane fails is a finding about the subject lane.

### Golds, against `androidx-jvm`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 126 | Settled. Neither lane disagrees. |
| only `androidx-jvm` passes | **6** | **The work list**, split below into the player's gaps and this runner's. |
| only `cmp` passes | 64 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 53 | An expectation that survives neither implementation, or a probe neither lane has. |

**6 the player: the reference draws or reports it and this one does not.**

- `collapsible_column_weights` — tree ×12, raster ×3
- `collapsible_row_weights` — tree ×12, raster ×3
- `fitbox_child_visibility` — tree ×18, raster ×3
- `modifier_scroll` — tree ×2
- `state_layout_child_visibility` — tree ×18, raster ×3
- `state_layout_padding_container` — tree ×6, raster ×3

### Golds, against `typescript`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 176 | Settled. Neither lane disagrees. |
| only `typescript` passes | **29** | **The work list**, split below into the player's gaps and this runner's. |
| only `cmp` passes | 14 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 30 | An expectation that survives neither implementation, or a probe neither lane has. |

**24 the player: the reference draws or reports it and this one does not.**

- `canvas_shader_gradient` — raster ×1
- `collapsible_column_weights` — tree ×12, raster ×3
- `collapsible_row_weights` — tree ×12, raster ×3
- `core_text_autosize_min_clamped` — tree ×6, raster ×3
- `core_text_autosize_multiline` — tree ×3, raster ×3
- `core_text_multiline_wrap` — tree ×3, raster ×3
- `core_text_overflow_ellipsis` — raster ×3, tree ×1
- `core_text_simple` — tree ×3, raster ×3
- `data_list_float_dynamic_update` — float_array:data ×1
- `expr_integer_bitwise_ops` — int ×8
- `interactivity_click_state_mutation` — trace:handled ×3, float ×1
- `interactivity_click_toggle_state` — float ×1, trace:handled ×2
- `interactivity_slider_touch_expression` — float ×2
- `interactivity_touch_coordinate_tracking` — float ×2
- `modifier_scroll` — tree ×2
- `particle_boundary_bounce` — raster ×9, particles ×96
- `particle_deterministic_seeding` — raster ×3, particles ×32
- `particle_drag_damping` — raster ×8, particles ×140
- `particle_gravity_fountain` — raster ×9, particles ×112
- `particle_lifetime_decay` — raster ×5, particles ×96
- `particle_linear_drift` — raster ×6, particles ×60
- `particle_radial_burst` — raster ×7, particles ×72
- `text_on_circle_stream_alignment` — records:glyph_runs ×1, ops:present ×1, ops:total_glyphs ×1
- `text_on_path_glyph_placement` — records:glyph_runs ×1, ops:total_glyphs ×1, raster ×1

**5 this runner: every disagreement is a probe it cannot observe.** Not a player finding — the lane has no seam for these channels and says so rather than scoring them as passes.

- `animation_spec_field_roundtrip` — records:animation_specs ×1
- `conditional_comparison_operators` — trace:branches ×1
- `conditional_nested_branches` — trace:branches ×1
- `interactivity_click_host_action` — trace:handled ×1, trace:host_actions ×1
- `interactivity_hit_testing_bounds` — trace:handled ×5

### Frames

| compared against | shared failures | unique to `cmp` |
| --- | ---: | ---: |
| `androidx-jvm` | 200 | 7 |
| `typescript` | 173 | 34 |

Unique to `cmp`, in full:

- `canvas_shader_gradient` at `initial`
- `collapsible_column_weights` at `initial`
- `collapsible_column_weights` at `resize_0`
- `collapsible_column_weights` at `resize_1`
- `collapsible_row_weights` at `initial`
- `collapsible_row_weights` at `resize_0`
- `collapsible_row_weights` at `resize_1`

## `cmp` by subsystem

| subsystem |  | passed | pass rate |
| --- | --- | ---: | ---: |
| layout | `█████████████████···` | 143 / 171 | 83.6% |
| particles | `····················` | 0 / 8 | 0.0% |
| interactivity | `····················` | 0 / 7 | 0.0% |
| animationspec | `····················` | 0 / 3 | 0.0% |
| canvas | `█████████████·······` | 6 / 9 | 66.7% |
| clock | `····················` | 0 / 3 | 0.0% |
| conditionals | `····················` | 0 / 3 | 0.0% |
| expressions | `█████████████████···` | 12 / 14 | 85.7% |
| dataoperations | `███████████████·····` | 3 / 4 | 75.0% |
| shaders | `█████████████·······` | 2 / 3 | 66.7% |
| colortheme | `████████████████████` | 2 / 2 | 100.0% |
| loom | `████████████████████` | 6 / 6 | 100.0% |
| matrixmath | `████████████████████` | 3 / 3 | 100.0% |
| pathoperations | `████████████████████` | 2 / 2 | 100.0% |
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
| `raster` | 207 | — |
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
| `records:anchor_runs` | 1 | 1 |
| `relation:anchor_runs` | 1 | 1 |
| `trace:host_actions` | 1 | 1 |

## Excluded as suspicious

Tagged by the corpus itself as disputed reference behaviour. Left out of the rate rather than counted as failures.

- `collapsible_column_scroll`
- `collapsible_row_scroll`
- `flow_max_lines`

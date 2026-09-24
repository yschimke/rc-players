# Conformance run — 2026-09-24

Every player in this repository, scored against the AndroidX RemoteCompose conformance corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on `vendor/androidx-rc-conformance`; it is deliberately not in the main line.

Measured at `372db787f47649dc18662ec7d6dafd3b2cf37572` on `main`.

## Lanes

| lane | core golds | pass rate | extended | raster disagreements | errored |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cmp` | 316 / 353 | 89.5% | 7 / 8 | 116 | 20 |
| `androidx-jvm` | 132 / 353 | 37.4% | 0 / 8 | 269 | 43 |
| `native-appkit` | 309 / 353 | 87.5% | 7 / 8 | 227 | 0 |
| `typescript` | 352 / 353 | 99.7% | 8 / 8 | 174 | 0 |

## Where the subject lane stands alone

Scored above but not compared here: `native-appkit`. A lane only counts as a reference if disagreeing with it is evidence; one that fails nearly every frame would mark every subject failure as shared and leave nothing to act on.

The corpus was generated *by* AndroidX, from its own player. So a gold both lanes fail is most likely the harness failing to observe something, or the reference asserting behaviour no independent player would reproduce — while a gold only the subject lane fails is a finding about the subject lane.

### Golds, against `androidx-jvm`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 132 | Settled. Neither lane disagrees. |
| only `androidx-jvm` passes | **0** | **The work list**, split below into the player's gaps and this runner's. |
| only `cmp` passes | 191 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 38 | An expectation that survives neither implementation, or a probe neither lane has. |

### Golds, against `typescript`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 323 | Settled. Neither lane disagrees. |
| only `typescript` passes | **37** | **The work list**, split below into the player's gaps and this runner's. |
| only `cmp` passes | 0 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 1 | An expectation that survives neither implementation, or a probe neither lane has. |

**17 the player: the reference draws or reports it and this one does not.**

- `animation_box_offset` — tree ×24, raster ×2
- `animation_measure_transition` — tree ×12, raster ×2
- `animation_state_3_states` — tree ×39, raster ×4
- `animation_state_row_to_column` — tree ×58, raster ×4
- `animation_state_transition` — tree ×19, raster ×4
- `conditional_skip_api_gate` — ops:present ×1, ops:absent ×1, ops:counts ×4
- `expr_integer_bitwise_ops` — int ×8
- `fitbox_fit` — tree ×8
- `interaction_swipe_scroll_decay` — raster ×5, tree ×4
- `interactivity_hit_testing_bounds` — trace:handled ×2
- `modifier_multi_click` — raster ×7, tree ×8
- `state_layout_expandable_card` — tree ×12
- `state_layout_nested_boxes_control` — tree ×8
- `state_layout_row_to_column` — tree ×29
- `state_layout_shared_element_across_states` — tree ×6
- `state_layout_switch_visibility` — tree ×15
- `text_on_circle_stream_alignment` — records:glyph_runs ×1, ops:present ×1, ops:total_glyphs ×1

**20 this runner: every disagreement is a probe it cannot observe.** Not a player finding — the lane has no seam for these channels and says so rather than scoring them as passes.

- `canvas_clip_path_and_skew` — ops:present ×1, float ×1
- `canvas_draw_bitmap_and_offscreen` — ops:present ×1, float ×1
- `canvas_draw_text_run_and_content` — ops:present ×1, float ×1
- `data_id_lookup_indirection` — ops:present ×1, text ×1
- `expr_rpn_comprehensive_float_operators` — ops:present ×1, float ×1
- `interactivity_touch_down_up_cancel` — ops:present ×1, float ×1
- `interactivity_value_string_and_int_actions` — ops:present ×1, text ×1
- `loom_macro_block_slot_injection` — float ×1
- `loom_macro_parameter_remapping` — float ×1
- `matrix_3d_pipeline_operations` — ops:present ×1, float ×1
- `matrix_from_path_tangent_frame` — ops:present ×1, float ×1
- `particle_attractor_radial_field` — ops:present ×1, float ×1
- `particle_compare_conditional_respawn` — ops:present ×1, float ×1
- `path_draw_tween_path_render` — ops:present ×1, float ×1
- `path_expression_polar_parametric` — ops:present ×1, float ×1
- `path_expression_wave_oscillator` — ops:present ×1, float ×1
- `path_tween_morph_interpolation` — ops:present ×1, float ×1
- `text_style_and_layout_text` — ops:present ×1, float ×1
- `wire_component_value_and_custom_layout` — ops:present ×1, float ×1
- `wire_id_lookup_table` — ops:present ×1, float ×1

### Frames

| compared against | shared failures | unique to `cmp` |
| --- | ---: | ---: |
| `androidx-jvm` | 116 | 0 |
| `typescript` | 85 | 31 |

## `cmp` by subsystem

| subsystem |  | passed | pass rate |
| --- | --- | ---: | ---: |
| layout | `██████████████████··` | 165 / 179 | 92.2% |
| canvas | `█████████████·······` | 8 / 12 | 66.7% |
| pathoperations | `████████████········` | 6 / 10 | 60.0% |
| interactivity | `███████████████·····` | 9 / 12 | 75.0% |
| expressions | `██████████████████··` | 14 / 16 | 87.5% |
| loom | `████████████████····` | 8 / 10 | 80.0% |
| matrixmath | `████████████████····` | 9 / 11 | 81.8% |
| particles | `████████████████····` | 8 / 10 | 80.0% |
| wire | `████████████████····` | 8 / 10 | 80.0% |
| conditionals | `██████████████████··` | 9 / 10 | 90.0% |
| dataoperations | `██████████████████··` | 9 / 10 | 90.0% |
| textoperations | `██████████████████··` | 9 / 10 | 90.0% |
| animationspec | `████████████████████` | 11 / 11 | 100.0% |
| clock | `████████████████████` | 10 / 10 | 100.0% |
| colortheme | `████████████████████` | 10 / 10 | 100.0% |
| scheduling | `████████████████████` | 10 / 10 | 100.0% |
| semantics | `████████████████████` | 10 / 10 | 100.0% |
| shaders | `████████████████████` | 10 / 10 | 100.0% |

## `cmp` by probe

Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can produce dozens. The last column is the runner admitting it has no seam for that channel — reported as a failure on purpose, so a player cannot score well by declining to look.

| probe | diffs | of which the lane cannot observe |
| --- | ---: | ---: |
| `tree` | 288 | — |
| `raster` | 116 | — |
| `ops:present` | 20 | — |
| `float` | 18 | — |
| `int` | 8 | — |
| `ops:counts` | 4 | — |
| `text` | 2 | — |
| `trace:handled` | 2 | — |
| `ops:absent` | 1 | — |
| `ops:total_glyphs` | 1 | — |
| `records:glyph_runs` | 1 | — |

## Crashes

A crash is not a conformance gap. These are player bugs on their own terms.

- `canvas_clip_path_and_skew` — java.lang.IllegalStateException: PathData 1 command at word 0 is not NaN-encoded
- `canvas_draw_bitmap_and_offscreen` — java.lang.IllegalArgumentException: DrawToBitmap references missing bitmap 1
- `canvas_draw_text_run_and_content` — java.lang.IllegalStateException: Check failed.
- `data_id_lookup_indirection` — java.lang.IllegalArgumentException: Required value was null.
- `expr_rpn_comprehensive_float_operators` — ee.schimke.composeai.rcplayer.protocol.RcWireException: Float expression too long at byte 42, opcode=81 (FloatExpression), field=expression.count
- `interactivity_touch_down_up_cancel` — java.lang.IllegalStateException: Container opcode 219 is not renderable
- `interactivity_value_string_and_int_actions` — java.lang.IllegalArgumentException: Missing action text 63
- `loom_macro_block_slot_injection` — ee.schimke.composeai.rcplayer.runtime.RcLinkException: MacroBlock outside MacroCall
- `loom_macro_parameter_remapping` — ee.schimke.composeai.rcplayer.runtime.RcLinkException: Unclosed RcMacroCall container at end of document
- `matrix_3d_pipeline_operations` — ee.schimke.composeai.rcplayer.protocol.RcWireException: Unsupported operation at byte 33, opcode=110, field=opcode
- `matrix_from_path_tangent_frame` — java.lang.IllegalStateException: Missing path 80
- `particle_attractor_radial_field` — java.lang.IllegalArgumentException: Missing particle system 2
- `particle_compare_conditional_respawn` — java.lang.IllegalArgumentException: Missing particle system 1
- `path_draw_tween_path_render` — java.lang.IllegalStateException: Path command at word 0 is invalid
- `path_expression_polar_parametric` — java.lang.IllegalArgumentException: path length must be > 1
- `path_expression_wave_oscillator` — java.lang.IllegalArgumentException: path length must be > 1
- `path_tween_morph_interpolation` — java.lang.IllegalStateException: Path command at word 0 is invalid
- `text_style_and_layout_text` — ee.schimke.composeai.rcplayer.runtime.RcLayoutException: Layout component appears outside a RootLayoutComponent
- `wire_component_value_and_custom_layout` — ee.schimke.composeai.rcplayer.runtime.RcLayoutException: Layout component appears outside a RootLayoutComponent
- `wire_id_lookup_table` — java.lang.IllegalArgumentException: Required value was null.

## Excluded as suspicious

Tagged by the corpus itself as disputed reference behaviour. Left out of the rate rather than counted as failures.

- `collapsible_column_scroll`
- `collapsible_row_scroll`
- `flow_max_lines`

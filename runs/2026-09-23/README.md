# Conformance run — 2026-09-23

Every player in this repository, scored against the AndroidX RemoteCompose conformance corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on `vendor/androidx-rc-conformance`; it is deliberately not in the main line.

Measured at `ee1864392326d3d7c7a21112bc1dd8a83cc1dcf8` on `main`.

## Lanes

| lane | core golds | pass rate | extended | raster disagreements | errored |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cmp` | 236 / 353 | 66.9% | 0 / 8 | 176 | 20 |
| `androidx-jvm` | 132 / 353 | 37.4% | 0 / 8 | 269 | 43 |
| `native-appkit` | 216 / 353 | 61.2% | 4 / 8 | 236 | 0 |
| `typescript` | 352 / 353 | 99.7% | 8 / 8 | 174 | 0 |

## Where the subject lane stands alone

Scored above but not compared here: `native-appkit`. A lane only counts as a reference if disagreeing with it is evidence; one that fails nearly every frame would mark every subject failure as shared and leave nothing to act on.

The corpus was generated *by* AndroidX, from its own player. So a gold both lanes fail is most likely the harness failing to observe something, or the reference asserting behaviour no independent player would reproduce — while a gold only the subject lane fails is a finding about the subject lane.

### Golds, against `androidx-jvm`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 132 | Settled. Neither lane disagrees. |
| only `androidx-jvm` passes | **0** | **The work list**, split below into the player's gaps and this runner's. |
| only `cmp` passes | 104 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 125 | An expectation that survives neither implementation, or a probe neither lane has. |

### Golds, against `typescript`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 236 | Settled. Neither lane disagrees. |
| only `typescript` passes | **124** | **The work list**, split below into the player's gaps and this runner's. |
| only `cmp` passes | 0 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 1 | An expectation that survives neither implementation, or a probe neither lane has. |

**28 this runner: every disagreement is a probe it cannot observe.** Not a player finding — the lane has no seam for these channels and says so rather than scoring them as passes.

- `animation_spec_component_binding` — records:animation_specs ×1, records:component_bindings ×1
- `animation_spec_defaults_and_disable` — records:component_bindings ×1
- `animation_spec_field_roundtrip` — records:animation_specs ×1
- `canvas_clip_path_and_skew` — ops:present ×1, float ×1
- `canvas_draw_bitmap_and_offscreen` — ops:present ×1, float ×1
- `canvas_draw_text_run_and_content` — ops:present ×1, float ×1
- `conditional_comparison_operators` — trace:branches ×1
- `conditional_nested_branches` — trace:branches ×1
- `data_float_array_indexing` — float_array ×1
- `data_id_lookup_indirection` — ops:present ×1, text ×1
- `expr_rpn_comprehensive_float_operators` — ops:present ×1, float ×1
- `interactivity_click_host_action` — trace:handled ×1, trace:host_actions ×1
- `interactivity_hit_testing_bounds` — trace:handled ×5
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
| `androidx-jvm` | 175 | 1 |
| `typescript` | 131 | 45 |

Unique to `cmp`, in full:

- `canvas_shader_gradient` at `initial`

## `cmp` by subsystem

| subsystem |  | passed | pass rate |
| --- | --- | ---: | ---: |
| layout | `█████████████████···` | 149 / 179 | 83.2% |
| interactivity | `██··················` | 1 / 12 | 8.3% |
| clock | `····················` | 0 / 10 | 0.0% |
| particles | `····················` | 0 / 10 | 0.0% |
| pathoperations | `████················` | 2 / 10 | 20.0% |
| scheduling | `████················` | 2 / 10 | 20.0% |
| shaders | `████················` | 2 / 10 | 20.0% |
| canvas | `██████████··········` | 6 / 12 | 50.0% |
| dataoperations | `████████············` | 4 / 10 | 40.0% |
| colortheme | `██████████··········` | 5 / 10 | 50.0% |
| wire | `██████████··········` | 5 / 10 | 50.0% |
| animationspec | `█████████████·······` | 7 / 11 | 63.6% |
| loom | `████████████········` | 6 / 10 | 60.0% |
| conditionals | `██████████████······` | 7 / 10 | 70.0% |
| expressions | `████████████████····` | 13 / 16 | 81.3% |
| matrixmath | `████████████████····` | 9 / 11 | 81.8% |
| textoperations | `████████████████····` | 8 / 10 | 80.0% |
| semantics | `████████████████████` | 10 / 10 | 100.0% |

## `cmp` by probe

Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can produce dozens. The last column is the runner admitting it has no seam for that channel — reported as a failure on purpose, so a player cannot score well by declining to look.

| probe | diffs | of which the lane cannot observe |
| --- | ---: | ---: |
| `particles` | 660 | — |
| `tree` | 336 | — |
| `raster` | 176 | — |
| `ops:present` | 65 | — |
| `float` | 58 | 5 |
| `trace:handled` | 14 | 14 |
| `int` | 8 | — |
| `ops:counts` | 4 | — |
| `ops:total_glyphs` | 2 | 2 |
| `records:animation_specs` | 2 | 2 |
| `records:component_bindings` | 2 | 2 |
| `records:glyph_runs` | 2 | 2 |
| `text` | 2 | — |
| `trace:branches` | 2 | 2 |
| `float_array` | 1 | 1 |
| `float_array:data` | 1 | — |
| `ops:absent` | 1 | — |
| `records:anchor_runs` | 1 | 1 |
| `relation:anchor_runs` | 1 | 1 |
| `trace:host_actions` | 1 | 1 |

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

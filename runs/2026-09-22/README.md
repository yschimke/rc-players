# Conformance run — 2026-09-22

Every player in this repository, scored against the AndroidX RemoteCompose conformance corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on `vendor/androidx-rc-conformance`; it is deliberately not in the main line.

Measured at `e25cf90d9c0e435a8a968ab8215ced886bd50f50` on `main`.

## Lanes

| lane | core golds | pass rate | extended | raster disagreements | errored |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cmp` | — | — | — | — | *not measured* |
| `androidx-jvm` | 132 / 353 | 37.4% | 0 / 8 | 269 | 43 |
| `native-appkit` | 178 / 353 | 50.4% | 3 / 8 | 242 | 0 |
| `typescript` | 352 / 353 | 99.7% | 8 / 8 | 174 | 0 |

## Where the subject lane stands alone

Scored above but not compared here: `native-appkit`. A lane only counts as a reference if disagreeing with it is evidence; one that fails nearly every frame would mark every subject failure as shared and leave nothing to act on.

The corpus was generated *by* AndroidX, from its own player. So a gold both lanes fail is most likely the harness failing to observe something, or the reference asserting behaviour no independent player would reproduce — while a gold only the subject lane fails is a finding about the subject lane.

### Golds, against `typescript`

| outcome | golds | what it means |
| --- | ---: | --- |
| both pass | 132 | Settled. Neither lane disagrees. |
| only `typescript` passes | **228** | **The work list**, split below into the player's gaps and this runner's. |
| only `androidx-jvm` passes | 0 | The subject is ahead here, or the reference cannot drive the timeline. |
| both fail | 1 | An expectation that survives neither implementation, or a probe neither lane has. |

### Frames

| compared against | shared failures | unique to `androidx-jvm` |
| --- | ---: | ---: |
| `typescript` | 152 | 117 |

117 frames are unique to `androidx-jvm` — see the results JSON.

## `androidx-jvm` by subsystem

| subsystem |  | passed | pass rate |
| --- | --- | ---: | ---: |
| layout | `███████████████·····` | 132 / 179 | 73.7% |
| expressions | `····················` | 0 / 16 | 0.0% |
| canvas | `····················` | 0 / 12 | 0.0% |
| interactivity | `····················` | 0 / 12 | 0.0% |
| animationspec | `····················` | 0 / 11 | 0.0% |
| matrixmath | `····················` | 0 / 11 | 0.0% |
| clock | `····················` | 0 / 10 | 0.0% |
| colortheme | `····················` | 0 / 10 | 0.0% |
| conditionals | `····················` | 0 / 10 | 0.0% |
| dataoperations | `····················` | 0 / 10 | 0.0% |
| loom | `····················` | 0 / 10 | 0.0% |
| particles | `····················` | 0 / 10 | 0.0% |
| pathoperations | `····················` | 0 / 10 | 0.0% |
| scheduling | `····················` | 0 / 10 | 0.0% |
| semantics | `····················` | 0 / 10 | 0.0% |
| shaders | `····················` | 0 / 10 | 0.0% |
| textoperations | `····················` | 0 / 10 | 0.0% |
| wire | `····················` | 0 / 10 | 0.0% |

## `androidx-jvm` by probe

Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can produce dozens. The last column is the runner admitting it has no seam for that channel — reported as a failure on purpose, so a player cannot score well by declining to look.

| probe | diffs | of which the lane cannot observe |
| --- | ---: | ---: |
| `raster` | 269 | — |
| `float` | 241 | 158 |
| `tree` | 227 | 20 |
| `ops:present` | 106 | 66 |
| `particles` | 66 | 8 |
| `text` | 21 | 19 |
| `color` | 20 | 14 |
| `trace:handled` | 14 | — |
| `ops:count` | 13 | 13 |
| `int` | 11 | 11 |
| `draw_log:commands` | 7 | 7 |
| `records:components` | 6 | 6 |
| `matrix` | 4 | 4 |
| `ops:counts` | 2 | 2 |
| `ops:total_glyphs` | 2 | 1 |
| `records:animation_specs` | 2 | 2 |
| `records:component_bindings` | 2 | 2 |
| `records:glyph_runs` | 2 | 1 |
| `records:semantics` | 2 | 2 |
| `records:uniforms` | 2 | 2 |
| `trace:branches` | 2 | 2 |
| `float_array` | 1 | — |
| `float_array:data` | 1 | 1 |
| `float_array:dynamic` | 1 | 1 |
| `ops:absent` | 1 | 1 |
| `ops:component_count` | 1 | — |
| `ops:distinct_ids` | 1 | 1 |
| `records:anchor_runs` | 1 | 1 |
| `records:impulses` | 1 | 1 |
| `records:paths` | 1 | 1 |
| `records:tweens` | 1 | 1 |
| `relation:anchor_runs` | 1 | 1 |
| `trace:host_actions` | 1 | — |

## Crashes

A crash is not a conformance gap. These are player bugs on their own terms.

- `canvas_draw_bitmap_and_offscreen` — java.lang.RuntimeException: Unknown operation encountered 190
- `text_on_circle_stream_alignment` — java.lang.RuntimeException: Unknown operation encountered 57
- `data_dynamic_float_list_mutation` — java.lang.RuntimeException: Unknown operation encountered 197
- `data_float_array_indexing` — java.lang.RuntimeException: Unknown operation encountered 197
- `data_id_lookup_indirection` — java.lang.RuntimeException: Unknown operation encountered 192
- `expr_rpn_comprehensive_float_operators` — java.lang.RuntimeException: Float expression too long
- `loom_macro_block_slot_injection` — java.lang.RuntimeException: Unknown operation encountered 249
- `loom_macro_parameter_remapping` — java.lang.RuntimeException: Unknown operation encountered 246
- `matrix_3d_pipeline_operations` — java.lang.RuntimeException: Unknown operation encountered 110
- `matrix_from_path_tangent_frame` — java.lang.RuntimeException: Unknown operation encountered 181
- `particle_attractor_radial_field` — java.lang.RuntimeException: Unknown operation encountered 194
- `particle_compare_conditional_respawn` — java.lang.RuntimeException: Unknown operation encountered 194
- `path_expression_polar_parametric` — java.lang.RuntimeException: Unknown operation encountered 193
- `path_expression_wave_oscillator` — java.lang.RuntimeException: Unknown operation encountered 193
- `scheduling_animation_settle_boundary` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_frame_sequence_autonomous` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_impulse_delayed_start` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_impulse_duration_expiry` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_impulse_process_body` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_multi_impulse_cascade` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_wake_in_dynamic_interval` — java.lang.RuntimeException: Unknown operation encountered 191
- `scheduling_wake_in_periodic_timer` — java.lang.RuntimeException: Unknown operation encountered 191
- `semantics_clear_and_set_override` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_invisible_to_user_pruning` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_merge_descendants` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_role_button_and_click_label` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_role_checkbox_checked_state` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_role_header_heading_level` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_role_slider_range_value` — java.lang.RuntimeException: Unknown operation encountered 65
- `semantics_root_content_description` — java.lang.RuntimeException: Unknown operation encountered 65
- `shaders_agsl_color_and_resolution_uniforms` — java.lang.RuntimeException: Unknown operation encountered 45
- `shaders_agsl_float_uniforms` — java.lang.RuntimeException: Unknown operation encountered 45
- `shaders_agsl_nested_canvas_draw` — java.lang.RuntimeException: Unknown operation encountered 45
- `shaders_agsl_time_animated_uniform` — java.lang.RuntimeException: Unknown operation encountered 45
- `shaders_linear_gradient_stops` — java.lang.RuntimeException: Unknown operation encountered 45
- `shaders_radial_gradient_center_radius` — java.lang.RuntimeException: Unknown operation encountered 45
- `shaders_sweep_gradient_angles` — java.lang.RuntimeException: Unknown operation encountered 45
- `text_style_and_layout_text` — java.lang.RuntimeException: Unknown operation encountered 242
- `text_subtext_slice_extraction` — java.lang.RuntimeException: Unknown operation encountered 182
- `wire_component_value_and_custom_layout` — java.lang.RuntimeException: Unknown operation encountered 238
- `wire_id_lookup_table` — java.lang.RuntimeException: Unknown operation encountered 192
- `wire_include_referenced_operations` — java.lang.RuntimeException: Unknown operation encountered 142
- `wire_sound_data_and_play_sound` — java.lang.RuntimeException: Unknown operation encountered 169

## Excluded as suspicious

Tagged by the corpus itself as disputed reference behaviour. Left out of the rate rather than counted as failures.

- `collapsible_column_scroll`
- `collapsible_row_scroll`
- `flow_max_lines`

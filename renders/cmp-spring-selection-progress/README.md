# Spring-animated selection rows drawn checked on the CMP player

Evidence for the `RcSpringAnimation` / `RcFloatExpressionRuntime` fix and the on-demand float
animation frame loop.

`selection-rows.png` is four `remote-m3` documents (the published catalog bundle's bytes, captured at
density 2.0) in three columns:

| column | what it is |
| --- | --- |
| cmp-android 2.0.4 (before) | the CMP player on Android (the preview daemon's `cmp-android` backend under Robolectric), rc-players 2.0.4 |
| cmp-android this change | the same harness against this branch, published to `mavenLocal` |
| baked (AndroidX embedded) | the published capture, drawn by the vendored AndroidX embedded player |

The CMP captures sit on the test window's light background; the baked capture is transparent and is
shown over grey.

## What was wrong

Every `remote-m3` selection control animates its `progress` with `remoteSpring` over the `checked`
flag, and the whole row's colours (container, split segment, thumb, check) interpolate on it. Two
bugs in the CMP player's spring, both against AndroidX `FloatExpression` / `SpringStopEngine`:

1. **It started at 0, not settled at its first target.** AndroidX `FloatExpression.apply` seeds a
   spring with `setInitialValue(v)` + `setTargetValue(v)` on its first evaluation, so a checked row
   is checked from the first frame. The CMP player sprang it in from 0.
2. **Its sub-stepping was inverted.** `SpringStopEngine.compute` over-samples
   `1 + 9 * (sqrt(k / m) * dt * 4)` times; the port had `1 + 9 / (…)`, which takes fewer sub-steps the
   longer the frame. At this spring's stiffness (1400) any frame longer than a few milliseconds ran the
   midpoint integrator past its stability limit and the value diverged instead of settling — the
   greyed, half-checked rows in the first column.

Also ported: a spring that is retargeted after it settled syncs its clock first, so an idle gap is not
integrated in one step (`FloatExpression.updateVariables`).

## Numbers

All 703 documents of the `remote-m3` bundle, `cmp-android` against the baked captures (% of pixels
differing by more than 24/255):

| | mean | median | > 10% |
| --- | --- | --- | --- |
| rc-players 2.0.4 | 0.47% | 0.00% | 4 |
| this change | 0.40% | 0.00% | 1 |

The split switch / checkbox / radio rows go from 14.1 / 12.3 / 12.3% to 1.55% each, and their
unsplit siblings from 4.5 / 3.3 / 2.8% to 1.55% — the same score the AndroidX embedded player gets
against its own capture in this harness (window background and text anti-aliasing). The one document
left above 10% is an indeterminate circular progress indicator, whose phase depends on the wall clock
the harness hands the player, not on this change.

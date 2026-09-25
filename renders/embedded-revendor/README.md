# Embedded player re-vendored from androidx-main `a12036836c4`

The 701-document catalog corpus rendered through the AndroidX View player and through the vendored
embedded player before and after the re-vendor, each scored against the View player with pixelmatch
(`scripts/design-artifacts/rc-lane-ab-score.mjs`).

| | before | after |
| --- | ---: | ---: |
| identical to the View player | 347 | 397 |
| under 1% different | 162 | 117 |
| 1-5% | 105 | 100 |
| over 5% | 87 | 87 |
| mean difference | 1.90% | 1.86% |

116 documents move closer to the View player and 6 move further away. `lanes.png` shows a sample:

- **Page indicator (`pageindicator-vertical__ideal__left-*`, 50 documents):** now identical to the
  View player. These captures mirror with `SCALE_X = -1` and no transform origin, and both AndroidX
  players pivot that at the corner, off the canvas. The old fork centred it.
- **Theme typography:** closer, from upstream's text changes.
- **Indeterminate circular progress (4 of the 6 that moved away):** an animated document. Before and
  after both differ from the View player by where the animation was sampled.
- **Title card (the other 2):** under 0.2% more different, which is text antialiasing.

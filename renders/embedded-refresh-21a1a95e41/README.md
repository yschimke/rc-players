# Embedded player refreshed to androidx-main `21a1a95e41d`

The 701-document catalog corpus (`scripts/rc-catalog-corpus/corpus`, its manifest reduced to the
`id`, `width`, `height` and `density` the harnesses read) rendered through the vendored embedded
player before the refresh, at `a328222bef4`, and after it, by `RcEmbeddedRenderHarness`; and through
the View player, by `RcViewPlayerRenderHarness`, for the score.

| embedded lane, before against after | documents |
| --- | ---: |
| pixel-identical | 696 |
| different | 5 |

The five are `circularprogressindicator__ideal__indeterminate__{192,204,216,225,240}dp`, an animated
document. Two renders of the *same* build differ on exactly those five as well, by where the
animation was sampled, so the refresh changes no document of the corpus.

Scored against the View player with pixelmatch (`scripts/design-artifacts/rc-lane-ab-score.mjs`):

| | before | after |
| --- | ---: | ---: |
| identical to the View player | 365 | 365 |
| under 1% different | 118 | 118 |
| 1-5% | 66 | 63 |
| over 5% | 152 | 155 |

The three that change band are among those five.

What the range changes in the embedded player (PROVENANCE, "The 2026-11-11 refresh") is how it reads
float fields that are variables with no number behind them, `DrawTextOnCircle` geometry given as
expressions, and a layer's compositing strategy. No document of the corpus draws differently for
any of them.

These renders were made on a Linux host with an empty Google Fonts cache, filled on demand. They are
comparable with each other, not with the lane images under `renders/rc-embedded-lane-ab/`, which
this refresh leaves as they are: the score against the View player here is far from the one
recorded there (the `edgebutton` documents differ by 98% on this host, before and after alike), and
that gap was not investigated.

```sh
scripts/agent-gradle.sh :third-party-rc-embedded-player:testDebugUnitTest --rerun \
  --tests '*RcViewPlayerRenderHarness*' --tests '*RcEmbeddedRenderHarness*' \
  -Prc.embedded.input=<stage> -Prc.view.output=<lanes>/view \
  -Prc.embedded.output=<lanes>/embedded -Pcomposeai.fonts.cacheDir=$HOME/.cache/composeai/fonts
node scripts/design-artifacts/rc-lane-ab-score.mjs <lanes>/view <lanes>/embedded
```

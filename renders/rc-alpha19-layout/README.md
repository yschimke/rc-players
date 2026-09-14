# Alpha19 layout-value parity

These deterministic renders use the same `.rc` operations before and after the player changes.
Transparent pixels appear black in the previews.

| Case | Before | After |
| --- | --- | --- |
| DP-behavior padding at playback density 2.0 | ![60px white content: padding was treated as captured pixels](dp-padding-before.png) | ![20px white content: 20dp padding is scaled to 40px per edge](dp-padding-after.png) |
| 180-degree rotation with no authored transform origin | ![red component rotates away around the incorrect top-left pivot](graphics-pivot-before.png) | ![red component remains in place around Compose's default center pivot](graphics-pivot-after.png) |

Regenerate the current-player images with:

```sh
RC_LAYOUT_EVIDENCE_DIR=renders/rc-alpha19-layout \
  ./gradlew :rc-player-compose:jvmTest \
  --tests '*writeDpPaddingEvidence' \
  --tests '*writeGraphicsPivotEvidence'
```

The harness writes `dp-padding.png` and `graphics-pivot.png`; rename them with the relevant
`-before` or `-after` suffix when recording a comparison.

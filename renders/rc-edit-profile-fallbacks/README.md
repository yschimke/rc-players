# `rc-player-edit` profile fallbacks

Evidence for `lowerMatrixExpressions()` and `colorThemesAsFallbacks()`, the two rewrites
`adaptTo` uses for targets without `MATRIX_EXPRESSION` (launcher widgets at API level 6) or
`COLOR_THEME` (level 6 and Wear widgets). Each image shows catalog corpus documents
(`scripts/rc-catalog-corpus/corpus`) before and after the rewrite, on the CMP player and on
AndroidX's `remote-player-view` (the `RcViewPlayerRenderHarness` lane, Robolectric, light theme).

- `matrix-lowering.png`: the `RemoteImageBrush` shader matrix rewritten as canvas transforms around
  the inverse path. The title cards match pixel for pixel on both players. The image buttons differ
  only in 50 partly covered pixels along the top curves of the pill.
- `color-theme-fallback.png`: the theme sections that replace each `ColorTheme`. Column 2 is the
  CMP player before this change, which skipped the one-pass loop that sets the colour, so the
  swatches were never filled. Column 3 is after. Column 4 is AndroidX drawing the same rewritten
  document.

`RcEditRewriteRenderTest` (`:rc-player-compose:jvmTest`) checks both rewrites against every
corpus document they touch.

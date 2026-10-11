# `rc-player-edit` ColorTheme fallback

Evidence for `colorThemesAsFallbacks()`, which `adaptTo(…, approximateColors = true)` uses for
targets without `COLOR_THEME` (launcher widgets at API level 6, Wear widgets).

`color-theme-fallback.png` shows the catalog's system-theme swatches
(`scripts/rc-catalog-corpus/corpus`) with each `ColorTheme` replaced by theme sections holding its
fallbacks. Column 2 is the CMP player before this change, which skipped the one-pass loop that sets
the colour, so the swatches were never filled. Column 3 is after, identical to the original on a
host with no system palette. Column 4 is AndroidX's `remote-player-view` (the
`RcViewPlayerRenderHarness` lane, Robolectric, light theme) drawing the same rewritten document.

`RcEditRewriteRenderTest` (`:rc-player-compose:jvmTest`) checks the rewrite against every corpus
document it touches.

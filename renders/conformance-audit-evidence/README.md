# Conformance audit evidence

Before/after screenshots of the published visual audit
(`cmp/conformance-audit.html` on GitHub Pages), from the run this branch's
runner produces against the same filtered corpus (`-Prc.filter=canvas`).

Before: the `cmp` audit published from `main` at `93e6b65` — the runner emitted
no `aaPixels`, no `diffHeatmapBase64`, and the metric only where a check failed,
so every panel read "Not computed by this player" and every badge "? PX / 16".

After: the same generator over a run of this branch — the heatmap panel renders
the difference map, the badge carries the AA-aware count the verdict used, and
the metric row is populated on passes and failures alike.

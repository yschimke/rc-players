# Apple player benchmarks

`scripts/measure-apple-players-macos.sh` compares the CMP and experimental native AppKit player
inside the same packaged Release macOS app on an Apple-silicon host. It writes
`build/apple-player-benchmark.json` (or `RC_APPLE_PLAYER_BENCHMARK_OUTPUT`). Build the Release app
first with `scripts/package-macos-player.sh`.

The first version records per player:

* **headline, one-to-one:** CMP and native AppKit window startup and first visible content;
* native AppKit decode, view-tree build, Core Graphics capture, retained-frame cost, and memory.

The JSON keeps the matched pair under `headline.cmp` and `headline.nativeAppKit`; all additional
native-only measurements are under `nativeAppKitDiagnostics`. This is a macOS-only baseline, not a
simulator result. The two renderers do not yet expose the same per-frame callback, so retained-frame
measurements remain diagnostics rather than falsely comparable headline numbers.

macOS results are regression evidence, not device performance claims. The harness does not set
pass/fail timing budgets because host load and window-server scheduling make absolute thresholds
misleading. Compare revisions on the same hardware and investigate large relative changes.

## Next critical scenarios

1. **Matched frame instrumentation:** add a CMP macOS frame callback, then report animation jank
   (median/p95 interval and missed frames) against the same retained-frame contract as AppKit.
2. **Real input latency:** drive click, drag, fling, text edit, and return channels through both
   macOS hosts and capture input-to-visible response.
3. **Memory under churn:** repeatedly replace documents, load images/fonts, background/foreground,
   and record peak footprint plus post-cycle recovery.
4. **Large/complex documents:** add matched fixtures for deep layout trees, text-heavy content,
   clipping/path work, bitmap pressure, and simultaneous animations.
5. **Thermal and energy:** run sustained animation and scrolling on dedicated hardware after a warmup.
6. **Correctness alongside speed:** retain screenshot/conformance checks for every benchmark fixture
   so a faster partial render is never treated as an improvement.

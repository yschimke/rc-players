# Apple player benchmarks

Three benchmark lanes cover the native Swift player across core, mobile (iOS), and desktop (macOS):

1. **Core (`scripts/measure-native-swift-core.sh` → `build/native-swift-core-benchmark.json`)**:
   runs `NativeSwiftBenchmark` in Release mode on Darwin or Linux. Measures decode, first frame,
   steady frame, replacement, and RSS growth across the 6 comparative fixtures plus a synthetic
   dense feed card (~65 nodes, 10 named variables, 5 expressions), along with 20-card feed arrival,
   single/burst host-value updates, and static wall-clock reuse.
2. **Mobile / iOS simulator (`scripts/measure-apple-players-simulator.sh` → `build/apple-player-simulator-benchmark.json`)**:
   compares CMP and native UIKit in the same Release simulator app on startup, first presentation,
   120-frame animation cadence, `UIScrollView` scroll, 12-card feed arrival, and document
   replacement. `scripts/measure-native-uikit-simulator.sh` captures native-only footprint and
   hierarchy budgets (`build/native-uikit-evidence.json`).
3. **Desktop / macOS (`scripts/measure-apple-players-macos.sh` → `build/apple-player-benchmark.json`)**:
   compares CMP and native AppKit inside the same packaged Release macOS app on an Apple-silicon
   host (build first with `scripts/package-macos-player.sh`):
   * **headline, one-to-one:** CMP and native AppKit window startup and first visible content;
   * **`nativeAppKitDiagnostics` & `nativeAppKitAnimatedDiagnostics`:** native AppKit decode,
     view-tree build, Core Graphics capture, retained-frame cost, 12-card feed arrival, and memory
     for both a static card (`TitleCardRemote-640x480`) and an animated canvas
     (`IndeterminateCircularProgress-400x400`).

The JSON keeps the matched pair under `headline.cmp` and `headline.nativeAppKit`; native-only
measurements stay under `nativeAppKitDiagnostics` / `nativeAppKitAnimatedDiagnostics`. The two
renderers do not yet expose the same per-frame callback on macOS, so retained-frame measurements
remain diagnostics rather than falsely comparable headline numbers.

macOS and simulator results are regression evidence, not device performance claims. Absolute
thresholds are avoided on comparative lanes because host load and window-server scheduling vary;
compare revisions on the same hardware and investigate large relative changes.

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

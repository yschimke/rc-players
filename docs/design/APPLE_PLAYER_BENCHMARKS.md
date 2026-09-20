# Apple player benchmarks

`scripts/measure-apple-players-simulator.sh` compares the CMP and experimental native UIKit player
inside the same packaged Release app and booted iPad simulator. It writes
`build/apple-player-benchmark.json` (or `RC_APPLE_PLAYER_BENCHMARK_OUTPUT`). Build the Release app
first with `scripts/package-apple-player.sh`.

The first version records per player:

* player startup and first presentation (creation through the next display-link callback);
* animation jank: median/p95 display-link interval and missed-frame count across 120 animation
  samples;
* scrolling: completion time for three animated `UIScrollView` content-offset transitions while the
  player is hosted in the scroll view.

`ApplePlayerBenchmarkInteractionUITests` separately sends a real XCUITest swipe to each renderer's
host and verifies that its scroll offset changes. Run it with
`scripts/check-native-uikit-accessibility-ui.sh`'s Xcode invocation, replacing the `-only-testing`
value with `RemoteComposePlayerUITests/ApplePlayerBenchmarkInteractionUITests`.

Simulator results are regression evidence, not device performance claims. The harness does not set
pass/fail timing budgets because simulator scheduling, host load, and graphics virtualization make
absolute thresholds misleading. Compare revisions on the same simulator runtime, and investigate
large relative changes. Before declaring a winner or setting a product SLA, move these fixtures to a
dedicated physical device and capture Time Profiler, Core Animation, memory, and energy traces.

## Next critical scenarios

1. **Real input latency:** capture touch-to-visible response for tap, drag, fling, text edit, and a
   return channel. The current XCUITest coverage proves a swipe reaches both hosts, while the timing
   metric remains programmatic for repeatability.
2. **Memory under churn:** repeatedly replace documents, load images/fonts, background/foreground,
   and record peak footprint plus post-cycle recovery.
3. **Large/complex documents:** add matched fixtures for deep layout trees, text-heavy content,
   clipping/path work, bitmap pressure, and simultaneous animations.
4. **Thermal and energy:** run sustained animation and scrolling on a physical device after a warmup;
   simulator CPU and GPU use cannot answer this.
5. **Correctness alongside speed:** retain screenshot/conformance checks for every benchmark fixture
   so a faster partial render is never treated as an improvement.

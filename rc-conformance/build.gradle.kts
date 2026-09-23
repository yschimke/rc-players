plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

// `:rc-conformance` — the runner that measures a player against the AndroidX RemoteCompose
// conformance corpus, and the scorecard that comes out of it.
//
// **The corpus is not here, and must never be.** It lives on the long-lived
// `vendor/androidx-rc-conformance` branch, reconstructed from an unmerged AOSP Gerrit change; this
// module resolves it as *configuration* (`--spec-dir`, or `RC_SPEC_DIR`), exactly as
// `PLAYER_IMPLEMENTATION_GUIDE.md` §1 requires. A hard-coded in-repo path is the one thing that
// makes a conformance number quietly stale, because the runner keeps passing against an
// expectation nobody refreshed.
//
// The split inside `src/main/kotlin` matters for the same reason the spec directory insists on it:
//
//   * `corpus/`  — the gold model and the result model. Knows the format, knows no player.
//   * `runner/`  — the timeline loop, check evaluation and the raster metric. Player-agnostic;
//                  it talks to an engine only through `ConformanceEngine`.
//   * `engine/`  — one adapter per player lane. This is the only package allowed to name a player.
//
// Nothing in `corpus/` or `runner/` may import a player. That boundary is what lets a second lane
// be a new file in `engine/` rather than a fork of the runner.

dependencies {
  implementation(project(":rc-player-compose"))
  implementation(project(":rc-player-protocol"))
  implementation(project(":rc-player-runtime"))

  // Compose Desktop supplies `ImageComposeScene` and skiko's software rasterizer, which is how the
  // CMP lane paints with no `DISPLAY` — in CI and in an agent sandbox, not only on a desktop.
  @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
  @Suppress("DEPRECATION") implementation(compose.runtime)
  @Suppress("DEPRECATION") implementation(compose.foundation)
  @Suppress("DEPRECATION") implementation(compose.ui)

  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(kotlin("test"))
  // The Compose test API drives a player the same way on every Compose target — `runComposeUiTest`
  // owns a composition, `mainClock` advances animation deterministically, semantics exposes the
  // tree, and `captureToImage` returns real pixels. It resolves from a test configuration, which is
  // why the engines live in the test source set. See docs/design/RC_CONFORMANCE_PLATFORMS.md.
  testImplementation("org.jetbrains.compose.ui:ui-test-desktop:1.11.1")
}

/**
 * Where the corpus is checked out.
 *
 * Resolution order — flag, then environment, then the conventional sibling worktree. There is no
 * fourth fallback on purpose: if none of these resolves, the lane reports that it could not find
 * the corpus instead of scoring zero against an empty directory, which looks identical to a player
 * that draws nothing.
 */
val specDirProvider =
  providers
    .gradleProperty("rc.specDir")
    .orElse(providers.environmentVariable("RC_SPEC_DIR"))
    .orElse(
      rootProject.layout.projectDirectory
        .dir(
          "../rc-players-conformance-spec/third_party/rc-conformance-spec/compose/remote/specification/conformance"
        )
        .asFile
        .absolutePath
    )

tasks.register<Test>("conformance") {
  description = "Score a player against the RemoteCompose conformance corpus."
  group = "verification"
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  filter { includeTestsMatching("*RcConformanceHarness*") }

  // Headless skiko: the same setting the other rendering lanes rely on, so this runs on a CI runner
  // with no display server.
  systemProperty("java.awt.headless", "true")

  // Resolved at configuration time rather than through a lazy provider: Gradle tracks each
  // `gradleProperty` read as a configuration input, so changing one still invalidates the cache.
  systemProperty("rc.specDir", specDirProvider.get())
  systemProperty("rc.player", providers.gradleProperty("rc.player").orElse("cmp").get())

  // The CMP lane's own version, for the results file and the published trend.
  //
  // It used to come from `Package.getImplementationVersion()`, which is read from a JAR manifest --
  // and a Gradle test runs against class *directories*, so that was null on every run and every
  // score this lane has published records the player as `dev`. A trend across releases that cannot
  // say which release it measured is not much of a trend.
  systemProperty("rc.player.version", project(":rc-player-compose").version.toString())
  systemProperty("rc.filter", providers.gradleProperty("rc.filter").orElse("").get())
  systemProperty(
    "rc.player.out",
    layout.buildDirectory.dir("conformance").get().asFile.absolutePath,
  )

  // The native-swift lane drives the packaged macOS player as a subprocess. Resolved here, against
  // the root project, because a relative default would resolve against this task's working
  // directory rather than the checkout and find nothing.
  systemProperty(
    "rc.macosPlayer",
    rootProject.layout.projectDirectory
      .dir("build/macos-player/Remote Compose Player.app/Contents/MacOS")
      .file("RemoteComposePlayer")
      .asFile
      .absolutePath,
  )

  // A conformance score is a measurement, not a build artifact. Declaring outputs would make the
  // task UP-TO-DATE on the second run — exactly when a re-measure is what was asked for.
  outputs.upToDateWhen { false }
  testLogging { showStandardStreams = true }
}

// `check` must not depend on it: the corpus is upstream-unmerged and the lane is deliberately
// non-gating, so a corpus revision can never turn a pull request red.
tasks.named<Test>("test") { filter { excludeTestsMatching("*RcConformanceHarness*") } }

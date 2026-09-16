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

tasks.register<JavaExec>("conformance") {
  description = "Score a player against the RemoteCompose conformance corpus."
  group = "verification"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("ee.schimke.composeai.rcconformance.ConformanceMainKt")

  // Headless skiko: the same setting `:rc-player-profile` relies on, so the lane runs on a CI
  // runner with no display server.
  systemProperty("java.awt.headless", "true")

  // Resolved at configuration time rather than through an `argumentProviders` lambda: a lambda
  // declared in a build script is a script object reference, which the configuration cache cannot
  // serialize. Gradle tracks each `gradleProperty` read as a configuration input, so changing
  // `-Prc.player` still invalidates the entry — laziness buys nothing here and costs the cache.
  val player = providers.gradleProperty("rc.player").orElse("cmp").get()
  val filter = providers.gradleProperty("rc.filter").orElse("").get()
  val outDir = layout.buildDirectory.dir("conformance").get().asFile.absolutePath

  // Deliberately not declared as task outputs: a conformance score is a measurement, not a build
  // artifact. Declaring it would make the task UP-TO-DATE on the second run — exactly when a
  // re-measure is what was asked for. `:rc-player-profile` makes the same call for the same reason.
  outputs.upToDateWhen { false }

  args = buildList {
    add("--spec-dir")
    add(specDirProvider.get())
    add("--player")
    add(player)
    add("--out")
    add(outDir)
    if (filter.isNotBlank()) {
      add("--filter")
      add(filter)
    }
  }
}

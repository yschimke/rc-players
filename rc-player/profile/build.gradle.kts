plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

// `:rc-player-profile` — the process that turns the CMP player's trace spans into numbers.
//
// It is an *application*, not a library, and that distinction is the point: `androidx.tracing`
// documents `Tracer.setGlobalTracer` as something a library must never call, so the player modules
// only ever read `Tracer.global`. This module is the process that installs a driver, so it is the
// only place in the repo that registers one.
//
// It runs headless. `ImageComposeScene` rasterizes through skiko's software path with no `DISPLAY`
// (see docs/AGENT_GUIDE.md), which is what lets `./gradlew :rc-player-profile:rcPlayerProfile`
// produce a
// real profile in CI and in an agent sandbox rather than only on a developer's desktop.

dependencies {
  implementation(project(":rc-player-compose"))
  implementation(project(":rc-player-protocol"))
  implementation(project(":rc-player-runtime"))
  implementation(project(":rc-player-trace"))

  // Compose Desktop supplies `ImageComposeScene` and skiko's software rasterizer.
  @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
  @Suppress("DEPRECATION") implementation(compose.runtime)
  @Suppress("DEPRECATION") implementation(compose.foundation)
  @Suppress("DEPRECATION") implementation(compose.ui)

  // androidx.tracing 2.x plus the wire driver that serializes spans as Perfetto `TracePacket`s.
  implementation(libs.androidx.tracing.kmp)
  implementation(libs.androidx.tracing.wire)

  // Published from yschimke/compose-preview-contracts; versioned by its BOM (see
  // `composeai-contracts` in the catalog), never pinned directly — a contracts release skips the
  // modules it did not change, so `common-io:<release>` need not exist. This module is not
  // published, so the platform reaches no POM.
  implementation(platform(libs.composeai.preview.contracts.bom))
  implementation(libs.composeai.common.io)
  implementation(libs.okio)

  testImplementation(libs.junit)
  testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("rcPlayerProfile") {
  description = "Profile the CMP Remote Compose player over the four reference documents."
  group = "verification"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("ee.schimke.composeai.rcplayer.profile.RcProfileMainKt")
  // Not declared as task outputs on purpose: a profile is a measurement, not a build artifact, and
  // declaring it would make the task UP-TO-DATE on the second run — exactly when a re-measure is
  // what was asked for.
  args(layout.buildDirectory.dir("profile").get().asFile.absolutePath)
}

tasks.register<JavaExec>("rcPlayerFeedProfile") {
  description = "Profile what a card arriving in a feed costs, per way of sharing its template."
  group = "verification"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("ee.schimke.composeai.rcplayer.profile.RcFeedProfileMain")
  // -Prc.jvmArgs="-XX:StartFlightRecording=..." to sample where a card's time goes.
  (findProperty("rc.jvmArgs") as String?)?.let { jvmArgs(it.split(' ')) }
  (findProperty("rc.feed.modes") as String?)?.let { systemProperty("rc.feed.modes", it) }
  // Card-shaped documents from the catalog corpus: what a feed of one template plays.
  val corpus = rootProject.file("scripts/rc-catalog-corpus/corpus")
  // -Prc.feed.templates=a.rc,b.rc profiles those instead (a catalog's own card templates).
  val named =
    (findProperty("rc.feed.templates") as String?)?.split(',').orEmpty().filter { it.isNotBlank() }
  args(
    listOf(layout.buildDirectory.dir("profile").get().asFile.absolutePath) +
      named.ifEmpty {
        listOf(
            "appcard__ideal__default__compact.rc",
            "appcard__ideal__icon-content-image__compact.rc",
            "titlecard__ideal__default__compact.rc",
          )
          .map { corpus.resolve(it).absolutePath }
      },
  )
}

tasks.register<JavaExec>("rcPlayerCardPhases") {
  description = "Time the document work a new card can cost: decode, prepare, state, seeded state."
  group = "verification"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("ee.schimke.composeai.rcplayer.profile.RcCardPhaseProfileMain")
  val corpus = rootProject.file("scripts/rc-catalog-corpus/corpus")
  // -Prc.cardPhases.templates=a.rc,b.rc times those instead.
  val named =
    (findProperty("rc.cardPhases.templates") as String?)?.split(',').orEmpty().filter {
      it.isNotBlank()
    }
  args(
    listOf(layout.buildDirectory.dir("profile").get().asFile.absolutePath) +
      named.ifEmpty {
        listOf("appcard__ideal__default__compact.rc", "titlecard__ideal__default__compact.rc").map {
          corpus.resolve(it).absolutePath
        }
      },
  )
}

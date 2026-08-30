plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.android.library) apply false
  // ktfmt is not declared here: `ComposeAiBaseConventionsPlugin` (build-logic) applies and
  // configures it on every module. ktfmt + the Kotlin Gradle plugin it links against ride on the
  // build-logic classpath, so declaring the alias here too would put a second ktfmt on a different
  // classloader.
  //
  // The publish plugin IS loaded into the root scope so the sibling publishing modules share the
  // plugin's ClassLoader. Without it, each sibling instantiates its own MavenCentralBuildService
  // class and Gradle refuses to share the build service across them.
  alias(libs.plugins.maven.publish) apply false
}

// `./gradlew ktfmtCheck` already fans out to every project that applies the plugin via Gradle's
// task-name matching. These aggregates exist so one task name works from the root and from CI.
// The ktfmt-carrying project paths are gathered in `settings.gradle.kts` and handed over via a
// system property (read through a configuration-cache-tracked provider).
val ktfmtProjectPaths = providers.systemProperty("composeai.ktfmtProjectPaths").get().split(",")

tasks.register("ktfmtCheckAll") {
  group = "verification"
  description = "Runs ktfmtCheck across every module in this build."
  ktfmtProjectPaths.forEach { dependsOn("$it:ktfmtCheck") }
}

tasks.register("ktfmtFormatAll") {
  group = "formatting"
  description = "Runs ktfmtFormat across every module in this build."
  ktfmtProjectPaths.forEach { dependsOn("$it:ktfmtFormat") }
}

// The published surface, in dependency order. `release.yml` and `snapshot.yml` drive this rather
// than a bare `publish` so a module that does not publish (the wasm host, the three build-only
// tools) can never be swept in by task-name matching, and so the list of coordinates this repo
// owns is written down in exactly one place.
val publishedProjects =
  listOf(
    ":rc-player-trace",
    ":rc-player-protocol",
    ":rc-player-runtime",
    ":rc-player-compose",
    ":third-party-rc-embedded-player",
  )

tasks.register("publishPlayers") {
  group = "publishing"
  description = "Publishes every player artifact this repository owns to Maven Central."
  publishedProjects.forEach { dependsOn("$it:publishToMavenCentral") }
}

tasks.register("publishPlayersToMavenLocal") {
  group = "publishing"
  description = "Publishes every player artifact this repository owns to mavenLocal."
  publishedProjects.forEach { dependsOn("$it:publishToMavenLocal") }
}

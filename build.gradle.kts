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

// `check` at the root, so `./gradlew check` — what CI runs, and what every contributor runs —
// reaches build-logic's own tests.
//
// `build-logic` is an `includeBuild`, and Gradle's task-name matching does not descend into an
// included build, so `PublishedArtifactIdsTest` would have been running for nobody. It pins the
// three copies of the path-to-artifact-id mapping against each other; a released POM naming a
// coordinate that was never uploaded cannot be withdrawn. Tests nothing runs are not a safety net.
//
// Attached to the root's own `check` rather than registered as one: `LifecycleBasePlugin` arrives
// here from elsewhere, and registering a second `check` fails that plugin's apply.
val buildLogicTests = gradle.includedBuild("build-logic").task(":test")

tasks.matching { it.name == "check" }.configureEach { dependsOn(buildLogicTests) }

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
  mapOf(
    ":rc-player-trace" to "rc-player-trace",
    ":rc-player-protocol" to "rc-player-protocol",
    ":rc-player-runtime" to "rc-player-runtime",
    ":rc-player-compose" to "rc-player-compose",
    ":rc-player-wasm-dist" to "rc-player-wasm-dist",
    ":third-party-rc-embedded-player" to "third-party-rc-embedded-player",
    ":third-party-rc-embedded-player-jvm" to "third-party-rc-embedded-player-jvm",
    // Named for what it contains rather than for the project that wraps it. Kept in step with
    // `PublishedArtifactIds` in build-logic by `PublishedArtifactIdsTest`, which reads both.
    ":third-party-remote-compose-player-dist" to "remote-compose-player-js-dist",
  )

// Which of those a release actually uploads.
//
// `-Pcomposeai.publishSet` is computed by `.github/scripts/maven-publish-plan.sh`: a module
// publishes when its own files changed since the tag it last published at, when something it
// depends on is publishing, or when a shared build input moved. Absent the property, everything
// publishes — the old behaviour, and the right default for a local run or a recovery release.
//
// Absent and empty mean different things and must not be collapsed: absent is "no plan ran,
// publish everything", empty is "the plan ran and found nothing". Deliberately mirrors
// `PublishedVersions.parsePublishSet`, which the modules and `:bom` use — the root build script
// applies no build-logic plugin, so it cannot see that class, and this is the one place the rule
// is restated.
val publishSet =
  providers
    .gradleProperty("composeai.publishSet")
    .orNull
    ?.split(",")
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toSet()

val projectsToPublish =
  publishedProjects.filterValues { publishSet == null || it in publishSet }.keys

tasks.register("publishPlayers") {
  group = "publishing"
  description = "Publishes the player artifacts this release changed to Maven Central."
  // `:bom` is never filtered: it is the index of the release, and a consumer resolving it at the
  // tag has to find it there whether or not any player changed.
  dependsOn(":bom:publishToMavenCentral")
  projectsToPublish.forEach { dependsOn("$it:publishToMavenCentral") }
}

tasks.register("publishPlayersToMavenLocal") {
  group = "publishing"
  description = "Publishes every player artifact this repository owns to mavenLocal."
  dependsOn(":bom:publishToMavenLocal")
  publishedProjects.keys.forEach { dependsOn("$it:publishToMavenLocal") }
}

// What `publishPlayers` would upload, one artifact id per line, for the release job to hand to
// `record-published.py`. Reading it back off the task graph rather than re-deriving it in YAML is
// what keeps the manifest describing what actually published.
tasks.register("printPublishSet") {
  group = "publishing"
  description = "Print the artifact id of each module publishPlayers would upload."
  notCompatibleWithConfigurationCache("Reports a configuration-time decision at execution time")
  val ids = projectsToPublish.mapNotNull { publishedProjects[it] }.sorted()
  doLast { ids.forEach(::println) }
}

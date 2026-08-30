pluginManagement {
  includeBuild("build-logic")
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

// Which line the Remote Compose group (`androidx.compose.remote`) resolves from:
//
//   * `release`  (default) — the alpha coordinates pinned in `gradle/libs.versions.toml`,
//     resolved from google(). This is the normal build: reproducible, never ages out, and
//     the only mode CI and published consumers see.
//   * `snapshot` — `1.0.0-SNAPSHOT` from the androidx-main post-submit build pinned by
//     `androidxSnapshotBuildId` below. Use it to try an API that has landed on androidx-main
//     but has not been released yet: `-Pcomposeai.remoteCompose=snapshot`.
//
// The vendored player in `third_party/rc-embedded-player` is compiled against whichever line is
// selected, which is the point of the lane: it is how a Remote Compose API change is exercised
// here before it reaches a release.
val remoteComposeLine =
  providers.gradleProperty("composeai.remoteCompose").orElse("release").get().trim().lowercase()

require(remoteComposeLine == "release" || remoteComposeLine == "snapshot") {
  "composeai.remoteCompose must be 'release' or 'snapshot', was '$remoteComposeLine'"
}

val useRemoteComposeSnapshot = remoteComposeLine == "snapshot"

// androidx-main post-submit build the Remote Compose artifacts resolve from when
// `composeai.remoteCompose=snapshot`. Bump this one line to move the group to a newer snapshot.
// Build ids age out of androidx.dev after a few weeks — if the artifacts 404, pick a fresh one
// from https://androidx.dev/snapshots/builds.
val androidxSnapshotBuildId = "16155060"

dependencyResolutionManagement {
  // Kotlin's wasmJs toolchain resolves Node.js from an Ivy repository that the Kotlin Gradle
  // plugin adds to the root project while kotlinWasmNodeJsSetup is realized. Rejecting or ignoring
  // that project repository makes the aggregate `check` task fail before any tests run because
  // org.nodejs:node is not published to our Maven repositories. The build scripts themselves keep
  // dependency repositories centralized here; project preference exists solely so the plugin-owned
  // Node.js distribution repository remains usable.
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
    // Pinned to a build id rather than `snapshots/latest` so even the snapshot lane stays
    // reproducible: a new snapshot lands only when `androidxSnapshotBuildId` changes. Scoped by
    // group regex (which also picks up `androidx.compose.remote.foundation`) and `snapshotsOnly()`,
    // so nothing else can drift onto an unreviewed snapshot and every release coordinate keeps
    // resolving from google() even here.
    if (useRemoteComposeSnapshot) {
      maven("https://androidx.dev/snapshots/builds/$androidxSnapshotBuildId/artifacts/repository") {
        name = "androidxSnapshots"
        content { includeGroupByRegex("androidx\\.compose\\.remote.*") }
        mavenContent { snapshotsOnly() }
      }
    }
  }

  // Snapshot mode rewrites the Remote Compose version ref in place, so the catalog file keeps
  // exactly one set of coordinates — the released ones — and `-Pcomposeai.remoteCompose=snapshot`
  // is the only thing that can move them.
  if (useRemoteComposeSnapshot) {
    versionCatalogs {
      // `create`, not `named`: `named` fails here with "VersionCatalogBuilder with name 'libs' not
      // found" — the default catalog is registered after settings are evaluated. `create("libs")`
      // returns that same builder with `gradle/libs.versions.toml` ALREADY imported (calling
      // `from(...)` on it fails with "Multiple 'from' invocations"), so this line overrides one
      // version and leaves every other entry as the TOML has it.
      create("libs") { version("compose-remote", "1.0.0-SNAPSHOT") }
    }
  }
}

rootProject.name = "rc-players"

// The Compose Multiplatform player stack, bottom-up:
//
//     trace <- protocol <- runtime <- compose <- wasm
//
// The first four are published to Maven Central (`composeai.maven-publishing`); `wasm` is the
// browser host, published as an npm package instead. `compat-tests`, `profile` and `metrics` are
// build-only tools and publish nothing.
//
// Directory names are short (`rc-player/protocol`) while project names are qualified
// (`:rc-player-protocol`) — the project name is what becomes the published artifact id, and
// `:protocol` would be a poor coordinate.
include(":rc-player-trace")

project(":rc-player-trace").projectDir = file("rc-player/trace")

include(":rc-player-protocol")

project(":rc-player-protocol").projectDir = file("rc-player/protocol")

include(":rc-player-runtime")

project(":rc-player-runtime").projectDir = file("rc-player/runtime")

include(":rc-player-compose")

project(":rc-player-compose").projectDir = file("rc-player/compose")

include(":rc-player-wasm")

project(":rc-player-wasm").projectDir = file("rc-player/wasm")

// The `wasmPlayerDist` bundle, wrapped as one publishable zip so a Gradle consumer in another
// repository can resolve it. See that module's build file for why it is not a second publication on
// `:rc-player-wasm`.
include(":rc-player-wasm-dist")

project(":rc-player-wasm-dist").projectDir = file("rc-player/wasm-dist")

include(":rc-player-compat-tests")

project(":rc-player-compat-tests").projectDir = file("rc-player/compat-tests")

include(":rc-player-profile")

project(":rc-player-profile").projectDir = file("rc-player/profile")

include(":rc-player-metrics")

project(":rc-player-metrics").projectDir = file("rc-player/metrics")

// The vendored, locally patched AndroidX embedded player — the Android comparison lane the CMP
// player above is measured against. Provenance and the patch log are in
// `third_party/rc-embedded-player/PROVENANCE.md`.
include(":third-party-rc-embedded-player")

project(":third-party-rc-embedded-player").projectDir = file("third_party/rc-embedded-player")

// The desktop-JVM cut of the same vendored player. See
// `third_party/rc-embedded-player/PROVENANCE.md`.
include(":third-party-rc-embedded-player-jvm")

project(":third-party-rc-embedded-player-jvm").projectDir =
  file("third_party/rc-embedded-player-jvm")

// Snapshot the project paths that carry ktfmt (every project except the root, which applies no
// convention plugin) for the root build's `ktfmtCheckAll` / `ktfmtFormatAll` aggregate tasks. We
// gather the paths here in settings — where every project is already known — and hand them to the
// root build through a system property, read back via a configuration-cache-tracked
// `providers.systemProperty(...)`.
val ktfmtProjectPaths = buildList {
  fun visit(descriptor: org.gradle.api.initialization.ProjectDescriptor) {
    // Only projects with a build script apply `composeai.base-conventions` (and therefore own a
    // `ktfmtCheck`/`ktfmtFormat` task).
    if (descriptor.buildFile.exists()) add(descriptor.path)
    descriptor.children.forEach(::visit)
  }
  // Start from the root's children: the root project can't apply `composeai.base-conventions`
  // (a build-logic plugin on the root classpath leaks to every subproject and collides with their
  // versioned plugin aliases), so the root carries no ktfmt and is left out.
  rootProject.children.forEach(::visit)
}

System.setProperty("composeai.ktfmtProjectPaths", ktfmtProjectPaths.joinToString(","))

pluginManagement {
  includeBuild("build-logic")
  repositories {
    // Google Maven first, scoped to its own groups, so AGP and androidx never reach the plugin
    // portal or Central: under load those answer a miss with HTTP 429, which fails the build,
    // instead of the 404 that would have moved on to Google.
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    gradlePluginPortal()
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
// browser host, published as an npm package instead. `compat-tests`, `profile`, `metrics` and the
// two `baselineprofile` modules are build-only tools and publish nothing.
//
// Directory names are short (`rc-player/protocol`) while project names are qualified
// (`:rc-player-protocol`) — the project name is what becomes the published artifact id, and
// `:protocol` would be a poor coordinate.

// The BOM: one coordinate a consumer imports to pin the whole published set at one version. Listed
// first because it is the index of the release rather than a member of it — it publishes at every
// release, whichever players changed.
include(":bom")

include(":rc-player-trace")

project(":rc-player-trace").projectDir = file("rc-player/trace")

include(":rc-player-protocol")

project(":rc-player-protocol").projectDir = file("rc-player/protocol")

include(":rc-player-runtime")

project(":rc-player-runtime").projectDir = file("rc-player/runtime")

include(":rc-player-compose")

project(":rc-player-compose").projectDir = file("rc-player/compose")

// Creation: rc-players' own API over the creation fork, in its own package
// (`ee.schimke.composeai.rccreation`), and the module that joins creation to the player. Published,
// experimental; see docs/design/RC_CREATION_BRIDGE.md.
include(":rc-creation-bridge")

project(":rc-creation-bridge").projectDir = file("rc-creation/bridge")

include(":rc-player-embedded")

project(":rc-player-embedded").projectDir = file("rc-player/embedded")

// Rewrites over a decoded document — embed, hardcode, collapse — for code that changes a document
// rather than plays it. Published, but beside the stack rather than in it: it depends on `protocol`
// and `runtime`, and no player module depends on it.
include(":rc-player-edit")

project(":rc-player-edit").projectDir = file("rc-player/edit")

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

// Baseline profiles for the published Android players: an app that plays the catalog corpus, and the
// test module that drives it to collect the profile `trace`, `protocol`, `runtime` and `compose`
// ship in their AARs. Neither is published. See rc-player/baselineprofile/README.md.
include(":rc-player-baselineprofile-host")

project(":rc-player-baselineprofile-host").projectDir = file("rc-player/baselineprofile/host")

include(":rc-player-baselineprofile")

project(":rc-player-baselineprofile").projectDir = file("rc-player/baselineprofile/generator")

// The conformance lane: scores a player against the AndroidX RemoteCompose conformance corpus and
// writes the scorecard. Root-level rather than under `rc-player/` because it measures every lane in
// the repository, the vendored comparison players included, and belongs to none of them.
//
// The corpus itself is NOT in this repository's main line. It lives on the long-lived
// `vendor/androidx-rc-conformance` branch, and this module resolves it as configuration
// (`-Prc.specDir`, or `RC_SPEC_DIR`) — see `third_party/rc-conformance-spec/PROVENANCE.md` there.
include(":rc-conformance")

project(":rc-conformance").projectDir = file("rc-conformance")

// Runnable examples of the host half of a custom component — see `rc-player/demos/build.gradle.kts`.
include(":rc-player-demos")

project(":rc-player-demos").projectDir = file("rc-player/demos")

// Incubating Apple-styled authoring components built on AndroidX's Remote Compose creation API.
// This intentionally lives outside the published player stack while its API and ownership settle.
include(":prototype-remote-apple-components")

project(":prototype-remote-apple-components").projectDir =
  file("prototypes/remote-apple-components")

// The Compose Multiplatform creation fork: AndroidX `remote-creation-compose` /
// `foundation` and the common write core, in AndroidX's own package with marked local patches only.
// See third_party/remote-creation-compose/PROVENANCE.md.
include(":third-party-remote-creation-writer")

project(":third-party-remote-creation-writer").projectDir =
  file("third_party/remote-creation-writer")

include(":third-party-remote-creation-compose")

project(":third-party-remote-creation-compose").projectDir =
  file("third_party/remote-creation-compose")

// The vendored, locally patched AndroidX embedded player — the Android comparison lane the CMP
// player above is measured against. Provenance and the patch log are in
// `third_party/rc-embedded-player/PROVENANCE.md`.
include(":third-party-rc-embedded-player")

project(":third-party-rc-embedded-player").projectDir = file("third_party/rc-embedded-player")

// The vendored TypeScript player's committed browser bundle, wrapped as one publishable zip for
// the same reason `:rc-player-wasm-dist` is. See `third_party/remote-compose-player/PROVENANCE.md`.
include(":third-party-remote-compose-player-dist")

project(":third-party-remote-compose-player-dist").projectDir =
  file("third_party/remote-compose-player-dist")

// Horologist's Lottie → Remote Compose compiler, vendored because Horologist publishes no artifact
// for it. It is what the UI builder's Wear `LottiePlayer` export compiles against. See

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

# rc-players

Remote Compose players — the runtimes that read a `.rc` document and draw it.

A `.rc` document is [Remote Compose](https://developer.android.com/jetpack/androidx/releases/compose-remote)'s
wire format: a compact, versioned operation stream that describes a piece of UI without shipping
code. This repository holds the players that interpret one, on every surface this stack targets, plus
the vendored AndroidX player they are all measured against.

## What is here

### The Compose Multiplatform player (`rc-player/`)

Written here, published to Maven Central, and the only player in this repository that is a supported
API. Four modules, bottom-up — each is the base of the next, and the dependency arrow is also the
dependency-weight arrow:

| Module | Artifact | What it is |
| --- | --- | --- |
| `rc-player/trace` | `rc-player-trace` | The tracing seam. An `expect`/`actual` facade so the stack above can open spans on any target. |
| `rc-player/protocol` | `rc-player-protocol` | The `.rc` wire codec and operation model: reader, writer, immutable operation IR, and the generated AndroidX operation inventory. **No Compose dependency at all** — usable from a build tool or a server. |
| `rc-player/runtime` | `rc-player-runtime` | Document semantics with no drawing: expression evaluation, animation timelines, layout tree, theming, named values. Still no Compose. |
| `rc-player/compose` | `rc-player-compose` | The Compose Multiplatform renderer. `RcComposePlayer` draws a document; this is the module most consumers want. |

Targets: JVM, `iosArm64`, `iosSimulatorArm64` and `wasmJs`. There is no `iosX64` slice anywhere in
the stack — Compose Multiplatform 1.11 stopped publishing the Intel simulator variant — so Intel Macs
cannot build against it.

All four run `explicitApi()` and an ABI dump gate: `checkKotlinAbi` diffs the real public surface
against the committed dumps in each module's `api/`, so a change to the published API shows up as a
diff in review. Regenerate with `./gradlew updateKotlinAbi`.

### The browser host (`rc-player/wasm`)

The CMP player compiled to WebAssembly and wrapped in a small embed contract, published to npm as
[`@yschimke/remote-compose-player-cmp`](https://www.npmjs.com/package/@yschimke/remote-compose-player-cmp)
rather than to Maven. It renders a document in an iframe driven by query parameters and
`window.rcPlayerLoad`. The distribution carries its own font faces (`rc-player/wasm/dist-assets/`)
because the lane is manifest-only and never fetches: a family the bundle does not carry fails the
availability check outright.

`wasmPlayerDist` builds it and enforces a size budget — a ratchet, not a target, so an unintended
jump fails the build.

### The iOS framework (`Package.swift`)

`rc-player/compose`'s iOS targets, assembled into `RcComposePlayer.xcframework` and distributed
through Swift Package Manager. `Package.swift` is **rewritten by the release job** on every release —
it has to describe an asset that already exists, and SPM verifies the checksum at resolve time, so
editing the URL or checksum by hand is never right. Usage is documented, and type-checked in CI,
in [`docs/design/RC_PLAYER_SWIFT.md`](docs/design/RC_PLAYER_SWIFT.md).

### The vendored AndroidX player (`third_party/`)

Not a supported API — this is the repository's pinned, locally patched copy of AndroidX's embedded
player, in two cuts:

* `third_party/rc-embedded-player` — the Android/Robolectric lane, published **for testing only** so
  parity jobs can select the vendored implementation independently from the newer player shipping
  inside androidx.dev's `remote-player-compose` snapshot.
* `third_party/rc-embedded-player-jvm` — the same player cut for desktop JVM, consumed by
  compose-ai-tools' `compose-preview serve` as its `cmp-jvm` lane.

Provenance and the full patch log: [`third_party/rc-embedded-player/PROVENANCE.md`](third_party/rc-embedded-player/PROVENANCE.md).

The CMP player exists to be compared against this one. `scripts/rc-lane-ab/` renders a catalog on
both lanes and scores them; `scripts/rc-text-metrics/` does the same for text metrics. Their
committed outputs are under `renders/`.

### Build-only tools

`rc-player/compat-tests` generates `.rc` fixtures with the real AndroidX writer, so the CMP reader is
tested against documents it did not produce. `rc-player/profile` and `rc-player/metrics` are the
profiling and text-metric harnesses. None of the three publishes anything.

## Consuming it

```kotlin
dependencies {
  implementation("ee.schimke.composeai:rc-player-compose:<version>")
}
```

`rc-player-compose` pulls the rest of the stack transitively. Take `rc-player-protocol` on its own
when you only need to read or write the wire format.

Swift Package Manager, by the bare version tag (SwiftPM only reads a tag as a semantic version when
the whole ref is `X.Y.Z`):

```swift
.package(url: "https://github.com/yschimke/rc-players.git", from: "<version>")
```

## Building

```bash
./gradlew build            # everything
./gradlew allTests         # the test suites
./gradlew ktfmtFormatAll   # format before committing — CI gates on ktfmtCheckAll
```

An Android SDK is needed for `third_party/rc-embedded-player` (`ANDROID_HOME`, or `sdk.dir` in
`local.properties`). The iOS targets only build on macOS; on Linux the Kotlin Gradle plugin disables
them with a warning rather than failing.

`-Pcomposeai.remoteCompose=snapshot` swaps the pinned AndroidX Remote Compose alphas for the
androidx-main post-submit build pinned in `settings.gradle.kts`. Use it to exercise an API that has
landed upstream but has not been released; the default `release` line is what CI and published
consumers see.

## Publishing

`./gradlew publishPlayers` publishes the five Maven artifacts; `publishPlayersToMavenLocal` is the
local equivalent. The version comes from `PLUGIN_VERSION` in the environment, or from
`.release-please-manifest.json` bumped to the next patch `-SNAPSHOT` for local builds. The npm bundle
and the XCFramework are assembled by the release workflow, which is the only thing that should write
`Package.swift`.

## Relationship to compose-ai-tools

These players were extracted from [yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools),
which now consumes them as published artifacts. The dependency runs both ways and that is deliberate:
this repository consumes `data-fonts-google` (one downloadable-font cache, shared by every lane) and
`data-layoutinspector-connector` (the production `compose/figma-svg` export) back from it. Neither
direction is a build-time cycle — both sides resolve released coordinates.

## Licence

Apache 2.0. The vendored AndroidX sources under `third_party/` carry their upstream Apache 2.0
licence and provenance; the font faces under `rc-player/wasm/dist-assets/fonts/` carry theirs
alongside them.

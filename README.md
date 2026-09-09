# rc-players

Remote Compose players — the runtimes that read a `.rc` document and draw it.

A `.rc` document is [Remote Compose](https://developer.android.com/jetpack/androidx/releases/compose-remote)'s
wire format: a compact, versioned operation stream that describes a piece of UI without shipping
code. This repository holds the players that interpret one, on every surface this stack targets, plus
the vendored AndroidX player they are all measured against.

## The players

Five of them, and the reason there are five is that each answers a question the others cannot. Four
render the same document on different surfaces; the fifth is upstream's, kept here as the reference
the others are measured against.

| Player | Target | Language / runtime | Supported? | Why it exists |
| --- | --- | --- | --- | --- |
| **CMP player** (`rc-player/compose`) | JVM · Android · iOS (`iosArm64`, `iosSimulatorArm64`) · macOS (`macosArm64`) · `wasmJs` | Kotlin Multiplatform + Compose Multiplatform | **Yes** — the one supported API here | The player written here. One implementation that draws a document natively on every surface this stack targets, with a platform-neutral wire model underneath it. This is what a consumer should depend on. |
| **Wasm host** (`rc-player/wasm`) | Browser | The CMP player compiled to WebAssembly | Yes, as an embed contract | Makes the CMP player renderable in a page with no server: an iframe driven by query parameters and `window.rcPlayerLoad`. It is the CMP player, not a second implementation — same pixels, different host. |
| **Apple XCFramework** (`Package.swift`) | iOS + Apple-silicon macOS, from Swift | The CMP player's native Apple targets, packaged for SwiftPM | Yes | Same code again, reachable from a Swift app that does not build Kotlin. Distribution, not implementation. |
| **Vendored AndroidX player** (`third_party/rc-embedded-player`) | Android (Robolectric) | Kotlin + Compose, vendored from androidx-main | **No** — testing only | The comparison lane. AndroidX's own embedded player, pinned to one commit and locally patched, so a parity number is attributable to a *known* player rather than to whichever alpha resolved that day. |
| **Vendored AndroidX player, JVM cut** (`third_party/rc-embedded-player-jvm`) | Desktop JVM (Skia) | The platform-neutral subset of the above, against Compose Desktop | **No** — testing/tooling only | Runs the same comparison headlessly, without Robolectric — and, by compiling the shared files against a non-Android target, makes "platform-neutral" a compiled fact rather than a claim. AndroidX publishes no desktop cut, so this one has no upstream to switch to. |
| **Vendored TypeScript player** (`third_party/remote-compose-player`) | Browser · Node · VS Code webview | TypeScript → Canvas2D, WebGL for shader ops | **No** — vendored, upstream elsewhere | A client-side lane that needs no Kotlin at all, so a viewer can render a captured `.rc` without a server-side daemon. Upstream is [yschimke/remotecompose-experiments](https://github.com/yschimke/remotecompose-experiments); changes are filed there. |

The shape of the whole thing: **one implementation, several hosts, two references.** The CMP player
is the product; the Wasm bundle and the XCFramework are it in different wrappers; the two vendored
AndroidX cuts and the TypeScript player are what it is checked against.

## What is here

### The Compose Multiplatform player stack (`rc-player/`)

The supported API, published to Maven Central. Four modules, bottom-up — each is the base of the
next, and the dependency arrow is also the dependency-weight arrow:

| Module | Artifact | What it is |
| --- | --- | --- |
| `rc-player/trace` | `rc-player-trace` | The tracing seam. An `expect`/`actual` facade so the stack above can open spans on any target. |
| `rc-player/protocol` | `rc-player-protocol` | The `.rc` wire codec and operation model: reader, writer, immutable operation IR, and the generated AndroidX operation inventory. **No Compose dependency at all** — usable from a build tool or a server. |
| `rc-player/runtime` | `rc-player-runtime` | Document semantics with no drawing: expression evaluation, animation timelines, layout tree, theming, named values. Still no Compose. |
| `rc-player/compose` | `rc-player-compose` | The Compose Multiplatform renderer. `RcComposePlayer` draws a document; this is the module most consumers want. |

There are no Apple x86_64 slices anywhere in the stack — Compose Multiplatform 1.11 stopped
publishing them — so Intel iOS simulators and Intel Macs cannot build against it.

All four run `explicitApi()` and an ABI dump gate: `checkKotlinAbi` diffs the real public surface
against the committed dumps in each module's `api/`, so a change to the published API shows up as a
diff in review. Regenerate with `./gradlew updateKotlinAbi`.

### The browser host (`rc-player/wasm`)

Published twice, because its two consumers resolve differently and neither channel substitutes for
the other:

* npm, as [`@yschimke/remote-compose-player-cmp`](https://www.npmjs.com/package/@yschimke/remote-compose-player-cmp) — how a web page consumes it;
* Maven, as `rc-player-wasm-dist` (a zip, `dist` classifier) — how a Gradle build stages it as a
  static sidecar.

Both are cut from `wasmPlayerDist`, which enforces a size budget — a ratchet, not a target, so an
unintended jump fails the build before an irreversible publish.

The distribution carries its own font faces (`rc-player/wasm/dist-assets/`) because the lane is
manifest-only and never fetches: a family the bundle does not carry fails the availability check
outright.

### The Apple framework (`Package.swift`)

`rc-player/compose`'s iOS and Apple-silicon macOS targets, assembled into
`RcComposePlayer.xcframework` and distributed through Swift Package Manager. **Consume it by a
bare `X.Y.Z` tag** — the release job writes the
real `url` and `checksum` into `Package.swift` and publishes that commit as the bare tag once the
XCFramework is uploaded. The copy on `main` is a permanent placeholder: a manifest cannot state the
checksum of an asset that does not exist yet, so `main` and the `v`-prefixed tag both fail the
checksum check by design. Editing the two values by hand is never right. Usage is documented, and
type-checked in CI, in [`docs/design/RC_PLAYER_SWIFT.md`](docs/design/RC_PLAYER_SWIFT.md).
Application targets normally select the `RcComposePlayerSwiftUI` product for direct `Data`, typed
events/errors, and SwiftUI/UIKit entry points; the `RcComposePlayer` product remains the raw binary
interop surface.

[`samples/apple-player`](samples/apple-player/) is the full application check: a SwiftUI document
player linked against the current release XCFramework build, built in CI for the arm64 iOS
Simulator, and
also runnable as a **Designed for iPad** desktop app on Apple-silicon Macs. It exercises application
integration that the generated-header sample cannot, including bundled and imported `.rc` files,
the UIKit controller lifecycle, required plist settings, and the framework's architecture matrix.

### The reference players (`third_party/`)

None of these is a supported API. Provenance, the pinned upstream commit and the full patch log for
the AndroidX ones: [`third_party/rc-embedded-player/PROVENANCE.md`](third_party/rc-embedded-player/PROVENANCE.md);
for the TypeScript one, [`third_party/remote-compose-player/PROVENANCE.md`](third_party/remote-compose-player/PROVENANCE.md).

The AndroidX player is vendored rather than resolved from the alpha coordinates on purpose, and it
is worth being clear that this is not stubbornness about a fork. Upstream's
`remote-player-compose` snapshot *does* now ship the embedded player. The problem is that it ships it
into the same package these sources used to occupy, so which bytes ran was decided by classpath
ordering — and on 2026-08-22 upstream reshaped the entry point and every live render died with a
`NoSuchMethodError`. These sources moved to `ee.schimke.composeai.rcembedded.player`, a package
nobody else publishes into, so both copies can sit on one classpath and a comparison number is
attributable by construction rather than by luck.

The local deltas over upstream are tracked as issues on this repository and are meant to shrink: two
of the original five were dropped when alpha17 restored them upstream.

`scripts/rc-lane-ab/` renders the focused Android View/vendored-embedded A/B, while
`scripts/rc-operation-conformance/render-lanes.sh` sends one manifest and the same `.rc` bytes
through AndroidX View, released and/or snapshot AndroidX embedded, vendored Android embedded,
vendored embedded JVM, and CMP JVM, then validates every result and writes all pairwise pixel
scores to `comparison.json`. `scripts/rc-text-metrics/` does the same for the focused text-metrics
set. Committed visual evidence is under `renders/`.

### Build-only tools

`rc-player/compat-tests` generates `.rc` fixtures with the real AndroidX writer, so the CMP reader is
tested against documents it did not produce. `rc-player/profile` and `rc-player/metrics` are the
profiling and text-metric harnesses. None of the three publishes anything.

`rc-player/demos` is the host half of a custom component, which is the one part of the stack the
published API cannot show on its own: the player draws a `Custom` component only if the host
registers a renderer for its config name. Two are demonstrated — `SupportSpannableString`, which the
player ships (`RcSpannableString`) because it is AndroidX's contract, and an editable text field,
whose keystrokes go back into the document through a `TEXT_RETURN` channel. `./gradlew
:rc-player-demos:run` opens both in a window, the `@Preview` functions render in the IDE, and
`RcDemoRenderTest` rasterizes them headless (`renders/rc-custom-components/`). It publishes nothing
either.

## Consuming it

```kotlin
dependencies {
  implementation("ee.schimke.composeai:rc-player-compose:<version>")
}
```

`rc-player-compose` pulls the rest of the stack transitively. Take `rc-player-protocol` on its own
when you only need to read or write the wire format.

The common Compose API also supports host-rendered custom components. A document names a component
through `LAYOUT_CUSTOM`; the host registers Compose content under that name. Because the content is
inserted into the player's existing Compose tree, it can be a native control, a named slot, or
another `RcComposePlayer`. This is the same API on Android, JVM, iOS and Wasm—the browser bundle is
only one possible host.

The implementation and cross-player conformance roadmap for completing the operation set on iOS
and macOS is in
[`docs/design/RC_CMP_APPLE_OPERATION_PLAN.md`](docs/design/RC_CMP_APPLE_OPERATION_PLAN.md).

```kotlin
lateinit var components: RcCustomComponentRegistry
components =
  RcCustomComponentRegistry(
    "slot:hero" to { _, modifier -> Hero(modifier) },
    "rc:document" to { component, modifier ->
      childDocuments[component.text(DOCUMENT_KEY)]?.let { child ->
        RcComposePlayer(child, modifier, theme = theme, customComponents = components)
      }
    },
  )

RcComposePlayer(compositeDocument, customComponents = components)
```

Custom properties can contain literals, live float references, text references and declared
float/text return channels. See
[`docs/design/RC_COMPOSITION.md`](docs/design/RC_COMPOSITION.md) for the composite-document, slot and
state-ownership model.

Swift Package Manager, by the **bare** version tag — not `main`, and not the `v`-prefixed release
tag, neither of which carries a resolvable checksum:

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
`local.properties`). The Apple targets only build on macOS; on Linux the Kotlin Gradle plugin disables
them with a warning rather than failing.

`-Pcomposeai.remoteCompose=snapshot` swaps the pinned AndroidX Remote Compose alphas for the
androidx-main post-submit build pinned in `settings.gradle.kts`. Use it to exercise an API that has
landed upstream but has not been released; the default `release` line is what CI and published
consumers see.

### What CI runs, and where

Two lanes in [`.github/workflows/ci.yml`](.github/workflows/ci.yml), split by what the host can do
rather than by what the change touched:

| | Linux (`ubuntu-latest`) | macOS (`macos-15`) |
| --- | --- | --- |
| JVM suites for the four published modules, `compat-tests`, `metrics`, `profile` | ✅ | ✅ (via `allTests`) |
| Vendored Android player, Robolectric (`testDebugUnitTest`) | ✅ | |
| Vendored JVM and TypeScript players | ✅ | |
| `macosArm64Test`, `iosSimulatorArm64Test`, `wasmJsBrowserTest` | | ✅ |
| `iosArm64` — device | | compiled only; a hosted runner has no device to run it on |
| The shipped Wasm bundle, in a real browser | ✅ `scripts/wasm-smoke` | |
| ABI gate (`checkKotlinAbi`) | | ✅ — the dumps cover the Apple klibs, which only build here |
| XCFramework link, Swift type check, Xcode packaging | | ✅, and **only** when the change can affect them |

Both lanes run on every pull request. Only the last row is path-gated (the `apple-changes` job): the
release link is what makes the macOS lane expensive, and a change that cannot reach the Apple
artifacts should not pay for it. Test execution is never gated — a lane that can only run here is
worth its minutes on every change, and the previous arrangement, where the path filter skipped the
whole job, meant a change confined to `rc-player/wasm` or `third_party/` merged without a single
Kotlin/Native or wasm test running.

Each lane then runs [`scripts/assert-test-execution.sh`](scripts/assert-test-execution.sh) over the
JUnit XML. `allTests` resolves its members per target on the runner, so a target that cannot run
there drops out of the graph and leaves the job green; the script fails it instead.

**No Android emulator lane, deliberately.** `third_party/rc-embedded-player` has no `androidTest`
source set, so one would boot an emulator to run nothing. Its drawing behaviour is not unverified
for want of a device: the suite is Robolectric-backed and rasterizes through
`RcEmbeddedRenderHarness` / `RcAndroidxEmbeddedRenderHarness`, the same lane
`scripts/rc-operation-conformance/render-lanes.sh` compares pixel-for-pixel against the AndroidX
View player and the JVM cut. Revisit this only alongside instrumented tests that assert something
Robolectric cannot — real Skia text shaping, or hardware-accelerated `RenderNode` behaviour.

### The Wasm browser smoke run

```bash
./gradlew :rc-player-wasm:wasmPlayerTestDist
npm --prefix scripts/wasm-smoke ci
npm --prefix scripts/wasm-smoke exec -- playwright install chromium
npm --prefix scripts/wasm-smoke test
```

`wasmPlayerTestDist` stages the optimized bundle next to four `.rc` documents written by the real
AndroidX writer; [`scripts/wasm-smoke/smoke.mjs`](scripts/wasm-smoke/smoke.mjs) serves that
directory and opens each of them in headless Chromium. A case passes only if the page reaches
`data-rc-player-state="ready"`, publishes the expected embed-contract version on both exports
(`window.rcPlayerContractVersion` and `data-rc-player-contract`, which must agree), writes nothing to
`console.error`, and produces a canvas that is not blank. It also covers the two halves of the embed
contract no Kotlin test can reach — the error marker for a `?src=` that 404s, and
`window.rcPlayerLoad`'s warm document swap, which has to repaint the viewport rather than leave the
outgoing document on screen. `CHROMIUM_EXECUTABLE` points it at a browser Playwright did not install
itself.

This is a different lane from `wasmJsBrowserTest`, which runs the Kotlin `commonTest` sources: this
one runs the *artifact* — the production-compiled `rcPlayer.wasm`, `index.html`, the `js-joda`
import map and the bundled font manifest, exactly as they ship to npm.

## Publishing

`./gradlew publishPlayers` publishes the Maven artifacts; `publishPlayersToMavenLocal` is the local
equivalent.

**The version line continues compose-ai-tools'.** `rc-player-trace`, `-protocol`, `-runtime`,
`-compose` and `third-party-rc-embedded-player` were published from there through `1.54.0`, so this
repository's manifest starts at `1.54.0` and the first release cut here is `1.55.0`. Restarting at
`0.1.0` would have published a version *below* what consumers already resolve — a downgrade to
anything using a range or a BOM, and silently invisible to everything else. `rc-player-wasm-dist`,
`third-party-rc-embedded-player-jvm` and `remote-compose-player-js-dist` are new coordinates and
first appear at that release. The version comes from `PLUGIN_VERSION` in the environment, or from
`.release-please-manifest.json` bumped to the next patch `-SNAPSHOT` for local builds. The npm bundle
and the XCFramework are assembled by the release workflow, which is the only thing that should write
`Package.swift`.

## Relationship to compose-ai-tools and compose-preview-daemon

These players were extracted from [yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools),
which now consumes them as published artifacts. This repository consumes two data extractors in
turn — `data-fonts-google` (one downloadable-font cache, shared by every lane) and
`data-layoutinspector-connector` (the production `compose/figma-svg` export) — which came back
from compose-ai-tools until they moved to
[yschimke/compose-preview-daemon](https://github.com/yschimke/compose-preview-daemon) with the
rest of the extractors (compose-ai-tools#5336). So the edges are: compose-ai-tools → rc-players,
and rc-players → compose-preview-daemon, which depends on neither. Both are released coordinates
(`composeai-preview-daemon` in `gradle/libs.versions.toml` pins the second), so nothing is a
build-time cycle.

## Licence

Apache 2.0. The vendored AndroidX sources under `third_party/` carry their upstream Apache 2.0
licence and provenance; the font faces under `rc-player/wasm/dist-assets/fonts/` carry theirs
alongside them.

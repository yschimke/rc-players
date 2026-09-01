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
| **CMP player** (`rc-player/compose`) | JVM · Android · iOS (`iosArm64`, `iosSimulatorArm64`) · `wasmJs` | Kotlin Multiplatform + Compose Multiplatform | **Yes** — the one supported API here | The player written here. One implementation that draws a document natively on every surface this stack targets, with a platform-neutral wire model underneath it. This is what a consumer should depend on. |
| **Wasm host** (`rc-player/wasm`) | Browser | The CMP player compiled to WebAssembly | Yes, as an embed contract | Makes the CMP player renderable in a page with no server: an iframe driven by query parameters and `window.rcPlayerLoad`. It is the CMP player, not a second implementation — same pixels, different host. |
| **iOS XCFramework** (`Package.swift`) | iOS, from Swift | The CMP player's iOS targets, packaged for SwiftPM | Yes | Same code again, reachable from a Swift app that does not build Kotlin. Distribution, not implementation. |
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

There is no `iosX64` slice anywhere in the stack — Compose Multiplatform 1.11 stopped publishing the
Intel simulator variant — so Intel Macs cannot build against it.

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

### The iOS framework (`Package.swift`)

`rc-player/compose`'s iOS targets, assembled into `RcComposePlayer.xcframework` and distributed
through Swift Package Manager. `Package.swift` is **rewritten by the release job** on every release —
it has to describe an asset that already exists, and SPM verifies the checksum at resolve time, so
editing the URL or checksum by hand is never right. Usage is documented, and type-checked in CI, in
[`docs/design/RC_PLAYER_SWIFT.md`](docs/design/RC_PLAYER_SWIFT.md).

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

`scripts/rc-lane-ab/` renders a catalog on both the Android lanes and scores them;
`scripts/rc-text-metrics/` does the same for text metrics. Their committed outputs are under
`renders/`.

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

The common Compose API also supports host-rendered custom components. A document names a component
through `LAYOUT_CUSTOM`; the host registers Compose content under that name. Because the content is
inserted into the player's existing Compose tree, it can be a native control, a named slot, or
another `RcComposePlayer`. This is the same API on Android, JVM, iOS and Wasm—the browser bundle is
only one possible host.

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

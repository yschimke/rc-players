# rc-players

Remote Compose players — the runtimes that read a `.rc` document and draw it.

A `.rc` document is [Remote Compose](https://developer.android.com/jetpack/androidx/releases/compose-remote)'s
wire format: a compact, versioned operation stream that describes a piece of UI without shipping
code. This repository holds the players that interpret one, on every surface this stack targets, plus
the vendored AndroidX player they are all measured against.

## The players

Seven lanes are listed because implementation, packaging, hosting, and comparison answer different
questions. The supported renderer remains CMP; UIKit is an explicitly experimental second renderer.

| Player | Target | Language / runtime | Supported? | Why it exists |
| --- | --- | --- | --- | --- |
| **CMP player** (`rc-player/compose`) | JVM · Android · iOS (`iosArm64`, `iosSimulatorArm64`) · macOS (`macosArm64`) · `wasmJs` | Kotlin Multiplatform + Compose Multiplatform | **Yes** — the one supported API here | The player written here. One implementation that draws a document natively on every surface this stack targets, with a platform-neutral wire model underneath it. This is what a consumer should depend on. |
| **Wasm host** (`rc-player/wasm`) | Browser | The CMP player compiled to WebAssembly | Yes, as an embed contract | Makes the CMP player renderable in a page with no server: an iframe driven by query parameters and `window.rcPlayerLoad` / `window.rcPlayerLoadBytes`, or by `postMessage` from another origin. It is the CMP player, not a second implementation — same pixels, different host. |
| **Apple XCFramework** (`Package.swift`) | iOS + Apple-silicon macOS, from Swift | The CMP player's native Apple targets, packaged for SwiftPM | Yes | Same code again, reachable from a Swift app that does not build Kotlin. Distribution, not implementation. |
| **Native UIKit POC** (`Sources/RcNativePlayerUIKit`) | iOS | Pure Swift + UIKit/Core Graphics/Core Text | **Experimental** | A second renderer beside CMP. It explores an idiomatic native component tree and reports unsupported operations explicitly; it is not a replacement or compatibility claim. |
| **macOS player app** (`samples/macos-player`) | Apple-silicon macOS | SwiftUI host + CMP or experimental AppKit-native rendering | **Release utility** | Opens local `.rc` files and persists a renderer preference, so both Apple desktop paths can be exercised from one downloadable app. |
| **Linux desktop viewer** (`rc-player/desktop`) | x86-64 Linux | The CMP player on the JVM, in a Compose Desktop window | **Release utility** | Opens local `.rc` files (dialog, drag-and-drop or command line), fetches `google:` fonts, opens a document in the [web inspector](https://camaelon.github.io/remotecompose-experiments/inspector/), and renders headless to PNG with `--render-png`. Each release attaches a `.deb` and a self-contained `tar.gz`, both bundling a JRE. |
| **Vendored AndroidX player** (`third_party/rc-embedded-player`) | Android (Robolectric) | Kotlin + Compose, vendored from androidx-main | **No** — testing only | The comparison lane. AndroidX's own embedded player, pinned to one commit and locally patched, so a parity number is attributable to a *known* player rather than to whichever alpha resolved that day. |
| **Vendored TypeScript player** (`third_party/remote-compose-player`) | Browser · Node · VS Code webview | TypeScript → Canvas2D, WebGL for shader ops | **No** — vendored, upstream elsewhere | A client-side lane that needs no Kotlin at all, so a viewer can render a captured `.rc` without a server-side daemon. Upstream is [yschimke/remotecompose-experiments](https://github.com/yschimke/remotecompose-experiments); changes are filed there. |

The shape of the whole thing: **one supported implementation, one experimental renderer, two CMP
hosts, and two references.** The CMP player is the product; the Wasm bundle and XCFramework are it
in different wrappers, and the macOS app and Linux viewer are downloadable windows onto it. The native UIKit POC sits beside it, while the vendored AndroidX player and
the TypeScript player remain comparison lanes. There is no separate JVM reference player: the
vendored player's JVM cut was removed on 2026-09-25, and where a JVM player is needed, the CMP player
is it.

### Player ids

Every tool that lets you choose a player — `compose-preview serve`'s `?rcPlayer=`, the daemon's
`RemoteComposeOverride.playerId`, a capture's `capturePlayer`, the `rc-compare` columns — names it
with one of these ids. **The id names the implementation that draws**, and `cmp-` means the CMP
player above and nothing else: `cmp-android`, `cmp-jvm` and `cmp-wasm` are one codebase on three
hosts.

| id | Implementation | Where it runs | Also accepted |
| --- | --- | --- | --- |
| `cmp-android` | CMP player (`rc-player-compose`) on Android | the preview daemon, under Robolectric | — |
| `cmp-jvm` | CMP player on the desktop JVM, over Skiko | `compose-preview serve`'s `rc-render-jvm` subprocess | `rcplayer-jvm` |
| `cmp-wasm` | CMP player compiled to Wasm (`rc-player/wasm`) | the browser | `rcplayer-wasm` |
| `androidx-embedded` | Vendored AndroidX embedded player (`third_party/rc-embedded-player`) | the preview daemon, under Robolectric | `embedded` |
| `androidx-view` | AndroidX `remote-player-view` `RemoteComposePlayer` (an Android `View`) | the preview daemon, under Robolectric | `java`, `view` |
| `camaelon-js` | Vendored TypeScript player (`third_party/remote-compose-player`) | the browser | `js` |

`androidx-view` is not in this repository: it is AndroidX's own published player, drawn by the
daemon for comparison. The two AndroidX players are the only ones a capture bakes through, so a
capture records `androidx-embedded` or `androidx-view`.

**`cmp-android` used to mean the AndroidX embedded player**, and some published links and capture
sidecars still say it with that meaning. As a request it now selects the CMP player. In a
`capturePlayer` field the old meaning is still read as `androidx-embedded`, because the CMP player
never captures, so that field cannot hold the new one. The same goes for the bare `cmp`, which is
no longer accepted anywhere: it cannot say which of the three hosts it means.

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

Beside the stack, and depending on `protocol` and `runtime` rather than the other way round:

| Module | Artifact | What it is |
| --- | --- | --- |
| `rc-player/edit` | `rc-player-edit` | Rewrites over a decoded document, for code that changes a `.rc` file rather than plays it: embed URL bitmaps inline, hardcode host-bound named values, collapse state layouts whose index can no longer change to their one live branch — when the hidden branches hold only layout, modifiers, drawing and touch handlers, so nothing outside can depend on them — fold and strip what a document no longer needs, check or adapt a document for the player it targets, and combine two documents into the branches of one state layout a host switches through a named value — identical definitions carried once, and the components both screens show (or that the caller pairs) given one animation id, so they move between their positions. No Compose. |

```kotlin
val document = RcDocumentCodec.decode(bytes)
val baked =
  document
    .embedBitmapUrls { url -> httpClient.get(url).readBytes() } // may suspend
    .hardcodeNamedValues(mapOf("tab" to RcHardcodedValue.Integer(2)))
    .hardcodeUnboundNamedValues(bound = setOf("progress"))
    .collapseConstantStateLayouts()
    .optimize()
File("baked.rc").writeBytes(RcDocumentCodec.encode(baked))
```

### Creation (`rc-creation/`, `third_party/remote-creation-*`) — experimental

Authoring Remote Compose from Compose Multiplatform, beside the player so one change can move both.
A fork plus a layer; the design is in [`docs/design/RC_CREATION_BRIDGE.md`](docs/design/RC_CREATION_BRIDGE.md).

| Module | Artifact | What it is |
| --- | --- | --- |
| `third_party/remote-creation-writer` | `third-party-remote-creation-writer` | The common `RemoteWriter` and `.rc` byte writer under the fork. |
| `third_party/remote-creation-compose` | `third-party-remote-creation-compose` | AndroidX `remote-creation-compose` and `remote-foundation` for Compose Multiplatform, in AndroidX's package with marked local patches ([PROVENANCE](third_party/remote-creation-compose/PROVENANCE.md)). Declares AndroidX's capabilities: on Android, take this or AndroidX's, never both. |
| `rc-creation/bridge` | `rc-creation-bridge` | rc-players' own creation API: `RcHostBridge` (host values, actions and Compose content by name) and `RcComponentIds` (stable component ids a host chooses), plus published `RemoteInput` creation helpers for the [input controls contract](docs/design/RC_INPUT_COMPONENTS.md). |
| `rc-player/embedded` | `rc-player-embedded` | `RcEmbeddedPlayer`: remote content composed live in-process and played by `RcComposePlayer`, re-rendered per edit instead of re-captured. |

The layer's API is `@ExperimentalRcCreationApi`; opt in to use it.

`optimize()` makes a document smaller and cheaper to play without changing what it plays: it
strips remarks and debug messages, folds every value producer whose value can never change into a
constant (integer and float expressions, colour expressions and the channels read from a colour,
merged, formatted and looked-up text — each evaluated by the player itself), drops repeated
identical definitions, and removes definitions nothing reads. `sizeReport()` shows where the bytes
are. Over the catalog corpus that is 7% (1.69 MB to 1.59 MB), every document checked to give every
remaining value exactly what the original did.

It pays most on a widget whose host values are baked in, as above: hardcoding turns the named
colours and values a document reads into constants, and `optimize()` after it turns what is
derived from them into constants too. Over the corpus, hardcoding every named value and
optimising takes it to 1.47 MB (13% smaller), folds nine in ten colour channel reads and two
thirds of the colour expressions, and cuts the time the player spends computing values each frame by about a
third.

It also checks a document against the player it is meant for. `RcTargetProfiles` names the
profiles AndroidX's creation API writes for — `WIDGETS_V6` (launcher widgets on Android 16),
`WIDGETS_V7`, `ANDROIDX`, `ANDROIDX_EXPERIMENTAL`, `WEAR_WIDGETS` — with operation sets transcribed
from AndroidX's own tables; `RcTargetProfile.of(apiLevel, profiles)` builds any other.

```kotlin
val wear = RcTargetProfiles.WEAR_WIDGETS
val document = wear.decode(bytes) // resolves `Skip` guards as the Wear player does
document.validateFor(wear) // every operation, field and header the player would reject
val adapted = document.adaptTo(wear) // inline macros, fold constants, re-declare the header
if (!adapted.isPlayable) println(adapted.violations)
```

A target without `CoreText` — Android 16 launcher widgets, Wear widgets — gets the older
`TextLayout` wherever it can say the same thing. Material typography also sets letter spacing,
line height and font axes, which `TextLayout` has no field for:
`adaptTo(target, approximateText = true)` drops those and lists each drop in
`RcProfileAdaptation.approximations`; underline, strikethrough and autosize still block. To serve
older devices a different design instead, keep a separate template per profile and check each with
`validateFor`.

To keep that styling rather than drop it, draw the text: `coreTextAsImages(target, rasterizer,
display, wrapWidth)` replaces each styled `CoreText` whose text is constant with an embedded image
of it, in a box that keeps the component's id, modifiers and accessibility text, sized in dp so it
scales with the device. A colour set at playback stays live: a Wear host's named colour, or a
`ColorTheme` that `adaptTo` turns into Android 16 theme sections, tints a white mask of the text
drawn in a canvas. Launcher widgets at level 7 have `CoreText` and `ColorTheme` and need neither.
`RcComposeTextRasterizer` (in `rc-player-compose`) draws with this player's own text stack and
fonts; on the JVM, `coreTextWidths(density, typefaces)` measures where the player wraps each text,
so a paragraph breaks its lines where it did. Over the catalog served to Wear widgets, 340 texts in
213 documents become images (294 of them tinted masks), each document at least as close to its
`CoreText` original as with the styling dropped and 184 matching it. Evidence — original, images,
styling dropped — is under [`renders/rc-text-images/`](renders/rc-text-images/).

```kotlin
val widths = document.coreTextWidths(density = 2f, typefaces)
val drawn =
  document.coreTextAsImages(wear, RcComposeTextRasterizer(typefaces), RcDisplay(2f)) { widths[it] }
val adapted = drawn.document.adaptTo(wear)
```

Neither API level 6 nor Wear has `ColorTheme`, the light/dark pair of system colours.
`adaptTo(target, setOf(RcApproximation.THEME_COLORS))` replaces each one with its recorded
fallbacks (`colorThemesAsFallbacks()`): in theme sections, the way AndroidX's level 6 widget writer
writes a themed colour, or as the dark fallback on Wear. The device's palette is lost. Evidence is
under
[`renders/rc-edit-profile-fallbacks/`](renders/rc-edit-profile-fallbacks/).

Android 16's launcher also reads paddings, and background and border colours and sizes, only as
literals, and it reads a background or border written as a colour reference as a transparent
one: `validateFor(WIDGETS_V6)` reports each, and `adaptTo` folds any that are constant. A
`RemoteDensity.Host` widget sizes its text and spacing as expressions over the player's density,
which no level 6 player can take. `adaptTo(target, approximations, RcDisplay(density, fontScale))`
bakes those expressions (`hardcodeDensity`) where the target needs literals, and converts the
border widths and offsets a level 7 header with legacy density behaviour has in pixels into the dp
Android 16's level 6 header has them in. Without a display it assumes `RcDisplay.DEFAULT`, a 420dpi
phone. `needsDisplay(target)` says whether the adaptation depends on the display at all — every
launcher catalog widget does for `WIDGETS_V6`, none for `WIDGETS_V7` — so a server asks the client
for its density only when it does, and serves one document to every display otherwise. Every
launcher catalog widget then plays on Android 16, and is 21% smaller after `optimize()`.

There are no Apple x86_64 slices anywhere in the stack — Compose Multiplatform 1.11 stopped
publishing them — so Intel iOS simulators and Intel Macs cannot build against it.

All five run `explicitApi()` and an ABI dump gate: `checkKotlinAbi` diffs the real public surface
against the committed dumps in each module's `api/`, so a change to the published API shows up as a
diff in review. Regenerate with `./gradlew updateKotlinAbi`.

### The browser host (`rc-player/wasm`)

Published twice, because its two consumers resolve differently and neither channel substitutes for
the other:

* npm, as [`@yschimke/remote-compose-player-cmp`](https://www.npmjs.com/package/@yschimke/remote-compose-player-cmp) — how a web page consumes it;
* the GitHub Release, as `rc-player-wasm.zip` — how a Gradle build stages it as a static sidecar,
  through an ivy repository over the release downloads
  (`https://github.com/yschimke/rc-players/releases/download`, pattern `v[revision]/[module].[ext]`,
  module `ee.schimke.composeai:rc-player-wasm`, requested `@zip`).

It is not on Maven Central. Releases through 2.17.x also uploaded it there as `rc-player-wasm-dist`,
about 11 MB of the org's monthly Central release-size limit per release for a bundle nothing compiles
against; those versions stay on Central, and every release also carries the asset.

Both are cut from `wasmPlayerDist`, which enforces a size budget — a ratchet, not a target, so an
unintended jump fails the build before an irreversible publish.

A page can include the player with one script tag: `rc-cmp-player.js`, shipped in the
distribution beside `index.html`, owns the iframe and exposes `RcCmp.createPlayer(...)` and an
`<rc-cmp-player>` element, the same shape as the TypeScript player's `RC.createPlayer` and
`<rc-player>` ([embed contract](docs/design/RC_PLAYER_EMBED.md)). The drag-and-drop comparison page
([`samples/web-compare`](samples/web-compare/)) includes both libraries and draws a dropped `.rc`
file in each; stage and serve it with:

```
build-brief ./gradlew :rc-player-wasm:rcCompareSite
python3 -m http.server -d rc-player/wasm/build/rcCompareSite 8000   # open http://localhost:8000
```

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
The manifest is `swift-tools-version: 6.0` and builds every target in the Swift 6 language mode
(complete strict concurrency), so it needs a Swift 6 toolchain — Xcode 16 or newer; the deployment
floor stays iOS 13, macOS 12 and visionOS 1.
Application targets normally select the `RcComposePlayerSwiftUI` product for direct `Data`, typed
events/errors, and SwiftUI/UIKit entry points; the `RcComposePlayer` product remains the raw binary
interop surface.

The experimental `RcNativePlayerUIKit` product is published by the same bare version tag. Each
GitHub Release also carries `RcNativePlayerUIKit.swiftpackage.zip` and its SHA-256 file: a
self-contained, pure-Swift local package containing the native sources with no binary target. It
gives evaluators a small pinned, offline-resolvable artifact without changing the supported CMP
product.

The native player's tests are two SwiftPM test targets written with swift-testing:
`RcNativePlayerCoreTests` for the pure-Swift document core and `RcNativePlayerUIKitTests` for the
platform layer. Run them on the Mac with `RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift test` — native-only
mode keeps the Kotlin binary target out of the package graph — and on an iOS simulator with
`scripts/check-native-swift-ios-tests.sh`. On Linux the manifest declares only the core and its
tests, so `swift test --filter RcNativePlayerCoreTests` runs there too.

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
through AndroidX View, released and/or snapshot AndroidX embedded, vendored Android embedded, and
CMP JVM, then validates every result and writes all pairwise pixel
scores to `comparison.json`. On macOS, `--native-uikit` adds the packaged simulator player to that
same manifest contract. `scripts/check-native-uikit-comparison.sh` is the focused five-document
CMP/UIKit regression used by CI. `scripts/rc-text-metrics/` does the same for the focused
text-metrics set. Committed visual evidence is under `renders/`.

### Build-only tools

`rc-player/compat-tests` generates `.rc` fixtures with the real AndroidX writer, so the CMP reader is
tested against documents it did not produce. `rc-player/profile` and `rc-player/metrics` are the
profiling and text-metric harnesses. None of the three publishes anything.

For an Apple-only, simulator-oriented CMP/UIKit comparison, see
[`docs/design/APPLE_PLAYER_BENCHMARKS.md`](docs/design/APPLE_PLAYER_BENCHMARKS.md).

`rc-conformance` scores a player against AndroidX's own **conformance corpus** — 252 golds over 18
subsystems, each carrying its own compiled document — and writes `conformance-results.json` plus a
scorecard. It is how *"how conformant is this player?"* gets a number attached to it, against one
published reference rather than against the other lanes here. The corpus is upstream-unmerged and
therefore lives on the long-lived `vendor/androidx-rc-conformance` branch rather than on `main`; the
module resolves it as configuration, never by path. The design, the current CMP score and the work
list are in [`docs/design/RC_CONFORMANCE.md`](docs/design/RC_CONFORMANCE.md).

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

URL and file bitmap payloads load asynchronously through Coil 3 by default, using the app's
`SingletonImageLoader` and its caches. The player includes Ktor networking with JVM/Android,
Apple and browser engines. Inline bitmap decoding stays in the player. Pending requests cancel
when the document or loader changes or the player leaves composition.

Override image loading for one player, or provide `LocalRcImageLoader` around several players:

```kotlin
val images = RcImageLoader { bitmap -> hostImages.load(bitmap.data.decodeToString()) }
RcComposePlayer(document, imageLoader = images)

CompositionLocalProvider(LocalRcImageLoader provides images) {
  RcComposePlayer(document)
}
```

An override returns a Compose `ImageBitmap`, or null for an unavailable image. It owns its access
policy and recoverable errors; it must not block the composition dispatcher. Use
`RcImageLoader.Empty` to disable external loading. To keep Coil while configuring authentication,
fetchers or caching, configure Coil's singleton before the first player is composed.
`rememberCoilRcImageLoader()` also returns the default adapter explicitly. Support reports remain
conservative until image ids are supplied through `availableImageIds`.

Compose dependency versions are consumer requirements, including `implementation` dependencies
published at runtime scope. The experimental `third-party-rc-embedded-player` now requests AndroidX
Compose **1.12.1**, intentionally matching the CMP player's resolved Android Compose UI floor.
Renovate permits stable 1.12.x patches for these core coordinates; moving to a newer minor line
requires a coordinated compatibility review. Before this update its `1.10.0` direct pins already resolved Compose UI to
**1.11.4** through Remote Compose `1.0.0-alpha19`; the direct pins were not a promise of 1.10 runtime
compatibility. Compose UI 1.12.1's AAR metadata requires **compileSdk 37 and AGP 9.1.0** (other
transitive artifacts can impose additional requirements).

An application declaring an older Compose version will normally resolve the higher transitive
version. Strict older constraints can instead fail resolution; forcing or excluding dependencies
to retain an older runtime is unverified and can cause linkage failures. This update is therefore
unsuitable for clients that must remain on Compose 1.11 or earlier. See
[Gradle's conflict-resolution rules](https://docs.gradle.org/current/userguide/dependency_graph_resolution.html).

The supported `rc-player-compose` artifact uses the separate Compose Multiplatform dependency line
and `androidx-compose-for-cmp` Android pin; changing the vendored player's `androidx-compose` pin
does not change that artifact's requirements. It already requests AndroidX Google Fonts 1.12.1,
so it likewise does not promise support for an older forced Android Compose runtime. The protocol,
runtime and trace modules do not gain Compose dependencies from this update.

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

Custom component renderers can display document children using `component.Child(index, modifier)`
or `component.Children()`. `component.childCount` includes all direct slots;
`component.customChild(index)` exposes a custom child's properties even when that child's config
has no registered renderer. See the [upstream adaptation review](docs/design/RC_CMP_UPSTREAM_REVIEW_2026_10.md).

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

### Run Gradle through `build-brief`

[`build-brief`](https://bb.staticvar.dev) ([`static-var/build-brief`](https://github.com/static-var/build-brief),
MIT, a single Go binary with no runtime dependencies) sits in front of Gradle, writes every line
Gradle emits to a log file, and prints only what changes the next step: status, failed tasks, failed
tests, warnings, build scan URLs, and generated artifact paths. Gradle's exit code passes through
unchanged, so it is safe anywhere a bare `./gradlew` was used.

```bash
brew install static-var/tap/build-brief      # or: curl -fsSL https://bb.staticvar.dev/install.sh | bash
build-brief doctor                            # read-only; never runs Gradle
```

Then wrap the commands above, for example `build-brief ./gradlew build` or `build-brief ./gradlew
allTests`. The managed block at the end of [`AGENTS.md`](AGENTS.md) carries the per-command rules and
can be regenerated with `build-brief --install`.

On a shared developer host, agents use `scripts/agent-gradle.sh` instead of invoking that wrapper
directly. Use its normal profile for focused work and `scripts/agent-gradle.sh --exclusive allTests`
for broad multiplatform, Wasm, or Apple task graphs. The exclusive profile uses the same machine lock
as the other Compose Preview repositories; interactive commands and hosted CI bypass it. Pass the
Apple link's required `--max-workers=1` normally—the launcher preserves narrower explicit limits.

Worth knowing here specifically:

- Multi-platform builds and the render/fixture tasks benefit most; the brief retains failures and
  artifact paths while the full output remains available at the printed raw-log path.
- Report-style commands such as `tasks`, `projects`, `dependencies`, and `dependencyInsight` keep
  their report bodies.
- Do not add `--ci` to local commands. It is opt-in and is not implied by the environment; this
  repository's workflows continue to invoke Gradle directly.
- `.build-brief.json` is available for project-specific regex matches but is intentionally absent
  until this repository has a result line worth surfacing.

An Android SDK is needed for `third_party/rc-embedded-player` and for the published `rc-player/*`
modules' Android target (`ANDROID_HOME`, or `sdk.dir` in `local.properties`). The Apple targets only build on macOS; on Linux the Kotlin Gradle plugin disables
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
| Android host tests for all four published modules (`compose` under Robolectric, including a remote-m3 catalog sample) | ✅ | |
| Vendored Android player, Robolectric (`testDebugUnitTest`) | ✅ | |
| Vendored TypeScript player | ✅ | |
| `wasmJsBrowserTest` | ✅ | ✅ (via `allTests`) |
| `macosArm64Test`, `iosSimulatorArm64Test` | | ✅ |
| `iosArm64` — device | | compiled only; a hosted runner has no device to run it on |
| The shipped Wasm bundle, in a real browser | ✅ `scripts/wasm-smoke` | |
| ABI gate (`checkKotlinAbi`), Apple klibs included — Kotlin/Native cross-compiles them on Linux | ✅ | ✅ |
| Native Swift player tests (`RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift test`, and the same target on an iOS simulator) | | ✅, path-gated with the row below |
| XCFramework link, Swift type check, Xcode packaging | | ✅, and **only** when the change can affect them |

The Linux lane runs on every pull request and every push. The macOS lane runs on every push to
main, but on a pull request **only when the change touches the Apple projects** (the `lane` output of
the `apple-changes` job): `Package.swift`, `Sources`, the native Swift tests, the Apple samples and
scripts, the Kotlin `apple*`/`ios*`/`macos*`/`native*` source sets, or the CI and release workflows
themselves. A macOS runner queues for 30-80 minutes, and a Gradle dependency bump or a common-code
change used to pay that on every PR. The trade: such a change gets its Kotlin/Native test run, its
XCFramework link and its Swift checks from main's push run, after merge, rather than before. The
wasm browser tests and the ABI gate, which once made the macOS lane mandatory, run on Linux, so a
change confined to `rc-player/wasm` or `third_party/` still runs them before it merges (#77).

Inside the macOS lane, the last two rows are gated again by the wider `required` list, so a push to
main skips the release link and the Swift packaging when nothing they build from changed.

Each lane then runs [`scripts/assert-test-execution.sh`](scripts/assert-test-execution.sh) over the
JUnit XML. `allTests` resolves its members per target on the runner, so a target that cannot run
there drops out of the graph and leaves the job green; the script fails it instead.

**No Android emulator lane, deliberately.** `third_party/rc-embedded-player` has no `androidTest`
source set, so one would boot an emulator to run nothing. Its drawing behaviour is not unverified
for want of a device: the suite is Robolectric-backed and rasterizes through
`RcEmbeddedRenderHarness` / `RcAndroidxEmbeddedRenderHarness`, the same lane
`scripts/rc-operation-conformance/render-lanes.sh` compares pixel-for-pixel against the AndroidX
View player and the CMP player. Revisit this only alongside instrumented tests that assert something
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
anything using a range or a BOM, and silently invisible to everything else.
`remote-compose-player-js-dist` is a new coordinate and first appears at that release (as did
`rc-player-wasm-dist`, which is now a Release asset instead; see the browser host above).
`third-party-rc-embedded-player-jvm` was published from here for a while and no longer is: the
vendored player's JVM cut was removed, and a consumer that needs a JVM player should depend on
`rc-player-compose`. The version comes from `PLUGIN_VERSION` in the environment, or from
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


Portable input controls use a [defined v1 contract](docs/design/RC_INPUT_COMPONENTS.md): text,
choice, boolean toggle, numeric entry and bounded range. `rc-player-protocol` publishes `RcInputs`,
`RcInputField`, `rcInputComponent`, `rcInputDocument` and validation without a UI dependency;
`rc-creation-bridge` publishes `RemoteInput` for common Compose capture. Player capability and host
write permission remain separate. [Rendered examples](renders/rc-input-controls/README.md).

# Forked: Compose Multiplatform Remote Compose creation

Covers `:third-party-remote-creation-compose` (this directory) and
`:third-party-remote-creation-writer` (`../remote-creation-writer`), published as
`third-party-remote-creation-compose` and `third-party-remote-creation-writer`.

This is a fork of AndroidX's Remote Compose **creation** stack, ported to Compose Multiplatform, so
`@RemoteComposable` content can be authored and captured on every rc-players target.

The fork stays in **AndroidX's own package** (`androidx.compose.remote.creation.*`) and changes as
little as it can. Component libraries such as Wear `remote-material3`, catalog code, and code a UI
builder exports are all written against that API. They then compile against this fork in a CMP host
and against real AndroidX on an Android device, from the same source.

Anything rc-players adds to creation lives outside this fork, in its own package
(`ee.schimke.composeai.rccreation`, `:rc-creation-bridge`). The fork carries only the seams that
layer cannot reach from outside, and each one is a marked local patch.

## Upstream

- Repository: <https://github.com/androidx/androidx>
- AndroidX change [4307936](https://android-review.googlesource.com/c/platform/frameworks/support/+/4307936)
  patch set 17, commit `01398020c7ec20f51e51bfc2b65b9b425e338be9`, refreshed to androidx.dev
  snapshot `16547072` (androidx-main `40ff48144`), then to androidx-main `51b1c49d7`
  (2026-10-15, a superset of snapshot `16617687` = `522d8695c`).
- License: Apache-2.0
- Paths:

| This fork | AndroidX source |
| --- | --- |
| `src/commonMain/.../creation/compose` | `compose/remote/remote-creation-compose` |
| `src/commonMain/.../foundation` | `compose/remote/foundation/foundation` |
| `../remote-creation-writer` | write-side sources from `remote-core`, `remote-creation-core` and `remote-creation`, re-expressed as a common `RemoteWriter` |

## The CMP port it starts from

The CMP port of those sources was made in yschimke/remote-m3-catalog `vendor/` and published there
as `ee.schimke.remotecompose:*:4307936-ps17-cmp09`. Its `vendor/README.md` lists what the port took
from upstream and what it left out. This fork is that port's **commonMain**, copied from
remote-m3-catalog `9cb05a6`:

- **remote-creation-compose and remote-foundation:** `src/commonMain` is copied into `src/commonMain`.
- **remote-write-core:** `src/commonMain` and `src/commonTest` are copied into `../remote-creation-writer`.
- **Actuals:**
  - `src/skikoMain` (JVM, Wasm, iOS, macOS) holds the port's three `wasmJsMain` actuals. They have no
    platform dependencies.
  - `src/androidMain` holds the port's Android actuals.

These are deliberately **not** carried:

- The port's legacy JVM writer path (`jvmMain`, `jvmAndAndroidMain`, and `vendor/remote-core`'s
  roughly 340 Java files).
- Its AndroidX `remote-creation` Android integration.

Every target, Android included, captures through the common `RemoteWriter`.

Formatting: `./gradlew :third-party-remote-creation-compose:ktfmtFormat` uses ktfmt's kotlinlang
style, which AndroidX uses. It re-wraps lines in about 80 files where the port had drifted.
`../remote-creation-writer` is the port's own code and keeps Google style. Both are `explicitApi()`:
AndroidX's sources already were, and the writer's implicit visibilities and return types (in
`PaintBundleData` and `RemoteDocumentWriter`) were made explicit when it was first published.

## Refresh 16547072 → androidx-main 51b1c49d7

Base and target were pinned by hashing every source in the snapshots' `-sources.jar`s against git:
`16547072` matches `40ff48144` (no compose/remote change until 2026-10-08 09:03 UTC) and `16617687`
matches `522d8695c`. The target is the later `51b1c49d7`, to include `7ce0408b0`. No `remote-core`
wire format changed in the range.

Taken:

- `915075137`: `animateEnterExit` defaults `animationId` to -1 (0 disabled the animation) and takes
  an `animationId`; `sharedElement`/`sharedBounds`/`animateEnterExit` reject 0.
- `04130d542`: `RemoteTextStyle.fromTextStyle` picks the variation settings of the font matching
  the style's weight and style (CSS matching), through the port's `Font.variationSettings` compat.
- `d3e5a08a0`, `9cda2fbc7` (common part): `HostAction` always writes a named action and loses its
  `id` field; new `HostIdAction`, restricted `hostAction(id, metadata)` and
  `hostAction(name, RemoteBoolean)`. These write through the existing `RemoteActionData.Host` /
  `HostMetadata` / `HostNamed`.
- `83b3b6737`, `7b5e7c3cc`: `RemoteDensity` and `RemoteDp` decoupled from Compose UI, with the Dp
  and TextUnit API in `RemoteDensityCompose.kt` / `RemoteDpCompose.kt`. `RemoteDp` is now a
  `BaseRemoteState<Float>`. Its `cacheKey` stays `internal`, as `a07eab443` is not taken.
- `d5c248a6d`: `RemoteTimeVariables` and `RemoteConfiguration` are public.
- `ce05fb8a3`: `RemoteBoolean.select(RemoteBoolean, RemoteBoolean)` peepholes.
- `09ca86e4d`: `GraphicsLayerModifier` takes a `RemoteShape` and writes a rounded corner's radius
  (dynamic too) instead of a fixed 40. The `Shape` overloads and `GraphicsLayerScope` stay in
  `commonMain`, because CMP has the types. The unsupported-shape `Log.w` is dropped.
- `bb80833b5`, `3da7b37d6`: `RemoteCustomPropertiesScope.property(id, RemoteImageBitmap)`, and
  `RemoteCustomComponent(content)`, whose children are recorded into the custom component's child
  span.
- `f5b27d93b`: `drawScaledBitmap` without a description writes id 0, not an empty `TextData`. The
  writer's `contentDescription` is nullable.

Already in the port: `7ce0408b0` (`RemoteDocumentWriter.addPathData` already dedupes path data);
`a4e385e70` (the Android `Font.variationSettings` compat reads `ResourceFont` reflectively);
`7f692f209`; `6586caebb` and `792724d34` (`PaintTracker` is already common).

Not applicable:

- Android-only files the fork does not carry: `PendingIntentAction*`, `RCWidget`,
  `RemoteTextMeasure` (`109bc2783`), `LocalPlatform` (`ad23d88e4`), `69b8baeda`.
- Upstream's capture loop, which the port replaces: `7d994aebb`, `ae4347854`, `6c83948ca`,
  `188c0b4a8`, `b80603c16`, `861cb428e`.
- `97010f58b`: the `RcFloat` DSL is not carried. The port's caches already use `NanIdEquality`.
- Player, test and JSON-only changes: `ee7f84157`, `1cfe2843c`, `6c49ce3e2`, `0e6b76d61`,
  `adb54cfeb`.

Deliberately skipped:

- Android/common file splits that only serve upstream's module layout, which the port already
  splits its own way: `a12e3c520`, `7554d3b46`, `59e506463`, `8a77cf6b3`.
- The `RecordingCanvas` → `RemoteCanvas` inlining step `33de12812` (`skew`, `saveCount`), as before.
- `a07eab443`: it exposes cache keys to the library group for `remote-creation-expression`, which
  the fork does not carry.
- `c93e26675` (`currentProfileSupports*`): the port has no `Profile`. Its `supportedOperations`
  lists only opted-in operations, so the helpers would report false by default.
- `4e646d52e`: KDoc only, and it names upstream's `RemotePath`.

## Local patches

Every patched line sits under a `// LOCAL PATCH (rc-players)` comment. Each patch is generic enough
to offer upstream, and the list is meant to shrink.

| Where | What | Why |
| --- | --- | --- |
| `capture/CaptureRemoteDocument.common.kt` | `captureCommonRemoteDocumentInto(writer, …)`; the byte path calls it | Capture into any `RemoteWriter` |
| `capture/RemoteDocumentSession.kt` (new) | A composition kept alive under a host `CompositionContext`, re-rendered into a fresh writer on demand; reports when a state its last render read changes (`onRenderInputsChanged`) | The live, in-process path. It mirrors upstream's Android `captureRemoteDocument` loop, including its `RenderInvalidationTracker`. |
| `capture/CommonCreationState.kt` | `writer` settable (internal); `resetComponentIds()` | A session renders each frame into a fresh writer. Without the reset, every render renumbers every component (upstream has the same drift). |
| `layout/RemoteComposeApplier.kt` | `onChangesApplied` | Tells a session when to re-render |
| `modifier/ComponentIdModifier.kt` (new), `layout/RemoteComposeNode.kt`, `capture/CommonCreationState.kt`, `modifier/RemoteModifier.kt` | `RemoteModifier.componentId(id)`: a node takes a requested component id (at most `MaxExplicitComponentId`, -2,000,000, below both ranges a document allocates) instead of a positional one, once per render | Lets a host name components: an editor maps the player's geometry back to its own nodes (`RcComponentIds` in `:rc-creation-bridge`) |
| `capture/DisplayInfo.kt` | `RemoteDensityBehavior.wireValue` | Lets a writer outside the module describe the same display |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` | `startCustom` content section takes a fresh component id | **Port bug.** Upstream's `addContentStart` allocates one; the port reused the layout's own id, and rc-players' player rejects the duplicate. The same fix is in yschimke/remote-m3-catalog's vendor copy (yschimke/remote-m3-catalog#133); this patch retires when that lands and the fork is re-taken from it. |
| `src/androidMain/.../ComposeColorFilter.android.kt` | Inlines the port's blend-mode mapping | The rest of the port's Android sources are not carried |
| `modifier/DrawWithContentModifier.kt`, `layout/RemoteDrawScope.kt`, `layout/RemoteContentDrawScope.kt` | A `drawWithContent` after padding draws inside it: translated past the padding before it, its scope's `width`/`height` reduced by it, its content drawn back at the component's origin | **Port bug.** AndroidX writes it this way, and the player does not inset a draw modifier itself, so the fork drew a padded component's decoration over its padding |

## Upstream follow-ups

Problems found here that belong in AndroidX, not in a local patch. The fork keeps upstream's
behaviour until a fix lands there and a refresh takes it.

- **Dp header dimensions.** With `RemoteDensityBehavior.Dp`, `RemoteCreationDisplayInfo.size`
  (pixels) is written unscaled into a header the player reads as dp, so a 200 px display at
  density 2 becomes a 200 dp (400 px) document, and `RemoteConfiguration.windowWidth` reports
  200 dp instead of 100. Upstream: `RemoteCreationDisplayInfo.toCreationDisplayInfo()` in
  `compose/remote/remote-creation-compose/.../capture/RemoteCreationDisplayInfo.kt`. Here:
  `captureCommonRemoteDocument` and `RcEmbeddedPlayer.writerFor`, both marked `TODO(upstream)`.
  Raised in review of yschimke/rc-players#672.

## Refreshing

Apply the upstream delta to the files this fork carries, as the port's earlier refreshes did, rather
than re-copying. Then re-apply the patches above. `:rc-player-embedded:jvmTest` is the check that the
seams still hold.

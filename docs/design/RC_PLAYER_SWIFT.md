# The Remote Compose player from Swift

How `ee.schimke.composeai:rc-player-compose`'s iOS and macOS entry points are packaged, what they
look like from Swift, and the places where the Kotlin API reads worse across the boundary than it
does in Kotlin. Companion to [RC_CMP_WASM_PLAYER.md](RC_CMP_WASM_PLAYER.md), which covers the
player itself.

Implements #4068.

## Consuming it

> [!IMPORTANT]
> **Resolve a bare `X.Y.Z` tag, not `main` and not `vX.Y.Z`.** Only the bare tag carries a
> `Package.swift` describing a real asset. `main`'s copy is a permanent placeholder and the
> `v`-prefixed release tag is cut before the XCFramework exists, so both fail the checksum check —
> deliberately, rather than resolving to something unverified.
>
> A version has a usable bare tag when the release job's `Publish the Apple XCFramework` step
> succeeded for it. Check with:
>
> ```
> git ls-remote --tags https://github.com/yschimke/rc-players.git '1.*'
> ```
>
> Releases before this scheme worked (v1.58.1, v1.59.0) uploaded their XCFramework but never got a
> bare tag — their asset is on the release page and can be pinned by URL and checksum by hand, but
> `from:` will not find them.

```swift
// Package.swift
.package(url: "https://github.com/yschimke/rc-players.git", from: "1.60.0")
```

For an application target, depend on the `RcComposePlayerSwiftUI` product. It wraps the binary
product without hiding it, so an advanced consumer can still select `RcComposePlayer` and use the
generated Kotlin API directly.

```swift
// iOS
import RcComposePlayerSwiftUI
import SwiftUI

@MainActor
func makePlayer() -> RemoteComposePlayerView {
  RemoteComposePlayerView(
    data: documentData,
    configuration: .init(
      theme: .system,
      compatibility: .compatible,
      background: .transparent
    ),
    onEvent: { event in print(event) },
    onError: { error in print(error.localizedDescription) }
  )
}
```

The same source overlay exposes the AppKit-backed window added in #64 on Apple-silicon macOS 12 and
newer:

```swift
// macOS
import RcComposePlayerSwiftUI
import AppKit

@MainActor
func openPlayerWindow() {
  RemoteComposePlayerWindow.open(
    data: documentData,
    title: "Remote Compose",
    width: 800,
    height: 600,
    configuration: .init(theme: .system, compatibility: .strict),
    onEvent: { event in print(event) },
    onError: { error in print(error.localizedDescription) }
  )
}
```

Both entry points accept `Foundation.Data`, are isolated to the main actor, default all callbacks,
and translate the exported protocol hierarchy into Swift enums. The UIKit controller is also public
as `RemoteComposePlayerViewController`. Its `update(data:configuration:onEvent:onError:)` method
rebuilds the embedded Compose controller only when playback input changes; merely re-rendering a
SwiftUI parent updates the closures without resetting playback.

The iOS configuration defaults to `.opaque`, matching Compose's existing behavior. Set
`background: .transparent` to preserve the Metal surface's alpha and reveal the SwiftUI or UIKit
content behind portions of the document that do not draw. Changing the value rebuilds the embedded
Compose controller because opacity is a renderer creation setting; it does not require replacing
the surrounding `RemoteComposePlayerViewController`.

An iOS host should set `CADisableMinimumFrameDurationOnPhone` to `YES` in its application
`Info.plist` to let Compose use the display's full refresh rate. The player disables Compose's
strict plist assertion and emits one console notice when the entry is absent, so prototypes and
embedded hosts continue rendering at the host application's configured rate.

### Live named variables

Keep a `RemoteComposePlayerController` alongside the view to discover and update the document's
host-driven state without rebuilding the Compose controller:

```swift
// iOS
@MainActor
struct LivePlayer: View {
  @State private var playerController = RemoteComposePlayerController()

  var body: some View {
    RemoteComposePlayerView(data: documentData, controller: playerController)
  }

  func updateHostValues() {
    print(playerController.names)
    playerController.setFloat(0.75, for: "progress")
    playerController.setString("Ready", for: "status")
    playerController.setColor(0xff336699, for: "accent")
  }
}
```

Bare names resolve in the `USER:` namespace; explicitly namespaced declarations are accepted as
written. Each setter returns `false` for an absent name or a type mismatch. Metadata actions and
text-valued named actions also expose a pre-parsed `event.url` convenience property.

### Raw interop

The underlying binary product remains public and source-compatible. Its direct iOS call is:

```swift
// iOS
import RcComposePlayer
import UIKit

let controller = RcComposeViewControllerKt.RcComposeViewController(
  bytes: RcDataBridgeKt.rcByteArray(data: documentData),
  theme: .system,
  onEvent: { event in handle(event) },
  typefaces: RcTypefaceLoaderCompanion.shared.Default,
  onError: { message in show(message) }
)
```

The raw macOS equivalent is:

```swift
// macOS
import RcComposePlayer
import AppKit

RcComposeWindowKt.RcComposeWindow(
  bytes: RcDataBridgeKt.rcByteArray(data: documentData),
  title: "Remote Compose",
  width: 800,
  height: 600,
  theme: .system,
  onEvent: { event in handle(event) },
  typefaces: RcTypefaceLoaderCompanion.shared.Default,
  onError: { message in show(message) },
  lenient: false
)
```

Three things in that call are not what a Swift author would write from scratch, and all three are
properties of Kotlin/Native's Objective-C export rather than choices made here. They are spelled
out because the generated header is the only other place they are written down, and it ships inside
the zip.

**`RcComposeViewControllerKt.` is not a typo.** `RcComposeViewController` is a *top-level* Kotlin
function, and Kotlin/Native exports top-level declarations as static members of a class named after
their file — so the entry point lands on `RcComposeViewControllerKt`, not in the global namespace.
Read out of the generated header, the selector is
`RcComposeViewController(bytes:theme:onEvent:typefaces:onError:)`, retaining Kotlin's capital `R`.

**Every argument is required.** The Kotlin declaration defaults `theme`, `onEvent`, `typefaces`, and
`onError`; Objective-C has no default arguments, so the exported selector takes all five. Passing
`.system` and `RcTypefaceLoaderCompanion.shared.Default` reproduces the Kotlin defaults.

**A sixth argument selects the playback gate.** A second overload,
`RcComposeViewController(bytes:theme:onEvent:typefaces:onError:lenient:)`, exports beside the
five-argument one. `lenient: true` plays a document carrying any operation the player *knows*, drawing
nothing for the ones the iOS backend has no branch for, instead of refusing the whole document —
which is what a document written against a wider write profile than any reader profile lists needs
(`DrawTextOnCircle` is the standing case). Malformed data, an undeclared id and a missing typeface
still route to `onError`: those throw from inside the draw pass, and no mode plays them. It is an
overload and not a defaulted parameter precisely because of the paragraph above — a default
argument would have rewritten the five-argument selector out from under every existing caller.

**A seventh argument selects renderer opacity.** The additive
`RcComposeViewController(bytes:theme:onEvent:typefaces:onError:lenient:opaque:)` overload preserves
alpha in Compose's Metal surface when `opaque: false`. The containing UIKit views must also be
clear, which is why ordinary Swift and SwiftUI consumers should prefer the source overlay's
`background: .transparent` configuration instead of calling this overload directly.

**`Data` bridges through one native copy.** `KotlinByteArray` itself exports only `init(size:)`,
`get(index:)`, and `set(index:value:)`, so the binary framework provides an `NSData` bridge that
copies directly into pinned Kotlin memory. The source overlay uses it after rejecting sizes that do
not fit Kotlin's array index:

```swift
let bytes = RcDataBridgeKt.rcByteArray(data: documentData)
```

**Font-variation axes use the player's own type, on purpose.** A Swift host that supplies typefaces
implements `RcTypefaceLoader`, whose `typeface(family:variations:)` takes
[`RcFontVariations`](../../rc-player/compose/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/compose/RcFontVariations.kt)
— a list of `RcFontAxis` tag/value pairs — rather than Compose's `FontVariation.Settings`. That is
the one place the exported API deliberately does *not* mirror Compose. `FontVariation.Settings` is
nested inside an `object` from a module this framework does not export, so Kotlin/Native wrote it
into the header as an Objective-C class carrying `swift_name("Ui_textFontVariation.Settings")` whose
outer half was never emitted; Swift could not complete the mapping and warned on every consumer
build, ending with "please report this issue to the owners of 'RcComposePlayer'". Owning the type
removes the warning, and it costs nothing in expressiveness — a `.rc` document only ever carries a
tag and a float.

These are properties of the raw interop layer rather than defects — the call works exactly as
written. The `RcComposePlayerSwiftUI` source target closes them for ordinary adoption while leaving
this API available for custom typeface loaders and other advanced integration.

**"Works exactly as written" is checked, not asserted.**
`scripts/check-swift-sample.sh` compiles the source overlay for iOS and macOS, then extracts the
`swift` blocks above and type-checks them against the overlay and assembled XCFramework. The macOS
CI job runs it right after building the framework. That pairing lapsed
while [#4222](https://github.com/yschimke/compose-ai-tools/issues/4222) had the framework build
disabled — the script exits 0 when no framework is present, so it reported green while checking
nothing, and was unwired rather than left to do that. Both steps are back, in that order, so the
sample is type-checked on every pull request again. The document remains the tested artifact, with
no second copy of its calls to keep in sync. That catches changes in both the maintained Swift API
and Kotlin/Native's generated Objective-C export.

**Apple silicon only.** The XCFramework contains iOS device, Apple-silicon iOS simulator, and
Apple-silicon macOS slices. Compose Multiplatform 1.11.1 publishes the experimental
`macosArm64` renderer used by the AppKit window, but no Apple x86_64 variants; Intel simulators and
Intel Macs therefore cannot build against this. The build opts into the experimental macOS target
explicitly and CI links it before a release.

### Relationship to the Apple sample app

[#63](https://github.com/yschimke/rc-players/pull/63) is a useful starting point for a desktop app:
its document library, SwiftUI split view, file import/drop, themes, zoom, and keyboard commands are
largely platform-neutral. Its current renderer adapter is not—it wraps the UIKit entry point with
`UIViewControllerRepresentable`, so the Mac destination is Apple's “Designed for iPad” runtime.

The native macOS slice added here lets an AppKit or macOS SwiftUI application call
`RcComposeWindow`, but that opens a player-owned top-level window. Compose Multiplatform 1.11.1's
experimental native macOS API exposes `Window`; it does not expose the underlying Compose `NSView`
needed for an `NSViewRepresentable` equivalent of #63's in-place canvas. A native desktop version
can therefore reuse #63's shell immediately if a separate player window is acceptable. Reusing its
full split-view layout with the player embedded in the detail pane still needs an upstream
embeddable AppKit view API (or a substantial locally maintained copy of Compose's private host,
which this library deliberately does not take on).

## How it is built and shipped

| step | where |
|---|---|
| `iosArm64` + `iosSimulatorArm64` + `macosArm64` static frameworks | `rc-player/compose/build.gradle.kts` |
| combined into `RcComposePlayer.xcframework` | `assembleRcComposePlayerReleaseXCFramework` (registered by `XCFrameworkConfig`) |
| zipped reproducibly + SHA-256 | `:rc-player-compose:rcPlayerXcframeworkChecksum` |
| attached to the GitHub Release | `release.yml` → `publish-xcframework` |
| `Package.swift` pointed at it, bare `<version>` tagged | same job, via `scripts/update-package-swift.sh` |

**Why a separate Swift tag rather than the `v<version>` release tag.** SPM's `binaryTarget`
addresses a zip by URL and pins it by checksum, verified at resolve time. Both values can only be
written *after* the asset exists — so `Package.swift` at `v<version>` necessarily still describes the
*previous* release, and a consumer resolving that tag would download the wrong binary. The Swift tag
points at the commit made after the upload, which is the first commit where the file and the asset
agree.

**Why the Swift tag is bare `1.16.0` and not `swift/1.16.0`.** SwiftPM reads a ref as a semantic
version only when the *entire* ref is `X.Y.Z` or `vX.Y.Z`; a leading `v` is the only decoration it
strips. A `swift/1.16.0` ref is therefore not a version at all, and `.package(..., from: "1.16.0")`
never saw it.

It did, however, see **`v1.16.0`** — the release tag, whose `Package.swift` describes the *previous*
release by construction. So the old scheme did not make `from:` resolve nothing; it made it resolve
the **wrong framework**, which is worse and quieter. Measured against Swift 6.3: with both refs
present SwiftPM selects the bare tag, in both orderings (bare on the older commit and on the newer),
so once `publish-xcframework` has run the bare tag governs.

Two things follow, and they are the reason this is a mitigation rather than a cure:

- **There is a window.** `v<version>` exists from the moment the release chain writes it until
  `publish-xcframework` pushes the bare tag. A `from:` resolve inside it gets the stale framework.
  Normally minutes, since both happen in one release run.
- **A failed `publish-xcframework` leaves the window open indefinitely**, with a SwiftPM-visible
  `v<version>` pointing at the wrong binary. Re-run the job — it is idempotent, and it refuses to
  move an existing Swift tag that describes a different asset.

Closing it properly needs `Package.swift` at `v<version>` to be correct *already*, which the
checksum-after-upload ordering forbids, or the Swift package to live in its own repository where the
version tags are its own. Tracked on [#4068](https://github.com/yschimke/compose-ai-tools/issues/4068).

**And the binary is built from `v<version>`, not from `main`.** `main` can advance between the tag
being cut and `publish-xcframework` starting; building from the branch would attach a framework of
newer source to the older release, disagreeing with the Maven artifacts and source release that
`publish-gradle-plugin` builds from the tag. Only the `Package.swift` commit touches `main`, on a
fresh checkout taken after the upload.

**The zip is built reproducibly** (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder =
true`) because the checksum has to match the bytes a consumer downloads. Without it, a re-run of the
release job would produce a `Package.swift` that no longer matches the already-uploaded asset.

**CI links it.** Until this landed, `ci.yml` compiled the iOS *test* targets and never linked or
packaged a framework, so framework assembly was entirely unexercised — and the note at the top of
the `rc-player-tests` job records that `linkDebugTestIosSimulatorArm64` has died before. The player
job now runs `rcPlayerXcframeworkChecksum` as a separate Gradle invocation, deliberately: the two
release links are the heaviest work in the job and running them alongside the test compiles is what
has failed in the past.

## What the Kotlin API actually looks like from Swift

#4068 asked whether the retyped parameters from #4058 and #4060 read *worse* across the boundary
than the `Int` and `Map` they replaced. Read out of the generated Objective-C header rather than
guessed:

**`RcPlayerTheme` — better.** It exports as an Obj-C enum class with `light` / `dark` / `system`
class properties, so Swift writes `theme: .system`. That is a plain improvement on passing `-2`.

**`RcTypefaceLoader` — better.** It exports as an Obj-C *protocol*, so a Swift host implements
`families` and `typeface(family:settings:)` directly rather than assembling a dictionary of Kotlin
objects. The companion values are reachable as `RcTypefaceLoaderCompanion.shared.Default` /
`.Empty` — slightly awkward, and the one place the Kotlin form is nicer.

**`RcPlayerEvent` — worse, and unavoidably so.** A Kotlin `sealed interface` flattens to a bare
Obj-C protocol with no members:

```objc
__attribute__((swift_name("RcPlayerEvent")))
@protocol RCPRcPlayerEvent
@required
@end
```

so Swift gets no exhaustive `switch`, only casts against the concrete classes:

```swift
func handle(_ event: RcPlayerEvent) {
  if let action = event as? RcPlayerEventHostAction {
    …
  } else if let named = event as? RcPlayerEventHostNamedAction {
    …
  } else if let debug = event as? RcPlayerEventDebugMessage {
    …
  }
  // No compiler error when a new case is added — see below.
}
```

The cost is real: adding a case to `RcPlayerEvent` is a source-compatible change in Kotlin that
compiles fine in Swift and silently does nothing at runtime. Nothing in Kotlin/Native's Obj-C export
fixes that today. What it argues for is treating a new `RcPlayerEvent` case as a **minor-version,
release-noted** change rather than an additive one, since Swift consumers cannot be told by their
compiler.

**Type names are clean because the stack is exported.** The frameworks `export(...)`
`:rc-player-runtime`, `:rc-player-protocol` and `:rc-player-trace`, which is legal because each
module already depends on the next with `api`. Without it, Kotlin/Native prefixes every transitively
sourced type with its module name and a Swift consumer sees `Rc_player_runtimeRcPlayerEvent` and
`Rc_player_protocolRcDocument` in the callbacks and parameters it has to name.

## Not covered here

CocoaPods. SPM reuses the release chain that already exists; a podspec is a second artifact and a
second publish path to keep in step, and nothing has asked for it yet.

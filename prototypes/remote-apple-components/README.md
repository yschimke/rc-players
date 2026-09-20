# Remote Apple components (prototype)

An intentionally non-published experiment in authoring Apple-styled `.rc` documents with
AndroidX `remote-creation-compose`.

The important boundary is **style, not platform widgets**: these functions emit ordinary Remote
Compose operations, so the existing CMP player renders the same document on Android, iOS, macOS,
JVM, and Wasm. They do not embed UIKit or SwiftUI controls in the document.

The first vertical slice includes:

- `RemoteAppleButton` — bordered, prominent, destructive, and borderless treatments;
- `RemoteAppleToggle` — document-owned state and a remotely executable value-change action;
- `RemoteAppleProgressView` — determinate linear progress;
- `RemoteAppleSection` and `RemoteAppleLabel` — settings-style grouping and typography; and
- `RemoteAppleComponentGallery` — one capture-ready document exercising the set.

The second tranche adds:

- `RemoteAppleSegmentedPicker` — document-owned integer selection;
- `RemoteAppleStepper` — bounded increment/decrement actions and a live value label;
- `RemoteAppleBadge` / `RemoteAppleBadgeRow` — compact counts and status tokens;
- `RemoteAppleDisclosureRow` — host-routed navigation rows; and
- `RemoteAppleStatusRow` — semantic status text with a state-derived indicator.

`RemoteAppleControlsGallery` is the matching capture-ready second sheet.

```kotlin
val enabled = rememberMutableRemoteBoolean(true)

RemoteAppleTheme {
  RemoteAppleSection("Playback".rs) {
    RemoteAppleToggle("Download over cellular".rs, isOn = enabled)
    RemoteAppleButton(
      action = hostAction("play".rs),
      title = "Play".rs,
      style = RemoteAppleButtonStyle.Prominent,
    )
  }
}
```

## Why the SwiftUI portion is small

SwiftUI is the host rather than the authoring runtime. Capture the Kotlin content to `Data`, then
show it with the source overlay already shipped by this repository:

```swift
RemoteComposePlayerView(
  data: appleStyledDocument,
  configuration: .init(theme: .system, background: .transparent),
  onEvent: handleRemoteComposeEvent
)
```

This preserves one wire document and one renderer contract. A future native Swift authoring DSL
could wrap a document writer, but it should not be coupled to the SwiftUI player view or duplicate
the component implementation before this vocabulary has settled.

## Side-by-side macOS comparison

`swiftui/RemoteAppleComparisonApp.swift` is a small SwiftUI reference app. It displays the live
SwiftUI implementation beside the gallery captured from `remote-creation-compose` and rendered by
the CMP player. The same executable has headless commands for rendering the SwiftUI side and
producing a pixel diff, so the visual reference and the app cannot quietly diverge.

On macOS:

```bash
scripts/build-remote-apple-comparison.sh
open "build/remote-apple-comparison/Remote Apple Comparison.app"
```

The Remote Compose pane is a committed CMP render rather than a live embedded player. Compose
Multiplatform's current macOS API exposes a player-owned `Window`, not the `NSView` needed to place
it inside a SwiftUI `HStack`. The app says this explicitly; interaction and live-document comparison
can move in-process when CMP publishes an embeddable macOS view.

## Deliberate prototype limits

- The palette is fixed to the light Apple system colors; mapping dynamic light/dark system colors
  onto Remote Compose theme expressions is follow-up work.
- The toggle exposes an accessibility role and state description, but the current creation API has
  no checked-state semantics property.
- Controls target familiar Apple geometry and hierarchy, not private Apple implementation details.
- This module is not added to Maven publication or `Package.swift`.

# macOS desktop player

## Status and intent

The macOS player is a downloadable Apple-silicon `.app` beside the iOS Simulator sample. It is a
developer and evaluator tool, not a new supported rendering contract. Its primary jobs are to open
an arbitrary local `.rc` file and make renderer selection explicit and repeatable.

The default remains the supported Compose Multiplatform (CMP) renderer. The second choice is an
experimental Swift-native AppKit renderer. Like the UIKit POC, it temporarily asks Kotlin to decode
the wire format and execute state, then owns its native view hierarchy and Core Graphics drawing in
Swift. It does not replace the CMP player.

## User model

The control window has two inputs:

1. a document selected with the standard macOS open panel, drag and drop, Finder's **Open With**,
   or a path passed to the executable; and
2. a preferred renderer, `CMP` or `Native AppKit POC`.

The renderer is stored in `UserDefaults`, exposed in the control window and Settings, and reused for
later documents. Opening a file through Finder renders it immediately with that preference. The app
declares a Viewer document type for the `.rc` extension but only an Alternate handler rank, so it
does not attempt to displace a user's default editor.

Each render opens a separate document window. That follows the existing macOS CMP surface, which
exports a window rather than an embeddable `NSView`, and allows two windows to be placed beside one
another without pretending the implementations share a host view.

## Component hierarchy

```text
NSApplication / RemoteComposeMacAppDelegate
└── NSWindow
    └── NSHostingController<DesktopPlayerView>
        ├── Document group (URL, size, Open button)
        ├── Renderer picker
        └── Open Player Window button

Selected renderer
├── CMP → RcComposeWindow (existing Kotlin/Native macOS entry point)
└── Native AppKit POC → NSWindow / NSScrollView / NativeMacDocumentView
    └── NativeMacComponentView per Remote Compose node
        ├── NSTextField for text components
        ├── NSButton for clickable semantic components
        ├── NativeMacCanvasView for draw commands
        └── child NativeMacComponentView instances
```

Text and controls intentionally use AppKit elements. They remain discoverable as text and buttons
instead of becoming anonymous pixels. Canvas primitives use Core Graphics. `Row`, `Column`, `Box`,
padding, alignment, fixed/fill/wrap dimensions, weights, offsets, visibility, background and corner
radius have a small frame-based implementation matching the UIKit POC's philosophy.

## Data and event flow

The host reads bytes with `Data(contentsOf:)`. CMP passes those bytes to `RcComposeWindow`. The
native path creates an `RcNativeSnapshotSession`, builds AppKit views from its immutable snapshot,
and asks the retained session for a new snapshot when an `NSButton` is activated. This retains one
mature decoder and state runtime during the POC while keeping all desktop UI native.

Malformed input is reported in the control window. The native renderer is intentionally
best-effort: unsupported commands are skipped rather than silently selecting CMP. Renderer identity
is always visible in the document-window title.

## Packaging and release

`scripts/build-macos-player.sh` compiles the Swift sources directly against the locally assembled
`macosArm64` slice of `RcComposePlayer.xcframework`. The framework is static, so the result is a
self-contained executable. The script creates a conventional `.app` bundle and ad-hoc signs it.

`scripts/package-macos-player.sh` creates:

```text
build/distributions/RemoteComposePlayer-macOS-arm64.zip
build/distributions/RemoteComposePlayer-macOS-arm64.zip.sha256
```

CI packages the app whenever the Apple lane is affected, validates the signature and archive, and
decodes the title-card fixture through the packaged executable's `--validate-native` smoke mode.
The release workflow includes the ZIP in build-provenance attestation and uploads the ZIP and SHA-256
sidecar to the GitHub Release with the Apple libraries.

The artifact is Apple-silicon only because the upstream Compose Multiplatform framework has no
macOS x86_64 slice. It is ad-hoc signed rather than Developer ID signed or notarized, so users may
need Control-click → Open on first launch.

## Deliberate POC limits

- CMP is the compatibility reference; Native AppKit is not a compatibility claim.
- Native canvas image resources, gradients, embedded fonts, animation pacing, touch gestures beyond
  click, sound, and the full accessibility-role mapping still need parity work.
- The two renderers open sibling windows because CMP does not expose an embeddable macOS `NSView`.
- Sandboxed security bookmarks are not persisted; the app reads the selected file into memory.
- Distribution is ad-hoc signed and not notarized.

## Next increments

1. Share renderer-neutral layout and paint policy between the UIKit and AppKit POCs without sharing
   platform views.
2. Add image resources, gradients, embedded fonts, scheduled snapshots and complete event delivery.
3. Add deterministic macOS CMP/native comparison captures to the existing representative corpus.
4. Add accessibility assertions for `NSTextField`, `NSButton`, state descriptions and merged nodes.
5. Decide whether the native AppKit experiment belongs in a reusable library only after operation,
   visual, performance and accessibility gates match the UIKit graduation criteria.

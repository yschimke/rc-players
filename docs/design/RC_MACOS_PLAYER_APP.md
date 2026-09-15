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

The control window has three inputs:

1. a document selected with the standard macOS open panel, drag and drop, Finder's **Open With**,
   or a path passed to the executable; and
2. a preferred renderer, `CMP` or `Native AppKit POC`; and
3. a native compatibility policy: `Compatible` renders the supported subset and reports known
   differences, while `Strict` refuses any partial frame.

The renderer and native compatibility policy are stored in `UserDefaults`, exposed in the control
window and Settings, and reused for later documents. Opening a file through Finder renders it
immediately with those preferences. The app declares a Viewer document type for the `.rc` extension
but only an Alternate handler rank, so it does not attempt to displace a user's default editor.

Each render opens a separate document window. That follows the existing macOS CMP surface, which
exports a window rather than an embeddable `NSView`, and allows two windows to be placed beside one
another without pretending the implementations share a host view.

## Component hierarchy

```text
NSApplication / RemoteComposeMacAppDelegate
└── NSWindow
    └── NSHostingController<DesktopPlayerView>
        ├── Document group (URL, size, Open button)
        ├── Renderer and native-policy pickers
        ├── Native compatibility and safety result
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
and asks the retained session for a new snapshot when an `NSButton` is activated. Events returned
atomically with that update are delivered to the host in wire order and shown in the control
window. CMP events use the same host event feed. This retains one mature decoder and state runtime
during the POC while keeping all desktop UI native.

The native document view reads the scheduling fields from every immutable snapshot. On macOS 14+
it uses `NSView.displayLink(target:selector:)`, so callbacks follow the display containing the view
and naturally stop while AppKit hides or detaches it. macOS 12–13 retain a one-frame `Timer`
fallback because `CADisplayLink` is not available there. Delayed runtime wakes use a common-mode
one-shot timer on every supported release. The logical animation clock pauses while the application
is inactive or the view is detached, preserves the remaining delayed wake, and observes AppKit's
Reduce Motion setting. Functional `requestsNextFrame` work still runs under Reduce Motion while
decorative continuous animation pauses.

Malformed or over-budget input is reported in the control window. Safety is independent of
compatibility: both modes enforce a 16 MiB encoded-document limit, finite and bounded geometry,
20,000 nodes, 256 levels of nesting, 50,000 draw commands, 100,000 path elements, 16 KiB of text,
32 resources with bounded encoded bytes and declared image dimensions/pixels, and 200,000 work units
per frame. Validation runs before the initial AppKit hierarchy is created and again before animated
or event-driven snapshots replace the current frame. Host events share that frame budget and are
validated before delivery.

The native renderer's `Compatible` mode is intentionally best-effort: unsupported commands are
skipped and structured diagnostics are shown in the control window rather than silently selecting
CMP. `Strict` refuses both unsupported and known-approximate behavior. The AppKit profile augments
the renderer-neutral bridge diagnostics with its current platform gaps: image components and draws,
gradients, paint textures, skew, path clipping, non-source-over blending, embedded fonts, pivoted
transforms, and incomplete non-button accessibility roles. External image references are validated
as bounded UTF-8 but never resolved by this app, so document input cannot initiate network or file
I/O. Renderer identity and policy are always visible in the document-window title.

## Packaging and release

`scripts/build-macos-player.sh` compiles the Swift sources directly against the locally assembled
`macosArm64` slice of `RcComposePlayer.xcframework`. The framework is static, so the result is a
self-contained executable. The script creates a conventional `.app` bundle and ad-hoc signs it.

`scripts/package-macos-player.sh` creates:

```text
build/distributions/RemoteComposePlayer-macOS-arm64.zip
build/distributions/RemoteComposePlayer-macOS-arm64.zip.sha256
```

CI packages the app whenever the Apple lane is affected, validates the signature and archive,
decodes the title-card fixture through the packaged executable's `--validate-native` smoke mode,
samples the continuous-progress fixture through `--validate-native-animation`, and exercises the
strict/compatible decision plus hard document limit through dedicated policy smoke modes.
The release workflow includes the ZIP in build-provenance attestation and uploads the ZIP and SHA-256
sidecar to the GitHub Release with the Apple libraries.

The artifact is Apple-silicon only because the upstream Compose Multiplatform framework has no
macOS x86_64 slice. It is ad-hoc signed rather than Developer ID signed or notarized, so users may
need Control-click → Open on first launch.

## Deliberate POC limits

- CMP is the compatibility reference; Native AppKit is not a compatibility claim.
- Native canvas image resources, gradients, embedded fonts, non-click pointer gestures, sound, and
  the full accessibility-role mapping still need parity work.
- The two renderers open sibling windows because CMP does not expose an embeddable macOS `NSView`.
- Sandboxed security bookmarks are not persisted; the app reads the selected file into memory.
- Distribution is ad-hoc signed and not notarized.

## Next increments

1. Share renderer-neutral layout and paint policy between the UIKit and AppKit POCs without sharing
   platform views.
2. Add image resources, gradients, embedded fonts, pointer gestures and named-value host controls.
3. Add deterministic macOS CMP/native comparison captures to the existing representative corpus.
4. Add accessibility assertions for `NSTextField`, `NSButton`, state descriptions and merged nodes.
5. Decide whether the native AppKit experiment belongs in a reusable library only after operation,
   visual, performance and accessibility gates match the UIKit graduation criteria.

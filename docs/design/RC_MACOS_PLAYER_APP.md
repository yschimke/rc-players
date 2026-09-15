# macOS desktop player

## Status and intent

The macOS player is a downloadable Apple-silicon `.app` beside the iOS Simulator sample. It is a
developer and evaluator tool, not a new supported rendering contract. Its primary jobs are to open
an arbitrary local `.rc` file and make renderer selection explicit and repeatable.

The default remains the supported Compose Multiplatform (CMP) renderer. The second choice is an
experimental Swift-native AppKit renderer. It shares the platform-neutral `RcNativePlayerCore`
decoder and retained session with the UIKit player, then owns its native view hierarchy and Core
Graphics drawing in Swift. The native path has no Kotlin runtime dependency and does not replace the
CMP player.

## User model

The control window has four inputs:

1. a document selected with the standard macOS open panel, drag and drop, Finder's **Open With**,
   or a path passed to the executable; and
2. a preferred renderer, `CMP` or `Native AppKit POC`; and
3. a native compatibility policy: `Compatible` renders the supported subset and reports known
   differences, while `Strict` refuses any partial frame; and
4. a downloadable Google Fonts preference, enabled by default for both renderers.

The renderer, native compatibility policy, and font preference are stored in `UserDefaults`,
exposed in the control window and Settings, and reused for later documents. Opening a file through
Finder renders it immediately with those preferences. The app declares a Viewer document type for
the `.rc` extension but only an Alternate handler rank, so it does not attempt to displace a user's
default editor.

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
native path creates a `NativeSwiftDocumentSession`, builds AppKit views from its immutable
`NativeSwiftDocumentSnapshot`, and asks the retained session for a new snapshot when an `NSButton`
is activated. Events are delivered to the host in wire order and shown in the control window. CMP
events use the same host event feed. Both native renderers therefore exercise one pure-Swift state
runtime while keeping their platform views independent.

The native document view reads the continuous-animation requirement from every immutable snapshot. On macOS 14+
it uses `NSView.displayLink(target:selector:)`, so callbacks follow the display containing the view
and naturally stop while AppKit hides or detaches it. macOS 12–13 retain a one-frame `Timer`
fallback because `CADisplayLink` is not available there. The logical animation clock pauses while
the application is inactive or the view is detached and observes AppKit's Reduce Motion setting.
Decorative continuous animation pauses under Reduce Motion.

Malformed or over-budget input is reported in the control window. Safety is independent of
compatibility: both modes enforce a 16 MiB encoded-document limit, finite and bounded geometry,
20,000 nodes, 256 levels of nesting, 50,000 draw commands, 100,000 path elements, 16 KiB of text,
and 200,000 work units per frame. Validation runs before the initial AppKit hierarchy is created and again before animated
or event-driven snapshots replace the current frame. Host events share that frame budget and are
validated before delivery.

The pure-Swift decoder rejects unsupported or malformed operation families instead of silently
falling back to CMP. `Compatible` and `Strict` retain distinct host-facing policy identities so
structured approximation diagnostics can be added as coverage grows. A `google:` font family is
the AppKit profile's only external resource: when the preference is enabled, the app resolves it
through the bounded HTTPS Google Fonts resolver and registers the validated bytes with CoreText at
process scope for the lifetime of the document window. CMP receives the same downloaded bytes
through its `RcTypefaceLoader`. Disabling the preference prevents the requests; any download,
validation, or registration failure is reported in the control window and both renderers continue
with their default font. Renderer identity and policy are always visible in the document-window
title.

## Packaging and release

`scripts/build-macos-player.sh` compiles `RcNativePlayerCore` and `RcPlayerAppleFonts` into the
application alongside the AppKit renderer. It also links the locally assembled `macosArm64` slice of
`RcComposePlayer.xcframework` for the separately selectable CMP renderer. The framework is static,
so the result is a self-contained executable. The script creates a conventional `.app` bundle and
ad-hoc signs it.

`scripts/package-macos-player.sh` creates:

```text
build/distributions/RemoteComposePlayer-macOS-arm64.zip
build/distributions/RemoteComposePlayer-macOS-arm64.zip.sha256
```

CI packages the app whenever the Apple lane is affected, validates the signature and archive,
decodes the title-card fixture through the packaged executable's `--validate-native` smoke mode,
samples the continuous-progress fixture through `--validate-native-animation`, dispatches the title
card action through `--validate-native-click-events`, and exercises the strict/compatible decision
plus hard document limit through dedicated policy smoke modes. Its offscreen
`--render-native-png` mode captures the same AppKit hierarchy for deterministic visual evidence.
`--render-native-google-font-png` enables the release app's live resolver in that capture path;
`scripts/check-macos-google-font-rendering.sh` uses both modes to preserve fallback/downloaded
before-and-after evidence.
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

1. Add drag, scroll, and raw pointer expressions to the shared session and both native event
   adapters; single, long, double, and pointer-lifecycle actions are already shared.
2. Fill the remaining layout, bitmap, advanced drawing, font, and complex-text operation families;
   standard shape/clip/transform operations and all three progress fixtures are pure Swift.
3. Add image resources and the Image Button comparison fixture.
4. Add deterministic macOS CMP/native comparison captures and accessibility assertions.
5. Complete the fuzzing and device-performance work tracked by GitHub issue #138.

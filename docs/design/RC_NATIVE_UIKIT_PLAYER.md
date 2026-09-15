# A native UIKit Remote Compose player

Status: proof of concept. The Compose Multiplatform player remains the supported implementation and
the compatibility oracle for this work.

## Summary

`RcNativePlayerUIKit` is a second Apple player next to, not instead of, the CMP player. It proves
that an iOS app can decode a real `.rc` document through the existing Kotlin runtime, turn it into a
small immutable render snapshot, and render it through a Swift-owned hierarchy of `UIView`s and Core
Graphics calls.

The POC optimizes for a comprehensible native component hierarchy rather than complete operation
coverage or exact pixels. Its primary API is `RemoteComposeNativePlayerView`; a conventional view
controller and a thin `UIViewRepresentable` adapter are also supplied. No Compose view or Skia
surface exists below those entry points.

It currently handles retained frames containing Box, Row, and Column layout; common size, padding,
alignment, rounded-clip, and background modifiers; rectangles, ovals, circles, lines, rounded
rectangles, arcs, sectors, basic text, color/alpha/stroke paint state, clipping, save/restore, and
basic transforms. It also replays validated paths, path clipping, inline linear/radial/sweep
gradients, stroke caps/joins, and Core Graphics-compatible blend modes in stream order. Layout text
is promoted to `UILabel`, while clickable or button-role components
are promoted to real transparent `UIButton`s over the document visuals. Unsupported drawing and
behavior opcodes are returned as diagnostics rather than being presented as full compatibility.
Inline and host-referenced images cross a bounded resource boundary and render through `UIImageView`
for conceptual image components or through the ordered Core Graphics stream for canvas images.
Embedded fonts are validated and registered for the owning player lifetime.
Hosts can update declared float, string, and color values without re-decoding the document. Native
buttons dispatch ordinary and single-click action blocks through typed main-actor callbacks.

## Goals

- Establish a clean, idiomatic Swift/UIKit component boundary.
- Reuse the mature `.rc` codec, linker, and expression evaluator during exploration.
- Render useful real documents in the existing Apple sample.
- Select CMP or UIKit explicitly against the same bytes.
- Keep unsupported behavior visible and measurable.
- Promote explicit Remote Compose semantics to native UIKit concepts when the mapping is sound.
- Define an incremental route to a production player without prematurely fixing its ABI.

## Non-goals for the POC

- Pixel parity with AndroidX or CMP.
- Complete operation coverage.
- Automatic animation scheduling, drag/scroll/raw touch expressions, long/double click, sound, or
  haptics.
- Production text shaping, bidirectional text, downloadable fonts, or text on paths.
- The final layout algorithm or final Kotlin/Swift boundary.
- Replacing, deprecating, or internally modifying `RcComposePlayer`.
- Native AppKit rendering.

## Why this is a separate player

`RcComposePlayerSwiftUI` is a Swift API over the supported CMP renderer. On iOS it installs
`RcComposeViewController` as a child controller, so lifecycle integration is native but pixels and
components still come from Compose and Skia.

This experiment asks whether Remote Compose maps naturally onto Apple's view model. A separate
product answers that question without changing the identity or behavior of the supported API:

```text
RcComposePlayerSwiftUI                  RcNativePlayerUIKit
          |                                      |
UIViewControllerRepresentable          UIViewRepresentable (optional)
          |                                      |
RcComposeViewController                RemoteComposeNativePlayerView
          |                                      |
Compose component tree                 NativeComponentView tree
          |                                      |
Compose / Skia drawing                 UIView.draw / Core Graphics
```

Only the left path is currently production-supported.

## Design principles

### Swift owns presentation

Kotlin may decode and resolve semantics during the POC, but it never creates an Apple view,
controller, layer, color, font, or graphics context. The snapshot crossing the language boundary is
immutable data. UIKit owns lifecycle and layout; Core Graphics owns drawing.

### Components retain native identity

Each linked Remote Compose container becomes a `NativeComponentView`. Local commands are drawn by a
child `NativeCanvasView`; linked component children become child `NativeComponentView`s. Structural
content wrappers remain inspectable but are transparent to the small frame-based Box/Row/Column
layout pass. This preserves component identity and mirrors the CMP player's layout/canvas split in
ordinary UIKit.

Where protocol semantics identify a platform concept, the component also owns the corresponding
native view. Clickable/button, switch, and image roles become transparent `UIButton`, `UISwitch`,
and `UIImageView` subclasses over their captured visuals. Checkbox, radio, tab, dropdown, picker,
and carousel roles use focused transparent `UIControl` overlays because UIKit has no exact standard
control with the authored Remote Compose contract. These elements supply UIKit hit testing, focus,
enabled state, accessibility traits, and inspectable type identity; the command surface preserves
the document's appearance. This promotion is evidence-based: a click modifier can imply a button,
while arbitrary painted content does not. Text commands similarly become `UILabel`s rather than
Core Graphics glyph calls. Conceptual image-layout components use visible `UIImageView`s.

The POC resolves fill fractions, proportional weights, wrap and exact sizing, min/max constraints,
padding, offsets, z-order, visibility, AndroidX linear arrangements, RTL placement, spacing, root
scaling/alignment, and rounded clipping. Pure functions in `NativeLayout.swift` keep measurement and
placement policy testable without constructing a UIKit hierarchy.

### Resolve before rendering

Swift does not inspect Kotlin operation subclasses or NaN-boxed identifiers. The bridge uses
`RcDocumentCodec`, `RcDocumentLinker`, and `RcPlayerState` to resolve a static frame. Swift receives
concrete floats, colors, strings, commands, and node relationships.

This keeps wire knowledge in one place and the renderer idiomatic. It is deliberately temporary: a
production bridge needs incremental state updates and must not rebuild a snapshot every frame.

### Partial means explicit

Every snapshot includes structured issues, plus legacy `unsupportedOpcodes` and human-readable
`notes` projections. The sample shows a diagnostic badge when any issue exists. A host can choose
compatible or strict policy; the player does not silently claim compatibility.

## Architecture

| Concern | Location | Responsibility |
|---|---|---|
| Decode and static resolution | `RcNativeSnapshotBridge` in `rc-player-compose` | Decode, link, evaluate supported values, and create a snapshot |
| Swift package product | `RcNativePlayerUIKit` in `Package.swift` | Ship the native source beside existing products |
| Public UIKit API | `RemoteComposeNativePlayer.swift` | View/controller lifecycle, replacement, errors, diagnostics |
| Component renderer | `NativeComponentViews.swift` | Build `UIView`s, aspect-fit the document, draw commands |
| SwiftUI adapter | `RemoteComposeNativePlayerRepresentable.swift` | Embed that same UIKit tree in SwiftUI |
| Executable host | `samples/apple-player` | Toggle CMP/native against identical `.rc` bytes |

The native source product depends on the `RcComposePlayer` binary only for the temporary bridge. It
does not depend on `RcComposePlayerSwiftUI`, and neither existing product depends on it.

### Data flow

1. The host creates `RemoteComposeNativePlayerView(data:)` or its controller.
2. A serial Swift actor opens `RcNativeSnapshotSession` on a detached task.
3. The session decodes once, links once, and retains `RcPlayerState`.
4. A requested time resolves an immutable frame without scheduling platform work.
5. Named values and clicks mutate that retained state and return an atomic frame-plus-events result.
6. Swift maps the interop frame to private Swift values on the main actor.
7. `NativeDocumentView` builds recursive `NativeComponentView`s for the first frame.
8. Compatible later frames reconcile those views in place; structural changes atomically replace
   the tree.
9. Each `NativeCanvasView` replays immutable commands in `draw(_:)` using Core Graphics.

Kotlin objects do not remain in the UIKit view tree. The retained Kotlin object is isolated inside
one Swift actor; UIKit receives only immutable frames. That keeps ownership clear and prevents the
non-`Sendable` generated Kotlin surface from crossing concurrent tasks unsafely.

### Snapshot model

`RcNativeDocumentSnapshot` contains document size, a root node, and structured compatibility
diagnostics. Each diagnostic records severity, opcode, inventory-derived operation name, component
id, and reason. The legacy unsupported-opcode and note projections remain on the experimental
bridge for source compatibility, but Swift treats the structured list as authoritative.
`RcNativeNodeSnapshot` carries a semantic kind (`root`, `content`, `canvas`, or `group`), component
id, local commands, children, and resolved role, mode, clickability, enabled, content-description,
text, and state-description semantics. Swift converts integer kinds, roles, and modes to private
enums at the boundary. Invalid role/mode values fail decode. Multiple accessibility modifiers on
one component remain an explicit compatibility diagnostic while the bridge exports one effective
semantic node.

`RcNativeDrawCommand` is a resolved Core Graphics-friendly value. Its flat six-float payload is not
proposed as a long-term IR; it keeps the POC's generated Kotlin/Native header small and avoids a
large exported sealed hierarchy that Swift would have to downcast.

### Public API

The primary API is a view because UIKit containers compose views:

```swift
import RcNativePlayerUIKit

let player = RemoteComposeNativePlayerView(
  data: documentData,
  background: .transparent,
  compatibilityPolicy: .compatible
) { diagnostics in
  for issue in diagnostics.issues {
    print("\(issue.operationName) in component \(issue.componentID): \(issue.reason)")
  }
}
container.addSubview(player)
```

`RemoteComposeNativePlayerViewController` is a convenience for controller-based hosts and exposes
`load(_:)`, resource configuration, explicit `renderFrame(at:)`, and asynchronous typed
`setFloat(_:for:)`, `setString(_:for:)`, and `setColor(_:for:)` updates. The view offers the same
operations. `RemoteComposeNativePlayerRepresentable` is only an adapter: its renderer is the same
UIKit tree. All three entry points accept an `onEvent` callback.

```swift
let player = RemoteComposeNativePlayerView(
  data: documentData,
  onEvent: { event in
    // Called on the main actor after the resulting frame is installed.
    print(event)
  }
)

Task { @MainActor in
  let accepted = await player.setFloat(0.75, for: "progress")
  assert(accepted)
}
```

The default `.compatible` policy renders the supported subset and reports all known differences.
`.strict` refuses to install a document view when any diagnostic is present, including a known
approximation; the error is displayed through the existing native error surface. Changing the
policy reloads the retained document bytes, so a host may inspect compatible output and then apply
a stricter gate without recreating the view.

## Rendering behavior

### Root sizing and component layout

UIKit lays out components in document coordinates and applies `RootContentBehavior` once at the
root view transform. Inside, fit, fill-width, fill-height, crop, fill-bounds, and alignment modes are
explicit and respond to host resizing. Scroll remains unsupported.

The static profile implements Box, Row, Column, and time-zero StateLayout selection, including
structural content flattening, proportional row/column weights, range constraints, visibility,
offset, z-order, and RTL row order. Hidden StateLayout alternatives are not rendered or reported as
reachable compatibility failures. Flow, intrinsic sizing, layout compute, and scroll remain
unsupported; required constraints are clamped to the native parent and remain diagnosed because
UIKit does not yet reproduce their overflow behavior. Layout stays deterministic and frame-based;
Auto Layout would introduce solver behavior not in the Remote Compose contract. Remote Compose has
no general component margin modifier; external spacing is expressed by parent arrangement and
`spacedBy`.

### Paint and graphics state

The bridge snapshots paint onto each drawing command, so Swift does not depend on Kotlin's internal
paint model. Core Graphics state remains ordered for save/restore, clipping, and transforms.

Current paint support is color, alpha, fill/stroke, stroke width/cap/join, Core
Graphics-representable blend modes, ordered line/quadratic/cubic paths, path clipping, inline
linear/radial/sweep gradients, and system-font size. Sweep gradients use bounded Core Graphics
tessellation because `CGContext` has no conic-gradient primitive. Non-clamp gradient tile modes and
rational conics are approximated with explicit diagnostics. Referenced shaders, color filters, path
effects, font axes, and textures produce diagnostics or unsupported opcodes.

### Text

Layout text uses real `UILabel`s and attributed strings. UIKit owns Unicode shaping, bidirectional
text, native fallback, Dynamic Type, accessibility, wrapping, line limits, ellipsis placement,
logical start/end alignment, line height, letter spacing, and decoration. Its intrinsic measurement
feeds the same frame-based layout pass as other components. The bridge resolves inherited
`CoreText` properties before Swift sees them.

Canvas text uses Core Text inside the ordered Core Graphics command stream, preserving the active
transform, clip, blend state, baseline anchor, and primitive interleaving. Canvas glyphs intentionally
remain fixed-size document graphics rather than Dynamic Type content. Generic sans-serif, serif,
and monospace families map to deterministic system designs. Other named families resolve only from
font bytes declared by the document and validated by the bounded resource store. Unavailable
families use deterministic system fallback with a diagnostic; fonts installed elsewhere in the
process are not consulted opportunistically.

### Images and resources

The snapshot exports image metadata and opaque bytes without asking Kotlin or UIKit to perform
network I/O. Inline PNG and raw alpha/RGBA resources are decoded only after enforcing per-resource,
aggregate-byte, dimension, decoded-pixel, and resource-count limits. Declared and decoded image
dimensions must agree. The shared decoded-image cache also has an independent byte-cost ceiling, so
repeated document replacement cannot retain one maximum-sized image per count slot. Duplicate ids,
malformed bytes, invalid references, and limit violations are typed
`RemoteComposeNativeResourceError` failures.

Referenced images are inert unless the host injects a `RemoteComposeNativeResourceResolving`
implementation. The resolver receives the original opaque reference and declared metadata; URL
policy, authentication, transport, and persistence remain host responsibilities. Replacement and
deallocation cancel the owning task, and cancellation is checked before resolved bytes are installed.
The in-memory cache key includes the opaque reference, encoding, type, and declared dimensions.

Conceptual `ImageLayout` nodes own a `UIImageView`. Bitmap commands inside a canvas remain in the
ordered command stream so transforms, clipping, blend state, and primitive interleaving are
preserved. AndroidX scale modes use a shared deterministic integer-centering geometry policy.
Embedded fonts are registered process-wide from private temporary files and unregistered when the
owning player releases its font registry.

The standard protocol text operations carry a single style rather than inline attributed runs, and
they do not carry a locale property. Link spans are a separate `SupportSpannableString` custom
component, to be handled by the semantic component registry. UIKit uses its Unicode script and host
locale behavior for the standard operations. Font variation axes, autosizing properties, exact CMP
metrics, and exact text pixels remain outside the current static profile and are reported rather
than silently claimed.

### Errors

Oversized input fails before bridging. Decode/link failures are exported as throwing Kotlin APIs and
translated to `RemoteComposeNativePlayerError`. The view shows a native error label for the POC; a
production API should add a first-class `onError` closure.

## Lifecycle and concurrency

Public view/controller APIs are `@MainActor`, as UIKit requires. Decode/link and frame evaluation run
through a serial actor away from the main actor. Generation-numbered tasks retain input bytes,
discard stale decode/resource/frame results, and install a complete candidate only after validation
and resource preparation succeed. A failed candidate therefore leaves the last valid hierarchy
and session intact while displaying the native error surface.

The session retains codec/link/runtime state, so animation never has to decode each display frame.
Frames carry a runtime scheduling contract: continuous work, one next frame, or an earliest delayed
`WakeIn`. Static documents schedule nothing. Continuous and one-shot work use `CADisplayLink`;
delayed work uses a generation-checked cancellable task. The player pauses its injectable monotonic
timeline while offscreen, backgrounded, or under Reduce Motion, then resumes without counting the
suspended interval. Reduce Motion suppresses continuous decorative motion but preserves one-shot
and delayed functional state updates. Entering the background cancels outstanding render work;
activation retries an interrupted full render without advancing the paused timeline.

Canvas expressions that read component width/height receive a native geometry settling pass before
commands are exported. This makes the real indeterminate-progress fixture resolve finite arc bounds
and change across deterministic timestamps. Stable canvas views compare complete render signatures,
so an unchanged frame does not call `setNeedsDisplay`.

## Accessibility and input

Components have stable accessibility identifiers, making the hierarchy inspectable in tests.
Semantic roles map to real or focused native views; content description and authored text form a
de-duplicated label, state description becomes the value, and disabled state becomes both control
state and the `notEnabled` trait. Set mode exposes the semantic node followed by descendants in
layout order. Merge and clear-and-set modes hide descendants; merge folds their labels into the
owning node. A canvas never becomes one monolithic accessibility element merely because it draws.

The wire operation has no checked/selected bit, progress range, or adjustable action callbacks, so
the player does not guess them from localized state text. Custom accessibility actions, complete
stateful-control behavior, VoiceOver/Switch Control automation, and explicit high-contrast behavior
remain outside the current core profile.

Click modifiers map to transparent `UIControl` subclasses owned by the semantic component. UIKit
hit testing follows the rendered component transform and explicitly orders overlapping children by
Remote Compose z-index, then insertion order. Hidden, clipped, or disabled controls do not dispatch.
The winning component id enters the retained session; its ordinary and single-click action blocks
execute in wire order and emit typed Swift action values exactly once. Generation and session
identity checks prevent a replaced document from delivering an old callback. Long press,
double-click, drag, scroll, and raw touch expressions remain explicit compatibility gaps.
Haptics should use UIKit feedback generators. Sound remains host-owned. External URLs remain host
events and must never trigger automatic network loads.

## Compatibility model

There are two distinct claims:

1. Decode compatibility: the shared codec/linker accept the document.
2. Native render compatibility: every executable operation in the selected frame has a UIKit path.

The POC reports structured opcode, name, component, severity, and reason entries. Time-zero
StateLayout selection is reachability-aware, so hidden alternatives do not make a compatible frame
partial. Strict mode refuses partial trees; compatible mode may render the supported subset while
returning diagnostics. The sample intentionally uses compatible behavior.

## Testing strategy

Current checks are:

- `RcNativeSnapshotTest` round-trips a protocol document and verifies nesting, paint, and geometry;
- `scripts/check-native-uikit-compatibility.sh` executes strict/compatible policy decisions as a
  host-platform Swift test;
- `scripts/check-native-uikit-layout.sh` executes pure Swift dimension, weight, arrangement, RTL,
  and dynamic root-resize assertions;
- `scripts/check-native-uikit-accessibility.sh` executes pure Swift role, merge, clear, and label
  policy assertions;
- `scripts/check-native-uikit-accessibility-simulator.sh` renders the packaged native player with
  Increased Contrast and an accessibility Dynamic Type size, then applies the normal title-card
  pixel sanity checks;
- `scripts/check-native-uikit-frame-timing.sh` advances monotonic timestamps directly and verifies
  static, continuous, one-shot, delayed, paused, resumed, and Reduce Motion scheduling policy;
- `scripts/check-native-uikit-animation-simulator.sh` captures two native Progress frames and
  requires both visible ink and changing pixels within the document surface;
- `scripts/build-apple-player.sh` compiles and links the Swift sources to the XCFramework;
- the sample toggles CMP/native for the same bundled files.

Production requires pure Swift mapping tests, UIKit hierarchy tests, Core Graphics image tests per
operation, CMP/native simulator A/B evidence, interaction/accessibility tests, lifecycle and
cancellation tests, malformed-input corpus tests, and performance budgets for decode, first frame,
steady-state work, and memory. Pixel thresholds can start tolerant; structure and state should be
exact from the beginning.

## Distribution and compatibility

`Package.swift` exposes `RcNativePlayerUIKit` separately. Existing consumers selecting
`RcComposePlayer` or `RcComposePlayerSwiftUI` see no source change.

The bare semantic-version tag is the normal SwiftPM distribution. Releases also publish
`RcNativePlayerUIKit.swiftpackage.zip` plus a SHA-256 sidecar. The archive is a self-contained local
Swift package with the UIKit sources and the exact `RcComposePlayer.xcframework` used by the
temporary bridge. CI extracts and builds that archive before release, so it tests the downloadable
unit rather than only the repository source tree. This is intentionally an experimental artifact;
it does not imply parity with or replacement of the CMP product.

The bridge adds recorded public Kotlin/Native ABI to the XCFramework. Before stable release, choose
whether to promote a renderer-neutral retained runtime, use a narrow C/Objective-C bridge, port the
codec/runtime to Swift, or retain an explicitly versioned experimental SPI. A retained shared
runtime best preserves one semantic implementation if incremental interop proves inexpensive.

## Security and robustness

The native lane accepts untrusted bytes wherever CMP does. It inherits codec/linker size, nesting,
and expansion checks but adds native resource risks. Bitmap/font byte totals, resource counts,
decoded dimensions, and decoded pixel counts are bounded today. Production must additionally bound
command count, path complexity, text length, offscreen area, and per-frame work. Numeric values must
be finite and sized before Core Graphics use. Custom components require an explicit host registry;
documents must never instantiate arbitrary Objective-C classes by name.

## Evolution plan

The detailed, PR-sized implementation sequence and acceptance gates live in
[`RC_NATIVE_UIKIT_PLAYER_PLAN.md`](RC_NATIVE_UIKIT_PLAYER_PLAN.md). The phases below describe the
product maturity boundaries; the plan describes how to reach them.

### Phase 0: architecture POC (this change)

Separate product and sample toggle; shared static bridge; recursive `UIView` hierarchy; a small
Box/Row/Column layout profile; `UILabel` and semantic `UIButton` promotion; primitive drawing;
visible diagnostics. Exit when the bundled Morning Run card is clearly recognizable in Native POC
mode without a Compose view beneath it and incomplete behavior is reported.

### Phase 1: stable static profile

Translate resolved layout geometry, modifiers, and root behavior; add paths, bitmaps, gradients,
and Core Text in clusters; add strict/compatible gates and simulator evidence. Exit when a declared
static profile has no unsupported diagnostics and reviewed A/B evidence.

### Phase 2: state and interaction

Build on the retained session with a named-value controller, action dispatch, remaining semantic
control mappings, haptics, sound, and component-local invalidation. Exit when selected-profile
state/interaction tests pass against CMP.

### Phase 3: animation and advanced graphics

Add display-link scheduling, runtime wakeups, layout transitions, shaders/offscreen rendering, font
variation, profiling, and resource budgets. Exit when device/simulator correctness and performance
budgets pass.

### Phase 4: API decision

Measure interop and maintenance cost; select and stabilize the session boundary; decide whether the
player remains experimental, becomes supported beside CMP, or stops. No phase replaces CMP.

## Alternatives considered

- **Another wrapper around Compose:** rejected because the existing overlay already does that.
- **Downcast exported Kotlin operations in Swift:** rejected because it spreads wire/runtime detail
  and couples UIKit to the generated Kotlin class surface.
- **Port the decoder/runtime first:** deferred because it duplicates the mature platform-neutral
  part before native rendering proves valuable.
- **One `UIView` for the document:** rejected as the architecture because it obstructs native
  layout, semantics, hit testing, custom components, and localized invalidation.
- **A `CALayer` component tree:** deferred as an optimization; `UIView` supplies traits, lifecycle,
  accessibility, and input composition with less infrastructure.

## Open decisions

- Is the production boundary a resolved tree, retained runtime session, or both?
- Which static profile is small enough to finish but useful enough to evaluate?
- Should layout execute in Kotlin and export geometry, or move into Swift?
- How should Core Text metrics reconcile with AndroidX's measured contract?
- Can command buffers be diffed cheaply, or do components need narrower typed updates?
- What compatibility and performance bar promotes the player beyond experimental status?

The POC exists to generate evidence before those choices become compatibility commitments.

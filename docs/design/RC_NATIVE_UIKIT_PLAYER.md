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

It currently handles a static frame containing Box, Row, and Column layout; common size, padding,
alignment, rounded-clip, and background modifiers; rectangles, ovals, circles, lines, rounded
rectangles, arcs, sectors, basic text, color/alpha/stroke paint state, clipping, save/restore, and
basic transforms. Layout text is promoted to `UILabel`, while clickable or button-role components
are promoted to real transparent `UIButton`s over the document visuals. Unsupported drawing and
behavior opcodes are returned as diagnostics rather than being presented as full compatibility.

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
- Animation, live named values, touch expressions, click actions, sound, or haptics.
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
native view. The POC turns `clickable` or button-role components into transparent `UIButton`s above
their captured visuals. The button supplies UIKit hit testing, focus, enabled state, accessibility
traits, and inspectable type identity; the command surface preserves the document's appearance.
This promotion is evidence-based: a click modifier can imply a button, while arbitrary painted
content does not. Text commands similarly become `UILabel`s rather than Core Graphics glyph calls.
Future mappings include `UIImageView` for image-role content, `UISwitch` for switch roles, and
purpose-built `UIControl` subclasses where UIKit has no matching standard control.

The POC resolves fill, weight, wrap, exact sizing, padding, minimum height, basic alignment, spacing,
and rounded clipping. A later complete layout engine can replace that policy in `layoutSubviews()`
without changing decoding, primitive rendering, or the public host API.

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
2. Swift bridges `Data` to `KotlinByteArray` through the existing data bridge.
3. `RcNativeSnapshotBridge` decodes the header and operations.
4. `RcDocumentLinker` validates containers and expands references/macros.
5. `RcPlayerState` loads constants and resolves supported expressions at time zero.
6. The bridge walks the linked tree and emits nodes plus resolved drawing commands.
7. Swift immediately maps interop objects to Swift value types.
8. `NativeDocumentView` builds recursive `NativeComponentView`s.
9. Each `NativeCanvasView` replays immutable commands in `draw(_:)` using Core Graphics.

Kotlin objects do not remain in the UIKit view tree. That keeps ownership clear, makes view tests
simple, and permits decode work to move off the main actor later.

### Snapshot model

`RcNativeDocumentSnapshot` contains document size, a root node, and structured compatibility
diagnostics. Each diagnostic records severity, opcode, inventory-derived operation name, component
id, and reason. The legacy unsupported-opcode and note projections remain on the experimental
bridge for source compatibility, but Swift treats the structured list as authoritative.
`RcNativeNodeSnapshot` carries a semantic kind (`root`, `content`, `canvas`, or `group`), component
id, local commands, children, and resolved role/clickability/enabled/label semantics. Swift converts
integer kinds and roles to private enums at the boundary.

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
`load(_:)`. `RemoteComposeNativePlayerRepresentable` is only an adapter: its renderer is the same
UIKit tree. A named-value controller is omitted until retained state is designed.

The default `.compatible` policy renders the supported subset and reports all known differences.
`.strict` refuses to install a document view when any diagnostic is present, including a known
approximation; the error is displayed through the existing native error surface. Changing the
policy reloads the retained document bytes, so a host may inspect compatible output and then apply
a stricter gate without recreating the view.

## Rendering behavior

### Root sizing and component layout

The header aspect ratio is aspect-fit in host bounds. UIKit frames are display points; commands stay
in document coordinates and are scaled once at the canvas drawing boundary. This is simpler than
the full `RootContentBehavior` contract; scroll, crop, fill, and alignment modes remain unsupported.

The static profile implements the Box, Row, and Column behavior needed by the Morning Run Title Card,
including structural content flattening and weighted rows. Flow, state, intrinsic sizing, layout
compute, scroll, and the full constraint contract remain unsupported. Layout stays deterministic and
frame-based; Auto Layout would introduce solver behavior not in the Remote Compose contract.

### Paint and graphics state

The bridge snapshots paint onto each drawing command, so Swift does not depend on Kotlin's internal
paint model. Core Graphics state remains ordered for save/restore, clipping, and transforms.

Current paint support is color, alpha, fill/stroke, stroke width, and system-font size. Gradients,
shaders, blend modes, color filters, path effects, font axes, and textures produce diagnostics or
unsupported opcodes.

### Text

Layout text uses real `UILabel`s with `UIFont.systemFont`; frames approximate the recorded metrics.
This gives UIKit ownership of traits, Dynamic Type integration points, accessibility, and text
lifecycle without reimplementing a platform text view. Canvas text remains in its ordered Core
Graphics command stream so transforms, clipping, and primitive interleaving are preserved. Core
Text shaping, context ranges, RTL, font ids, spans, overflow, and measurement still must be
implemented together. Exact text pixels are not a POC claim.

### Errors

Oversized input fails before bridging. Decode/link failures are exported as throwing Kotlin APIs and
translated to `RemoteComposeNativePlayerError`. The view shows a native error label for the POC; a
production API should add a first-class `onError` closure.

## Lifecycle and concurrency

Public view/controller APIs are `@MainActor`, as UIKit requires. Decode is synchronous for a simple,
deterministic POC, which is not suitable for arbitrary production documents.

A production implementation should use generation-numbered tasks: retain input bytes, decode to
immutable data off-main, discard stale generations, then build or diff views on the main actor. The
result must be Swift `Sendable` data before it returns to UIKit.

Animation cannot re-decode every display frame. A retained runtime session should own state;
`CADisplayLink` should request a resolved delta or refreshed command buffer only for invalidated
component surfaces.

## Accessibility and input

Components have stable accessibility identifiers, making the hierarchy inspectable in tests. A
recognized button is a real `UIButton`, with its enabled state and accessibility label copied from
the resolved semantics. That is not full accessibility support: merge/clear modes, state
descriptions, custom actions, dynamic updates, and non-button roles remain incomplete. A canvas must
not become one monolithic accessibility element.

Click areas map naturally to transparent `UIControl` subclasses or gesture-owning components.
Static POC buttons do not dispatch Remote Compose actions yet; exposing an inert control as a
supported interactive feature would be misleading, so action opcodes remain diagnostics.
Haptics should use UIKit feedback generators. Sound remains host-owned. External URLs remain host
events and must never trigger automatic network loads.

## Compatibility model

There are two distinct claims:

1. Decode compatibility: the shared codec/linker accept the document.
2. Native render compatibility: every executable operation in the selected frame has a UIKit path.

The POC reports the second as opcode numbers but does not yet model conditional reachability. A
production gate should reuse the operation inventory/profile and report structured opcode, name,
component, and reason entries. Strict mode should refuse partial trees; compatible mode may render
the subset while returning diagnostics. The sample intentionally uses compatible behavior.

## Testing strategy

Current checks are:

- `RcNativeSnapshotTest` round-trips a protocol document and verifies nesting, paint, and geometry;
- `scripts/check-native-uikit-compatibility.sh` executes strict/compatible policy decisions as a
  host-platform Swift test;
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
and expansion checks but adds native resource risks. Production must bound command count, path
complexity, bitmap/font bytes, text length, offscreen area, and per-frame work. Numeric values must
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

Introduce a retained session, named-value controller, action dispatch, remaining semantic control
mappings, haptics, sound, and component-local invalidation. Exit when selected-profile
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

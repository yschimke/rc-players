# A native UIKit Remote Compose player

Status: proof of concept. The Compose Multiplatform player remains the supported implementation and
the compatibility oracle for this work.

## Summary

`RcNativePlayerUIKit` is a second Apple player next to, not instead of, the CMP player. It proves
that an iOS app can decode a real `.rc` document into a small immutable render snapshot and render
it through a Swift-owned hierarchy of `UIView`s and Core Graphics calls. The player now contains a
bounded, Foundation-only Swift decoder and retained Swift state session. The source product and its
standalone archive have no Kotlin or XCFramework dependency. Documents outside the migrated Swift
operation families fail with a typed decode error; hosts can select the separate CMP product.

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
Host-authored `Custom` layouts resolve through an explicit Swift registry. The host may supply a
UIKit plugin with make/update/dismantle lifecycle methods or register SwiftUI content directly;
either form remains inside the native view tree and can write through declared return channels.

## Goals

- Establish a clean, idiomatic Swift/UIKit component boundary.
- Replace the `.rc` codec, linker, and expression evaluator incrementally with ordinary Swift
  value types and actor-confined state, retaining the mature runtime as the parity oracle.
- Render useful real documents in the existing Apple sample.
- Select CMP or UIKit explicitly against the same bytes.
- Keep unsupported behavior visible and measurable.
- Promote explicit Remote Compose semantics to native UIKit concepts when the mapping is sound.
- Define an incremental route to a production player without prematurely fixing its ABI.

## Non-goals for the POC

- Pixel parity with AndroidX or CMP.
- Complete operation coverage.
- Drag/scroll/raw touch expressions, long/double click, automatic sound playback, or inferred
  haptics.
- Text on paths, inline link spans, font variation axes, or exact CMP text metrics.
- Replacing, deprecating, or internally modifying `RcComposePlayer`.
- A stabilized reusable AppKit API; the macOS sample currently consumes the shared core directly.

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

### Swift owns the complete native path

Foundation decodes and retains document state, UIKit owns lifecycle and layout, and Core Graphics
and Core Text own drawing. The native product has no language boundary or binary bridge. CMP remains
an independent renderer and test oracle rather than a runtime fallback.

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
placement policy testable without constructing a UIKit hierarchy. Width-constrained containers are
measured in two passes so multiline `UILabel` content contributes its final height. DP-behavior
documents preserve density-independent padding, spacing, constraints, and corner radii across the
root transform; captured-pixel documents retain their pixel geometry. Rounded corners are clamped
to half the resolved component bounds, matching the protocol's normalized shape rather than relying
on out-of-range Core Animation clipping.

### Resolve before rendering

The renderer receives concrete floats, colors, strings, commands, and node relationships; it never
inspects wire operations or NaN-boxed identifiers. A Foundation-only decoder and retained session
perform that work before UIKit materializes the view model.

### Partial means explicit

Every snapshot includes structured issues, plus legacy `unsupportedOpcodes` and human-readable
`notes` projections. The sample shows a diagnostic badge when any issue exists. A host can choose
compatible or strict policy; the player does not silently claim compatibility.

## Architecture

| Concern | Location | Responsibility |
|---|---|---|
| Swift decode and state | `Sources/RcNativePlayerCore/NativeSwiftCore.swift` | Bounded wire reads, immutable parsed tree, retained document values, snapshot resolution shared by UIKit and AppKit |
| Session isolation | `NativeSession.swift` | Own the Swift runtime in an actor and serialize state updates |
| Swift package product | `RcNativePlayerUIKit` in `Package.swift` | Ship the native source beside existing products |
| Public UIKit API | `RemoteComposeNativePlayer.swift` | View/controller lifecycle, replacement, errors, diagnostics |
| Component renderer | `NativeComponentViews.swift` | Build `UIView`s, aspect-fit the document, draw commands |
| SwiftUI adapter | `RemoteComposeNativePlayerRepresentable.swift` | Embed that same UIKit tree in SwiftUI |
| Executable host | `samples/apple-player` | Toggle CMP/native against identical `.rc` bytes |

The native source product has no dependency on either CMP product. The repository still publishes
those products independently, and the samples can link both to provide an explicit renderer switch.
The macOS app builds its native branch from `RcNativePlayerCore` and AppKit; only its independently
selectable CMP branch links the XCFramework.

### Data flow

1. The host creates `RemoteComposeNativePlayerView(data:)` or its controller.
2. A serial Swift actor asks `NativeSwiftDocumentSession` to open the bytes.
3. The session decodes once and retains document state; malformed or unsupported input fails closed.
5. A requested time resolves an immutable frame without scheduling platform work.
6. Inputs mutate that retained state and return an atomic frame-plus-events result.
7. Swift maps the immutable frame to private UIKit values on the main actor.
8. `NativeDocumentView` builds recursive `NativeComponentView`s for the first frame.
9. Compatible later frames reconcile those views in place; structural changes atomically replace
   the tree.
10. Each `NativeCanvasView` replays immutable commands in `draw(_:)` using Core Graphics.

Frames are `Sendable` Swift value types with no platform objects. Mutable retained state remains
inside one Swift actor; UIKit receives only immutable snapshots.

### Pure Swift migration boundary

The initial Swift family is deliberately a complete executable vertical slice, not a mock codec. It
parses the AndroidX big-endian wire representation for legacy and modern headers, text, float and
color constants, root/content/canvas/box/row/column/text/custom containers, container ends, and a
bounded subset of size, padding, rounded clip, paint, path, transform, arc, and background operations. It
resolves ordinary text into `UILabel` commands and custom properties into the public host component
model. A `TEXT_RETURN` updates retained Swift text state, so both the custom field and an ordinary
document label observe the next atomic frame without foreign-runtime involvement.

The retained Swift value store accepts typed float, string, and color host values. Click modifiers
promote their conceptual component to a native `UIControl`; named actions are resolved and emitted
from the actor as an atomic frame-plus-events update. The first animated canvas family evaluates the
bounded AndroidX reverse-Polish float operations used by the indeterminate progress fixture,
supplies its logical monotonic time, exports save/restore/rotate/arc commands, and requests the
existing demand-driven `CADisplayLink` only while that moving system value is referenced.

Safety rules are part of the boundary: byte reads are checked, UTF-8 is strict, strings,
collections, and operation counts are capped, component identifiers are unique, nesting must
balance, dimensions and paint values must be finite, and malformed supported input fails closed.
`NativeSwiftCoreError` distinguishes unsupported operation families from malformed bytes, and both
fail closed at the public player boundary.

The next migration slices are the remaining expression operators and action payloads, layout
modifiers, remaining canvas paint/path commands, images and fonts, then complex text and accessibility
operations. Each slice adds corpus parity tests against CMP output. There is no runtime fallback:
broader coverage is added only in Swift.

### Snapshot model

`NativeSwiftDocumentSnapshot` is the Foundation-only value passed by the Swift session. It contains
the document size, generation density and density behavior, root-node concepts, and resolved values.
Draw commands also retain whether their geometry depends on component-width or component-height
values. UIKit can therefore replace stale pre-measurement background geometry with the owning
component's final bounds without treating arbitrary paths as backgrounds. Compatibility diagnostics record severity, opcode, inventory-derived
operation name, component id, and reason. Legacy unsupported-opcode and note projections remain in
the experimental public diagnostics for source compatibility, but Swift treats structured issues as
authoritative.
`NativeSwiftNodeSnapshot` carries a semantic kind, component
id, local commands, children, and resolved role, mode, clickability, enabled, content-description,
text, and state-description semantics. Swift converts integer kinds, roles, and modes to private
enums at the boundary. Invalid role/mode values fail decode. Multiple accessibility modifiers on
one component remain an explicit compatibility diagnostic while the session exports one effective
semantic node.

`NativeDrawCommand` is a private, resolved Core Graphics-friendly value. Its flat payload is an
internal implementation detail and can evolve without exposing wire operations through the API.

### Clocks

Two clocks feed a document's system variables, matching AndroidX's `TimeVariables`. The animation
clock (`ANIMATION_TIME`, and `CONTINUOUS_SEC` when no wall clock is supplied) is the host's logical
timeline: the player advances it and a test or capture can hold it still. The wall clock
(`TIME_IN_SEC`, `TIME_IN_MIN`, `TIME_IN_HR`, `CALENDAR_MONTH`, `OFFSET_TO_UTC`, `WEEK_DAY`,
`DAY_OF_MONTH`, `DAY_OF_YEAR`, `YEAR`) needs an absolute instant, so a host supplies a
`NativeSwiftWallClock` with the frame; omitted, those variables stay unset rather than silently
resolving to the 1970 epoch. The UIKit player and the AppKit sample publish the system clock; a
test or a corpus capture supplies a fixed instant. A document that declares its own value at one of
these ids keeps it, matching the reference's claimed-id rule. `ANIMATION_DELTA_TIME` and the
integer `EPOCH_SECOND` are deliberately not loaded yet — see `NativeSwiftSystemVariables`.

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

### Custom components

Custom components are named host extensions, not dynamically loaded classes. The document supplies
a config name plus typed properties; the application explicitly registers the implementation. A
missing registration is an unsupported `LAYOUT_CUSTOM` diagnostic, so `.strict` refuses it and
`.compatible` renders the rest of the document without silently claiming coverage.

The UIKit lifecycle deliberately follows the vocabulary of `UIViewRepresentable`:

```swift
struct RatingPlugin: RemoteComposeNativeCustomComponentPlugin {
  let name = "example:Rating"
  private let value = RemoteComposeNativeFloatProperty(1)
  private let changed = RemoteComposeNativeFloatReturnProperty(2)

  func makeUIView(component: RemoteComposeNativeCustomComponent) -> UISlider {
    let slider = UISlider()
    slider.addAction(UIAction { [weak component] action in
      guard let slider = action.sender as? UISlider else { return }
      component?.send(slider.value, to: changed)
    }, for: .valueChanged)
    return slider
  }

  func updateUIView(_ view: UISlider, component: RemoteComposeNativeCustomComponent) {
    view.value = component.float(value)
  }

  func dismantleUIView(_ view: UISlider) {}
}

let components = RemoteComposeNativeCustomComponentRegistry(RatingPlugin())
let player = RemoteComposeNativePlayerView(data: data, customComponents: components)
```

Property keys make expected value types visible at call sites and provide host defaults. The raw
property array and its optional `Kind` remain available for generic inspection and forward
compatibility. `send(_:to:)` accepts only a matching declared float/text return channel and rejects
non-finite floats before they cross the runtime boundary. Acceptance means the value was enqueued;
the resulting document frame is still serialized with clicks and named-value updates by the player.

SwiftUI content is a convenience over the same registry, not a second renderer:

```swift
let components = RemoteComposeNativeCustomComponentRegistry()
  .registerSwiftUI("example:Headline") { component in
    Text(component.text(RemoteComposeNativeTextProperty(1)))
      .font(.headline)
  }
```

`RemoteComposeNativeCustomComponent` is a stable `ObservableObject`. The registry makes its view
once, updates it when resolved properties change, and dismantles it when the Remote Compose node is
removed. Stable identity lets UIKit and SwiftUI preserve focus, selection, caret position, and
control-local state. A component that edits document-owned state should update its local control
state immediately, send the return value, and then reconcile when the resulting document snapshot
arrives; binding a text field directly to the asynchronous return path can fight the caret.
Registrations are revisioned: calling `configureCustomComponents(_:)` after mutating an installed
registry retries the retained document and rebuilds only the registry-bound document tree.

The Swift session counts custom properties and their resolved strings toward the existing per-frame
work and text limits, resolves supported references, and applies supported return values. View
construction, sizing, lifecycle, input, and accessibility remain Apple-native.

## Rendering behavior

### Root sizing and component layout

UIKit lays out components in document coordinates and applies `RootContentBehavior` once at the
root view transform. Inside, fit, fill-width, fill-height, crop, fill-bounds, and alignment modes are
explicit and respond to host resizing. Scroll remains unsupported.

The static profile implements Box, Row, Column, and time-zero StateLayout selection, including
structural content flattening, proportional row/column weights, range constraints, visibility,
offset, z-order, and RTL row order. A collapsible row or column (`CollapsibleRowLayout`,
`CollapsibleColumnLayout`) also collapses: children are measured, the ones that do not fit the
container's axis are hidden in the order their `CollapsiblePriority` modifiers give, and the
container itself is hidden when nothing fits. `NativeSwiftCollapsible` holds that decision so the
UIKit and AppKit renderers cannot disagree about it — including the two rules that are easy to get
backwards, that a child with no priority modifier is kept ahead of one that has a priority, and that
a weighted child never consumes the space that would drop another. Hidden StateLayout alternatives
are not rendered or reported as reachable compatibility failures. Flow, FitBox and scroll are laid
out. A `FitBoxLayout` shows the first alternative whose natural size fits and nothing at all when
none does — the box included, background and all — and an alternative's own visibility modifier is
ignored while it is being chosen, because a document switches alternatives with it. A scrolled
container measures and arranges its children against the *content* they make rather than the viewport
that clips them: the viewport decides what is visible, the content decides where things sit. That
second half is a reference quirk worth stating — `collapsible_column_scroll` centres its survivors in
the pre-collapse total, so a collapse frees no space, it only moves the content — and the scroll
offset itself is read from the document but not yet applied to the paint. Intrinsic sizing and layout
compute remain unsupported; required constraints are clamped to the native parent and
remain diagnosed because UIKit does not yet reproduce their overflow behavior. Layout stays
deterministic and frame-based; Auto Layout would introduce solver behavior not in the Remote Compose
contract. Remote Compose has no general component margin modifier; external spacing is expressed by
parent arrangement and `spacedBy`.

### Density and Android compatibility

A `.rc` header records the density the document was generated at (`DOC_DENSITY_AT_GENERATION`) and
how dp-typed values are meant to be converted at playback (`DOC_DENSITY_BEHAVIOR`). Apple UI is
authored in points and has no equivalent conversion, so the native player does not adopt the Android
contract implicitly. `RemoteComposeNativePlayerAndroidCompatibility` makes the choice explicit and
is independent of renderer selection:

* `.disabled` — the default. dp-typed sizes, padding, spacing, and constraints resolve at a density
  of 1.0, so they are captured document units and scale with the document like every other
  coordinate. A document that carries density-typed geometry is reported rather than silently
  reinterpreted: `NativeDensityPolicy` emits a `Header` warning naming the declared generation
  density, `.compatible` renders it with that difference stated, and `.strict` refuses it. The
  condition is the declared *behavior*, not the generation density — both behaviors convert against
  the **playback** density, which is not knowable at decode time, so a document generated at density
  1 still differs the moment a host plays it at anything else.
* `.enabled` — dp-typed geometry converts with the playback density that the root transform
  resolved (document units per host point), reproducing the authored Android layout. This is what
  makes the density-2 `TitleCardRemote-640x480` fixture match the CMP player, which always applies
  the Android contract because it is the compatibility oracle.

`DENSITY_BEHAVIOR_PIXELS` is not implemented in either mode; a document that declares it at a
non-unit density is diagnosed whichever mode is selected.

#### The other density axis: what the capture did

`DOC_DENSITY_BEHAVIOR` says how dp-typed values convert. It does **not** say whether the document
carries a density at all, and that is a separate question with a separate answer:

* A **`RemoteDensity.from(displayInfo)` capture** folds the capture device's density and font scale
  into literal constants. Its numbers only mean what they meant on that device — this is the
  document worth warning about when the player is not reproducing that density.
* A **`RemoteDensity.Host` capture** defers instead: it writes expressions over the player-supplied
  `ID_DENSITY` (27) and `ID_FONT_SIZE` (33) — `([33] 14.0 / [27] / 15.0 *)` for a 15sp label — so
  the same bytes resolve correctly at whatever density the player supplies, 1.0 included. Nothing to
  warn about; something to *supply*.

Every fixture this repository shipped before `host-density.rc` was the first kind, which is why no
lane noticed that neither the CMP player nor the Swift core loaded those two ids. Both now do:
`RcSystemVariables.DENSITY`/`FONT_SIZE` on the Kotlin side, seeded from `LocalDensity` through
`RcPlayerState.setHostDensity`, and `NativeSwiftSystemVariables` on the Swift side, seeded from
`setHostDensity(_:fontScale:)` and defaulting to 1.0 — which is the density the native default mode
already resolves dp geometry at, so the two agree by construction. `RemoteComposeNativePlayerView`
exposes `configureHostDensity(_:fontScale:)` for a host reproducing Android geometry.

The Swift core resolves those deferred values rather than declining them. Text size and weight,
width and height, padding, corner radius, min/max constraints and row/column spacing are all kept as
the raw wire word and resolved with `NativeSwiftFloatExpression.resolve` at snapshot time — the same
path draw commands have always taken — so a `RemoteDensity.Host` capture renders at whatever
`setHostDensity` supplied. The validation those fields used to get while parsing moved with them: a
size that resolves non-positive, or a dimension that resolves non-finite, throws a typed
`NativeSwiftCoreError` instead of reaching UIKit. The explicit refusal survives where a field
genuinely cannot resolve later — `floatWord(_:requireLiteral: true)`, which is what a background
written as colour channels rather than a colour id still uses.

This also closed a quieter bug on the way. A Row read its spacing with `requireLiteral: false` and
stored the reference word's raw bits into a plain `Float`, so a computed `spacedBy` laid out at
`NaN` — silently, while the Column one opcode later refused the identical word. Accepting a
reference and honouring it are now the same thing.

```swift
let player = RemoteComposeNativePlayerView(
  data: androidAuthoredDocument,
  androidCompatibility: .enabled)
```

The mode is settable after construction (`view.androidCompatibility`,
`controller.configureAndroidCompatibility(_:)`, or the representable's initializer) and changing it
reloads the retained bytes, so a host can inspect the diagnostic first and then opt in.
`scripts/check-native-uikit-comparison.sh` renders the title card through both modes against the
same CMP lane, so the default and the opt-in are both measured.

### Paint and graphics state

The runtime resolves paint into each drawing command before UIKit rendering. Core Graphics state
remains ordered for save/restore, clipping, and transforms.

Current paint support is color, alpha, fill/stroke, stroke width/cap/join, Core
Graphics-representable blend modes, ordered line/quadratic/cubic paths, path clipping, inline
linear/radial/sweep gradients, and system-font size. Sweep gradients use bounded Core Graphics
tessellation because `CGContext` has no conic-gradient primitive. Non-clamp gradient tile modes and
rational conics are approximated with explicit diagnostics. Referenced shaders, color filters, path
effects, and document-declared font axes produce diagnostics or unsupported opcodes.

A bitmap texture paint carries a `SHADER_MATRIX` — an RPN `MATRIX_EXPRESSION` over the document's
floats — that maps the bitmap onto the shape, and a tile mode per axis. Both are applied: the matrix
transforms each tile draw in the same user space as the clipped path, clamp and repeat tile it,
mirror flips alternate tiles, and decal paints only the bitmap's own area. A `CGPattern` was tried
first and painted nothing on a UIKit layer context — a pattern matrix is device-space and ignores
the CTM — so the fill draws transformed images instead, capped at a bounded tile count. Clamp only
clamps where the reference does when the shape lies inside the mapped bitmap; outside it this player
repeats rather than extending the edge pixels. Setting a texture or a gradient replaces the paint's
previous shader.

How an image or texture is sampled is paint state too. `IMAGE_FILTER_QUALITY` (0 none, 1 low,
2 medium, 3 high) and the legacy `FILTER_BITMAP` flag map onto Core Graphics'
`interpolationQuality`; a paint that names neither leaves the context's own default in place, and a
command that names neither takes the default back rather than inheriting the previous command's.

### Text

Layout text uses real `UILabel`s and attributed strings. UIKit owns Unicode shaping, bidirectional
text, native fallback, Dynamic Type, accessibility, wrapping, line limits, ellipsis placement,
logical start/end alignment, line height, letter spacing, and decoration. Its intrinsic measurement
feeds the same frame-based layout pass as other components. The Swift session resolves supported
`CoreText` properties before UIKit sees them.

A `CoreText` carries its weight on the text run, not on the font resource, so one resolved family is
asked for several weights inside a document. When a face declares a `wght` variation axis, the run's
weight is set on that axis; a static instance has no axis to set, so the weight is carried as the
bold symbolic trait instead. The shared `RemoteComposeGoogleFontsResolver` requests the variable
face (modern agent, full weight range) so the axis exists to be set, and falls back to the plain
family and then the legacy TrueType response when a family has no such axis.

Canvas text uses Core Text inside the ordered Core Graphics command stream, preserving the active
transform, clip, blend state, baseline anchor, and primitive interleaving. Canvas glyphs intentionally
remain fixed-size document graphics rather than Dynamic Type content. Generic sans-serif, serif,
and monospace families map to deterministic system designs. Other named families resolve only from
font bytes declared by the document or an explicitly injected
`RemoteComposeDownloadableFontResolving` source. `google:` families can use the shared
`RemoteComposeGoogleFontsResolver`; UIKit waits for validated bytes and then registers them through
CoreText before installing the document view. Without a resolver, a `google:` family uses the
native system default without failing the document. Fonts installed elsewhere in the process are
not consulted opportunistically.

### Images and resources

The snapshot exports image metadata and opaque bytes without asking UIKit to perform network I/O.
Inline PNG and raw alpha/RGBA resources are decoded only after enforcing per-resource,
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

```swift
import RcNativePlayerUIKit
import RcPlayerAppleFonts

RemoteComposeNativePlayerRepresentable(
  data: documentData,
  downloadableFontResolver: RemoteComposeGoogleFontsResolver()
)
```

The standard protocol text operations carry a single style rather than inline attributed runs, and
they do not carry a locale property. Link spans are a separate `SupportSpannableString` custom
component, to be handled by the semantic component registry. UIKit uses its Unicode script and host
locale behavior for the standard operations. Font variation axes, autosizing properties, exact CMP
metrics, and exact text pixels remain outside the current static profile and are reported rather
than silently claimed.

### Errors

Oversized input fails before decoding. Swift decode/link failures become
`RemoteComposeNativePlayerError`. The view shows a native error label for the POC; a production API
should add a first-class `onError` closure.

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
owning node. A conceptual control with no authored label derives one from visible descendant text
and owns those descendants as a single assistive-technology target, while its `UILabel`s remain
native rendering views. A canvas never becomes one monolithic accessibility element merely because
it draws.

The wire operation has no checked/selected bit, progress range, or adjustable action callbacks, so
the player does not guess them from localized state text. Custom accessibility actions, complete
stateful-control behavior, physical-device VoiceOver speech and Switch Control navigation, and
explicit high-contrast behavior remain outside the current core profile. Simulator XCUITest covers
accessibility-server discovery, stable identity, label ownership, enabled/hittable state, and
Apple's standard element/trait/description/hit-region audit.

Click modifiers map to transparent `UIControl` subclasses owned by the semantic component. UIKit
hit testing follows the rendered component transform and explicitly orders overlapping children by
Remote Compose z-index, then insertion order. Hidden, clipped, or disabled controls do not dispatch.
The winning component id enters the retained session; its ordinary and single-click action blocks
execute in wire order and emit typed Swift action values exactly once. Generation and session
identity checks prevent a replaced document from delivering an old callback. Long press,
double-click, drag, scroll gestures, and raw touch expressions remain explicit compatibility gaps.
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
- `scripts/check-native-uikit-accessibility-ui.sh` queries the simulator accessibility server
  through XCUITest, verifies the native Title Card button owns its descendant labels, and runs
  Apple's element, trait, description, and hit-region audits;
- `scripts/check-native-uikit-frame-timing.sh` advances monotonic timestamps directly and verifies
  static, continuous, one-shot, delayed, paused, resumed, and Reduce Motion scheduling policy;
- `scripts/check-native-uikit-animation-simulator.sh` captures two native Progress frames and
  requires both visible ink and changing pixels within the document surface;
- `scripts/check-native-uikit-comparison.sh` renders Title Card, indeterminate and determinate
  progress, arc progress, and a texture-backed button through CMP JVM and the packaged UIKit player,
  validates the shared manifest contract, and records pairwise pixel scores;
- `scripts/rc-operation-conformance/render-lanes.sh --native-uikit` includes UIKit in an arbitrary
  macOS comparison corpus rather than maintaining a separate fixture format;
- `scripts/measure-native-uikit-simulator.sh` records packaged Release timing, hierarchy,
  accessibility, allocation, memory, binary-size, lifecycle-recovery, and deallocation evidence;
- `scripts/check-native-swift-fuzz.sh` mutation-fuzzes the pure-Swift core against the derived
  corpus described below;
- `scripts/measure-native-swift-core.sh` and `scripts/measure-native-appkit.sh` publish the
  host-independent and AppKit halves of the native performance evidence;
- `scripts/check-native-uikit-density.sh` executes the density contract as a pure Swift test;
- `scripts/build-apple-player.sh` compiles and links the Swift sources to the XCFramework;
- the sample toggles CMP/native for the same bundled files.

Physical-device release evidence still requires VoiceOver speech, Switch Control navigation,
frame-pacing and memory measurements, plus an external released-artifact consumer check. Pixel
thresholds can start tolerant; structure and state should be exact from the beginning.

### Fuzzing and performance budgets

The core is the only thing between host bytes and a retained render session, so its property is
narrow and absolute: any input either decodes into a bounded snapshot or fails with a typed
`NativeSwiftCoreError`. It may never trap, never throw an untyped error, and never run unbounded
work.

`scripts/check-native-swift-fuzz.sh` asserts exactly that. The corpus is **derived, not committed**:
every fixture the support profile lists as verified is a seed, alongside empty, single-byte,
header-only, all-zero, all-ones and noise seeds, and a deterministic PRNG expands each into
truncated, bit-flipped, spliced, deleted, inserted, zeroed, duplicated and extreme-word variants —
`-1`, `Int32.max`/`min`, `±infinity` and NaN written over the big-endian words where counts, lengths
and floats live. Deriving it rather than checking bytes in means the corpus follows the fixture set
instead of drifting from it. Each case is run through the whole retained surface: decode, frames at
several times including negative and far-future ones, host float/string/color updates under hostile
names, every gesture kind against real and impossible component ids, custom return channels, and a
determinism check that the same time resolves the same tree twice.

A second, **structure-aware** family reaches the code byte-level corruption cannot. The core hands
back the operation spans it walked (`NativeSwiftDocumentSession.operationSpans`), so the mutator and
the decoder cannot disagree about framing; it then perturbs operand words toward boundary values,
duplicates, drops and swaps whole operations, and unbalances container begin/end pairs by exactly
one. Those inputs stay walkable, so a rejection means the decoder judged the *content* — expression
evaluation, layout modifiers, resource metadata — rather than the framing. The two families
complement each other and the run prints both counts: at the default width roughly a fifth of cases
now decode, against about a sixteenth with byte-level mutations alone, and the gate is a proportion
of cases rather than a bare seed count. The first soak with the new family found a genuine trap —
a colour channel computed to infinity reached `Int(value)` in the ARGB packer — which is fixed by
saturating a non-finite channel instead of converting it.

Failures are reproducible and self-reporting. `RC_NATIVE_FUZZ_SEED` and `RC_NATIVE_FUZZ_ITERATIONS`
replay the same case sequence on any host, a failing case writes its bytes to
`RC_NATIVE_FUZZ_CORPUS_OUT` (uploaded by CI), and a watchdog thread turns a hang into a named
failure rather than a job timeout. `RC_NATIVE_FUZZ_SANITIZE=address|thread|undefined` rebuilds the
same harness under a sanitizer; CI runs the plain pass at full width and a shorter sanitized pass,
because the sanitizer — not the mutation count — is the expensive part.

Performance evidence is published from three lanes that answer different questions, each emitting a
machine-readable report that carries the budgets it was judged against, so a CI artifact is
reviewable without rerunning it:

| Lane | Script | Covers |
| --- | --- | --- |
| Core | `scripts/measure-native-swift-core.sh` | Decode, first frame, steady-state frame cost over a second of animation, document replacement, and retained-session memory growth, per fixture, on any macOS host |
| UIKit | `scripts/measure-native-uikit-simulator.sh` | The packaged Release app on a fixed iPad simulator: view hierarchy, accessibility exposure, allocation, footprint, binary size, background/foreground recovery, deallocation |
| AppKit | `scripts/measure-native-appkit.sh` | The packaged macOS player: native view-tree build, a real Core Graphics capture, steady-state frames driven end to end through the renderer (resolve, reconcile, lay out, draw), retained memory |

The timing budgets are order-of-magnitude ceilings rather than tuning gates — a hosted runner's
clock is too noisy to gate a device-quality number, so the numbers are published and the gate only
catches a real regression. All three reports, plus the rendered comparison output, upload as the
`native-performance-evidence` CI artifact. The structural numbers are the exact ones: node and command counts, view
counts, accessibility exposure, and whether a replaced document is released.

## Distribution and compatibility

`Package.swift` exposes `RcNativePlayerUIKit` separately. Existing consumers selecting
`RcComposePlayer` or `RcComposePlayerSwiftUI` see no source change.

The bare semantic-version tag is the normal SwiftPM distribution. Releases also publish
`RcNativePlayerUIKit.swiftpackage.zip` plus a SHA-256 sidecar. The archive is a self-contained local
Swift package with the UIKit sources and no binary target. CI extracts and builds that archive
before release, so it tests the downloadable
unit rather than only the repository source tree. This is intentionally an experimental artifact;
it does not imply parity with or replacement of the CMP product.

The native artifact adds no ABI to the CMP XCFramework. Before stable release, decide which Swift
runtime and model types remain internal and which host-facing capabilities warrant stable API.

The current distribution, compatibility, provenance, and migration decisions are recorded in
[`RC_NATIVE_UIKIT_DISTRIBUTION.md`](RC_NATIVE_UIKIT_DISTRIBUTION.md). Each release publishes the
validated `rc-native-uikit-core-v1` machine-readable profile beside the standalone archive and
embeds the identical profile inside it.

## Security and robustness

The native lane accepts untrusted bytes wherever CMP does. The codec refuses documents above 16
MiB or 100,000 operations, including conditional records omitted from the decoded model. The linker
then bounds container nesting, expansion depth, and expanded nodes. UIKit adds two independently
configurable policies: resource limits cover bitmap/font bytes, counts, decoded dimensions, and
decoded pixels; execution limits cover native node depth/count, commands, path elements, text,
canvas dimensions, finite coordinate magnitude, and total frame work. Every initial, animated, and
input-produced frame passes the same typed validation before view reconciliation or Core Graphics.
Custom components require an explicit host registry; documents never instantiate arbitrary
Objective-C classes by name.

## Evolution plan

The detailed, PR-sized implementation sequence and acceptance gates live in
[`RC_NATIVE_UIKIT_PLAYER_PLAN.md`](RC_NATIVE_UIKIT_PLAYER_PLAN.md). The phases below describe the
product maturity boundaries; the plan describes how to reach them.

### Phase 0: architecture POC (this change)

Separate product and sample toggle; pure-Swift static session; recursive `UIView` hierarchy; a small
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

### Phase 4: pure Swift core (in progress)

The runtime dependency and fallback are removed. Continue extending the bounded wire reader, typed
protocol operations, link/evaluation state, and actor-isolated session. Validate each family against
the independent CMP oracle before adding it to the released profile. No phase replaces CMP.

## Alternatives considered

- **Another wrapper around Compose:** rejected because the existing overlay already does that.
- **Downcast exported Kotlin operations in Swift:** rejected because it spreads wire/runtime detail
  and couples UIKit to the generated Kotlin class surface.
- **Port the decoder/runtime first:** originally deferred until native rendering proved valuable;
  custom components, interaction, animation, safety limits, and comparison evidence now justify a
  staged pure-Swift replacement behind the established player API.
- **One `UIView` for the document:** rejected as the architecture because it obstructs native
  layout, semantics, hit testing, custom components, and localized invalidation.
- **A `CALayer` component tree:** deferred as an optimization; `UIView` supplies traits, lifecycle,
  accessibility, and input composition with less infrastructure.

## Open decisions

- What is the smallest independently testable protocol family for the first pure-Swift session?
- Which static profile is small enough to finish but useful enough to evaluate?
- Which runtime expression and animation families should remain oracle-backed longest?
- How should Core Text metrics reconcile with AndroidX's measured contract?
- Can command buffers be diffed cheaply, or do components need narrower typed updates?
- What compatibility and performance bar promotes the player beyond experimental status?

The POC exists to generate evidence before those choices become compatibility commitments.

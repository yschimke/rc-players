# Swift review: `RcNativePlayerCore` / `RcNativePlayerUIKit`

A review of the native UIKit player from the perspective of someone who writes Apple platform code
for a living and did not spend the last five years in `androidx.compose`. It is deliberately
unkind, because the polite version of this feedback has evidently not landed.

Scope: `Sources/RcNativePlayerCore/NativeSwiftCore.swift` (2083 lines),
`Sources/RcNativePlayerUIKit/` (~3400 lines), `Tests/RcNativePlayerUIKitTests/`, and the SwiftPM
manifest. The CMP player, the Wasm host and the vendored AndroidX cuts are out of scope.

The design doc ([`RC_NATIVE_UIKIT_PLAYER.md`](RC_NATIVE_UIKIT_PLAYER.md)) states "proof of concept"
and lists non-goals. Several findings below are fair against that bar; the ones that are not are
marked **[POC-fair]** and parked. The rest are not "we haven't got to it yet" — they are choices
that will have to be unmade before anyone can build on this.

---

## The one-sentence version

This is a well-tested, carefully bounded, genuinely thoughtful Android renderer that has been
transliterated into Swift syntax, and at no point did anyone ask what Swift or UIKit would have done
instead.

---

## 1. The type system is switched off

### 1.1 `Int` is not a type

Count the domains modelled as bare `Int` in the public-ish surface:

| Field | File | Real domain |
| --- | --- | --- |
| `NativeSwiftDrawCommandSnapshot.kind` | Core | 20-odd draw ops |
| `.blendMode`, `.strokeCap`, `.strokeJoin`, `.pathWinding` | Core | CG enums |
| `NativeSwiftNodeSnapshot.widthType` / `heightType` | Core | 9 layout modes |
| `.horizontalPositioning` / `.verticalPositioning` | Core | alignment + arrangement |
| `NativeSwiftAccessibilitySnapshot.role` / `.mode` | Core | 10 roles / 3 merge modes |
| `NativeSwiftTextSnapshot.style` / `.alignment` / `.overflow` | Core | bitfield + 2 enums |
| `NativeSwiftCustomPropertySnapshot.dataType` | Core | 10 wire types |
| `densityBehavior` | Core | 3 documented behaviours |
| `NativeImageDraw.scaleType` | UIKit | 8 scale types |

Every one of these is then decoded by a `switch` on an integer literal, at every call site, with a
`default:` that silently does something. `NativeGraphicsState.blendMode(_:)` maps 29 integers with a
`default: .normal`. `NativeTextPolicy.alignment(value:justified:direction:)` takes an `Int`.
`NativeLayoutDimension.resolve` switches `case 0, 6:` / `case 1, 7, 8:` / `case 3:` / `default:` and
you have to read `estimatedDimension` in a different module to learn that `2` means wrap-content
and `3` means weight.

The project *knows how to do this* — `NativeSwiftGestureKind`, `NativeAccessibilityRole`,
`NativeAccessibilityMode`, `RemoteComposeNativeCustomProperty.Kind` are all `Int`-raw-value enums.
They were written and then not used: `NativeNode.semanticRole` is still `Int`, wrapped back into
`NativeAccessibilityRole(rawValue:)` at the point of use, with `-1` as an out-of-band "no role"
sentinel *next to* a perfectly good `Optional`.

**Remediation.** One `RawRepresentable` enum per wire domain, in `RcNativePlayerCore`, converted
exactly once — at the decoder, where the wire integer actually exists. Everything downstream takes
the enum. `default:` in a `switch` over a closed enum then becomes a compile error instead of a
silent wrong pixel, which is the entire point. Where the wire genuinely is open-ended, `enum Foo {
case known…; case unknown(Int) }` keeps the round-trip honest.

### 1.2 `values: [Float]`, indexed positionally

`NativeSwiftDrawCommandSnapshot` is a 15-field struct in which the meaning of `values` depends on
`kind`, and most of the other 14 fields are inert for any given command. The renderer reads it as:

```swift
case 14:
  let path = UIBezierPath(
    roundedRect: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
    cornerRadius: max(v[4], v[5]))
```

`v[5]`. On an array whose length is a decoder invariant expressed nowhere in the type. `drawArc`
reads `v[0...5]`, `drawText` reads `v[0...3]`, `NativePathBuilder.make` reads up to `v[5]` behind a
`default: break` that will not save you because the crash happens on the line above it. The decoder
does currently guarantee the counts; the compiler does not know that, the next contributor does not
know that, and a `precondition`-free release build turns a malformed document into
`Fatal error: Index out of range` rather than the typed `NativeSwiftCoreError` the whole module is
otherwise scrupulous about.

**Remediation.** This is the single highest-value change in the review:

```swift
public enum DrawCommand: Sendable {
  case save, restore
  case translate(CGVector)
  case scale(CGSize, pivot: CGPoint?)
  case rotate(Angle, pivot: CGPoint?)
  case clip(CGRect)
  case clipPath(Path, FillRule)
  case rect(CGRect, Paint)
  case roundedRect(CGRect, radii: CGSize, Paint)
  case arc(CGRect, start: Angle, sweep: Angle, kind: ArcKind, Paint)
  case text(TextRun, at: CGPoint, pan: CGPoint)
  case image(ImageDraw)
  // …
}
```

The renderer's `switch` then exhausts, `v[5]` becomes `radii.height`, the "is this field meaningful
for this kind" question disappears, and `Paint` stops being eleven parallel fields smeared across
every command including `save`/`restore`, which have no paint.

### 1.3 Sentinels where `Optional` exists

`maximumWidth: Float` uses `-1` for "none", encoded as a literal word, laundered through
`maximumWidth > 1_000_000 ? -1 : maximumWidth` in the core, then converted back with
`snapshot.maximumWidth < 0 ? nil : CGFloat(...)` in the UIKit layer. Three representations of
absence for one concept, two of them numeric. `familyID: Int = -1`, `semanticRole = -1`,
`contentDescriptionID == 0 ? nil : …` — same story. `NativeSwiftCustomSnapshot` properties carry
`floatValue`, `integerValue` *and* `textValue` simultaneously, only one of which is ever live, with
`dataType: Int` as the discriminator. That is a C tagged union written out by hand in a language
with `enum`.

---

## 2. The naming is Java with the dots removed

`RemoteComposeNativePlayerCompatibilityPolicy`. `RemoteComposeNativeCustomComponentRegistry`.
`RemoteComposeNativePlayerAndroidCompatibility`. `RemoteComposeNativeFloatReturnProperty`.
`NativeSwiftDocumentSnapshot` — a Swift type, in a Swift file, in a Swift module, named "Swift".
`NativeSwiftNodeSnapshot`, `NativeSwiftPathElementSnapshot`, `NativeSwiftCustomPropertySnapshot`.

Swift has had module-scoped namespacing since 2014 and nested types since forever. The prefix is
load-bearing in exactly zero of these cases; `RcPlayer.CompatibilityPolicy`,
`RcPlayer.Diagnostics`, `Document.Snapshot`, `Document.Node` say the same thing and fit on a line.
`RemoteComposeNativePlayerViewController.configureAndroidCompatibility(_:)` is 58 characters to set
a two-case enum.

The method names match. Six `configure…` setters —
`configureResources(limits:resolver:)`, `configureExecutionLimits(_:)`,
`configureAndroidCompatibility(_:)`, `configureHostDensity(_:fontScale:)`,
`configureDownloadableFonts(resolver:)`, `configureCustomComponents(_:)` — sitting next to four
properties (`playerBackground`, `compatibilityPolicy`, `androidCompatibility`, `onEvent`) that do
the identical job through `didSet`. The same class exposes both idioms for the same category of
state, and `androidCompatibility` is reachable *both* as a property with a `didSet` *and* via
`configureAndroidCompatibility`. Pick one. It should be the property.

**Remediation.** Drop the prefixes to module scope. Collapse the `configure…` family into a single
`Configuration` value type (`var configuration: RcPlayer.Configuration { didSet { … } }`) — it is
one `Equatable` struct, one diff, one reload decision, and it removes the six separate
"did anything change?" comparisons currently hand-written in the view, including the two that
compare resolver identity with `!==` by hand.

---

## 3. `RemoteComposeNativePlayerView` is unowned complexity

This class is 800 lines and holds **eighteen** pieces of mutable coordination state:

```
loadTask, pendingWork, inputTail, loadGeneration, inputGeneration, lifecycleGeneration,
sessionEpoch, retainedSessionEpoch, isApplicationActive, needsForegroundRender,
isRenderingDocument, needsRetry, currentFrameTime, serializedInputCount, deferredFrameTime,
frameSchedule, hasPendingScheduledFrame, frameDriverGeneration
```

`pendingWork` is assigned in five places and read in none. It is dead. `PendingWork` is a
two-case enum that exists to be written to.

The rest is a hand-rolled cancellation protocol. `updateSession(_:)` contains this guard —

```swift
guard
  isApplicationActive, lifecycle == lifecycleGeneration,
  epoch == sessionEpoch, retainedSessionEpoch == epoch,
  self.retainedSession === retainedSession
else { return update.accepted }
```

— **six times**, in one 90-line function, with three different failure values (`update.accepted`,
`false`, and a fall-through that dispatches events anyway). Four of the six differ from each other
by one clause. There is no way to read this function and be confident it is right, and no test
could cover the state space it implies.

Meanwhile `enqueueInput` serialises work by having each `Task` `await previous?.value` — an
unbounded chain of retained `Task`s, each holding the last, with `inputTail` as the head. A dropped
frame under load means a strong chain of suspended tasks; nothing bounds its length.

**Remediation.** The generation counters are a symptom of doing actor work in a `@MainActor` class.

1. Move the load/frame/input pipeline into an `actor RcPlayerEngine` that owns the session, the
   current frame time and the input queue. Serialisation is then the actor's job, not
   `inputTail`'s, and the "did the world move under me" checks collapse to a single monotonically
   increasing `generation` compared once on the way out.
2. Replace the boolean pile with one state enum — `enum State { case idle, loading(Task<…>),
   presenting(Document), failed(any Error) }` — so illegal combinations
   (`isRenderingDocument && needsRetry`) stop being representable.
3. Use structured concurrency for cancellation. `loadTask?.cancel()` + a manual generation counter
   is what you write when you cannot scope a child task; a task group or an
   `AsyncStream` of requests scopes it for you.

---

## 4. Concurrency annotations that are decoration

`NativeSwiftDocumentSession` is a `final class` with six `var` properties and is declared
`@unchecked Sendable` with no lock, no queue and no comment justifying it. It happens to be safe
today because exactly one `actor` holds it, which means the correct annotation is *no* annotation:
let the actor's isolation do the work, and let the compiler enforce it. `@unchecked` is a promise to
the compiler, and this one is being kept by luck.

Elsewhere, `NativeSwiftCoreError` is a `public enum` that is thrown across an actor boundary and is
not `Sendable` (public types get no implicit conformance). The package builds today because it is
`swift-tools-version:5.9` with no `swiftLanguageMode` and no
`.enableUpcomingFeature("StrictConcurrency")` — the moment anyone turns Swift 6 mode on, this
module will not compile, and the fixes will not all be one-liners.

`NativeFontRegistry.deinit` spawns an unstructured `Task { @MainActor in … }` to unregister fonts.
`deinit` cannot be `@MainActor`-isolated, so the cleanup is fire-and-forget, unordered relative to
the next registration of the same PostScript name, and dropped entirely at process exit.

**Remediation.** Turn on `StrictConcurrency` in `Package.swift` now, while the module is small, and
fix what falls out. Delete `@unchecked`. Make `NativeSwiftDocumentSession` either an `actor` or a
`struct` with value-semantic frame resolution (it is nearly one already — `snapshot(timeSeconds:)`
is a pure function of the parsed document plus four dictionaries). Replace the `deinit` Task with
explicit ownership: fonts are released when the resource store is replaced, which `install(_:)`
already knows how to do.

---

## 5. UIKit used as a drawing buffer

### 5.1 One `UIView` per node, at 20,000 nodes

`NativeComponentView` is a `UIView` per Remote Compose component, each with up to four child views
(canvas, labels, image views, custom, semantic), all laid out by assigning `.frame` in
`layoutSubviews`, with `NativeExecutionLimits.maximumNodeCount = 20_000`. That is a view hierarchy
UIKit will not survive. Nothing here needs a view: this is an immutable scene graph redrawn from a
snapshot. `CALayer` (no touch handling, no responder chain, no Auto Layout engine) is the right
primitive, and for the pure-drawing subtree a single layer with a `CGContext` is better still.

The views exist for two reasons, both solvable without them: hit testing (solvable with a geometry
walk over the snapshot — you already have every frame) and accessibility (see below).

### 5.2 Layout is re-measured, quadratically

`preferredSize(in:)` recurses into every child. `layoutColumn` calls it for all children, then
`applyDimensions` per child. `layoutRow` calls it, allocates weights, and then **calls
`preferredSize(in:)` again** on every child at its allocated width. Each of those recursions
re-descends the whole subtree. Depth *d* of nested rows costs O(2^d) measurement passes over the
leaves. `maximumNestingDepth` is 256.

And `preferredSize(maximumWidth:documentScale:)` on `NativeTextLabel`, called from the innermost
loop of that recursion, constructs a **fresh TextKit 1 stack** —
`NSTextStorage` + `NSLayoutManager` + `NSTextContainer` — per call, per frame. `NSLayoutManager` is
soft-deprecated in favour of TextKit 2, and this is the most expensive way in the framework to ask
"how tall is this string".

It also mutates the receiver: `preferredSize` assigns `self.documentScale` and calls
`configureFont`. A sizing query with side effects, called from layout, is how you get
"layout is dirty during layout" warnings and non-deterministic frames.

**Remediation.** Measure once, in a pure pass over the snapshot, into an immutable
`LayoutResult` tree of frames; then apply. No view participates in measurement, nothing is mutated
during it, and it is directly unit-testable without a window — which the project already believes
in (`NativeLayout.swift`'s doc comment says exactly this) and then abandoned for the real tree. For
text, `CTFramesetterSuggestFrameSizeWithConstraints` with a cached `CTFramesetter` per run, keyed
on (string, attributes, width).

### 5.3 The redraw signature

`NativeCanvasView.signature(commands:images:fontNames:)` hashes every command, every path point,
every colour component, every text style field, and every image's `ObjectIdentifier` — on every
frame — to decide whether to call `setNeedsDisplay()`. This is ~80 lines of hand-written `Hasher`
feeding, it allocates via `cgColor.components` (which is `nil` for pattern and monochrome colours,
silently skipping them), and a hash collision means a *silently dropped frame*. Correctness by
64-bit hash, to avoid a redraw that would have been cheaper than computing the hash.

**Remediation.** Make the command model `Equatable` (it is all value types the moment §1.2 lands)
and compare. Or redraw unconditionally — `setNeedsDisplay` is already coalesced by the run loop.

### 5.4 Fonts via the filesystem

`NativeFontRegistry.register(data:id:)` writes document-controlled bytes to
`FileManager.default.temporaryDirectory`, registers the *URL* with `CTFontManager` at `.process`
scope, and refcounts PostScript names in a `private static var processRegistrations` dictionary —
global mutable state, keyed by a string the document controls. Temp files leak on crash. Two
players in one process share the refcount table across independent documents.

`CTFontManagerRegisterGraphicsFont(_:_:)` takes a `CGFont` and touches no disk. It has existed
since iOS 4.1. Use it, scope the registration to the player, and delete the static.

### 5.5 Everything else in this layer

- `hitTest(_:with:)` is overridden to sort children by `layer.zPosition` — allocating a sorted array
  per hit test — except `zIndex` is hard-coded to `0` at construction (`NativeNode.init`), so the
  sort is a no-op over constants and the comparator's tie-break on `offset` is the only live branch.
- `UIColor(patternImage:)` for textured fills. Pattern colours are anchored to the *layer's*
  coordinate space, not the current CTM, so any translate/scale in the command stream above the
  fill puts the texture in the wrong place.
- `NativeGradientRenderer.drawSweep` renders a conic gradient as **360 filled pie wedges**, every
  draw. `CGShading` with a custom `CGFunction` does it in one call; on iOS 12+
  `CAGradientLayer(type: .conic)` does it on the GPU.
- `alpha > 0.01` as a visibility test, in two places, undocumented.
- `NativeGraphicsState.apply` does `max(strokeWidth, 0.5)` — silently promoting a hairline to half a
  point, at document scale, on a surface whose `contentsScale` it never reads.
- `NativeDocumentView.layoutSubviews` sets `componentView.transform` to identity, then mutates
  `bounds` and `center`, then sets `transform` again. The identity round-trip is a workaround for
  the `frame`/`transform` interaction that `bounds`+`center` already avoids; it just costs two
  extra layout invalidations.

---

## 6. Accessibility: the right instinct, the wrong API

Promoting semantic components to real controls is genuinely good, and the merge/set/clearAndSet
handling in `NativeAccessibilityDescriptor` is careful work. The execution undoes it.

- `NativeSemanticSwitch` is a real `UISwitch` with `layer.opacity = 0` stacked over the document
  pixels. An invisible, non-interactive `UISwitch`, present solely to donate traits.
- Every semantic view is then set `isUserInteractionEnabled = false`, which means the
  `addTarget(self, action: #selector(activate), for: .touchUpInside)` in all four semantic classes
  is **dead code that can never fire**. Activation only ever arrives through the
  `accessibilityActivate()` override. Four classes, four targets, four `@objc` methods, none
  reachable.
- The four semantic classes (`NativeSemanticButton`, `…Switch`, `…ImageView`, `…Control`) are
  identical apart from their superclass: same two stored properties, same `activate`, same
  `accessibilityActivate`, same `init?(coder:)` trap. They exist only to be distinguished by
  `view is NativeSemanticButton` in `semanticView(_:matches:)`.

**Remediation.** `UIAccessibilityElement` is the type for "an accessibility identity that is not a
view". Subclass it once, give it `accessibilityFrameInContainerSpace`, `accessibilityTraits`,
`accessibilityLabel/Value` and an `accessibilityActivate()` closure, and publish an array of them as
the container's `accessibilityElements`. Zero extra views, zero dead `UIControl` plumbing, one
class instead of four, and it composes with §5.1's move to layers — which the current design
actively blocks, because it needs a real `UIView` subtree to hang controls on.

---

## 7. Code that cannot run

Beyond `pendingWork` and the `zIndex` sort:

| Dead thing | Why |
| --- | --- |
| `NativeGradient`, `NativeGradientRenderer`, the sweep renderer, gradient paths in `paint(_:_:_:fillRule:)` and the hash | `NativeDrawCommand.gradient` is assigned `nil` in both initialisers. Unreachable. |
| `NativeFontRegistry` for document fonts | `NativeDocument.fonts = []`, unconditionally. Only downloadable fonts ever register. The design doc claims "Embedded fonts are validated and registered". |
| `NativeFrameSchedule.wake(after:)`, `NativeWakeCountdown`, `delayedWakeTask`, `frameDriverGeneration` | `requestsNextFrame: false, wakeAfter: nil` are hard-coded at construction. The `.wake` driver mode is unreachable; ~60 lines and a whole tested type serve it. |
| `NativeNode.Kind.init(rawValue: Int32)` and `.group` | Nothing constructs a node from an `Int32`; the only initialiser switches over `NativeSwiftNodeSnapshot.Kind`, which has no `group`. `isStructural` branches on `.group` twice. |
| `NativeNode.visibility` / `.offset` | Hard-coded to `1` / `.zero`. `isHidden`, `alpha`, `offsetFrame`, and five `visibility == 1` guards all operate on constants. |
| `NativeDocument.rootSizing/rootMode/rootAlignment` | Hard-coded `2`/`4`/`34`. `NativeRootTransform.resolve` is a tested function with six modes and nine alignments, of which one combination is reachable. |
| `NativeSnapshotSessionHandle.click(componentID:at:)` | Superseded by `gesture(_:componentID:sample:at:)`; nothing calls it. |
| `returnCustomFloat` | Declared `throws`, never throws, and `return false` is its only exit — with a comment explaining that it always refuses. A public API that documents its own uselessness. |
| `NativeSwiftGestureKind: CaseIterable` | `allCases` never used. |

Some of this is scaffolding for work in flight, and **[POC-fair]** applies to a little of it. It
does not apply to a tested type (`NativeWakeCountdown`), a tested pure function
(`NativeRootTransform`) or an entire rendering subsystem being wired to a constant, because that is
how you get a test suite that is green about code the product does not run.

---

## 8. Errors

`NativeSwiftCoreError.malformed(offset:reason:)` carries a byte offset, which is exactly the right
design. It is then constructed with `offset: 0` in **every** call site outside the decoder —
eighteen of them across expression evaluation, path resolution and field resolution. The field is a
lie in the majority of cases, and callers have no way to tell a real offset from the placeholder.

`.unsupported(opcode:offset:reason:)` is worse: `NativeSwiftFloatExpression.evaluate` throws
`opcode: 81`, `NativeSwiftIntegerExpression` throws `opcode: 144`, `ParsedPath.resolve` throws
`opcode: 123` — each hard-coding the opcode of the operation that *originally produced* the data,
from a context that has no idea which operation that was. `WireReader.floatWord` throws
`opcode: -1`.

Then the boundary:

```swift
// NativeSession.swift:64, 77
throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
```

`NativeSwiftCoreError` conforms to `CustomStringConvertible`, not `LocalizedError`.
`localizedDescription` on a plain Swift `Error` returns the bridged
`NSError` description — so a malformed *frame* (as opposed to a malformed document, which is
special-cased two functions up with `as NativeSwiftCoreError` → `.description`) surfaces to the user
as:

> The operation couldn't be completed. (RcNativePlayerCore.NativeSwiftCoreError error 1.)

And `RemoteComposeNativePlayerView.show(error:)` puts that string in a `UILabel` on screen. This is
a live bug, not a style opinion.

**Remediation.** Make `NativeSwiftCoreError: LocalizedError` and delete the `.description`
special-case; the boundary then works for every error uniformly. Carry the real offset (thread a
`Context` through resolution — it is one parameter) or delete the field. Carry the real opcode via
the enum from §1.1 or delete it. A field that is right 20% of the time is worse than absent.

Also: `resolvedFloat(_:_:values:positive:)` takes a `String` field name for the error message and is
called ~14 times per node per frame. Those literals are static, so the allocation cost is low, but
the API shape — pass a human-readable string into a hot path so a rare throw can be phrased nicely —
is backwards. `@autoclosure () -> String`, or a `Field` enum, or drop it.

---

## 9. Tests

`Package.swift` declares five targets. **None of them is a `.testTarget`.** `swift test` on this
package runs nothing. `Tests/RcNativePlayerUIKitTests/` is not referenced by the manifest at all.

The twelve files in there are `@main enum` executables that assert with `precondition`, invoked by
`scripts/check-native-swift-core.sh`, which `swiftc`s exactly two source files together and passes
seven fixture paths as `argv`. The test body then dispatches on `CommandLine.arguments.count == 4`,
`== 6`, `== 8` — so adding an eighth fixture silently disables the block guarded by `== 8`, and
`precondition` is compiled out under `-Ounchecked`.

Consequences, concretely: `NativeComponentViews.swift` (1913 lines, the entire renderer),
`RemoteComposeNativePlayer.swift` (935 lines, all the concurrency), `NativeResources.swift` and
`NativeCustomComponents.swift` are **never compiled by CI on any platform**. The macOS lane
type-checks `Sources/RcComposePlayerSwiftUI/*.swift` and builds the sample Xcode project; the UIKit
player's own code is reached by neither.

Nor is there a `swift-format` gate. `format.yml` runs `ktfmtCheckAll` and nothing else, and it shows
— `Sources/RcNativePlayerCore/NativeSwiftCore.swift` has a `switch` inside `applyPaint` whose `case`
labels are indented two columns out of line with their `switch`, which any formatter would have
caught.

**Remediation.** In priority order:

1. Add `.testTarget(name: "RcNativePlayerUIKitTests", dependencies: ["RcNativePlayerUIKit"])` and
   port the `precondition` scripts to `swift-testing` (`@Test`, `#expect`) or XCTest. Fixtures go in
   `resources:` and are loaded by `Bundle.module`, not `argv`.
2. Add a `swift build` of the whole package to the macOS lane. Compiling the renderer at all would
   be a strict improvement on the status quo.
3. Add `swift-format lint --strict` to `format.yml`, with a `.swift-format` checked in.
4. Snapshot-test the renderer against the existing `.rc` fixtures once §5.2 makes layout a pure
   function — that is the only way the "compatibility oracle" framing in the design doc becomes
   checkable rather than aspirational.

---

## 10. Smaller things, uncatalogued

- `WireReader.init(_ data: Data)` does `bytes = Array(data)` — a full copy of up to
  `maximumDocumentBytes` (16 MB), then reads it a byte at a time with manual shifts.
  `data.withUnsafeBytes { $0.loadUnaligned(fromByteOffset:as: UInt32.self).bigEndian }` copies
  nothing and is what the standard library is for.
- `WireReader.utf8Bytes(_:length:)` is never called.
- `@available(*, unavailable) required init?(coder:) { fatalError(…) }` appears **eleven times**,
  verbatim. It is unavoidable boilerplate, but eleven copies is a signal about §5.1.
- `NativeSwiftSystemVariables.density = 27` / `.fontSize = 33` — public API surfacing raw wire ids
  as `Int` constants. At minimum a `SystemVariable` enum; better, not public.
- `estimatedDimension` measures text as `texts[id]?.count * size * 0.6`. `String.count` is grapheme
  clusters, so an emoji is 0.6× a font size wide and a CJK glyph is 0.6× when it is ~1.0×. The
  magic constant is an Android heuristic and is wrong on Apple platforms for anything but Latin.
- `interpolateColor` does gamma by hand with `powf(x, 2.2)` and truncates with `Int(...)`, while
  `argbColor` three lines away rounds with `+ 0.5`. Two rounding conventions in one file.
- `NativeImageCache` keys `NSCache` with
  `"\(encoding)|\(type)|\(w)x\(h)|\(reference)" as NSString` — string-concatenated cache keys, a
  Java idiom, from data where a `Hashable` struct is free.
- The `NativeTextPolicy.lineBreak → NSLineBreakMode` mapping is written out twice
  (`NativeTextLabel.configureParagraph` and `NativeTextAttributes.lineBreakMode`), identically.
- `RemoteComposeNativePlayerViewController` forwards twelve methods to `playerView` with no
  behaviour of its own. If the view is the API, say so and let hosts wrap it.
- `#if canImport(RcNativePlayerCore)` around an import of a target this target *declares as a
  dependency*. It can always be imported; the guard is noise that hides a real failure if the
  dependency is ever dropped.
- Lifecycle is observed via `UIApplication.didEnterBackgroundNotification` /
  `didBecomeActiveNotification` with `@objc` selectors. On iPadOS multi-window and on visionOS the
  app-level notifications are the wrong granularity — a player in a backgrounded *scene* keeps its
  `CADisplayLink` running. `UIScene.didEnterBackgroundNotification`, or SwiftUI's `scenePhase` for
  the representable, is the correct signal. Modern spelling is
  `for await _ in NotificationCenter.default.notifications(named:)` in a task tied to
  `didMoveToWindow`.
- `ProcessInfo.processInfo.systemUptime` as the clock. `CACurrentMediaTime()` is the one
  `CADisplayLink` timestamps are in; `ContinuousClock` is the modern spelling of the abstraction
  `RemoteComposeNativePlayerClock` is reinventing.

---

## What is actually good

Saying so is not politeness; these are the parts worth preserving through the rewrite.

- The **bounded-work model**. `NativeFrameBudget` and `RemoteComposeNativeExecutionLimits` treat the
  document as hostile input and account for node count, depth, path elements, text bytes and total
  work with overflow-checked arithmetic. Most renderers do not do this at all.
- The **resource boundary**. `NativeResourcePolicy` validates dimensions, declared-vs-decoded
  mismatch, pixel counts and decoded byte totals before any of it reaches Core Graphics. The
  `RemoteComposeNativeResourceResolving` protocol correctly refuses to perform I/O on the host's
  behalf.
- The **diagnostics model**. Reporting an unsupported operation as structured data with a
  `.compatible` / `.strict` policy, rather than drawing something plausible and wrong, is the right
  call and is rare.
- The **density policy**. `NativeDensityPolicy`'s doc comments are the best writing in the module:
  they explain why dp geometry resolves at 1.0, why gating on generation density would be wrong, and
  what the host has to opt into. That is a real design decision, documented at the point of
  decision.
- The **custom component plugin API**. `makeUIView`/`updateUIView`/`dismantleUIView` deliberately
  mirrors `UIViewRepresentable`, and `registerSwiftUI` is a genuinely nice touch. This is the one
  place the code asked what an Apple developer would expect.
- The **comments generally**. Nearly every non-obvious branch explains itself, including the ones
  explaining why an earlier approach was abandoned. That is worth more than most of what is above.

---

## Remediation, sequenced

Ordered by (value ÷ risk), not by severity.

**Now, cheap, unblocks everything else**

1. Add the `.testTarget` and a `swift build` step. Four lines of manifest; today the renderer is not
   compiled by CI at all.
2. Fix the `localizedDescription` boundary (§8) — conform `NativeSwiftCoreError` to
   `LocalizedError`. Two lines, one user-visible bug.
3. Delete the dead code in §7, or wire it up. Each item is independently decidable.
4. `swift-format` config + lint gate.

**Next, mechanical, large payoff**

5. Wire enums for every `Int` domain (§1.1), converted once at the decoder.
6. `DrawCommand` as an enum with associated values (§1.2), which deletes the positional `values`
   array and the `Paint` smear.
7. Turn on `StrictConcurrency`; delete `@unchecked Sendable` (§4).
8. Collapse the six `configure…` setters into one `Configuration` (§2).

**Then, the real work**

9. Extract layout into a pure measure pass producing an immutable frame tree (§5.2). Everything
   downstream — testability, the quadratic re-measure, the mutating `preferredSize` — falls out of
   this one change.
10. Replace the generation-counter protocol with an `actor` engine and a state enum (§3).
11. Accessibility onto `UIAccessibilityElement` (§6), which is a prerequisite for:
12. `UIView`-per-node → `CALayer`-per-node, or a single drawn layer (§5.1).

**Whenever**

13. `CTFontManagerRegisterGraphicsFont` instead of temp files (§5.4).
14. `CGShading` for the sweep gradient; `Data.withUnsafeBytes` in `WireReader`; scene-level
    lifecycle notifications.

Items 1–4 are worth doing this week regardless of what happens to the POC. Items 9–12 are only worth
doing if this player is meant to become real — but if it is, they are not optional, and every week
of new operation coverage laid on the current foundation makes them more expensive.

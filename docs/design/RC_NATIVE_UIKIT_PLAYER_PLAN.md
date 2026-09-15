# Native UIKit player feature plan

Status: functional work packages 1–11 are implemented for the experimental
`RcNativePlayerUIKit` product; stabilization evidence remains in progress.

This plan turns the architecture POC into a useful native player without weakening the existing
CMP player's compatibility contract. Work is ordered by dependency and by how much real document
coverage it unlocks. Each numbered slice is intended to be independently reviewable and to leave
the player in a releasable state.

## Baseline

The current POC opens a retained Kotlin runtime session off the main actor and renders immutable
frames through a Swift-owned UIKit hierarchy. It has approximate Box, Row, and Column layout,
native `UILabel` text, semantic `UIButton` overlays, a useful Core Graphics subset, explicit
compatibility diagnostics, the Morning Run Title Card regression, and a downloadable experimental
package.

It dispatches the core click/action contract, updates typed named values, schedules only requested
animation frames, and publishes a positive machine-readable profile. It does not claim complete
Remote Compose compatibility: long/double click, drag, scroll, raw touch expressions, advanced
semantic state, and unlisted layout/drawing families remain explicit diagnostics rather than
implicit fallbacks.

## Progress

| Package | Status | Result |
|---|---|---|
| 1 — Static compatibility profile | Implemented | Structured issues, strict/compatible policy, malformed failures, and StateLayout reachability tests |
| 2 — Static layout geometry | Implemented | Pure Swift geometry, constraints, proportional weights, visibility, offsets, z-order, RTL, and root scaling |
| 3 — Core Graphics static drawing | Implemented | Validated paths/clipping, gradients, graphics-state order, stroke caps/joins, and representable blend modes |
| 4 — Static text | Core implemented | Native labels, Core Text canvas glyphs, inherited paragraph styles, bidi alignment, overflow, decoration, and deterministic fallback |
| 5 — Bounded resources | Implemented | Inline/referenced images, `UIImageView` promotion, ordered canvas images, embedded fonts, limits, cache, resolver cancellation, and typed failures |
| 6 — Retained session | Core implemented | Off-main decode, retained codec/link/state, immutable frames, stable component reconciliation, generation cancellation, and atomic replacement |
| 7 — Named values, actions, and input | Core implemented | Typed float/string/color updates, ordered host events, single-click dispatch, semantic controls, and UIKit visual-order hit testing |
| 8 — Semantic UIKit components and accessibility | Core implemented | Native role identity, label/text/value mapping, merge/clear behavior, deterministic VoiceOver order, enabled state, and policy tests |
| 9 — Deterministic time and animation | Core implemented | Runtime frame/wake contract, injectable monotonic timeline, demand-driven display link, lifecycle/Reduce Motion policy, and animated progress fixture |
| 10 — Performance and untrusted input | Core implemented | Decode-time operation ceiling plus configurable typed byte, node, depth, draw, path, text, geometry, and per-frame work limits |
| 11 — Distribution and API evidence | Core implemented | Versioned machine-readable profile, explicit experimental compatibility/migration decision, checksummed consumer archive, and GitHub provenance attestations |

## Delivery rules

- Keep `RcNativePlayerUIKit` beside the CMP products; never route an existing API to it implicitly.
- Land vertical slices: bridge model, Swift implementation, tests, diagnostics, docs, and rendered
  evidence belong in the same change.
- Promote a native UIKit element only when protocol semantics establish its identity. Painted
  content remains drawing; text layout may use `UILabel`; clickable/button semantics may use
  `UIControl`.
- Preserve operation order and graphics state before optimizing view count.
- Treat malformed documents and unbounded work as correctness issues, not later polish.
- Add public API only when a real host needs it. Keep experimental machinery internal where
  possible and record intentional Kotlin/Native ABI changes.

## Milestones

| Milestone | User-visible outcome | Exit gate |
|---|---|---|
| M0 — distributable POC | Title Card renders and a pinned native package can be downloaded | Release archive extracts, resolves, and builds in CI |
| M1 — static profile | A declared set of static catalog cards renders without silent omissions | Profile corpus has no unexpected diagnostics and reviewed CMP/native A/B evidence |
| M2 — interactive profile | Hosts can update values and receive clicks/actions without rebuilding the player | State, event, accessibility, and lifecycle tests pass on simulator |
| M3 — animated profile | Time-based documents update smoothly with bounded scheduling work | Deterministic clock tests and device performance budgets pass |
| M4 — hardened experimental API | Untrusted inputs and resources have explicit limits and stable failure behavior | Fuzz corpus, resource budgets, cancellation, and compatibility policy are enforced |
| M5 — product decision | Evidence supports stabilizing, retaining as experimental, or stopping | API/maintenance review compares UIKit and CMP costs with measured coverage |

## Ordered work packages

### 1. Define the static compatibility profile — core implemented

Replace the current loose opcode notes with structured diagnostics containing operation id/name,
component id, severity, and reason. Add `strict` and `compatible` policies: strict refuses a frame
outside the declared profile; compatible renders the supported subset and returns diagnostics.

Acceptance:

- the supported operation list is generated from the protocol inventory rather than duplicated;
- ignored paint/layout behavior cannot report full compatibility;
- malformed operands produce a typed load error at the Swift boundary;
- tests cover supported, unsupported, malformed, and unreachable operations.

### 2. Complete static layout geometry

Grow the snapshot layout model before adding more visual primitives. Implement exact/wrap/fill,
weight allocation, min/max constraints, margin and padding, Box/Row/Column alignment, direction,
visibility, z-order, and root content behavior. Separate measurement from placement so later state
updates can invalidate only affected subtrees.

Acceptance:

- a pure Swift test suite asserts measured sizes and frames without screenshots;
- nested weighted and constrained layouts match the selected profile fixtures within tolerance;
- RTL and dynamic host resizing have explicit behavior;
- structural wrapper nodes never overwrite frames owned by their nearest layout parent.

### 3. Fill the Core Graphics static drawing set

Implement paths, quadratic/cubic segments, gradients, complete stroke caps/joins, blend modes that
Core Graphics can represent, and clip/save/restore behavior. Keep canvas text in the same ordered
command stream when promotion to a native text element would alter compositing. Bitmap draw geometry
and actual drawing land together with package 5 so every byte enters through the same bounded,
cancellable resource resolver; the static drawing package must not add an unbounded interim decoder.

Acceptance:

- each operation family has a small deterministic image fixture;
- graphics-state nesting and draw order have regression coverage;
- unsupported shader or blend behavior produces structured diagnostics;
- no command indexes unvalidated operand data.

### 4. Make text production-capable for the static profile — core implemented

Add paragraph width, wrapping, line limits, baseline and alignment behavior, attributed spans,
bidirectional text, locale, font weight/style, and deterministic fallback. Use native text views for
conceptual layout text and Core Text for ordered canvas glyph rendering. Define which metrics are
expected to differ from CMP instead of hiding them with broad pixel tolerances.

Acceptance:

- Latin, CJK, Arabic/RTL, emoji, multiline, truncation, and accessibility-size fixtures exist;
- label intrinsic size feeds layout measurement correctly;
- canvas text respects transforms, clipping, and interleaving;
- font fallback and unavailable-font diagnostics are deterministic offline.

The standard `TextLayout` and `CoreText` protocol operations carry one resolved style per text
component, so UIKit applies that style with an attributed string but cannot invent multiple inline
runs. Link spans currently belong to the separate `SupportSpannableString` custom component and are
deferred to package 8's explicit semantic-component registry. The protocol also carries no locale
property in this text vocabulary; UIKit/Core Text performs Unicode script shaping and fallback
using the host locale. Named non-generic families deliberately use the system fallback, with a
diagnostic, until package 5 can resolve font bytes with bounded ownership. Layout labels participate
in Dynamic Type; ordered canvas glyphs remain fixed document graphics.

### 5. Add images and bounded resource loading — implemented

Introduce a host-supplied resource resolver with cancellation, byte limits, decode-size limits, and
cache policy. Support inline and referenced bitmaps first, then downloadable fonts if the security
and ownership model remains acceptable. A document must never trigger arbitrary network access by
default.

Acceptance:

- resource loading is opt-in and injectable;
- replacement or deallocation cancels outstanding work;
- oversized, corrupt, missing, and slow resources have typed errors/diagnostics;
- cache keys include content identity and rendering-relevant parameters.

### 6. Replace snapshots with a retained session — core implemented

Create a narrow renderer-neutral session in Kotlin that owns `RcPlayerState`, exposes immutable
frame deltas, and schedules no UIKit work itself. Swift applies those deltas on the main actor while
retaining stable component views by id. Full snapshot rebuild remains available as a correctness
fallback during development.

The first retained slice exports complete immutable frames rather than a compact wire delta. Swift
reconciles compatible component shapes recursively and mutates the existing canvas, label, image,
semantic, and component views in place; a structural mismatch atomically installs a newly built
tree. Generation-numbered tasks discard stale decode/resource/frame results. Entering the
background cancels outstanding work and records whether a full render must resume on activation.
Compact component-local deltas remain an optimization to measure before making them bridge ABI.

Acceptance:

- loading and decoding can run away from the main actor;
- component identity survives value-only updates;
- document replacement is atomic and cancels prior work;
- lifecycle tests cover repeated load, failure, backgrounding, and deallocation.

### 7. Named values, actions, and input — core implemented

Expose typed named-value updates and event callbacks that mirror the supported CMP host contract.
Route UIKit touch coordinates into the session, implement click/action dispatch, and preserve event
ordering. Start with tap/click; add drag, scroll, and touch expressions only after coordinate-space
tests are exact.

The first interactive slice accepts declared float, string, and packed ARGB named values. Bare
names resolve in the `USER:` namespace; a missing name or type mismatch returns `false` without
changing runtime state. Ordinary click and multi-click `SINGLE` containers become enabled native
buttons and execute their action blocks in wire order. UIKit resolves root/component transforms,
rounded clipping, visibility, enabled state, and visual z-order before sending the winning component
id to the retained session. Host action, metadata, named-action, and debug events cross as typed
Swift values and are delivered on the main actor. Long press, double click, drag, scroll, raw touch
expressions, and compact component-local deltas remain later slices and stay diagnosed.

Acceptance:

- host updates coalesce without dropping the final value;
- clicks emit the expected action once and on the main actor;
- hit testing honors transforms, clipping, enabled state, and z-order;
- replacing a document cannot deliver stale callbacks from the old session.

### 8. Expand semantic UIKit components and accessibility — core implemented

Map explicit roles/states to `UIButton`, `UISwitch`, progress views, image views, and focused custom
`UIControl` subclasses where behavior aligns. Do not infer controls from appearance. Build a stable
accessibility tree with labels, values, traits, ordering, enabled state, and actions.

Acceptance:

- semantic elements are inspectable as the intended UIKit types;
- VoiceOver order follows semantic/layout order, not incidental canvas order;
- Dynamic Type, high contrast, Reduce Motion, and Switch Control behavior is tested;
- visual and semantic hit targets remain synchronized after updates.

The core semantic slice preserves the three distinct authored strings: content description and text
form the accessibility label, while state description becomes the accessibility value. Explicit
button, switch, and image roles own transparent `UIButton`, `UISwitch`, and `UIImageView` subclasses;
checkbox, radio, tab, dropdown, picker, and carousel roles use focused `UIControl` overlays with
role-appropriate button or adjustable traits. The document still owns pixels. Set semantics expose
their children in stable layout order, while merge and clear-and-set semantics replace descendants
with one element; merge de-duplicates descendant labels. Role/mode bounds fail at decode, and
multiple modifiers remain diagnosed because the current bridge intentionally exports one effective
semantic node.

The protocol semantics operation does not carry checked/selected state, a progress range, custom
accessibility actions, or adjustable increment/decrement actions. The player does not infer those
from localized state-description text. `UISwitch` therefore supplies native identity and activation
but only exposes the authored state description as its value. Stateful controls, custom actions,
VoiceOver UI automation, high-contrast policy, and Switch Control verification remain required
before the package can drop its `core implemented` qualifier.

### 9. Add deterministic time and animation — core implemented

Give the retained session an injectable monotonic clock and wake-up contract. Use `CADisplayLink`
only while a frame is due; pause when offscreen/backgrounded and resume without accumulating time
drift. Add layout and graphics animation families incrementally.

Acceptance:

- clock-driven tests advance time without sleeping;
- static documents schedule no display-link work;
- frame pacing, pause/resume, and reduced-motion policy are measured on device;
- updates invalidate only affected views/canvases.

Each immutable bridge frame now reports whether it needs continuous display-paced work, exactly one
next frame, or an earliest delayed `WakeIn`. Moving system-variable reads use the same static
analysis as the CMP player. A real `CADisplayLink` is installed only for due display-paced work;
delayed wakes use a cancellable task and static documents retain neither. The public clock protocol
defaults to system uptime and can be replaced in tests. Its accumulator excludes background,
offscreen, and Reduce Motion intervals, so resuming does not jump the animation clock. One-shot and
delayed functional updates remain enabled under Reduce Motion while continuous decorative motion
is suppressed.

The first graphics family is component-size-driven canvas animation. Before exporting commands, a
native settling pass publishes inherited/exact geometry to the retained runtime, allowing the real
indeterminate progress fixture to resolve finite bounds and changing arc angles. Retained canvas
views hash their render inputs and request display only when those inputs change. Pure timing tests
advance synthetic timestamps without sleeping; the simulator gate captures two real Progress
frames and requires visible, changing pixels inside the document surface.

### 10. Harden performance and untrusted-input behavior — core implemented

Enforce limits for operation count, nesting, macro expansion, path complexity, text length, bitmap
bytes, offscreen dimensions, and per-frame work. Profile decode, first frame, updates, view count,
allocations, and memory on a fixed corpus. Prefer explicit refusal over UIKit/Core Graphics calls
with non-finite or extreme geometry.

Acceptance:

- malformed and adversarial corpora cannot crash or hang the process;
- limits produce typed, diagnosable failures;
- performance budgets run in CI where stable and in scheduled device jobs otherwise;
- no resource, timer, task, or display link survives player deallocation.

The wire decoder now stops at 100,000 operations, counting conditional `Skip` records as work even
though they do not enter the decoded model. This complements the existing 16 MiB document limit,
256-level container limit, 64-level expansion limit, and 100,000-node expansion limit before the
native bridge begins exporting a frame.

Swift exposes a separate `RemoteComposeNativeExecutionLimits` policy. Every initial frame, timed
frame, named-value update, and click result is checked before view reconciliation for document
bytes, node count and nesting, draw-command count, aggregate path elements, per-command UTF-8 text,
canvas dimensions, finite coordinates, coordinate magnitude, and aggregate frame work. Failures
are public `RemoteComposeNativeLimitError` values with stable associated measurements; changing the
policy invalidates the current generation before retrying, so an older task cannot install a frame
after a new policy rejects it. Resource byte and decoded-image limits remain independently
configurable because they govern host resolution and cache ownership rather than execution work.
The asynchronous load path captures only immutable policy/cache inputs while awaiting a host
resource resolver; it no longer holds the player view strongly. Deallocation can therefore cancel
the load and delayed-wake tasks, while the display-link proxy already keeps only a weak owner.

Pure adversarial tests exercise every typed Swift refusal and prove the codec operation ceiling is
applied while decoding. Repository linker tests retain the nesting, recursive macro/reference, and
expanded-node corpus. Release builds exercise the validation in the real UIKit host. The packaged
Release sample now emits a source-identified JSON baseline from seven Title Card iterations on a
named iPad simulator. CI checks broad time, hierarchy, allocation, physical-footprint, executable,
and app-bundle budgets and requires native labels, a native button/control, and accessibility
elements. It also interrupts a real public player view's initial load with background/foreground
notifications, requires the native hierarchy to recover, and verifies ARC deallocation after the
last strong reference is released. Fixed-device time, allocation, memory, and frame-pacing
baselines remain required before this package drops its `core implemented` qualifier; simulator
measurements are a regression tripwire, not a device performance claim.

### 11. Stabilize distribution and API only after evidence — core implemented

Keep the downloadable package and SwiftPM product experimental through M4. At M5, review whether
the Kotlin bridge should become stable SPI, move behind a C interface, or be replaced by a Swift
runtime. Decide minimum OS/architecture support, semantic versioning guarantees, module naming,
and whether the standalone archive remains useful beside normal SwiftPM distribution.

Acceptance:

- public API has a written compatibility policy and migration story;
- the operation/profile claim is machine-readable and release-versioned;
- release artifacts have consumer builds, checksums, and provenance;
- the support decision is based on coverage, performance, binary size, and maintenance data.

The decision remains deliberately experimental. The additive repository product and standalone iOS
package support arm64 devices and Apple-silicon simulators on iOS 13 or newer; neither redirects the
CMP products, and unsupported documents retain an explicit CMP migration path. The reviewed
`rc-native-uikit-core-v1` JSON template lists native node/draw kinds, verified fixtures, platform
matrix, default limits, compatibility policy, and artifact names. Release assembly injects the
semantic version and source SHA, embeds the identical profile in the standalone package, publishes
both SHA-256 sidecars, and creates GitHub build-provenance attestations for the Apple artifacts.

The long-term runtime boundary remains open until corpus coverage plus fixed-device performance,
memory, binary-size, accessibility, and maintenance measurements justify stability. The detailed
decision, verification commands, stability gate, and future major-version migration policy live in
`RC_NATIVE_UIKIT_DISTRIBUTION.md`.

## Corpus and verification ladder

Every work package advances through the same ladder:

1. pure Kotlin tests for decode, linking, evaluation, and exported bridge failures;
2. pure Swift tests for value mapping, measurement, placement, and diagnostics;
3. focused UIKit/Core Graphics tests for hierarchy, semantics, and pixels;
4. simulator A/B rendering against CMP for the selected fixture shard;
5. packaged-artifact consumer build, not only a checkout build;
6. device measurements when timing, memory, Metal/Core Animation, or accessibility is involved.

The initial corpus should grow outward from the Morning Run Title Card: static typography cards,
nested layout cards, shape/paint cards, images, then one fixture per interaction and animation
family. A fixture enters a declared profile only when it has an expected diagnostic set and an
owned reason for every tolerated visual difference.

## Immediate next sequence

Land the stacked packages and the packaged-simulator evidence slice in order. Then run the same
versioned report on a fixed physical device, add VoiceOver/Switch Control UI automation, and verify
one published standalone archive from an external consumer. Keep
`rc-native-uikit-core-v1` unchanged while collecting that evidence. Any new operation family starts
a new reviewed profile diff with a fixture and owned diagnostics before it becomes a release claim.

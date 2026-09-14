# Native UIKit player feature plan

Status: active implementation sequence for the experimental `RcNativePlayerUIKit` product.

This plan turns the architecture POC into a useful native player without weakening the existing
CMP player's compatibility contract. Work is ordered by dependency and by how much real document
coverage it unlocks. Each numbered slice is intended to be independently reviewable and to leave
the player in a releasable state.

## Baseline

The current POC decodes a time-zero snapshot through Kotlin and renders it through a Swift-owned
UIKit hierarchy. It has approximate Box, Row, and Column layout, native `UILabel` text, semantic
`UIButton` overlays, a useful Core Graphics subset, explicit compatibility diagnostics, the Morning
Run Title Card regression, and a downloadable experimental package.

It does not yet retain a running document, dispatch actions, update named values, animate, load
images or fonts, or claim a complete Remote Compose profile. Those are feature gaps, not implicit
fallbacks: unsupported behavior must remain visible throughout this plan.

## Progress

| Package | Status | Result |
|---|---|---|
| 1 — Static compatibility profile | Implemented | Structured issues, strict/compatible policy, malformed failures, and StateLayout reachability tests |
| 2 — Static layout geometry | Core implemented | Pure Swift geometry, constraints, proportional weights, visibility, offsets, z-order, RTL, and root scaling |
| 3 — Core Graphics static drawing | Next | Ordered paths, gradients, bitmaps, stroke detail, and blend diagnostics |
| 4–11 | Planned | Ordered below |

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

Implement paths, quadratic/cubic segments, gradients, bitmap draw modes, complete stroke caps/joins,
blend modes that Core Graphics can represent, and layer/save/restore behavior. Keep canvas text in the
same ordered command stream when promotion to a native text element would alter compositing.

Acceptance:

- each operation family has a small deterministic image fixture;
- graphics-state nesting and draw order have regression coverage;
- unsupported shader or blend behavior produces structured diagnostics;
- no command indexes unvalidated operand data.

### 4. Make text production-capable for the static profile

Add paragraph width, wrapping, line limits, baseline and alignment behavior, attributed spans,
bidirectional text, locale, font weight/style, and deterministic fallback. Use native text views for
conceptual layout text and Core Text for ordered canvas glyph rendering. Define which metrics are
expected to differ from CMP instead of hiding them with broad pixel tolerances.

Acceptance:

- Latin, CJK, Arabic/RTL, emoji, multiline, truncation, and accessibility-size fixtures exist;
- label intrinsic size feeds layout measurement correctly;
- canvas text respects transforms, clipping, and interleaving;
- font fallback and unavailable-font diagnostics are deterministic offline.

### 5. Add images and bounded resource loading

Introduce a host-supplied resource resolver with cancellation, byte limits, decode-size limits, and
cache policy. Support inline and referenced bitmaps first, then downloadable fonts if the security
and ownership model remains acceptable. A document must never trigger arbitrary network access by
default.

Acceptance:

- resource loading is opt-in and injectable;
- replacement or deallocation cancels outstanding work;
- oversized, corrupt, missing, and slow resources have typed errors/diagnostics;
- cache keys include content identity and rendering-relevant parameters.

### 6. Replace snapshots with a retained session

Create a narrow renderer-neutral session in Kotlin that owns `RcPlayerState`, exposes immutable
frame deltas, and schedules no UIKit work itself. Swift applies those deltas on the main actor while
retaining stable component views by id. Full snapshot rebuild remains available as a correctness
fallback during development.

Acceptance:

- loading and decoding can run away from the main actor;
- component identity survives value-only updates;
- document replacement is atomic and cancels prior work;
- lifecycle tests cover repeated load, failure, backgrounding, and deallocation.

### 7. Named values, actions, and input

Expose typed named-value updates and event callbacks that mirror the supported CMP host contract.
Route UIKit touch coordinates into the session, implement click/action dispatch, and preserve event
ordering. Start with tap/click; add drag, scroll, and touch expressions only after coordinate-space
tests are exact.

Acceptance:

- host updates coalesce without dropping the final value;
- clicks emit the expected action once and on the main actor;
- hit testing honors transforms, clipping, enabled state, and z-order;
- replacing a document cannot deliver stale callbacks from the old session.

### 8. Expand semantic UIKit components and accessibility

Map explicit roles/states to `UIButton`, `UISwitch`, progress views, image views, and focused custom
`UIControl` subclasses where behavior aligns. Do not infer controls from appearance. Build a stable
accessibility tree with labels, values, traits, ordering, enabled state, and actions.

Acceptance:

- semantic elements are inspectable as the intended UIKit types;
- VoiceOver order follows semantic/layout order, not incidental canvas order;
- Dynamic Type, high contrast, Reduce Motion, and Switch Control behavior is tested;
- visual and semantic hit targets remain synchronized after updates.

### 9. Add deterministic time and animation

Give the retained session an injectable monotonic clock and wake-up contract. Use `CADisplayLink`
only while a frame is due; pause when offscreen/backgrounded and resume without accumulating time
drift. Add layout and graphics animation families incrementally.

Acceptance:

- clock-driven tests advance time without sleeping;
- static documents schedule no display-link work;
- frame pacing, pause/resume, and reduced-motion policy are measured on device;
- updates invalidate only affected views/canvases.

### 10. Harden performance and untrusted-input behavior

Enforce limits for operation count, nesting, macro expansion, path complexity, text length, bitmap
bytes, offscreen dimensions, and per-frame work. Profile decode, first frame, updates, view count,
allocations, and memory on a fixed corpus. Prefer explicit refusal over UIKit/Core Graphics calls
with non-finite or extreme geometry.

Acceptance:

- malformed and adversarial corpora cannot crash or hang the process;
- limits produce typed, diagnosable failures;
- performance budgets run in CI where stable and in scheduled device jobs otherwise;
- no resource, timer, task, or display link survives player deallocation.

### 11. Stabilize distribution and API only after evidence

Keep the downloadable package and SwiftPM product experimental through M4. At M5, review whether
the Kotlin bridge should become stable SPI, move behind a C interface, or be replaced by a Swift
runtime. Decide minimum OS/architecture support, semantic versioning guarantees, module naming,
and whether the standalone archive remains useful beside normal SwiftPM distribution.

Acceptance:

- public API has a written compatibility policy and migration story;
- the operation/profile claim is machine-readable and release-versioned;
- release artifacts have consumer builds, checksums, and provenance;
- the support decision is based on coverage, performance, binary size, and maintenance data.

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

The next three changes should be packages 1, 2, and 3 in that order. Structured compatibility makes
new coverage measurable; complete geometry prevents drawing work from being judged in the wrong
frames; only then does broadening the primitive set reliably unlock real documents. Text and images
follow as distinct reviewable tracks, while retained state begins after the static profile is
stable enough to serve as its correctness oracle.

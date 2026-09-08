# Full Remote Compose operation coverage on iOS and macOS

An implementation and conformance plan for completing the Remote Compose operation set in the
Compose Multiplatform player on Apple's platforms. This is a companion to
[`RC_CMP_WASM_PLAYER.md`](RC_CMP_WASM_PLAYER.md): that document defines the shared player
architecture and the original operation clusters; this one defines the remaining work, Apple test
lanes, cross-player comparison, and upstream-reporting loop.

## Outcome and scope

The outcome is not merely that every known opcode decodes. A document using any operation that is
executable in the authoritative AndroidX Java profile must:

1. decode, validate, and link without losing wire information;
2. execute the same state, timing, layout, action, and drawing semantics in shared KMP code;
3. render and interact on both supported Apple surfaces;
4. have operation-isolating conformance evidence against the AndroidX View and embedded players;
5. report any remaining difference as an owned CMP defect, a known backend difference, or a linked
   upstream defect.

“Full” is deliberately tied to executable upstream behavior rather than to every integer in
`Operations.java`. Reserved extension markers and constants for which AndroidX has no readable,
executable operation are inventory rows, not behavior to invent. They remain explicitly classified
and are re-audited whenever the AndroidX baseline advances.

The Apple targets in this plan are:

| Surface | Repository target | Host used for conformance |
| --- | --- | --- |
| iPhone/iPad device | `iosArm64` | `RcComposeViewController` in the XCFramework |
| Apple-silicon iOS Simulator | `iosSimulatorArm64` | A small XCTest/simulator render host |
| macOS desktop | the published `jvm()` artifact | Compose Desktop and `ImageComposeScene` on a macOS runner |

“macOS” therefore means the existing Compose Desktop/JVM player running on macOS. This repository
does not currently publish a Kotlin/Native `macosArm64` player, and adding one is not required for
operation completeness. If a native AppKit framework becomes a product requirement, it needs a
separate distribution and host plan; the protocol, runtime, fixtures, and most renderer work here
would still be reusable.

Intel iOS simulators are also out of scope because Compose Multiplatform 1.11 does not publish the
required `iosX64` artifacts. Device arm64 and Apple-silicon simulator arm64 remain required.

## Starting point

The source of truth is
[`rc-operations.manifest`](../../rc-player/protocol/src/main/rc-operations.manifest), generated into
`RcOperationInventory`. At the time this plan was written it contains all 172 public alpha16
entries:

| Status | Rows | Meaning |
| --- | ---: | --- |
| `implemented` | 140 | Codec and shared CMP semantics exist |
| `implemented_upstream_unavailable` | 1 | `DrawTextOnCircle` works in CMP but the Java reader profile cannot read it |
| `unsupported` | 22 | Known operation and planned implementation work |
| `unavailable` | 4 | AndroidX exposes a constant but no usable authoritative operation |
| `reserved` | 5 | Extension markers, not operations |

There are no `parse_only` rows. That is useful, but it is not yet proof of Apple completeness:

- a single manifest status currently represents the shared implementation, while an operation can
  have an iOS-only or desktop-only backend gap;
- one opcode can contain many modes, properties, or paint commands, some of which
  `composeSupportReport` may still reject;
- nearly all render assertions use the JVM headless renderer; `iosTest` currently proves the UIKit
  host and event bridge, not the pixels produced by an iOS simulator;
- the broad catalog A/B establishes useful real-document parity, but does not guarantee that every
  operation and boundary value appeared in the corpus.

The remaining 22 unsupported rows fall into six deliverable groups:

| Group | Operations |
| --- | --- |
| Structural compatibility | `ComponentStart`, `Skip` |
| Referenced operations and macros | `ReferencedOperations`, `MacroForEach`, `IncludeReferencedOperations`, `MacroDefine`, `MacroCall`, `MacroArgument`, `MacroBlock` |
| Shaders and offscreen drawing | `ShaderData`, `DrawToBitmap` |
| Bitmap fonts | `BitmapFontData`, `BitmapTextMeasure`, `DrawBitmapFontTextRun`, `DrawBitmapFontTextRunOnPath`, `DrawBitmapTextAnchored` |
| Particles | `ParticleDefine`, `ParticleLoop`, `ParticleCompare` |
| Sound | `SoundData`, `SoundExpression`, `PlaySound` |

`LoadBitmap`, `MatrixSet`, `ParticleProcess`, and `Update` remain unavailable until an upstream
reader and executable contract exist. `DrawTextOnCircle` stays in the CMP Apple profile but out of
the AndroidX Java profile. Neither exception may be hidden to make a headline coverage percentage
look better.

## Design input: recent AndroidX StateLayout and FitBox animation work

The plan also uses two changes recently landed in the official
[`androidx/androidx`](https://github.com/androidx/androidx) GitHub mirror as behavioral references:

- [`3d97be4c888b`](https://github.com/androidx/androidx/commit/3d97be4c888ba198e39b311eeed348f5a5faa0ef)
  adds StateLayout branch animation and shared-element transitions;
- [`6fb763d3fe4f`](https://github.com/androidx/androidx/commit/6fb763d3fe4fce250c9d86ea715c08ca8c22aab8)
  applies the same transition model to FitBox after selecting an alternative through intrinsic
  measurement.

This review is about the approach and observable contract, not copying the Android implementation.
The important ideas are:

1. **The layout switch owns branch lifetime.** StateLayout keeps the outgoing and incoming branches
   alive for the transition and cross-fades them according to the layout's motion duration/easing.
   The state index is clamped, empty layouts remain valid, and a zero-duration specification is an
   immediate switch.
2. **Animation ids define identity below the switch.** Descendant components with the same non-zero
   animation id in the old and new branches are treated as one shared element. Their bounds are
   remeasured throughout the transition, rather than scaling a snapshot, so contents can reflow at
   intermediate sizes. The transition scopes are propagated down the tree because matching
   components can be nested arbitrarily deeply.
3. **FitBox selection and animation are separate phases.** A probe composition asks all alternatives
   for intrinsic sizes, selects the first one that fits the current constraints, then a content
   composition renders and transitions only the selected alternative. Probe nodes have semantics
   removed so invisible candidates do not leak into accessibility. The chosen index is passed
   directly into the content subcomposition instead of mutating Compose state during measurement.
4. **Tests assert geometry through time.** The AndroidX StateLayout change covers size and position
   interpolation, several simultaneous shared elements, and instant transitions. The FitBox change
   adds constraint-driven alternative selection and verifies a shared element at the start,
   midpoint, and end of the resize.

The current CMP renderer already follows this structure through `RcAnimatedAlternatives`,
`rcSharedElementModifier`, and the two-phase FitBox `SubcomposeLayout`. Its existing JVM tests cover
StateLayout cross-fade/position morphing and FitBox cross-fade. Treat that as a starting point, not
closure: add the upstream cases CMP does not yet pin explicitly—shared-element size interpolation in
both layouts, several concurrent identities, zero-duration motion, out-of-range and empty states,
constraint oscillation, no-fit fallback, probe semantics, interruption/re-targeting, and nested
switchers. Run the same timed assertions on macOS and iOS, and against the pinned snapshot embedded
player which contains these changes; the older vendored embedded player remains useful for showing
the before behavior but is not the oracle for this feature.

This also sets an implementation rule for later operation clusters: reuse one shared
identity/transition mechanism for any operation that switches alternatives. Do not build a second
animation engine into macros, particles, or future layout managers, and do not let a probe or
offscreen pass publish geometry, semantics, actions, or other externally observable state.

## 1. Make coverage target-specific and mechanically complete

Extend the generated inventory into a conformance matrix instead of maintaining prose checklists.
Keep the existing manifest as the opcode authority, then generate or validate a second checked-in
record with one row per operation and these dimensions:

- codec: decode, encode, negative/boundary coverage;
- linker: nesting, placement, id/reference validation;
- runtime: state, expressions, time, invalidation, and actions;
- `cmp-macos`: supported, blocked, or supported with a declared tolerance;
- `cmp-ios`: the same three states;
- reference coverage: View, released embedded, snapshot embedded, and vendored JVM embedded;
- fixture ids, upstream source revision, and issue/report links.

Variant coverage belongs under the operation row. Examples include every paint-bundle command,
bitmap encoding and scale mode, text overflow and line-break mode, layout alignment, graphics-layer
attribute, animation branch, and action subtype. An opcode cannot be marked complete because its
simplest form rendered.

Add validation tests with these invariants:

- every non-reserved manifest row has exactly one conformance entry;
- every executable AndroidX operation is either supported on both Apple targets or has a linked,
  reasoned target exception;
- every supported row has at least one fixture and all applicable test-layer results;
- no `parse_only`, temporary exclusion, or allowed diff exists without an owner and issue URL;
- advancing the AndroidX registry cannot silently retain profile names with the old release suffix.

Publish the generated Markdown/JSON matrix as a CI artifact and keep a concise checked-in summary.
The JSON is the machine gate; Markdown is a view of the same data, never a separately edited truth.

Exit criterion: adding, removing, or reclassifying an opcode causes an explicit reviewable matrix
diff, and `CMP_IOS_*` plus a new `CMP_DESKTOP_*` profile describe the real target behavior.

## 2. Build one fixture corpus, authored independently of CMP

Create a small fixture generator in `rc-player/compat-tests` using the real AndroidX writer wherever
its public API can express the operation. Each fixture directory contains:

- the `.rc` bytes;
- manifest metadata: id, operation and variants covered, dimensions, density, layout direction,
  theme, locale, font/image assets, and required host capabilities;
- deterministic clock and animation frame times;
- expected decoded structure and observable state/action events;
- per-lane pixel comparison policy: exact, perceptual with a fixed tolerance, or unscorable with a
  precise reason.

Hand-authored bytes are limited to malformed inputs, exact float/NaN bit patterns, and operations
the public writer cannot emit. Before becoming a positive conformance fixture, those bytes must be
accepted by an authoritative AndroidX reader or carry the upstream-unavailable classification.
Never use the CMP encoder to generate the only expected bytes for the CMP decoder.

Every supported operation gets minimum, representative, boundary, and invalid fixtures. Stateful
operations also get a short trace of inputs and expected states; interactions get pointer/keyboard
scripts and expected host events. Complex operations should have focused fixtures as well as one
composition fixture proving that they work in a realistic tree.

Pin all sources of visual nondeterminism: fonts and fallback order, density, pixel bounds, locale,
layout direction, theme, image bytes, wall clock, animation/play time, random seed, and capture
frame. A fixture that cannot be made deterministic is reported separately and cannot satisfy an
operation's conformance requirement.

Exit criterion: the manifest-to-fixture validator can prove that every supported operation and
declared variant is exercised without inspecting test source by hand.

## 3. Add first-class macOS and iOS render lanes

### macOS desktop lane

Generalize `RcCmpRenderHarness` into a reusable manifest-driven runner and execute it on the macOS
CI image, not only on Linux. It should continue to use the published JVM artifact path and
`ImageComposeScene`, write a PNG plus structured support/errors/events for every fixture, and fail
if an expected result is absent or stale.

Running the same JVM bytecode on Linux and macOS is intentional. It separates interpreter defects
from host font, Skia, input, and graphics-backend differences and makes macOS an observed platform
rather than an inference from a Linux JVM test.

### iOS lane

Add a minimal iOS test application which consumes the assembled `RcComposePlayer.xcframework`,
mounts `RcComposeViewController`, and accepts the same fixture manifest as every other lane. XCTest
drives it in an Apple-silicon simulator and records:

- a screenshot at the fixture's exact logical bounds and pinned frame time;
- support-report and decode/render errors;
- emitted host, named, haptic, diagnostic, and value-change events;
- accessibility tree assertions for semantic operations;
- lifecycle results for load, dispose, background/foreground, and repeated playback.

The host must load the same committed font and image bytes as the desktop/reference lanes. It must
not silently fall back to a system font or remote URL. Screenshot capture waits for a player-owned
“frame presented and stable” signal rather than an arbitrary delay. Animated fixtures use an
injectable clock or explicit frame advancement.

Keep fast codec, runtime, and support-report tests in `commonTest`; keep JVM Compose tests for quick
renderer feedback. The simulator lane is the platform proof, not a replacement for those tests.
Run a small smoke slice on every PR and shard the complete operation corpus across the macOS job or
a scheduled workflow if runtime is too high. Device execution can remain periodic, but both klibs
and the release XCFramework continue to build on every PR.

Exit criterion: a pull request changing renderer behavior produces macOS and iOS results for the
same bytes, and a missing iOS screenshot is a test failure rather than an implicit pass.

## 4. Compare against several players, not a single oracle

Render every conformance fixture through as many applicable lanes as possible:

| Lane | Role |
| --- | --- |
| AndroidX View player | Original Android renderer and wire/runtime reference |
| Released AndroidX embedded player | Consumer-visible upstream behavior |
| Pinned AndroidX snapshot embedded player | Early warning for behavior that has landed upstream |
| Vendored AndroidX embedded player | Reproducible, locally diagnosed baseline with documented patches |
| Vendored embedded JVM cut | Platform-neutral AndroidX interpreter against Compose Desktop |
| CMP desktop on macOS | Product result on macOS |
| CMP iOS simulator | Product result on iOS |
| CMP desktop on Linux and CMP Wasm | Triangulation for shared-code versus backend failures |

The same bytes, dimensions, density, theme, assets, clock, and input trace must reach every lane. A
lane that cannot express a host capability reports `not-applicable(reason)`; it must not emit a
blank image that participates in scoring.

Compare at three levels:

1. Exact decoded operations, linked trees, state transitions, and emitted events. These should not
   need pixel tolerances.
2. Exact pixels for controlled primitives, bundled bitmap fonts, clipping, transforms, and other
   fixtures whose raster path is intentionally identical.
3. Perceptual pixel diffs for platform shaping and anti-aliasing. Tolerances are fixed per fixture
   and a pull request is judged on the delta from its merge-base result, not only on an absolute
   threshold.

Store the two source images, a highlighted diff, metrics, support reports, and environment metadata
for each comparison. CI uploads the complete bundle and posts a concise PR summary listing new
failures, newly unsupported rows, and the largest changed diffs. Accepted differences are
checked-in classifications with evidence and an issue; they are not a golden image updated by the
same change that caused the difference.

No one lane is automatically correct. In particular, the existing 475-document sweep proves cases
where the View lane is the outlier and the CMP plus embedded players agree. Triage uses a majority
only to locate the likely layer, then checks the AndroidX writer/reader and implementation source to
establish the contract.

Classify every new difference as one of:

- wire/codec;
- linker or runtime semantics;
- CMP shared renderer;
- Apple host/backend;
- AndroidX View defect or harness limitation;
- AndroidX embedded defect;
- intentional platform rasterization difference;
- nondeterministic or insufficient fixture.

Exit criterion: every changed result has both an artifact and a classification; “pixels differ” is
never the final diagnosis.

## 5. Implement the remaining operations in dependency order

Land work in small operation clusters. Each cluster includes its fixtures, shared implementation,
both Apple results, reference comparisons, matrix update, and any upstream report in the same pull
request.

### Phase A — structural compatibility and macros

Implement `ComponentStart` and `Skip`, then the referenced-operation and macro family. This work
belongs first because it can expand or suppress the operation stream used by every later feature.
Keep stored definitions immutable, validate argument counts and types, preserve wire order, and
bound recursion, expansion depth, and total inflated operations. Test id remapping, nested calls,
empty blocks, missing definitions, forward references, large loops, and malicious expansion.

Exit: macro-expanded documents produce the same linked structure and runtime state as the AndroidX
players before pixels are compared.

### Phase B — offscreen drawing and shaders

Introduce a small renderer backend seam for render targets and shader creation; keep resource
ownership and operation scheduling in common code. Implement `DrawToBitmap` with explicit target
lifetime, nesting rules, cache invalidation, dimensions, and allocation limits. Implement the
portable shader forms supported by Skia on both desktop and iOS, including local-matrix handling,
paint references, compilation errors, and a deterministic unsupported result for any dialect the
backend cannot compile.

Exit: offscreen output can feed later bitmap draws on both Apple targets, shader fixtures match the
reference within declared GPU/CPU tolerance, and malformed shaders cannot crash or exhaust the
host.

### Phase C — bitmap fonts

Implement bitmap-font data, measurement, straight/path/anchored drawing, kerning/advance behavior,
alignment, clipping, and missing-glyph fallback. Prefer shared geometry and atlas lookup; isolate
only image decoding and final raster work behind the backend seam.

Exit: the five bitmap-font rows pass exact measurement/state tests and reviewed Apple/reference
renders, including non-ASCII and missing-glyph cases supported by the format.

### Phase D — particles

Implement particle definition, comparison, and loop semantics with an injected deterministic
random source and clock. Re-audit `ParticleProcess`: if the selected upstream baseline now has a
readable executable contract, move it from unavailable into this phase; otherwise continue to
exclude it and link the source audit from the matrix. Bound particle count, lifetime, nested loops,
and per-frame work.

Exit: identical seeds and frame times produce repeatable state traces; Apple renders are compared at
named frames rather than after wall-clock sleeps.

### Phase E — sound

Split sound into common scheduling/expression semantics and host playback. Add an opt-in audio host
interface so document decode or composition can never play sound implicitly. Implement an AVFoundation
backend for iOS and a documented desktop backend for macOS; define interruption, mute, unavailable
device, preload, disposal, and repeated-trigger behavior. Reference comparison asserts schedules and
events rather than audio waveforms unless the reference player exposes deterministic PCM output.

Exit: all three sound rows have deterministic common tests, host integration tests, and graceful
no-audio behavior; CI never needs a physical audio device.

### Phase F — exhaustive implemented-operation audit

After the unsupported count reaches zero, audit every already-implemented row and every
`composeSupportReport` branch. Close missing variants, invalid placements, backend-specific
attributes, and interaction/accessibility gaps discovered by the generated matrix. Then run the
operation corpus and the full real-document catalog across all lanes.

Exit: zero unexplained target exclusions, zero unowned diffs, and no executable upstream operation
outside the two Apple profiles.

## 6. Track upstream continuously

Treat an AndroidX upgrade as a reviewed compatibility change, not a dependency refresh:

1. Pin the released coordinates and the androidx-main snapshot build id used for comparison.
2. Diff `Operations.java`, registered reader profiles, wire classes, writer APIs, and executable
   implementations against the previous pin.
3. Regenerate the inventory and profiles; rename their release suffix when the registry generation
   changes.
4. Generate fixtures for new operations and variants before advertising support.
5. Run the complete comparison suite against both the previous released baseline and the new
   release/snapshot to separate CMP regressions from upstream behavior changes.
6. Refresh the vendored player only after its `PROVENANCE.md` diff and local patch table have been
   reviewed.

When a difference is demonstrated upstream, open a minimal report in the appropriate AndroidX/AOSP
tracker and a local `rc-players` issue that owns follow-up. A useful report includes:

- exact release coordinate or androidx-main commit/build id;
- minimal `.rc` fixture and, where possible, its writer source;
- View, embedded, and CMP results plus the pixel/state diff;
- device/emulator, API level, density, font/assets, theme, clock, and render backend;
- expected behavior justified from the writer, reader, or operation implementation;
- whether the released and snapshot players reproduce it.

Link the upstream report, local issue, fixture, and accepted-difference classification in the
conformance row. When upstream fixes it, first add a snapshot comparison that proves the fix, then
remove the local workaround when the released dependency contains it. The vendored patch table must
therefore shrink or carry a current reason at every refresh.

Run a scheduled snapshot canary even when no dependency bump is planned. It should report new
opcodes, reader-profile changes, wire changes, fixed accepted diffs, and newly introduced diffs;
notification is required, automatic baseline mutation is not.

## Delivery and pull-request gates

Each operation-cluster pull request must include:

- manifest/conformance rows and independently authored fixtures;
- codec, negative, linker, runtime, and Compose tests as applicable;
- macOS and iOS render or interaction evidence;
- View plus embedded comparisons, with the JVM embedded lane when applicable;
- support-profile changes and ABI dumps when public API changes;
- before/after embedded images for any drawing change, using the repository's committed-render
  workflow;
- local and upstream issue links for known differences.

The normal fast checks remain `commonTest` and `jvmTest`. The macOS CI job additionally runs the
macOS corpus shard, iOS simulator smoke/corpus shards, iOS compilation, ABI validation, release
XCFramework assembly, and Swift sample type-check. A scheduled full sweep runs every operation
fixture and the real-document catalog against released and snapshot reference players.

A parity job should block on missing outputs, crashes, support regressions, state/event mismatches,
and exact-pixel regressions. Perceptual raster changes initially report a merge-base delta with
artifacts; a stable, measured threshold may become blocking after the lane demonstrates low
self-diff noise. Animated or otherwise nondeterministic fixtures never participate in a blocking
pixel score until determinism is fixed.

## Definition of done

The Apple operation project is complete when all of the following are true:

- every executable operation in the pinned AndroidX registry is in both Apple profiles;
- reserved and upstream-unavailable rows are explicitly sourced and monitored;
- every operation and legal variant has independent fixtures and all applicable conformance layers;
- the same corpus runs through CMP macOS, CMP iOS, AndroidX View, and at least one AndroidX embedded
  lane, with the other reference lanes used where applicable;
- macOS and iOS results come from those real hosts, not only from common-code compilation;
- every accepted difference has evidence, an owner, and a local or upstream issue;
- the released and pinned snapshot baselines are recorded and reproducible;
- the XCFramework and JVM artifact expose the same supported document semantics, except for
  explicitly tested host capabilities such as audio and platform rasterization;
- no conformance row is parse-only, temporarily excluded, missing evidence, or silently tolerated.

That definition makes “full operation set” a maintained compatibility claim. A future AndroidX
release can change the count, but it cannot change the claim without producing a visible inventory,
fixture, profile, and comparison diff.

# RemoteCompose Conformance Test Suite — Coverage Gaps & Improvement Areas

This document outlines the gaps in test coverage across the **Android reference player** (`androidx.compose.remote.core.*`) when evaluated against the conformance test suite.

---

## 1. Executive Summary & Objective

- **The Android codebase is the normative reference implementation.** Third-party players (TypeScript, Web, Flutter, iOS, Rust, etc.) must match Android's runtime semantics.
- **The conformance test suite (`tests/` and `gold/`) defines the specification standard.** 
- **The metric of success:** Measuring code and branch coverage of the Android reference engine during conformance test replay ([`ConformanceGoldGeneratorTest.replayGoldCorpus`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-creation-core/src/test/java/androidx/compose/remote/creation/json/ConformanceGoldGeneratorTest.java)) verifies whether the conformance corpus is truly representative of the reference implementation.

### Current Baseline (Android Reference Engine JaCoCo Audit)

- **Instruction Coverage**: **66.18%** (73,348 / 110,835 instructions)
- **Line Coverage**: **67.43%** (16,882 / 25,036 lines)
- **Branch Coverage**: **62.27%** (5,897 / 9,470 branches)
- **Target**: **>80% across all subsystems (ideally >90%)**.

---

## 2. Subsystem Coverage Status Overview

Measured across 361 gold documents replayed through Android's [`CoreDocument`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/CoreDocument.java), [`LayoutManager`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/layout/managers/LayoutManager.java), and [`RemoteContext`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/RemoteContext.java):

| Subsystem | Instruction Coverage | Line Coverage | Branch Coverage | Priority |
| :--- | :---: | :---: | :---: | :--- |
| **📐 Layout Conformance** | **92.2%** (32,336 / 35,078) | **92.4%** (7,815 / 8,454) | **82.9%** (3,191 / 3,849) | ✅ **Target Achieved (>80%)** |
| **🎬 AnimationSpec** | **94.6%** (1,675 / 1,770) | **94.0%** (410 / 436) | **73.4%** (58 / 79) | 🟢 Minor branch gap |
| **⏱️ Clock & Time** | **72.6%** (631 / 869) | **46.5%** (101 / 217) | **43.1%** (31 / 72) | 🟡 Moderate |
| **🧵 Loom Macro Engine** | **69.0%** (1,301 / 1,886) | **69.8%** (331 / 474) | **61.3%** (87 / 142) | 🟡 Moderate |
| **⚡ Expression Engine** | **63.2%** (9,687 / 15,325) | **63.5%** (1,805 / 2,844) | **56.2%** (720 / 1,282) | 🟡 Moderate |
| **✨ Particle System** | **61.4%** (1,631 / 2,657) | **61.5%** (343 / 558) | **57.1%** (202 / 354) | 🟡 Moderate |
| **🧮 Matrix Math** | **60.4%** (2,376 / 3,937) | **61.2%** (548 / 895) | **48.1%** (130 / 270) | 🟡 Moderate |
| **〰️ Path Operations** | **58.6%** (2,553 / 4,358) | **57.2%** (506 / 884) | **46.7%** (170 / 364) | 🔴 High Priority |
| **🔀 Conditionals** | **58.2%** (408 / 701) | **58.0%** (91 / 163) | **62.0%** (49 / 79) | 🟡 Moderate |
| **♿ Semantics & A11y** | **54.1%** (326 / 602) | **43.0%** (64 / 149) | **14.3%** (4 / 28) | 🔴 High Priority (Low Branch) |
| **👆 Interactivity** | **53.1%** (1,482 / 2,790) | **56.6%** (351 / 620) | **50.4%** (131 / 260) | 🔴 High Priority |
| **⏰ Scheduling** | **52.5%** (53 / 101) | **54.5%** (12 / 22) | **50.0%** (2 / 4) | 🟡 Moderate |
| **💾 Data Operations** | **51.8%** (724 / 1,399) | **52.6%** (162 / 308) | **52.6%** (41 / 78) | 🟡 Moderate |
| **🔤 Text Operations** | **50.9%** (1,706 / 3,349) | **51.6%** (458 / 887) | **41.6%** (82 / 197) | 🔴 High Priority |
| **🔮 Shaders & AGSL** | **50.4%** (399 / 792) | **50.9%** (88 / 173) | **53.1%** (51 / 96) | 🔴 High Priority |
| **📡 Wire Protocol** | **48.5%** (3,845 / 7,932) | **47.2%** (801 / 1,697) | **53.0%** (143 / 270) | 🔴 High Priority |
| **🎭 Color Theme** | **38.3%** (808 / 2,112) | **43.7%** (207 / 474) | **29.3%** (39 / 133) | 🔴 High Priority |
| **🎨 2D Canvas** | **38.2%** (4,935 / 12,910) | **40.5%** (1,172 / 2,893) | **28.6%** (238 / 833) | 🔴 Critical Gap |

---

## 3. Detailed Subsystem Gaps & Actionable Test Scenarios

### A. 2D Canvas & Paint System (`canvas` — 38.2% Inst, 28.6% Branch)
The canvas subsystem has the highest volume of unreached code (7,975 missed instructions in Android).

- **[`PaintBundle`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/paint/PaintBundle.java) (3,370 missed instructions, 312 missed branches)**:
  - **Gap**: `PaintBundle` manages 30+ paint attributes. Existing tests primarily test solid color fills.
  - **Needed Tests**:
    - Stroke cap styles (`Cap.BUTT`, `Cap.ROUND`, `Cap.SQUARE`).
    - Stroke join styles (`Join.MITER`, `Join.ROUND`, `Join.BEVEL`) and miter limit clamping.
    - Path effects (`PathEffect.dash`, corner path rounding).
    - Color blend modes (SrcOver, Multiply, Screen, Overlay, Darken, Lighten).
    - Alpha channel modulation via dynamic float variables.
- **[`DrawBitmapTextAnchored`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/DrawBitmapTextAnchored.java) (Opcode 133 — 0% Coverage, 834 missed inst)**:
  - **Gap**: Completely unexercised by conformance tests.
  - **Needed Tests**: Anchored glyph positioning with horizontal/vertical alignment flags, non-zero `panX`/`panY`, and dynamic coordinate offsets.
- **[`DrawBitmapFontTextOnPath`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/DrawBitmapFontTextOnPath.java) (Opcode 132 — 0% Coverage, 585 missed inst)**:
  - **Gap**: Completely unexercised by conformance tests.
  - **Needed Tests**: Mapping bitmap font glyph runs along complex Bezier paths with positive and negative `hOffset` and `vOffset`.
- **[`DrawBitmapFontText`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/DrawBitmapFontText.java) (Opcode 131 — 23.78% Coverage)**:
  - **Needed Tests**: Multiline bitmap text formatting, line-break advancing, and kerning offset tables.

---

### B. Color Theme & Palettes (`colortheme` — 38.3% Inst, 29.3% Branch)

- **[`ColorExpression`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/ColorExpression.java) (Opcode 83 — 34.73% Coverage, 874 missed inst, 71 missed br)**:
  - **Gap**: Only basic theme color lookups are exercised.
  - **Needed Tests**:
    - Color space transforms (HSV to RGB, RGB to HSV).
    - Component extraction (extract red, green, blue, alpha as float variables).
    - Color blending calculations (linear interpolate between two dynamic colors, multiply/screen blending).
    - Luminance computation and dynamic contrast inversion (light/dark adaptive text colors).

---

### C. Wire Protocol & Buffer Framing (`wire` — 48.2% Inst, 53.0% Branch)

- **[`RecordingRemoteComposeBuffer`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/RecordingRemoteComposeBuffer.java) (14.15% Coverage, 1,990 missed inst)** & **[`RemoteComposeBuffer`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/RemoteComposeBuffer.java)**:
  - **Gap**: Binary serialization, stream compaction, index rewinds, and error handling.
  - **Needed Tests**:
    - Large document stream partitioning and buffer growth reallocation.
    - Profile bitmask handshakes (`PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL | PROFILE_DEPRECATED`).
    - Malformed payload boundary checks (reading beyond available bytes, invalid opcodes).
    - `Header` metadata extensions (custom device density, scaling factors, document tags).

---

### D. Expression Engine (`expressions` — 63.2% Inst, 56.2% Branch)

- **Android Wire Limit Adherence ([`Limits.MAX_EXPRESSION_SIZE = 32`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/Limits.java#L60))**:
  - **Finding**: Android limits any single `FloatExpression` to 32 elements. Tests exceeding 32 elements throw `"Float expression too long"` on Android.
  - **Action**: Split comprehensive math operator validation into chained expressions of $\le 32$ elements.
- **[`MonotonicCurveFit`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/utilities/easing/MonotonicCurveFit.java) (23.87% Coverage, 1,180 missed inst, 90 missed br)**:
  - **Needed Tests**: Spline interpolation with non-uniform time knots, slope clamping, and boundary extrapolation.
- **[`VelocityEasing`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/utilities/touch/VelocityEasing.java) (21.46% Coverage, 666 missed inst, 48 missed br)**:
  - **Needed Tests**: Fling velocity decay curves, deceleration thresholds, and settling time calculation.
- **Audio & Tone Synthesizer ([`SoundExpression`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/SoundExpression.java) / [`ToneSynthesizer`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/utilities/ToneSynthesizer.java) — 0% Coverage)**:
  - **Needed Tests**: Tone generation parameters, frequency modulation, envelope attack/decay curves.

---

### E. Interactivity & Touch Gestures (`interactivity` — 53.1% Inst, 50.4% Branch)

- **Recent Additions (Audited from `player-view-demos`)**:
  - **Reference Replay Interaction Support**: Replay harness in [`ConformanceGoldGeneratorTest.java`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-creation-core/src/test/java/androidx/compose/remote/creation/json/ConformanceGoldGeneratorTest.java) now actively replays all `timeline` and `parameters.interactions` steps (`click`, `touch_down`, `touch_up`, `touch_drag`, `longpress`, `doubleclick`, `advance_time`) against Android's `CoreDocument`, measuring and painting after each event.
  - **Dynamic Visibility Toggle (`interaction_click_toggle_visibility`)**: Toggling an element between `VISIBLE (1)` and `GONE (0)` via `ComponentVisibilityOperation` and `valueIntegerChange` on `onClick`. Verifies that setting child visibility to `GONE` collapses the container height and dynamically shifts sibling elements up, and subsequent clicks restore layout positioning.
  - **Press & Release Lifecycle (`interaction_touch_down_up_press`)**: Testing separate `onTouchDown` and `onTouchUp` actions on components, dynamically modifying state variables upon finger touch-down and release.
  - **StateLayout Expand/Collapse (`state_layout_expandable_card`)**: Inspired by `RcDroidKaigiExpandableLazyColumnDemo`, dynamically toggles between collapsed (height 40, expand button) and expanded (height 120, full detail + collapse button) states via child `onClick` actions, verifying sibling positioning adjustments.
  - **StateLayout Switch Widget (`state_layout_switch_visibility`)**: Inspired by `SwitchWidget.kt`, demonstrates dual action: animating a StateLayout switch thumb between OFF and ON while concurrently toggling external component visibility (`GONE` vs `VISIBLE`).
  - **StateLayout Arrangement Morphing (`state_layout_row_to_column`)**: Inspired by `RcStateLayoutRowToColumnDemo.kt`, dynamically switches StateLayout child arrangement between Row (height 60) and Column (height 160) configurations on click.

- **Remaining Gaps**:
  - **[`TouchExpression`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/TouchExpression.java) (Opcode 157 — 56.26% Coverage, 796 missed inst, 115 missed br)**:
    - Continuous multi-stage drag with live tracking variable updates.
    - Clamped drag bounds with boundary bounce / overscroll resistance.
    - Fling momentum and velocity decay curves evaluated over multi-frame timelines.
  - **Gesture Conflict Resolution**:
    - Simultaneous drag and click thresholds (disambiguating tap vs drag).
    - Touch cancellation lifecycles during programmatic layout mutations.

---

### F. Shaders & AGSL Procedural Pipelines (`shaders` — 50.4% Inst, 53.1% Branch)

- **[`ShaderData`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/ShaderData.java) (Opcode 45 — 50.38% Coverage, 393 missed inst, 45 missed br)**:
  - **Needed Tests**:
    - Uniform array bindings: float vectors (`vec2`, `vec3`, `vec4`), integer matrices.
    - Dynamic color uniform updates linked to theme tokens.
    - Time-animated procedural AGSL shaders sampled across multi-frame sequences.
    - Sweep gradient angle offsets and radial gradient focal point offsets.

---

### G. Path Operations & Morphing (`pathoperations` — 58.6% Inst, 46.7% Branch)

- **[`PathExpression`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/PathExpression.java) (Opcode 193 — 51.05% Coverage, 374 missed inst, 47 missed br)**:
  - **Finding**: Android requires `count > 1` sampling points; tests specifying zero count throw `"path length must be > 1"`.
  - **Needed Tests**:
    - Parametric polar curves ($r = f(\theta)$) with valid point sampling ($count \ge 16$).
    - Wave oscillator paths with dynamic frequency/amplitude inputs.
    - Closed vs. open loop flag evaluation.
- **[`PathAppend`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/PathAppend.java) (Opcode 149)**:
  - **Needed Tests**: Conic arc segments with weight factors $w \neq 1$, relative cubic Bézier segments, subpath re-opening after close.

---

### H. Text Operations (`textoperations` — 50.9% Inst, 41.6% Branch)

- **[`TextSubtext`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/TextSubtext.java) (Opcode 182)**:
  - **Needed Tests**: Substring extraction with dynamic start/end indices, negative indices, length clamping, and empty range handling.
- **[`TextStyle`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/TextStyle.java) (Opcode 242)**:
  - **Needed Tests**: Font weight variations (100–900), italic slant, text decoration flags (underline, strikethrough), and letter spacing.
- **[`BitmapTextMeasure`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/BitmapTextMeasure.java) (Opcode 183)**:
  - **Needed Tests**: Measurement outputs for multiline strings, max lines truncation, and ellipsis handling.

---

### I. Matrix Math & Transformations (`matrixmath` — 60.4% Inst, 48.1% Branch)

- **[`Matrix`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/utilities/Matrix.java) (54.99% Coverage, 591 missed inst, 73 missed br)**:
  - **Needed Tests**:
    - 3D perspective projection matrix setup and normalization.
    - Matrix inversion error handling (singular matrix detection where determinant $\approx 0$).
    - Transform composition (Translate $\times$ Rotate $\times$ Scale) applied to homogeneous 3D and 4D vectors.

---

### J. Particle Physics Simulation (`particles` — 61.4% Inst, 57.1% Branch)

- **[`ParticlesCompare`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/ParticlesCompare.java) (Opcode 194 — 42.07% Coverage, 771 missed inst, 118 missed br)**:
  - **Finding**: Tests must ensure [`ParticlesCreate`](file:///Users/nicolasroard/androidx-main-secondary/frameworks/support/compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/ParticlesCreate.java) precedes comparison operations to prevent `NullPointerException` on `mParticles`.
  - **Needed Tests**:
    - Attractor radial field physics with distance falloff.
    - Boundary collision bounce with restitution damping.
    - Particle lifespan expiration and recycling thresholds across $\ge 10$ frames.

---

### K. Semantics & Accessibility (`semantics` — 54.1% Inst, 14.3% Branch)

- **Accessibility Tree Operations (Opcode 65 — `ROOT_CONTENT_BEHAVIOR`)**:
  - **Finding**: Requires `PROFILE_DEPRECATED` in the profile mask during decoding.
  - **Needed Tests**:
    - Merging semantics descendants for complex container hierarchies.
    - Clearing parent semantics via `clearAndSetSemantics`.
    - Accessibility roles: Button, Checkbox, Slider, Heading levels (1–6).
    - Custom accessibility actions and state descriptions.

---

## 4. Verification Workflow

To measure JaCoCo coverage gains on the Android reference engine after adding or updating conformance tests:

```bash
# 1. Run the Android reference test replay harness
PROJECT_PREFIX=:compose:remote: ./gradlew :compose:remote:remote-creation-core:test \
  --tests "*ConformanceGoldGeneratorTest*"

# 2. Re-render the multi-subsystem gold overview report
node compose/remote/specification/conformance/generate-gold-overview.mjs

# 3. Inspect updated metrics in your browser
open compose/remote/specification/conformance/gold-overview.html
```

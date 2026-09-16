# RemoteCompose Player Specification: High-Level Areas

This document defines the high-level functional areas that constitute the normative specification for a **RemoteCompose Player**. 

A RemoteCompose Player is a runtime environment responsible for decoding, materializing, laying out, evaluating, animating, rendering, and handling interactions for RemoteCompose binary documents. The goal of this specification suite is to ensure deterministic, byte-level and visual interoperability across diverse player runtimes (e.g., Android View, Jetpack Compose).

---

## 1. High-Level Runtime Architecture

A conforming RemoteCompose Player executes according to the following lifecycle pipeline:

```
+-------------------------------------------------------------------------+
|                          1. Ingestion & Wire Protocol                    |
|  Binary stream (WireBuffer) -> Header validation -> Opcode stream        |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                  2. Materialization & Expansion (Loom)                  |
|  PatternDefine registration -> PatternInflation -> ID Remapping        |
|  Collection iteration (PatternForEach) -> Procedural Functions          |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                  3. Document Tree & State Initialization                |
|  LayoutComponent hierarchy -> Modifier chains -> Variable registration   |
|  Semantic tree construction -> Initial state resolution                 |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                       4. Playback / Frame Loop                          |
|                                                                         |
|  [A. Time & Clock]      -> Update ANIMATION_TIME, CONTINUOUS_SEC        |
|  [B. Sensor/Input Poll] -> Ingest touch, sensors, and host overrides    |
|  [C. Expression Eval]   -> Topological RPN evaluation of dirty state    |
|  [D. Measure & Layout]  -> Two-pass constraint measurement & placement  |
|  [E. Particle Step]     -> Physics simulation loop update               |
|  [F. Render / Paint]    -> Paint state machine, 2D primitives, shaders  |
|  [G. Host Dispatch]     -> Flush pending click/host action callbacks    |
+-------------------------------------------------------------------------+
```

---

## 1.1 Platform Abstraction Layer (PAL) & Clean-Room Porting Contract

To implement a clean-room player on another platform (such as Swift on Apple platforms or C#/Direct2D on Windows), the platform host must provide four core abstraction interfaces that bind to the platform-agnostic document engine (`RemoteContext`):

### 1. `PaintContext` (Canvas & Rendering Pipeline)
* **Matrix Stack**: `save()`, `restore()`, `translate(dx, dy)`, `scale(sx, sy)`, `rotate(deg, px, py)`, `concat(matrix9)`.
* **Clipping**: `clipRect(left, top, right, bottom)`, `clipPath(pathId)`.
* **2D Vector Primitives**: `drawRect`, `drawRoundRect`, `drawCircle`, `drawOval`, `drawArc`, `drawLine`, `drawPath`.
* **Paint State Application (`applyPaint(PaintBundle)`)**: Translates stroke width, cap/join styles, miter limits, blend modes, linear/radial/sweep gradient shaders, color filters, and path effects into the native platform's graphics context.
* **Bitmap & Text**: `drawImage(bitmapId, srcRect, dstRect, scaleMode)`, `drawText(text, x, y, alignment, fontId, size)`.

### 2. `PlatformClock` & Frame Pump
* **Monotonic Nanosecond Clock**: Provides monotonically increasing time in continuous seconds for `ID_ANIMATION_TIME` and `ID_CONTINUOUS_SEC`.
* **Display VSync Pump**: Pushes frame ticks synchronized with the host display refresh rate (e.g. 60Hz / 120Hz via `CADisplayLink` on Apple or `CompositionTarget.Rendering` / `DXGI` on Windows).
* **64-Bucket Rate Limiter**: Windowed frame interval tracking guaranteeing that rendering pauses when no animated variables, springs, or continuous time loops are active.

### 3. `ResourceProvider`
* **Bitmap Decoder & Cache**: Decodes raw byte buffers (PNG, JPEG, WebP) into native image handles (`CGImage` / `ID2D1Bitmap`) keyed by integer resource ID.
* **Typeface Resolver**: Maps font family names, weights, and slant styles to native font handles (`CTFont` / `IDWriteFontFace`).

### 4. `PlatformInputBridge`
* **Pointer Dispatch**: Routes mouse/touch down, move, up, and cancel events into `RemoteContext`.
* **Inverse Matrix Hit-Testing**: Applies the inverse of the component's accumulated transformation matrix ($P_{\text{local}} = M^{-1} \cdot P_{\text{screen}}$) to determine interactive hit bounds.
* **Action Callback Dispatcher**: Emits `HostActionOperation` events to the embedding application host.

---

## 1.2 Cross-Platform Porting Considerations (Gotchas)

Implementers porting to non-Android environments must account for key graphics conventions:

1. **Coordinate Systems & Vertical Orientation**:
   * RemoteCompose uses a standard computer graphics top-left origin $(0, 0)$ with $+X$ right and $+Y$ down.
   * Platforms whose native graphics contexts use a Cartesian bottom-left origin with $+Y$ up (such as unmodified Apple CoreGraphics `CGContext`) must apply a root coordinate flip:
     $$\text{context.translateBy}(x: 0, y: \text{height}); \quad \text{context.scaleBy}(x: 1.0, y: -1.0)$$
2. **Color Channel Packing**:
   * RemoteCompose encodes 32-bit colors strictly as **ARGB** (`(A << 24) | (R << 16) | (G << 8) | B`).
   * Win32 GDI uses `COLORREF` (`0x00BBGGRR` - Blue and Red channels swapped).
   * Direct2D and Metal / CoreGraphics shaders expect normalized floats $(R, G, B, A) \in [0.0, 1.0]$. The player must provide a color unpacking adapter.
3. **Affine Matrix Order**:
   * 3x3 matrices in RemoteCompose are 9-element arrays in row-major order: `[scaleX, skewX, transX, skewY, scaleY, transY, persp0, persp1, persp2]`.
   * Translation elements reside at array indices `2` and `5`.
4. **Byte Endianness**:
   * All binary wire reading must enforce Big-Endian parsing regardless of the host CPU's native endianness.

---

## 2. Master Table of Specification Areas

| Area ID | Area Name | Primary Focus |
| :--- | :--- | :--- |
| **AREA-01** | [**Wire Protocol, Binary Encoding & Materialization**](AREA-01-WIRE-PROTOCOL.md) | Binary encoding, data types, version negotiation, capability profiles, and inflation lifecycle |
| **AREA-02** | [**Core Operations & Opcode Catalog**](AREA-02-OPERATIONS-CATALOG.md) | Normative catalog of operations, opcode layouts, arguments, and execution phase |
| **AREA-03** | [**Document Manipulation, Loom & Functions**](AREA-03-DOCUMENT-MANIPULATION.md) | Component templates (Loom), macros, ID tiering/remapping, loops, and procedural functions |
| **AREA-04** | [**Expression Engine & Reactive State**](AREA-04-EXPRESSION-ENGINE.md) | Reverse Polish Notation (RPN), arithmetic/trig/logic, dependency graph, and variable domains |
| **AREA-05** | [**Component Hierarchy & Layout Engine**](AREA-05-LAYOUT.md) | Layout managers (Row, Column, Box, Flow, etc.), modifiers pipeline, and 2-pass measure/layout |
| **AREA-06** | [**2D Graphics Canvas & Paint State Machine**](AREA-06-GRAPHICS-CANVAS.md) | Paint bundling, commit boundaries, vector paths, matrix transforms, and drawing primitives |
| **AREA-07** | [**Shaders & Dynamic GPU Materials**](AREA-07-SHADERS-EFFECTS.md) | AGSL / RuntimeShader execution, dynamic expression uniform binding, and shader security |
| **AREA-08** | [**Text Subsystem & Typography**](AREA-08-TEXT-TYPOGRAPHY.md) | Anchored text, path/circular text, multi-line wrapping, dynamic text formatting, font resolvers |
| **AREA-09** | [**Asset Pipeline & Memory Management**](AREA-09-ASSETS-MEMORY.md) | Bitmap streaming, pre-decoding/prepared documents, scaling modes, and offscreen buffers |
| **AREA-10** | [**Clock, Time & Animation Engine**](AREA-10-CLOCK-ANIMATION.md) | Monotonic vs. render clocks, AnimationSpec, spring/tween physics, frame budgeting, FPS windowing |
| **AREA-11** | [**Particle Simulation Engine**](AREA-11-PARTICLE-SYSTEM.md) | Particle lifecycle, emitter configurations, state buffer integration, and physics simulation loop |
| **AREA-12** | [**Interactivity, Input & Gestures**](AREA-12-INTERACTIVITY-INPUT.md) | Hit-testing, coordinate mapping, click/drag modifiers, scroll delegates, and physics |
| **AREA-13** | [**Host Application Communication Bridge**](AREA-13-HOST-BRIDGE.md) | Action callbacks (`HostActionOperation`), external state mutation, and document patching |
| **AREA-14** | [**Platform Services & Hardware Integration**](AREA-14-PLATFORM-SERVICES.md) | Haptic triggers, audio synthesis/playback, sensor telemetry, and system theme palettes |
| **AREA-15** | [**Accessibility (a11y) & Semantics**](AREA-15-ACCESSIBILITY-SEMANTICS.md) | Semantic node generation, screen reader traversal, action affordances, and virtual bounding boxes |
| **AREA-16** | [**Security, Sandboxing & Resource Quotas**](AREA-16-SECURITY-SANDBOXING.md) | Op count limits, bitmap memory limits, recursion caps, shader controls, and isolation |
| **AREA-17** | [**Conformance, Diagnostics & Debugging**](AREA-17-CONFORMANCE-DEBUGGING.md) | Telemetry metrics, wireframe/debug overlays, reference test suites, and golden baselines |

---

## 3. Detailed Scope for Each Area

### [AREA-01: Wire Protocol, Binary Encoding & Materialization Pipeline](AREA-01-WIRE-PROTOCOL.md)
* **Encoding Rules**: Primitive size definitions (BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BUFFER, UTF8-as-BUFFER), byte alignment, endianness (Big-Endian network order).
* **Document Header**: Version extraction (`MAJOR_VERSION`, `MINOR_VERSION`), capability flags, document dimensions, and metadata.
* **Compatibility & Profiles**: Backward/forward compatibility rules, handling unsupported/unknown opcodes (skip tables vs. abort), and profile negotiation (`RcProfiles`).
* **Materialization Pipeline**: Ordered stages from raw binary buffer to ready-to-render document representation (`CoreDocument`).

### [AREA-02: Core Operations & Opcode Catalog](AREA-02-OPERATIONS-CATALOG.md)
* **Opcode Registry**: Complete enumeration of integer opcodes and their mapping to operations.
* **Serialization Invariants**: Exact field-by-field layout, required vs. optional fields, variable-length buffers.
* **Execution Classification**: Classification of each operation into its execution phase:
  * Parse / Registration phase
  * Measure / Layout phase
  * Evaluation phase
  * Paint / Draw phase

### [AREA-03: Document Manipulation, Loom & Procedural Functions](AREA-03-DOCUMENT-MANIPULATION.md)
* **Loom Component Templates (Macros)**:
  * Definition (`PatternDefine`) and inflation (`PatternInflation`, `IncludeReferencedOperations`).
  * Expansion in both structural child lists and within `ComponentModifiers`.
  * Slot-based composition using `PatternArgument` and `PatternBlock`.
* **ID Scoping & Remapping Architecture**:
  * Tier 1 (Global System IDs: 0–41): Invariant across expansions.
  * Tier 2 (Macro-Local IDs: `0x4000`–`0x4FFF`): Distinct dynamic allocation per expansion call site.
  * Tier 3 (Document IDs): Normal document-level variable and asset keys.
  * Call-site parameter-to-argument substitution via `LoomWireBuffer` and `RemapContext`.
* **Collection Iteration**: `PatternForEach` semantics, dynamic context forking, and `localItemId` remapping over collections.
* **Procedural Subroutines**: `FloatFunctionDefine` and `FloatFunctionCall`, argument stack mechanics, return registers, and call-depth limits.
* **Incremental Updates**: Document patching semantics via `updateDocument` and selective reinflation.

### [AREA-04: Expression Engine & Reactive State](AREA-04-EXPRESSION-ENGINE.md)
* **Reverse Polish Notation (RPN) Model**: Stack-based evaluation semantics, NaN-encoded ID resolution (`resolveNanId`, `resolveLongNanId`).
* **Operations & Operators**:
  * Arithmetic: `ADD`, `SUB`, `MUL`, `DIV`, `MOD`, `POW`.
  * Trigonometry: `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`, `ATAN`, `ATAN2`.
  * Logic & Comparison: `EQ`, `NEQ`, `GT`, `GE`, `LT`, `LE`, `AND`, `OR`, `IFELSE`.
  * Specialized Math: `ABS`, `MIN`, `MAX`, `CLAMP`, `RAND`, `PINGPONG`, `SQUARE`, `SQRT`.
* **Evaluation Semantics**: Division by zero, floating point precision requirements, integer casting, boolean normalization.
* **Reactive Dependency Graph**: Variable registration (`VariableProvider`), dirty flag propagation, topological evaluation order, and cycle prevention.
* **State Domains & Namespaces**: Document-scoped state, platform system variables (`SYSTEM:*`), host application state (`USER:*`), and named variables (`NamedVariable`).

### [AREA-05: Component Hierarchy & Layout Engine](AREA-05-LAYOUT.md)
* **Component Model**: Lifecycle of `ComponentStart`, `ComponentEnd`, `ContainerEnd`, and `RootLayoutComponent`.
* **Layout Managers**:
  * Linear Layouts: `RowLayout`, `ColumnLayout` (arrangement, alignment, weights).
  * Container Layouts: `BoxLayout`, `FitBoxLayout`, `CanvasLayout`.
  * Advanced Layouts: `FlowLayout`, `StateLayout`.
  * Responsive / Collapsible Layouts: `CollapsibleRowLayout`, `CollapsibleColumnLayout` using `CollapsiblePriority`.
* **Modifier Pipeline**:
  * Evaluation order and wrapper hierarchy.
  * Dimensional constraints: `WidthModifier`, `HeightModifier`, `DimensionConstraintsModifier`, `WidthIn`, `HeightIn`.
  * Spatial & Visual: `PaddingModifier`, `OffsetModifier`, `BackgroundModifier`, `BorderModifier`, `ClipRect`, `RoundedClipRect`, `GraphicsLayerModifier`, `ZIndexModifier`.
  * Behavioral: `ScrollModifierOperation`, `MarqueeModifierOperation`, `ComponentVisibilityOperation`.
* **Measure & Layout Cycle**:
  * Two-pass algorithm: constraint propagation down, resolved sizes up, placement down.
  * Measure Policy generations (Version 0 Legacy through Version 4 EnforceConstraints).
  * Invalidation triggers, boundary pruning, and partial layout passes.
* **Layout Optimization Subsystem (`FEATURE_OPTIMIZATION_LEVEL`)**:
  * Bitmask flags: `OPTIMIZATION_MEASURE_CACHE (1)`, `OPTIMIZATION_LAYOUT_BOUNDARIES (2)`, `OPTIMIZATION_FLAT_MEASURE_PASS (4)`, `OPTIMIZATION_CONSTRAINTS_CACHE (8)`.
  * Relayout boundary subtree isolation and `mDirtyBoundaries` partial execution.
  * `FlatMeasurePass` $O(1)$ flat array lookup and generational token invalidation.
  * Constraints caching with exact, vertical compatible, and horizontal compatible multi-pass reuse.
  * Steady-state zero-allocation object recycling via `ComponentMeasurePool`.

### [AREA-06: 2D Graphics Canvas & Paint State Machine](AREA-06-GRAPHICS-CANVAS.md)
* **Paint State Model**: Accumulation of style properties into `PaintBundle`, commit boundaries (`PAINT_VALUES`), and inheritance.
* **Canvas Transformation Stack**: Matrix stacks (`MatrixSave`, `MatrixRestore`, `MatrixTranslate`, `MatrixRotate`, `MatrixScale`, `MatrixSkew`, `MatrixFromPath`).
* **Vector Path Engine**:
  * Explicit path segments (`PathCreate`, `PathAppend`).
  * Algorithmic / mathematical paths (Cartesian and Polar generators).
  * Path operations: Boolean combinations (`PathCombine`), morphing/interpolation (`PathTween`, `DrawTweenPath`).
* **Drawing Primitives**: Specification of geometric rasterization for rects, rounded rects, lines, circles, ovals, arcs, and sectors.
* **Compositing & Effects**: Blend modes, stroke join/cap styles, and dash/corner path effects (`PaintPathEffects`).

### [AREA-07: Shaders & Dynamic GPU Materials](AREA-07-SHADERS-EFFECTS.md)
* **AGSL / RuntimeShader Subsystem**: Shader source loading via `ShaderData`, runtime shader compilation, and caching.
* **Uniform Binding Engine**: Dynamic linking between shader uniforms (floats, vectors, colors) and RemoteCompose reactive expressions.
* **Fallback & Security Policy**: Verification through `ShaderControl`, graceful degradation to solid fills or fallbacks if unsupported or rejected by host.
* **Filters & Effects**: Color filters, matrix image filters, and layer blur effects.

### [AREA-08: Text Subsystem & Typography](AREA-08-TEXT-TYPOGRAPHY.md)
* **Direct Drawing**: `DrawText`, `DrawTextAnchored` (horizontal/vertical alignments, baseline offsets), `DrawTextOnCircle`, and `DrawTextOnPath`.
* **High-Level Layout (`CoreText`, `TextLayout`)**: Multi-line wrapping, ellipsis truncation, line breaking, justification, and cesure analysis.
* **Dynamic String Operations**:
  * Number formatting: `TextFromFloat` (digit precision, formatting flags).
  * String manipulation: `TextMerge`, `TextSubtext`, `TextTransform`, `TextLength`, and lookups (`TextLookup`, `TextLookupInt`).
* **Typeface & Font Resolution**:
  * Embedded TrueType/OpenType font resources (`FontData`).
  * Platform font lookup via `TypefaceResolver`.
  * High-performance pre-rendered bitmap fonts (`BitmapFontData`, `DrawBitmapFontText`, `DrawBitmapFontTextOnPath`, `BitmapTextMeasure`).

### [AREA-09: Asset Pipeline & Memory Management](AREA-09-ASSETS-MEMORY.md)
* **Bitmap Management**: Decoding raster formats (`BitmapData`), asynchronous loading via `BitmapLoader`, and lifecycle tracking.
* **Document Preparation**: Pre-computation and asset precaching (`shouldPrepare`, `RemotePreparedDocument`).
* **Aspect & Fitting Modes**: `DrawBitmapScaled`, `FitBox` aspect fit, aspect fill, matrix scaling, and nine-patch support.
* **Render Targets**: Offscreen bitmap rendering surfaces (`DrawToBitmap`) and render-to-texture capabilities.

### [AREA-10: Clock, Time & Animation Engine](AREA-10-CLOCK-ANIMATION.md)
* **Clock Providers**: Synchronized time base (`RemoteClock`, `SystemClock`, `CalendarSystemClock`), simulation time vs. wall-clock time.
* **Animation Specifications (`AnimationSpec`)**: Physics-based springs (damping, stiffness), duration-based cubic bezier curves, linear interpolation, repeat, and reverse modes.
* **Animatable Values & Measurement**: Continuous transitions of component dimensions and constraints (`AnimateMeasure`, `RootAnimateMeasure`).
* **Frame Rate Management**:
  * Instantaneous maximum frame rate (`MAX_FPS`).
  * Sustained average frame rate windowing (`DEFAULT_MAX_AVG_FPS`, `DEFAULT_WINDOW_SEC`).
  * Interaction-based boost (`touchBoost`) and idle downclocking/pausing.

### [AREA-11: Particle Simulation Engine](AREA-11-PARTICLE-SYSTEM.md)
* **Particle Lifecycle**: Emitter initialization (`ParticlesCreate`), maximum particle capacity, spawn rates, and lifetimes.
* **Simulation Loop**: Per-frame step execution (`ParticlesLoop`), velocity/acceleration integration, forces (gravity, drag), and boundary comparisons (`ParticlesCompare`).
* **State Buffer**: Layout and update mechanics of particle float buffers.
* **Determinism & Performance**: Seeded PRNG stability, handling variable delta-time, and batch drawing primitives.

### [AREA-12: Interactivity, Input & Gestures](AREA-12-INTERACTIVITY-INPUT.md)
* **Hit-Testing Engine**: Hierarchical point-in-polygon / point-in-rect tests, accounting for inverted transformation matrices and clipping bounds.
* **Pointer & Touch Events**: Dispatch lifecycle of touch down, move, up, cancel, and hover.
* **Gesture Modifiers**:
  * Click handlers: `ClickArea`, `ClickModifierOperation`, `MultiClickModifier` (tap counts, intervals).
  * Draggable content: Motion thresholds, drag updates, and draggable component state.
* **Scroll Engine**:
  * `ScrollModifierOperation` and `ScrollDelegate`.
  * Physics calculation: `TouchExpression`, inertia, fling velocity decel, and boundary spring back.
  * Overscroll & Edge Effects: `ScrollingEdgeEffect`.
  * Nested scrolling: Parent-child scroll arbitration and bubbling.

### [AREA-13: Host Application Communication Bridge](AREA-13-HOST-BRIDGE.md)
* **Host Action Dispatch**:
  * Event emission: `HostActionOperation`, `HostNamedActionOperation`, `IdActionCallbacks`.
  * Event metadata payloads and structured serialization.
* **External State Injection**:
  * Host mutations via `StateUpdater`: updating string, integer, float, color, and bitmap variables.
  * Domain isolation (`USER:` vs `SYSTEM:` namespaces).
* **Extensibility**: Extension points via `CustomContext` and custom operation handlers.

### [AREA-14: Platform Services & Hardware Integration](AREA-14-PLATFORM-SERVICES.md)
* **Haptics**: Standard feedback constants (`HapticFeedback`) and integration via `HapticSupport`.
* **Sound Subsystem**: Tone generation (`ToneSynthesizer`), audio resource playback (`PlaySound`, `SoundData`), and audio triggers (`SoundExpression`).
* **Hardware Sensors**: Standard system variables for accelerometer, gyroscope, orientation, and ambient light sensors (`SensorSupport`).
* **System Theming**: Dynamic theme switching (`Theme.LIGHT`, `Theme.DARK`, `Theme.UNSPECIFIED`), color theme tables (`ColorTheme`), and system palette overrides.

### [AREA-15: Accessibility (a11y) & Semantics](AREA-15-ACCESSIBILITY-SEMANTICS.md)
* **Semantic Node Hierarchy**: Generation of an accessible node tree from layout components and modifiers (`CoreDocumentAccessibility`, `SemanticNodeApplier`).
* **Semantic Attributes**: Content descriptions (`RootContentDescription`), roles (button, container, text, image), headings, and state descriptions.
* **Action Affordances**: Screen reader click actions, custom accessibility actions, scroll actions (`showOnScreen`, `scrollByOffset`).
* **Virtual Touch Exploration**: Coordinate transformations for virtual accessibility views (`RemoteComposeTouchHelper`).

### [AREA-16: Security, Sandboxing & Resource Quotas](AREA-16-SECURITY-SANDBOXING.md)
* **Runtime Execution Quotas (`Limits`)**:
  * Maximum operations per frame (`MAX_OP_COUNT`).
  * Maximum loop iterations and macro recursion depth.
  * RPN expression stack size caps.
* **Memory Quotas**:
  * Maximum bitmap memory allocation per player (`MAX_BITMAP_MEMORY`).
  * Maximum single image dimension (`MAX_IMAGE_DIMENSION`).
  * Maximum wire buffer and document size limits.
* **Sandboxing & Defenses**:
  * Path traversal protections in persistent macro caching (`saveMacro`).
  * Shader evaluation policy via `ShaderControl`.
  * Safe error containment (ignoring malformed operations without crashing the host process).

### [AREA-17: Conformance, Diagnostics & Debugging](AREA-17-CONFORMANCE-DEBUGGING.md)
* **Performance Telemetry**: Instrumentation APIs for frame evaluation time (`getEvalTime`), ops per frame (`getOpsPerFrame`), layout duration, and render duration.
* **Visual Diagnostics**: Debug overlay flags (`setDebug`), component boundary wireframes, modifier badges, and layout anchors.
* **Conformance Test Protocol**: Golden screenshot suites, expression evaluation unit test vectors, and wire format decode verification matrices.

---

## 4. Next Steps & Specification Workflow

Each high-level area will be specified in an individual specification document under this directory, following a standardized template:
1. **Normative Overview & Invariants**
2. **Binary Wire Format & Data Structures**
3. **Execution Pipeline & Algorithms**
4. **Edge Cases & Failure Recovery**
5. **Reference Conformance Test Vectors**

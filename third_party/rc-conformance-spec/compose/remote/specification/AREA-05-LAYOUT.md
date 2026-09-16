# AREA-05: Component Hierarchy & Layout Engine Specification

**Document Version:** 1.1.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview & Architectural Invariants

The RemoteCompose Layout Subsystem is responsible for converting a flat stream of serialized binary operations into a hierarchical, constraint-based UI tree, resolving dynamic dimensional constraints, arranging components, handling animated transitions, and ordering rendering passes.

Designed specifically for streaming UI over process boundaries, the layout architecture is governed by four strict architectural invariants:

1. **Strict 2-Pass Decoupled Lifecycle**: Measurement and placement/layout MUST be strictly separated:
   * **Measure Pass**: Queries components with incoming dimensional constraints `(minWidth, maxWidth, minHeight, maxHeight)` and outputs resolved dimensions, padding insets, and visibility into an external, detached `MeasurePass` / `FlatMeasurePass` table without mutating the component's internal coordinate state `(mX, mY)`.
   * **Layout / Placement Pass**: Reads the `MeasurePass` table, computes final local coordinates `(mX, mY)`, evaluates modifier layout insets, configures scroll delegates, and initiates bounds transitions if dimensions changed across frames.
   * **Paint Pass**: Canvas matrix saving/restoration, modifier drawing, scroll edge effect composition, Z-index sorting, and graphics layer buffer compositing.
2. **Order-Dependent Modifier Pipeline**: Modifiers are evaluated sequentially. A `Padding` modifier placed before a `Background` modifier acts as an external margin; a `Padding` modifier placed after acts as an internal inset.
3. **Deterministic Constraint Enforcement**: Conforming players MUST adhere to `Measure Policy Version 4 (EnforceConstraints)`: final measured dimensions MUST strictly fall within parent `[minWidth, maxWidth]` $\times$ `[minHeight, maxHeight]` and modifier-level clamping limits.
4. **Zero-Allocation Steady State**: During continuous animations or steady-state re-layouts at 60/120 FPS, the layout engine must avoid per-frame heap allocations by using flat array-backed `FlatMeasurePass` tables, generational invalidation tokens, and pre-allocated `ComponentMeasurePool` object pools.

---

## 2. Component Hierarchy & Object Model

### 2.1 Wire Tokenization & Materialization

In the binary wire format, layout hierarchies are delimited by structural tokens:

```
[RootLayoutComponent (id, animId, x, y, w, h)]
    [RowLayout | ColumnLayout | BoxLayout | ...]
        [ComponentModifiers]
            [WidthModifier | HeightModifier | PaddingModifier | ...]
        [LayoutComponentContent]
            [ComponentStart (id, animId, x, y, w, h)]
                ... child operations ...
            [ComponentEnd]
        [ComponentEnd] (or [ContainerEnd])
[ComponentEnd] (or [ContainerEnd])
```

During document materialization (`CoreDocument.initFromBuffer`):
1. **Stack Inflation**: When encountering container starts (`RootLayoutComponent`, `RowLayout`, `BoxLayout`, etc.), the player instantiates the corresponding `Component` class and pushes it onto an inflation stack.
2. **Operation Attachment**: Subsequent operations are appended to the container's operation list (`mList`).
3. **Container Nesting**: When encountering `ComponentEnd` (opcode `162`) or `ContainerEnd` (opcode `136`), the container is popped from the stack and nested under its parent component.
4. **Synthetic ID Assignment**: If a component does not contain an explicitly serialized ID (`mComponentId == -1`), `RootLayoutComponent.assignIds()` assigns a sequential negative ID (`-1, -2, -3, ...`) to guarantee global addressability across all player tools and debuggers.
5. **Dense Layout Indexing**: `CoreDocument.assignAllLayoutIndices()` traverses the entire component tree and assigns contiguous 0-based layout indices (`mInternalLayoutIndex = 0, 1, 2, ... N - 1`). These indices enable direct $O(1)$ array slot lookups in `FlatMeasurePass`.

### 2.2 Class Hierarchy

```
Operation
   └── Component (mComponentId, mAnimationId, mInternalLayoutIndex, mX, mY, mW, mH, mVisibility, mList)
         └── LayoutComponent (mComponentModifiers, mChildrenComponents, mContent, Padding, Z-Index, Scroll)
               ├── LayoutManager (Measurable, Measure Policies, Weight Distribution, Intrinsic Queries)
               │     ├── RowLayout
               │     ├── ColumnLayout
               │     ├── BoxLayout
               │     ├── FlowLayout
               │     ├── CollapsibleRowLayout / CollapsibleColumnLayout
               │     ├── FitBoxLayout
               │     ├── StateLayout
               │     ├── CanvasLayout
               │     ├── ImageLayout
               │     └── TextLayout
               └── RootLayoutComponent (Viewport root, MeasurePass orchestration, Dirty Boundary queue)
```

* **`Component`**: Base layoutable unit. Encapsulates identity, layout coordinates, visibility state (`VISIBLE`, `INVISIBLE`, `GONE`), animation specifications (`AnimationSpec`), dirty flags (`mNeedsMeasure`, `mNeedsRepaint`, `mDirty`), and tree structure (`mParent`, `mList`).
* **`LayoutComponent`**: Extends `Component`. Adds modifier management (`ComponentModifiers`), child component list (`mChildrenComponents`), padding values (`mPaddingLeft`, `mPaddingTop`, `mPaddingRight`, `mPaddingBottom`), computed layout operations (`mComputedLayoutModifiers`), scroll delegates (`mHorizontalScrollDelegate`, `mVerticalScrollDelegate`), and Z-index state.
* **`LayoutManager`**: Abstract base for resizable layout containers implementing `Measurable`. Manages multi-pass measurement policies, weight calculations, intrinsic width/height queries, and wrap size computations.
* **`RootLayoutComponent`**: The top-level root node of the layout tree. Ingests viewport dimensions from the host player window, maintains the shared `MeasurePass` / `FlatMeasurePass` instance, manages the dirty boundary queue (`mDirtyBoundaries`), and drives partial or full layout passes.

---

## 3. The Two-Pass Measure & Layout Lifecycle

```
================================================================================
 1. INVALIDATION PHASE
================================================================================
 Dynamic state mutation (Text update, expression evaluation, variable change)
                            |
                            v
 Component.invalidateMeasure()
   ├── Sets mNeedsMeasure = true, mNeedsRepaint = true
   └── Ancestor walk:
         ├── If ancestor isRelayoutBoundary() == true:
         │     root.registerDirtyBoundary(boundary)
         │     STOP walk (ancestors above boundary remain clean)
         └── Else:
               Walk continues to RootLayoutComponent (root.mNeedsMeasure = true)

================================================================================
 2. MEASURE PASS (RootLayoutComponent.layout / RootLayoutComponent.measure)
================================================================================
 Top-Down Constraint Propagation -> Bottom-Up Dimension Synthesis
   ├── Viewport bounds injected: [minWidth, maxWidth], [minHeight, maxHeight]
   ├── LayoutManager.measure():
   │     ├── Check constraints cache & measure cache:
   │     │     If clean & cached constraints match -> BYPASS SUBTREE MEASURE
   │     └── Execute active Measure Policy (Version 4: EnforceConstraints)
   │           ├── 1. Compute modifier-defined dimensions (Width/Height exact/fill/dp)
   │           ├── 2. Subtract padding to derive inset constraints (insetMaxWidth, insetMaxHeight)
   │           ├── 3. If Wrap/IntrinsicMin: runComputeWrapSize() against children
   │           ├── 4. Clamp dimensions to [minWidth, maxWidth] and WidthIn/HeightIn
   │           └── 5. Populate ComponentMeasure record in MeasurePass
   └── Self coordinates (mX, mY) are NOT modified during this pass

================================================================================
 3. LAYOUT / PLACEMENT PASS (LayoutComponent.layout)
================================================================================
 Consume ComponentMeasure & Position Children
   ├── Retrieve resolved (w, h, visibility) from MeasurePass
   ├── If animated & bounds changed:
   │     Obtain origin & target from ComponentMeasurePool -> start AnimateMeasure
   ├── Apply modifier layout: ComponentModifiers.layout(context, this, w, h)
   ├── Position children: assign local offsets (mX, mY) according to container rules
   │     (Row: start/center/end/spacedBy/weights; Column: top/center/bottom/weights)
   ├── Recursively invoke child.layout(context, measurePass)
   └── Mark this.mNeedsMeasure = false
```

### 3.1 Measure Policy Generations (`FEATURE_MEASURE_VERSION = 17`)

Conforming players MUST support the five indexed measure policy versions to maintain backwards compatibility with legacy documents while defaulting to Version 4:

| Version | Name | Class | Key Characteristics |
| :---: | :--- | :--- | :--- |
| **0** | `LegacyMeasurePolicy` | `LegacyMeasurePolicy.java` | v0.4.0 backwards compatibility. Unconstrained wrap computations without inset clamping; post-hoc fill dimension overrides. |
| **1** | `BaseModernMeasurePolicy` | `BaseModernMeasurePolicy.java` | Formal two-pass decoupling; support for fractional fills (`fillMaxWidth(fraction)`), padding insets, and intrinsic minimum/maximum queries. |
| **2** | `InsetWrapMeasurePolicy` | `InsetWrapMeasurePolicy.java` | Activates `shouldApplyInsetWrap() == true`. Restricts cross-axis wrap dimensions by subtracting padding from fixed measured dimensions. |
| **3** | `InlineExpressionMeasurePolicy` | `InlineExpressionMeasurePolicy.java` | Activates `shouldUpdateComponentValues() == true`. Evaluates inline expressions and dynamic variable operations directly during the measure pass before wrap computation. |
| **4** | `EnforceConstraintsMeasurePolicy` | `EnforceConstraintsMeasurePolicy.java` | **Default Normative Engine** (`DEFAULT_MEASURE_TYPE = 4`). Activates `shouldEnforceConstraints() == true`. Guarantees that measured dimensions strictly clamp within `[minWidth, maxWidth]` $\times$ `[minHeight, maxHeight]` and explicit modifier constraints (`WidthIn`, `HeightIn`). |

#### Touch Event Policy Versions (`FEATURE_TOUCH_VERSION = 18`)
* **Version 0**: Legacy coordinate hit-testing.
* **Version 1 (`FIX_TOUCH_EVENT`, Default)**: Normative default (`DEFAULT_TOUCH_VERSION = 1`). Corrects coordinate inversion and hit target margins for transformed and animated components.

### 3.2 Normative Measure Algorithm (Version 4: EnforceConstraints)

For any `LayoutManager` instance, the measurement pass executes the following exact sequence:

1. **Modifier Baseline Extraction**:
   $$\text{measuredWidth} = \min(\text{maxWidth}, \text{computeModifierDefinedWidth}())$$
   $$\text{measuredHeight} = \min(\text{maxHeight}, \text{computeModifierDefinedHeight}())$$
2. **Intrinsic Dimension Fallback**:
   * If `widthModifier.isIntrinsicMin()`: $\text{maxWidth} = \text{minIntrinsicWidth}() + \text{paddingLeft} + \text{paddingRight}$.
   * If `heightModifier.isIntrinsicMin()`: $\text{maxHeight} = \text{minIntrinsicHeight}() + \text{paddingTop} + \text{paddingBottom}$.
3. **Explicit Modifier Clamping Limits (`WidthIn` / `HeightIn`)**:
   $$\text{minWidth} = \max(\text{minWidth}, \text{widthIn.getMin}()), \quad \text{maxWidth} = \min(\text{maxWidth}, \text{widthIn.getMax}())$$
   $$\text{minHeight} = \max(\text{minHeight}, \text{heightIn.getMin}()), \quad \text{maxHeight} = \min(\text{maxHeight}, \text{heightIn.getMax}())$$
4. **Padding Insets**:
   $$\text{insetMaxWidth} = \max(0, \text{maxWidth} - \text{paddingLeft} - \text{paddingRight})$$
   $$\text{insetMaxHeight} = \max(0, \text{maxHeight} - \text{paddingTop} - \text{paddingBottom})$$
5. **Fill & Fraction Resolution**:
   * If `isInHorizontalFill()`: $\text{measuredWidth} = \text{maxWidth} \times \text{fraction}$; $\text{minWidth} = \text{measuredWidth} - \text{paddingLeft} - \text{paddingRight}$.
   * If `isInFillParentMaxWidth()`: $\text{measuredWidth} = \text{viewportWidth} \times \text{fraction}$; $\text{minWidth} = \text{measuredWidth} - \text{paddingLeft} - \text{paddingRight}$.
6. **Wrap-Content Resolution**:
   If dimension is set to `WRAP` or `INTRINSIC_MIN`:
   * Execute `runComputeWrapSize(context, minWidth, insetMaxWidth, minHeight, insetMaxHeight, measure)`.
   * Add padding insets: $\text{measuredWidth} = \text{wrapSize.width} + \text{paddingLeft} + \text{paddingRight}$.
7. **Strict Constraint Clamping (`shouldEnforceConstraints`)**:
   $$\text{measuredWidth} = \text{clamp}(\text{measuredWidth}, \text{minWidth}, \text{maxWidth})$$
   $$\text{measuredHeight} = \text{clamp}(\text{measuredHeight}, \text{minHeight}, \text{maxHeight})$$
   $$\text{measuredWidth} = \text{applyWidthConstraints}(\text{measuredWidth})$$
   $$\text{measuredHeight} = \text{applyHeightConstraints}(\text{measuredHeight})$$
8. **Output Record**: Populate `ComponentMeasure` for this component in `MeasurePass` with final $(w, h, \text{visibility})$.

---

## 4. The Layout Optimization Subsystem

RemoteCompose incorporates a comprehensive suite of layout optimizations designed to eliminate CPU overhead, eradicate garbage collection churn, and avoid redundant subtree traversals during dynamic animations and interactive state changes.

### 4.1 Optimization Flags & Header Configuration

Layout optimizations are bitmask-controlled via the document header property `FEATURE_OPTIMIZATION_LEVEL` (Tag ID `28`) or programmatically configured via `CoreDocument.setOptimizationLevel(int mask)`:

```java
public static final int OPTIMIZATION_NONE               = 0;  // 0b0000: All optimizations disabled
public static final int OPTIMIZATION_MEASURE_CACHE      = 1;  // 0b0001: Component measure result caching
public static final int OPTIMIZATION_LAYOUT_BOUNDARIES  = 2;  // 0b0010: Relayout boundary subtree isolation
public static final int OPTIMIZATION_FLAT_MEASURE_PASS  = 4;  // 0b0100: Flat array-backed O(1) measure table
public static final int OPTIMIZATION_CONSTRAINTS_CACHE  = 8;  // 0b1000: Parent constraint cache & multi-pass reuse
public static final int OPTIMIZATION_ALL               = 15; // 0b1111: All optimizations enabled (Default)
```

The normative default for conforming documents and players is `OPTIMIZATION_ALL` (`DEFAULT_FEATURE_OPTIMIZATION_LEVEL = 15`).

---

### 4.2 Optimization 1: Relayout Boundaries (`OPTIMIZATION_LAYOUT_BOUNDARIES = 2`)

#### What it does
Relayout Boundaries isolate internal layout invalidations within a component subtree. When an element inside a boundary changes its size or content, the invalidation propagation stops at the boundary node. Ancestors and siblings outside the boundary are completely omitted from subsequent measurement and layout passes.

#### Why it exists
In naive hierarchical layout engines, any invalidation in a leaf component (such as updating a clock label, animating a progress bar, or toggling an icon) bubbles unconditionally up the parent chain until it reaches the root. As a result, the entire document tree—potentially hundreds of nodes across complex nested structures—must be re-measured and re-positioned on every frame. This causes severe CPU frame drops and prevents high-frequency updates (e.g., 60/120 FPS animations).

#### How it works

1. **Boundary Identification**:
   A node qualifies as a relayout boundary (`Component.isRelayoutBoundary() == true`) if:
   * Relayout boundaries are enabled: `document.isRelayoutBoundaryEnabled() == true`.
   * The node does NOT have dynamic computed layout: `!hasComputedLayout()`.
   * The node's outer dimensions are fixed and independent of its children's sizes:
     $$\big(\text{widthModifier.isExact()} \land \text{heightModifier.isExact()}\big) \lor \big(\text{widthModifier.isFill()} \land \text{heightModifier.isFill()}\big)$$

2. **Invalidation Pruning (`Component.invalidateMeasure()`)**:
   When a child node's measurement changes:
   ```java
   public void invalidateMeasure() {
       needsRepaint();
       mNeedsMeasure = true;
       RootLayoutComponent root = getRoot();
       if (root != null) {
           Component p = mParent;
           while (p != null) {
               p.mNeedsMeasure = true;
               if (p.isRelayoutBoundary()) {
                   root.registerDirtyBoundary(p);
                   break; // TERMINATE: ancestor walk halts here!
               }
               p = p.mParent;
           }
           return;
       }
   }
   ```
   Because the boundary component's outer dimensions cannot change as a result of its children changing, its parent does not need to remeasure. The ancestor walk terminates immediately, and the boundary node is registered in `RootLayoutComponent.mDirtyBoundaries`.

3. **Incremental Partial Layout Pass (`RootLayoutComponent.performPartialLayoutPass()`)**:
   On the next frame, `RootLayoutComponent.layout()` inspects its state:
   * If `mNeedsMeasure == false` but `mDirtyBoundaries` is not empty:
     ```java
     for (Component boundary : mDirtyBoundaries) {
         if (boundary.mNeedsMeasure) {
             resetSubTreeMeasureState(boundary, measurePass);
             float w = boundary.getWidth();
             float h = boundary.getHeight();
             boundary.measure(context.getPaintContext(), w, w, h, h, measurePass);
             boundary.layout(context, measurePass);
         }
     }
     mDirtyBoundaries.clear();
     ```
   * The root simply measures and lays out the dirty boundary subtree using its existing width and height $(W, W, H, H)$. The rest of the document hierarchy is entirely skipped.

4. **Disabled Fallback (`OPTIMIZATION_NONE`)**:
   If the boundary optimization bit is cleared, `isRelayoutBoundary()` always returns `false`. Invalidation bubbles all the way to `RootLayoutComponent`, setting `root.mNeedsMeasure = true` and forcing a full-tree layout cycle.

---

### 4.3 Optimization 2: Measure Cache (`OPTIMIZATION_MEASURE_CACHE = 1`)

#### What it does
Caches resolved measurement outputs directly on `ComponentMeasure` records across measure passes and intermediate probe passes.

#### Why it exists
Layout containers frequently probe children multiple times during a single layout frame:
* In a `RowLayout` or `ColumnLayout` containing weighted children, all unweighted children are measured first to determine remaining available space, and weighted children are measured subsequently.
* In a `FitBoxLayout`, multiple candidate children are measured speculatively until one fits within the bounding box.
* In a `FlowLayout`, items are probed to determine line wrapping boundaries before final placement.
Without a measure cache, subtrees are measured 2 to 3 times per frame, exponentially increasing measurement cost in nested hierarchies.

#### How it works
* When `isMeasureCacheEnabled()` is active in conjunction with `OPTIMIZATION_CONSTRAINTS_CACHE`:
* Before invoking the active measure policy, `LayoutManager.measure()` checks:
  ```java
  if (useConstraintsCache && useMeasureCache && !this.mNeedsMeasure
          && !hasDynamicComputes() && !hasChildWithComputedLayout()
          && m.hasCachedConstraints(minWidth, maxWidth, minHeight, maxHeight)) {
      return; // Cache hit: bypass measure policy entirely!
  }
  ```
* If the component's subtree is clean (`!mNeedsMeasure`), contains no dynamic computed expressions, and incoming constraints match the cached constraints, measurement returns immediately.
* The previously computed dimensions $(mW, mH)$ and visibility state stored in `ComponentMeasure` remain intact and are reused directly for placement.

---

### 4.4 Optimization 3: Flat Measure Pass (`OPTIMIZATION_FLAT_MEASURE_PASS = 4`)

#### What it does
Replaces the standard tree/map-backed `MeasurePass` table with `FlatMeasurePass`—a flat, contiguous array of `ComponentMeasure` references indexed by dense, sequential component layout indices.

#### Why it exists
The baseline `MeasurePass` stores measurement records in a `HashMap<Integer, ComponentMeasure>`:
1. **Autoboxing Allocation**: Every lookup (`measurePass.get(id)`) requires boxing the primitive `int id` into a heap-allocated `java.lang.Integer`. Across hundreds of components over multiple passes, this produces thousands of transient objects per second.
2. **Hash Overhead & Cache Misses**: Hash calculations, bucket pointer chasing, and potential collisions incur CPU cycles and disrupt CPU L1/L2 cache locality.
3. **Map Clearing Overhead**: Discarding or clearing the map each frame requires traversing table buckets or discarding and reallocating table buckets.

#### How it works

```
+-----------------------------------------------------------------------------------+
| CoreDocument: assignAllLayoutIndices() during inflation                           |
| Component A -> idx 0 | Component B -> idx 1 | Component C -> idx 2 ...            |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
 FlatMeasurePass
 +---------------------------------------------------------------------------------+
 | mGeneration = 42                                                                |
 | mMetrics: [ Slot 0 ]       [ Slot 1 ]       [ Slot 2 ]       ... [ Slot N-1 ]   |
 |           ComponentMeasure ComponentMeasure ComponentMeasure                    |
 +---------------------------------------------------------------------------------+
             |
             +---> get(Component C):
                     1. idx = C.mInternalLayoutIndex (O(1) direct array index)
                     2. measure = mMetrics[idx]
                     3. If measure.mGeneration != mGeneration:
                          measure.reset(...)  // Zero-allocation in-place reuse!
                          measure.mGeneration = mGeneration
                     4. Return measure
```

1. **Dense Layout Index Assignment (`CoreDocument.assignAllLayoutIndices`)**:
   During document inflation, the document traverses all components depth-first and assigns dense, contiguous indices:
   ```java
   public int assignLayoutIndex(@NonNull Component component) {
       if (component.mInternalLayoutIndex < 0) {
           component.mInternalLayoutIndex = mNextLayoutIndex++;
       }
       return component.mInternalLayoutIndex;
   }
   ```
2. **Direct $O(1)$ Array Indexing**:
   `FlatMeasurePass` allocates a contiguous array sized to `totalComponents`:
   `mMetrics = new ComponentMeasure[totalComponents];`
   Accessing a component's measure record requires zero hashing and zero autoboxing:
   ```java
   int idx = c.mInternalLayoutIndex;
   ComponentMeasure measure = mMetrics[idx];
   ```
3. **$O(1)$ Generational Invalidation Token**:
   Instead of clearing or reallocating the array each frame, `FlatMeasurePass` increments a single generational token:
   ```java
   public void clear() {
       super.clear();
       mGeneration++;
       if (mGeneration == Integer.MAX_VALUE) {
           mGeneration = 1;
           java.util.Arrays.fill(mMetrics, null);
       }
   }
   ```
   Clearing the pass takes $O(1)$ constant time.
4. **In-Place Slot Recycling**:
   When a component is accessed via `get(Component c)`:
   * If a `ComponentMeasure` already exists at `mMetrics[idx]` but its `mGeneration != mGeneration`, it is stale from a prior frame.
   * Rather than allocating a new object, the player calls `measure.reset(id, x, y, w, h, visibility)` and stamps it with the current `mGeneration`.
   * If unallocated, an instance is fetched from `ComponentMeasurePool`.
   * Heap allocations during steady-state frames are completely eliminated ($0$ bytes allocated).

---

### 4.5 Optimization 4: Constraints Cache (`OPTIMIZATION_CONSTRAINTS_CACHE = 8`)

#### What it does
Stores incoming parent constraints $(minWidth, maxWidth, minHeight, maxHeight)$ on `ComponentMeasure` and defines multi-pass compatibility rules that allow reusing measurement results even when constraint ranges differ between probing and placement passes.

#### Why it exists
In many real-world UI hierarchies:
1. When an unrelated sibling animates, the parent container re-runs measurement across all children, delivering the exact same constraint bounding box to clean children.
2. In flex and linear containers, a child is often probed first with loose constraints (e.g., wrap height $[0, \infty)$), and then during positioning is offered exact constraints fixed to the child's own computed size $[H, H]$. Without intelligent constraint compatibility matching, the child would be forced to re-measure itself unnecessarily.

#### How it works

1. **State Storage**:
   `ComponentMeasure` maintains constraint caching fields:
   ```java
   private float mMinWidth = -1f;
   private float mMaxWidth = -1f;
   private float mMinHeight = -1f;
   private float mMaxHeight = -1f;
   private boolean mHasCache = false;
   ```
   At the conclusion of a successful `LayoutManager.measure()` pass, constraints are recorded via:
   `m.setCachedConstraints(minWidth, maxWidth, minHeight, maxHeight);`

2. **Constraint Compatibility Rules (`ComponentMeasure.hasCachedConstraints`)**:
   A constraint cache hit occurs if `mHasCache == true` and ANY of the following three conditions are met:
   * **Rule 1: Exact Match**:
     $$minWidth == mMinWidth \land maxWidth == mMaxWidth \land minHeight == mMinHeight \land maxHeight == mMaxHeight$$
   * **Rule 2: Compatible Vertical Layout Positioning Pass**:
     Horizontal constraints are identical, and incoming vertical constraints are fixed exactly to the previously computed height $mH$:
     $$minWidth == mMinWidth \land maxWidth == mMaxWidth \land minHeight == mH \land maxHeight == mH$$
   * **Rule 3: Compatible Horizontal Layout Positioning Pass**:
     Vertical constraints are identical, and incoming horizontal constraints are fixed exactly to the previously computed width $mW$:
     $$minHeight == mMinHeight \land maxHeight == mMaxHeight \land minWidth == mW \land maxWidth == mW$$

3. **Bypass Execution**:
   If any compatibility rule matches, the entire measurement policy execution, child wrap calculation, and modifier re-evaluations are skipped.

---

### 4.6 Zero-Allocation Object Recycling (`ComponentMeasurePool`)

#### What it does
Maintains a reusable object pool of `ComponentMeasure` instances used across layout cycles, speculative measurement probes, and animated geometry transitions (`AnimateMeasure`).

#### Why it exists
During animated layout transitions (`AnimateMeasure`), the player must capture snapshot records of origin bounds $(X_0, Y_0, W_0, H_0, Vis_0)$ and target bounds $(X_1, Y_1, W_1, H_1, Vis_1)$ for every moving component. Creating new heap objects on every animation start or layout pass triggers immediate Garbage Collection churn and micro-stutter.

#### How it works

1. **Pool Structure**:
   `ComponentMeasurePool` maintains an internal `ArrayList<ComponentMeasure> mPool`:
   * `obtain(id, x, y, w, h, visibility)`: Pops the last element from `mPool` (LIFO for hot cache reuse) and calls `measure.reset(...)`. Only instantiates `new ComponentMeasure()` if the pool is empty during the initial warm-up pass.
   * `recycle(measure)`: Pushes the instance back into `mPool` for future reuse.

2. **Lifecycle Integration**:
   * At the end of an animated transition or when an animation is retargeted, origin and target bounds objects are returned to `ComponentMeasurePool.recycle()`.
   * When `MeasurePass.clear()` is called, all active `ComponentMeasure` records are recycled to the pool.
   * Conformance is validated by `LayoutMemoryChurnTest`: after an initial warm-up pass, subsequent re-layouts and animated size transitions produce **exactly 0 heap allocations**.

---

### 4.7 Operation Dirty Flag Optimization (`ENABLE_DIRTY_FLAG_OPTIMIZATION`)

#### What it does
Tracks granular dirty state (`mDirty = true/false`) on individual operations (`Operation`).

#### Why it exists
When dynamic expressions, remote variables, or sensory data streams update, only a small subset of operations in the document are impacted. Evaluating every operation in every container on every paint pass wastes CPU time.

#### How it works
* `Operation.markDirty()`: Sets `mDirty = true`.
* `Operation.markNotDirty()`: Clears `mDirty = false`.
* During layout and paint passes, components inspect `op.isDirty()` before re-evaluating expressions or updating variables (`VariableSupport.updateVariables()`). Clean operations are executed directly or skipped.

---

### 4.8 Optimization Interactions & Combined Pipeline

The following matrix summarizes how the layout engine behaves across different optimization levels:

| Optimization Level | Invalidation Scope | Measure Pass Data Structure | Measurement Cache Hit Logic | Frame Heap Allocations |
| :--- | :--- | :--- | :--- | :--- |
| **`OPTIMIZATION_NONE (0)`** | Full tree to root (`root.mNeedsMeasure = true`) | `HashMap<Integer, ComponentMeasure>` | None (always re-measures) | High (autoboxing, map entries) |
| **`MEASURE_CACHE (1)`** | Full tree to root | `HashMap<Integer, ComponentMeasure>` | Exact constraint match | Moderate |
| **`LAYOUT_BOUNDARIES (2)`** | Nearest relayout boundary (`mDirtyBoundaries`) | `HashMap<Integer, ComponentMeasure>` | None | Moderate |
| **`FLAT_MEASURE_PASS (4)`** | Full tree to root | Flat array `mMetrics[idx]` ($O(1)$) | None | Low |
| **`ALL (15)` (Normative)** | **Nearest boundary only** | **Flat array + $O(1)$ generational token** | **Exact + Horizontal/Vertical compatibility** | **0 bytes (steady-state zero-allocation)** |

---

## 5. Layout Managers Specification

All layout managers extend `LayoutManager` and implement positioning logic.

### 5.1 RowLayout

Arranges children horizontally in a single row.

* **Opcode Parameters**:
  * `horizontalPositioning`: `START (1)`, `CENTER (2)`, `END (3)`, `SPACE_BETWEEN (6)`, `SPACE_EVENLY (7)`, `SPACE_AROUND (8)`
  * `verticalPositioning`: `TOP (4)`, `CENTER (2)`, `BOTTOM (5)`
  * `spacedBy`: Fixed gap between children (multiplied by density if `DENSITY_BEHAVIOR_DP` is active).

#### Weight Distribution
If any children specify `WidthModifier.weight > 0`:
1. Measure all unweighted visible children to calculate $\text{childrenWidth} = \sum W_{\text{unweighted}}$.
2. Calculate remaining available space:
   $$\text{availableSpace} = \text{selfWidth} - \text{childrenWidth} - (\text{spacedBy} \times (N_{\text{visible}} - 1))$$
3. For each weighted child:
   $$W_{\text{child}} = \frac{\text{weight}}{\sum \text{weight}} \times \max(0, \text{availableSpace})$$
4. Clamped to any `WidthIn(min, max)` constraints on the child, then re-measured with exact constraints:
   $$\text{measure}(W_{\text{child}}, W_{\text{child}}, H_{\text{child}}, H_{\text{child}})$$

#### Horizontal Positioning Equations
Let $W_{\text{children}}$ be the sum of all measured child widths, $N$ be the number of visible children, and $\text{totalGap} = \text{selfWidth} - \sum W_i$:

| Mode | Initial Offset $X_0$ | Intermediate Gap $G$ |
| :--- | :--- | :--- |
| `START` | $0$ | $\text{spacedBy}$ |
| `END` | $\text{selfWidth} - (W_{\text{children}} + \text{spacedBy} \times (N - 1))$ | $\text{spacedBy}$ |
| `CENTER` | $\frac{\text{selfWidth} - (W_{\text{children}} + \text{spacedBy} \times (N - 1))}{2}$ | $\text{spacedBy}$ |
| `SPACE_BETWEEN` | $0$ | $\frac{\text{totalGap}}{N - 1}$ (or centered if $N = 1$) |
| `SPACE_EVENLY` | $\frac{\text{totalGap}}{N + 1}$ | $\frac{\text{totalGap}}{N + 1}$ |
| `SPACE_AROUND` | $\frac{\text{totalGap}}{2N}$ | $\frac{\text{totalGap}}{N}$ |

#### Vertical Alignment Equations
For each child with height $H_i$:
* `TOP`: $Y_i = 0$
* `CENTER`: $Y_i = \frac{\text{selfHeight} - H_i}{2}$
* `BOTTOM`: $Y_i = \text{selfHeight} - H_i$
* **`AlignByModifier` (Baseline / Guide)**: If children specify `AlignByModifier`, $Y_i$ is offset by:
  $$Y_i = Y_i + (\max(\text{alignBy}) - \text{alignBy}_i)$$

---

### 5.2 ColumnLayout

Arranges children vertically in a single column.

* **Opcode Parameters**:
  * `verticalPositioning`: `TOP (4)`, `CENTER (2)`, `BOTTOM (5)`, `SPACE_BETWEEN (6)`, `SPACE_EVENLY (7)`, `SPACE_AROUND (8)`
  * `horizontalPositioning`: `START (1)`, `CENTER (2)`, `END (3)`
  * `spacedBy`: Fixed gap between children.

* **Algorithms**: Identical to `RowLayout`, with the X and Y axes transposed:
  * Weights apply to `HeightModifier.weight` over vertical available space.
  * Cross-axis alignments apply to X (`START`, `CENTER`, `END`).

---

### 5.3 BoxLayout

Overlays all children on top of each other at the same spatial location.

* **Wrap Sizing**:
  $$\text{wrapWidth} = \max_{i}(W_i) + \text{paddingLeft} + \text{paddingRight}$$
  $$\text{wrapHeight} = \max_{i}(H_i) + \text{paddingTop} + \text{paddingBottom}$$
* **Child Alignment**: Each child is positioned according to horizontal and vertical alignment attributes (`START`, `CENTER`, `END`, `TOP`, `BOTTOM`).

---

### 5.4 FlowLayout

Arranges children horizontally, wrapping to a new line when a child exceeds available width.

* **Cross-Axis Stacking**: Each line's height is defined by $\max_{i \in \text{line}}(H_i)$.
* **Line Justification**: Children on each line are justified according to horizontal positioning rules.
* **Line Spacing**: Successive lines are offset vertically by `lineSpacing`.

---

### 5.5 CollapsibleRowLayout & CollapsibleColumnLayout

Responsive layouts that dynamically drop elements that do not fit in available space.

* **Priority Resolution**:
  1. All children are initially measured with intrinsic/wrap constraints.
  2. If $\sum W_i > \text{availableWidth}$, the manager identifies children with `CollapsiblePriorityModifierOperation`.
  3. Children with the lowest priority value (or rightmost children if priorities are equal) are set to `Visibility.GONE` in the `MeasurePass`.
  4. The layout re-evaluates total dimensions until all remaining visible children fit within constraints.
  5. If space expands on subsequent frames, previously collapsed children are restored to `Visibility.VISIBLE`.

---

### 5.6 FitBoxLayout

A selector container that displays the first single child that fits within the available bounds.

* **Execution Algorithm**:
  1. Evaluates children in declared order.
  2. Measures child $i$ with current constraints.
  3. If child $i$ fits ($W_i \le \text{maxWidth}$ and $H_i \le \text{maxHeight}$):
     * Set child $i$ visibility to `VISIBLE`.
     * Set all other children to `GONE`.
     * Terminate evaluation.
  4. If no child fits, the last child is selected as fallback.

---

### 5.7 StateLayout

A container managing multiple alternative UI states (variants) switchable via a state variable.

* **State Selection**: Tracks an active state index (resolved via an integer or string expression).
* **Visibility Mapping**: The active child container is measured and set to `VISIBLE`; all other state branches are marked `GONE`.
* **Shared Element Transitions**: Components across different state trees sharing the same `mAnimationId` are recognized as shared elements. When switching states, the player animates the element seamlessly from its geometry in the outgoing state to its geometry in the incoming state.

---

### 5.8 CanvasLayout

A layout container that exposes an unconstrained 2D drawing surface.

* **Coordinate Space**: Children are positioned using explicit local coordinates or dynamic matrix expressions rather than automatic linear packing.
* **Measure Contract**: Takes the exact constraints provided by its parent or computes its bounds from explicit dimension modifiers.

---

## 6. The Modifier Pipeline

Modifiers are sequential decorating operations attached to a `LayoutComponent`.

```
Component
  └── ComponentModifiers
        ├── [1] PaddingModifierOperation (left=8, top=8)
        ├── [2] BackgroundModifierOperation (color=#FF0000)
        ├── [3] PaddingModifierOperation (left=16, top=16)
        ├── [4] BorderModifierOperation (width=2, color=#000000)
        └── [5] ClickModifierOperation (id=42)
```

### 6.1 Modifier Evaluation Order

1. **Dimensional Insets (`layout` pass)**:
   * Traversed forward (`0` to `size - 1`).
   * As `PaddingModifierOperation` is encountered:
     $$\text{availableWidth} = \text{availableWidth} - (\text{left} + \text{right})$$
     $$\text{availableHeight} = \text{availableHeight} - (\text{top} + \text{bottom})$$
2. **Paint Pass (`paint` pass)**:
   * Traversed forward.
   * `PaddingModifierOperation`: Translates canvas by `(+left, +top)` and accumulates total translation `(tx, ty)`.
   * `BackgroundModifierOperation`: Draws shape using current translated bounds $(0, 0, w, h)$.
   * After completing modifier painting, the accumulated padding translation is backed out: `canvas.translate(-tx, -ty)`.
3. **Touch Dispatch Pass (`onClick`, `onTouchDown`)**:
   * Traversed in **reverse order** (`size - 1` down to `0`) so inner/later modifiers have hit-test precedence over outer modifiers.

---

### 6.2 Modifier Specifications

#### Sizing Modifiers
* **`WidthModifierOperation` / `HeightModifierOperation`**:
  * `FIXED`: Dimension is set to a constant or dynamic float value.
  * `WRAP`: Dimension is determined by child contents.
  * `FILL`: Takes maximum constraint from parent multiplied by optional fraction $(0.0, 1.0]$.
  * `FILL_PARENT_MAX`: Takes root viewport dimension multiplied by optional fraction.
  * `WEIGHT`: Specifies weight fraction within a linear layout manager.
  * `INTRINSIC_MIN` / `INTRINSIC_MAX`: Queries intrinsic sizing pass.
* **`DimensionConstraintsModifierOperation` (`WidthIn`, `HeightIn`)**:
  * Specifies `min` and `max` clamping limits.

#### Spatial & Transformation Modifiers
* **`PaddingModifierOperation`**:
  * Insets layout box by `(left, top, right, bottom)`. Supports dynamic density scaling.
* **`OffsetModifierOperation`**:
  * Shifts visual rendering position by `(x, y)` expressions without affecting parent layout boundaries.
* **`GraphicsLayerModifierOperation`**:
  * Applies GPU layer attributes: `alpha`, `scaleX`, `scaleY`, `translationX`, `translationY`, `rotationZ`, `transformOrigin`, `cameraDistance`, and `shadowElevation`.
* **`ZIndexModifierOperation`**:
  * Defines float $Z$-ordering. Components with higher $Z$ are painted after and receive touch events before siblings.

#### Visual Modifiers
* **`BackgroundModifierOperation`**:
  * Renders solid color or brush. Supports `ShapeType` (`RECTANGLE`, `ROUNDED_RECTANGLE`, `CIRCLE`).
* **`BorderModifierOperation`**:
  * Renders stroked perimeter with thickness and corner radii. Always drawn on top of content.
* **`ClipRectModifierOperation` / `RoundedClipRectModifierOperation`**:
  * Clamps canvas clip to component bounds or rounded bounds.

#### Behavioral Modifiers
* **`ScrollModifierOperation`**:
  * Attaches a `ScrollDelegate` (horizontal or vertical). Converts wheel, drag, and fling inputs into dynamic scroll offsets with edge resistance.
* **`MarqueeModifierOperation`**:
  * If measured content exceeds component width, automatically runs continuous horizontal scrolling animation with configurable delay, speed, and iteration count.
* **`ComponentVisibilityOperation`**:
  * Dynamically controls visibility:
    * `VISIBLE (0)`: Measured, laid out, painted, and hit-tested.
    * `INVISIBLE (1)`: Measured and occupies layout space; not painted and does not receive touches.
    * `GONE (2)`: Excluded from measurement and layout entirely (zero size).

---

## 7. Animated Layout Transitions (`AnimateMeasure`)

When a component's measured bounds change across frames, the player automatically animates the geometry transition if animations are enabled.

### 7.1 Transition Lifecycle

1. **Trigger Condition**:
   $$\text{mFirstLayout} = \text{false} \land \text{context.isAnimationEnabled()} \land \text{mAnimationSpec.isAnimationEnabled()} \land \text{allowsAnimation}$$
2. **State Capture**:
   * $\text{Origin} = (X_{\text{current}}, Y_{\text{current}}, W_{\text{current}}, H_{\text{current}}, \text{Vis}_{\text{current}})$
   * $\text{Target} = (X_{\text{measured}}, Y_{\text{measured}}, W_{\text{measured}}, H_{\text{measured}}, \text{Vis}_{\text{measured}})$
   * Objects are allocated from and recycled to `ComponentMeasurePool`.
3. **Animation Engine (`AnimateMeasure`)**:
   * If $\text{Origin} \neq \text{Target}$, an `AnimateMeasure` controller is created or retargeted with start timestamp $T_0 = \text{context.currentTime}$.
   * Duration and easing are governed by the component's `AnimationSpec`:
     * Motion duration & motion easing (`CUBIC_STANDARD`, `OVERSHOOT`, `BOUNCE`, etc.).
     * Visibility duration & enter/exit transitions (fade, expand, slide).
4. **Per-Frame Interpolation**:
   During the paint pass, if $T < T_0 + \text{duration}$:
   $$X(t) = \text{interpolate}(X_{\text{origin}}, X_{\text{target}}, \text{easing}(t))$$
   $$Y(t) = \text{interpolate}(Y_{\text{origin}}, Y_{\text{target}}, \text{easing}(t))$$
   $$W(t) = \text{interpolate}(W_{\text{origin}}, W_{\text{target}}, \text{easing}(t))$$
   $$H(t) = \text{interpolate}(H_{\text{origin}}, H_{\text{target}}, \text{easing}(t))$$
   The player marks `needsRepaint = true` to schedule the next animation frame.
5. **Retargeting Mid-Flight**:
   If a new layout pass occurs while an animation is in flight, the current interpolated values become the new $\text{Origin}$, and the transition smoothly redirects to the new $\text{Target}$ without visual popping.

---

## 8. Rendering & Compositing Sequence

A conforming player must execute `LayoutComponent.paintingComponent()` in the following exact sequence:

```
[1. Save Canvas Matrix & Clip]
        |
[2. Translate Canvas by (mX, mY)]
        |
[3. If Visual Debug Active: Draw Component Wireframe Overlay]
        |
[4. If GraphicsLayerModifier Present: Start Graphics Layer Buffer]
        |
[5. Layout Modifiers: ComponentModifiers.layout(context, w, h)]
        |
[6. Paint Modifiers: ComponentModifiers.paint(context)]
        |
[7. Translate Canvas by (PaddingLeft + ScrollX, PaddingTop + ScrollY)]
        |
[8. Apply Pre-Draw Scroll Edge Effects (Overscroll glow/bounce)]
        |
[9. Draw Child Elements]:
    ├── If any children have Z-Index > 0:
    │     Sort visible children ascending by Z-Index; paint each child
    └── Else:
          Paint visible children in declared document order
        |
[10. Apply Post-Draw Scroll Edge Effects]
        |
[11. If GraphicsLayerModifier Present: End & Composite Graphics Layer]
        |
[12. Restore Canvas Matrix & Clip]
```

---

## 9. Conformance Test Requirements

A conforming RemoteCompose player must pass the following test scenarios:

1. **Relayout Boundary Isolation Test**: In a document where a boundary container has exact dimensions (`100x100`), calling `child.invalidateMeasure()` on an internal child must set `child.mNeedsMeasure = true` and register the boundary in `RootLayoutComponent.mDirtyBoundaries`, while `root.mNeedsMeasure` remains `false`.
2. **Zero-Allocation Steady State Test**: During repeated re-layouts of static or animating hierarchies, total object allocations for `ComponentMeasure` after the first warm-up pass must equal `0`.
3. **Generational Token Invalidation Test**: In `FlatMeasurePass`, calling `clear()` must increment `mGeneration` without zeroing or iterating the array; subsequent accesses must reset stale measures in-place.
4. **Constraint Cache Compatibility Test**: If a child container is probed with $(minW, maxW, 0, \infty)$ and subsequently re-probed with $(minW, maxW, computedH, computedH)$, `hasCachedConstraints()` must evaluate to `true` and bypass the measure policy.
5. **Zero & Negative Constraint Handling**: When offered `maxWidth = 0` or negative constraints, dimensions must clamp safely to `0` without negative size exceptions or NaN propagation.
6. **Padding Accumulation Test**: A sequence of `Padding(4) -> Background -> Padding(8) -> Content` must position content at offset `(12, 12)` while the background is drawn at offset `(4, 4)`.
7. **Weight Normalization**: If two weighted children have weights `1.0` and `3.0` in a `RowLayout` of width `400`, their measured widths must resolve to `100.0` and `300.0` respectively.
8. **Z-Index Hit Precedence**: A child with `Z-Index = 1.0` declared *before* a sibling with `Z-Index = 0.0` must be rendered on top of the sibling and must intercept touch events first.
9. **Collapsible Priority Drop Order**: A `CollapsibleRow` under constrained width must drop low-priority items before higher-priority items regardless of document declaration order.
10. **Retargeted Animation Continuity**: When target dimensions change while an `AnimateMeasure` transition is at $50\%$, the position curve must remain $C^0$ continuous with no instantaneous jump.

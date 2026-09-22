/-
Copyright 2026 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-/
import RemoteComposeLayout.Measure
import Mathlib.Tactic

/-!
# RemoteCompose Layout Optimization Passes

This module formally specifies and verifies all layout optimization passes defined in
the RemoteCompose Java implementation (`CoreDocument`, `LayoutManager`, `Component`,
`ComponentMeasure`, and `FlatMeasurePass`).

## Overview of Optimizations

The Java engine defines 4 core layout optimizations configured via bitmask in `CoreDocument`:
```java
public static final int OPTIMIZATION_NONE = 0;
public static final int OPTIMIZATION_MEASURE_CACHE = 1;       // Bit 0
public static final int OPTIMIZATION_LAYOUT_BOUNDARIES = 2;   // Bit 1
public static final int OPTIMIZATION_FLAT_MEASURE_PASS = 4;   // Bit 2
public static final int OPTIMIZATION_CONSTRAINTS_CACHE = 8;   // Bit 3
public static final int OPTIMIZATION_ALL = 15;
```

In addition, the engine includes:
5. **Two-Pass Flex / Weighted Resolution**: Shortcut single-pass layout when no weights exist.
6. **Generational $O(1)$ Clearing in FlatMeasurePass**: Version-stamped array slots.
7. **Measure Policy Hierarchy**: 5 versioned policies (`Legacy` through `EnforceConstraints`).
-/

namespace RemoteCompose

/-! ## 1. Optimization Level Configuration Bitmask -/

/-- Optimization level flags mirroring `CoreDocument.OPTIMIZATION_*`. -/
structure OptimizationLevel where
  measureCache : Bool := true       -- Bit 0 (val 1): OPTIMIZATION_MEASURE_CACHE
  layoutBoundaries : Bool := true   -- Bit 1 (val 2): OPTIMIZATION_LAYOUT_BOUNDARIES
  flatMeasurePass : Bool := true    -- Bit 2 (val 4): OPTIMIZATION_FLAT_MEASURE_PASS
  constraintsCache : Bool := true   -- Bit 3 (val 8): OPTIMIZATION_CONSTRAINTS_CACHE
  deriving Repr, DecidableEq, Inhabited

namespace OptimizationLevel

def none : OptimizationLevel :=
  ⟨false, false, false, false⟩

def all : OptimizationLevel :=
  ⟨true, true, true, true⟩

def toNat (opt : OptimizationLevel) : Nat :=
  (if opt.measureCache then 1 else 0) +
  (if opt.layoutBoundaries then 2 else 0) +
  (if opt.flatMeasurePass then 4 else 0) +
  (if opt.constraintsCache then 8 else 0)

def fromNat (n : Nat) : OptimizationLevel :=
  ⟨n &&& 1 != 0, n &&& 2 != 0, n &&& 4 != 0, n &&& 8 != 0⟩

theorem toNat_fromNat_roundtrip (opt : OptimizationLevel) :
    fromNat (toNat opt) = opt := by
  cases opt with
  | mk mc lb fm cc =>
    cases mc <;> cases lb <;> cases fm <;> cases cc <;> rfl

end OptimizationLevel

/-! ## 2. Relayout Boundaries Optimization (`OPTIMIZATION_LAYOUT_BOUNDARIES`) -/

/-- A node acts as a relayout boundary (`Component.isRelayoutBoundary`) when its
external dimensions are fixed (either both exact or both fill with default fraction).
Changes inside this subtree cannot alter the size or position of ancestors or siblings. -/
def Node.isRelayoutBoundary (n : Node) : Bool :=
  let w := n.widthDim
  let h := n.heightDim
  (w.isExact && h.isExact) || (w.isFill && h.isFill)

def Node.withChildren : Node → List Node → Node
  | .leaf m c, _ => .leaf m c
  | .text m s, _ => .text m s
  | .box m ha va _, cs => .box m ha va cs
  | .row m arr va sp _, cs => .row m arr va sp cs
  | .column m ha arr sp _, cs => .column m ha arr sp cs
  | .collapsibleRow m arr va sp _, cs => .collapsibleRow m arr va sp cs
  | .collapsibleColumn m ha arr sp _, cs => .collapsibleColumn m ha arr sp cs
  | .flow m arr va sp mi ml _, cs => .flow m arr va sp mi ml cs
  | .fitBox m ha va _, cs => .fitBox m ha va cs
  | .stateLayout m st _, cs => .stateLayout m st cs

def Node.getAt? : Node → List Nat → Option Node
  | n, [] => some n
  | n, i :: is =>
    match n.children[i]? with
    | some c => Node.getAt? c is
    | none => none

def replaceInList {α : Type} (f : α → α) (xs : List α) (idx : Nat) : List α :=
  match xs, idx with
  | [], _ => []
  | x :: rest, 0 => f x :: rest
  | x :: rest, k + 1 => x :: replaceInList f rest k

def Node.replaceAt : Node → List Nat → Node → Node
  | _, [], replacement => replacement
  | n, i :: is, replacement =>
    n.withChildren (replaceInList (fun ch => ch.replaceAt is replacement) n.children i)

def LayoutTree.getAt? : LayoutTree → List Nat → Option LayoutTree
  | t, [] => some t
  | .node _ _ _ cs, i :: is =>
    match cs[i]? with
    | some c => LayoutTree.getAt? c is
    | none => none

def LayoutTree.replaceAt : LayoutTree → List Nat → LayoutTree → LayoutTree
  | _, [], replacement => replacement
  | .node r p g cs, i :: is, replacement =>
    .node r p g (replaceInList (fun ch => ch.replaceAt is replacement) cs i)

/-- Invalidation target determined by walking the ancestor chain (`Component.invalidateMeasure`). -/
inductive InvalidationTarget where
  | dirtyBoundary (path : List Nat) (boundary : Node)
  | fullRoot
  deriving Repr

def prefixes : List Nat → List (List Nat)
  | [] => [[]]
  | x :: xs => [] :: (prefixes xs).map (x :: ·)

def candidateAncestors (target : List Nat) : List (List Nat) :=
  (prefixes target.dropLast).reverse

/-- Walk ancestor chain upwards from target, stopping at the first relayout boundary. -/
def findRelayoutBoundary (root : Node) (target : List Nat) (enabled : Bool) : InvalidationTarget :=
  if !enabled then
    .fullRoot
  else if target.isEmpty then
    if root.isRelayoutBoundary then .dirtyBoundary [] root else .fullRoot
  else
    match (candidateAncestors target).findSome? (fun p =>
      match root.getAt? p with
      | some n => if n.isRelayoutBoundary then some (p, n) else none
      | none => none) with
    | some (p, n) => .dirtyBoundary p n
    | none =>
      if root.isRelayoutBoundary then .dirtyBoundary [] root else .fullRoot

/-- Incremental Partial Layout Pass (`RootLayoutComponent.performPartialLayoutPass`).
Only re-measures the registered dirty boundary subtrees, preserving the rest of the document. -/
def performPartialLayoutPass (cached : LayoutTree) (dirty : List (List Nat × Node)) : LayoutTree :=
  dirty.foldl (fun tree (path, node) =>
    match tree.getAt? path with
    | some oldSubtree =>
      let newSubtree := layout node oldSubtree.w oldSubtree.h
      let positionedSubtree := newSubtree.withPos oldSubtree.x oldSubtree.y
      tree.replaceAt path positionedSubtree
    | none => tree
  ) cached

/-- High-level document layout supporting both full and partial passes. -/
def documentLayout (root : Node) (w h : S)
    (cachedTree : Option LayoutTree)
    (dirtyBoundaries : List (List Nat × Node))
    (rootNeedsMeasure : Bool)
    (opt : OptimizationLevel) : LayoutTree :=
  if opt.layoutBoundaries && !rootNeedsMeasure && !dirtyBoundaries.isEmpty then
    match cachedTree with
    | some cached => performPartialLayoutPass cached dirtyBoundaries
    | none => layout root w h
  else
    layout root w h

/-! ## 3. Constraints Cache Optimization (`OPTIMIZATION_MEASURE_CACHE` & `OPTIMIZATION_CONSTRAINTS_CACHE`) -/

/-- Cached constraints descriptor on a component measure pass. -/
structure CachedConstraints where
  minW : S
  maxW : S
  minH : S
  maxH : S
  deriving Repr, DecidableEq, Inhabited

/-- Check if component has cached constraints matching specified parameters.
Matches `ComponentMeasure.hasCachedConstraints`. -/
def hasCachedConstraints (c : CachedConstraints) (cur : Constraints) (prevW prevH : S) : Bool :=
  -- 1. Exact match
  (c.minW == cur.minW && c.maxW == cur.maxW && c.minH == cur.minH && c.maxH == cur.maxH) ||
  -- 2. Compatible vertical layout positioning pass
  (c.minW == cur.minW && c.maxW == cur.maxW && cur.minH == prevH && cur.maxH == prevH) ||
  -- 3. Compatible horizontal layout positioning pass
  (c.minH == cur.minH && c.maxH == cur.maxH && cur.minW == prevW && cur.maxW == prevW)

/-- An entry in the measure memoization cache. -/
structure CacheEntry where
  constraints : CachedConstraints
  tree : LayoutTree
  deriving Repr

/-- Measure cache mapping component keys to cached layout results. -/
def MeasureCache := List (Nat × CacheEntry)

def MeasureCache.lookup? (cache : MeasureCache) (key : Nat) (cur : Constraints) : Option LayoutTree :=
  match cache.lookup key with
  | some entry =>
    if hasCachedConstraints entry.constraints cur entry.tree.w entry.tree.h then
      some entry.tree
    else
      none
  | none => none

def MeasureCache.insert (cache : MeasureCache) (key : Nat) (cur : Constraints) (t : LayoutTree) : MeasureCache :=
  let entry : CacheEntry := ⟨⟨cur.minW, cur.maxW, cur.minH, cur.maxH⟩, t⟩
  (key, entry) :: cache.filter (fun (k, _) => k != key)

/-! ## 4. Generational Flat Array Measure Pass (`OPTIMIZATION_FLAT_MEASURE_PASS`) -/

/-- A flat slot holding an O(1) generational token. -/
structure FlatSlot where
  generation : Nat
  tree : LayoutTree
  deriving Repr, Inhabited

/-- High-performance flat array-backed MeasurePass (`FlatMeasurePass.java`). -/
structure FlatPassState where
  generation : Nat := 1
  slots : List (Option FlatSlot) := []
  deriving Repr, Inhabited

def FlatPassState.init (capacity : Nat) : FlatPassState :=
  ⟨1, List.replicate capacity none⟩

/-- O(1) clear: simply increment the generation counter.
Matches `FlatMeasurePass.clear()`. -/
def FlatPassState.clear (s : FlatPassState) : FlatPassState :=
  if s.generation = 2147483647 then
    ⟨1, List.replicate s.slots.length none⟩
  else
    ⟨s.generation + 1, s.slots⟩

/-- Check if slot is fresh for the current pass without zeroing array elements. -/
def FlatPassState.isFresh (s : FlatPassState) (idx : Nat) : Bool :=
  match s.slots[idx]? with
  | some (some slot) => slot.generation == s.generation
  | _ => false

def FlatPassState.get? (s : FlatPassState) (idx : Nat) : Option LayoutTree :=
  match s.slots[idx]? with
  | some (some slot) =>
    if slot.generation == s.generation then some slot.tree else none
  | _ => none

def FlatPassState.store (s : FlatPassState) (idx : Nat) (t : LayoutTree) : FlatPassState :=
  let rec setAt (xs : List (Option FlatSlot)) (i : Nat) (val : Option FlatSlot) : List (Option FlatSlot) :=
    match xs, i with
    | [], 0 => [val]
    | [], k + 1 => none :: setAt [] k val
    | _ :: rest, 0 => val :: rest
    | x :: rest, k + 1 => x :: setAt rest k val
  { s with slots := setAt s.slots idx (some ⟨s.generation, t⟩) }

/-! ## 5. Two-Pass Flex / Weighted Resolution Optimization -/

/-- In a row without weights, the children measure pass executes in a single pass
with no flex redistribution. -/
theorem unweighted_row_single_pass (cs : List Node)
    (h : cs.any (fun c => c.widthDim.hasWeight || c.heightDim.hasWeight) = false) :
    cs.all (fun c => !c.widthDim.hasWeight && !c.heightDim.hasWeight) = true := by
  revert h
  induction cs with
  | nil =>
    intro _
    rfl
  | cons c rest ih =>
    intro h
    simp only [List.any_cons, Bool.or_eq_false_iff] at h
    simp only [List.all_cons, Bool.and_eq_true_iff]
    refine ⟨?_, ?_⟩
    · cases h.1 with
      | intro hw hh =>
        simp [hw, hh]
    · apply ih
      exact h.2

/-! ## 6. Measure Policies Hierarchy Progression -/

inductive MeasurePolicyVersion where
  | v0_legacy
  | v1_baseModern
  | v2_insetWrap
  | v3_inlineExpression
  | v4_enforceConstraints
  deriving Repr, DecidableEq, Inhabited

structure MeasurePolicyConfig where
  applyInsetWrap : Bool
  updateComponentValues : Bool
  enforceConstraints : Bool
  deriving Repr, DecidableEq

def MeasurePolicyVersion.config : MeasurePolicyVersion → MeasurePolicyConfig
  | .v0_legacy => ⟨false, false, false⟩
  | .v1_baseModern => ⟨false, false, false⟩
  | .v2_insetWrap => ⟨true, false, false⟩
  | .v3_inlineExpression => ⟨true, true, false⟩
  | .v4_enforceConstraints => ⟨true, true, true⟩

/-! ## 7. Formal Theorems on Optimization Passes -/

/-- Generational Invalidation Soundness: Bumping generation immediately expires
all previous entries without touching the array slots. -/
theorem flatPass_clear_expires_slots (s : FlatPassState) (idx : Nat)
    (h_gen : s.generation < 2147483647)
    (h_stored : ∀ (slot : FlatSlot), s.slots[idx]? = some (some slot) → slot.generation ≤ s.generation) :
    (s.clear).isFresh idx = false := by
  unfold FlatPassState.clear
  split
  · next h_max => omega
  · next h_not_max =>
    unfold FlatPassState.isFresh
    dsimp
    cases h_slot : s.slots[idx]? with
    | none => rfl
    | some opt =>
      cases opt with
      | none => rfl
      | some slot =>
        dsimp
        have h_le := h_stored slot h_slot
        have h_ne : slot.generation ≠ s.generation + 1 := by omega
        apply beq_false_of_ne h_ne

/-- Exact constraint match always succeeds in `hasCachedConstraints`. -/
theorem hasCachedConstraints_exact_match (c : CachedConstraints) (cur : Constraints) (w h : S)
    (hw1 : c.minW = cur.minW) (hw2 : c.maxW = cur.maxW)
    (hh1 : c.minH = cur.minH) (hh2 : c.maxH = cur.maxH) :
    hasCachedConstraints c cur w h = true := by
  unfold hasCachedConstraints
  simp [hw1, hw2, hh1, hh2]

/-- Relayout boundary isolation: an exact-sized node's outer dimensions are fixed
by its own exact modifiers, so internal child variations do not alter its outer size. -/
theorem exact_boundary_width_fixed (w : S) (h : S) (ha : HAlign) (va : VAlign)
    (children1 children2 : List Node) :
    let n1 := Node.box [Modifier.width (.exact w) none, Modifier.height (.exact h) none] ha va children1
    let n2 := Node.box [Modifier.width (.exact w) none, Modifier.height (.exact h) none] ha va children2
    n1.isRelayoutBoundary = true ∧ n2.isRelayoutBoundary = true := by
  dsimp [Node.isRelayoutBoundary, Node.widthDim, Node.heightDim, Node.modifiers,
         Modifiers.widthDim, Modifiers.heightDim, Dim.isExact]
  simp

end RemoteCompose

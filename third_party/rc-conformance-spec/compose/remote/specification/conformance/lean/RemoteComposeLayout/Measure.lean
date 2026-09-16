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
import RemoteComposeLayout.Node

/-!
# RemoteCompose layout specification — the measure algorithm

This file is a line-by-line model of the RemoteCompose measure pass as it runs
under the **default** measure policy, `EnforceConstraintsMeasurePolicy`
(`LayoutManager.DEFAULT_MEASURE_TYPE = ENFORCE_CONSTRAINTS`, version 4).

That policy is the bottom of an inheritance chain

```
EnforceConstraintsMeasurePolicy      -- shouldEnforceConstraints  = true
  extends InlineExpressionMeasurePolicy   -- shouldUpdateComponentValues = true
  extends InsetWrapMeasurePolicy          -- shouldApplyInsetWrap    = true
  extends BaseModernMeasurePolicy         -- the algorithm itself
```

so the algorithm modelled here is `BaseModernMeasurePolicy.measure` with all
three feature flags enabled.

## Shape of the pass

`measure` is a single top-down recursion. For each node it

1. resolves the node's *own* size from its modifiers and the incoming
   constraints (`§ resolveAxis`),
2. measures the children, through exactly one of two hooks:
   * `computeWrapSize`, when the node wraps its content on either axis, or
   * `computeSize`, otherwise,
3. finalises its own size (clamping, `widthIn`/`heightIn`),
4. positions the children (`internalLayoutMeasure`).

Step 2 is the only place where the child constraints are decided, and steps 2
and 4 are the only kind-specific parts: `Box`, `Row` and `Column` differ purely
in `computeWrapSize` / `computeSize` / `internalLayoutMeasure`.
-/

namespace RemoteCompose

/-! ## Axis resolution

The horizontal half of `BaseModernMeasurePolicy.measure`, lines 90–121. The
vertical half is the mirror image, so both are handled by the single function
below, applied once per axis.

`resolveAxis` returns the tuple the reference implementation carries in local
variables: the running measured size, the (possibly rewritten) minimum
constraint, the inset maximum handed to the children, and whether this axis
wraps its content. -/

/-- One axis of the size-resolution step.

Arguments mirror the reference implementation's locals:
* `dim` — the axis' `DimensionModifierOperation`;
* `inFill` — `isInHorizontalFill()` / `isInVerticalFill()`, which a `Row`/
  `Column` reports as `true` when its children carry weights;
* `pad` — total padding on this axis;
* `defined` — `computeModifierDefinedWidth(context)`, uncapped;
* `measured0` — `Math.min(maxWidth, defined)`, computed against the *incoming*
  maximum, before the `widthIn` / `heightIn` rewrite;
* `minC`, `maxC` — the constraints after the `widthIn` / `heightIn` rewrite;
* `insetMax0` — `maxC - pad`.

Returns `(measured, min', insetMax, wraps)`. -/
def resolveAxis (dim : Dim) (inFill : Bool)
    (pad defined measured0 minC maxC insetMax0 : S) : S × S × S × Bool :=
  if inFill then
    -- `if (layout.isInHorizontalFill())`, lines 90–98.
    match dim.value with
    | none =>
      -- `Float.isNaN(fraction)`: fill the whole slot.
      (maxC, insetMax0, insetMax0, false)
    | some f =>
      if dim.isExact then
        -- A `Row` whose own width is EXACT but whose children carry weights
        -- reaches this branch: `isExact()` wins and the row fills its slot.
        (maxC, insetMax0, insetMax0, false)
      else
        let measured := maxC * f
        (measured, measured - pad, insetMax0, false)
  else if dim.hasWeight then
    -- `else if (layout.getWidthModifier().hasWeight())`, lines 107–109.
    -- Only provisional: the real size is imposed by the parent afterwards,
    -- through the tight constraints of `rowResolveWeights`.
    (max measured0 defined, minC, insetMax0, false)
  else
    -- `else`, lines 110–121: clamp, then decide whether we wrap.
    let measured := min (max measured0 minC) maxC
    let wraps := dim.isWrap
    -- `shouldApplyInsetWrap()` is true for the default policy: when the axis
    -- does *not* wrap, children are capped by the resolved size rather than by
    -- the incoming maximum.
    let insetMax := if wraps then insetMax0 else measured - pad
    (measured, minC, insetMax, wraps)

/-! ## Main-axis arrangement

`RowLayout.internalLayoutMeasure` lines 465–512 (and the `ColumnLayout` mirror).

`childrenMain` is the total extent of the children *including* `spacedBy`;
`total` is the same sum *excluding* `spacedBy`. The reference implementation
really does use the `spacedBy`-free `total` for the `SPACE_*` gaps while still
adding `spacedBy` between children afterwards, so a row that mixes `spacedBy`
with a `SPACE_*` arrangement overflows by `spacedBy * (n - 1)`. That quirk is
reproduced faithfully. -/

/-- Returns `(offset of the first child, extra gap inserted between children)`. -/
def arrange (a : Arrangement) (selfMain childrenMain total : S) (n : Nat) : S × S :=
  match a with
  | .Start => (0, 0)
  | .End => (selfMain - childrenMain, 0)
  | .Center => ((selfMain - childrenMain) / 2, 0)
  | .SpaceBetween =>
    if 1 < n then (0, (selfMain - total) / ((n : S) - 1))
    -- "we center the element"
    else ((selfMain - childrenMain) / 2, 0)
  | .SpaceEvenly =>
    let g := (selfMain - total) / ((n : S) + 1)
    (g, g)
  | .SpaceAround =>
    let g := (selfMain - total) / (n : S)
    (g / 2, g)

/-- Cross-axis offset of a child of a `Row`, `RowLayout.internalLayoutMeasure`
lines 524–545 (with `alignBy` out of scope). -/
def crossY (va : VAlign) (selfH childH : S) : S :=
  match va with
  | .Top => 0
  | .Center => (selfH - childH) / 2
  | .Bottom => selfH - childH

/-- Cross-axis offset of a child of a `Column`, and the horizontal offset of a
child of a `Box`. -/
def crossX (ha : HAlign) (selfW childW : S) : S :=
  match ha with
  | .Start => 0
  | .Center => (selfW - childW) / 2
  | .End => selfW - childW

/-! ### Pure placement loops

These walk the already-measured children and assign offsets. They are the tail
of `internalLayoutMeasure` and involve no re-measurement. -/

/-- Lay children out left to right, `RowLayout.internalLayoutMeasure`
lines 514–558.

A child that is gone is still assigned a position — the reference implementation
calls `setX` / `setY` *before* testing `isGone`, and only the cursor advance is
skipped. A gone child therefore reports the coordinates that the next visible
child will occupy, and the gold dumps record exactly that. -/
def rowPlace (ts : List LayoutTree) (va : VAlign) (selfH spacedBy gap : S)
    (spaced : Bool) (tx : S) : List LayoutTree :=
  match ts with
  | [] => []
  | t :: rest =>
    let t' := t.withPos tx (crossY va selfH t.h)
    let tx' :=
      if t.gone then tx
      else tx + t.w + (if spaced then gap else 0) + spacedBy
    t' :: rowPlace rest va selfH spacedBy gap spaced tx'

/-- Lay children out top to bottom, `ColumnLayout.internalLayoutMeasure`. -/
def colPlace (ts : List LayoutTree) (ha : HAlign) (selfW spacedBy gap : S)
    (spaced : Bool) (ty : S) : List LayoutTree :=
  match ts with
  | [] => []
  | t :: rest =>
    let t' := t.withPos (crossX ha selfW t.w) ty
    let ty' :=
      if t.gone then ty
      else ty + t.h + (if spaced then gap else 0) + spacedBy
    t' :: colPlace rest ha selfW spacedBy gap spaced ty'

/-- Stack children on top of one another, `BoxLayout.internalLayoutMeasure`
lines 158–194. -/
def boxPlace (ts : List LayoutTree) (ha : HAlign) (va : VAlign) (selfW selfH : S) :
    List LayoutTree :=
  ts.map fun t => t.withPos (crossX ha selfW t.w) (crossY va selfH t.h)

/-! ## Collapsing

The second phase of `CollapsibleRowLayout.computeVisibleChildren`, lines
290–325, and its `ColumnLayout` mirror. It re-orders the already-measured
children by priority, then walks them greedily, dropping every child that does
not fit — and, once one has been dropped, every child after it in that order.

Nothing here re-enters `measure`: the phase works purely on sizes the first
phase already established, which is why it lives outside the recursion. -/

/-- The greedy sweep, in priority order. Returns a decision per index.

Three things are worth noting against the reference implementation:

* `overflow` is **sticky** — once a child has been refused, every later child in
  priority order is refused too, whatever its size;
* a child that arrived already gone is skipped *without* setting `overflow`, so
  it costs its successors nothing;
* the fit test compares against the running total of the survivors only and
  takes **no account of `spacedBy`**, so a spaced collapsible layout admits one
  child too many and overflows by up to `spacedBy * (k - 1)`. -/
def collapseSweep (horiz : Bool) (budget : S) :
    List (Nat × LayoutTree) → S → Bool → List (Nat × Bool)
  | [], _, _ => []
  | (i, t) :: rest, acc, ovf =>
    if ovf || t.gone then
      (i, true) :: collapseSweep horiz budget rest acc ovf
    else
      let ext := if horiz then t.w else t.h
      if acc + ext > budget then
        (i, true) :: collapseSweep horiz budget rest acc true
      else
        (i, false) :: collapseSweep horiz budget rest (acc + ext) false

/-- The decision recorded for index `i`. -/
def lookupFlag (rs : List (Nat × Bool)) (i : Nat) : Bool :=
  match rs.find? (fun p => p.1 == i) with
  | some (_, b) => b
  | none => false

/-- Decide which children survive, in declaration order.

The children are sorted by priority only when at least one of them declares a
priority modifier, exactly as the `hasPriorities` flag gates
`sortWithPriorities`. -/
def collapseFlags (o : Orient) (horiz : Bool) (budget : S)
    (cs : List Node) (ts : List LayoutTree) : List Bool :=
  let idx := (List.range ts.length).zip ts
  let paired := (List.range cs.length).zip cs
  let order :=
    if hasAnyPriority cs then
      -- Sort the indexed children, then read the measured child back by index.
      (sortWithPriorities o (fun p => p.2) paired).filterMap fun (i, _) =>
        (idx.find? (fun q => q.1 == i))
    else idx
  let decided := collapseSweep horiz budget order 0 false
  (List.range ts.length).map (lookupFlag decided)

/-- Apply a decision list to the measured children. -/
def applyFlags : List LayoutTree → List Bool → List LayoutTree
  | [], _ => []
  | t :: ts, [] => t :: applyFlags ts []
  | t :: ts, g :: gs => t.withGone (t.gone || g) :: applyFlags ts gs


/-! ## Intrinsic sizing

`Component.minIntrinsicWidth` / `minIntrinsicHeight` and their overrides. These
never consult a measurement, only the modifier chain and the children, so they
form their own recursion independent of `measure`. `FlowLayout` is the only
manager in scope that consumes them, to height the rows it has segmented.

The `isMin = true` argument to `computeModifierDefined…` means a `FILL` axis
contributes `0` rather than `Ω`. -/

mutual

/-- `minIntrinsicWidth`.

`RowLayout` and `FlowLayout` sum their children; `LayoutManager` — and so
`BoxLayout` and `ColumnLayout`, neither of which overrides it — takes the
maximum; a collapsible manager adds the single child it would keep longest. -/
def minIntrinsicWidth : Node → S
  | .leaf m sz => max (m.definedWidth true) sz.w
  | .text m spec => max (m.definedWidth true) spec.nominalSize.w
  | .box m _ _ cs => max (m.definedWidth true) (maxMinIW cs)
  | .column m _ _ _ cs => max (m.definedWidth true) (maxMinIW cs)
  | .row m _ _ _ cs => max (m.definedWidth true) (sumMinIW cs)
  | .flow m _ _ _ _ _ cs => max (m.definedWidth true) (sumMinIW cs)
  | .collapsibleRow m _ _ _ cs =>
    m.definedWidth true + (lastStandingIW .horizontal cs).getD 0
  | .collapsibleColumn m _ _ _ cs =>
    m.definedWidth true + (lastStandingIW .vertical cs).getD 0

/-- `minIntrinsicHeight`. `ColumnLayout` sums; everything else takes a maximum,
except the collapsible managers, which again add a single child. -/
def minIntrinsicHeight : Node → S
  | .leaf m sz => max (m.definedHeight true) sz.h
  | .text m spec => max (m.definedHeight true) spec.nominalSize.h
  | .box m _ _ cs => max (m.definedHeight true) (maxMinIH cs)
  | .row m _ _ _ cs => max (m.definedHeight true) (maxMinIH cs)
  | .flow m _ _ _ _ _ cs => max (m.definedHeight true) (maxMinIH cs)
  | .column m _ _ _ cs => max (m.definedHeight true) (sumMinIH cs)
  | .collapsibleRow m _ _ _ cs =>
    m.definedHeight true + (lastStandingIH .horizontal cs).getD 0
  | .collapsibleColumn m _ _ _ cs =>
    m.definedHeight true + (lastStandingIH .vertical cs).getD 0

/-- `Σ c.minIntrinsicWidth`. -/
def sumMinIW : List Node → S
  | [] => 0
  | c :: cs => minIntrinsicWidth c + sumMinIW cs

/-- `max c.minIntrinsicWidth`, from `0`. -/
def maxMinIW : List Node → S
  | [] => 0
  | c :: cs => max (minIntrinsicWidth c) (maxMinIW cs)

/-- `Σ c.minIntrinsicHeight`. -/
def sumMinIH : List Node → S
  | [] => 0
  | c :: cs => minIntrinsicHeight c + sumMinIH cs

/-- `max c.minIntrinsicHeight`, from `0`. -/
def maxMinIH : List Node → S
  | [] => 0
  | c :: cs => max (minIntrinsicHeight c) (maxMinIH cs)

/-- The intrinsic width of `CollapsiblePriority.findLastStanding`.

Written as a fold over the whole list rather than a lookup followed by a
recursive call, so that the recursion stays structural. Ties go to the earliest
child, matching the strict `>` in the reference implementation's scan. -/
def lastStandingIW (o : Orient) : List Node → Option S
  | [] => none
  | c :: cs =>
    let pc := Modifiers.priority o c.modifiers
    match lastStandingIW o cs, lastStandingPri o cs with
    | some vr, some pr => if pr > pc then some vr else some (minIntrinsicWidth c)
    | _, _ => some (minIntrinsicWidth c)

/-- The intrinsic height of `findLastStanding`. -/
def lastStandingIH (o : Orient) : List Node → Option S
  | [] => none
  | c :: cs =>
    let pc := Modifiers.priority o c.modifiers
    match lastStandingIH o cs, lastStandingPri o cs with
    | some vr, some pr => if pr > pc then some vr else some (minIntrinsicHeight c)
    | _, _ => some (minIntrinsicHeight c)

/-- The priority of `findLastStanding`. -/
def lastStandingPri (o : Orient) : List Node → Option S
  | [] => none
  | c :: cs =>
    let pc := Modifiers.priority o c.modifiers
    match lastStandingPri o cs with
    | some pr => some (if pr > pc then pr else pc)
    | none => some pc

end

/-! ## Flow segmentation

`FlowLayout.segmentComponents`, lines 210–288. Given the extent each child would
take, it walks them in declaration order and cuts a new row whenever the current
one is full, refusing children outright once no further row may be opened.

Segmentation is run afresh by each of the three entry points, and it consults
only sizes that have already been established, so — like the collapsing sweep —
it is a pure function of the measured children. What it produces here is, per
child, the index of the row it joined, or `none` if it was refused. -/

/-- The `(width, height)` `segmentComponents` attributes to each child, lines
231–253.

A child that is already gone counts as empty but is still placed in a row; a
child carrying a weight is *not* measured at all, contributing its `widthIn`
minimum and its intrinsic height. -/
def flowDims : List Node → List LayoutTree → List (S × S × Bool)
  | [], _ => []
  | _, [] => []
  | c :: cs, t :: ts =>
    let d : S × S × Bool :=
      if t.gone then (0, 0, true)
      else if c.widthDim.hasWeight then
        ((c.modifiers.widthIn.bind (·.min)).getD 0, minIntrinsicHeight c, false)
      else (t.w, t.h, false)
    d :: flowDims cs ts

/-- The greedy row assignment, lines 225–277.

`rows` counts the rows opened so far, starting at `1` because the reference
implementation adds an empty row before the walk begins — which is what makes
`maxLines` bite one row earlier than one might expect. `spacedBy` is added to the
running width after **every** child, including the last one in a row, so a spaced
flow breaks one item earlier than the arithmetic suggests. -/
def flowSegment (spacedBy maxW maxH : S) (maxItems maxLines : Nat)
    (ds : List (S × S × Bool)) (curW rowH totH : S) (rows cnt : Nat) (ovf : Bool) :
    List (Option Nat) :=
  match ds with
  | [] => []
  | (cw, ch, _) :: rest =>
    if ovf then
      none :: flowSegment spacedBy maxW maxH maxItems maxLines rest curW rowH totH
        rows cnt true
    else
      -- Line 255: the current row cannot take this child.
      let brk := cw + curW > maxW || cnt ≥ maxItems
      -- Line 257: nor may another row be opened for it.
      if brk && (rows ≥ maxLines || totH + rowH ≥ maxH) then
        none :: flowSegment spacedBy maxW maxH maxItems maxLines rest curW rowH totH
          rows cnt true
      else
        let curW' := if brk then 0 else curW
        let rowH' := if brk then 0 else rowH
        let totH' := if brk then totH + rowH else totH
        let rows' := if brk then rows + 1 else rows
        let cnt' := if brk then 0 else cnt
        some (rows' - 1) ::
          flowSegment spacedBy maxW maxH maxItems maxLines rest (curW' + cw + spacedBy)
            (max rowH' ch) totH' rows' (cnt' + 1) false
termination_by ds

/-- How many rows the segmentation opened. At least one, always. -/
def flowRowCount (ids : List (Option Nat)) : Nat :=
  ids.foldl (fun n o => match o with | some r => max n (r + 1) | none => n) 1

/-- The row indices, in order. -/
def flowRowList (ids : List (Option Nat)) : List Nat := List.range (flowRowCount ids)

/-- How many children joined row `r`. -/
def flowRowMembers (r : Nat) : List (Option Nat) → Nat
  | [] => 0
  | o :: os => (if o == some r then 1 else 0) + flowRowMembers r os

/-- Hide every child that is not a member of row `r`.

This is how a row is handed to the inherited `RowLayout` machinery: rather than
extracting a sublist — which would take the recursion off the structure Lean can
see decreasing — the whole child list is passed with the other rows marked gone,
which every accumulator in `RowLayout` already skips. -/
def maskRow : List LayoutTree → List (Option Nat) → Nat → List LayoutTree
  | [], _, _ => []
  | t :: ts, [], r => t.withGone true :: maskRow ts [] r
  | t :: ts, o :: os, r => t.withGone (t.gone || o != some r) :: maskRow ts os r

/-- Take the members of row `r` out of a row placement, shifted down by `dy`,
and leave every other child as it was. -/
def mergeRow (dy : S) (r : Nat) :
    List LayoutTree → List LayoutTree → List (Option Nat) → List LayoutTree
  | [], _, _ => []
  | a :: as, p :: ps, o :: os =>
    (if o == some r then p.withPos p.x (p.y + dy) else a) :: mergeRow dy r as ps os
  | as, _, _ => as

/-- `minIntrinsicHeight(context, row, true)` for row `r`, the height
`FlowLayout.internalLayoutMeasure` gives that row.

Note that the modifier-defined height folded in is the **flow's own**, not any
child's: the protected `RowLayout.minIntrinsicHeight` is called on `this`. -/
def flowRowIntrinsic (ownH : S) (r : Nat) : List Node → List (Option Nat) → S
  | [], _ => ownH
  | _, [] => ownH
  | c :: cs, o :: os =>
    let rest := flowRowIntrinsic ownH r cs os
    if o == some r then max (minIntrinsicHeight c) rest else rest

/-- `Σ minIntrinsicHeight(context, row, true)`, the stack height the vertical
positioning is resolved against. -/
def flowRowsHeight (ownH : S) (cs : List Node) (ids : List (Option Nat)) : List Nat → S
  | [] => 0
  | r :: rs => flowRowIntrinsic ownH r cs ids + flowRowsHeight ownH cs ids rs

/-- Whether any member of row `r` carries a horizontal weight. -/
def flowRowHasWeight (r : Nat) : List Node → List (Option Nat) → Bool
  | [], _ => false
  | _, [] => false
  | c :: cs, o :: os =>
    (o == some r && c.widthDim.hasWeight) || flowRowHasWeight r cs os

/-- The total horizontal weight of row `r`. -/
def flowRowWeights (r : Nat) : List Node → List (Option Nat) → S
  | [], _ => 0
  | _, [] => 0
  | c :: cs, o :: os =>
    (if o == some r then c.widthDim.weightValue else 0) + flowRowWeights r cs os

/-! ## The recursion

Everything below is mutually recursive with `measure`. The kind-specific hooks
take the child list rather than the node itself, which is what makes the
recursion structural. -/


mutual

/-- `LayoutManager.measure` under `EnforceConstraintsMeasurePolicy`.

Models `BaseModernMeasurePolicy.measure` with `shouldApplyInsetWrap`,
`shouldUpdateComponentValues` and `shouldEnforceConstraints` all `true`. -/
def measure (n : Node) (c : Constraints) : LayoutTree :=
  let m := n.modifiers
  let p := Modifiers.padding m

  -- Lines 57–60. `computeModifierDefinedWidth` returns `Float.MAX_VALUE` for a
  -- FILL axis, so the `min` degenerates to `maxWidth` there.
  let definedW := m.definedWidth false
  let definedH := m.definedHeight false
  let measuredW0 := min c.maxW definedW
  let measuredH0 := min c.maxH definedH

  -- Lines 70–79: `widthIn` / `heightIn` tighten the incoming constraints.
  let minW := clampLo c.minW (m.widthIn.bind (·.min))
  let maxW := clampHi c.maxW (m.widthIn.bind (·.max))
  let minH := clampLo c.minH (m.heightIn.bind (·.min))
  let maxH := clampHi c.maxH (m.heightIn.bind (·.max))

  -- Lines 81–82.
  let insetMaxW0 := maxW - p.horizontal
  let insetMaxH0 := maxH - p.vertical

  -- Lines 90–154.
  let (mw0, minW', insetMaxW, wrapsW) :=
    resolveAxis m.widthDim n.isInHorizontalFill p.horizontal definedW measuredW0
      minW maxW insetMaxW0
  let (mh0, minH', insetMaxH, wrapsH) :=
    resolveAxis m.heightDim n.isInVerticalFill p.vertical definedH measuredH0
      minH maxH insetMaxH0

  -- Lines 163–168: `shouldEnforceConstraints()`.
  let mw1 := min (max mw0 minW') maxW
  let mh1 := min (max mh0 minH') maxH

  -- Lines 170–175: fully determined constraints win outright.
  let mw2 := if minW' = maxW then maxW else mw1
  let mh2 := if minH' = maxH then maxH else mh1

  -- Lines 181–336: measure the children, through exactly one of the two hooks.
  let (mw3, mh3, kids) :=
    if wrapsW || wrapsH then
      -- Lines 182–205.
      let wc : Constraints := ⟨minW', insetMaxW, minH', insetMaxH⟩
      let (ws, kids) :=
        match n with
        | .leaf _ content => (content, ([] : List LayoutTree))
        | .text _ spec => (spec.measure wc, ([] : List LayoutTree))
        | .box _ _ _ cs => boxWrapPass cs wc
        | .row _ _ _ sb cs => rowWrapPass cs sb wc
        | .column _ _ _ sb cs => colWrapPass cs sb wc
        | .collapsibleRow _ _ _ sb cs => cRowWrapPass cs sb wc wrapsW
        | .collapsibleColumn _ _ _ sb cs => cColWrapPass cs sb wc wrapsH
        | .flow _ _ _ sb mi ml cs => flowWrapPass cs sb mi ml wc
      let mw := if wrapsW then max (ws.w + p.horizontal) minW' else mw2
      let mh := if wrapsH then max (ws.h + p.vertical) minH' else mh2
      (mw, mh, kids)
    else
      -- Lines 324–335: no wrapping and (scrolling being out of scope) no
      -- intrinsic dimension, so the children see the resolved content box.
      let sc : Constraints := ⟨0, mw2 - p.horizontal, 0, mh2 - p.vertical⟩
      let kids :=
        match n with
        | .leaf _ _ => ([] : List LayoutTree)
        | .text _ _ => ([] : List LayoutTree)
        | .box _ _ _ cs => boxSizePass cs sc
        | .row _ _ _ _ cs => rowSizePass cs sc
        | .column _ _ _ _ cs => colSizePass cs sc
        | .collapsibleRow _ _ _ _ cs => cRowSizePass cs sc
        | .collapsibleColumn _ _ _ _ cs => cColSizePass cs sc
        | .flow _ _ _ sb mi ml cs => flowSizePass cs sb mi ml sc
      (mw2, mh2, kids)

  -- Lines 349–357: final clamp, then the `widthIn` / `heightIn` constraints.
  let mw := applyDimIn m.widthIn (min (max mw3 minW') maxW)
  let mh := applyDimIn m.heightIn (min (max mh3 minH') maxH)

  -- Line 364: `internalLayoutMeasure`, over the node's *content* box.
  let selfW := mw - p.horizontal
  let selfH := mh - p.vertical
  -- A collapsible manager re-runs its visibility decision here and may declare
  -- *itself* gone; every other manager is unconditionally visible.
  let (selfGone, placed) :=
    match n with
    | .leaf _ _ => (false, ([] : List LayoutTree))
    | .text _ _ => (false, ([] : List LayoutTree))
    | .box _ ha va _ => (false, boxPlace kids ha va selfW selfH)
    | .row _ arr va sb cs => (false, rowPlaceAll cs kids arr va sb selfW selfH)
    | .column _ ha arr sb cs => (false, colPlaceAll cs kids ha arr sb selfW selfH)
    | .collapsibleRow _ arr va sb cs =>
      cRowPlaceAll cs kids arr va sb mw selfW selfH
    | .collapsibleColumn _ ha arr sb cs =>
      cColPlaceAll cs kids ha arr sb mh selfW selfH
    | .flow _ arr va sb mi ml cs =>
      (false, flowPlaceAll cs kids arr va sb mi ml selfW selfH (m.definedHeight true))

  .node { x := 0, y := 0, w := mw, h := mh } p selfGone placed
termination_by (sizeOf n, 0, 0)

-- ### `BoxLayout`

/-- `BoxLayout.computeWrapSize`, lines 110–135.

Children are measured against loose constraints and the box takes the maximum
extent on each axis. -/
def boxWrapPass (cs : List Node) (c : Constraints) : Size × List LayoutTree :=
  let ts := measureEach cs ⟨0, c.maxW, 0, c.maxH⟩
  (⟨maxWidth ts, maxHeight ts⟩, ts)
termination_by (sizeOf cs, 1, 0)

/-- `BoxLayout.computeSize`, lines 137–155. Every child sees the same
constraints. -/
def boxSizePass (cs : List Node) (c : Constraints) : List LayoutTree :=
  measureEach cs c
termination_by (sizeOf cs, 1, 0)

/-- Measure every node against identical constraints. -/
def measureEach (cs : List Node) (c : Constraints) : List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest => measure ch c :: measureEach rest c
termination_by (sizeOf cs, 0, 0)

-- ### `RowLayout`

/-- `RowLayout.computeWrapSize`, lines 155–240.

When no child carries a weight this is a single left-to-right sweep in which
each child sees the space its predecessors did not take. When weights are
present the sweep is split in two: unweighted children are measured first, and
whatever is left is divided between the weighted ones in proportion to their
weight. -/
def rowWrapPass (cs : List Node) (spacedBy : S) (c : Constraints) :
    Size × List LayoutTree :=
  let remaining :=
    if hasHWeights cs then rowWrapRemaining cs c.maxW c.maxH else c.maxW
  let ts := rowWrapMeasure cs c.maxW remaining (totalHWeights cs) c.maxH
  let n := countVisible ts
  let spacing := if cs.isEmpty then 0 else spacedBy * ((n : S) - 1)
  (⟨sumWidths ts + spacing, maxHeight ts⟩, ts)
termination_by (sizeOf cs, 1, 0)

/-- The space left for the weighted children: the first sweep of
`RowLayout.computeWrapSize`, lines 186–200, which measures only the children
that do *not* carry a weight. -/
def rowWrapRemaining (cs : List Node) (curMaxW maxH : S) : S :=
  match cs with
  | [] => curMaxW
  | ch :: rest =>
    if ch.widthDim.hasWeight then rowWrapRemaining rest curMaxW maxH
    else rowWrapRemaining rest (curMaxW - (measure ch ⟨0, curMaxW, 0, maxH⟩).w) maxH
termination_by (sizeOf cs, 0, 0)

/-- The measuring sweep of `RowLayout.computeWrapSize`.

Unweighted children consume the running budget `curMaxW`; weighted children are
pinned to their share of `remaining`. Because `measure` is a function, measuring
an unweighted child here yields exactly the value `rowWrapRemaining` already
computed for it, which is why the reference implementation's two passes collapse
into this single ordered sweep. -/
def rowWrapMeasure (cs : List Node) (curMaxW remaining totalW maxH : S) :
    List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    if ch.widthDim.hasWeight then
      let cw := ch.widthDim.weightValue * remaining / totalW
      measure ch ⟨cw, cw, 0, maxH⟩ ::
        rowWrapMeasure rest curMaxW remaining totalW maxH
    else
      let t := measure ch ⟨0, curMaxW, 0, maxH⟩
      t :: rowWrapMeasure rest (curMaxW - t.w) remaining totalW maxH
termination_by (sizeOf cs, 0, 0)

/-- `RowLayout.computeSize`, lines 254–273.

Note that, unlike the wrap sweep, this one forwards `minWidth` unchanged to every
child while shrinking only the maximum. -/
def rowSizePass (cs : List Node) (c : Constraints) : List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    let t := measure ch c
    t :: rowSizePass rest { c with maxW := c.maxW - t.w }
termination_by (sizeOf cs, 0, 0)

/-- `RowLayout.internalLayoutMeasure`, lines 353–564.

The weighted children are re-measured against the space actually left over
inside the *final* self width, then everything is arranged along the main axis.
Every accumulation here skips the children that are gone, weights included.

The reference implementation wraps the weight resolution in a
`while (checkWeights)` loop that re-runs whenever `applyVisibility` reports a
change. `LayoutManager.applyVisibility` returns `false` unconditionally and no
subclass overrides it — not even the collapsible managers, which take their
decision in `computeVisibleChildren` instead — so the loop always runs exactly
once and a single pass is exact. -/
def rowPlaceAll (cs : List Node) (ts : List LayoutTree) (arr : Arrangement)
    (va : VAlign) (spacedBy selfW selfH : S) : List LayoutTree :=
  let ts' :=
    if hasHWeightsVis cs ts then
      rowResolveWeights cs ts (selfW - sumWidthsUnweighted cs ts) (totalHWeightsVis cs ts)
    else ts
  let total := sumWidths ts'
  let n := countVisible ts'
  let childrenW := total + (if n = 0 then 0 else spacedBy * ((n : S) - 1))
  let (tx, gap) := arrange arr selfW childrenW total n
  rowPlace ts' va selfH spacedBy gap arr.isSpaced tx
termination_by (sizeOf cs, 1, 0)

/-- Give each weighted child its share of `avail` and re-measure it against the
resulting tight constraints, `RowLayout.internalLayoutMeasure` lines 393–426. -/
def rowResolveWeights (cs : List Node) (ts : List LayoutTree) (avail totalW : S) :
    List LayoutTree :=
  match cs, ts with
  | [], _ => []
  | _, [] => []
  | ch :: crest, t :: trest =>
    let t' :=
      if ch.widthDim.hasWeight && !t.gone then
        let cw := applyDimIn ch.modifiers.widthIn
          (ch.widthDim.weightValue * avail / totalW)
        measure ch ⟨cw, cw, t.h, t.h⟩
      else t
    t' :: rowResolveWeights crest trest avail totalW
termination_by (sizeOf cs, 0, 0)

-- ### `ColumnLayout`
--
-- The mirror image of `RowLayout`: the main axis is vertical, the cross axis
-- horizontal.

/-- `ColumnLayout.computeWrapSize`, lines 133–213. -/
def colWrapPass (cs : List Node) (spacedBy : S) (c : Constraints) :
    Size × List LayoutTree :=
  let remaining :=
    if hasVWeights cs then colWrapRemaining cs c.maxW c.maxH else c.maxH
  let ts := colWrapMeasure cs c.maxW c.maxH remaining (totalVWeights cs)
  let n := countVisible ts
  let spacing := if cs.isEmpty then 0 else spacedBy * ((n : S) - 1)
  (⟨maxWidth ts, sumHeights ts + spacing⟩, ts)
termination_by (sizeOf cs, 1, 0)

/-- The space left for the weighted children of a column. -/
def colWrapRemaining (cs : List Node) (maxW curMaxH : S) : S :=
  match cs with
  | [] => curMaxH
  | ch :: rest =>
    if ch.heightDim.hasWeight then colWrapRemaining rest maxW curMaxH
    else colWrapRemaining rest maxW (curMaxH - (measure ch ⟨0, maxW, 0, curMaxH⟩).h)
termination_by (sizeOf cs, 0, 0)

/-- The measuring sweep of `ColumnLayout.computeWrapSize`. -/
def colWrapMeasure (cs : List Node) (maxW curMaxH remaining totalW : S) :
    List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    if ch.heightDim.hasWeight then
      let chh := ch.heightDim.weightValue * remaining / totalW
      measure ch ⟨0, maxW, chh, chh⟩ ::
        colWrapMeasure rest maxW curMaxH remaining totalW
    else
      let t := measure ch ⟨0, maxW, 0, curMaxH⟩
      t :: colWrapMeasure rest maxW (curMaxH - t.h) remaining totalW
termination_by (sizeOf cs, 0, 0)

/-- `ColumnLayout.computeSize`, lines 216–278.

Unlike `RowLayout.computeSize`, which is a plain shrinking sweep, this one is
gated on `DIRECT_WEIGHT_CALCULATION` — a `private static final boolean` that is
hard-coded to `true` — and resolves vertical weights already during the size
pass. The two managers are genuinely asymmetric here. -/
def colSizePass (cs : List Node) (c : Constraints) : List LayoutTree :=
  if hasVWeights cs then
    let noWeights := colSizeUnweightedTotal cs c.minW c.maxW c.minH c.maxH
    colSizeWeighted cs c.minW c.maxW c.minH c.maxH
      (c.maxH - noWeights) (totalVWeights cs)
  else
    colSizeSeq cs c
termination_by (sizeOf cs, 1, 0)

/-- The first sweep of `ColumnLayout.computeSize`: the total height of the
unweighted children, each measured against its own running budget. -/
def colSizeUnweightedTotal (cs : List Node) (minW maxW minH maxh : S) : S :=
  match cs with
  | [] => 0
  | ch :: rest =>
    if ch.heightDim.hasWeight then colSizeUnweightedTotal rest minW maxW minH maxh
    else
      let t := measure ch ⟨minW, maxW, minH, maxh⟩
      t.h + colSizeUnweightedTotal rest minW maxW minH (maxh - t.h)
termination_by (sizeOf cs, 0, 0)

/-- The second sweep of `ColumnLayout.computeSize`.

Weighted children are pinned to their share of `avail`; unweighted ones are
re-measured against a budget that now also shrinks by the weighted children.
Note that this budget differs from the one the first sweep used, so an
unweighted child can measure differently in the two sweeps — it is the second
result that is kept. -/
def colSizeWeighted (cs : List Node) (minW maxW minh maxh avail totalW : S) :
    List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    let t :=
      if ch.heightDim.hasWeight then
        let chh := avail * ch.heightDim.weightValue / totalW
        measure ch ⟨minW, maxW, chh, chh⟩
      else measure ch ⟨minW, maxW, minh, maxh⟩
    t :: colSizeWeighted rest minW maxW minh (maxh - t.h) avail totalW
termination_by (sizeOf cs, 0, 0)

/-- The weight-free branch of `ColumnLayout.computeSize`, mirroring
`RowLayout.computeSize`. -/
def colSizeSeq (cs : List Node) (c : Constraints) : List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    let t := measure ch c
    t :: colSizeSeq rest { c with maxH := c.maxH - t.h }
termination_by (sizeOf cs, 0, 0)

/-- `ColumnLayout.internalLayoutMeasure`. -/
def colPlaceAll (cs : List Node) (ts : List LayoutTree) (ha : HAlign)
    (arr : Arrangement) (spacedBy selfW selfH : S) : List LayoutTree :=
  let ts' :=
    if hasVWeightsVis cs ts then
      colResolveWeights cs ts (selfH - sumHeightsUnweighted cs ts) (totalVWeightsVis cs ts)
    else ts
  let total := sumHeights ts'
  let n := countVisible ts'
  let childrenH := total + (if n = 0 then 0 else spacedBy * ((n : S) - 1))
  let (ty, gap) := arrange arr selfH childrenH total n
  colPlace ts' ha selfW spacedBy gap arr.isSpaced ty
termination_by (sizeOf cs, 1, 0)

/-- Give each weighted child its share of the leftover vertical space. -/
def colResolveWeights (cs : List Node) (ts : List LayoutTree) (avail totalW : S) :
    List LayoutTree :=
  match cs, ts with
  | [], _ => []
  | _, [] => []
  | ch :: crest, t :: trest =>
    let t' :=
      if ch.heightDim.hasWeight && !t.gone then
        let chh := applyDimIn ch.modifiers.heightIn
          (ch.heightDim.weightValue * avail / totalW)
        measure ch ⟨t.w, t.w, chh, chh⟩
      else t
    t' :: colResolveWeights crest trest avail totalW
termination_by (sizeOf cs, 0, 0)

-- ### `CollapsibleRowLayout`
--
-- A `RowLayout` that runs `computeVisibleChildren` — a measuring sweep followed
-- by the collapsing sweep — at each of its three entry points, and then defers
-- to `RowLayout` for the arrangement itself.

/-- The first phase of `CollapsibleRowLayout.computeVisibleChildren`, lines
251–281.

Each child is measured once, in declaration order, and the budget the next child
sees shrinks by what this one took. The budget is only *offered* to a child that
is itself collapsible or that carries a weight, though: any other child is
measured against `Float.MAX_VALUE` and so reports the width it would like rather
than the width that is left. This is what lets the second phase discover that a
child does not fit. -/
def cRowPhase1 (cs : List Node) (curMaxW maxH : S) : List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    let budget := if ch.isCollapsibleRow || ch.widthDim.hasWeight then curMaxW else fltMax
    let t := measure ch ⟨0, budget, 0, maxH⟩
    t :: cRowPhase1 rest (if t.gone then curMaxW else curMaxW - t.w) maxH
termination_by (sizeOf cs, 0, 0)

/-- `CollapsibleRowLayout.computeWrapSize`, which is `computeVisibleChildren`
with a `Size` to fill in.

Two accumulations run side by side and only one survives: the sum over the
children the *first* phase measured, spacing included, and the sum over the
children the *second* phase kept, which ignores spacing entirely. When the width
wraps it is the second that wins, clamped to `maxWidth`; the height is always the
tallest child of the first phase, whether or not it is later collapsed. -/
def cRowWrapPass (cs : List Node) (spacedBy : S) (c : Constraints) (horizWrap : Bool) :
    Size × List LayoutTree :=
  let ts := cRowPhase1 cs c.maxW c.maxH
  let n := countVisible ts
  let spacing := if cs.isEmpty then 0 else spacedBy * ((n : S) - 1)
  let childrenW := sumWidths (applyFlags ts (collapseFlags .horizontal true c.maxW cs ts))
  let w := if horizWrap then min c.maxW childrenW else sumWidths ts + spacing
  (⟨w, maxHeight ts⟩, ts)
termination_by (sizeOf cs, 1, 0)

/-- `CollapsibleRowLayout.computeSize`: the same sweep with no `Size` to fill in.

The collapsing decision this pass reaches is recorded nowhere in the result,
because `internalLayoutMeasure` clears every override and takes it again against
a different budget. Only the measurement survives. -/
def cRowSizePass (cs : List Node) (c : Constraints) : List LayoutTree :=
  cRowPhase1 cs c.maxW c.maxH
termination_by (sizeOf cs, 1, 0)

/-- `CollapsibleRowLayout.internalLayoutMeasure`, lines 232–237.

The collapsing sweep is taken one final time and it is this decision that is
observable. Note the budget: `m.getW()`, the manager's **full** measured width,
padding included — so a padded collapsible row admits children its content box
cannot hold.

The manager declares *itself* gone when nothing survived, which an empty
collapsible row does vacuously. -/
def cRowPlaceAll (cs : List Node) (ts : List LayoutTree) (arr : Arrangement)
    (va : VAlign) (spacedBy mw selfW selfH : S) : Bool × List LayoutTree :=
  let ts1 := applyFlags ts (collapseFlags .horizontal true mw cs ts)
  (countVisible ts1 == 0, rowPlaceAll cs ts1 arr va spacedBy selfW selfH)
termination_by (sizeOf cs, 2, 0)

-- ### `CollapsibleColumnLayout`
--
-- The mirror image, verbatim: the reference implementation is a copy of
-- `CollapsibleRowLayout` with the axes exchanged, and — unlike the plain
-- `ColumnLayout` — it introduces no asymmetry of its own.

/-- The first phase of `CollapsibleColumnLayout.computeVisibleChildren`. -/
def cColPhase1 (cs : List Node) (maxW curMaxH : S) : List LayoutTree :=
  match cs with
  | [] => []
  | ch :: rest =>
    let budget := if ch.isCollapsibleColumn || ch.heightDim.hasWeight then curMaxH else fltMax
    let t := measure ch ⟨0, maxW, 0, budget⟩
    t :: cColPhase1 rest maxW (if t.gone then curMaxH else curMaxH - t.h)
termination_by (sizeOf cs, 0, 0)

/-- `CollapsibleColumnLayout.computeWrapSize`. -/
def cColWrapPass (cs : List Node) (spacedBy : S) (c : Constraints) (vertWrap : Bool) :
    Size × List LayoutTree :=
  let ts := cColPhase1 cs c.maxW c.maxH
  let n := countVisible ts
  let spacing := if cs.isEmpty then 0 else spacedBy * ((n : S) - 1)
  let childrenH := sumHeights (applyFlags ts (collapseFlags .vertical false c.maxH cs ts))
  let h := if vertWrap then min c.maxH childrenH else sumHeights ts + spacing
  (⟨maxWidth ts, h⟩, ts)
termination_by (sizeOf cs, 1, 0)

/-- `CollapsibleColumnLayout.computeSize`. -/
def cColSizePass (cs : List Node) (c : Constraints) : List LayoutTree :=
  cColPhase1 cs c.maxW c.maxH
termination_by (sizeOf cs, 1, 0)

/-- `CollapsibleColumnLayout.internalLayoutMeasure`, deciding against the
manager's full measured height. -/
def cColPlaceAll (cs : List Node) (ts : List LayoutTree) (ha : HAlign)
    (arr : Arrangement) (spacedBy mh selfW selfH : S) : Bool × List LayoutTree :=
  let ts1 := applyFlags ts (collapseFlags .vertical false mh cs ts)
  (countVisible ts1 == 0, colPlaceAll cs ts1 ha arr spacedBy selfW selfH)
termination_by (sizeOf cs, 2, 0)

-- ### `FlowLayout`
--
-- A `RowLayout` that segments its children into rows and then runs the
-- inherited row machinery once per row. Its visibility bookkeeping is unlike
-- the collapsible managers': `segmentComponents` calls `setVisibility(GONE)`,
-- which writes the *base* value rather than an override, and nothing ever
-- clears it. A child refused by one pass therefore stays gone for the rest of
-- the layout — and, being gone, is attributed zero width by the next pass,
-- which lets it rejoin a row as an empty item at whatever position the cursor
-- had reached.

/-- The measuring sweep `FlowLayout.computeSize` performs, lines 342–353.

Segmentation measures every child that needs it against the full box, and the
per-row loop that follows measures them again against exactly the same
constraints, the shrinking budget having been commented out. The two therefore
collapse into a single uniform measurement. -/
def flowSizePass (cs : List Node) (spacedBy : S) (maxItems maxLines : Nat)
    (c : Constraints) : List LayoutTree :=
  let ts := measureEach cs ⟨0, c.maxW, 0, c.maxH⟩
  let ids := flowSegment spacedBy c.maxW c.maxH maxItems maxLines (flowDims cs ts)
    0 0 0 1 0 false
  applyFlags ts (ids.map (·.isNone))
termination_by (sizeOf cs, 1, 0)

/-- The unweighted members of row `r`, measured against a budget that shrinks as
the row fills: the first sweep of `RowLayout.computeWrapSize` restricted to one
row. Returns what is left for the weighted members. -/
def flowRowRemaining (cs : List Node) (ids : List (Option Nat)) (r : Nat)
    (curMaxW maxH : S) : S :=
  match cs, ids with
  | [], _ => curMaxW
  | _, [] => curMaxW
  | ch :: rest, o :: os =>
    if o != some r || ch.widthDim.hasWeight then flowRowRemaining rest os r curMaxW maxH
    else flowRowRemaining rest os r (curMaxW - (measure ch ⟨0, curMaxW, 0, maxH⟩).w) maxH
termination_by (sizeOf cs, 0, 0)

/-- `RowLayout.computeWrapSize` restricted to the members of row `r`; every other
child is passed through untouched, since the reference implementation hands that
method one row at a time. -/
def flowRowWrapMeasure (cs : List Node) (ts : List LayoutTree) (ids : List (Option Nat))
    (r : Nat) (curMaxW remaining totalW maxH : S) : List LayoutTree :=
  match cs, ts, ids with
  | [], _, _ => []
  | _, [], _ => []
  | _, _, [] => ts
  | ch :: crest, t :: trest, o :: orest =>
    if o != some r then
      t :: flowRowWrapMeasure crest trest orest r curMaxW remaining totalW maxH
    else if ch.widthDim.hasWeight then
      let cw := ch.widthDim.weightValue * remaining / totalW
      measure ch ⟨cw, cw, 0, maxH⟩ ::
        flowRowWrapMeasure crest trest orest r curMaxW remaining totalW maxH
    else
      let t' := measure ch ⟨0, curMaxW, 0, maxH⟩
      t' :: flowRowWrapMeasure crest trest orest r (curMaxW - t'.w) remaining totalW maxH
termination_by (sizeOf cs, 0, 0)

/-- One `super.computeWrapSize` call per row, accumulating the widest row and the
total height, `FlowLayout.computeWrapSize` lines 312–326. -/
def flowWrapRows (cs : List Node) (ts : List LayoutTree) (ids : List (Option Nat))
    (rows : List Nat) (spacedBy maxW maxH w h : S) : S × S × List LayoutTree :=
  match rows with
  | [] => (w, h, ts)
  | r :: rs =>
    let totalW := flowRowWeights r cs ids
    let remaining :=
      if flowRowHasWeight r cs ids then flowRowRemaining cs ids r maxW maxH else maxW
    let ts' := flowRowWrapMeasure cs ts ids r maxW remaining totalW maxH
    let members := maskRow ts' ids r
    let n := countVisible members
    let rowW :=
      sumWidths members +
        (if flowRowMembers r ids = 0 then 0 else spacedBy * ((n : S) - 1))
    flowWrapRows cs ts' ids rs spacedBy maxW maxH (max w rowW) (h + maxHeight members)
termination_by (sizeOf cs, 1, rows.length)

/-- `FlowLayout.computeWrapSize`, lines 290–331.

The flow is as wide as its widest row and as tall as its rows stacked, both
clamped into the incoming constraints — the only place in the model where a
`computeWrapSize` clamps on its own account. -/
def flowWrapPass (cs : List Node) (spacedBy : S) (maxItems maxLines : Nat)
    (c : Constraints) : Size × List LayoutTree :=
  let ts := measureEach cs ⟨0, c.maxW, 0, c.maxH⟩
  let ids := flowSegment spacedBy c.maxW c.maxH maxItems maxLines (flowDims cs ts)
    0 0 0 1 0 false
  let (w, h, ts') :=
    flowWrapRows cs ts ids (flowRowList ids) spacedBy c.maxW c.maxH c.minW 0
  (⟨min (max c.minW w) c.maxW, min (max c.minH h) c.maxH⟩,
    applyFlags ts' (ids.map (·.isNone)))
termination_by (sizeOf cs, 2, 0)

/-- Re-measure for the final segmentation, `segmentComponents` line 249 as
reached from `internalLayoutMeasure`.

A child that is gone is left alone — it is not measured again, and its stale size
is what the segmentation will read back as zero. A weighted child is not measured
here either; the row machinery will resolve its weight in a moment. -/
def flowRemeasure (cs : List Node) (ts : List LayoutTree) (w h : S) : List LayoutTree :=
  match cs, ts with
  | [], _ => []
  | _, [] => []
  | ch :: crest, t :: trest =>
    (if t.gone || ch.widthDim.hasWeight then t else measure ch ⟨0, w, 0, h⟩)
      :: flowRemeasure crest trest w h
termination_by (sizeOf cs, 0, 0)

/-- Lay out one row at a time, `FlowLayout.internalLayoutMeasure` lines 393–398.

Each row is handed to `RowLayout.internalLayoutMeasure` as a box `selfW` wide and
`minIntrinsicHeight(row)` tall, and the cursor then advances by the row's
*measured* height — the tallest visible child — which is a different quantity, so
rows overlap or gape whenever a row's intrinsic and measured heights disagree. -/
def flowRowLoop (cs : List Node) (ts : List LayoutTree) (ids : List (Option Nat))
    (rows : List Nat) (arr : Arrangement) (va : VAlign) (spacedBy selfW ownH y : S)
    (acc : List LayoutTree) : List LayoutTree :=
  match rows with
  | [] => acc
  | r :: rs =>
    let rowH := flowRowIntrinsic ownH r cs ids
    let placed := rowPlaceAll cs (maskRow ts ids r) arr va spacedBy selfW rowH
    flowRowLoop cs ts ids rs arr va spacedBy selfW ownH (y + maxHeight placed)
      (mergeRow y r acc placed ids)
termination_by (sizeOf cs, 2, rows.length)

/-- `FlowLayout.internalLayoutMeasure`, lines 356–399.

The rows are stacked from an origin the flow's *vertical* positioning fixes
against the total intrinsic height of the stack, and the same positioning is then
applied again inside each row. -/
def flowPlaceAll (cs : List Node) (ts : List LayoutTree) (arr : Arrangement)
    (va : VAlign) (spacedBy : S) (maxItems maxLines : Nat) (selfW selfH ownH : S) :
    List LayoutTree :=
  let ts1 := flowRemeasure cs ts selfW selfH
  let ids := flowSegment spacedBy selfW selfH maxItems maxLines (flowDims cs ts1)
    0 0 0 1 0 false
  let ts2 := applyFlags ts1 (ids.map (·.isNone))
  let rows := flowRowList ids
  let stack := flowRowsHeight ownH cs ids rows
  let y0 :=
    match va with
    | .Top => 0
    | .Center => (selfH - stack) / 2
    | .Bottom => selfH - stack
  flowRowLoop cs ts2 ids rows arr va spacedBy selfW ownH y0 ts2
termination_by (sizeOf cs, 3, 0)

end

/-! ## Entry point -/

/-- Lay a document out in a viewport of `width × height`.

The root component is measured against tight constraints, exactly as
`RootLayoutComponent` does when the host hands it a viewport. -/
def layout (n : Node) (width height : S) : LayoutTree :=
  measure n (Constraints.tight width height)

mutual

/-- Absolute, window-relative bounds of every node of a laid-out tree, in
pre-order. Adds each ancestor's padding, mirroring
`LayoutComponent.getLocationInWindow`. -/
def LayoutTree.absolute : LayoutTree → S → S → List Rect
  | .node r p _ ks, ox, oy =>
    let x := ox + r.x
    let y := oy + r.y
    { r with x := x, y := y } :: absoluteList ks (x + p.left) (y + p.top)

/-- `LayoutTree.absolute`, lifted over a sibling list. -/
def absoluteList : List LayoutTree → S → S → List Rect
  | [], _, _ => []
  | k :: ks, ox, oy => k.absolute ox oy ++ absoluteList ks ox oy

end

end RemoteCompose

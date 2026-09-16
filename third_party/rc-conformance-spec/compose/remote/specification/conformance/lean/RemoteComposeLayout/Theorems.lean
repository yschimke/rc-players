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
import RemoteComposeLayout.Conform
import Mathlib.Tactic

/-!
# Properties of the layout algorithm

Theorems proved against the specification in `Measure.lean`. They fall into
three groups:

* **Invariants** that hold for every document, and that a re-implementation of
  the layout engine may rely on;
* **Characterisations** pinning down the behaviour of individual steps;
* **Sharpness results**, which record deliberately surprising behaviour of the
  reference implementation by exhibiting a document that exercises it. These
  double as regression tests: if the specification is ever "cleaned up", they
  break.
-/

namespace RemoteCompose

/-! ## Placement preserves the measured children

Positioning is a separate step from measurement: `internalLayoutMeasure` only
assigns `x` and `y`. It never adds, drops, reorders or resizes a child. -/

@[simp]
theorem rowPlace_length (ts : List LayoutTree) (va : VAlign) (selfH sb gap : S)
    (sp : Bool) (tx : S) : (rowPlace ts va selfH sb gap sp tx).length = ts.length := by
  induction ts generalizing tx with
  | nil => rfl
  | cons t rest ih => simp [rowPlace, ih]

@[simp]
theorem colPlace_length (ts : List LayoutTree) (ha : HAlign) (selfW sb gap : S)
    (sp : Bool) (ty : S) : (colPlace ts ha selfW sb gap sp ty).length = ts.length := by
  induction ts generalizing ty with
  | nil => rfl
  | cons t rest ih => simp [colPlace, ih]

@[simp]
theorem withPos_w (t : LayoutTree) (x y : S) : (t.withPos x y).w = t.w := by
  cases t; rfl

@[simp]
theorem withPos_h (t : LayoutTree) (x y : S) : (t.withPos x y).h = t.h := by
  cases t; rfl

/-- A row never resizes its children while arranging them. -/
theorem rowPlace_preserves_widths (ts : List LayoutTree) (va : VAlign)
    (selfH sb gap : S) (sp : Bool) (tx : S) :
    (rowPlace ts va selfH sb gap sp tx).map LayoutTree.w = ts.map LayoutTree.w := by
  induction ts generalizing tx with
  | nil => rfl
  | cons t rest ih => simp [rowPlace, ih]

/-- A column never resizes its children while arranging them. -/
theorem colPlace_preserves_heights (ts : List LayoutTree) (ha : HAlign)
    (selfW sb gap : S) (sp : Bool) (ty : S) :
    (colPlace ts ha selfW sb gap sp ty).map LayoutTree.h = ts.map LayoutTree.h := by
  induction ts generalizing ty with
  | nil => rfl
  | cons t rest ih => simp [colPlace, ih]

/-- A box never resizes or reorders its children while stacking them. -/
theorem boxPlace_preserves_sizes (ts : List LayoutTree) (ha : HAlign) (va : VAlign)
    (selfW selfH : S) :
    (boxPlace ts ha va selfW selfH).map (fun t => (t.w, t.h))
      = ts.map (fun t => (t.w, t.h)) := by
  simp [boxPlace, List.map_map, Function.comp_def]

/-! ## Main-axis progression

With `Arrangement.Start` and no spacing, a row lays its children out end to end:
each child begins exactly where its predecessor ended. -/

theorem rowPlace_start_head (t : LayoutTree) (rest : List LayoutTree) (va : VAlign)
    (selfH : S) (tx : S) :
    (rowPlace (t :: rest) va selfH 0 0 false tx).head?.map LayoutTree.x = some tx := by
  simp [rowPlace, LayoutTree.withPos, LayoutTree.x, LayoutTree.rect]

theorem rowPlace_start_tail (t : LayoutTree) (rest : List LayoutTree) (va : VAlign)
    (selfH : S) (tx : S) (hv : t.gone = false) :
    (rowPlace (t :: rest) va selfH 0 0 false tx).tail
      = rowPlace rest va selfH 0 0 false (tx + t.w) := by
  simp [rowPlace, hv]

/-- A child that is gone occupies no main-axis extent: its successors are placed
exactly where they would have been had it not been there at all. -/
theorem rowPlace_gone_no_advance (t : LayoutTree) (rest : List LayoutTree)
    (va : VAlign) (selfH spacedBy gap : S) (spaced : Bool) (tx : S)
    (hg : t.gone = true) :
    (rowPlace (t :: rest) va selfH spacedBy gap spaced tx).tail
      = rowPlace rest va selfH spacedBy gap spaced tx := by
  simp [rowPlace, hg]

/-! ## Cross-axis alignment

Centring is symmetric, and the extreme alignments touch their edge. -/

@[simp]
theorem crossX_Start (selfW childW : S) : crossX .Start selfW childW = 0 := rfl

@[simp]
theorem crossY_Top (selfH childH : S) : crossY .Top selfH childH = 0 := rfl

/-- A centred child leaves the same slack on both sides. -/
theorem crossX_Center_symmetric (selfW childW : S) :
    crossX .Center selfW childW = selfW - (crossX .Center selfW childW + childW) := by
  simp only [crossX]; ring

/-- An end-aligned child's trailing edge coincides with the container's. -/
theorem crossX_End_flush (selfW childW : S) :
    crossX .End selfW childW + childW = selfW := by
  simp [crossX]

/-- A bottom-aligned child's trailing edge coincides with the container's. -/
theorem crossY_Bottom_flush (selfH childH : S) :
    crossY .Bottom selfH childH + childH = selfH := by
  simp [crossY]

/-- Centring a child that exactly fills its container is a no-op, on both axes. -/
theorem cross_Center_exact (s : S) :
    crossX .Center s s = 0 ∧ crossY .Center s s = 0 := by
  constructor <;> simp [crossX, crossY]

/-! ## Arrangements

`SpaceEvenly` produces `n + 1` equal gaps and `SpaceAround` produces `n` of
them, half of one at each end. -/

/-- `SpaceEvenly`: the leading gap equals the inter-child gap, and the `n + 1`
gaps together with the children exactly fill the container. -/
theorem spaceEvenly_fills (selfMain childrenMain total : S) (n : ℕ) :
    let (tx, gap) := arrange .SpaceEvenly selfMain childrenMain total n
    tx = gap ∧ ((n : S) + 1) * gap + total = selfMain := by
  refine ⟨rfl, ?_⟩
  have hn : ((n : S) + 1) ≠ 0 := by positivity
  field_simp [arrange]
  ring

/-- `SpaceAround`: the leading and trailing half-gaps plus the `n - 1` full
inter-child gaps and the children exactly fill the container. -/
theorem spaceAround_fills (selfMain childrenMain total : S) (n : ℕ) (hn : n ≠ 0) :
    let (tx, gap) := arrange .SpaceAround selfMain childrenMain total n
    tx = gap / 2 ∧ (n : S) * gap + total = selfMain := by
  refine ⟨rfl, ?_⟩
  have : (n : S) ≠ 0 := Nat.cast_ne_zero.mpr hn
  field_simp [arrange]
  ring

/-- `SpaceBetween` with a single child degenerates to centring, which is what
the `else` branch of `RowLayout.internalLayoutMeasure` says in so many words
("we center the element"). -/
theorem spaceBetween_singleton (selfMain childrenMain total : S) :
    arrange .SpaceBetween selfMain childrenMain total 1
      = arrange .Center selfMain childrenMain total 1 := by
  simp [arrange]

/-! ## The modifier chain

The chain is order-sensitive, and deliberately so. -/

/-- Total padding does not depend on where the size modifier sits. -/
theorem padding_order_irrelevant (v w : S) :
    Modifiers.padding [.padding (.all v), .width (.exact w)]
      = Modifiers.padding [.width (.exact w), .padding (.all v)] := by
  simp [Modifiers.padding, Padding.all]

/-- **Sharpness.** The modifier-defined width *does* depend on that order:
padding declared before the size modifier is added to it, padding declared
after is not. `LayoutComponent.computeModifierDefinedWidth` `break`s at the
first `WidthModifierOperation`.

This is what makes `Modifier.size(50).padding(8)` measure `50` wide, as the
`row_padding_all` gold file records. -/
theorem definedWidth_order_matters :
    Modifiers.definedWidth [.padding (.all 8), .width (.exact 50)] false = 66 ∧
    Modifiers.definedWidth [.width (.exact 50), .padding (.all 8)] false = 50 := by
  constructor <;> norm_num [Modifiers.definedWidth, Modifiers.definedWidth.go,
    Modifiers.Dim.base, Padding.all, clampLo]

/-- A height modifier does not stop the walk that computes the defined width. -/
theorem definedWidth_ignores_height (v p w : S) :
    Modifiers.definedWidth [.padding (.all p), .height (.exact v), .width (.exact w)] false
      = p + w + p := by
  simp [Modifiers.definedWidth, Modifiers.definedWidth.go, Modifiers.Dim.base,
    Padding.all, clampLo]

/-- The first width modifier wins, matching `mWidthModifier == null` in
`LayoutComponent.updateModifiers`. -/
theorem widthDim_first_wins (d e : Dim) (c c' : Option DimIn) :
    Modifiers.widthDim [.width d c, .width e c'] = d := rfl

/-! ## Constraint enforcement

The default policy is `EnforceConstraintsMeasurePolicy`, so a measured size
never exceeds the maximum it was given. -/

/-- `min (max x lo) hi ≤ hi`, the shape of the final clamp. -/
theorem clamp_le (x lo hi : S) : min (max x lo) hi ≤ hi := min_le_right (α := S) _ _

/-- **Invariant.** A component that declares no `widthIn` never measures wider
than the maximum width it was offered. -/
theorem measure_width_le_maxW (n : Node) (c : Constraints)
    (h : Modifiers.widthIn n.modifiers = none) : (measure n c).w ≤ c.maxW := by
  rw [measure]
  simp only [h, bind_none_left, clampHi_none, applyDimIn_none, LayoutTree.w,
    LayoutTree.rect]
  exact min_le_right _ _

/-- **Invariant.** Dually for the height. -/
theorem measure_height_le_maxH (n : Node) (c : Constraints)
    (h : Modifiers.heightIn n.modifiers = none) : (measure n c).h ≤ c.maxH := by
  rw [measure]
  simp only [h, bind_none_left, clampHi_none, applyDimIn_none, LayoutTree.h,
    LayoutTree.rect]
  exact min_le_right _ _

/-! ## Collapsing and segmentation

The two structural passes the collapsible and flow managers add: neither may
lose a child, and both are monotone once they start refusing. -/

/-- The collapsing sweep reaches a decision about every child, exactly once. -/
theorem collapseSweep_length (horiz : Bool) (budget : S)
    (xs : List (Nat × LayoutTree)) (acc : S) (ovf : Bool) :
    (collapseSweep horiz budget xs acc ovf).length = xs.length := by
  induction xs generalizing acc ovf with
  | nil => rfl
  | cons p rest ih =>
    obtain ⟨i, t⟩ := p
    simp only [collapseSweep]
    split
    · simp [ih]
    · split <;> split <;> simp [ih]

/-- **Refusal is sticky.** Once one child has been refused, every child after it
*in priority order* is refused too, however small — a collapsible manager never
back-fills. -/
theorem collapseSweep_overflow (horiz : Bool) (budget acc : S)
    (xs : List (Nat × LayoutTree)) :
    collapseSweep horiz budget xs acc true = xs.map (fun p => (p.1, true)) := by
  induction xs with
  | nil => rfl
  | cons p rest ih => simp [collapseSweep, ih]

/-- Applying the decisions changes visibility and nothing else. -/
theorem applyFlags_preserves_rects (ts : List LayoutTree) (gs : List Bool) :
    (applyFlags ts gs).map LayoutTree.rect = ts.map LayoutTree.rect := by
  induction ts generalizing gs with
  | nil => rfl
  | cons t rest ih =>
    cases gs with
    | nil => simpa [applyFlags, LayoutTree.withGone, LayoutTree.rect] using ih []
    | cons g gs => simpa [applyFlags, LayoutTree.withGone, LayoutTree.rect] using ih gs

/-- No child precedes itself: the truncating comparator is irreflexive, which is
the least a sort may ask of it. -/
theorem priorityLt_irrefl (o : Orient) (a : Node) : priorityLt o a a = false := by
  simp [priorityLt]

/-- Inserting into the priority order lengthens it by one. -/
theorem priorityInsert_length (o : Orient) (a : α) (key : α → Node) (l : List α) :
    (priorityInsert o a key l).length = l.length + 1 := by
  induction l with
  | nil => rfl
  | cons b bs ih =>
    by_cases h : priorityLt o (key a) (key b) <;> simp [priorityInsert, h, ih]

/-- **The priority sort keeps every child.** It only decides the order in which
they are offered space; which of them survive is the sweep's business. -/
theorem sortWithPriorities_length (o : Orient) (key : α → Node) (l : List α) :
    (sortWithPriorities o key l).length = l.length := by
  induction l with
  | nil => rfl
  | cons a as ih => simp [sortWithPriorities, priorityInsert_length, ih]

/-- Segmentation assigns every child either a row or a refusal. -/
theorem flowSegment_length (spacedBy maxW maxH : S) (maxItems maxLines : Nat)
    (ds : List (S × S × Bool)) (curW rowH totH : S) (rows cnt : Nat) (ovf : Bool) :
    (flowSegment spacedBy maxW maxH maxItems maxLines ds curW rowH totH rows cnt ovf).length
      = ds.length := by
  induction ds generalizing curW rowH totH rows cnt ovf with
  | nil => simp [flowSegment]
  | cons d rest ih =>
    obtain ⟨cw, ch, g⟩ := d
    simp only [flowSegment]
    split
    · simp [ih]
    · split <;> simp [ih]

/-- A flow always opens a row, even if nothing ever joins it: the reference
implementation adds one before it starts walking, which is why `maxLines` bites
a row earlier than one might expect. -/
theorem flowRowCount_pos (ids : List (Option Nat)) : 0 < flowRowCount ids := by
  have h : ∀ (l : List (Option Nat)) (n : Nat), 0 < n →
      0 < l.foldl (fun n o => match o with | some r => max n (r + 1) | none => n) n := by
    intro l
    induction l with
    | nil => intro n hn; simpa using hn
    | cons o os ih =>
      intro n hn
      cases o with
      | none => simpa using ih n hn
      | some r =>
        simp only [List.foldl]
        exact ih _ (lt_of_lt_of_le hn (le_max_left n (r + 1)))
  exact h ids 1 Nat.one_pos

/-- Handing one row to the row machinery hides the others and disturbs nothing
else, which is what makes the masking faithful to passing a sublist. -/
theorem maskRow_length (ts : List LayoutTree) (ids : List (Option Nat)) (r : Nat) :
    (maskRow ts ids r).length = ts.length := by
  induction ts generalizing ids with
  | nil => rfl
  | cons t rest ih =>
    cases ids with
    | nil => simpa [maskRow] using ih []
    | cons o os => simpa [maskRow] using ih os

/-! ## Worked examples

Small documents whose layout is settled by computation. They are the smallest
witnesses of the behaviours the gold suite exercises at scale. -/

section Examples

/-- A `100 × 40` box, centred in a `300 × 200` viewport. -/
def centeredBox : Node :=
  .box [.width (.fill none), .height (.fill none)] .Center .Center
    [.box [.width (.exact 100), .height (.exact 40)] .Start .Top []]

example : (layout centeredBox 300 200).preorder
    = [⟨0, 0, 300, 200⟩, ⟨100, 80, 100, 40⟩] := by native_decide

/-- Three boxes in a row, splitting the leftover space `1 : 3` after a fixed
`100` px child. -/
def weightedRow : Node :=
  .row [.width (.fill none), .height (.fill none)] .Start .Top 0
    [ .box [.width (.exact 100), .height (.exact 20)] .Start .Top []
    , .box [.width (.weight 1), .height (.exact 20)] .Start .Top []
    , .box [.width (.weight 3), .height (.exact 20)] .Start .Top [] ]

example : (layout weightedRow 500 100).preorder
    = [⟨0, 0, 500, 100⟩, ⟨0, 0, 100, 20⟩, ⟨100, 0, 100, 20⟩, ⟨200, 0, 300, 20⟩] := by
  native_decide

/-- **Sharpness.** A wrapping row sizes itself to its children, and its padding
is added *around* that content size: `10 + (30 + 5 + 40) + 10 = 95` wide and
`10 + 30 + 10 = 50` tall.

The row has to be nested here: `layout` measures the root against *tight*
viewport constraints, and `minW = maxW` forces the measured size to the viewport
regardless of the content (`BaseModernMeasurePolicy.measure` lines 170–175). -/
def paddedWrapRow : Node :=
  .box [.width (.fill none), .height (.fill none)] .Start .Top
    [ .row [.padding (.all 10)] .Start .Top 5
        [ .box [.width (.exact 30), .height (.exact 30)] .Start .Top []
        , .box [.width (.exact 40), .height (.exact 20)] .Start .Top [] ] ]

example : (layout paddedWrapRow 500 500).preorder
    = [⟨0, 0, 500, 500⟩, ⟨0, 0, 95, 50⟩, ⟨0, 0, 30, 30⟩, ⟨35, 0, 40, 20⟩] := by
  native_decide

/-- A root component is measured tightly, so even a `WRAP` root fills the
viewport. -/
example : (layout (.row [.padding (.all 10)] .Start .Top 0 []) 500 500).rect
    = ⟨0, 0, 500, 500⟩ := by native_decide

/-- **Sharpness.** `spacedBy` is added *on top of* a `SPACE_*` arrangement's
gaps, even though those gaps were computed from the children's widths alone.
A row that mixes the two therefore overflows by `spacedBy * (n - 1)`: here the
last child ends at `210`, `10` px past the `200` px container.

`RowLayout.internalLayoutMeasure` computes `horizontalGap` from `total`, which
excludes `spacedBy`, and then still executes `tx += spacedBy`. -/
def spacedAndArranged : Node :=
  .row [.width (.fill none), .height (.exact 50)] .SpaceBetween .Top 10
    [ .box [.width (.exact 50), .height (.exact 10)] .Start .Top []
    , .box [.width (.exact 50), .height (.exact 10)] .Start .Top [] ]

example : (layout spacedAndArranged 200 50).preorder
    = [⟨0, 0, 200, 50⟩, ⟨0, 0, 50, 10⟩, ⟨160, 0, 50, 10⟩] := by native_decide

/-! ### Collapsible and flow -/

/-- **Sharpness.** A collapsible row keeps as many children as fit, dropping them
from the lowest priority upwards. Three `90` px children cannot fit in `200` px,
so the one of priority `1` goes.

The child that goes is still *positioned* — at `x = 10`, which is exactly where
its successor starts. The reference implementation calls `setX` before testing
`isGone` and only skips the cursor advance, and the gold dumps record that. -/
def collapsingRow : Node :=
  .collapsibleRow [.width (.fill none), .height (.fill none)] .Center .Center 0
    [ .box [.width (.exact 90), .height (.exact 50), .collapsiblePriority .horizontal 1]
        .Center .Center []
    , .box [.width (.exact 90), .height (.exact 50), .collapsiblePriority .horizontal 2]
        .Center .Center []
    , .box [.width (.exact 90), .height (.exact 50), .collapsiblePriority .horizontal 3]
        .Center .Center [] ]

example : (layout collapsingRow 200 100).preorder
    = [⟨0, 0, 200, 100⟩, ⟨10, 25, 90, 50⟩, ⟨10, 25, 90, 50⟩, ⟨100, 25, 90, 50⟩] := by
  native_decide

example : (layout collapsingRow 200 100).preorderGone = [false, true, false, false] := by
  native_decide

/-- **Sharpness.** The collapsing fit test takes no account of `spacedBy`: two
`90` px children are admitted into `180` px because `90 + 90 ≤ 180`, and the
`20` px of spacing is then added anyway. The row overflows its container by
exactly `spacedBy * (n - 1)`, the second child ending at `200`. -/
def spacedCollapsingRow : Node :=
  .collapsibleRow [.width (.fill none), .height (.fill none)] .Start .Top 20
    [ .box [.width (.exact 90), .height (.exact 50), .collapsiblePriority .horizontal 1]
        .Center .Center []
    , .box [.width (.exact 90), .height (.exact 50), .collapsiblePriority .horizontal 2]
        .Center .Center [] ]

example : (layout spacedCollapsingRow 180 100).preorder
    = [⟨0, 0, 180, 100⟩, ⟨0, 0, 90, 50⟩, ⟨110, 0, 90, 50⟩] := by native_decide

example : (layout spacedCollapsingRow 180 100).preorderGone = [false, false, false] := by
  native_decide

/-- A flow breaks a row as soon as `maxColumns` is reached, whatever the space
left: four `80 × 40` children in a `400` px viewport still make a `2 × 2` grid.

Each row is as tall as `minIntrinsicHeight` says, and the cursor then advances by
the row's *measured* height — here the two agree, at `40`. -/
def wrappingFlow : Node :=
  .flow [.width (.fill none), .height (.fill none)] .Start .Top 0 2 2147483647
    [ .box [.width (.exact 80), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 80), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 80), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 80), .height (.exact 40)] .Center .Center [] ]

example : (layout wrappingFlow 400 300).preorder
    = [⟨0, 0, 400, 300⟩, ⟨0, 0, 80, 40⟩, ⟨80, 0, 80, 40⟩,
       ⟨0, 40, 80, 40⟩, ⟨80, 40, 80, 40⟩] := by native_decide

/-- **Sharpness.** `maxLines` refuses the fifth child, and the refusal is
recorded as a *base* visibility that nothing ever clears. The next segmentation
pass therefore reads it back as zero-width and lets it rejoin the last row, where
it is positioned at `x = 200` — past the `260` px container, and past the two
children that actually occupy that row.

This is the behaviour `flow_max_lines` is tagged `suspicious` for in the gold
suite. The specification reproduces it rather than tidying it away. -/
def cappedFlow : Node :=
  .flow [.width (.fill none), .height (.fill none)] .Start .Top 0 2147483647 2
    [ .box [.width (.exact 100), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 100), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 100), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 100), .height (.exact 40)] .Center .Center []
    , .box [.width (.exact 100), .height (.exact 40)] .Center .Center [] ]

example : (layout cappedFlow 260 400).preorder
    = [⟨0, 0, 260, 400⟩, ⟨0, 0, 100, 40⟩, ⟨100, 0, 100, 40⟩,
       ⟨0, 40, 100, 40⟩, ⟨100, 40, 100, 40⟩, ⟨200, 40, 100, 40⟩] := by native_decide

example : (layout cappedFlow 260 400).preorderGone
    = [false, false, false, false, false, true] := by native_decide

/-- **Sharpness.** Autosize text inside a collapsible column.
`CollapsibleColumnLayout`'s first pass measures unweighted children against
`Float.MAX_VALUE` (`fltMax`). An autosizing text child therefore expands to its
maximum font size (`maxFontSize = 100`, text height `120`), which then exceeds
the container's available height (`100`).
The greedy second pass declares the child `GONE`, leaving 0 visible children,
causing the collapsible column to mark itself `GONE` and collapse to height `0`. -/
def autosizeCollapsingColumn : Node :=
  .collapsibleColumn [.width (.exact 300), .height (.wrap)] .Center .Center 0
    [ .textAutosize [.width (.wrap), .height (.wrap)] 42 (84/5) 4 100 14 ]

example : (layout autosizeCollapsingColumn 300 100).gone = true := by
  native_decide

example : (layout autosizeCollapsingColumn 300 100).preorderGone = [true, true] := by
  native_decide

/-- Inside a wrapping parent container, the collapsed column's measured wrap height is 0. -/
def boxedAutosizeCollapsing : Node :=
  .box [.width (.exact 300), .height (.wrap)] .Start .Top
    [ .collapsibleColumn [.width (.exact 300), .height (.wrap)] .Center .Center 0
        [ .textAutosize [.width (.wrap), .height (.wrap)] 42 (84/5) 4 100 14 ] ]

example : (layout boxedAutosizeCollapsing 300 100).kids.head!.h = 0 := by
  native_decide

end Examples

end RemoteCompose

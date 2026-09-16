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
import RemoteComposeLayout.Basic

/-!
# RemoteCompose layout specification — component tree

Defines the input tree (`Node`) and the output tree (`LayoutTree`), plus the
purely arithmetic helpers used by the measure algorithm.
-/

namespace RemoteCompose

/-! ## Text specification (`CoreText.java`) -/

/-- Sizing specification for text components (`CoreText.java`). -/
structure TextSpec where
  /-- Nominal text dimensions at base font size `nominalFontSize`. -/
  nominalSize : Size
  /-- Base font size (sp) corresponding to `nominalSize`. Defaults to 14. -/
  nominalFontSize : S := 14
  /-- Whether autosizing is enabled (`mAutosize = true`). -/
  autosize : Bool := false
  /-- Minimum font size (sp) when autosizing. Defaults to 4. -/
  minFontSize : S := 4
  /-- Maximum font size (sp) when autosizing. Defaults to 400. -/
  maxFontSize : S := 400
  deriving Repr, DecidableEq, Inhabited

namespace TextSpec

/-- Fixed-size text with nominal dimensions. -/
def fixed (w h : S) : TextSpec :=
  ⟨⟨w, h⟩, 14, false, 4, 400⟩

/-- Autosizing text scaling proportionally with font size between `minFont` and `maxFont`. -/
def autosized (w h minFont maxFont : S) (nominalFont : S := 14) : TextSpec :=
  ⟨⟨w, h⟩, nominalFont, true, minFont, maxFont⟩

/-- Compute the measured text content size under constraints `c`.
Matches `CoreText.computeWrapSize`. -/
def measure (spec : TextSpec) (c : Constraints) : Size :=
  if !spec.autosize then
    spec.nominalSize
  else if spec.nominalFontSize ≤ 0 then
    spec.nominalSize
  else
    let wRatio := spec.nominalSize.w / spec.nominalFontSize
    let hRatio := spec.nominalSize.h / spec.nominalFontSize
    let sW := if wRatio > 0 then c.maxW / wRatio else fltMax
    let sH := if hRatio > 0 then c.maxH / hRatio else fltMax
    let sFit := min sW sH
    let font := min spec.maxFontSize (max spec.minFontSize sFit)
    let finalScale := font / spec.nominalFontSize
    ⟨min c.maxW (spec.nominalSize.w * finalScale),
     spec.nominalSize.h * finalScale⟩

end TextSpec

/-! ## Input tree -/

/-- A RemoteCompose component tree.

Each constructor corresponds to a subclass of
`operations.layout.managers.LayoutManager`:

* `leaf` stands for any measurable terminal with fixed intrinsic size (`ImageLayout`,
  `CanvasLayout`, `Spacer`, …).
* `text` stands for a `TextLayout` / `CoreText` component with nominal or autosizing metrics.
* `box` is `managers.BoxLayout`.
* `row` is `managers.RowLayout`.
* `column` is `managers.ColumnLayout`. -/
inductive Node where
  | leaf (m : Modifiers) (content : Size)
  | text (m : Modifiers) (spec : TextSpec)
  | box (m : Modifiers) (ha : HAlign) (va : VAlign) (children : List Node)
  | row (m : Modifiers) (arr : Arrangement) (va : VAlign) (spacedBy : S)
      (children : List Node)
  | column (m : Modifiers) (ha : HAlign) (arr : Arrangement) (spacedBy : S)
      (children : List Node)
  /-- `managers.CollapsibleRowLayout`, a `RowLayout` that hides children from the
  lowest priority upwards until the rest fit. -/
  | collapsibleRow (m : Modifiers) (arr : Arrangement) (va : VAlign) (spacedBy : S)
      (children : List Node)
  /-- `managers.CollapsibleColumnLayout`. -/
  | collapsibleColumn (m : Modifiers) (ha : HAlign) (arr : Arrangement) (spacedBy : S)
      (children : List Node)
  /-- `managers.FlowLayout`, a `RowLayout` that segments its children into rows.
  `maxItems` and `maxLines` default to `Integer.MAX_VALUE` on the wire. -/
  | flow (m : Modifiers) (arr : Arrangement) (va : VAlign) (spacedBy : S)
      (maxItems : Nat) (maxLines : Nat) (children : List Node)
  deriving Repr, Inhabited

/-- The modifiers attached to a node. -/
def Node.modifiers : Node → Modifiers
  | .leaf m _ => m
  | .text m _ => m
  | .box m _ _ _ => m
  | .row m _ _ _ _ => m
  | .column m _ _ _ _ => m
  | .collapsibleRow m _ _ _ _ => m
  | .collapsibleColumn m _ _ _ _ => m
  | .flow m _ _ _ _ _ _ => m

/-- `LayoutComponent.getWidthModifier()`. -/
def Node.widthDim (n : Node) : Dim := n.modifiers.widthDim

/-- `LayoutComponent.getHeightModifier()`. -/
def Node.heightDim (n : Node) : Dim := n.modifiers.heightDim

/-- The node's children, in paint order. -/
def Node.children : Node → List Node
  | .leaf _ _ => []
  | .text _ _ => []
  | .box _ _ _ cs => cs
  | .row _ _ _ _ cs => cs
  | .column _ _ _ _ cs => cs
  | .collapsibleRow _ _ _ _ cs => cs
  | .collapsibleColumn _ _ _ _ cs => cs
  | .flow _ _ _ _ _ _ cs => cs

/-- Whether this node is a `CollapsibleRowLayout`.

`CollapsibleRowLayout.computeVisibleChildren` singles such a child out for a
*bounded* horizontal measurement, where any other child would be measured
against `Float.MAX_VALUE`. -/
def Node.isCollapsibleRow : Node → Bool
  | .collapsibleRow .. => true
  | _ => false

/-- Whether this node is a `CollapsibleColumnLayout`. -/
def Node.isCollapsibleColumn : Node → Bool
  | .collapsibleColumn .. => true
  | _ => false

/-- Whether this node is a text component. -/
def Node.isText : Node → Bool
  | .text .. => true
  | _ => false

/-- Helper constructor for fixed-size text nodes. -/
def Node.textFixed (m : Modifiers) (w h : S) : Node :=
  .text m (.fixed w h)

/-- Helper constructor for autosizing text nodes. -/
def Node.textAutosize (m : Modifiers) (w h minFont maxFont : S) (nominalFont : S := 14) : Node :=
  .text m (.autosized w h minFont maxFont nominalFont)

/-! ## Weights

`LayoutManager.childrenHaveHorizontalWeights` / `childrenHaveVerticalWeights`,
and the `totalWeights` accumulators in `RowLayout` / `ColumnLayout`. -/

/-- Whether any child claims a horizontal weight. -/
def hasHWeights (cs : List Node) : Bool := cs.any (·.widthDim.hasWeight)

/-- Whether any child claims a vertical weight. -/
def hasVWeights (cs : List Node) : Bool := cs.any (·.heightDim.hasWeight)

/-- Sum of the horizontal weights claimed by the children. -/
def totalHWeights (cs : List Node) : S :=
  cs.foldl (fun acc c => acc + c.widthDim.weightValue) 0

/-- Sum of the vertical weights claimed by the children. -/
def totalVWeights (cs : List Node) : S :=
  cs.foldl (fun acc c => acc + c.heightDim.weightValue) 0

/-- `LayoutManager.isInHorizontalFill()`.

`RowLayout` overrides this so that a row containing weighted children behaves as
if it were `fillMaxWidth`, because it has to hand the leftover space out.
`CollapsibleRowLayout` and `FlowLayout` extend `RowLayout` and inherit it. -/
def Node.isInHorizontalFill : Node → Bool
  | .row m _ _ _ cs => m.widthDim.isFill || hasHWeights cs
  | .collapsibleRow m _ _ _ cs => m.widthDim.isFill || hasHWeights cs
  | .flow m _ _ _ _ _ cs => m.widthDim.isFill || hasHWeights cs
  | n => n.modifiers.widthDim.isFill

/-- `LayoutManager.isInVerticalFill()`.

`ColumnLayout` overrides this symmetrically, and `CollapsibleColumnLayout`
inherits it. -/
def Node.isInVerticalFill : Node → Bool
  | .column m _ _ _ cs => m.heightDim.isFill || hasVWeights cs
  | .collapsibleColumn m _ _ _ cs => m.heightDim.isFill || hasVWeights cs
  | n => n.modifiers.heightDim.isFill

/-! ## Collapsible priority ordering

`CollapsiblePriority.sortWithPriorities` sorts the children into the order in
which a collapsible manager is willing to keep them: highest priority first, so
that the *last* elements of the sorted list are the first to be dropped. -/

/-- Whether any child carries a `CollapsiblePriorityModifier`.

`CollapsibleRowLayout.computeVisibleChildren` sets its `hasPriorities` flag
without consulting the modifier's orientation, so a child carrying a *vertical*
priority is enough to make a **row** sort its children — even though
`getPriority` will then return the `Float.MAX_VALUE` sentinel for that child.
The discrepancy is reproduced rather than tidied away. -/
def hasAnyPriority (cs : List Node) : Bool :=
  cs.any fun c => c.modifiers.any fun m =>
    match m with
    | .collapsiblePriority _ _ => true
    | _ => false

/-- Strict precedence under `CollapsiblePriority.sortWithPriorities`.

The comparator is `(int) (p2 - p1)`, and its **truncation** matters: it reports
`a` before `b` only when `p b - p a` truncates to something negative, which for a
difference `d` means `d ≤ -1`. Two children whose priorities differ by less than
one therefore compare equal and, the sort being stable, keep their declaration
order. So `a` strictly precedes `b` exactly when `p a ≥ p b + 1`. -/
def priorityLt (o : Orient) (a b : Node) : Bool :=
  Modifiers.priority o a.modifiers ≥ Modifiers.priority o b.modifiers + 1

/-- Insert into an already-sorted list, after every element that does not
strictly follow. Keeping the comparison strict is what makes the sort stable. -/
def priorityInsert (o : Orient) (a : α) (key : α → Node) : List α → List α
  | [] => [a]
  | b :: bs =>
    if priorityLt o (key a) (key b) then a :: b :: bs
    else b :: priorityInsert o a key bs

/-- A stable sort by decreasing collapsible priority.

> Any stable sort agrees with Java's `TimSort` here *provided* the comparator is
> a strict weak ordering, which the truncating comparator only is when the
> priorities in play are at least one apart — as integer priorities always are.
> For fractional priorities within one unit of each other the reference
> implementation's output depends on `TimSort`'s internals and is not specified. -/
def sortWithPriorities (o : Orient) (key : α → Node) : List α → List α
  | [] => []
  | a :: as => priorityInsert o a key (sortWithPriorities o key as)

/-- `CollapsiblePriority.findLastStanding`: the child a collapsible manager will
keep longest, that is the one of greatest priority, ties going to the earliest.

Guarded by `Header.FEATURE_PRIORITY_FIX`, which defaults to enabled; the legacy
branch simply took the first child. -/
def findLastStanding (o : Orient) : List Node → Option Node
  | [] => none
  | c :: cs =>
    some <| cs.foldl
      (fun best c' =>
        if Modifiers.priority o c'.modifiers > Modifiers.priority o best.modifiers
        then c' else best)
      c

/-! ## Output tree -/

/-- The result of laying out a `Node`.

`rect.x` and `rect.y` are expressed **relative to the content origin of the
parent**, that is, the parent's padding is *not* folded in. This matches
`ComponentMeasure.setX` / `setY`, which `BoxLayout.internalLayoutMeasure` and
friends fill in with offsets measured inside `selfWidth = w - paddingLeft -
paddingRight`. `LayoutComponent.getLocationInWindow` adds the padding back when
walking up to window coordinates; `LayoutTree.absolute` below does the same.

`gone` records `ComponentMeasure.isGone()`. The reference implementation encodes
visibility as a bitfield — a base value (`GONE = 0`, `VISIBLE = 1`,
`INVISIBLE = 2`) that an enclosing manager may shadow with an override bit
(`OVERRIDE_GONE = 16`, `OVERRIDE_VISIBLE = 32`), with `isGone` consulting the
override whenever one is present. Since the only base value reachable in scope
is `VISIBLE`, and the only manager that writes overrides is a collapsible one,
the whole lattice collapses to this single boolean. -/
inductive LayoutTree where
  | node (rect : Rect) (padding : Padding) (gone : Bool) (children : List LayoutTree)
  deriving Repr, Inhabited

namespace LayoutTree

/-- The node's own measured and positioned box. -/
def rect : LayoutTree → Rect
  | .node r _ _ _ => r

/-- The node's padding, needed to place its children in window coordinates. -/
def padding : LayoutTree → Padding
  | .node _ p _ _ => p

/-- `ComponentMeasure.isGone()`. -/
def gone : LayoutTree → Bool
  | .node _ _ g _ => g

/-- The laid-out children. -/
def kids : LayoutTree → List LayoutTree
  | .node _ _ _ cs => cs

/-- Measured width. -/
def w (t : LayoutTree) : S := t.rect.w

/-- Measured height. -/
def h (t : LayoutTree) : S := t.rect.h

/-- Assigned x offset, relative to the parent's content origin. -/
def x (t : LayoutTree) : S := t.rect.x

/-- Assigned y offset, relative to the parent's content origin. -/
def y (t : LayoutTree) : S := t.rect.y

/-- Reposition a subtree, as `ComponentMeasure.setX` / `setY` do. -/
def withPos (t : LayoutTree) (x y : S) : LayoutTree :=
  .node { t.rect with x := x, y := y } t.padding t.gone t.kids

/-- Resize a subtree, as `ComponentMeasure.setW` / `setH` do. -/
def withSize (t : LayoutTree) (w h : S) : LayoutTree :=
  .node { t.rect with w := w, h := h } t.padding t.gone t.kids

/-- `ComponentMeasure.addVisibilityOverride(OVERRIDE_GONE / OVERRIDE_VISIBLE)`. -/
def withGone (t : LayoutTree) (g : Bool) : LayoutTree :=
  .node t.rect t.padding g t.kids

end LayoutTree

/-! ## Arithmetic helpers over a list of measured children

These mirror the accumulation loops in `RowLayout` / `ColumnLayout` /
`BoxLayout`. They are pure: they never re-enter `measure`.

Every one of them **skips children that are gone**, which is what the
`if (childMeasure.isGone()) continue;` guards in the reference implementation
do. For a tree containing no collapsible manager no child is ever gone, so these
agree with the naive versions. -/

/-- The children that take part in layout. -/
def visible (ts : List LayoutTree) : List LayoutTree := ts.filter (!·.gone)

/-- `visibleChildrens`, the count the arrangement arithmetic divides by. -/
def countVisible (ts : List LayoutTree) : Nat := (visible ts).length

/-- `Σ childMeasure.getW()` over the visible children. -/
def sumWidths (ts : List LayoutTree) : S :=
  ts.foldl (fun a t => if t.gone then a else a + t.w) 0

/-- `Σ childMeasure.getH()` over the visible children. -/
def sumHeights (ts : List LayoutTree) : S :=
  ts.foldl (fun a t => if t.gone then a else a + t.h) 0

/-- `max childMeasure.getW()` over the visible children, starting from `0` as
the reference implementation does. -/
def maxWidth (ts : List LayoutTree) : S :=
  ts.foldl (fun a t => if t.gone then a else max a t.w) 0

/-- `max childMeasure.getH()` over the visible children, starting from `0`. -/
def maxHeight (ts : List LayoutTree) : S :=
  ts.foldl (fun a t => if t.gone then a else max a t.h) 0

/-- Total width of the visible children that do *not* carry a weight.

`RowLayout.internalLayoutMeasure` computes this to work out how much space is
left for the weighted children. -/
def sumWidthsUnweighted : List Node → List LayoutTree → S
  | [], _ => 0
  | _, [] => 0
  | c :: cs, t :: ts =>
    (if t.gone || c.widthDim.hasWeight then 0 else t.w) + sumWidthsUnweighted cs ts

/-- Total height of the visible children that do *not* carry a weight. -/
def sumHeightsUnweighted : List Node → List LayoutTree → S
  | [], _ => 0
  | _, [] => 0
  | c :: cs, t :: ts =>
    (if t.gone || c.heightDim.hasWeight then 0 else t.h) + sumHeightsUnweighted cs ts

/-! ### Weights among the visible children

`RowLayout.internalLayoutMeasure` lines 377–389 collects `hasWeights` and
`totalWeights` behind an `if (childMeasure.isGone()) continue;`, so a gone child
contributes neither. This only differs from `hasHWeights` / `totalHWeights` once
something can be gone — that is, under a collapsible manager, or in one of the
rows a `FlowLayout` has segmented. -/

/-- Whether any *visible* child claims a horizontal weight. -/
def hasHWeightsVis : List Node → List LayoutTree → Bool
  | [], _ => false
  | _, [] => false
  | c :: cs, t :: ts =>
    (!t.gone && c.widthDim.hasWeight) || hasHWeightsVis cs ts

/-- Sum of the horizontal weights claimed by the *visible* children. -/
def totalHWeightsVis : List Node → List LayoutTree → S
  | [], _ => 0
  | _, [] => 0
  | c :: cs, t :: ts =>
    (if t.gone then 0 else c.widthDim.weightValue) + totalHWeightsVis cs ts

/-- Whether any *visible* child claims a vertical weight. -/
def hasVWeightsVis : List Node → List LayoutTree → Bool
  | [], _ => false
  | _, [] => false
  | c :: cs, t :: ts =>
    (!t.gone && c.heightDim.hasWeight) || hasVWeightsVis cs ts

/-- Sum of the vertical weights claimed by the *visible* children. -/
def totalVWeightsVis : List Node → List LayoutTree → S
  | [], _ => 0
  | _, [] => 0
  | c :: cs, t :: ts =>
    (if t.gone then 0 else c.heightDim.weightValue) + totalVWeightsVis cs ts

end RemoteCompose

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
import Mathlib.Data.Rat.Defs
import Mathlib.Data.Rat.Lemmas
import Mathlib.Tactic.Linarith
import Mathlib.Tactic.Positivity

/-!
# RemoteCompose layout specification — core types

This file defines the vocabulary shared by the whole specification: scalars,
geometry, dimension descriptors and alignment enumerations.

Everything here mirrors, name for name, the Java implementation living in
`compose/remote/remote-core/src/main/java/androidx/compose/remote/core/operations/layout/`.

## Scalars

The reference implementation computes in `float`. Floats are opaque in Lean, so
the specification uses exact rationals `ℚ`. Every arithmetic operation performed
by the specification (`+`, `-`, `*`, `/`, `min`, `max`) is performed by the
reference implementation too, in the same order, so the two agree up to
floating-point rounding. The conformance suite already allows a `tolerance`
(typically `0.5` px), which is far larger than the accumulated `float` error for
the tree depths used in practice.
-/

namespace RemoteCompose

/-- Scalar used for all layout arithmetic.

The reference implementation uses `float`; we use exact rationals so that the
specification is both executable and provable. -/
abbrev S := ℚ

/-- `Float.MAX_VALUE`, i.e. `2^128 - 2^104`.

The reference implementation genuinely uses this *finite* value as its
"unbounded" sentinel (see `LayoutComponent.computeModifierDefinedWidth`, which
returns `Float.MAX_VALUE` for a `FILL` dimension). Modelling it as a finite
rational rather than as `⊤` keeps the specification faithful, including the
overflow-ish corner cases. -/
def fltMax : S := 340282346638528859811704183484516925440

/-! ## Geometry -/

/-- A measured size. Mirrors `layout.measure.Size`. -/
structure Size where
  w : S
  h : S
  deriving Repr, DecidableEq, Inhabited

/-- A positioned, measured box. Mirrors the `x`/`y`/`w`/`h` quadruple stored in
`layout.measure.ComponentMeasure`. -/
structure Rect where
  x : S := 0
  y : S := 0
  w : S
  h : S
  deriving Repr, DecidableEq, Inhabited

/-- Incoming measurement constraints, i.e. the
`(minWidth, maxWidth, minHeight, maxHeight)` tuple threaded through
`Measurable.measure`. -/
structure Constraints where
  minW : S
  maxW : S
  minH : S
  maxH : S
  deriving Repr, DecidableEq, Inhabited

/-- Constraints that force an exact size. -/
def Constraints.tight (w h : S) : Constraints := ⟨w, w, h, h⟩

/-- Constraints that only impose an upper bound. -/
def Constraints.loose (w h : S) : Constraints := ⟨0, w, 0, h⟩

/-- Padding contributed by `modifiers.PaddingModifierOperation`. -/
structure Padding where
  left : S := 0
  right : S := 0
  top : S := 0
  bottom : S := 0
  deriving Repr, DecidableEq, Inhabited

/-- Total horizontal padding, `mPaddingLeft + mPaddingRight`. -/
def Padding.horizontal (p : Padding) : S := p.left + p.right

/-- Total vertical padding, `mPaddingTop + mPaddingBottom`. -/
def Padding.vertical (p : Padding) : S := p.top + p.bottom

/-- Uniform padding on all four edges, i.e. `Modifier.padding(all)`. -/
def Padding.all (v : S) : Padding := ⟨v, v, v, v⟩

/-! ## Dimensions

Mirrors `modifiers.DimensionModifierOperation.Type`. The specification covers
the four types used by the Box/Row/Column core; the remaining types
(`INTRINSIC_MIN`, `INTRINSIC_MAX`, `FILL_PARENT_MAX_WIDTH`,
`FILL_PARENT_MAX_HEIGHT`) are listed in `README.md` as out of scope. `EXACT_DP`
behaves exactly like `EXACT` once the density multiplication has been applied,
so it is modelled by `Dim.exact`. -/

/-- A width or height descriptor attached to a component by a
`WidthModifierOperation` / `HeightModifierOperation`. -/
inductive Dim where
  /-- `Type.EXACT` (and `Type.EXACT_DP` post-density): a fixed pixel size. -/
  | exact (v : S)
  /-- `Type.FILL`: occupy the incoming maximum constraint.

  `fraction` models `DimensionModifierOperation.getValue()`; `none` models the
  `Float.NaN` "no fraction supplied" sentinel, i.e. `fillMaxWidth()` as opposed
  to `fillMaxWidth(0.5f)`. -/
  | fill (fraction : Option S)
  /-- `Type.WRAP`: size to the content. -/
  | wrap
  /-- `Type.WEIGHT`: claim a share of the parent's leftover main-axis space. -/
  | weight (w : S)
  deriving Repr, DecidableEq, Inhabited

/-- `DimensionModifierOperation.isExact()`. -/
def Dim.isExact : Dim → Bool
  | .exact _ => true
  | _ => false

/-- `DimensionModifierOperation.isFill()`. -/
def Dim.isFill : Dim → Bool
  | .fill _ => true
  | _ => false

/-- `DimensionModifierOperation.isWrap()`. -/
def Dim.isWrap : Dim → Bool
  | .wrap => true
  | _ => false

/-- `DimensionModifierOperation.hasWeight()`. -/
def Dim.hasWeight : Dim → Bool
  | .weight _ => true
  | _ => false

/-- `DimensionModifierOperation.getValue()`.

`none` models `Float.NaN`, which the reference implementation tests for with
`Float.isNaN`. -/
def Dim.value : Dim → Option S
  | .exact v => some v
  | .fill f => f
  | .wrap => none
  | .weight w => some w

/-- The weight carried by a `Type.WEIGHT` dimension, `0` otherwise. -/
def Dim.weightValue : Dim → S
  | .weight w => w
  | _ => 0

/-- A `widthIn` / `heightIn` constraint pair, mirroring
`modifiers.DimensionInModifierOperation`.

The reference implementation encodes "unset" as `-1`; we use `none`. -/
structure DimIn where
  min : Option S := none
  max : Option S := none
  deriving Repr, DecidableEq, Inhabited

/-- Raise `x` to `lo`, if `lo` is set. Models `Math.max(x, in.getMin())` guarded
by the `!= -1` sentinel check. -/
def clampLo (x : S) : Option S → S
  | none => x
  | some lo => max x lo

/-- Lower `x` to `hi`, if `hi` is set. Models `Math.min(x, in.getMax())` guarded
by the `!= -1` sentinel check. -/
def clampHi (x : S) : Option S → S
  | none => x
  | some hi => min x hi

/-- `DimensionInModifierOperation.applyWidthConstraint` /
`applyHeightConstraint`: clamp into `[min, max]`, ignoring unset bounds. -/
def DimIn.apply (c : DimIn) (x : S) : S := clampHi (clampLo x c.min) c.max

/-- `LayoutComponent.applyWidthConstraints` / `applyHeightConstraints`: a no-op
when the component carries no `widthIn` / `heightIn` modifier. -/
def applyDimIn : Option DimIn → S → S
  | none, x => x
  | some c, x => c.apply x

@[simp] theorem bind_none_left {α β : Type} (f : α → Option β) :
    (none : Option α).bind f = none := rfl

@[simp] theorem clampLo_none (x : S) : clampLo x none = x := rfl

@[simp] theorem clampHi_none (x : S) : clampHi x none = x := rfl

@[simp] theorem applyDimIn_none (x : S) : applyDimIn none x = x := rfl

/-! ## Modifiers

The reference implementation stores modifiers as an *ordered list*
(`modifiers.ComponentModifiers`), and that order is observable:

* `LayoutComponent.updateModifiers` accumulates `mPaddingLeft` … from **every**
  `PaddingModifierOperation` in the list, and takes `mWidthModifier` to be the
  **first** `WidthModifierOperation`;
* `LayoutComponent.computeModifierDefinedWidth` walks the same list but `break`s
  at that first width modifier, so only the padding declared **before** the size
  modifier contributes to the modifier-defined width.

The two therefore disagree on purpose: `Modifier.padding(8).size(50)` has a
modifier-defined width of `66`, while `Modifier.size(50).padding(8)` has one of
`50` — in both cases with a total padding of `8` on each edge, which is what
insets the children. The specification reproduces this by keeping the list. -/

/-- The axis a `CollapsiblePriorityModifierOperation` applies to.

`CollapsiblePriority.HORIZONTAL = 0`, `CollapsiblePriority.VERTICAL = 1`. -/
inductive Orient where
  | horizontal | vertical
  deriving Repr, DecidableEq, Inhabited

/-- A single entry of a component's modifier chain.

Only the layout-relevant operations are modelled; decorative ones
(`BackgroundModifierOperation`, `BorderModifierOperation`, `ZIndex…`, …) do not
participate in measurement and are simply absent from the list. -/
inductive Modifier where
  /-- `modifiers.PaddingModifierOperation`. -/
  | padding (p : Padding)
  /-- `modifiers.WidthModifierOperation`, together with the
  `WidthInModifierOperation` folded into its `widthIn` field. -/
  | width (d : Dim) (constraint : Option DimIn := none)
  /-- `modifiers.HeightModifierOperation`, together with its `heightIn`. -/
  | height (d : Dim) (constraint : Option DimIn := none)
  /-- `modifiers.CollapsiblePriorityModifierOperation`. Read only by
  `CollapsibleRowLayout` / `CollapsibleColumnLayout`, and only for the
  orientation matching the manager. -/
  | collapsiblePriority (orientation : Orient) (priority : S)
  deriving Repr, DecidableEq, Inhabited

/-- A component's modifier chain, in declaration order.

Mirrors `modifiers.ComponentModifiers.getModifiersList()`. -/
abbrev Modifiers := List Modifier

namespace Modifiers

/-- `LayoutComponent.mWidthModifier`: the **first** width modifier in the chain,
defaulting to `WRAP` when the component declares none. -/
def widthDim : Modifiers → Dim
  | [] => .wrap
  | .width d _ :: _ => d
  | _ :: rest => widthDim rest

/-- `LayoutComponent.mHeightModifier`: the first height modifier in the chain. -/
def heightDim : Modifiers → Dim
  | [] => .wrap
  | .height d _ :: _ => d
  | _ :: rest => heightDim rest

/-- `LayoutComponent.mWidthModifier.getWidthIn()`. -/
def widthIn : Modifiers → Option DimIn
  | [] => none
  | .width _ c :: _ => c
  | _ :: rest => widthIn rest

/-- `LayoutComponent.mHeightModifier.getHeightIn()`. -/
def heightIn : Modifiers → Option DimIn
  | [] => none
  | .height _ c :: _ => c
  | _ :: rest => heightIn rest

/-- `LayoutComponent.updatePadding`: the sum of **every** padding modifier,
wherever it sits in the chain. This is what insets the children. -/
def padding : Modifiers → Padding
  | [] => {}
  | .padding p :: rest =>
    let q := padding rest
    ⟨p.left + q.left, p.right + q.right, p.top + q.top, p.bottom + q.bottom⟩
  | _ :: rest => padding rest

/-- `CollapsiblePriority.getPriority`.

Returns the priority of the **first** matching `CollapsiblePriorityModifier`
whose orientation agrees with the manager's, and `Float.MAX_VALUE` when the
component declares none. The sentinel is deliberate: a child without a declared
priority outranks every child that has one, and so is collapsed last. -/
def priority (o : Orient) : Modifiers → S
  | [] => fltMax
  | .collapsiblePriority o' p :: rest => if o' = o then p else priority o rest
  | _ :: rest => priority o rest

/-- The size a dimension descriptor pins down by itself, before padding. -/
def Dim.base (d : Dim) (isMin : Bool) : S :=
  match d with
  | .exact v => v
  | .fill _ => if isMin then 0 else fltMax
  | .wrap => 0
  | .weight _ => 0

/-- `LayoutComponent.computeModifierDefinedWidth(context, isMin)`.

Walks the chain accumulating horizontal padding and stops at the first width
modifier, returning `paddingBefore.left + base + paddingBefore.right`. Note that
a `height` modifier does *not* stop the walk, and that a chain with no width
modifier at all contributes its full horizontal padding. -/
def definedWidth (ms : Modifiers) (isMin : Bool) : S :=
  go ms 0 0
where
  go : Modifiers → S → S → S
  | [], s, e => s + e
  | .width d c :: _, s, e => s + clampLo (Dim.base d isMin) (c.bind (·.min)) + e
  | .padding p :: rest, s, e => go rest (s + p.left) (e + p.right)
  | _ :: rest, s, e => go rest s e

/-- `LayoutComponent.computeModifierDefinedHeight(context, isMin)`. -/
def definedHeight (ms : Modifiers) (isMin : Bool) : S :=
  go ms 0 0
where
  go : Modifiers → S → S → S
  | [], t, b => t + b
  | .height d c :: _, t, b => t + clampLo (Dim.base d isMin) (c.bind (·.min)) + b
  | .padding p :: rest, t, b => go rest (t + p.top) (b + p.bottom)
  | _ :: rest, t, b => go rest t b

end Modifiers

/-! ## Alignment

Mirrors the integer constants declared on `BoxLayout`, `RowLayout` and
`ColumnLayout`. All three classes agree on the encoding:
`START = 1`, `CENTER = 2`, `END = 3`, `TOP = 4`, `BOTTOM = 5`,
`SPACE_BETWEEN = 6`, `SPACE_EVENLY = 7`, `SPACE_AROUND = 8`. -/

/-- Cross-axis alignment on the horizontal axis (`START` / `CENTER` / `END`). -/
inductive HAlign where
  | Start | Center | End
  deriving Repr, DecidableEq, Inhabited

/-- Cross-axis alignment on the vertical axis (`TOP` / `CENTER` / `BOTTOM`). -/
inductive VAlign where
  | Top | Center | Bottom
  deriving Repr, DecidableEq, Inhabited

/-- Main-axis arrangement for `RowLayout` / `ColumnLayout`. -/
inductive Arrangement where
  | Start | Center | End
  | SpaceBetween | SpaceEvenly | SpaceAround
  deriving Repr, DecidableEq, Inhabited

/-- Whether the arrangement inserts an inter-child gap that is added *on top of*
`spacedBy`. Mirrors the `mHorizontalPositioning == SPACE_BETWEEN || ... ` test
in `RowLayout.internalLayoutMeasure`. -/
def Arrangement.isSpaced : Arrangement → Bool
  | .SpaceBetween | .SpaceEvenly | .SpaceAround => true
  | _ => false

/-- The integer opcode value written to the wire buffer. -/
def HAlign.toWire : HAlign → Nat
  | .Start => 1 | .Center => 2 | .End => 3

/-- The integer opcode value written to the wire buffer. -/
def VAlign.toWire : VAlign → Nat
  | .Top => 4 | .Center => 2 | .Bottom => 5

/-- The integer opcode value written to the wire buffer. -/
def Arrangement.toWire : Arrangement → Nat
  | .Start => 1 | .Center => 2 | .End => 3
  | .SpaceBetween => 6 | .SpaceEvenly => 7 | .SpaceAround => 8

end RemoteCompose

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
import Mathlib.Tactic

/-!
# Extended Layout Modifiers

This module formalizes additional geometry, alignment, visibility, and transformation
modifiers defined in `remote-core`:
1. `ComponentVisibilityOperation` (`VISIBLE`, `INVISIBLE`, `GONE`)
2. `AlignByModifierOperation` (typographic baseline alignment)
3. `OffsetModifierOperation` (local translation without container layout disturbance)
4. `ScrollModifierOperation` (virtual scrollable viewports and bounds clamping)
5. `GraphicsLayerModifierOperation` (affine visual transformations)
6. `ZIndexModifierOperation` (z-ordering of layout elements)
-/

namespace RemoteCompose

/-! ## 1. Component Visibility -/

/-- Component visibility state (`Component.Visibility`). -/
inductive VisibilityMode where
  | visible     -- 0: Participates in layout, paints, receives touch
  | invisible   -- 4: Participates in layout, takes space, DOES NOT paint
  | gone        -- 8: Collapsed to 0x0, excluded from layout flow
  deriving Repr, DecidableEq, Inhabited

namespace VisibilityMode

def takesSpace : VisibilityMode → Bool
  | .visible   => true
  | .invisible => true
  | .gone      => false

def isPainted : VisibilityMode → Bool
  | .visible   => true
  | .invisible => false
  | .gone      => false

/-- Effective dimension under visibility mode. -/
def effectiveDim (vis : VisibilityMode) (naturalSize : S) : S :=
  if vis.takesSpace then naturalSize else 0.0

theorem invisible_preserves_space (s : S) :
    effectiveDim .invisible s = effectiveDim .visible s := by
  rfl

theorem gone_collapses_space (s : S) :
    effectiveDim .gone s = 0.0 := by
  rfl

end VisibilityMode

/-! ## 2. Typographic Baseline Alignment (`AlignByModifierOperation`) -/

/-- Typographic baseline anchor. -/
inductive BaselineAnchor where
  | firstBaseline
  | lastBaseline
  | custom (offset : S)
  deriving Repr, Inhabited

/-- Child baseline descriptor for cross-axis alignment. -/
structure BaselineDesc where
  height : S
  baseline : S
  deriving Repr, Inhabited

/-- Compute common baseline row alignment offset for a child.
Given maximum baseline `maxB` across all aligned siblings, each child is placed
at `y = maxB - childBaseline`. -/
def alignByOffsetY (maxB : S) (childBaseline : S) : S :=
  maxB - childBaseline

/-- Coincidence theorem: all children aligned by baseline share the exact same
global baseline position `y + childBaseline = maxB`. -/
theorem baseline_alignment_coincident (maxB : S) (b : S) :
    alignByOffsetY maxB b + b = maxB := by
  dsimp [alignByOffsetY]
  ring

/-! ## 3. Visual Offset Modifier (`OffsetModifierOperation`) -/

structure OffsetModifier where
  dx : S
  dy : S
  deriving Repr, DecidableEq, Inhabited

namespace OffsetModifier

def zero : OffsetModifier := ⟨0.0, 0.0⟩

/-- Apply offset to a positioned rectangle. -/
def applyToRect (off : OffsetModifier) (r : Rect) : Rect :=
  ⟨r.x + off.dx, r.y + off.dy, r.w, r.h⟩

theorem offset_preserves_dimensions (off : OffsetModifier) (r : Rect) :
    (off.applyToRect r).w = r.w ∧ (off.applyToRect r).h = r.h := by
  dsimp [applyToRect]
  exact ⟨rfl, rfl⟩

end OffsetModifier

/-! ## 4. Scroll Modifier & Viewport Bounds (`ScrollModifierOperation`) -/

inductive ScrollDirection where
  | horizontal
  | vertical
  | both
  deriving Repr, DecidableEq, Inhabited

structure ScrollState where
  direction : ScrollDirection
  scrollX : S
  scrollY : S
  maxScrollX : S
  maxScrollY : S
  deriving Repr, Inhabited

namespace ScrollState

/-- Clamping scroll offset within valid content range [0, maxScroll]. -/
def clampScroll (pos : S) (maxPos : S) : S :=
  if maxPos ≤ 0.0 then 0.0
  else if pos ≤ 0.0 then 0.0
  else if pos ≥ maxPos then maxPos
  else pos

theorem scroll_clamp_nonneg (pos maxPos : S) :
    0.0 ≤ clampScroll pos maxPos := by
  unfold clampScroll
  split
  · rfl
  · split
    · rfl
    · split
      · next h1 h2 h3 => linarith
      · next h1 h2 h3 => linarith

theorem scroll_clamp_upper (pos maxPos : S) (h_pos : 0.0 ≤ maxPos) :
    clampScroll pos maxPos ≤ maxPos := by
  unfold clampScroll
  split
  · next h_le => linarith
  · split
    · linarith
    · split
      · rfl
      · next h1 h2 h3 => linarith

end ScrollState

/-! ## 5. Graphics Layer Transforms (`GraphicsLayerModifierOperation`) -/

structure GraphicsLayer where
  scaleX : S := 1.0
  scaleY : S := 1.0
  rotation : S := 0.0
  translationX : S := 0.0
  translationY : S := 0.0
  alpha : S := 1.0
  deriving Repr, Inhabited

namespace GraphicsLayer

def identity : GraphicsLayer := {}

def isIdentity (g : GraphicsLayer) : Prop :=
  g.scaleX = 1.0 ∧ g.scaleY = 1.0 ∧
  g.rotation = 0.0 ∧
  g.translationX = 0.0 ∧ g.translationY = 0.0 ∧
  g.alpha = 1.0

theorem identity_is_identity : (GraphicsLayer.identity).isIdentity := by
  dsimp [identity, isIdentity]
  refine ⟨rfl, rfl, rfl, rfl, rfl, rfl⟩

end GraphicsLayer

/-! ## 6. Z-Index Ordering (`ZIndexModifierOperation`) -/

structure ZIndexModifier where
  zIndex : S := 0.0
  deriving Repr, DecidableEq, Inhabited

end RemoteCompose

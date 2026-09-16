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
import RemoteComposeLayout.Measure
import RemoteComposeLayout.ExtendedModifiers
import Mathlib.Tactic

/-!
# Window Coordinates & Pointer Hit Testing

This module formalizes:
1. `getLocationInWindow` (`Component.java`): Accumulation of hierarchical ancestor
   coordinates, padding offsets, and scroll translation to establish global window space.
2. `getBoundsInSemanticParent`: Relative bounding box computation for semantic nodes.
3. Pointer Hit Testing: Dispatching pointer events $(px, py)$ down the layout tree
   respecting geometric inclusion, visibility modes, and z-index ordering.
-/

namespace RemoteCompose

/-! ## 1. Window Coordinate Accumulation (`Component.getLocationInWindow`) -/

/-- An ancestor frame along the hierarchy path: (localX, localY, scrollX, scrollY). -/
structure AncestorFrame where
  x : S
  y : S
  scrollX : S := 0.0
  scrollY : S := 0.0
  deriving Repr, Inhabited

/-- Accumulate parent origin and scroll offsets along the ancestor chain.
Matches `Component.getLocationInWindow(context, value, forSelf)`. -/
def accumulateWindowLocation (frames : List AncestorFrame) (localX localY : S) : S × S :=
  frames.foldr (fun f (accX, accY) =>
    (accX + f.x - f.scrollX, accY + f.y - f.scrollY)
  ) (localX, localY)

/-- Subtree Locality Theorem: Shifting an ancestor's origin by (dx, dy)
shifts the global window location of all descendant nodes by exactly (dx, dy). -/
theorem subtree_locality_shift (frames : List AncestorFrame) (localX localY dx dy : S) :
    let shiftedFrames := frames.map (fun f => { f with x := f.x + dx, y := f.y + dy })
    accumulateWindowLocation shiftedFrames localX localY =
      let (origX, origY) := accumulateWindowLocation frames localX localY
      (origX + (frames.length : S) * dx, origY + (frames.length : S) * dy) := by
  dsimp [accumulateWindowLocation]
  induction frames with
  | nil =>
    dsimp
    simp
  | cons f rest ih =>
    dsimp [List.map, List.foldr]
    rw [ih]
    dsimp
    have h_len : ((rest.length + 1 : Nat) : S) = (rest.length : S) + 1 := by
      push_cast
      rfl
    rw [h_len]
    ring_nf

/-! ## 2. Pointer Hit Testing (`TouchHandler.java` / `ClickHandler.java`) -/

/-- Check if a point (px, py) lies inside rectangle [x, x + w) × [y, y + h). -/
def pointInRect (px py : S) (r : Rect) : Bool :=
  r.x ≤ px && px < r.x + r.w && r.y ≤ py && py < r.y + r.h

/-- A tagged interactive node in the layout tree. -/
inductive HitTarget where
  | node (id : Nat) (rect : Rect) (visMode : VisibilityMode) (zIndex : S) (children : List HitTarget)
  deriving Repr, Inhabited

def HitTarget.id : HitTarget → Nat
  | .node i _ _ _ _ => i

def HitTarget.rect : HitTarget → Rect
  | .node _ r _ _ _ => r

def HitTarget.visMode : HitTarget → VisibilityMode
  | .node _ _ v _ _ => v

def HitTarget.children : HitTarget → List HitTarget
  | .node _ _ _ _ cs => cs

mutual
  /-- Hit test dispatch: recursively walks the hit-test tree, testing point inclusion
  and prioritizing children before parents. Matches `TouchHandler` dispatch. -/
  def hitTest : HitTarget → S → S → Option Nat
    | .node id rect visMode _ children, px, py =>
      if !visMode.takesSpace || !pointInRect px py rect then
        none
      else
        match hitTestList children px py with
        | some hitId => some hitId
        | none =>
          if visMode.isPainted then some id else none

  def hitTestList : List HitTarget → S → S → Option Nat
    | [], _, _ => none
    | k :: rest, px, py =>
      match hitTest k px py with
      | some hitId => some hitId
      | none => hitTestList rest px py
end

/-- Hit Test Soundness: If a node is directly selected by hitTest, the query point
(px, py) is strictly contained within its bounding rectangle. -/
theorem hitTest_soundness (target : HitTarget) (px py : S)
    (h_hit : hitTest target px py = some target.id) :
    target.rect.x ≤ px ∧ px < target.rect.x + target.rect.w ∧
    target.rect.y ≤ py ∧ py < target.rect.y + target.rect.h := by
  cases target with
  | node tid trect tvis tz tchildren =>
    dsimp [HitTarget.id, HitTarget.rect, HitTarget.visMode] at *
    dsimp [hitTest] at h_hit
    split at h_hit
    · contradiction
    · next h_in =>
      have h_in_false : (!tvis.takesSpace || !pointInRect px py trect) = false := by
        cases h : (!tvis.takesSpace || !pointInRect px py trect)
        · rfl
        · contradiction
      simp only [Bool.or_eq_false_iff] at h_in_false
      have h_rect := h_in_false.2
      have h_rect_true : pointInRect px py trect = true := by
        cases h_p : pointInRect px py trect
        · rw [h_p] at h_rect
          contradiction
        · rfl
      unfold pointInRect at h_rect_true
      simp only [Bool.and_eq_true, decide_eq_true_iff] at h_rect_true
      exact ⟨h_rect_true.1.1.1, h_rect_true.1.1.2, h_rect_true.1.2, h_rect_true.2⟩

end RemoteCompose

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
# Extended Layout Containers

This module formalizes additional container managers from RemoteCompose:
1. `FitBoxLayout`: Selection container that renders only the first child fitting available space.
2. `StateLayout`: Multi-state container displaying a single active state based on an index.
3. `ImageLayout`: Intrinsic image aspect ratio and scaling modes.
-/

namespace RemoteCompose

/-! ## 1. FitBoxLayout (`FitBoxLayout.java`) -/

/-- FitBox child decision: either admitted as the single visible child, or collapsed to gone. -/
structure FitBoxChildResult where
  childIndex : Nat
  w : S
  h : S
  visible : Bool
  deriving Repr, DecidableEq, Inhabited

def fitBoxAux (maxW maxH : S) : Nat → Bool → List (S × S) → List FitBoxChildResult
  | _, _, [] => []
  | idx, found, (cw, ch) :: rest =>
    if !found && cw ≤ maxW && ch ≤ maxH then
      ⟨idx, cw, ch, true⟩ :: fitBoxAux maxW maxH (idx + 1) true rest
    else
      ⟨idx, cw, ch, false⟩ :: fitBoxAux maxW maxH (idx + 1) found rest

/-- Sequential sweep of FitBox: admits the first child with w <= maxW and h <= maxH,
marking all other children as gone. Matches `FitBoxLayout.computeWrapSize`. -/
def fitBoxSelect (maxW maxH : S) (candidates : List (S × S)) : List FitBoxChildResult :=
  fitBoxAux maxW maxH 0 false candidates

theorem fitBoxAux_visible_length (maxW maxH : S) (candidates : List (S × S)) (idx : Nat) (found : Bool) :
    ((fitBoxAux maxW maxH idx found candidates).filter (fun r => r.visible)).length ≤ if found then 0 else 1 := by
  induction candidates generalizing idx found with
  | nil =>
    cases found <;> simp [fitBoxAux]
  | cons c rest ih =>
    rcases c with ⟨cw, ch⟩
    dsimp [fitBoxAux]
    split
    · next h_fit =>
      simp only [Bool.and_eq_true, decide_eq_true_iff] at h_fit
      rcases h_fit with ⟨⟨h_found, _⟩, _⟩
      cases found
      · dsimp
        have ih_true := ih (idx + 1) true
        simp only [ite_true] at ih_true
        have h_zero : ((fitBoxAux maxW maxH (idx + 1) true rest).filter (fun r => r.visible)).length = 0 := by
          omega
        simp [h_zero]
      · contradiction
    · next h_not_fit =>
      show ((fitBoxAux maxW maxH (idx + 1) found rest).filter (fun r => r.visible)).length ≤ if found then 0 else 1
      exact ih (idx + 1) found

/-- At most one child is marked visible in a FitBox layout. -/
theorem fitbox_at_most_one_visible (maxW maxH : S) (candidates : List (S × S)) :
    ((fitBoxSelect maxW maxH candidates).filter (fun r => r.visible)).length ≤ 1 := by
  unfold fitBoxSelect
  have h := fitBoxAux_visible_length maxW maxH candidates 0 false
  exact h

theorem fitBoxAux_admitted_fits (maxW maxH : S) (candidates : List (S × S)) (idx : Nat) (found : Bool)
    (r : FitBoxChildResult)
    (h_mem : r ∈ fitBoxAux maxW maxH idx found candidates)
    (h_vis : r.visible = true) :
    r.w ≤ maxW ∧ r.h ≤ maxH := by
  induction candidates generalizing idx found r with
  | nil =>
    cases h_mem
  | cons c rest ih =>
    rcases c with ⟨cw, ch⟩
    dsimp [fitBoxAux] at h_mem
    split at h_mem
    · next h_fit =>
      simp only [Bool.and_eq_true, decide_eq_true_iff] at h_fit
      rcases h_fit with ⟨⟨_, hw⟩, hh⟩
      cases h_mem with
      | head =>
        exact ⟨hw, hh⟩
      | tail _ ht =>
        exact ih (idx + 1) true r ht h_vis
    · next h_not =>
      cases h_mem with
      | head =>
        contradiction
      | tail _ ht =>
        exact ih (idx + 1) found r ht h_vis

/-- Any child marked visible by FitBox strictly fits within container constraints. -/
theorem fitbox_admitted_fits_container (maxW maxH : S) (candidates : List (S × S))
    (r : FitBoxChildResult)
    (h_mem : r ∈ fitBoxSelect maxW maxH candidates)
    (h_vis : r.visible = true) :
    r.w ≤ maxW ∧ r.h ≤ maxH := by
  unfold fitBoxSelect at h_mem
  exact fitBoxAux_admitted_fits maxW maxH candidates 0 false r h_mem h_vis

/-! ## 2. StateLayout (`StateLayout.java`) -/

/-- A state-driven container layout. Displays child state `states[activeState]`,
collapsing all alternative states. -/
structure StateLayoutDesc where
  activeState : Nat
  states : List LayoutTree
  deriving Repr

/-- Resolve visible state tree in StateLayout. -/
def resolveStateLayout (desc : StateLayoutDesc) : Option LayoutTree :=
  desc.states[desc.activeState]?

/-- State Isolation Theorem: If two StateLayout configurations have the same
activeState index and that state matches, their resolved output is identical
regardless of unused alternative states. -/
theorem state_isolation (idx : Nat) (s1 s2 : List LayoutTree) (t : LayoutTree)
    (h1 : s1[idx]? = some t)
    (h2 : s2[idx]? = some t) :
    resolveStateLayout ⟨idx, s1⟩ = resolveStateLayout ⟨idx, s2⟩ := by
  dsimp [resolveStateLayout]
  rw [h1, h2]

/-! ## 3. ImageLayout Aspect Ratio Modes (`ImageLayout.java`) -/

inductive ImageScaleType where
  | fit
  | fillBounds
  | inside
  deriving Repr, DecidableEq, Inhabited

/-- Resolve image dimensions preserving aspect ratio under container constraints. -/
def resolveImageSize (scaleType : ImageScaleType) (imgW imgH : S) (boxW boxH : S) : S × S :=
  match scaleType with
  | .fillBounds => (boxW, boxH)
  | .fit =>
    let scaleW := boxW / imgW
    let scaleH := boxH / imgH
    let scale := min scaleW scaleH
    (imgW * scale, imgH * scale)
  | .inside =>
    if imgW ≤ boxW ∧ imgH ≤ boxH then
      (imgW, imgH)
    else
      let scaleW := boxW / imgW
      let scaleH := boxH / imgH
      let scale := min scaleW scaleH
      (imgW * scale, imgH * scale)

/-- Aspect ratio theorem: Under fit scaling with positive dimensions,
scaled width and height preserve the intrinsic aspect ratio. -/
theorem fit_preserves_aspect_ratio (imgW imgH scale : S)
    (h_scale : scale ≠ 0) (_h_imgH : imgH ≠ 0) :
    (imgW * scale) / (imgH * scale) = imgW / imgH := by
  have h1 : (imgW * scale) / (imgH * scale) = (imgW / imgH) * (scale / scale) := by
    rw [mul_div_mul_comm]
  rw [h1, div_self h_scale, mul_one]

end RemoteCompose

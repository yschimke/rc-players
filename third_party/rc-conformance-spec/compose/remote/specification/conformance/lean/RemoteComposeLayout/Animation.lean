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
import RemoteComposeLayout.ExtendedModifiers
import Mathlib.Tactic

/-!
# Layout Animations & Temporal Interpolation

This module formalizes layout animation mechanisms from `AnimateMeasure.java`:
1. Temporal progress mapping: Clamped linear and normalized progress $p(t) \in [0, 1]$.
2. Rectangle interpolation: Continuous transition from origin bounds to target bounds.
3. Enter and exit transition descriptors: Slide, Fade, and Rotate effects.
4. Boundary continuity theorems: Verification of exact start and target arrival.
-/

namespace RemoteCompose

/-! ## 1. Animation Progress & Easing -/

/-- Normalized animation progress over duration T > 0.
Matches `FloatAnimation.get(currentTime)`. -/
def animProgress (t : S) (duration : S) : S :=
  if duration ≤ 0 then 1
  else if t ≤ 0 then 0
  else if t ≥ duration then 1
  else t / duration

theorem animProgress_nonneg (t duration : S) :
    0 ≤ animProgress t duration := by
  unfold animProgress
  split
  · linarith
  · split
    · rfl
    · split
      · linarith
      · next h_dur h_t _ =>
        have h_t_pos : 0 < t := lt_of_not_ge h_t
        have h_dur_pos : 0 < duration := lt_of_not_ge h_dur
        exact le_of_lt (div_pos h_t_pos h_dur_pos)

theorem animProgress_le_one (t duration : S) :
    animProgress t duration ≤ 1 := by
  unfold animProgress
  split
  · rfl
  · split
    · linarith
    · split
      · rfl
      · next h_dur _ h_ge =>
        have h_dur_pos : 0 < duration := lt_of_not_ge h_dur
        have h_t_lt : t < duration := lt_of_not_ge h_ge
        exact (div_le_one h_dur_pos).mpr (le_of_lt h_t_lt)

/-! ## 2. Geometric Bounds Interpolation (`AnimateMeasure.java`) -/

/-- Linear interpolation between scalar values: a + p * (b - a). -/
def lerpS (a b p : S) : S :=
  a + p * (b - a)

@[simp] theorem lerpS_zero (a b : S) : lerpS a b 0 = a := by
  dsimp [lerpS]
  ring

@[simp] theorem lerpS_one (a b : S) : lerpS a b 1 = b := by
  dsimp [lerpS]
  ring

@[simp] theorem lerpS_same (a p : S) : lerpS a a p = a := by
  dsimp [lerpS]
  ring

/-- Interpolate rectangle coordinates from origin to target. -/
def interpRect (orig target : Rect) (p : S) : Rect :=
  ⟨lerpS orig.x target.x p,
   lerpS orig.y target.y p,
   lerpS orig.w target.w p,
   lerpS orig.h target.h p⟩

/-- Boundary Continuity Theorem at t = 0: Interpolation begins exactly at the origin. -/
theorem animate_at_zero (orig target : Rect) (duration : S) (h_dur : 0 < duration) :
    interpRect orig target (animProgress 0 duration) = orig := by
  have h_p : animProgress 0 duration = 0 := by
    unfold animProgress
    have h_not_dur : ¬(duration ≤ 0) := by linarith
    simp [h_not_dur]
  dsimp [interpRect]
  rw [h_p]
  simp

/-- Boundary Continuity Theorem at t >= duration: Interpolation reaches exactly the target. -/
theorem animate_at_end (orig target : Rect) (t duration : S) (h_end : duration ≤ t) (h_dur : 0 < duration) :
    interpRect orig target (animProgress t duration) = target := by
  have h_p : animProgress t duration = 1 := by
    unfold animProgress
    have h_not_dur : ¬(duration ≤ 0) := by linarith
    have h_not_t : ¬(t ≤ 0) := by linarith
    have h_ge : t ≥ duration := h_end
    simp [h_not_dur, h_not_t, h_ge]
  dsimp [interpRect]
  rw [h_p]
  simp

/-- Stationary Invariant: If origin and target have identical bounds,
the interpolated rectangle is constant across all time. -/
theorem animate_stationary (r : Rect) (p : S) :
    interpRect r r p = r := by
  dsimp [interpRect]
  simp

/-! ## 3. Enter and Exit Transition Modes -/

inductive EnterExitAnimation where
  | fadeIn
  | fadeOut
  | slideLeft
  | slideRight
  | slideTop
  | slideBottom
  | rotate
  deriving Repr, DecidableEq, Inhabited

end RemoteCompose

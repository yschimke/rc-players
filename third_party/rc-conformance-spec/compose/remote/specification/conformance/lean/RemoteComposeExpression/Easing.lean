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
import RemoteComposeExpression.Basic

/-!
# RemoteCompose Float Expression Engine — Easing & Spline Functions

Implements the animation easing and monotonic spline curves used by
operators such as `CUBIC`, `A_SPLINE`, and `A_SPLINE_LOOP`, mirroring:
* `androidx/compose/remote/core/operations/utilities/easing/CubicEasing.java`
* `androidx/compose/remote/core/operations/utilities/easing/MonotonicSpline.java`
-/

namespace RemoteCompose.Expression

/-! ## Cubic Bezier Easing -/

/-- Cubic bezier easing function with control points `(x1, y1)` and `(x2, y2)`. -/
structure CubicEasing where
  x1 : Float := 0.4
  y1 : Float := 0.0
  x2 : Float := 0.2
  y2 : Float := 1.0
  deriving Repr, Inhabited

namespace CubicEasing

def standard   : CubicEasing := ⟨0.4, 0.0, 0.2, 1.0⟩
def accelerate : CubicEasing := ⟨0.4, 0.05, 0.8, 0.7⟩
def decelerate : CubicEasing := ⟨0.0, 0.0, 0.2, 0.95⟩
def linear     : CubicEasing := ⟨1.0, 1.0, 0.0, 0.0⟩
def anticipate : CubicEasing := ⟨0.36, 0.0, 0.66, -0.56⟩
def overshoot  : CubicEasing := ⟨0.34, 1.56, 0.64, 1.0⟩

/-- Evaluates the X coordinate of the cubic curve at parameter `t`. -/
def getX (c : CubicEasing) (t : Float) : Float :=
  let t1 := 1.0 - t
  let f1 := 3.0 * t1 * t1 * t
  let f2 := 3.0 * t1 * t * t
  let f3 := t * t * t
  c.x1 * f1 + c.x2 * f2 + f3

/-- Evaluates the Y coordinate of the cubic curve at parameter `t`. -/
def getY (c : CubicEasing) (t : Float) : Float :=
  let t1 := 1.0 - t
  let f1 := 3.0 * t1 * t1 * t
  let f2 := 3.0 * t1 * t * t
  let f3 := t * t * t
  c.y1 * f1 + c.y2 * f2 + f3

/-- Binary search to invert `getX(t) ≈ x`, then evaluates `getY(t)`.
Matches `CubicEasing.get` in `CubicEasing.java`. -/
def eval (c : CubicEasing) (x : Float) : Float :=
  if x <= 0.0 then 0.0
  else if x >= 1.0 then 1.0
  else
    let rec bisection (t range : Float) (fuel : Nat) : Float × Float :=
      match fuel with
      | 0 => (t, range)
      | fuel' + 1 =>
        if range <= 0.01 then (t, range)
        else
          let tx := c.getX t
          let newRange := range * 0.5
          let newT := if tx < x then t + newRange else t - newRange
          bisection newT newRange fuel'
    let (t, range) := bisection 0.5 0.5 16
    let x1 := c.getX (t - range)
    let x2 := c.getX (t + range)
    let y1 := c.getY (t - range)
    let y2 := c.getY (t + range)
    let dx := x2 - x1
    if dx == 0.0 then y1
    else (y2 - y1) * (x - x1) / dx + y1

end CubicEasing

/-! ## Monotonic Spline Interpolation -/

/-- 1D Monotonic cubic Hermite spline interpolation over an array of data points. -/
structure MonotonicSpline where
  times : Array Float
  values : Array Float
  tangents : Array Float
  deriving Repr, Inhabited

namespace MonotonicSpline

private def hermiteInterpolate (h x y1 y2 t1 t2 : Float) : Float :=
  let x2 := x * x
  let x3 := x2 * x
  (-2.0 * x3 * y2 + 3.0 * x2 * y2 + 2.0 * x3 * y1 - 3.0 * x2 * y1 + y1 +
   h * t2 * x3 + h * t1 * x3 - h * t2 * x2 - 2.0 * h * t1 * x2 + h * t1 * x)

/-- Constructs a MonotonicSpline given optional knot points and values. -/
def create (timePoints : Option (Array Float)) (y : Array Float) : MonotonicSpline := Id.run do
  let n := y.size
  if n == 0 then
    return ⟨#[], #[], #[]⟩
  if n == 1 then
    return ⟨#[0.0], y, #[0.0]⟩

  let times := match timePoints with
    | some tp => tp
    | none =>
      let fn := (n - 1).toFloat
      Array.ofFn (fun (i : Fin n) => i.val.toFloat / fn)

  -- Compute slopes
  let mut slope : Array Float := Array.mkEmpty (n - 1)
  for i in [0 : n - 1] do
    let dt := times[i + 1]! - times[i]!
    let dtSafe := if dt == 0.0 then 1.0 else dt
    slope := slope.push ((y[i + 1]! - y[i]!) / dtSafe)

  -- Initial tangents
  let mut tangent : Array Float := Array.mkEmpty n
  tangent := tangent.push slope[0]!
  for i in [1 : n - 1] do
    tangent := tangent.push ((slope[i - 1]! + slope[i]!) * 0.5)
  tangent := tangent.push slope[n - 2]!

  -- Adjust for monotonicity
  for i in [0 : n - 1] do
    let s := slope[i]!
    if s == 0.0 then
      tangent := tangent.set! i 0.0
      tangent := tangent.set! (i + 1) 0.0
    else
      let a := tangent[i]! / s
      let b := tangent[i + 1]! / s
      let h := Float.sqrt (a * a + b * b)
      if h > 9.0 then
        let t := 3.0 / h
        tangent := tangent.set! i (t * a * s)
        tangent := tangent.set! (i + 1) (t * b * s)

  return ⟨times, y, tangent⟩

/-- Evaluates the monotonic spline at position `t`. -/
def getPos (s : MonotonicSpline) (t : Float) : Float :=
  let n := s.times.size
  if n == 0 then t
  else if n == 1 then s.values[0]!
  else
    let t0 := s.times[0]!
    let tn := s.times[n - 1]!
    if t <= t0 then
      let dt := s.times[1]! - t0
      let slope := if dt == 0.0 then 0.0 else (s.values[1]! - s.values[0]!) / dt
      s.values[0]! + (t - t0) * slope
    else if t >= tn then
      let dt := tn - s.times[n - 2]!
      let slope := if dt == 0.0 then 0.0 else (s.values[n - 1]! - s.values[n - 2]!) / dt
      s.values[n - 1]! + (t - tn) * slope
    else
      -- Find segment
      let rec findSegment (i : Nat) : Float :=
        if i >= n - 1 then s.values[n - 1]!
        else if t < s.times[i + 1]! then
          let h := s.times[i + 1]! - s.times[i]!
          let x := if h == 0.0 then 0.0 else (t - s.times[i]!) / h
          hermiteInterpolate h x s.values[i]! s.values[i + 1]! s.tangents[i]! s.tangents[i + 1]!
        else
          findSegment (i + 1)
      findSegment 0

end MonotonicSpline

end RemoteCompose.Expression

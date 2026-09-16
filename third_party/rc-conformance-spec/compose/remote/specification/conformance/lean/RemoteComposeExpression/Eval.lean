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
import RemoteComposeExpression.Easing
import RemoteComposeExpression.Context

/-!
# RemoteCompose Float Expression Engine — RPN Evaluator

This file implements the stack-based RPN evaluator for `AnimatedFloatExpression`,
mirroring `androidx/compose/remote/core/operations/utilities/AnimatedFloatExpression.java`.
-/

namespace RemoteCompose.Expression

/-! ## Floating Point Modulo -/

/-- Floating point remainder matching Java `a % b`. -/
def floatMod (a b : Float) : Float :=
  if b == 0.0 then 0.0
  else
    let q := (a / b).toInt64.toFloat
    a - q * b

instance : HMod Float Float Float where
  hMod := floatMod

/-! ## Pseudo-Random Number Generator (Java LCG) -/

/-- Java `java.util.Random` compatible 48-bit Linear Congruential Generator. -/
structure RngState where
  seed : UInt64 := 0x123456789ABC
  deriving Repr, Inhabited

namespace RngState

def setSeed (seedVal : UInt32) : RngState :=
  let s : UInt64 := (seedVal.toUInt64 ^^^ (0x5DEECE66D : UInt64)) &&& (((1 : UInt64) <<< 48) - 1)
  ⟨s⟩

/-- Advances the generator and returns next float in `[0.0, 1.0)`. -/
def nextFloat (rng : RngState) : Float × RngState :=
  let nextSeed : UInt64 := (rng.seed * (0x5DEECE66D : UInt64) + (0xB : UInt64)) &&& (((1 : UInt64) <<< 48) - 1)
  let bits : UInt64 := nextSeed >>> 24
  let f := bits.toFloat / 16777216.0 -- 2^24
  (f, ⟨nextSeed⟩)

end RngState

/-! ## Stack Values -/

/-- An element on the evaluation stack: either a computed float scalar or an array token reference.
Using a sum type prevents hardware-level quiet NaN canonicalization on ARM64/x86 architectures
from stripping payload bits from array tokens. -/
inductive StackVal where
  | num (v : Float)
  | arr (id : UInt32)
  deriving Repr, Inhabited

namespace StackVal

def toFloat : StackVal → Float
  | .num v => v
  | .arr id => (Float32.ofBits (Token.array id).toRawBits).toFloat

def toArrId : StackVal → UInt32
  | .arr id => id
  | .num v => (Float.toFloat32 v).toBits &&& ID_MASK_22

end StackVal

/-! ## Evaluator State -/

/-- State of the stack machine during RPN evaluation. -/
structure EvalState where
  stack : List StackVal := []
  registers : Registers := {}
  rng : RngState := {}
  deriving Inhabited

namespace EvalState

/-- Pushes a float scalar onto the evaluation stack. -/
def push (s : EvalState) (v : Float) : EvalState :=
  { s with stack := .num v :: s.stack }

/-- Pushes a stack value onto the evaluation stack. -/
def pushVal (s : EvalState) (v : StackVal) : EvalState :=
  { s with stack := v :: s.stack }

/-- Safe peek at top scalar value. -/
def peek (s : EvalState) : Option Float :=
  match s.stack with
  | [] => none
  | x :: _ => some x.toFloat

end EvalState

/-! ## Deterministic Noise Calculation -/

/-- Deterministic bit-scrambled 1D noise from a seed float.
Matches `AnimatedFloatExpression.opEval` case `OP_NOISE_FROM`. -/
def noiseFrom (seed : Float) : Float :=
  let bits := (Float.toFloat32 seed).toBits
  let x := (bits <<< 13) ^^^ bits
  let poly := x * (x * x * 15731 + 789221) + 1376312589
  let masked := poly &&& 0x7FFFFFFF
  1.0 - (masked.toNat.toFloat / 1.0737418e9)

/-! ## Step Function -/

/-- Executes a single `Op` against the evaluation state. -/
def applyOp (o : Op) (state : EvalState) (ca : CollectionsAccess)
    (vars : List Float) : Except String EvalState := do
  match o with
  -- Stack manipulation
  | .nop => return state

  | .dup =>
    match state.stack with
    | x :: s => return { state with stack := x :: x :: s }
    | [] => throw "Stack underflow in DUP"

  | .swap =>
    match state.stack with
    | y :: x :: s => return { state with stack := x :: y :: s }
    | _ => throw "Stack underflow in SWAP"

  -- Scratch registers
  | .storeR0 =>
    match state.stack with
    | x :: s => return { state with stack := s, registers := state.registers.set 0 x.toFloat }
    | [] => throw "Stack underflow in STORE_R0"
  | .storeR1 =>
    match state.stack with
    | x :: s => return { state with stack := s, registers := state.registers.set 1 x.toFloat }
    | [] => throw "Stack underflow in STORE_R1"
  | .storeR2 =>
    match state.stack with
    | x :: s => return { state with stack := s, registers := state.registers.set 2 x.toFloat }
    | [] => throw "Stack underflow in STORE_R2"
  | .storeR3 =>
    match state.stack with
    | x :: s => return { state with stack := s, registers := state.registers.set 3 x.toFloat }
    | [] => throw "Stack underflow in STORE_R3"

  | .loadR0 => return state.push (state.registers.get 0)
  | .loadR1 => return state.push (state.registers.get 1)
  | .loadR2 => return state.push (state.registers.get 2)
  | .loadR3 => return state.push (state.registers.get 3)

  | .cmd1 | .cmd2 | .cmd3 | .cmd4 => return state

  -- Context variables
  | .var1 => return state.push (vars.getD 0 0.0)
  | .var2 => return state.push (vars.getD 1 0.0)
  | .var3 => return state.push (vars.getD 2 0.0)

  -- Random & Noise
  | .rand =>
    let (r, nextRng) := state.rng.nextFloat
    return { state with stack := .num r :: state.stack, rng := nextRng }

  | .randSeed =>
    match state.stack with
    | seed :: s =>
      let bits := (Float.toFloat32 seed.toFloat).toBits
      let nextRng := RngState.setSeed bits
      return { state with stack := s, rng := nextRng }
    | [] => throw "Stack underflow in RAND_SEED"

  | .noiseFrom =>
    match state.stack with
    | seed :: s => return { state with stack := .num (noiseFrom seed.toFloat) :: s }
    | [] => throw "Stack underflow in NOISE_FROM"

  | .randInRange =>
    match state.stack with
    | maxVal :: minVal :: s =>
      let (r, nextRng) := state.rng.nextFloat
      let v := r * (maxVal.toFloat - minVal.toFloat) + minVal.toFloat
      return { state with stack := .num v :: s, rng := nextRng }
    | _ => throw "Stack underflow in RAND_IN_RANGE"

  -- Fast math & Unary ops
  | .changeSign =>
    match state.stack with
    | x :: s => return { state with stack := .num (-x.toFloat) :: s }
    | [] => throw "Stack underflow in CHANGE_SIGN"

  | .square =>
    match state.stack with
    | x :: s =>
      let v := x.toFloat
      return { state with stack := .num (v * v) :: s }
    | [] => throw "Stack underflow in SQUARE"

  | .sqrt =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.sqrt (max 0.0 x.toFloat)) :: s }
    | [] => throw "Stack underflow in SQRT"

  | .abs =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.abs x.toFloat) :: s }
    | [] => throw "Stack underflow in ABS"

  | .sign =>
    match state.stack with
    | x :: s =>
      let v := x.toFloat
      let res := if v > 0.0 then 1.0 else if v < 0.0 then -1.0 else 0.0
      return { state with stack := .num res :: s }
    | [] => throw "Stack underflow in SIGN"

  | .exp =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.exp x.toFloat) :: s }
    | [] => throw "Stack underflow in EXP"

  | .floor =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.floor x.toFloat) :: s }
    | [] => throw "Stack underflow in FLOOR"

  | .ceil =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.ceil x.toFloat) :: s }
    | [] => throw "Stack underflow in CEIL"

  | .round =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.round x.toFloat) :: s }
    | [] => throw "Stack underflow in ROUND"

  | .ln =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.log x.toFloat) :: s }
    | [] => throw "Stack underflow in LN"

  | .log =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.log x.toFloat / Float.log 10.0) :: s }
    | [] => throw "Stack underflow in LOG"

  | .log2 =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.log x.toFloat / Float.log 2.0) :: s }
    | [] => throw "Stack underflow in LOG2"

  | .inv =>
    match state.stack with
    | x :: s =>
      let v := x.toFloat
      let res := if v == 0.0 then 0.0 else 1.0 / v
      return { state with stack := .num res :: s }
    | [] => throw "Stack underflow in INV"

  | .fract =>
    match state.stack with
    | x :: s =>
      let v := x.toFloat
      let res := v - Float.floor v
      return { state with stack := .num res :: s }
    | [] => throw "Stack underflow in FRACT"

  | .cbrt =>
    match state.stack with
    | x :: s =>
      let v := x.toFloat
      let res := if v >= 0.0 then Float.pow v (1.0 / 3.0)
                 else -Float.pow (-v) (1.0 / 3.0)
      return { state with stack := .num res :: s }
    | [] => throw "Stack underflow in CBRT"

  -- Trigonometry
  | .sin =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.sin x.toFloat) :: s }
    | [] => throw "Stack underflow in SIN"

  | .cos =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.cos x.toFloat) :: s }
    | [] => throw "Stack underflow in COS"

  | .tan =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.tan x.toFloat) :: s }
    | [] => throw "Stack underflow in TAN"

  | .asin =>
    match state.stack with
    | x :: s =>
      let clamped := max (-1.0) (min 1.0 x.toFloat)
      return { state with stack := .num (Float.asin clamped) :: s }
    | [] => throw "Stack underflow in ASIN"

  | .acos =>
    match state.stack with
    | x :: s =>
      let clamped := max (-1.0) (min 1.0 x.toFloat)
      return { state with stack := .num (Float.acos clamped) :: s }
    | [] => throw "Stack underflow in ACOS"

  | .atan =>
    match state.stack with
    | x :: s => return { state with stack := .num (Float.atan x.toFloat) :: s }
    | [] => throw "Stack underflow in ATAN"

  | .deg =>
    match state.stack with
    | x :: s => return { state with stack := .num (x.toFloat * FP_TO_DEG) :: s }
    | [] => throw "Stack underflow in DEG"

  | .rad =>
    match state.stack with
    | x :: s => return { state with stack := .num (x.toFloat * FP_TO_RAD) :: s }
    | [] => throw "Stack underflow in RAD"

  -- Binary Arithmetic
  | .add =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (a.toFloat + b.toFloat) :: s }
    | _ => throw "Stack underflow in ADD"

  | .sub =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (a.toFloat - b.toFloat) :: s }
    | _ => throw "Stack underflow in SUB"

  | .mul =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (a.toFloat * b.toFloat) :: s }
    | _ => throw "Stack underflow in MUL"

  | .div =>
    match state.stack with
    | b :: a :: s =>
      let bv := b.toFloat
      let res := if bv == 0.0 then 0.0 else a.toFloat / bv
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in DIV"

  | .mod =>
    match state.stack with
    | b :: a :: s =>
      let bv := b.toFloat
      let res := if bv == 0.0 then 0.0 else a.toFloat % bv
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in MOD"

  | .min =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (min a.toFloat b.toFloat) :: s }
    | _ => throw "Stack underflow in MIN"

  | .max =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (max a.toFloat b.toFloat) :: s }
    | _ => throw "Stack underflow in MAX"

  | .pow =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (Float.pow a.toFloat b.toFloat) :: s }
    | _ => throw "Stack underflow in POW"

  | .copySign =>
    match state.stack with
    | b :: a :: s =>
      let absA := Float.abs a.toFloat
      let res := if b.toFloat < 0.0 then -absA else absA
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in COPY_SIGN"

  | .atan2 =>
    match state.stack with
    | b :: a :: s => return { state with stack := .num (Float.atan2 a.toFloat b.toFloat) :: s }
    | _ => throw "Stack underflow in ATAN2"

  | .squareSum =>
    match state.stack with
    | b :: a :: s =>
      let av := a.toFloat; let bv := b.toFloat
      return { state with stack := .num (av * av + bv * bv) :: s }
    | _ => throw "Stack underflow in SQUARE_SUM"

  | .hypot =>
    match state.stack with
    | b :: a :: s =>
      let av := a.toFloat; let bv := b.toFloat
      return { state with stack := .num (Float.sqrt (av * av + bv * bv)) :: s }
    | _ => throw "Stack underflow in HYPOT"

  | .step =>
    match state.stack with
    | edge :: x :: s =>
      let res := if x.toFloat > edge.toFloat then 1.0 else 0.0
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in STEP"

  | .pingpong =>
    match state.stack with
    | maxVal :: x :: s =>
      let mv := maxVal.toFloat
      let xv := x.toFloat
      let max2 := mv * 2.0
      let tmp := if max2 == 0.0 then 0.0 else xv % max2
      let res := if tmp < mv then tmp else max2 - tmp
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in PINGPONG"

  -- 3-arg Operators
  | .mad =>
    match state.stack with
    | c :: b :: a :: s =>
      return { state with stack := .num (c.toFloat + b.toFloat * a.toFloat) :: s }
    | _ => throw "Stack underflow in MAD"

  | .ifElse =>
    match state.stack with
    | cond :: trueVal :: falseVal :: s =>
      let res := if cond.toFloat > 0.0 then trueVal.toFloat else falseVal.toFloat
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in IFELSE"

  | .clamp =>
    match state.stack with
    | minVal :: maxVal :: v :: s =>
      let res := min maxVal.toFloat (max minVal.toFloat v.toFloat)
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in CLAMP"

  | .lerp =>
    match state.stack with
    | t :: b :: a :: s =>
      let av := a.toFloat; let bv := b.toFloat; let tv := t.toFloat
      let res := av + (bv - av) * tv
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in LERP"

  | .smoothStep =>
    match state.stack with
    | min1 :: max2 :: val3 :: s =>
      let v3 := val3.toFloat; let m2 := max2.toFloat; let m1 := min1.toFloat
      let res :=
        if v3 < m1 then 0.0
        else if v3 > m2 then 1.0
        else
          let range := m2 - m1
          let v := if range == 0.0 then 0.0 else (v3 - m1) / range
          v * v * (3.0 - 2.0 * v)
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in SMOOTH_STEP"

  -- 5-arg Easing
  | .cubic =>
    match state.stack with
    | pos :: y2 :: x2 :: y1 :: x1 :: s =>
      let easing : CubicEasing := ⟨x1.toFloat, y1.toFloat, x2.toFloat, y2.toFloat⟩
      let res := easing.eval pos.toFloat
      return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in CUBIC"

  -- Array Collections operations
  | .aDeref =>
    match state.stack with
    | idxVal :: arrVal :: s =>
      let arrId := arrVal.toArrId
      let idx := idxVal.toFloat.toUInt64.toNat
      let v := ca.getFloatValue arrId idx
      return { state with stack := .num v :: s }
    | _ => throw "Stack underflow in A_DEREF"

  | .aMax =>
    match state.stack with
    | arrVal :: s =>
      let arrId := arrVal.toArrId
      match ca.getFloats arrId with
      | none => return { state with stack := .num 0.0 :: s }
      | some arr =>
        if arr.isEmpty then return { state with stack := .num 0.0 :: s }
        else
          let mut m := arr[0]!
          for i in [1 : arr.size] do
            m := max m arr[i]!
          return { state with stack := .num m :: s }
    | [] => throw "Stack underflow in A_MAX"

  | .aMin =>
    match state.stack with
    | arrVal :: s =>
      let arrId := arrVal.toArrId
      match ca.getFloats arrId with
      | none => return { state with stack := .num 0.0 :: s }
      | some arr =>
        if arr.isEmpty then return { state with stack := .num 0.0 :: s }
        else
          let mut m := arr[0]!
          for i in [1 : arr.size] do
            m := min m arr[i]!
          return { state with stack := .num m :: s }
    | [] => throw "Stack underflow in A_MIN"

  | .aSum =>
    match state.stack with
    | arrVal :: s =>
      let arrId := arrVal.toArrId
      match ca.getFloats arrId with
      | none => return { state with stack := .num 0.0 :: s }
      | some arr =>
        let mut sum := 0.0
        for x in arr do
          sum := sum + x
        return { state with stack := .num sum :: s }
    | [] => throw "Stack underflow in A_SUM"

  | .aAvg =>
    match state.stack with
    | arrVal :: s =>
      let arrId := arrVal.toArrId
      match ca.getFloats arrId with
      | none => return { state with stack := .num 0.0 :: s }
      | some arr =>
        if arr.isEmpty then return { state with stack := .num 0.0 :: s }
        else
          let mut sum := 0.0
          for x in arr do
            sum := sum + x
          return { state with stack := .num (sum / arr.size.toFloat) :: s }
    | [] => throw "Stack underflow in A_AVG"

  | .aLen =>
    match state.stack with
    | arrVal :: s =>
      let arrId := arrVal.toArrId
      let len := ca.getListLength arrId
      return { state with stack := .num len.toFloat :: s }
    | [] => throw "Stack underflow in A_LEN"

  | .aSpline =>
    match state.stack with
    | pos :: arrVal :: s =>
      let arrId := arrVal.toArrId
      let pv := pos.toFloat
      match ca.getFloats arrId with
      | none => return { state with stack := .num pv :: s }
      | some arr =>
        if arr.isEmpty then return { state with stack := .num pv :: s }
        else
          let spline := MonotonicSpline.create none arr
          return { state with stack := .num (spline.getPos pv) :: s }
    | _ => throw "Stack underflow in A_SPLINE"

  | .aSplineLoop =>
    match state.stack with
    | pos :: arrVal :: s =>
      let arrId := arrVal.toArrId
      let pv := pos.toFloat
      match ca.getFloats arrId with
      | none => return { state with stack := .num pv :: s }
      | some arr =>
        if arr.isEmpty then return { state with stack := .num pv :: s }
        else
          let r := pv - Float.floor pv
          let spline := MonotonicSpline.create none arr
          return { state with stack := .num (spline.getPos r) :: s }
    | _ => throw "Stack underflow in A_SPLINE_LOOP"

  | .aSumTill =>
    match state.stack with
    | lastIdxVal :: arrVal :: s =>
      let arrId := arrVal.toArrId
      let last := lastIdxVal.toFloat.toUInt64.toNat
      let clampedLast := Nat.min last MAX_SUM_TILL_ITERATIONS
      let mut sum := 0.0
      for j in [0 : clampedLast + 1] do
        sum := sum + ca.getFloatValue arrId j
      return { state with stack := .num sum :: s }
    | _ => throw "Stack underflow in A_SUM_TILL"

  | .aSumXy =>
    match state.stack with
    | arrYVal :: arrXVal :: s =>
      let idX := arrXVal.toArrId
      let idY := arrYVal.toArrId
      let arrX := (ca.getFloats idX).getD #[]
      let arrY := (ca.getFloats idY).getD #[]
      let len := Nat.min arrX.size arrY.size
      let mut sumXY := 0.0
      for i in [0 : len] do
        sumXY := sumXY + arrX[i]! * arrY[i]!
      return { state with stack := .num sumXY :: s }
    | _ => throw "Stack underflow in A_SUM_XY"

  | .aSumSqr =>
    match state.stack with
    | arrVal :: s =>
      let arrId := arrVal.toArrId
      let arr := (ca.getFloats arrId).getD #[]
      let mut sumSq := 0.0
      for x in arr do
        sumSq := sumSq + x * x
      return { state with stack := .num sumSq :: s }
    | [] => throw "Stack underflow in A_SUM_SQR"

  | .aLerp =>
    match state.stack with
    | pos :: arrVal :: s =>
      let arrId := arrVal.toArrId
      let pv := pos.toFloat
      let arr := (ca.getFloats arrId).getD #[]
      let n := arr.size
      if n == 0 then return { state with stack := .num 0.0 :: s }
      else if n == 1 then return { state with stack := .num arr[0]! :: s }
      else
        let pLerp := pv * (n - 1).toFloat
        let idx := pLerp.toUInt64.toNat
        let res :=
          if pLerp < 0.0 then arr[0]!
          else if idx >= n - 1 then arr[n - 1]!
          else
            let tLerp := pLerp - idx.toFloat
            arr[idx]! + tLerp * (arr[idx + 1]! - arr[idx]!)
        return { state with stack := .num res :: s }
    | _ => throw "Stack underflow in A_LERP"

/-- Executes a single token against the evaluation state. -/
def stepToken (token : Token) (state : EvalState) (ctx : RemoteContext)
    (ca : CollectionsAccess) (vars : List Float) : Except String EvalState := do
  match token with
  | .lit v => return state.push v
  | .var id => return state.push (ctx.getFloat id)
  | .array id => return state.pushVal (.arr id)
  | .op o => applyOp o state ca vars

/-- Evaluates an entire sequence of RPN tokens.
Returns the top value on the stack upon completion. -/
def eval (tokens : List Token) (ctx : RemoteContext := {})
    (ca : CollectionsAccess := .empty) (vars : List Float := []) : Except String Float := do
  let mut state : EvalState := {}
  for token in tokens do
    state ← stepToken token state ctx ca vars
  match state.peek with
  | some res => return res
  | none => throw "Expression evaluated to empty stack"

/-- Evaluates an RPN sequence, returning a default value on error. -/
def evalD (tokens : List Token) (ctx : RemoteContext := {})
    (ca : CollectionsAccess := .empty) (vars : List Float := []) (defaultVal : Float := 0.0) : Float :=
  match eval tokens ctx ca vars with
  | .ok v => v
  | .error _ => defaultVal

/-- Evaluates an array of raw 32-bit wire words directly. -/
def evalRaw (words : List UInt32) (ctx : RemoteContext := {})
    (ca : CollectionsAccess := .empty) (vars : List Float := []) : Except String Float :=
  eval (words.map Token.ofRawBits) ctx ca vars

end RemoteCompose.Expression

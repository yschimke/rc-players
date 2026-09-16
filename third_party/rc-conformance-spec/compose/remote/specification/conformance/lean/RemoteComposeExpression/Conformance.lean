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
import RemoteComposeExpression.Eval
import RemoteComposeExpression.FloatExpression
import RemoteComposeExpression.Parser

/-!
# Conformance Test Suite for the RemoteCompose Float Expression Engine

Validates the Lean specification against the Android reference implementation tests in:
* `androidx/compose/remote/core/AnimatedFloatExpressionTest.java`
* `androidx/compose/remote/core/operations/FloatExpression.java`
* `specification/AREA-04-EXPRESSION-ENGINE.md`

Every `#guard` runs during `lake build`. A successful build guarantees that
all conformance checks pass.
-/

namespace RemoteCompose.Expression

private def approxEq (eps : Float := 1e-4) (a b : Float) : Bool :=
  Float.abs (a - b) <= eps

/-! ## 1. Simple Expression Tests (`simpleTest`) -/

-- (3+5)*(2-8) -> [3, 5, +, 2, 8, -, *] = -48.0
#guard evalD [
  .lit 3.0, .lit 5.0, .op .add,
  .lit 2.0, .lit 8.0, .op .sub,
  .op .mul
] == -48.0

-- Evaluated through infix parser: "(3 + 5) * (2 - 8)"
#guard match parseInfix "(3 + 5) * (2 - 8)" with
  | .ok toks => evalD toks == -48.0
  | .error _ => false

/-! ## 2. All Operators Infix Tests (`allOperatorsTest`) -/

#guard match parseInfix "2 + 3" with
  | .ok toks => evalD toks == 5.0
  | .error _ => false

#guard match parseInfix "min(2, 3)" with
  | .ok toks => evalD toks == 2.0
  | .error _ => false

#guard match parseInfix "max(2, 3)" with
  | .ok toks => evalD toks == 3.0
  | .error _ => false

#guard match parseInfix "pow(2, 3)" with
  | .ok toks => evalD toks == 8.0
  | .error _ => false

#guard match parseInfix "sqrt(4)" with
  | .ok toks => evalD toks == 2.0
  | .error _ => false

#guard match parseInfix "abs(-4)" with
  | .ok toks => evalD toks == 4.0
  | .error _ => false

#guard match parseInfix "sign(-4)" with
  | .ok toks => evalD toks == -1.0
  | .error _ => false

#guard match parseInfix "copySign(3, -4)" with
  | .ok toks => evalD toks == -3.0
  | .error _ => false

#guard match parseInfix "copySign(3, 4)" with
  | .ok toks => evalD toks == 3.0
  | .error _ => false

#guard match parseInfix "floor(-4)" with
  | .ok toks => evalD toks == -4.0
  | .error _ => false

#guard match parseInfix "round(4.45)" with
  | .ok toks => evalD toks == 4.0
  | .error _ => false

#guard match parseInfix "ifElse(3, 4, 2)" with
  | .ok toks => evalD toks == 4.0
  | .error _ => false

#guard match parseInfix "mad(2, 3, 4)" with
  | .ok toks => evalD toks == 10.0
  | .error _ => false

/-! ## 3. Advanced Operators Tests (`testAdvanceOperators`) -/

-- acos(2/3), asin(2/3), atan(2/3)
#guard approxEq 1e-5 (evalD [.lit 2.0, .lit 3.0, .op .div, .op .acos]) (Float.acos (2.0 / 3.0))
#guard approxEq 1e-5 (evalD [.lit 2.0, .lit 3.0, .op .div, .op .asin]) (Float.asin (2.0 / 3.0))
#guard approxEq 1e-5 (evalD [.lit 2.0, .lit 3.0, .op .div, .op .atan]) (Float.atan (2.0 / 3.0))
#guard approxEq 1e-5 (evalD [.lit 2.0, .lit 3.0, .op .atan2]) (Float.atan2 2.0 3.0)

-- MAD: 2 * 3 + 4 = 10
#guard evalD [.lit 2.0, .lit 3.0, .lit 4.0, .op .mad] == 10.0

-- IFELSE: cond > 0 ? 4 : 3
#guard evalD [.lit 3.0, .lit 4.0, .lit 1.0, .op .ifElse] == 4.0
#guard evalD [.lit 3.0, .lit 4.0, .lit 0.0, .op .ifElse] == 3.0

-- CLAMP: clamp(43, max=20, min=0) -> 20
#guard evalD [.lit 43.0, .lit 20.0, .lit 0.0, .op .clamp] == 20.0

-- CBRT: 32^(1/3)
#guard approxEq 1e-4 (evalD [.lit 32.0, .op .cbrt]) (Float.pow 32.0 (1.0 / 3.0))

-- DEG & RAD
#guard approxEq 1e-4 (evalD [.lit 32.0, .op .deg]) (32.0 * FP_TO_DEG)
#guard approxEq 1e-4 (evalD [.lit 32.0, .op .rad]) (32.0 * FP_TO_RAD)

-- CEIL & FLOOR
#guard evalD [.lit 234.2, .op .ceil] == 235.0
#guard evalD [.lit 234.2, .op .floor] == 234.0

-- MIN & MAX & POW & SQRT & ABS & SIGN
#guard evalD [.lit 21.0, .lit 32.0, .op .max] == 32.0
#guard evalD [.lit 21.0, .lit 32.0, .op .min] == 21.0
#guard evalD [.lit 2.0, .lit 16.0, .op .pow] == 65536.0
#guard evalD [.lit 144.0, .op .sqrt] == 12.0
#guard evalD [.lit (-234.2), .op .abs] == 234.2
#guard evalD [.lit (-234.2), .op .sign] == -1.0

-- EXP, LOG, LN, ROUND
#guard approxEq 1e-5 (evalD [.lit 1.2, .op .exp]) (Float.exp 1.2)
#guard approxEq 1e-5 (evalD [.lit 1.2, .op .ln]) (Float.log 1.2)
#guard approxEq 1e-5 (evalD [.lit 1.2, .op .log]) (Float.log 1.2 / Float.log 10.0)
#guard evalD [.lit 1.2, .op .round] == 1.0

-- COPY_SIGN & TAN
#guard evalD [.lit 2.0, .lit (-1.2), .op .copySign] == -2.0
#guard approxEq 1e-5 (evalD [.lit (-1.2), .op .tan]) (Float.tan (-1.2))

-- LOG2, INV, FRACT, PINGPONG
#guard approxEq 1e-5 (evalD [.lit 1.2, .op .log2]) (Float.log 1.2 / Float.log 2.0)
#guard approxEq 1e-5 (evalD [.lit 1.2, .op .inv]) (1.0 / 1.2)
#guard approxEq 1e-5 (evalD [.lit 1.2, .op .fract]) 0.2
#guard approxEq 1e-5 (evalD [.lit 1.2, .lit 1.0, .op .pingpong]) 0.8
#guard approxEq 1e-5 (evalD [.lit 0.2, .lit 1.0, .op .pingpong]) 0.2

/-! ## 4. Fast Math & 3-Ops Tests (`testSet3Ops`) -/

#guard evalD [.lit 4.0, .lit 3.0, .op .squareSum] == 25.0
#guard evalD [.lit 4.0, .lit 3.0, .op .step] == 1.0
#guard evalD [.lit 2.0, .lit 3.0, .op .step] == 0.0
#guard evalD [.lit 3.0, .op .square] == 9.0
#guard evalD [.lit 3.0, .op .dup, .op .mul] == 9.0
#guard evalD [.lit 3.0, .lit 4.0, .op .hypot] == 5.0
#guard evalD [.lit 2.0, .lit 4.0, .op .div] == 0.5
#guard evalD [.lit 2.0, .lit 4.0, .op .swap, .op .div] == 2.0
#guard evalD [.lit 100.0, .lit 200.0, .lit 0.75, .op .lerp] == 175.0
#guard approxEq 1e-5 (evalD [.lit 5.0, .lit 10.0, .lit 0.0, .op .smoothStep]) 0.5

/-! ## 5. Change Sign Tests (`testChangeSign`) -/

#guard evalD [.lit 1.0, .op .changeSign] == -1.0
#guard evalD [.lit (-1.0), .op .changeSign] == 1.0
#guard evalD [.lit 0.0, .op .changeSign] == 0.0
#guard evalD [.lit 123.321, .op .changeSign] == -123.321

/-! ## 6. Cubic Easing Tests (`testCubic`) -/

#guard evalD [.lit 0.4, .lit 0.0, .lit 0.2, .lit 1.0, .lit 0.0, .op .cubic] == 0.0
#guard evalD [.lit 0.4, .lit 0.0, .lit 0.2, .lit 1.0, .lit 1.0, .op .cubic] == 1.0
#guard evalD [.lit 0.4, .lit 0.0, .lit 0.2, .lit 1.0, .lit (-2.0), .op .cubic] == 0.0
#guard evalD [.lit 0.4, .lit 0.0, .lit 0.2, .lit 1.0, .lit 22.0, .op .cubic] == 1.0

-- Matches CubicEasing instance at 0.5
#guard approxEq 1e-3
  (evalD [.lit 0.4, .lit 0.0, .lit 0.2, .lit 1.0, .lit 0.5, .op .cubic])
  (CubicEasing.standard.eval 0.5)

/-! ## 7. Array Collections Operations Tests -/

private def sampleCollections : CollectionsAccess :=
  CollectionsAccess.fromList [
    (1, #[10.0, 20.0, 30.0, 40.0]),
    (2, #[1.0, 2.0, 3.0, 4.0])
  ]

#guard evalD [.array 1, .lit 2.0, .op .aDeref] {} sampleCollections == 30.0
#guard evalD [.array 1, .op .aMax] {} sampleCollections == 40.0
#guard evalD [.array 1, .op .aMin] {} sampleCollections == 10.0
#guard evalD [.array 1, .op .aSum] {} sampleCollections == 100.0
#guard evalD [.array 1, .op .aAvg] {} sampleCollections == 25.0
#guard evalD [.array 1, .op .aLen] {} sampleCollections == 4.0
#guard evalD [.array 1, .lit 2.0, .op .aSumTill] {} sampleCollections == 60.0 -- 10 + 20 + 30
#guard evalD [.array 1, .array 2, .op .aSumXy] {} sampleCollections == 300.0 -- 10*1 + 20*2 + 30*3 + 40*4
#guard evalD [.array 2, .op .aSumSqr] {} sampleCollections == 30.0 -- 1 + 4 + 9 + 16

/-! ## 8. Variables & Dirty Tracking Tests -/

def testVariableContext : RemoteContext :=
  RemoteContext.fromList [
    (ID_WINDOW_WIDTH, 1920.0),
    (ID_WINDOW_HEIGHT, 1080.0),
    (START_VAR, 42.0)
  ] (animTime := 1.5)

#guard evalD [.var ID_WINDOW_WIDTH, .var ID_WINDOW_HEIGHT, .op .add] testVariableContext == 3000.0
#guard evalD [.var START_VAR, .lit 2.0, .op .mul] testVariableContext == 84.0

-- FloatExpression creation and reactive update
def sampleExpression : FloatExpression :=
  FloatExpression.create 100 [.var START_VAR, .lit 2.0, .op .mul]

#guard sampleExpression.dependencies == [START_VAR]

#guard match sampleExpression.updateVariables testVariableContext with
  | (updated, changed) => changed == true && updated.lastCalculatedValue == some 84.0

-- When variable doesn't change, changed is false
#guard match (sampleExpression.updateVariables testVariableContext).1.updateVariables testVariableContext with
  | (_, changed) => changed == false

/-! ## 9. Wire Bit-Level Roundtrip Tests -/

-- Raw IEEE bits roundtrip
#guard match evalRaw [
  (Float.toFloat32 3.0).toBits,
  (Float.toFloat32 5.0).toBits,
  (Token.op .add).toRawBits
] with
  | .ok res => res == 8.0
  | .error _ => false

end RemoteCompose.Expression

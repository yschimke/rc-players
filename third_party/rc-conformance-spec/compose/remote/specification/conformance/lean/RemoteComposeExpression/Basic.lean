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

/-!
# RemoteCompose Float Expression Engine — Basic Types and Constants

This file defines the foundational types, constants, operator codes,
and token representations for the RemoteCompose RPN Float Expression Engine,
mirroring:
* `androidx/compose/remote/core/operations/utilities/AnimatedFloatExpression.java`
* `androidx/compose/remote/core/operations/utilities/NanMap.java`
* `androidx/compose/remote/core/operations/Utils.java`
* `specification/AREA-04-EXPRESSION-ENGINE.md`
-/

namespace RemoteCompose.Expression

/-! ## Nan Encoding Constants and Region Offsets -/

/-- Base offset in the float NaN payload space for math operators (0x310000). -/
def OFFSET : UInt32 := 0x310000

/-- System variable region tag (0x0). -/
def TYPE_SYSTEM : UInt32 := 0

/-- User-defined variable region tag (0x1). -/
def TYPE_VARIABLE : UInt32 := 1

/-- Array / Collection region tag (0x2). -/
def TYPE_ARRAY : UInt32 := 2

/-- Operator region tag (0x3). -/
def TYPE_OPERATION : UInt32 := 3

/-- 3-bit region mask (bits 20..22). -/
def ID_REGION_MASK : UInt32 := 0x700000

/-- Array region base offset (0x200000). -/
def ID_REGION_ARRAY : UInt32 := 0x200000

/-- 22-bit identifier mask. -/
def ID_MASK_22 : UInt32 := 0x3FFFFF

/-- 23-bit mantissa mask. -/
def ID_MASK_23 : UInt32 := 0x7FFFFF

/-- First variable id (42). -/
def START_VARIABLE_ID : UInt32 := 42

/-- Base id for normal user variables (0x10002A). -/
def START_VAR : UInt32 := ((1 : UInt32) <<< 20) + START_VARIABLE_ID

/-- Base id for arrays (0x20002A). -/
def START_ARRAY : UInt32 := ((2 : UInt32) <<< 20) + START_VARIABLE_ID

/-- Maximum stack depth supported by the evaluator. -/
def MAX_STACK_SIZE : Nat := 128

/-- Maximum expression token length. -/
def MAX_EXPRESSION_SIZE : Nat := 256

/-- Maximum loop count for `A_SUM_TILL`. -/
def MAX_SUM_TILL_ITERATIONS : Nat := 10000

/-- Degree to radian factor (π / 180). -/
def FP_TO_RAD : Float := 0.017453292

/-- Radian to degree factor (180 / π). -/
def FP_TO_DEG : Float := 57.29578

/-! ## Predefined Global System Variables -/

def ID_CONTINUOUS_SEC       : UInt32 := 1
def ID_TIME_IN_SEC          : UInt32 := 2
def ID_TIME_IN_MIN          : UInt32 := 3
def ID_TIME_IN_HR           : UInt32 := 4
def ID_WINDOW_WIDTH         : UInt32 := 5
def ID_WINDOW_HEIGHT        : UInt32 := 6
def ID_COMPONENT_WIDTH      : UInt32 := 7
def ID_COMPONENT_HEIGHT     : UInt32 := 8
def ID_CALENDAR_MONTH       : UInt32 := 9
def ID_OFFSET_TO_UTC        : UInt32 := 10
def ID_WEEK_DAY             : UInt32 := 11
def ID_DAY_OF_MONTH         : UInt32 := 12
def ID_TOUCH_POS_X          : UInt32 := 13
def ID_TOUCH_POS_Y          : UInt32 := 14
def ID_TOUCH_VEL_X          : UInt32 := 15
def ID_TOUCH_VEL_Y          : UInt32 := 16
def ID_ACCELERATION_X       : UInt32 := 17
def ID_ACCELERATION_Y       : UInt32 := 18
def ID_ACCELERATION_Z       : UInt32 := 19
def ID_GYRO_ROT_X           : UInt32 := 20
def ID_GYRO_ROT_Y           : UInt32 := 21
def ID_GYRO_ROT_Z           : UInt32 := 22
def ID_MAGNETIC_X           : UInt32 := 23
def ID_MAGNETIC_Y           : UInt32 := 24
def ID_MAGNETIC_Z           : UInt32 := 25
def ID_LIGHT                : UInt32 := 26
def ID_DENSITY              : UInt32 := 27
def ID_API_LEVEL            : UInt32 := 28
def ID_TOUCH_EVENT_TIME     : UInt32 := 29
def ID_ANIMATION_TIME       : UInt32 := 30
def ID_ANIMATION_DELTA_TIME : UInt32 := 31
def ID_EPOCH_SECOND         : UInt32 := 32
def ID_FONT_SIZE            : UInt32 := 33
def ID_DAY_OF_YEAR          : UInt32 := 34
def ID_YEAR                 : UInt32 := 35

/-! ## Operators Catalog -/

/-- All math and stack operators supported by AnimatedFloatExpression. -/
inductive Op where
  -- Arithmetic
  | add          -- 1: a + b
  | sub          -- 2: a - b
  | mul          -- 3: a * b
  | div          -- 4: a / b
  | mod          -- 5: a % b
  | min          -- 6: min(a, b)
  | max          -- 7: max(a, b)
  | pow          -- 8: a ^ b
  | sqrt         -- 9: sqrt(a)
  | abs          -- 10: |a|
  | sign         -- 11: signum(a)
  | copySign     -- 12: copySign(a, b)
  | exp          -- 13: exp(a)
  | floor        -- 14: floor(a)
  | log          -- 15: log10(a)
  | ln           -- 16: ln(a)
  | round        -- 17: round(a)
  | sin          -- 18: sin(a)
  | cos          -- 19: cos(a)
  | tan          -- 20: tan(a)
  | asin         -- 21: asin(a)
  | acos         -- 22: acos(a)
  | atan         -- 23: atan(a)
  | atan2        -- 24: atan2(y, x)
  | mad          -- 25: a * b + c
  | ifElse       -- 26: cond > 0 ? trueVal : falseVal
  | clamp        -- 27: clamp(v, max, min)
  | cbrt         -- 28: a ^ (1/3)
  | deg          -- 29: a * (180/pi)
  | rad          -- 30: a * (pi/180)
  | ceil         -- 31: ceil(a)
  -- Array operations
  | aDeref       -- 32: array[index]
  | aMax         -- 33: max(array)
  | aMin         -- 34: min(array)
  | aSum         -- 35: sum(array)
  | aAvg         -- 36: avg(array)
  | aLen         -- 37: length(array)
  | aSpline      -- 38: spline(array, pos)
  -- Random & Noise
  | rand         -- 39: random()
  | randSeed     -- 40: setSeed(seed)
  | noiseFrom    -- 41: noise(seed)
  | randInRange  -- 42: rand * (max - min) + min
  -- Geometry & Fast math
  | squareSum    -- 43: a^2 + b^2
  | step         -- 44: a > edge ? 1 : 0
  | square       -- 45: a * a
  | dup          -- 46: duplicate top
  | hypot        -- 47: sqrt(a^2 + b^2)
  | swap         -- 48: swap top two
  | lerp         -- 49: lerp(a, b, t)
  | smoothStep   -- 50: smoothStep(min, max, val)
  | log2         -- 51: log2(a)
  | inv          -- 52: 1 / a
  | fract        -- 53: a - floor(a)
  | pingpong     -- 54: bounce
  | nop          -- 55: no-op
  -- Scratch Registers
  | storeR0      -- 56: store R0
  | storeR1      -- 57: store R1
  | storeR2      -- 58: store R2
  | storeR3      -- 59: store R3
  | loadR0       -- 60: load R0
  | loadR1       -- 61: load R1
  | loadR2       -- 62: load R2
  | loadR3       -- 63: load R3
  -- Reserved commands
  | cmd1         -- 64
  | cmd2         -- 65
  | cmd3         -- 66
  | cmd4         -- 67
  -- Context variables
  | var1         -- 70: mVar[0]
  | var2         -- 71: mVar[1]
  | var3         -- 72: mVar[2]
  -- Extended ops
  | changeSign   -- 73: -a
  | cubic        -- 74: cubic bezier easing
  | aSplineLoop  -- 75: looping spline
  | aSumTill     -- 76: sum till index
  | aSumXy       -- 77: dot product of two arrays
  | aSumSqr      -- 78: sum of squares
  | aLerp        -- 79: piecewise linear interpolation
  deriving Repr, DecidableEq, Inhabited

/-- Opcode identifier offset from `OFFSET` (1..79). -/
def Op.toOpId : Op → UInt32
  | .add => 1
  | .sub => 2
  | .mul => 3
  | .div => 4
  | .mod => 5
  | .min => 6
  | .max => 7
  | .pow => 8
  | .sqrt => 9
  | .abs => 10
  | .sign => 11
  | .copySign => 12
  | .exp => 13
  | .floor => 14
  | .log => 15
  | .ln => 16
  | .round => 17
  | .sin => 18
  | .cos => 19
  | .tan => 20
  | .asin => 21
  | .acos => 22
  | .atan => 23
  | .atan2 => 24
  | .mad => 25
  | .ifElse => 26
  | .clamp => 27
  | .cbrt => 28
  | .deg => 29
  | .rad => 30
  | .ceil => 31
  | .aDeref => 32
  | .aMax => 33
  | .aMin => 34
  | .aSum => 35
  | .aAvg => 36
  | .aLen => 37
  | .aSpline => 38
  | .rand => 39
  | .randSeed => 40
  | .noiseFrom => 41
  | .randInRange => 42
  | .squareSum => 43
  | .step => 44
  | .square => 45
  | .dup => 46
  | .hypot => 47
  | .swap => 48
  | .lerp => 49
  | .smoothStep => 50
  | .log2 => 51
  | .inv => 52
  | .fract => 53
  | .pingpong => 54
  | .nop => 55
  | .storeR0 => 56
  | .storeR1 => 57
  | .storeR2 => 58
  | .storeR3 => 59
  | .loadR0 => 60
  | .loadR1 => 61
  | .loadR2 => 62
  | .loadR3 => 63
  | .cmd1 => 64
  | .cmd2 => 65
  | .cmd3 => 66
  | .cmd4 => 67
  | .var1 => 70
  | .var2 => 71
  | .var3 => 72
  | .changeSign => 73
  | .cubic => 74
  | .aSplineLoop => 75
  | .aSumTill => 76
  | .aSumXy => 77
  | .aSumSqr => 78
  | .aLerp => 79

/-- Decodes an opcode identifier into `Op`. -/
def Op.fromOpId (id : UInt32) : Option Op :=
  match id with
  | 1 => some .add
  | 2 => some .sub
  | 3 => some .mul
  | 4 => some .div
  | 5 => some .mod
  | 6 => some .min
  | 7 => some .max
  | 8 => some .pow
  | 9 => some .sqrt
  | 10 => some .abs
  | 11 => some .sign
  | 12 => some .copySign
  | 13 => some .exp
  | 14 => some .floor
  | 15 => some .log
  | 16 => some .ln
  | 17 => some .round
  | 18 => some .sin
  | 19 => some .cos
  | 20 => some .tan
  | 21 => some .asin
  | 22 => some .acos
  | 23 => some .atan
  | 24 => some .atan2
  | 25 => some .mad
  | 26 => some .ifElse
  | 27 => some .clamp
  | 28 => some .cbrt
  | 29 => some .deg
  | 30 => some .rad
  | 31 => some .ceil
  | 32 => some .aDeref
  | 33 => some .aMax
  | 34 => some .aMin
  | 35 => some .aSum
  | 36 => some .aAvg
  | 37 => some .aLen
  | 38 => some .aSpline
  | 39 => some .rand
  | 40 => some .randSeed
  | 41 => some .noiseFrom
  | 42 => some .randInRange
  | 43 => some .squareSum
  | 44 => some .step
  | 45 => some .square
  | 46 => some .dup
  | 47 => some .hypot
  | 48 => some .swap
  | 49 => some .lerp
  | 50 => some .smoothStep
  | 51 => some .log2
  | 52 => some .inv
  | 53 => some .fract
  | 54 => some .pingpong
  | 55 => some .nop
  | 56 => some .storeR0
  | 57 => some .storeR1
  | 58 => some .storeR2
  | 59 => some .storeR3
  | 60 => some .loadR0
  | 61 => some .loadR1
  | 62 => some .loadR2
  | 63 => some .loadR3
  | 64 => some .cmd1
  | 65 => some .cmd2
  | 66 => some .cmd3
  | 67 => some .cmd4
  | 70 => some .var1
  | 71 => some .var2
  | 72 => some .var3
  | 73 => some .changeSign
  | 74 => some .cubic
  | 75 => some .aSplineLoop
  | 76 => some .aSumTill
  | 77 => some .aSumXy
  | 78 => some .aSumSqr
  | 79 => some .aLerp
  | _ => none

/-- Number of operands consumed from the stack. -/
def Op.arity : Op → Nat
  | .nop | .loadR0 | .loadR1 | .loadR2 | .loadR3
  | .cmd1 | .cmd2 | .cmd3 | .cmd4
  | .var1 | .var2 | .var3 | .rand => 0
  | .sqrt | .abs | .sign | .exp | .floor | .log | .ln | .round
  | .sin | .cos | .tan | .asin | .acos | .atan | .cbrt | .deg | .rad | .ceil
  | .aMax | .aMin | .aSum | .aAvg | .aLen
  | .randSeed | .noiseFrom | .square | .dup
  | .log2 | .inv | .fract
  | .storeR0 | .storeR1 | .storeR2 | .storeR3
  | .changeSign | .aSumSqr => 1
  | .add | .sub | .mul | .div | .mod | .min | .max | .pow
  | .copySign | .atan2 | .aDeref | .aSpline | .randInRange
  | .squareSum | .step | .hypot | .swap | .pingpong
  | .aSplineLoop | .aSumTill | .aSumXy | .aLerp => 2
  | .mad | .ifElse | .clamp | .lerp | .smoothStep => 3
  | .cubic => 5

/-- Human readable mathematical name for the operator. -/
def Op.name : Op → String
  | .add => "+"
  | .sub => "-"
  | .mul => "*"
  | .div => "/"
  | .mod => "%"
  | .min => "min"
  | .max => "max"
  | .pow => "pow"
  | .sqrt => "sqrt"
  | .abs => "abs"
  | .sign => "sign"
  | .copySign => "copySign"
  | .exp => "exp"
  | .floor => "floor"
  | .log => "log"
  | .ln => "ln"
  | .round => "round"
  | .sin => "sin"
  | .cos => "cos"
  | .tan => "tan"
  | .asin => "asin"
  | .acos => "acos"
  | .atan => "atan"
  | .atan2 => "atan2"
  | .mad => "mad"
  | .ifElse => "ifElse"
  | .clamp => "clamp"
  | .cbrt => "cbrt"
  | .deg => "deg"
  | .rad => "rad"
  | .ceil => "ceil"
  | .aDeref => "A_DEREF"
  | .aMax => "A_MAX"
  | .aMin => "A_MIN"
  | .aSum => "A_SUM"
  | .aAvg => "A_AVG"
  | .aLen => "A_LEN"
  | .aSpline => "A_SPLINE"
  | .rand => "RAND"
  | .randSeed => "RAND_SEED"
  | .noiseFrom => "noise_from"
  | .randInRange => "rand_in_range"
  | .squareSum => "square_sum"
  | .step => "step"
  | .square => "square"
  | .dup => "dup"
  | .hypot => "hypot"
  | .swap => "swap"
  | .lerp => "lerp"
  | .smoothStep => "smooth_step"
  | .log2 => "log2"
  | .inv => "inv"
  | .fract => "fract"
  | .pingpong => "ping_pong"
  | .nop => "nop"
  | .storeR0 => "store0"
  | .storeR1 => "store1"
  | .storeR2 => "store2"
  | .storeR3 => "store3"
  | .loadR0 => "load0"
  | .loadR1 => "load1"
  | .loadR2 => "load2"
  | .loadR3 => "load3"
  | .cmd1 => "cmd1"
  | .cmd2 => "cmd2"
  | .cmd3 => "cmd3"
  | .cmd4 => "cmd4"
  | .var1 => "a[0]"
  | .var2 => "a[1]"
  | .var3 => "a[2]"
  | .changeSign => "change_sign"
  | .cubic => "cubic"
  | .aSplineLoop => "a_spline_loop"
  | .aSumTill => "a_sum_till"
  | .aSumXy => "a_sum_xy"
  | .aSumSqr => "a_sum_sqr"
  | .aLerp => "a_lerp"

/-- Looks up an `Op` by its mathematical or functional name. -/
def Op.fromName (s : String) : Option Op :=
  match s with
  | "+" | "add" => some .add
  | "-" | "sub" => some .sub
  | "*" | "mul" => some .mul
  | "/" | "div" => some .div
  | "%" | "mod" => some .mod
  | "min" => some .min
  | "max" => some .max
  | "pow" => some .pow
  | "sqrt" => some .sqrt
  | "abs" => some .abs
  | "sign" => some .sign
  | "copySign" => some .copySign
  | "exp" => some .exp
  | "floor" => some .floor
  | "log" => some .log
  | "ln" => some .ln
  | "round" => some .round
  | "sin" => some .sin
  | "cos" => some .cos
  | "tan" => some .tan
  | "asin" => some .asin
  | "acos" => some .acos
  | "atan" => some .atan
  | "atan2" => some .atan2
  | "mad" => some .mad
  | "ifElse" => some .ifElse
  | "clamp" => some .clamp
  | "cbrt" => some .cbrt
  | "deg" => some .deg
  | "rad" => some .rad
  | "ceil" => some .ceil
  | "A_DEREF" | "a_deref" => some .aDeref
  | "A_MAX" | "a_max" => some .aMax
  | "A_MIN" | "a_min" => some .aMin
  | "A_SUM" | "a_sum" => some .aSum
  | "A_AVG" | "a_avg" => some .aAvg
  | "A_LEN" | "a_len" => some .aLen
  | "A_SPLINE" | "a_spline" => some .aSpline
  | "RAND" | "rand" => some .rand
  | "RAND_SEED" | "rand_seed" => some .randSeed
  | "noise_from" => some .noiseFrom
  | "rand_in_range" => some .randInRange
  | "square_sum" => some .squareSum
  | "step" => some .step
  | "square" => some .square
  | "dup" => some .dup
  | "hypot" => some .hypot
  | "swap" => some .swap
  | "lerp" => some .lerp
  | "smooth_step" => some .smoothStep
  | "log2" => some .log2
  | "inv" => some .inv
  | "fract" => some .fract
  | "ping_pong" | "pingpong" => some .pingpong
  | "nop" | "NOP" => some .nop
  | "store0" | "store_r0" => some .storeR0
  | "store1" | "store_r1" => some .storeR1
  | "store2" | "store_r2" => some .storeR2
  | "store3" | "store_r3" => some .storeR3
  | "load0" | "load_r0" => some .loadR0
  | "load1" | "load_r1" => some .loadR1
  | "load2" | "load_r2" => some .loadR2
  | "load3" | "load_r3" => some .loadR3
  | "a[0]" | "var1" => some .var1
  | "a[1]" | "var2" => some .var2
  | "a[2]" | "var3" => some .var3
  | "change_sign" => some .changeSign
  | "cubic" => some .cubic
  | "a_spline_loop" => some .aSplineLoop
  | "a_sum_till" => some .aSumTill
  | "a_sum_xy" => some .aSumXy
  | "a_sum_sqr" => some .aSumSqr
  | "a_lerp" => some .aLerp
  | _ => none

/-- Whether the operator can be rendered infix. -/
def Op.isInfix : Op → Bool
  | .add | .sub | .mul | .div | .mod => true
  | .mad | .ifElse => true
  | _ => false

/-! ## Expression Tokens -/

/-- An expression token in the RPN evaluation sequence. -/
inductive Token where
  | lit (v : Float)
  | op (o : Op)
  | var (id : UInt32)
  | array (id : UInt32)
  deriving Repr, Inhabited

instance : ToString Token where
  toString
    | .lit v => s!"{v}"
    | .op o => o.name
    | .var id => s!"[{id}]"
    | .array id => s!"[A_{id}]"

/-! ## Wire Encoding and Decoding (Bit-level conformance) -/

/-- Checks if 32-bit float IEEE bits represent a NaN. -/
def isRawNan (bits : UInt32) : Bool :=
  (bits &&& 0x7F800000) == 0x7F800000 && (bits &&& 0x007FFFFF) != 0

/-- Extracts the 23-bit payload from a NaN bit pattern. -/
def fromNaNBits (bits : UInt32) : UInt32 :=
  bits &&& ID_MASK_23

/-- Encodes an id into a NaN 32-bit pattern: `v | 0xFF800000`. -/
def asNanBits (v : UInt32) : UInt32 :=
  v ||| 0xFF800000

/-- Unboxes a raw 32-bit word into a typed `Token`.
Matches the wire decoding semantics in `AREA-04` and `AnimatedFloatExpression.java`. -/
def Token.ofRawBits (bits : UInt32) : Token :=
  if !isRawNan bits then
    -- It's a standard IEEE float literal
    -- Convert raw 32-bit bits into Float
    .lit (Float32.ofBits bits).toFloat
  else
    let payload := fromNaNBits bits
    let region := (payload >>> 20) &&& 0x7
    if region == TYPE_OPERATION then
      if payload > OFFSET && payload <= OFFSET + 79 then
        match Op.fromOpId (payload - OFFSET) with
        | some o => .op o
        | none => .var (payload &&& ID_MASK_22)
      else
        .var (payload &&& ID_MASK_22)
    else if region == TYPE_ARRAY then
      .array (payload &&& ID_MASK_22)
    else
      -- System variable or user variable
      .var (payload &&& ID_MASK_22)

/-- Boxes a typed `Token` back into its raw 32-bit wire representation. -/
def Token.toRawBits : Token → UInt32
  | .lit v => (Float.toFloat32 v).toBits
  | .op o => asNanBits (OFFSET + o.toOpId)
  | .array id => asNanBits ((TYPE_ARRAY <<< 20) ||| (id &&& ID_MASK_22))
  | .var id =>
    let region := if id <= 41 then TYPE_SYSTEM else TYPE_VARIABLE
    asNanBits ((region <<< 20) ||| (id &&& ID_MASK_22))

end RemoteCompose.Expression

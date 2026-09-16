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
import RemoteComposeExpression.Eval
import RemoteComposeExpression.FloatExpression

/-!
# Properties and Formal Invariants of the RemoteCompose Expression Engine

Theorems proved against the specification in `Basic.lean`, `Eval.lean`,
and `FloatExpression.lean`. They cover:
* **Opcode and Wire Format Roundtrips**: invertible serialization of operator tokens.
* **Arity and Stack Invariants**: bounds on operands consumed.
* **Safety Rules**: deterministic division by zero without exceptions (AREA-04 §6).
* **Register Isolation and State Preservation**: scratch register roundtrips.
* **Equivalence Results**: algebraic relationships between operators (e.g. `SQUARE ≡ DUP, MUL`).
-/

namespace RemoteCompose.Expression

/-! ## Wire Format & Serialization Invariants -/

/-- Every valid `Op` decodes back to itself from its opcode identifier. -/
theorem op_id_roundtrip (o : Op) : Op.fromOpId (Op.toOpId o) = some o := by
  cases o <;> rfl

/-- Every operator token serialized to raw 32-bit float bits is a NaN. -/
theorem op_is_nan (o : Op) : isRawNan (Token.op o).toRawBits = true := by
  cases o <;> rfl

/-- Every operator token is tagged with region `TYPE_OPERATION` (region 3). -/
theorem op_to_raw_bits_region (o : Op) :
    ((Token.op o).toRawBits >>> 20) &&& 0x7 = TYPE_OPERATION := by
  cases o <;> rfl

/-- Complete wire deserialization roundtrip for all operators. -/
theorem op_from_raw_bits_roundtrip (o : Op) :
    Token.ofRawBits (Token.op o).toRawBits = Token.op o := by
  cases o <;> rfl

/-- Operator arity is strictly bounded by 5 (achieved by `CUBIC`). -/
theorem op_arity_le_five (o : Op) : o.arity <= 5 := by
  cases o <;> decide

/-! ## Safety Rules (AREA-04 Conformance) -/

/-- Division by 0.0 must evaluate to 0.0 deterministically without raising an error. -/
theorem div_by_zero_safe (a : Float) :
    eval [Token.lit a, Token.lit 0.0, Token.op .div] = Except.ok 0.0 := by
  rfl

/-- Remainder modulo 0.0 must evaluate to 0.0 deterministically. -/
theorem mod_by_zero_safe (a : Float) :
    eval [Token.lit a, Token.lit 0.0, Token.op .mod] = Except.ok 0.0 := by
  rfl

/-- Inverse (`1/x`) of 0.0 must evaluate to 0.0 deterministically. -/
theorem inv_zero_safe :
    eval [Token.lit 0.0, Token.op .inv] = Except.ok 0.0 := by
  rfl

/-! ## Scratch Register Roundtrips -/

/-- Value stored into register R0 and subsequently loaded is preserved identically. -/
theorem store_load_r0 (a : Float) :
    eval [Token.lit a, Token.op .storeR0, Token.op .loadR0] = Except.ok a := by
  rfl

/-- Value stored into register R1 and subsequently loaded is preserved identically. -/
theorem store_load_r1 (a : Float) :
    eval [Token.lit a, Token.op .storeR1, Token.op .loadR1] = Except.ok a := by
  rfl

/-- Value stored into register R2 and subsequently loaded is preserved identically. -/
theorem store_load_r2 (a : Float) :
    eval [Token.lit a, Token.op .storeR2, Token.op .loadR2] = Except.ok a := by
  rfl

/-- Value stored into register R3 and subsequently loaded is preserved identically. -/
theorem store_load_r3 (a : Float) :
    eval [Token.lit a, Token.op .storeR3, Token.op .loadR3] = Except.ok a := by
  rfl

/-! ## Operator Algebraic Equivalences -/

/-- `SQUARE` is definitionally equivalent to `DUP` followed by `MUL`. -/
theorem square_eq_dup_mul (a : Float) :
    eval [Token.lit a, Token.op .square] =
    eval [Token.lit a, Token.op .dup, Token.op .mul] := by
  rfl

/-- `CHANGE_SIGN` followed by `CHANGE_SIGN` negates twice. -/
theorem change_sign_twice (a : Float) :
    eval [Token.lit a, Token.op .changeSign, Token.op .changeSign] = Except.ok (- - a) := by
  rfl

/-- `MAD(a, b, c)` computes `c + b * a`. -/
theorem mad_semantics (a b c : Float) :
    eval [Token.lit a, Token.lit b, Token.lit c, Token.op .mad] = Except.ok (c + b * a) := by
  rfl

/-- `IFELSE` with condition > 0 chooses the true branch. -/
theorem ifelse_positive_branch (f t : Float) :
    eval [Token.lit f, Token.lit t, Token.lit 1.0, Token.op .ifElse] = Except.ok t := by
  rfl

/-- `IFELSE` with condition <= 0 chooses the false branch. -/
theorem ifelse_nonpositive_branch (f t : Float) :
    eval [Token.lit f, Token.lit t, Token.lit 0.0, Token.op .ifElse] = Except.ok f := by
  rfl

/-- `STEP` evaluates to 1.0 when operand strictly exceeds the edge threshold. -/
theorem step_exceeds :
    eval [Token.lit 4.0, Token.lit 3.0, Token.op .step] = Except.ok 1.0 := by
  rfl

/-- `STEP` evaluates to 0.0 when operand does not exceed the edge threshold. -/
theorem step_not_exceeds :
    eval [Token.lit 2.0, Token.lit 3.0, Token.op .step] = Except.ok 0.0 := by
  rfl

/-! ## Dependency Tracking Invariants -/

/-- Pure literal expressions have empty variable dependencies. -/
theorem lit_dependencies_empty (a b : Float) :
    FloatExpression.dependencies ⟨1, [Token.lit a, Token.lit b, Token.op .add], none, none, none⟩ = [] := by
  rfl

/-- Variable tokens are correctly registered in the dependency list. -/
theorem var_dependencies_registered :
    FloatExpression.dependencies ⟨1, [Token.var 10, Token.var 20, Token.op .add], none, none, none⟩ = [20, 10] := by
  rfl

end RemoteCompose.Expression

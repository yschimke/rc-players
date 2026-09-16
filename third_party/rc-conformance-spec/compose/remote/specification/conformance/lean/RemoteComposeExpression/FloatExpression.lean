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

/-!
# RemoteCompose Float Expression Engine — FloatExpression Operation

Models the `FloatExpression` operation component (`Operations.ANIMATED_FLOAT = 81`),
mirroring `androidx/compose/remote/core/operations/FloatExpression.java`.
Includes dependency tracking, dynamic variable binding, and reactive dirty detection.
-/

namespace RemoteCompose.Expression

/-- Representation of a RemoteCompose `FloatExpression` operation. -/
structure FloatExpression where
  /-- ID of the resulting float variable in RemoteContext. -/
  id : UInt32
  /-- Source expression token sequence in RPN order. -/
  srcValue : List Token
  /-- Optional animation specification parameters. -/
  srcAnimation : Option (List Float) := none
  /-- Last computed float value. -/
  lastCalculatedValue : Option Float := none
  /-- Timestamp of the last value change (animation time in seconds). -/
  lastChange : Option Float := none
  deriving Repr, Inhabited

namespace FloatExpression

/-- Creates a new FloatExpression with given ID and token sequence. -/
def create (id : UInt32) (tokens : List Token)
    (animation : Option (List Float) := none) : FloatExpression :=
  ⟨id, tokens, animation, none, none⟩

/-- Extracts the set of variable IDs that this expression depends on.
Matches `registerListening` in `FloatExpression.java`. -/
def dependencies (exp : FloatExpression) : List UInt32 :=
  let rec collect (toks : List Token) (acc : List UInt32) : List UInt32 :=
    match toks with
    | [] => acc
    | .var vId :: rest =>
      if acc.contains vId then collect rest acc
      else collect rest (vId :: acc)
    | _ :: rest => collect rest acc
  collect exp.srcValue []

/-- Evaluates the expression given the current context and collections. -/
def evaluate (exp : FloatExpression) (ctx : RemoteContext)
    (ca : CollectionsAccess := .empty) (vars : List Float := []) : Except String Float :=
  eval exp.srcValue ctx ca vars

/-- Evaluates the expression, returning `0.0` on failure. -/
def evaluateD (exp : FloatExpression) (ctx : RemoteContext)
    (ca : CollectionsAccess := .empty) (vars : List Float := []) (defaultVal : Float := 0.0) : Float :=
  evalD exp.srcValue ctx ca vars defaultVal

/-- Updates variables from context, re-evaluates the expression, and detects whether
the output value changed. Matches `updateVariables` in `FloatExpression.java`.
Returns the updated expression structure along with a boolean `valueChanged`. -/
def updateVariables (exp : FloatExpression) (ctx : RemoteContext)
    (ca : CollectionsAccess := .empty) (vars : List Float := []) : FloatExpression × Bool :=
  match exp.evaluate ctx ca vars with
  | .error _ => (exp, false)
  | .ok newVal =>
    match exp.lastCalculatedValue with
    | some oldVal =>
      if oldVal == newVal then
        (exp, false)
      else
        let updated := { exp with
          lastCalculatedValue := some newVal
          lastChange := some ctx.animationTime
        }
        (updated, true)
    | none =>
      -- Initial calculation
      let updated := { exp with
        lastCalculatedValue := some newVal
        lastChange := some ctx.animationTime
      }
      (updated, true)

end FloatExpression

end RemoteCompose.Expression

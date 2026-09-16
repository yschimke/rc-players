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
# Dynamic Layout Expressions & Variable Binding

This module formalizes dynamic layout dimension computation:
1. `LayoutExpr` AST: Arithmetic expressions over state variables (`FloatExpression`,
   `IntegerExpression`, `LayoutComputeOperation`).
2. Evaluation semantics under runtime variable environments.
3. Soundness and boundedness theorems: Clamping guarantees bounds enforcement.
-/

namespace RemoteCompose

/-! ## 1. Expression Syntax -/

inductive LayoutExpr where
  | const (val : S)
  | var (id : Nat)
  | add (e1 e2 : LayoutExpr)
  | sub (e1 e2 : LayoutExpr)
  | mul (e1 e2 : LayoutExpr)
  | clamp (e : LayoutExpr) (minVal maxVal : S)
  deriving Repr, DecidableEq, Inhabited

def Env := Nat → S

def Env.update (env : Env) (id : Nat) (val : S) : Env :=
  fun i => if i = id then val else env i

/-! ## 2. Evaluation Semantics -/

def evalExpr (env : Env) : LayoutExpr → S
  | .const v => v
  | .var id => env id
  | .add e1 e2 => evalExpr env e1 + evalExpr env e2
  | .sub e1 e2 => evalExpr env e1 - evalExpr env e2
  | .mul e1 e2 => evalExpr env e1 * evalExpr env e2
  | .clamp e minV maxV => max minV (min (evalExpr env e) maxV)

/-! ## 3. Semantic Invariants & Theorems -/

/-- Constant expressions evaluate identically across all variable environments. -/
theorem eval_const (env1 env2 : Env) (v : S) :
    evalExpr env1 (.const v) = evalExpr env2 (.const v) := by
  rfl

/-- Clamping Invariant (Lower Bound): eval of clamp is at least minVal. -/
theorem eval_clamp_lower (env : Env) (e : LayoutExpr) (minV maxV : S) :
    minV ≤ evalExpr env (.clamp e minV maxV) := by
  dsimp [evalExpr]
  exact le_max_left minV (min (evalExpr env e) maxV)

/-- Clamping Invariant (Upper Bound): eval of clamp is at most maxVal when minVal <= maxVal. -/
theorem eval_clamp_upper (env : Env) (e : LayoutExpr) (minV maxV : S) (h_order : minV ≤ maxV) :
    evalExpr env (.clamp e minV maxV) ≤ maxV := by
  dsimp [evalExpr]
  apply max_le h_order
  exact min_le_right (evalExpr env e) maxV

/-- Variable set of an expression. -/
def LayoutExpr.vars : LayoutExpr → List Nat
  | .const _ => []
  | .var id => [id]
  | .add e1 e2 => e1.vars ++ e2.vars
  | .sub e1 e2 => e1.vars ++ e2.vars
  | .mul e1 e2 => e1.vars ++ e2.vars
  | .clamp e _ _ => e.vars

/-- Environment Irrelevance: If two environments agree on all variables in an expression,
evalExpr produces identical results. -/
theorem eval_env_agree (e : LayoutExpr) (env1 env2 : Env)
    (h_agree : ∀ id ∈ e.vars, env1 id = env2 id) :
    evalExpr env1 e = evalExpr env2 e := by
  induction e with
  | const v => rfl
  | var id =>
    dsimp [evalExpr]
    dsimp [LayoutExpr.vars] at h_agree
    apply h_agree id (by simp)
  | add e1 e2 ih1 ih2 =>
    dsimp [evalExpr]
    dsimp [LayoutExpr.vars] at h_agree
    have h1 : evalExpr env1 e1 = evalExpr env2 e1 := by
      apply ih1
      intro id h_in
      apply h_agree id
      simp [h_in]
    have h2 : evalExpr env1 e2 = evalExpr env2 e2 := by
      apply ih2
      intro id h_in
      apply h_agree id
      simp [h_in]
    rw [h1, h2]
  | sub e1 e2 ih1 ih2 =>
    dsimp [evalExpr]
    dsimp [LayoutExpr.vars] at h_agree
    have h1 : evalExpr env1 e1 = evalExpr env2 e1 := by
      apply ih1
      intro id h_in
      apply h_agree id
      simp [h_in]
    have h2 : evalExpr env1 e2 = evalExpr env2 e2 := by
      apply ih2
      intro id h_in
      apply h_agree id
      simp [h_in]
    rw [h1, h2]
  | mul e1 e2 ih1 ih2 =>
    dsimp [evalExpr]
    dsimp [LayoutExpr.vars] at h_agree
    have h1 : evalExpr env1 e1 = evalExpr env2 e1 := by
      apply ih1
      intro id h_in
      apply h_agree id
      simp [h_in]
    have h2 : evalExpr env1 e2 = evalExpr env2 e2 := by
      apply ih2
      intro id h_in
      apply h_agree id
      simp [h_in]
    rw [h1, h2]
  | clamp e minV maxV ih =>
    dsimp [evalExpr]
    dsimp [LayoutExpr.vars] at h_agree
    have h : evalExpr env1 e = evalExpr env2 e := ih h_agree
    rw [h]

end RemoteCompose

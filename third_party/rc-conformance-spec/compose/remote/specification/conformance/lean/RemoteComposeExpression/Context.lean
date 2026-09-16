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
# RemoteCompose Float Expression Engine — Context and Collections State

Defines the runtime state and context interfaces:
* `RemoteContext`: variable resolution, time, and system globals.
* `CollectionsAccess`: array lookups for data collection operators.
* `Registers`: scratchpad registers $R_0 \dots R_3$.
-/

namespace RemoteCompose.Expression

/-! ## Scratch Registers -/

/-- Four local scratchpad registers `r0..r3` private to each expression invocation. -/
structure Registers where
  r0 : Float := 0.0
  r1 : Float := 0.0
  r2 : Float := 0.0
  r3 : Float := 0.0
  deriving Repr, Inhabited

def Registers.get (r : Registers) : Nat → Float
  | 0 => r.r0
  | 1 => r.r1
  | 2 => r.r2
  | 3 => r.r3
  | _ => 0.0

def Registers.set (r : Registers) (idx : Nat) (val : Float) : Registers :=
  match idx with
  | 0 => { r with r0 := val }
  | 1 => { r with r1 := val }
  | 2 => { r with r2 := val }
  | 3 => { r with r3 := val }
  | _ => r

/-! ## RemoteContext -/

/-- Runtime environment providing dynamic variables and system values to expressions. -/
structure RemoteContext where
  /-- Lookup function for variable IDs. -/
  varMap : UInt32 → Float := fun _ => 0.0
  animationTime : Float := 0.0
  deriving Inhabited

namespace RemoteContext

/-- Constructs a context from a finite list of `(id, value)` pairs. -/
def fromList (vars : List (UInt32 × Float)) (animTime : Float := 0.0) : RemoteContext where
  varMap := fun id =>
    match vars.find? (fun (k, _) => k == id) with
    | some (_, v) => v
    | none =>
      if id == ID_ANIMATION_TIME then animTime
      else 0.0
  animationTime := animTime

/-- Fetches a float variable by its ID. -/
def getFloat (ctx : RemoteContext) (id : UInt32) : Float :=
  if id == ID_ANIMATION_TIME then
    ctx.animationTime
  else
    let v := ctx.varMap id
    -- Density defaults to 1.0 if unset/0.0
    if id == ID_DENSITY && v == 0.0 then 1.0
    else v

end RemoteContext

/-! ## Collections Access -/

/-- Interface allowing expressions to query float arrays. -/
structure CollectionsAccess where
  arrayMap : UInt32 → Option (Array Float) := fun _ => none
  deriving Inhabited

namespace CollectionsAccess

/-- Empty collections access. -/
def empty : CollectionsAccess := ⟨fun _ => none⟩

/-- Constructs collections access from a list of `(id, array)` pairs. -/
def fromList (arrs : List (UInt32 × Array Float)) : CollectionsAccess where
  arrayMap := fun id =>
    match arrs.find? (fun (k, _) => k == id) with
    | some (_, a) => some a
    | none => none

/-- Fetches the float array for a collection ID. -/
def getFloats (ca : CollectionsAccess) (id : UInt32) : Option (Array Float) :=
  ca.arrayMap id

/-- Fetches the float value at a given array index. -/
def getFloatValue (ca : CollectionsAccess) (id : UInt32) (index : Nat) : Float :=
  match ca.arrayMap id with
  | some arr =>
    if h : index < arr.size then arr[index]
    else 0.0
  | none => 0.0

/-- Gets the length of an array collection. -/
def getListLength (ca : CollectionsAccess) (id : UInt32) : Nat :=
  match ca.arrayMap id with
  | some arr => arr.size
  | none => 0

end CollectionsAccess

end RemoteCompose.Expression

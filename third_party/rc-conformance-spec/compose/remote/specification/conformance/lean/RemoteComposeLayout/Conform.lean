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
import RemoteComposeLayout.Measure

/-!
# Comparing a specified layout against a recorded one

Utilities used by `Conformance.lean`, which is generated from the gold files in
`compose/remote/specification/conformance/gold/layout/` by
`tools/gen_conformance.py`.

The gold files record the bounds the Android engine produced for a document, in
the same coordinate system the specification uses: `x` and `y` are relative to
the parent's content origin (`ComponentMeasure.getX()` / `getY()`).
-/

namespace RemoteCompose

mutual

/-- Every node of a laid-out tree in pre-order, keeping the coordinates
relative to the parent's content origin.

This is the order in which `ConformanceGoldGeneratorTest` records nodes, so a
gold `expected_tree` read back to front lines up with this list. -/
def LayoutTree.preorder : LayoutTree → List Rect
  | .node r _ _ ks => r :: preorderList ks

/-- `LayoutTree.preorder`, lifted over a sibling list. -/
def preorderList : List LayoutTree → List Rect
  | [] => []
  | k :: ks => k.preorder ++ preorderList ks

end

mutual

/-- `ComponentMeasure.isGone()` for every node, in the same pre-order. -/
def LayoutTree.preorderGone : LayoutTree → List Bool
  | .node _ _ g ks => g :: preorderGoneList ks

/-- `LayoutTree.preorderGone`, lifted over a sibling list. -/
def preorderGoneList : List LayoutTree → List Bool
  | [] => []
  | k :: ks => k.preorderGone ++ preorderGoneList ks

end

/-- The tolerance the conformance suite allows, in pixels.

The reference implementation computes in `float` and the gold files store the
resulting decimals; the specification computes exactly. A weight split such as
`200 / 3` therefore reads back as `66.66666666666667`, which agrees with the
specification to well within half a pixel. -/
def defaultTolerance : S := 1 / 2

/-- Whether two rectangles agree to within `tol` on every coordinate. -/
def Rect.approxEq (tol : S) (a b : Rect) : Bool :=
  |a.x - b.x| ≤ tol && |a.y - b.y| ≤ tol &&
  |a.w - b.w| ≤ tol && |a.h - b.h| ≤ tol

/-- Whether two traversals agree pointwise to within `tol`. -/
def rectsApproxEq (tol : S) : List Rect → List Rect → Bool
  | [], [] => true
  | a :: as, b :: bs => Rect.approxEq tol a b && rectsApproxEq tol as bs
  | _, _ => false

/-- The conformance predicate: laying `n` out in a `w × h` viewport reproduces
the recorded bounds `expected`. -/
def conforms (n : Node) (w h : S) (expected : List Rect) : Bool :=
  rectsApproxEq defaultTolerance (layout n w h).preorder expected

/-- The conformance predicate for documents that collapse: as `conforms`, and
additionally every node's recorded visibility must agree.

Geometry alone is a weak check for a collapsible layout, because a gone child
keeps the position of the next visible one — a specification that failed to
collapse anything could still place the survivors correctly in some documents.
Checking `isGone` pointwise closes that gap. -/
def conformsVis (n : Node) (w h : S) (expected : List Rect)
    (expectedGone : List Bool) : Bool :=
  let t := layout n w h
  rectsApproxEq defaultTolerance t.preorder expected
    && t.preorderGone == expectedGone

end RemoteCompose

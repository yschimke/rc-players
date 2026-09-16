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
import RemoteComposeLayout.Conform

/-!
# Conformance of the specification against the Android engine

**This file is generated. Do not edit.**
Regenerate with `python3 tools/gen_conformance.py`.

Each check below replays a document from
`compose/remote/specification/conformance/tests/layout/` through the
specification and compares the result against the bounds the Android engine
recorded in `compose/remote/specification/conformance/gold/layout/`, at every
viewport size the gold file covers.

99 documents are in scope, covering 294 viewport configurations.
-/

namespace RemoteCompose.Conformance

/-- `box_align_corners` — Box placing children at all four corners via horizontal/vertical alignments -/
def box_align_corners : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (30 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.End VAlign.Top [Node.box [Modifier.width (Dim.exact (30 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Bottom [Node.box [Modifier.width (Dim.exact (30 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.End VAlign.Bottom [Node.box [Modifier.width (Dim.exact (30 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center []]]

#guard conformsVis box_align_corners (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(170 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (170 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(170 : S), (170 : S), (30 : S), (30 : S)⟩] [false, false, false, false, false, false, false, false, false]
#guard conformsVis box_align_corners (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(170 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (170 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(170 : S), (170 : S), (30 : S), (30 : S)⟩] [false, false, false, false, false, false, false, false, false]
#guard conformsVis box_align_corners (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(370 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (370 : S), (30 : S), (30 : S)⟩, ⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(370 : S), (370 : S), (30 : S), (30 : S)⟩] [false, false, false, false, false, false, false, false, false]

/-- `box_alignment_all` — Box layout arranging children with various corner and center alignments -/
def box_alignment_all : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.End VAlign.Top [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Bottom [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.End VAlign.Bottom [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]]

#guard conformsVis box_alignment_all (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(260 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (260 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(260 : S), (260 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(120 : S), (120 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false, false, false, false, false, false, false]
#guard conformsVis box_alignment_all (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(260 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (260 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(260 : S), (260 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(120 : S), (120 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false, false, false, false, false, false, false]
#guard conformsVis box_alignment_all (600 : S) (600 : S) [⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(560 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (560 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(560 : S), (560 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(270 : S), (270 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false, false, false, false, false, false, false]

/-- `box_background_border_container` — Box container with background and border styling -/
def box_background_border_container : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis box_background_border_container (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false]
#guard conformsVis box_background_border_container (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false]
#guard conformsVis box_background_border_container (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false]

/-- `box_center_in_parent` — Single box centered precisely within parent container -/
def box_center_in_parent : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis box_center_in_parent (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(70 : S), (70 : S), (60 : S), (60 : S)⟩] [false, false]
#guard conformsVis box_center_in_parent (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(70 : S), (70 : S), (60 : S), (60 : S)⟩] [false, false]
#guard conformsVis box_center_in_parent (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(170 : S), (170 : S), (60 : S), (60 : S)⟩] [false, false]

/-- `box_child_priority` — Box layout children with collapsiblePriority metadata -/
def box_child_priority : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis box_child_priority (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false]
#guard conformsVis box_child_priority (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false]
#guard conformsVis box_child_priority (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false]

/-- `box_fill_max_size_nested` — Nested boxes each using fillMaxSize fractions -/
def box_fill_max_size_nested : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (3 / 4 : S))) none, Modifier.height (Dim.fill (some (3 / 4 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 / 2 : S))) none, Modifier.height (Dim.fill (some (1 / 2 : S))) none] HAlign.Center VAlign.Center []]]

#guard conformsVis box_fill_max_size_nested (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (150 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (75 : S), (75 : S)⟩] [false, false, false]
#guard conformsVis box_fill_max_size_nested (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (150 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (75 : S), (75 : S)⟩] [false, false, false]
#guard conformsVis box_fill_max_size_nested (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (150 : S), (150 : S)⟩] [false, false, false]

/-- `box_padding_all` — Box container and children using padding modifiers -/
def box_padding_all : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(24 : S), (24 : S), (24 : S), (24 : S)⟩] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(12 : S), (12 : S), (12 : S), (12 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis box_padding_all (250 : S) (250 : S) [⟨(0 : S), (0 : S), (250 : S), (250 : S)⟩, ⟨(0 : S), (0 : S), (202 : S), (202 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis box_padding_all (250 : S) (250 : S) [⟨(0 : S), (0 : S), (250 : S), (250 : S)⟩, ⟨(0 : S), (0 : S), (202 : S), (202 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis box_padding_all (500 : S) (500 : S) [⟨(0 : S), (0 : S), (500 : S), (500 : S)⟩, ⟨(0 : S), (0 : S), (452 : S), (452 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false]

/-- `box_stack` — Box container stacking overlapping children with distinct alignments -/
def box_stack : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (120 : S)) none, Modifier.height (Dim.exact (120 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis box_stack (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (120 : S)⟩] [false, false, false]
#guard conformsVis box_stack (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (120 : S)⟩] [false, false, false]
#guard conformsVis box_stack (600 : S) (600 : S) [⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (120 : S)⟩] [false, false, false]

/-- `collapsible_column` — Collapsible column hiding low-priority children vertically on height overflow -/
def collapsible_column : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.height (Dim.exact (70 : S)) none, Modifier.width (Dim.exact (100 : S)) none, Modifier.collapsiblePriority Orient.vertical (10 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.height (Dim.exact (70 : S)) none, Modifier.width (Dim.exact (100 : S)) none, Modifier.collapsiblePriority Orient.vertical (5 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.height (Dim.exact (70 : S)) none, Modifier.width (Dim.exact (100 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column (200 : S) (180 : S) [⟨(0 : S), (0 : S), (200 : S), (180 : S)⟩, ⟨(50 : S), (20 : S), (100 : S), (70 : S)⟩, ⟨(50 : S), (90 : S), (100 : S), (70 : S)⟩, ⟨(50 : S), (160 : S), (100 : S), (70 : S)⟩] [false, false, false, true]
#guard conformsVis collapsible_column (200 : S) (180 : S) [⟨(0 : S), (0 : S), (200 : S), (180 : S)⟩, ⟨(50 : S), (20 : S), (100 : S), (70 : S)⟩, ⟨(50 : S), (90 : S), (100 : S), (70 : S)⟩, ⟨(50 : S), (160 : S), (100 : S), (70 : S)⟩] [false, false, false, true]
#guard conformsVis collapsible_column (400 : S) (360 : S) [⟨(0 : S), (0 : S), (400 : S), (360 : S)⟩, ⟨(150 : S), (75 : S), (100 : S), (70 : S)⟩, ⟨(150 : S), (145 : S), (100 : S), (70 : S)⟩, ⟨(150 : S), (215 : S), (100 : S), (70 : S)⟩] [false, false, false, false]

/-- `collapsible_column_all_fit` — CollapsibleColumn sized adequately so all priority children remain visible -/
def collapsible_column_all_fit : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (3 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_all_fit (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(25 : S), (90 : S), (100 : S), (40 : S)⟩, ⟨(25 : S), (130 : S), (100 : S), (40 : S)⟩, ⟨(25 : S), (170 : S), (100 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_column_all_fit (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(25 : S), (90 : S), (100 : S), (40 : S)⟩, ⟨(25 : S), (130 : S), (100 : S), (40 : S)⟩, ⟨(25 : S), (170 : S), (100 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_column_all_fit (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(100 : S), (240 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (280 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (320 : S), (100 : S), (40 : S)⟩] [false, false, false, false]

/-- `collapsible_column_background_border` — CollapsibleColumn container with background and border styling -/
def collapsible_column_background_border : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_background_border (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (110 : S), (60 : S), (40 : S)⟩] [false, false]
#guard conformsVis collapsible_column_background_border (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (110 : S), (60 : S), (40 : S)⟩] [false, false]
#guard conformsVis collapsible_column_background_border (300 : S) (520 : S) [⟨(0 : S), (0 : S), (300 : S), (520 : S)⟩, ⟨(120 : S), (240 : S), (60 : S), (40 : S)⟩] [false, false]

/-- `collapsible_column_child_graphicslayer` — CollapsibleColumn items with graphicsLayer transformations -/
def collapsible_column_child_graphicslayer : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_child_graphicslayer (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (90 : S), (60 : S), (40 : S)⟩, ⟨(45 : S), (130 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_child_graphicslayer (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (90 : S), (60 : S), (40 : S)⟩, ⟨(45 : S), (130 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_child_graphicslayer (300 : S) (520 : S) [⟨(0 : S), (0 : S), (300 : S), (520 : S)⟩, ⟨(120 : S), (220 : S), (60 : S), (40 : S)⟩, ⟨(120 : S), (260 : S), (60 : S), (40 : S)⟩] [false, false, false]

/-- `collapsible_column_child_padding` — CollapsibleColumn children with padding modifiers -/
def collapsible_column_child_padding : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.padding ⟨(8 : S), (8 : S), (8 : S), (8 : S)⟩, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.padding ⟨(8 : S), (8 : S), (8 : S), (8 : S)⟩, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_child_padding (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (90 : S), (60 : S), (40 : S)⟩, ⟨(45 : S), (130 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_child_padding (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (90 : S), (60 : S), (40 : S)⟩, ⟨(45 : S), (130 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_child_padding (300 : S) (520 : S) [⟨(0 : S), (0 : S), (300 : S), (520 : S)⟩, ⟨(120 : S), (220 : S), (60 : S), (40 : S)⟩, ⟨(120 : S), (260 : S), (60 : S), (40 : S)⟩] [false, false, false]

/-- `collapsible_column_child_zindex` — CollapsibleColumn items with custom zIndex ordering -/
def collapsible_column_child_zindex : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_child_zindex (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (90 : S), (60 : S), (40 : S)⟩, ⟨(45 : S), (130 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_child_zindex (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(45 : S), (90 : S), (60 : S), (40 : S)⟩, ⟨(45 : S), (130 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_child_zindex (300 : S) (520 : S) [⟨(0 : S), (0 : S), (300 : S), (520 : S)⟩, ⟨(120 : S), (220 : S), (60 : S), (40 : S)⟩, ⟨(120 : S), (260 : S), (60 : S), (40 : S)⟩] [false, false, false]

/-- `collapsible_column_container_gone` — CollapsibleColumn where available height cannot accommodate any child, collapsing container to GONE -/
def collapsible_column_container_gone : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (50 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_container_gone (100 : S) (40 : S) [⟨(0 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(20 : S), (20 : S), (60 : S), (80 : S)⟩, ⟨(20 : S), (20 : S), (60 : S), (80 : S)⟩] [true, true, true]

/-- `collapsible_column_non_contiguous` — CollapsibleColumn where middle child has lowest priority and collapses while outer children compact vertically -/
def collapsible_column_non_contiguous : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (5 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (50 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_non_contiguous (120 : S) (200 : S) [⟨(0 : S), (0 : S), (120 : S), (200 : S)⟩, ⟨(25 : S), (20 : S), (70 : S), (80 : S)⟩, ⟨(25 : S), (100 : S), (70 : S), (80 : S)⟩, ⟨(25 : S), (100 : S), (70 : S), (80 : S)⟩] [false, false, true, false]

/-- `collapsible_column_padding_container` — CollapsibleColumn container with outer padding modifier -/
def collapsible_column_padding_container : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(16 : S), (16 : S), (16 : S), (16 : S)⟩] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_padding_container (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(29 : S), (74 : S), (60 : S), (40 : S)⟩, ⟨(29 : S), (114 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_padding_container (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(29 : S), (74 : S), (60 : S), (40 : S)⟩, ⟨(29 : S), (114 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_padding_container (300 : S) (520 : S) [⟨(0 : S), (0 : S), (300 : S), (520 : S)⟩, ⟨(104 : S), (204 : S), (60 : S), (40 : S)⟩, ⟨(104 : S), (244 : S), (60 : S), (40 : S)⟩] [false, false, false]

/-- `collapsible_column_priority_collapse` — CollapsibleColumn where lowest priority child collapses and is marked GONE when vertical space is insufficient -/
def collapsible_column_priority_collapse : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (50 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (10 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_priority_collapse (150 : S) (200 : S) [⟨(0 : S), (0 : S), (150 : S), (200 : S)⟩, ⟨(35 : S), (20 : S), (80 : S), (80 : S)⟩, ⟨(35 : S), (100 : S), (80 : S), (80 : S)⟩, ⟨(35 : S), (180 : S), (80 : S), (80 : S)⟩] [false, false, false, true]

/-- `collapsible_column_space_resize` — CollapsibleColumn dynamically collapsing children as height shrinks and uncollapsing as height expands -/
def collapsible_column_space_resize : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (50 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (10 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_space_resize (120 : S) (280 : S) [⟨(0 : S), (0 : S), (120 : S), (280 : S)⟩, ⟨(30 : S), (20 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (100 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (180 : S), (60 : S), (80 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_column_space_resize (120 : S) (280 : S) [⟨(0 : S), (0 : S), (120 : S), (280 : S)⟩, ⟨(30 : S), (20 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (100 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (180 : S), (60 : S), (80 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_column_space_resize (120 : S) (190 : S) [⟨(0 : S), (0 : S), (120 : S), (190 : S)⟩, ⟨(30 : S), (15 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (95 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (175 : S), (60 : S), (80 : S)⟩] [false, false, false, true]
#guard conformsVis collapsible_column_space_resize (120 : S) (100 : S) [⟨(0 : S), (0 : S), (120 : S), (100 : S)⟩, ⟨(30 : S), (10 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (90 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (90 : S), (60 : S), (80 : S)⟩] [false, false, true, true]
#guard conformsVis collapsible_column_space_resize (120 : S) (40 : S) [⟨(0 : S), (0 : S), (120 : S), (40 : S)⟩, ⟨(30 : S), (20 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (20 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (20 : S), (60 : S), (80 : S)⟩] [true, true, true, true]
#guard conformsVis collapsible_column_space_resize (120 : S) (280 : S) [⟨(0 : S), (0 : S), (120 : S), (280 : S)⟩, ⟨(30 : S), (20 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (100 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (180 : S), (60 : S), (80 : S)⟩] [false, false, false, false]

/-- `collapsible_column_spacing` — CollapsibleColumn with priority items and vertical item spacing -/
def collapsible_column_spacing : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.vertical (10 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.vertical (5 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.vertical (20 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_spacing (150 : S) (220 : S) [⟨(0 : S), (0 : S), (150 : S), (220 : S)⟩, ⟨(25 : S), (10 : S), (100 : S), (50 : S)⟩, ⟨(25 : S), (60 : S), (100 : S), (50 : S)⟩, ⟨(25 : S), (110 : S), (100 : S), (50 : S)⟩, ⟨(25 : S), (160 : S), (100 : S), (50 : S)⟩] [false, false, false, false, false]
#guard conformsVis collapsible_column_spacing (150 : S) (220 : S) [⟨(0 : S), (0 : S), (150 : S), (220 : S)⟩, ⟨(25 : S), (10 : S), (100 : S), (50 : S)⟩, ⟨(25 : S), (60 : S), (100 : S), (50 : S)⟩, ⟨(25 : S), (110 : S), (100 : S), (50 : S)⟩, ⟨(25 : S), (160 : S), (100 : S), (50 : S)⟩] [false, false, false, false, false]
#guard conformsVis collapsible_column_spacing (300 : S) (440 : S) [⟨(0 : S), (0 : S), (300 : S), (440 : S)⟩, ⟨(100 : S), (120 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (170 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (220 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (270 : S), (100 : S), (50 : S)⟩] [false, false, false, false, false]

/-- `collapsible_column_spacing_collapse` — CollapsibleColumn with spacedBy where overflowing child is marked GONE and remaining visible children use spacing -/
def collapsible_column_spacing_collapse : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (50 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none, Modifier.collapsiblePriority Orient.vertical (10 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_spacing_collapse (120 : S) (220 : S) [⟨(0 : S), (0 : S), (120 : S), (220 : S)⟩, ⟨(30 : S), (30 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (110 : S), (60 : S), (80 : S)⟩, ⟨(30 : S), (190 : S), (60 : S), (80 : S)⟩] [false, false, false, true]

/-- `collapsible_column_weights` — CollapsibleColumn items combining proportional weights and priorities -/
def collapsible_column_weights : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.height (Dim.weight (1 : S)) none, Modifier.width (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.height (Dim.weight (2 : S)) none, Modifier.width (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_column_weights (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(45 : S), (0 : S), (60 : S), (100 : S)⟩, ⟨(45 : S), (100 : S), (60 : S), (200 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_weights (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(45 : S), (0 : S), (60 : S), (100 : S)⟩, ⟨(45 : S), (100 : S), (60 : S), (200 : S)⟩] [false, false, false]
#guard conformsVis collapsible_column_weights (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (200 : S)⟩, ⟨(120 : S), (200 : S), (60 : S), (400 : S)⟩] [false, false, false]

/-- `collapsible_row` — CollapsibleRow displaying children according to priority threshold -/
def collapsible_row : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (3 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row (200 : S) (100 : S) [⟨(0 : S), (0 : S), (200 : S), (100 : S)⟩, ⟨(10 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(10 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(100 : S), (25 : S), (90 : S), (50 : S)⟩] [false, true, false, false]
#guard conformsVis collapsible_row (200 : S) (100 : S) [⟨(0 : S), (0 : S), (200 : S), (100 : S)⟩, ⟨(10 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(10 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(100 : S), (25 : S), (90 : S), (50 : S)⟩] [false, true, false, false]
#guard conformsVis collapsible_row (400 : S) (200 : S) [⟨(0 : S), (0 : S), (400 : S), (200 : S)⟩, ⟨(65 : S), (75 : S), (90 : S), (50 : S)⟩, ⟨(155 : S), (75 : S), (90 : S), (50 : S)⟩, ⟨(245 : S), (75 : S), (90 : S), (50 : S)⟩] [false, false, false, false]

/-- `collapsible_row_all_fit` — CollapsibleRow sized adequately so all priority children remain visible -/
def collapsible_row_all_fit : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (3 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_all_fit (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(90 : S), (25 : S), (40 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (40 : S), (50 : S)⟩, ⟨(170 : S), (25 : S), (40 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_row_all_fit (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(90 : S), (25 : S), (40 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (40 : S), (50 : S)⟩, ⟨(170 : S), (25 : S), (40 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_row_all_fit (600 : S) (200 : S) [⟨(0 : S), (0 : S), (600 : S), (200 : S)⟩, ⟨(240 : S), (75 : S), (40 : S), (50 : S)⟩, ⟨(280 : S), (75 : S), (40 : S), (50 : S)⟩, ⟨(320 : S), (75 : S), (40 : S), (50 : S)⟩] [false, false, false, false]

/-- `collapsible_row_background_border` — CollapsibleRow container with background and border styling -/
def collapsible_row_background_border : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_background_border (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(105 : S), (30 : S), (50 : S), (40 : S)⟩] [false, false]
#guard conformsVis collapsible_row_background_border (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(105 : S), (30 : S), (50 : S), (40 : S)⟩] [false, false]
#guard conformsVis collapsible_row_background_border (520 : S) (200 : S) [⟨(0 : S), (0 : S), (520 : S), (200 : S)⟩, ⟨(235 : S), (80 : S), (50 : S), (40 : S)⟩] [false, false]

/-- `collapsible_row_child_graphicslayer` — CollapsibleRow items with graphicsLayer transformations -/
def collapsible_row_child_graphicslayer : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_child_graphicslayer (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(80 : S), (25 : S), (50 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_child_graphicslayer (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(80 : S), (25 : S), (50 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_child_graphicslayer (520 : S) (200 : S) [⟨(0 : S), (0 : S), (520 : S), (200 : S)⟩, ⟨(210 : S), (75 : S), (50 : S), (50 : S)⟩, ⟨(260 : S), (75 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `collapsible_row_child_padding` — CollapsibleRow children with padding modifiers -/
def collapsible_row_child_padding : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.padding ⟨(8 : S), (8 : S), (8 : S), (8 : S)⟩, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.padding ⟨(8 : S), (8 : S), (8 : S), (8 : S)⟩, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_child_padding (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(70 : S), (25 : S), (60 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (60 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_child_padding (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(70 : S), (25 : S), (60 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (60 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_child_padding (520 : S) (200 : S) [⟨(0 : S), (0 : S), (520 : S), (200 : S)⟩, ⟨(200 : S), (75 : S), (60 : S), (50 : S)⟩, ⟨(260 : S), (75 : S), (60 : S), (50 : S)⟩] [false, false, false]

/-- `collapsible_row_child_zindex` — CollapsibleRow items with custom zIndex ordering -/
def collapsible_row_child_zindex : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_child_zindex (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(80 : S), (25 : S), (50 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_child_zindex (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(80 : S), (25 : S), (50 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_child_zindex (520 : S) (200 : S) [⟨(0 : S), (0 : S), (520 : S), (200 : S)⟩, ⟨(210 : S), (75 : S), (50 : S), (50 : S)⟩, ⟨(260 : S), (75 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `collapsible_row_container_gone` — CollapsibleRow where available width cannot accommodate even highest priority child, collapsing container to GONE -/
def collapsible_row_container_gone : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (50 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_container_gone (60 : S) (100 : S) [⟨(0 : S), (0 : S), (60 : S), (100 : S)⟩, ⟨(30 : S), (25 : S), (100 : S), (50 : S)⟩, ⟨(30 : S), (25 : S), (100 : S), (50 : S)⟩] [true, true, true]

/-- `collapsible_row_non_contiguous` — CollapsibleRow where middle child has lowest priority and collapses while outer children compact without gap -/
def collapsible_row_non_contiguous : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (5 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (50 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_non_contiguous (220 : S) (100 : S) [⟨(0 : S), (0 : S), (220 : S), (100 : S)⟩, ⟨(20 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(110 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(110 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, true, false]

/-- `collapsible_row_padding_container` — CollapsibleRow container with outer padding modifier -/
def collapsible_row_padding_container : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(16 : S), (16 : S), (16 : S), (16 : S)⟩] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_padding_container (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(64 : S), (9 : S), (50 : S), (50 : S)⟩, ⟨(114 : S), (9 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_padding_container (260 : S) (100 : S) [⟨(0 : S), (0 : S), (260 : S), (100 : S)⟩, ⟨(64 : S), (9 : S), (50 : S), (50 : S)⟩, ⟨(114 : S), (9 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_padding_container (520 : S) (200 : S) [⟨(0 : S), (0 : S), (520 : S), (200 : S)⟩, ⟨(194 : S), (59 : S), (50 : S), (50 : S)⟩, ⟨(244 : S), (59 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `collapsible_row_priority_collapse` — CollapsibleRow where lowest priority child collapses and is marked GONE when space is insufficient -/
def collapsible_row_priority_collapse : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (50 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (10 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_priority_collapse (250 : S) (100 : S) [⟨(0 : S), (0 : S), (250 : S), (100 : S)⟩, ⟨(25 : S), (20 : S), (100 : S), (60 : S)⟩, ⟨(125 : S), (20 : S), (100 : S), (60 : S)⟩, ⟨(225 : S), (20 : S), (100 : S), (60 : S)⟩] [false, false, false, true]

/-- `collapsible_row_space_resize` — CollapsibleRow dynamically collapsing children as width shrinks and uncollapsing as width expands -/
def collapsible_row_space_resize : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (50 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (10 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_space_resize (350 : S) (100 : S) [⟨(0 : S), (0 : S), (350 : S), (100 : S)⟩, ⟨(40 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(220 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_row_space_resize (350 : S) (100 : S) [⟨(0 : S), (0 : S), (350 : S), (100 : S)⟩, ⟨(40 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(220 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis collapsible_row_space_resize (220 : S) (100 : S) [⟨(0 : S), (0 : S), (220 : S), (100 : S)⟩, ⟨(20 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(110 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(200 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, false, true]
#guard conformsVis collapsible_row_space_resize (120 : S) (100 : S) [⟨(0 : S), (0 : S), (120 : S), (100 : S)⟩, ⟨(15 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(105 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(105 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, true, true]
#guard conformsVis collapsible_row_space_resize (50 : S) (100 : S) [⟨(0 : S), (0 : S), (50 : S), (100 : S)⟩, ⟨(25 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(25 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(25 : S), (25 : S), (90 : S), (50 : S)⟩] [true, true, true, true]
#guard conformsVis collapsible_row_space_resize (350 : S) (100 : S) [⟨(0 : S), (0 : S), (350 : S), (100 : S)⟩, ⟨(40 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(130 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(220 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, false, false]

/-- `collapsible_row_spacing` — CollapsibleRow with priority items and horizontal item spacing -/
def collapsible_row_spacing : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (10 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (5 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (20 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_spacing (220 : S) (120 : S) [⟨(0 : S), (0 : S), (220 : S), (120 : S)⟩, ⟨(10 : S), (30 : S), (50 : S), (60 : S)⟩, ⟨(60 : S), (30 : S), (50 : S), (60 : S)⟩, ⟨(110 : S), (30 : S), (50 : S), (60 : S)⟩, ⟨(160 : S), (30 : S), (50 : S), (60 : S)⟩] [false, false, false, false, false]
#guard conformsVis collapsible_row_spacing (220 : S) (120 : S) [⟨(0 : S), (0 : S), (220 : S), (120 : S)⟩, ⟨(10 : S), (30 : S), (50 : S), (60 : S)⟩, ⟨(60 : S), (30 : S), (50 : S), (60 : S)⟩, ⟨(110 : S), (30 : S), (50 : S), (60 : S)⟩, ⟨(160 : S), (30 : S), (50 : S), (60 : S)⟩] [false, false, false, false, false]
#guard conformsVis collapsible_row_spacing (440 : S) (240 : S) [⟨(0 : S), (0 : S), (440 : S), (240 : S)⟩, ⟨(120 : S), (90 : S), (50 : S), (60 : S)⟩, ⟨(170 : S), (90 : S), (50 : S), (60 : S)⟩, ⟨(220 : S), (90 : S), (50 : S), (60 : S)⟩, ⟨(270 : S), (90 : S), (50 : S), (60 : S)⟩] [false, false, false, false, false]

/-- `collapsible_row_spacing_collapse` — CollapsibleRow with spacedBy where overflowing child is marked GONE and remaining visible children use spacing -/
def collapsible_row_spacing_collapse : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (100 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (50 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (10 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_spacing_collapse (220 : S) (100 : S) [⟨(0 : S), (0 : S), (220 : S), (100 : S)⟩, ⟨(20 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(110 : S), (25 : S), (90 : S), (50 : S)⟩, ⟨(200 : S), (25 : S), (90 : S), (50 : S)⟩] [false, false, false, true]

/-- `collapsible_row_weights` — CollapsibleRow items combining proportional weights and priorities -/
def collapsible_row_weights : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.weight (1 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.weight (2 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis collapsible_row_weights (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (25 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (25 : S), (200 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_weights (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (25 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (25 : S), (200 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis collapsible_row_weights (600 : S) (200 : S) [⟨(0 : S), (0 : S), (600 : S), (200 : S)⟩, ⟨(0 : S), (75 : S), (200 : S), (50 : S)⟩, ⟨(200 : S), (75 : S), (400 : S), (50 : S)⟩] [false, false, false]

/-- `column_align_center_mixed_widths` — Column with horizontal center alignment and varying child widths -/
def column_align_center_mixed_widths : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (120 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_align_center_mixed_widths (200 : S) (300 : S) [⟨(0 : S), (0 : S), (200 : S), (300 : S)⟩, ⟨(80 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(40 : S), (40 : S), (120 : S), (50 : S)⟩, ⟨(60 : S), (90 : S), (80 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis column_align_center_mixed_widths (200 : S) (300 : S) [⟨(0 : S), (0 : S), (200 : S), (300 : S)⟩, ⟨(80 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(40 : S), (40 : S), (120 : S), (50 : S)⟩, ⟨(60 : S), (90 : S), (80 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis column_align_center_mixed_widths (400 : S) (600 : S) [⟨(0 : S), (0 : S), (400 : S), (600 : S)⟩, ⟨(180 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(140 : S), (40 : S), (120 : S), (50 : S)⟩, ⟨(160 : S), (90 : S), (80 : S), (40 : S)⟩] [false, false, false, false]

/-- `column_align_start_end` — Column container with end alignment positioning all children -/
def column_align_start_end : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.End Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_align_start_end (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(160 : S), (0 : S), (40 : S), (30 : S)⟩, ⟨(130 : S), (30 : S), (70 : S), (30 : S)⟩, ⟨(150 : S), (60 : S), (50 : S), (30 : S)⟩] [false, false, false, false]
#guard conformsVis column_align_start_end (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(160 : S), (0 : S), (40 : S), (30 : S)⟩, ⟨(130 : S), (30 : S), (70 : S), (30 : S)⟩, ⟨(150 : S), (60 : S), (50 : S), (30 : S)⟩] [false, false, false, false]
#guard conformsVis column_align_start_end (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(360 : S), (0 : S), (40 : S), (30 : S)⟩, ⟨(330 : S), (30 : S), (70 : S), (30 : S)⟩, ⟨(350 : S), (60 : S), (50 : S), (30 : S)⟩] [false, false, false, false]

/-- `column_alignment_space_around` — Column layout with spaceAround vertical distribution -/
def column_alignment_space_around : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.SpaceAround (0 : S) [Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_alignment_space_around (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (50 : S), (70 : S), (50 : S)⟩, ⟨(0 : S), (200 : S), (70 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis column_alignment_space_around (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (50 : S), (70 : S), (50 : S)⟩, ⟨(0 : S), (200 : S), (70 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis column_alignment_space_around (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(0 : S), (125 : S), (70 : S), (50 : S)⟩, ⟨(0 : S), (425 : S), (70 : S), (50 : S)⟩] [false, false, false]

/-- `column_alignment_space_between` — Column layout with spaceBetween vertical distribution -/
def column_alignment_space_between : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.SpaceBetween (0 : S) [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_alignment_space_between (150 : S) (350 : S) [⟨(0 : S), (0 : S), (150 : S), (350 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (155 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (310 : S), (80 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis column_alignment_space_between (150 : S) (350 : S) [⟨(0 : S), (0 : S), (150 : S), (350 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (155 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (310 : S), (80 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis column_alignment_space_between (300 : S) (700 : S) [⟨(0 : S), (0 : S), (300 : S), (700 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (330 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (660 : S), (80 : S), (40 : S)⟩] [false, false, false, false]

/-- `column_alignment_space_evenly` — Column layout with spaceEvenly vertical distribution -/
def column_alignment_space_evenly : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.SpaceEvenly (0 : S) [Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_alignment_space_evenly (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (7333 / 100 : S), (70 : S), (40 : S)⟩, ⟨(0 : S), (18667 / 100 : S), (70 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis column_alignment_space_evenly (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (7333 / 100 : S), (70 : S), (40 : S)⟩, ⟨(0 : S), (18667 / 100 : S), (70 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis column_alignment_space_evenly (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(0 : S), (17333 / 100 : S), (70 : S), (40 : S)⟩, ⟨(0 : S), (38667 / 100 : S), (70 : S), (40 : S)⟩] [false, false, false]

/-- `column_background_border` — Column container with background and border styling -/
def column_background_border : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_background_border (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false]
#guard conformsVis column_background_border (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false]
#guard conformsVis column_background_border (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false]

/-- `column_child_graphicslayer` — Column children with graphicsLayer transformations -/
def column_child_graphicslayer : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_child_graphicslayer (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis column_child_graphicslayer (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis column_child_graphicslayer (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `column_child_zindex` — Column children with custom zIndex ordering -/
def column_child_zindex : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_child_zindex (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis column_child_zindex (150 : S) (300 : S) [⟨(0 : S), (0 : S), (150 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis column_child_zindex (300 : S) (600 : S) [⟨(0 : S), (0 : S), (300 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `column_fill_max_width` — Column with children filling parent maximum width -/
def column_fill_max_width : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.fill (some (3 / 5 : S))) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_fill_max_width (300 : S) (250 : S) [⟨(0 : S), (0 : S), (300 : S), (250 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (180 : S), (50 : S)⟩, ⟨(0 : S), (90 : S), (100 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis column_fill_max_width (300 : S) (250 : S) [⟨(0 : S), (0 : S), (300 : S), (250 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (180 : S), (50 : S)⟩, ⟨(0 : S), (90 : S), (100 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis column_fill_max_width (600 : S) (500 : S) [⟨(0 : S), (0 : S), (600 : S), (500 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (360 : S), (50 : S)⟩, ⟨(0 : S), (90 : S), (100 : S), (40 : S)⟩] [false, false, false, false]

/-- `column_multi_weights` — Column dividing available height according to 1:2:3 fractional weights -/
def column_multi_weights : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.height (Dim.weight (1 : S)) none, Modifier.width (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.height (Dim.weight (2 : S)) none, Modifier.width (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.height (Dim.weight (3 : S)) none, Modifier.width (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_multi_weights (120 : S) (360 : S) [⟨(0 : S), (0 : S), (120 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (80 : S), (120 : S)⟩, ⟨(0 : S), (180 : S), (80 : S), (180 : S)⟩] [false, false, false, false]
#guard conformsVis column_multi_weights (120 : S) (360 : S) [⟨(0 : S), (0 : S), (120 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (80 : S), (120 : S)⟩, ⟨(0 : S), (180 : S), (80 : S), (180 : S)⟩] [false, false, false, false]
#guard conformsVis column_multi_weights (240 : S) (720 : S) [⟨(0 : S), (0 : S), (240 : S), (720 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (120 : S)⟩, ⟨(0 : S), (120 : S), (80 : S), (240 : S)⟩, ⟨(0 : S), (360 : S), (80 : S), (360 : S)⟩] [false, false, false, false]

/-- `column_nested_rows` — Column containing nested Rows with proportional weights -/
def column_nested_rows : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.row [Modifier.height (Dim.weight (1 : S)) none, Modifier.width (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (30 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []], Node.row [Modifier.height (Dim.weight (2 : S)) none, Modifier.width (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]]

#guard conformsVis column_nested_rows (250 : S) (300 : S) [⟨(0 : S), (0 : S), (250 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (250 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(30 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (100 : S), (250 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false, false, false, false]
#guard conformsVis column_nested_rows (250 : S) (300 : S) [⟨(0 : S), (0 : S), (250 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (250 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(30 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (100 : S), (250 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false, false, false, false]
#guard conformsVis column_nested_rows (500 : S) (600 : S) [⟨(0 : S), (0 : S), (500 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (500 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (30 : S), (30 : S)⟩, ⟨(30 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (200 : S), (500 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false, false, false, false]

/-- `column_padding_all` — Column container and children using uniform padding modifiers -/
def column_padding_all : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(20 : S), (20 : S), (20 : S), (20 : S)⟩] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none, Modifier.padding ⟨(6 : S), (6 : S), (6 : S), (6 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.padding ⟨(10 : S), (10 : S), (10 : S), (10 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_padding_all (200 : S) (350 : S) [⟨(0 : S), (0 : S), (200 : S), (350 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (80 : S), (50 : S)⟩, ⟨(0 : S), (90 : S), (50 : S), (30 : S)⟩] [false, false, false, false]
#guard conformsVis column_padding_all (200 : S) (350 : S) [⟨(0 : S), (0 : S), (200 : S), (350 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (80 : S), (50 : S)⟩, ⟨(0 : S), (90 : S), (50 : S), (30 : S)⟩] [false, false, false, false]
#guard conformsVis column_padding_all (400 : S) (700 : S) [⟨(0 : S), (0 : S), (400 : S), (700 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (80 : S), (50 : S)⟩, ⟨(0 : S), (90 : S), (50 : S), (30 : S)⟩] [false, false, false, false]

/-- `column_spaced_by` — Column layout with spacedBy modifier distributing inter-item gaps -/
def column_spaced_by : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (15 : S) [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (70 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_spaced_by (250 : S) (400 : S) [⟨(0 : S), (0 : S), (250 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (50 : S)⟩, ⟨(0 : S), (65 : S), (80 : S), (60 : S)⟩, ⟨(0 : S), (140 : S), (80 : S), (70 : S)⟩] [false, false, false, false]
#guard conformsVis column_spaced_by (250 : S) (400 : S) [⟨(0 : S), (0 : S), (250 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (50 : S)⟩, ⟨(0 : S), (65 : S), (80 : S), (60 : S)⟩, ⟨(0 : S), (140 : S), (80 : S), (70 : S)⟩] [false, false, false, false]
#guard conformsVis column_spaced_by (500 : S) (800 : S) [⟨(0 : S), (0 : S), (500 : S), (800 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (50 : S)⟩, ⟨(0 : S), (65 : S), (80 : S), (60 : S)⟩, ⟨(0 : S), (140 : S), (80 : S), (70 : S)⟩] [false, false, false, false]

/-- `column_weights` — Column layout distributing remaining vertical height among weighted children -/
def column_weights : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.weight (1 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.weight (3 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis column_weights (200 : S) (500 : S) [⟨(0 : S), (0 : S), (200 : S), (500 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (100 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (200 : S), (100 : S), (300 : S)⟩] [false, false, false, false]
#guard conformsVis column_weights (200 : S) (500 : S) [⟨(0 : S), (0 : S), (200 : S), (500 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (100 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (200 : S), (100 : S), (300 : S)⟩] [false, false, false, false]
#guard conformsVis column_weights (400 : S) (1000 : S) [⟨(0 : S), (0 : S), (400 : S), (1000 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (100 : S), (100 : S), (225 : S)⟩, ⟨(0 : S), (325 : S), (100 : S), (675 : S)⟩] [false, false, false, false]

/-- `flow_basic` — Flow layout wrapping items across multiple horizontal lines -/
def flow_basic : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (90 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_basic (250 : S) (400 : S) [⟨(0 : S), (0 : S), (250 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (90 : S), (40 : S)⟩, ⟨(90 : S), (40 : S), (70 : S), (40 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_basic (250 : S) (400 : S) [⟨(0 : S), (0 : S), (250 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (90 : S), (40 : S)⟩, ⟨(90 : S), (40 : S), (70 : S), (40 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_basic (500 : S) (800 : S) [⟨(0 : S), (0 : S), (500 : S), (800 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(180 : S), (0 : S), (90 : S), (40 : S)⟩, ⟨(270 : S), (0 : S), (70 : S), (40 : S)⟩] [false, false, false, false, false]

/-- `flow_child_background_border` — Flow layout items with backgrounds and border styling -/
def flow_child_background_border : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_child_background_border (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis flow_child_background_border (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis flow_child_background_border (500 : S) (360 : S) [⟨(0 : S), (0 : S), (500 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false, false]

/-- `flow_child_graphicslayer` — Flow items with graphicsLayer transformations -/
def flow_child_graphicslayer : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_child_graphicslayer (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis flow_child_graphicslayer (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis flow_child_graphicslayer (500 : S) (360 : S) [⟨(0 : S), (0 : S), (500 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `flow_child_padding` — Flow layout items with individual padding modifiers -/
def flow_child_padding : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.padding ⟨(8 : S), (8 : S), (8 : S), (8 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (70 : S)) none, Modifier.padding ⟨(12 : S), (12 : S), (12 : S), (12 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.padding ⟨(6 : S), (6 : S), (6 : S), (6 : S)⟩] HAlign.Center VAlign.Center []]

#guard conformsVis flow_child_padding (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (70 : S), (70 : S)⟩, ⟨(130 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis flow_child_padding (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (70 : S), (70 : S)⟩, ⟨(130 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis flow_child_padding (500 : S) (360 : S) [⟨(0 : S), (0 : S), (500 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (70 : S), (70 : S)⟩, ⟨(130 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false]

/-- `flow_child_zindex` — Flow items with explicit zIndex ordering -/
def flow_child_zindex : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_child_zindex (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis flow_child_zindex (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis flow_child_zindex (500 : S) (360 : S) [⟨(0 : S), (0 : S), (500 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `flow_item_alignment` — Flow layout with vertical center alignment for varying item heights -/
def flow_item_alignment : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Center (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_item_alignment (200 : S) (180 : S) [⟨(0 : S), (0 : S), (200 : S), (180 : S)⟩, ⟨(0 : S), (75 : S), (50 : S), (30 : S)⟩, ⟨(50 : S), (65 : S), (60 : S), (50 : S)⟩, ⟨(110 : S), (70 : S), (50 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis flow_item_alignment (200 : S) (180 : S) [⟨(0 : S), (0 : S), (200 : S), (180 : S)⟩, ⟨(0 : S), (75 : S), (50 : S), (30 : S)⟩, ⟨(50 : S), (65 : S), (60 : S), (50 : S)⟩, ⟨(110 : S), (70 : S), (50 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis flow_item_alignment (400 : S) (360 : S) [⟨(0 : S), (0 : S), (400 : S), (360 : S)⟩, ⟨(0 : S), (165 : S), (50 : S), (30 : S)⟩, ⟨(50 : S), (155 : S), (60 : S), (50 : S)⟩, ⟨(110 : S), (160 : S), (50 : S), (40 : S)⟩] [false, false, false, false]

/-- `flow_max_columns` — Flow layout with maxColumns constraint limiting items per row -/
def flow_max_columns : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2 2147483647 [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_max_columns (400 : S) (300 : S) [⟨(0 : S), (0 : S), (400 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (40 : S), (80 : S), (40 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_max_columns (400 : S) (300 : S) [⟨(0 : S), (0 : S), (400 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (40 : S), (80 : S), (40 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_max_columns (800 : S) (600 : S) [⟨(0 : S), (0 : S), (800 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (0 : S), (80 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (80 : S), (40 : S)⟩, ⟨(80 : S), (40 : S), (80 : S), (40 : S)⟩] [false, false, false, false, false]

/-- `flow_max_lines` — Flow layout with maxLines constraint limiting wrapped rows -/
def flow_max_lines : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2 [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_max_lines (260 : S) (400 : S) [⟨(0 : S), (0 : S), (260 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (40 : S), (100 : S), (40 : S)⟩, ⟨(200 : S), (40 : S), (100 : S), (40 : S)⟩] [false, false, false, false, false, true]
#guard conformsVis flow_max_lines (260 : S) (400 : S) [⟨(0 : S), (0 : S), (260 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (40 : S), (100 : S), (40 : S)⟩, ⟨(200 : S), (40 : S), (100 : S), (40 : S)⟩] [false, false, false, false, false, true]
#guard conformsVis flow_max_lines (520 : S) (800 : S) [⟨(0 : S), (0 : S), (520 : S), (800 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(300 : S), (0 : S), (100 : S), (40 : S)⟩, ⟨(400 : S), (0 : S), (100 : S), (40 : S)⟩] [false, false, false, false, false, false]

/-- `flow_padding_container` — Flow container with uniform padding around wrapping items -/
def flow_padding_container : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(16 : S), (16 : S), (16 : S), (16 : S)⟩] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_padding_container (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_padding_container (250 : S) (180 : S) [⟨(0 : S), (0 : S), (250 : S), (180 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_padding_container (500 : S) (360 : S) [⟨(0 : S), (0 : S), (500 : S), (360 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(180 : S), (0 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false]

/-- `flow_spacing` — Flow layout with row and column spacedBy spacing -/
def flow_spacing : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (12 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_spacing (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(112 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (100 : S), (50 : S)⟩, ⟨(112 : S), (50 : S), (100 : S), (50 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_spacing (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(112 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (100 : S), (50 : S)⟩, ⟨(112 : S), (50 : S), (100 : S), (50 : S)⟩] [false, false, false, false, false]
#guard conformsVis flow_spacing (600 : S) (600 : S) [⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(112 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(224 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(336 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false, false]

/-- `flow_spacing_wrap` — Flow layout with item spacing wrapping multiple elements -/
def flow_spacing_wrap : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis flow_spacing_wrap (180 : S) (200 : S) [⟨(0 : S), (0 : S), (180 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false, false, false, false]
#guard conformsVis flow_spacing_wrap (180 : S) (200 : S) [⟨(0 : S), (0 : S), (180 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (50 : S), (50 : S), (50 : S)⟩] [false, false, false, false, false, false]
#guard conformsVis flow_spacing_wrap (360 : S) (400 : S) [⟨(0 : S), (0 : S), (360 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(150 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(200 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false, false, false, false]

/-- `modifier_background` — Box with background modifier applying solid fill color -/
def modifier_background : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (140 : S)) none, Modifier.height (Dim.exact (90 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_background (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (140 : S), (90 : S)⟩] [false, false]
#guard conformsVis modifier_background (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (140 : S), (90 : S)⟩] [false, false]
#guard conformsVis modifier_background (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (140 : S), (90 : S)⟩] [false, false]

/-- `modifier_border` — Box with border modifier specifying stroke width, corner radius, and color -/
def modifier_border : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (160 : S)) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_border (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (160 : S), (100 : S)⟩] [false, false]
#guard conformsVis modifier_border (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (160 : S), (100 : S)⟩] [false, false]
#guard conformsVis modifier_border (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (160 : S), (100 : S)⟩] [false, false]

/-- `modifier_border_padding` — Box styling combining border and inner padding modifiers -/
def modifier_border_padding : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(16 : S), (16 : S), (16 : S), (16 : S)⟩] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_border_padding (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (168 : S), (168 : S)⟩] [false, false]
#guard conformsVis modifier_border_padding (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (168 : S), (168 : S)⟩] [false, false]
#guard conformsVis modifier_border_padding (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (368 : S), (368 : S)⟩] [false, false]

/-- `modifier_constraints_in` — Dimension constraints clamping sizes using widthIn and heightIn -/
def modifier_constraints_in : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) (some ⟨some (100 : S), some (200 : S)⟩), Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (300 : S)) (some ⟨some (50 : S), some (120 : S)⟩), Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_constraints_in (400 : S) (300 : S) [⟨(0 : S), (0 : S), (400 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (80 : S)⟩, ⟨(100 : S), (0 : S), (120 : S), (80 : S)⟩] [false, false, false]
#guard conformsVis modifier_constraints_in (400 : S) (300 : S) [⟨(0 : S), (0 : S), (400 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (80 : S)⟩, ⟨(100 : S), (0 : S), (120 : S), (80 : S)⟩] [false, false, false]
#guard conformsVis modifier_constraints_in (800 : S) (600 : S) [⟨(0 : S), (0 : S), (800 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (80 : S)⟩, ⟨(100 : S), (0 : S), (120 : S), (80 : S)⟩] [false, false, false]

/-- `modifier_fractional_fill` — Child components sized with fractional fillMaxWidth and fillMaxHeight -/
def modifier_fractional_fill : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.fill (some (3 / 4 : S))) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.fill (some (1 / 2 : S))) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_fractional_fill (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (100 : S), (200 : S), (80 : S)⟩] [false, false, false]
#guard conformsVis modifier_fractional_fill (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (100 : S), (200 : S), (80 : S)⟩] [false, false, false]
#guard conformsVis modifier_fractional_fill (800 : S) (800 : S) [⟨(0 : S), (0 : S), (800 : S), (800 : S)⟩, ⟨(0 : S), (0 : S), (600 : S), (100 : S)⟩, ⟨(0 : S), (100 : S), (400 : S), (80 : S)⟩] [false, false, false]

/-- `modifier_graphics_layer` — Component with graphicsLayer modifier specifying scale and alpha -/
def modifier_graphics_layer : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (120 : S)) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_graphics_layer (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (80 : S)⟩] [false, false]
#guard conformsVis modifier_graphics_layer (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (80 : S)⟩] [false, false]
#guard conformsVis modifier_graphics_layer (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (80 : S)⟩] [false, false]

/-- `modifier_graphicslayer_scale` — Child component modified with graphicsLayer scaling -/
def modifier_graphicslayer_scale : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_graphicslayer_scale (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false]
#guard conformsVis modifier_graphicslayer_scale (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false]
#guard conformsVis modifier_graphicslayer_scale (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false]

/-- `modifier_padding` — Padding modifier creating insets around child layout content -/
def modifier_padding : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(20 : S), (20 : S), (20 : S), (20 : S)⟩] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_padding (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (260 : S), (60 : S)⟩] [false, false]
#guard conformsVis modifier_padding (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (260 : S), (60 : S)⟩] [false, false]
#guard conformsVis modifier_padding (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (560 : S), (60 : S)⟩] [false, false]

/-- `modifier_padding_directional` — Component with asymmetric 4-sided directional padding -/
def modifier_padding_directional : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(10 : S), (30 : S), (20 : S), (40 : S)⟩] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_padding_directional (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (260 : S), (140 : S)⟩] [false, false]
#guard conformsVis modifier_padding_directional (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (260 : S), (140 : S)⟩] [false, false]
#guard conformsVis modifier_padding_directional (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (560 : S), (340 : S)⟩] [false, false]

/-- `modifier_padding_symmetric` — Component using 2-element symmetric padding [vertical, horizontal] -/
def modifier_padding_symmetric : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(15 : S), (15 : S), (30 : S), (30 : S)⟩] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_padding_symmetric (200 : S) (150 : S) [⟨(0 : S), (0 : S), (200 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (170 : S), (90 : S)⟩] [false, false]
#guard conformsVis modifier_padding_symmetric (200 : S) (150 : S) [⟨(0 : S), (0 : S), (200 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (170 : S), (90 : S)⟩] [false, false]
#guard conformsVis modifier_padding_symmetric (400 : S) (300 : S) [⟨(0 : S), (0 : S), (400 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (370 : S), (240 : S)⟩] [false, false]

/-- `modifier_size_array` — Box sized with [width, height] array syntax -/
def modifier_size_array : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (110 : S)) none, Modifier.height (Dim.exact (75 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_size_array (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (110 : S), (75 : S)⟩, ⟨(110 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis modifier_size_array (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (110 : S), (75 : S)⟩, ⟨(110 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis modifier_size_array (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (110 : S), (75 : S)⟩, ⟨(110 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `modifier_size_exact` — Component with explicit width and height modifier dimensions -/
def modifier_size_exact : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (140 : S)) none, Modifier.height (Dim.exact (70 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_size_exact (250 : S) (200 : S) [⟨(0 : S), (0 : S), (250 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (140 : S), (70 : S)⟩] [false, false]
#guard conformsVis modifier_size_exact (250 : S) (200 : S) [⟨(0 : S), (0 : S), (250 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (140 : S), (70 : S)⟩] [false, false]
#guard conformsVis modifier_size_exact (500 : S) (400 : S) [⟨(0 : S), (0 : S), (500 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (140 : S), (70 : S)⟩] [false, false]

/-- `modifier_zindex` — Box stack with zIndex modifiers controlling child evaluation order -/
def modifier_zindex : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (150 : S)) none, Modifier.height (Dim.exact (150 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (200 : S)) none, Modifier.height (Dim.exact (200 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_zindex (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (150 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩] [false, false, false, false]
#guard conformsVis modifier_zindex (300 : S) (300 : S) [⟨(0 : S), (0 : S), (300 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (150 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩] [false, false, false, false]
#guard conformsVis modifier_zindex (600 : S) (600 : S) [⟨(0 : S), (0 : S), (600 : S), (600 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (150 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩] [false, false, false, false]

/-- `modifier_zindex_reorder` — Overlapping elements ordered explicitly via zIndex modifier -/
def modifier_zindex_reorder : Node :=
  Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start VAlign.Top [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (120 : S)) none, Modifier.height (Dim.exact (120 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis modifier_zindex_reorder (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false, false, false]
#guard conformsVis modifier_zindex_reorder (200 : S) (200 : S) [⟨(0 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false, false, false]
#guard conformsVis modifier_zindex_reorder (400 : S) (400 : S) [⟨(0 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (80 : S)⟩] [false, false, false, false]

/-- `nested_complex_layout` — Hierarchical card UI with nested Columns, Rows, Padding, and Weights -/
def nested_complex_layout : Node :=
  Node.column [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(16 : S), (16 : S), (16 : S), (16 : S)⟩] HAlign.Start Arrangement.Start (0 : S) [Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.exact (80 : S)) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (64 : S)) none, Modifier.height (Dim.exact (64 : S)) none] HAlign.Center VAlign.Center [], Node.column [Modifier.width (Dim.weight (1 : S)) none, Modifier.padding ⟨(12 : S), (0 : S), (0 : S), (0 : S)⟩] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.exact (24 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (120 : S)) none, Modifier.height (Dim.exact (16 : S)) none] HAlign.Center VAlign.Center []]], Node.box [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.weight (1 : S)) none, Modifier.padding ⟨(0 : S), (0 : S), (12 : S), (0 : S)⟩] HAlign.Center VAlign.Center []]

#guard conformsVis nested_complex_layout (360 : S) (400 : S) [⟨(0 : S), (0 : S), (360 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (328 : S), (80 : S)⟩, ⟨(0 : S), (0 : S), (64 : S), (64 : S)⟩, ⟨(64 : S), (0 : S), (264 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (252 : S), (24 : S)⟩, ⟨(0 : S), (24 : S), (120 : S), (16 : S)⟩, ⟨(0 : S), (80 : S), (328 : S), (288 : S)⟩] [false, false, false, false, false, false, false]
#guard conformsVis nested_complex_layout (360 : S) (400 : S) [⟨(0 : S), (0 : S), (360 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (328 : S), (80 : S)⟩, ⟨(0 : S), (0 : S), (64 : S), (64 : S)⟩, ⟨(64 : S), (0 : S), (264 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (252 : S), (24 : S)⟩, ⟨(0 : S), (24 : S), (120 : S), (16 : S)⟩, ⟨(0 : S), (80 : S), (328 : S), (288 : S)⟩] [false, false, false, false, false, false, false]
#guard conformsVis nested_complex_layout (720 : S) (800 : S) [⟨(0 : S), (0 : S), (720 : S), (800 : S)⟩, ⟨(0 : S), (0 : S), (688 : S), (80 : S)⟩, ⟨(0 : S), (0 : S), (64 : S), (64 : S)⟩, ⟨(64 : S), (0 : S), (624 : S), (40 : S)⟩, ⟨(0 : S), (0 : S), (612 : S), (24 : S)⟩, ⟨(0 : S), (24 : S), (120 : S), (16 : S)⟩, ⟨(0 : S), (80 : S), (688 : S), (688 : S)⟩] [false, false, false, false, false, false, false]

/-- `resize_collapsible_column` — CollapsibleColumn responsive priority collapse and expansion under vertical constraints -/
def resize_collapsible_column : Node :=
  Node.collapsibleColumn [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center Arrangement.Center (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (70 : S)) none, Modifier.collapsiblePriority Orient.vertical (3 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (70 : S)) none, Modifier.collapsiblePriority Orient.vertical (2 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (70 : S)) none, Modifier.collapsiblePriority Orient.vertical (1 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis resize_collapsible_column (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(25 : S), (25 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (95 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (165 : S), (100 : S), (70 : S)⟩] [false, false, false, false]
#guard conformsVis resize_collapsible_column (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(25 : S), (25 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (95 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (165 : S), (100 : S), (70 : S)⟩] [false, false, false, false]
#guard conformsVis resize_collapsible_column (150 : S) (180 : S) [⟨(0 : S), (0 : S), (150 : S), (180 : S)⟩, ⟨(25 : S), (20 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (90 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (160 : S), (100 : S), (70 : S)⟩] [false, false, false, true]
#guard conformsVis resize_collapsible_column (150 : S) (90 : S) [⟨(0 : S), (0 : S), (150 : S), (90 : S)⟩, ⟨(25 : S), (10 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (80 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (80 : S), (100 : S), (70 : S)⟩] [false, false, true, true]
#guard conformsVis resize_collapsible_column (150 : S) (260 : S) [⟨(0 : S), (0 : S), (150 : S), (260 : S)⟩, ⟨(25 : S), (25 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (95 : S), (100 : S), (70 : S)⟩, ⟨(25 : S), (165 : S), (100 : S), (70 : S)⟩] [false, false, false, false]

/-- `resize_collapsible_row` — CollapsibleRow responsive priority-based element collapse and restoration during resize -/
def resize_collapsible_row : Node :=
  Node.collapsibleRow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Center VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (3 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (2 : S)] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.collapsiblePriority Orient.horizontal (1 : S)] HAlign.Center VAlign.Center []]

#guard conformsVis resize_collapsible_row (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(30 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(110 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(190 : S), (20 : S), (80 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis resize_collapsible_row (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(30 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(110 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(190 : S), (20 : S), (80 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis resize_collapsible_row (190 : S) (100 : S) [⟨(0 : S), (0 : S), (190 : S), (100 : S)⟩, ⟨(15 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(95 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(175 : S), (20 : S), (80 : S), (60 : S)⟩] [false, false, false, true]
#guard conformsVis resize_collapsible_row (100 : S) (100 : S) [⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(10 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(90 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(90 : S), (20 : S), (80 : S), (60 : S)⟩] [false, false, true, true]
#guard conformsVis resize_collapsible_row (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(30 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(110 : S), (20 : S), (80 : S), (60 : S)⟩, ⟨(190 : S), (20 : S), (80 : S), (60 : S)⟩] [false, false, false, false]

/-- `resize_flow_wrap` — Flow layout responsive line-wrapping reflow across dynamic viewport resizes -/
def resize_flow_wrap : Node :=
  Node.flow [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) 2147483647 2147483647 [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis resize_flow_wrap (350 : S) (200 : S) [⟨(0 : S), (0 : S), (350 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis resize_flow_wrap (350 : S) (200 : S) [⟨(0 : S), (0 : S), (350 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis resize_flow_wrap (230 : S) (200 : S) [⟨(0 : S), (0 : S), (230 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (100 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis resize_flow_wrap (110 : S) (200 : S) [⟨(0 : S), (0 : S), (110 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(0 : S), (50 : S), (100 : S), (50 : S)⟩, ⟨(0 : S), (100 : S), (100 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis resize_flow_wrap (350 : S) (200 : S) [⟨(0 : S), (0 : S), (350 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false]

/-- `resize_row_weights` — Row proportional weight distribution responding dynamically to width resize changes -/
def resize_row_weights : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.weight (1 : S)) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.weight (2 : S)) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center []]

#guard conformsVis resize_row_weights (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(100 : S), (0 : S), (200 : S), (100 : S)⟩] [false, false, false]
#guard conformsVis resize_row_weights (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (100 : S)⟩, ⟨(100 : S), (0 : S), (200 : S), (100 : S)⟩] [false, false, false]
#guard conformsVis resize_row_weights (600 : S) (100 : S) [⟨(0 : S), (0 : S), (600 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (100 : S)⟩, ⟨(200 : S), (0 : S), (400 : S), (100 : S)⟩] [false, false, false]
#guard conformsVis resize_row_weights (150 : S) (100 : S) [⟨(0 : S), (0 : S), (150 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (100 : S)⟩, ⟨(50 : S), (0 : S), (100 : S), (100 : S)⟩] [false, false, false]

/-- `row_align_center_mixed_heights` — Row with vertical center alignment and varying child heights -/
def row_align_center_mixed_heights : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_align_center_mixed_heights (300 : S) (150 : S) [⟨(0 : S), (0 : S), (300 : S), (150 : S)⟩, ⟨(0 : S), (60 : S), (50 : S), (30 : S)⟩, ⟨(50 : S), (25 : S), (60 : S), (100 : S)⟩, ⟨(110 : S), (45 : S), (40 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis row_align_center_mixed_heights (300 : S) (150 : S) [⟨(0 : S), (0 : S), (300 : S), (150 : S)⟩, ⟨(0 : S), (60 : S), (50 : S), (30 : S)⟩, ⟨(50 : S), (25 : S), (60 : S), (100 : S)⟩, ⟨(110 : S), (45 : S), (40 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis row_align_center_mixed_heights (600 : S) (300 : S) [⟨(0 : S), (0 : S), (600 : S), (300 : S)⟩, ⟨(0 : S), (135 : S), (50 : S), (30 : S)⟩, ⟨(50 : S), (100 : S), (60 : S), (100 : S)⟩, ⟨(110 : S), (120 : S), (40 : S), (60 : S)⟩] [false, false, false, false]

/-- `row_align_top_bottom` — Row container with bottom alignment positioning all children -/
def row_align_top_bottom : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Bottom (0 : S) [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (30 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (70 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_align_top_bottom (250 : S) (120 : S) [⟨(0 : S), (0 : S), (250 : S), (120 : S)⟩, ⟨(0 : S), (90 : S), (40 : S), (30 : S)⟩, ⟨(40 : S), (50 : S), (40 : S), (70 : S)⟩, ⟨(80 : S), (70 : S), (40 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis row_align_top_bottom (250 : S) (120 : S) [⟨(0 : S), (0 : S), (250 : S), (120 : S)⟩, ⟨(0 : S), (90 : S), (40 : S), (30 : S)⟩, ⟨(40 : S), (50 : S), (40 : S), (70 : S)⟩, ⟨(80 : S), (70 : S), (40 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis row_align_top_bottom (500 : S) (240 : S) [⟨(0 : S), (0 : S), (500 : S), (240 : S)⟩, ⟨(0 : S), (210 : S), (40 : S), (30 : S)⟩, ⟨(40 : S), (170 : S), (40 : S), (70 : S)⟩, ⟨(80 : S), (190 : S), (40 : S), (50 : S)⟩] [false, false, false, false]

/-- `row_alignment_center` — Row layout vertically centering children of different heights -/
def row_alignment_center : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Center (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (100 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (70 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_alignment_center (300 : S) (150 : S) [⟨(0 : S), (0 : S), (300 : S), (150 : S)⟩, ⟨(0 : S), (55 : S), (60 : S), (40 : S)⟩, ⟨(60 : S), (25 : S), (80 : S), (100 : S)⟩, ⟨(140 : S), (45 : S), (70 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis row_alignment_center (300 : S) (150 : S) [⟨(0 : S), (0 : S), (300 : S), (150 : S)⟩, ⟨(0 : S), (55 : S), (60 : S), (40 : S)⟩, ⟨(60 : S), (25 : S), (80 : S), (100 : S)⟩, ⟨(140 : S), (45 : S), (70 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis row_alignment_center (600 : S) (300 : S) [⟨(0 : S), (0 : S), (600 : S), (300 : S)⟩, ⟨(0 : S), (130 : S), (60 : S), (40 : S)⟩, ⟨(60 : S), (100 : S), (80 : S), (100 : S)⟩, ⟨(140 : S), (120 : S), (70 : S), (60 : S)⟩] [false, false, false, false]

/-- `row_alignment_end` — Row layout with horizontal end alignment and bottom vertical alignment -/
def row_alignment_end : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.End VAlign.Bottom (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (70 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_alignment_end (400 : S) (150 : S) [⟨(0 : S), (0 : S), (400 : S), (150 : S)⟩, ⟨(260 : S), (110 : S), (60 : S), (40 : S)⟩, ⟨(320 : S), (80 : S), (80 : S), (70 : S)⟩] [false, false, false]
#guard conformsVis row_alignment_end (400 : S) (150 : S) [⟨(0 : S), (0 : S), (400 : S), (150 : S)⟩, ⟨(260 : S), (110 : S), (60 : S), (40 : S)⟩, ⟨(320 : S), (80 : S), (80 : S), (70 : S)⟩] [false, false, false]
#guard conformsVis row_alignment_end (800 : S) (300 : S) [⟨(0 : S), (0 : S), (800 : S), (300 : S)⟩, ⟨(660 : S), (260 : S), (60 : S), (40 : S)⟩, ⟨(720 : S), (230 : S), (80 : S), (70 : S)⟩] [false, false, false]

/-- `row_alignment_space_around` — Row layout with spaceAround distribution -/
def row_alignment_space_around : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.SpaceAround VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_alignment_space_around (400 : S) (100 : S) [⟨(0 : S), (0 : S), (400 : S), (100 : S)⟩, ⟨(70 : S), (0 : S), (60 : S), (40 : S)⟩, ⟨(270 : S), (0 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis row_alignment_space_around (400 : S) (100 : S) [⟨(0 : S), (0 : S), (400 : S), (100 : S)⟩, ⟨(70 : S), (0 : S), (60 : S), (40 : S)⟩, ⟨(270 : S), (0 : S), (60 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis row_alignment_space_around (800 : S) (200 : S) [⟨(0 : S), (0 : S), (800 : S), (200 : S)⟩, ⟨(170 : S), (0 : S), (60 : S), (40 : S)⟩, ⟨(570 : S), (0 : S), (60 : S), (40 : S)⟩] [false, false, false]

/-- `row_alignment_space_between` — Row layout with spaceBetween distribution -/
def row_alignment_space_between : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.SpaceBetween VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_alignment_space_between (400 : S) (100 : S) [⟨(0 : S), (0 : S), (400 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(175 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(350 : S), (0 : S), (50 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis row_alignment_space_between (400 : S) (100 : S) [⟨(0 : S), (0 : S), (400 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(175 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(350 : S), (0 : S), (50 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis row_alignment_space_between (800 : S) (200 : S) [⟨(0 : S), (0 : S), (800 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(375 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(750 : S), (0 : S), (50 : S), (40 : S)⟩] [false, false, false, false]

/-- `row_alignment_space_evenly` — Row layout with spaceEvenly distribution -/
def row_alignment_space_evenly : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.SpaceEvenly VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_alignment_space_evenly (400 : S) (100 : S) [⟨(0 : S), (0 : S), (400 : S), (100 : S)⟩, ⟨(100 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(250 : S), (0 : S), (50 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis row_alignment_space_evenly (400 : S) (100 : S) [⟨(0 : S), (0 : S), (400 : S), (100 : S)⟩, ⟨(100 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(250 : S), (0 : S), (50 : S), (40 : S)⟩] [false, false, false]
#guard conformsVis row_alignment_space_evenly (800 : S) (200 : S) [⟨(0 : S), (0 : S), (800 : S), (200 : S)⟩, ⟨(23333 / 100 : S), (0 : S), (50 : S), (40 : S)⟩, ⟨(51667 / 100 : S), (0 : S), (50 : S), (40 : S)⟩] [false, false, false]

/-- `row_background_border` — Row container with background and border styling -/
def row_background_border : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_background_border (300 : S) (120 : S) [⟨(0 : S), (0 : S), (300 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false]
#guard conformsVis row_background_border (300 : S) (120 : S) [⟨(0 : S), (0 : S), (300 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false]
#guard conformsVis row_background_border (600 : S) (240 : S) [⟨(0 : S), (0 : S), (600 : S), (240 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false]

/-- `row_basic` — Row layout arranging fixed-size children horizontally with top alignment -/
def row_basic : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (120 : S)) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_basic (400 : S) (200 : S) [⟨(0 : S), (0 : S), (400 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(80 : S), (0 : S), (120 : S), (80 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis row_basic (400 : S) (200 : S) [⟨(0 : S), (0 : S), (400 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(80 : S), (0 : S), (120 : S), (80 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis row_basic (800 : S) (400 : S) [⟨(0 : S), (0 : S), (800 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(80 : S), (0 : S), (120 : S), (80 : S)⟩, ⟨(200 : S), (0 : S), (100 : S), (40 : S)⟩] [false, false, false, false]

/-- `row_child_graphicslayer` — Row children with graphicsLayer transformations -/
def row_child_graphicslayer : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_child_graphicslayer (300 : S) (120 : S) [⟨(0 : S), (0 : S), (300 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis row_child_graphicslayer (300 : S) (120 : S) [⟨(0 : S), (0 : S), (300 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis row_child_graphicslayer (600 : S) (240 : S) [⟨(0 : S), (0 : S), (600 : S), (240 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `row_child_zindex` — Row children with custom zIndex ordering -/
def row_child_zindex : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_child_zindex (300 : S) (120 : S) [⟨(0 : S), (0 : S), (300 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis row_child_zindex (300 : S) (120 : S) [⟨(0 : S), (0 : S), (300 : S), (120 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]
#guard conformsVis row_child_zindex (600 : S) (240 : S) [⟨(0 : S), (0 : S), (600 : S), (240 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (50 : S), (50 : S)⟩] [false, false, false]

/-- `row_fill_max_height` — Row with children filling parent maximum height -/
def row_fill_max_height : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.fill (some (1 / 2 : S))) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (80 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_fill_max_height (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (200 : S)⟩, ⟨(60 : S), (0 : S), (80 : S), (100 : S)⟩, ⟨(140 : S), (0 : S), (60 : S), (80 : S)⟩] [false, false, false, false]
#guard conformsVis row_fill_max_height (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (200 : S)⟩, ⟨(60 : S), (0 : S), (80 : S), (100 : S)⟩, ⟨(140 : S), (0 : S), (60 : S), (80 : S)⟩] [false, false, false, false]
#guard conformsVis row_fill_max_height (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (400 : S)⟩, ⟨(60 : S), (0 : S), (80 : S), (200 : S)⟩, ⟨(140 : S), (0 : S), (60 : S), (80 : S)⟩] [false, false, false, false]

/-- `row_multi_weights` — Row dividing available width according to 1:2:3 fractional weights -/
def row_multi_weights : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.weight (1 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.weight (2 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.weight (3 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_multi_weights (360 : S) (100 : S) [⟨(0 : S), (0 : S), (360 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (120 : S), (60 : S)⟩, ⟨(180 : S), (0 : S), (180 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis row_multi_weights (360 : S) (100 : S) [⟨(0 : S), (0 : S), (360 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(60 : S), (0 : S), (120 : S), (60 : S)⟩, ⟨(180 : S), (0 : S), (180 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis row_multi_weights (720 : S) (200 : S) [⟨(0 : S), (0 : S), (720 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (120 : S), (60 : S)⟩, ⟨(120 : S), (0 : S), (240 : S), (60 : S)⟩, ⟨(360 : S), (0 : S), (360 : S), (60 : S)⟩] [false, false, false, false]

/-- `row_nested_columns` — Row containing nested Columns with proportional weights -/
def row_nested_columns : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.column [Modifier.width (Dim.weight (1 : S)) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []], Node.column [Modifier.width (Dim.weight (2 : S)) none, Modifier.height (Dim.fill (some (1 : S))) none] HAlign.Start Arrangement.Start (0 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]]

#guard conformsVis row_nested_columns (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (40 : S), (40 : S)⟩, ⟨(100 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false, false, false]
#guard conformsVis row_nested_columns (300 : S) (200 : S) [⟨(0 : S), (0 : S), (300 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (40 : S), (40 : S)⟩, ⟨(100 : S), (0 : S), (200 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false, false, false]
#guard conformsVis row_nested_columns (600 : S) (400 : S) [⟨(0 : S), (0 : S), (600 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (200 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(0 : S), (40 : S), (40 : S), (40 : S)⟩, ⟨(200 : S), (0 : S), (400 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(0 : S), (60 : S), (60 : S), (60 : S)⟩] [false, false, false, false, false, false, false]

/-- `row_padding_all` — Row container and children using uniform padding modifiers -/
def row_padding_all : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none, Modifier.padding ⟨(16 : S), (16 : S), (16 : S), (16 : S)⟩] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (50 : S)) none, Modifier.padding ⟨(8 : S), (8 : S), (8 : S), (8 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (60 : S)) none, Modifier.padding ⟨(12 : S), (12 : S), (12 : S), (12 : S)⟩] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_padding_all (300 : S) (150 : S) [⟨(0 : S), (0 : S), (300 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(110 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis row_padding_all (300 : S) (150 : S) [⟨(0 : S), (0 : S), (300 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(110 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false, false]
#guard conformsVis row_padding_all (600 : S) (300 : S) [⟨(0 : S), (0 : S), (600 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (50 : S), (50 : S)⟩, ⟨(50 : S), (0 : S), (60 : S), (60 : S)⟩, ⟨(110 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false, false]

/-- `row_spaced_by` — Row layout with spacedBy modifier distributing inter-item gaps -/
def row_spaced_by : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (20 : S) [Node.box [Modifier.width (Dim.exact (60 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_spaced_by (400 : S) (150 : S) [⟨(0 : S), (0 : S), (400 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (50 : S)⟩, ⟨(80 : S), (0 : S), (80 : S), (50 : S)⟩, ⟨(180 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis row_spaced_by (400 : S) (150 : S) [⟨(0 : S), (0 : S), (400 : S), (150 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (50 : S)⟩, ⟨(80 : S), (0 : S), (80 : S), (50 : S)⟩, ⟨(180 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis row_spaced_by (800 : S) (300 : S) [⟨(0 : S), (0 : S), (800 : S), (300 : S)⟩, ⟨(0 : S), (0 : S), (60 : S), (50 : S)⟩, ⟨(80 : S), (0 : S), (80 : S), (50 : S)⟩, ⟨(180 : S), (0 : S), (100 : S), (50 : S)⟩] [false, false, false, false]

/-- `row_weights` — Row layout distributing remaining space using weighted modifiers -/
def row_weights : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (100 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.weight (1 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center [], Node.box [Modifier.width (Dim.weight (2 : S)) none, Modifier.height (Dim.exact (50 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis row_weights (400 : S) (200 : S) [⟨(0 : S), (0 : S), (400 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(200 : S), (0 : S), (200 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis row_weights (400 : S) (200 : S) [⟨(0 : S), (0 : S), (400 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(200 : S), (0 : S), (200 : S), (50 : S)⟩] [false, false, false, false]
#guard conformsVis row_weights (800 : S) (400 : S) [⟨(0 : S), (0 : S), (800 : S), (400 : S)⟩, ⟨(0 : S), (0 : S), (100 : S), (50 : S)⟩, ⟨(100 : S), (0 : S), (23333 / 100 : S), (50 : S)⟩, ⟨(33333 / 100 : S), (0 : S), (46667 / 100 : S), (50 : S)⟩] [false, false, false, false]

/-- `spacer_fixed_sizes` — Row using explicit fixed-width spacers between boxes -/
def spacer_fixed_sizes : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.leaf [Modifier.width (Dim.exact (30 : S)) none, Modifier.height (Dim.exact (1 : S)) none] ⟨0, 0⟩, Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center [], Node.leaf [Modifier.width (Dim.exact (50 : S)) none, Modifier.height (Dim.exact (1 : S)) none] ⟨0, 0⟩, Node.box [Modifier.width (Dim.exact (40 : S)) none, Modifier.height (Dim.exact (40 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis spacer_fixed_sizes (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(40 : S), (0 : S), (30 : S), (1 : S)⟩, ⟨(70 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(110 : S), (0 : S), (50 : S), (1 : S)⟩, ⟨(160 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false, false, false, false]
#guard conformsVis spacer_fixed_sizes (300 : S) (100 : S) [⟨(0 : S), (0 : S), (300 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(40 : S), (0 : S), (30 : S), (1 : S)⟩, ⟨(70 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(110 : S), (0 : S), (50 : S), (1 : S)⟩, ⟨(160 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false, false, false, false]
#guard conformsVis spacer_fixed_sizes (600 : S) (200 : S) [⟨(0 : S), (0 : S), (600 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(40 : S), (0 : S), (30 : S), (1 : S)⟩, ⟨(70 : S), (0 : S), (40 : S), (40 : S)⟩, ⟨(110 : S), (0 : S), (50 : S), (1 : S)⟩, ⟨(160 : S), (0 : S), (40 : S), (40 : S)⟩] [false, false, false, false, false, false]

/-- `spacer_weighted` — Row with content separated by an expanding weighted spacer -/
def spacer_weighted : Node :=
  Node.row [Modifier.width (Dim.fill (some (1 : S))) none, Modifier.height (Dim.fill (some (1 : S))) none] Arrangement.Start VAlign.Top (0 : S) [Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center [], Node.leaf [Modifier.width (Dim.weight (1 : S)) none] ⟨0, 0⟩, Node.box [Modifier.width (Dim.exact (80 : S)) none, Modifier.height (Dim.exact (60 : S)) none] HAlign.Center VAlign.Center []]

#guard conformsVis spacer_weighted (500 : S) (100 : S) [⟨(0 : S), (0 : S), (500 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(80 : S), (0 : S), (340 : S), (0 : S)⟩, ⟨(420 : S), (0 : S), (80 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis spacer_weighted (500 : S) (100 : S) [⟨(0 : S), (0 : S), (500 : S), (100 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(80 : S), (0 : S), (340 : S), (0 : S)⟩, ⟨(420 : S), (0 : S), (80 : S), (60 : S)⟩] [false, false, false, false]
#guard conformsVis spacer_weighted (1000 : S) (200 : S) [⟨(0 : S), (0 : S), (1000 : S), (200 : S)⟩, ⟨(0 : S), (0 : S), (80 : S), (60 : S)⟩, ⟨(80 : S), (0 : S), (840 : S), (0 : S)⟩, ⟨(920 : S), (0 : S), (80 : S), (60 : S)⟩] [false, false, false, false]

end RemoteCompose.Conformance

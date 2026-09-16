#!/usr/bin/env python3
# Copyright 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Generate RemoteComposeLayout/Conformance.lean from the gold layout suite.

Reads the language-neutral test documents in ``../tests/layout/`` together with
the bounds the Android engine recorded in ``../gold/layout/``, and emits Lean
``#guard`` checks asserting that the specification reproduces those bounds.

Tests exercising features the specification deliberately leaves out (scrolling,
collapsing, text measurement, flow, state layouts, ...) are skipped, and the
reason is reported.

Usage:
    python3 tools/gen_conformance.py [--report]
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from fractions import Fraction

HERE = os.path.dirname(os.path.abspath(__file__))
LEAN_ROOT = os.path.dirname(HERE)
SUITE = os.path.dirname(LEAN_ROOT)
TESTS = os.path.join(SUITE, "tests", "layout")
GOLD = os.path.join(SUITE, "gold", "layout")
OUT = os.path.join(LEAN_ROOT, "RemoteComposeLayout", "Conformance.lean")

# Component kinds the specification models.
SUPPORTED_TYPES = {
    "box", "row", "column", "spacer",
    "collapsibleRow", "collapsibleColumn", "flow",
}

# `RemoteComposeJsonParser.isVerticalContainer`: only the column family stacks
# vertically, and a bare `weight` or `collapsiblePriority` is resolved against
# the enclosing container's axis at authoring time.
VERTICAL_TYPES = {"column", "collapsibleColumn"}

# `Integer.MAX_VALUE`, the wire default for the flow's two limits.
INT_MAX = 2147483647

# Modifiers that influence layout and that the specification models.
LAYOUT_MODIFIERS = {
    "size", "width", "height",
    "fillMaxSize", "fillMaxWidth", "fillMaxHeight",
    "padding", "weight", "horizontalWeight", "verticalWeight",
    "widthIn", "heightIn",
    "collapsiblePriority", "spacedBy",
}

# Modifiers that are purely decorative: they never move or resize a component,
# so a document using them is still in scope.
IGNORED_MODIFIERS = {"background", "border", "zIndex", "graphicsLayer", "onClick", "clip"}

ALIGN_H = {"start": "HAlign.Start", "center": "HAlign.Center", "end": "HAlign.End"}
ALIGN_V = {"top": "VAlign.Top", "center": "VAlign.Center", "bottom": "VAlign.Bottom"}
ARRANGE = {
    "start": "Arrangement.Start",
    "center": "Arrangement.Center",
    "end": "Arrangement.End",
    "spaceBetween": "Arrangement.SpaceBetween",
    "spaceEvenly": "Arrangement.SpaceEvenly",
    "spaceAround": "Arrangement.SpaceAround",
}


class Unsupported(Exception):
    """Raised when a document uses a feature outside the specification."""


def q(value) -> str:
    """Render a JSON number as an exact Lean rational literal."""
    f = Fraction(str(value))
    if f.denominator == 1:
        return f"({f.numerator} : S)"
    return f"({f.numerator} / {f.denominator} : S)"


def parse_modifiers(mods, vertical):
    """Translate a JSON modifier list into a Lean `Modifiers` chain literal.

    Order is preserved, because ``computeModifierDefinedWidth`` stops at the
    first width modifier while ``updatePadding`` sums the whole chain.

    ``vertical`` is ``RemoteComposeJsonParser.isParentVertical()``: true when the
    enclosing container is a ``column`` or a ``collapsibleColumn``. The wire
    format has no axis-agnostic weight or priority, so ``DefaultModifierParsers``
    resolves both against that axis at authoring time.

    Returns the chain literal together with the container properties that reach
    the component through a modifier rather than through a JSON key.
    """
    chain = []          # list of ['padding'|'width'|'height', payload]
    priorities = []     # collapsible priority entries, order-insensitive
    extra = {}          # container properties carried by a modifier
    width_in = height_in = None

    def set_axis(axis, payload):
        """`RecordingModifier.setWidthModifier` / `setHeightModifier`.

        A component carries at most one width and one height modifier: a later
        write finds the existing element and updates it *in place*, keeping its
        position in the chain. So `Modifier.width(100).weight(1)` records a
        single WEIGHT modifier, and the `100` is lost.
        """
        for entry in chain:
            if entry[0] == axis:
                entry[1] = payload
                return
        chain.append([axis, payload])

    def dim(kind, v):
        if kind == "exact":
            return f"Dim.exact {q(v)}"
        if kind == "weight":
            return f"Dim.weight {q(v)}"
        return f"Dim.fill (some {q(v)})"

    for m in mods or []:
        if not isinstance(m, dict):
            raise Unsupported("non-object modifier")
        for key, val in m.items():
            if key in IGNORED_MODIFIERS:
                continue
            if key not in LAYOUT_MODIFIERS:
                raise Unsupported(f"modifier '{key}'")
            if key == "size":
                if isinstance(val, list):
                    set_axis("width", dim("exact", val[0]))
                    set_axis("height", dim("exact", val[1]))
                else:
                    set_axis("width", dim("exact", val))
                    set_axis("height", dim("exact", val))
            elif key == "width":
                set_axis("width", dim("exact", val))
            elif key == "height":
                set_axis("height", dim("exact", val))
            elif key == "fillMaxSize":
                set_axis("width", dim("fill", val))
                set_axis("height", dim("fill", val))
            elif key == "fillMaxWidth":
                set_axis("width", dim("fill", val))
            elif key == "fillMaxHeight":
                set_axis("height", dim("fill", val))
            elif key in ("weight", "horizontalWeight", "verticalWeight"):
                # `DefaultModifierParsers` resolves a bare `weight` against the
                # enclosing container's axis — vertical inside the column
                # family, horizontal everywhere else, flow included. The
                # explicit keys override that choice.
                if key == "weight":
                    axis = "height" if vertical else "width"
                else:
                    axis = "height" if key == "verticalWeight" else "width"
                set_axis(axis, dim("weight", val))
            elif key == "collapsiblePriority":
                # Three spellings: a bare number, `[orientation, priority]`, or
                # an object that may name the orientation. The default
                # orientation is again the enclosing container's axis.
                orient = "Orient.vertical" if vertical else "Orient.horizontal"
                if isinstance(val, dict):
                    if "orientation" in val:
                        orient = ("Orient.vertical"
                                  if str(val["orientation"]).lower() == "vertical"
                                  else "Orient.horizontal")
                    prio = val.get("priority", 0)
                elif isinstance(val, list):
                    orient = "Orient.vertical" if val[0] == 1 else "Orient.horizontal"
                    prio = val[1]
                else:
                    prio = val
                priorities.append(f"Modifier.collapsiblePriority {orient} {q(prio)}")
            elif key == "spacedBy":
                # `RecordingModifier.spacedBy` sets the container's own spacing;
                # it is not a modifier the measure pass ever reads.
                extra["spacedBy"] = val
            elif key == "padding":
                if isinstance(val, dict):
                    pad = {
                        "left": val.get("start", 0), "right": val.get("end", 0),
                        "top": val.get("top", 0), "bottom": val.get("bottom", 0),
                    }
                elif isinstance(val, list) and len(val) == 2:
                    pad = {"left": val[0], "right": val[0],
                           "top": val[1], "bottom": val[1]}
                elif isinstance(val, list) and len(val) == 4:
                    pad = {"left": val[0], "top": val[1],
                           "right": val[2], "bottom": val[3]}
                elif isinstance(val, list):
                    raise Unsupported("padding array of unexpected arity")
                else:
                    pad = {"left": val, "right": val, "top": val, "bottom": val}
                chain.append([
                    "padding",
                    f"⟨{q(pad['left'])}, {q(pad['right'])}, "
                    f"{q(pad['top'])}, {q(pad['bottom'])}⟩",
                ])
            elif key == "widthIn":
                width_in = val
            elif key == "heightIn":
                height_in = val

    def dim_in(c):
        if c is None:
            return "none"
        lo = f"some {q(c['min'])}" if "min" in c else "none"
        hi = f"some {q(c['max'])}" if "max" in c else "none"
        return f"(some ⟨{lo}, {hi}⟩)"

    # `widthIn` / `heightIn` are fields of the size modifier, so attach them to
    # the first one on that axis; a chain with no size modifier on that axis
    # cannot express the constraint.
    entries = []
    attached_w = attached_h = False
    for kind, payload in chain:
        if kind == "padding":
            entries.append(f"Modifier.padding {payload}")
        elif kind == "width":
            c = dim_in(width_in) if not attached_w else "none"
            attached_w = True
            entries.append(f"Modifier.width ({payload}) {c}")
        else:
            c = dim_in(height_in) if not attached_h else "none"
            attached_h = True
            entries.append(f"Modifier.height ({payload}) {c}")

    if width_in is not None and not attached_w:
        raise Unsupported("widthIn without a width modifier")
    if height_in is not None and not attached_h:
        raise Unsupported("heightIn without a height modifier")

    # A priority is read by name, never by position, so where it sits in the
    # chain cannot matter.
    entries.extend(priorities)

    return "[" + ", ".join(entries) + "]", extra


def convert(node, vertical=False):
    """Translate a JSON component into a Lean `Node` literal.

    ``vertical`` says whether the *enclosing* container stacks vertically, which
    is what an axis-relative modifier is resolved against.
    """
    if not isinstance(node, dict):
        raise Unsupported("malformed component")
    kind = node.get("type")
    if kind not in SUPPORTED_TYPES:
        raise Unsupported(f"component type '{kind}'")

    allowed = {
        "type", "modifiers", "children", "name", "description",
        "verticalAlignment", "horizontalAlignment",
        "horizontalArrangement", "verticalArrangement", "spacedBy",
    }
    if kind == "flow":
        allowed |= {"maxColumns", "maxLines"}
    for key in node:
        if key not in allowed:
            raise Unsupported(f"component property '{key}'")

    mods, extra = parse_modifiers(node.get("modifiers"), vertical)
    # A `spacedBy` *key* on the component is inert: no component parser reads
    # one, so only the modifier of that name ever reaches the container. Two
    # gold files record exactly that — a document that asks for spacing this way
    # is laid out with none.
    spaced = extra.get("spacedBy", 0)
    inner = kind in VERTICAL_TYPES
    kids = [convert(c, inner) for c in node.get("children", [])]
    body = ", ".join(kids)

    def horizontal(default):
        """`RemoteComposeJsonParser.getHorizontalAlign`: the alignment key wins,
        the arrangement key is the fallback, and both name the same wire slot."""
        return node.get("horizontalAlignment",
                        node.get("horizontalArrangement", default))

    def vertical_of(default):
        return node.get("verticalAlignment", node.get("verticalArrangement", default))

    def align_h(default):
        v = ALIGN_H.get(horizontal(default))
        if v is None:
            raise Unsupported(f"{kind} alignment")
        return v

    def align_v(default):
        v = ALIGN_V.get(vertical_of(default))
        if v is None:
            raise Unsupported(f"{kind} alignment")
        return v

    def arrange_h(default):
        v = ARRANGE.get(horizontal(default))
        if v is None:
            raise Unsupported(f"{kind} alignment")
        return v

    def arrange_v(default):
        v = ARRANGE.get(vertical_of(default))
        if v is None:
            raise Unsupported(f"{kind} alignment")
        return v

    if kind == "spacer":
        if node.get("children"):
            raise Unsupported("spacer with children")
        return f"Node.leaf {mods} ⟨0, 0⟩"

    if kind == "box":
        # `DefaultComponentParsers` gives a childless box center/center and a
        # populated one start/top.
        d_h, d_v = ("start", "top") if node.get("children") else ("center", "center")
        return f"Node.box {mods} {align_h(d_h)} {align_v(d_v)} [{body}]"

    if kind == "row":
        # A row's main axis is horizontal, so the horizontal key denotes the
        # arrangement rather than an alignment.
        return f"Node.row {mods} {arrange_h('start')} {align_v('top')} {q(spaced)} [{body}]"

    if kind == "column":
        return f"Node.column {mods} {align_h('start')} {arrange_v('start')} {q(spaced)} [{body}]"

    if kind == "collapsibleRow":
        # Both axes default to `center` for the collapsible family.
        return (f"Node.collapsibleRow {mods} {arrange_h('center')} {align_v('center')} "
                f"{q(spaced)} [{body}]")

    if kind == "collapsibleColumn":
        return (f"Node.collapsibleColumn {mods} {align_h('center')} {arrange_v('center')} "
                f"{q(spaced)} [{body}]")

    # flow
    max_items = node.get("maxColumns", INT_MAX)
    max_lines = node.get("maxLines", INT_MAX)
    return (f"Node.flow {mods} {arrange_h('start')} {align_v('top')} {q(spaced)} "
            f"{max_items} {max_lines} [{body}]")


def expected_nodes(tree):
    """Recorded bounds and visibility in pre-order, dropping the
    `RootLayoutComponent` wrapper.

    The gold generator emits nodes in reverse pre-order, and wraps the document
    root in a `RootLayoutComponent` that models the viewport rather than a
    component of the document.
    """
    nodes = list(reversed(tree))
    if not nodes or nodes[0].get("kind") != "RootLayoutComponent":
        raise Unsupported("unexpected gold tree shape")
    body = nodes[1:]
    rects = [
        f"⟨{q(n['x'])}, {q(n['y'])}, {q(n['width'])}, {q(n['height'])}⟩"
        for n in body
    ]
    gone = ["true" if n.get("isGone") else "false" for n in body]
    return rects, gone


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", action="store_true", help="list skipped tests")
    args = ap.parse_args()

    cases, skipped = [], []

    for name in sorted(os.listdir(TESTS)):
        if not name.endswith(".json"):
            continue
        stem = name[:-len(".json")]
        gold_path = os.path.join(GOLD, stem + ".gold.json")
        if not os.path.exists(gold_path):
            skipped.append((stem, "no gold file"))
            continue

        test = json.load(open(os.path.join(TESTS, name)))
        gold = json.load(open(gold_path))
        try:
            root = test.get("document", {}).get("root")
            if root is None:
                raise Unsupported("no root component")
            node = convert(root)

            params = gold.get("parameters", {})
            if params.get("density", 1) != 1:
                raise Unsupported("non-unit density")

            # Layout expectations live in the v2 `checks` array: each `tree` check names a
            # timeline step, and the step carries the viewport it was measured at. The implicit
            # `initial` step is measured at the document's own dimensions.
            viewport = {"initial": (params["width"], params["height"])}
            order = ["initial"]
            for step in gold.get("timeline", []) or []:
                if step.get("kind") == "resize":
                    viewport[step["id"]] = (step["width"], step["height"])
                    order.append(step["id"])

            trees = {
                c["at"]: c["expect"]
                for c in gold.get("checks", []) or []
                if c.get("probe") == "tree"
            }
            unknown = set(trees) - set(viewport)
            if unknown:
                raise Unsupported("tree check at unknown step(s): " + ", ".join(sorted(unknown)))

            steps = [viewport[step_id] + (trees[step_id],) for step_id in order if step_id in trees]
            if not steps:
                raise Unsupported("no tree checks")

            checks = [(w, h) + expected_nodes(t) for (w, h, t) in steps]
        except (Unsupported, KeyError, TypeError) as exc:
            skipped.append((stem, str(exc)))
            continue

        cases.append((stem, gold.get("description", ""), node, checks))

    ident = lambda s: s if s[0].isalpha() else "t_" + s

    out = [
        "/-",
        "Copyright 2026 The Android Open Source Project",
        "",
        'Licensed under the Apache License, Version 2.0 (the "License");',
        "you may not use this file except in compliance with the License.",
        "You may obtain a copy of the License at",
        "",
        "     http://www.apache.org/licenses/LICENSE-2.0",
        "",
        "Unless required by applicable law or agreed to in writing, software",
        'distributed under the License is distributed on an "AS IS" BASIS,',
        "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
        "See the License for the specific language governing permissions and",
        "limitations under the License.",
        "-/",
        "import RemoteComposeLayout.Conform",
        "",
        "/-!",
        "# Conformance of the specification against the Android engine",
        "",
        "**This file is generated. Do not edit.**",
        "Regenerate with `python3 tools/gen_conformance.py`.",
        "",
        "Each check below replays a document from",
        "`compose/remote/specification/conformance/tests/layout/` through the",
        "specification and compares the result against the bounds the Android engine",
        "recorded in `compose/remote/specification/conformance/gold/layout/`, at every",
        "viewport size the gold file covers.",
        "",
        f"{len(cases)} documents are in scope, covering "
        f"{sum(len(c[3]) for c in cases)} viewport configurations.",
        "-/",
        "",
        "namespace RemoteCompose.Conformance",
        "",
    ]

    for stem, desc, node, checks in cases:
        out.append(f"/-- `{stem}`" + (f" — {desc}" if desc else "") + " -/")
        out.append(f"def {ident(stem)} : Node :=")
        out.append(f"  {node}")
        out.append("")
        for (w, h, rects, gone) in checks:
            body = ", ".join(rects)
            vis = ", ".join(gone)
            out.append(
                f"#guard conformsVis {ident(stem)} {q(w)} {q(h)} [{body}] [{vis}]")
        out.append("")

    out.append("end RemoteCompose.Conformance")

    with open(OUT, "w") as fh:
        fh.write("\n".join(out) + "\n")

    total_checks = sum(len(c[3]) for c in cases)
    print(f"wrote {OUT}")
    print(f"  {len(cases)} documents in scope, {total_checks} viewport checks")
    print(f"  {len(skipped)} documents skipped")
    if args.report:
        by_reason = {}
        for stem, reason in skipped:
            by_reason.setdefault(reason, []).append(stem)
        for reason in sorted(by_reason, key=lambda r: -len(by_reason[r])):
            names = by_reason[reason]
            print(f"    {len(names):3d}  {reason}")


if __name__ == "__main__":
    sys.exit(main())

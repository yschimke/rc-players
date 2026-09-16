# Implementing a RemoteCompose Player Conformance Runner

**Audience:** you are writing a RemoteCompose player — in C++, Kotlin, Swift, Rust, anything —
and you want to know how conformant it is.

This document tells you what to build. It is a practical guide; the normative schema is
[CONFORMANCE_FORMAT.md](CONFORMANCE_FORMAT.md), and the wire format each operation uses is in
the `AREA-*.md` specifications one directory up.

---

## 1. What you are being asked to produce

One JSON file. That is the entire deliverable.

```
     gold/**/*.gold.json                 your player          results/<you>/conformance-results.json
   (252 golds, this directory)   ───▶   + ~300 lines of     ───▶   (the audit)
                                          driver code                     │
                                                                          ▼
                                                              node generate-audit-report.mjs
                                                                          │
                                                                          ▼
                                                      results/<you>/conformance-audit.html
```

The report generators in this directory are player-agnostic and have zero npm dependencies.
They will render *any* player's results file. You never touch them.

### The one rule that makes this cheap

> [!IMPORTANT]
> **Each gold carries its own compiled document in `document_base64`. You play that.**
>
> You do not need to parse the authoring JSON in `tests/`, you do not need a converter, and
> you do not need to reproduce the authoring toolchain. If your player can load a
> RemoteCompose binary, you already have the hard part.

### Where everything lives

The division is by *ownership*, and it is worth understanding before you start:

| | Owned by this directory (AndroidX) | Owned by your player's repository |
|---|---|---|
| The gold corpus `gold/`, `tests/` | ✅ | ✗ |
| The tooling that generates the golds | ✅ | ✗ |
| The report generators / viewers | ✅ | ✗ |
| This specification | ✅ | ✗ |
| **The runner that drives your player** | ✗ | ✅ |
| `results/<your-player>/conformance-results.json` | it is published here | your runner produces it |

> [!IMPORTANT]
> **This directory is player-agnostic and stays that way.** There may be any number of
> third-party players; the specification must not know about any of them. Nothing here imports
> a player, and no player-specific code should ever be added here.

The corollary is that **the runner is yours**. It is the one piece that must know how to
construct your engine, so it belongs with your engine. That is a few hundred lines: §2 is the
loop, §3–§7 are the details.

> [!WARNING]
> **Read `gold/` in place. Do not vendor a copy into your repository.**
>
> This directory is the only version-controlled home of the corpus. A copy in a player repo
> drifts the first time a gold is regenerated, and the drift is silent: your runner keeps
> passing against a stale expectation.

Take the corpus directory as *configuration* — a command-line flag, an environment
variable, or a machine-local config file that is not committed. Any of those is fine;
what matters is that it is a setting rather than a checked-in duplicate.

### Where to write your results

Pick a short name for your player and write to:

```
<spec>/results/<your-player>/conformance-results.json
```

The directory is namespaced because the spec tree collects results from every implementation
being measured. An unqualified `conformance-results.json` at the top level would mean
"whichever player ran last", which is exactly the ambiguity this avoids. Use the same string
for the directory and for the `player.name` field in the file — it is the label the reports
render.

Writing into the spec tree (rather than your own repository) keeps the published numbers next
to the corpus they were measured against, and lets the report's relative
`tests/<category>/<name>.json` hyperlinks resolve.

> [!TIP]
> Existing players publish their runners in their own repositories. If you can find one
> for a language close to yours, reading it alongside this document is faster than
> reading either alone.

---

## 2. The loop

```
for each gold in gold/**/*.gold.json:
    if gold.format_version != 2: report SKIP and continue

    install text-metrics policy from gold.harness.text_metrics   # BEFORE constructing
    doc = load(base64_decode(gold.document_base64))
    install event recorders (§6)                                 # BEFORE first paint
    doc.applyDataOperations(ctx)
    install branch instrumentation                               # AFTER the data pass

    checksByStep = group(gold.checks, by = check.at)

    for each step in gold.timeline:
        execute(step)                                            # §4
        for each check in checksByStep[step.id]:
            evaluate(check) and record the diff or the observed value

    for each step id in checksByStep that never executed:
        fail those checks as STEP_NOT_RUN

emit results JSON (§7)
```

That is the whole runner. Everything else in this document is about the two calls that look
innocuous — `execute(step)` and `evaluate(check)`.

---

## 3. A complete worked example

`gold/layout/column_basic.gold.json`, with the base64 and the authoring-side keys elided:

```jsonc
{
  "format_version": 2,
  "name": "column_basic",
  "category": "layout",
  "profile": "core",
  "harness":    { "text_metrics": "ahem" },
  "parameters": { "width": 300, "height": 400, "density": 1, "tolerance": 0.5 },
  "document_base64": "…296 bytes…",

  "timeline": [
    { "id": "initial",  "kind": "paint",  "frames": 2, "animation_enabled": false },
    { "id": "resize_0", "kind": "resize", "width": 300, "height": 400, "frames": 2 },
    { "id": "resize_1", "kind": "resize", "width": 600, "height": 800, "frames": 2 }
  ],

  "checks": [
    { "at": "initial",  "probe": "tree", "tolerance": 0.5, "expect": [ … ] },
    { "at": "resize_0", "probe": "tree", "tolerance": 0.5, "expect": [ … ] },
    { "at": "resize_1", "probe": "tree", "tolerance": 0.5, "expect": [
        { "id": -7, "kind": "BoxLayout",          "depth": 2, "x": 0, "y": 140,
          "width": 80,  "height": 50,  "isGone": false, "visibility": "VISIBLE" },
        { "id": -6, "kind": "BoxLayout",          "depth": 2, "x": 0, "y": 60,
          "width": 150, "height": 80,  "isGone": false, "visibility": "VISIBLE" },
        { "id": -5, "kind": "BoxLayout",          "depth": 2, "x": 0, "y": 0,
          "width": 100, "height": 60,  "isGone": false, "visibility": "VISIBLE" },
        { "id": -3, "kind": "ColumnLayout",       "depth": 1, "x": 0, "y": 0,
          "width": 600, "height": 800, "isGone": false, "visibility": "VISIBLE" },
        { "id": -2, "kind": "RootLayoutComponent","depth": 0, "x": 0, "y": 0,
          "width": 600, "height": 800, "isGone": false, "visibility": "VISIBLE" }
      ] }
  ]
}
```

To pass it you: load the document, paint twice with animation off, snapshot the tree, resize
to 300×400 and paint twice, snapshot, resize to 600×800 and paint twice, snapshot — then
compare each snapshot against its `expect` with a tolerance of 0.5px.

Note what this gold is actually testing: the column's fixed-size children keep their
positions (`y: 0, 60, 140`) when the viewport doubles, while the container itself grows to
600×800. That is only visible because the tree is captured *per resize step*.

---

## 4. Executing timeline steps

Full table in [CONFORMANCE_FORMAT.md §3](CONFORMANCE_FORMAT.md). The two you must get right:

**`paint`** — measure (unless `measure: false`), then paint, `frames` times, advancing
animation time by 1/60 s per frame. Honour `animation_enabled` and `animation_time_seconds`.

**`resize`** — set the viewport, **write the new width and height into float slots 5 and 6**,
then measure + paint `frames` times. The slot write is not optional: documents read their own
size from there.

A gold runs only if you support *every* step kind it uses:

| implement | golds fully runnable |
|---|---|
| `paint` | 27.8% |
| **+ `resize`** | **87.7%** |
| + `frame_sequence`, `settle` | 91.3% |
| + `click`, `touch_down`, `touch_drag`, `touch_up` | 94.8% |
| + `advance_time`, `trigger`, `clock_snapshot`, `theme`, `longPress`, `doubleClick` | 100% |

---

## 5. Evaluating checks

A check is `{ at, probe, channel?, target?, expect, tolerance? }`. Resolve the tolerance as:

```
tolerance = check.tolerance ?? gold.parameters.tolerance ?? 0
```

Implement probes in this order; the percentages are how much of the corpus each one unlocks,
measured, cumulatively:

| implement | golds covered | what your player must be able to do |
|---|---|---|
| `tree` | **69.3%** | walk the laid-out component hierarchy |
| + `float` | 78.9% | read a float slot by id **and by variable name** |
| + `ops:count` | 83.7% | walk the decoded operation list |
| + `raster` | 86.5% | render to a bitmap; compare with the antialiasing-aware pixel count (§10) |
| + `particles` | 89.6% | read the particle array out of the particles op |
| + `draw_log`, `ops:present`, `text`, `records:components`, `trace:handled`, `color` | 94.0% | §6 for the first and last |
| + the remaining 20 channels | 100% | mostly decoded-operation field reads |

> [!TIP]
> **`tree` + `float` + `ops:count`, driven by `paint` + `resize`, is ~84% of the corpus.**
> That is the sensible first milestone. Everything past it is long tail.

### The tree probe, in detail

It is 69% of the corpus, so it is worth stating exactly.

Each node is `{ id, kind, depth, x, y, width, height, isGone, visibility }`, plus `scroll_x` /
`scroll_y` when non-zero. Three things to know:

- **Nodes are ordered by component id ascending.** Ids are descending negatives in creation
  order, so the array is in reverse creation order: a node appears *after* all its descendants.
- **To rebuild parentage:** for the node at index `i` with depth `d`, walk backwards while
  `depth > d`; its direct children sit at exactly `d + 1`.
- **`x`/`y` are parent-relative and reflect only the layout manager's assignment.**
  Modifier-induced translation — padding, offset — is applied at paint time and is *not* in
  `x`/`y`. A padded child reports `x: 0`; the padding shows up as the parent being larger.

> [!WARNING]
> Do not compare the geometry of nodes with `isGone: true`. A gone node's bounds are
> undefined and the two engines legitimately disagree about them.

### Strings, and your own naming

Compare class names with leading underscores stripped — `_BoxLayout` → `BoxLayout` — so your
internal name mangling does not leak into the corpus. If your player names its layout classes
something else entirely, map them; `kind` is a portable vocabulary, not your type name.

---

## 6. The part that is genuinely specific to your player

Four observations are **transient events**. Once the paint returns there is nothing left to
query, so your player has to surface them as they happen — either by exposing a hook, or by
wrapping its own internals the way the reference runner does.

| channel | what has to be captured |
|---|---|
| `records:glyph_runs` | every glyph anchor, mapped into device space by tracking the CTM through save / restore / translate / rotate / scale |
| `records:anchor_runs` | the resolved, anchor-adjusted baseline origin of each text run |
| `draw_log:commands` | the ordered stream of draw calls, mapped onto the portable vocabulary |
| `trace:branches` | whether each conditional's body executed, and how many child ops ran |

Only `trace:host_actions` corresponds to an ordinary API (an action callback).

`draw_log:commands` is matched as an **ordered subsequence**, not an exact list, because the
recorded stream also carries the root layout's own matrix scaffolding. Map your method names
onto the shared vocabulary (`applyPaint`→`paint`, `matrixSave`→`save`, `drawBitmap`→
`drawBitmapScaled`, …) before comparing.

These four are all in the long tail past 90%. Skip them until the rest works.

---

## 7. Emitting the results

```jsonc
{
  "player":  { "name": "my-player", "version": "0.1" },
  "corpus":  { "gold_count": 252 },
  "summary": { "passed": 0, "failed": 0, "errored": 0, "suspicious": 0,
               "checks_total": 0, "checks_failed": 0 },
  "results": [
    {
      "name": "column_basic",
      "category": "layout",
      "description": "…",
      "profile": "core",
      "status": "PASS",            // PASS | FAIL | ERROR | SKIP | SUSPICIOUS
      "raw_status": "PASS",        // status before the suspicious exclusion
      "suspicious": false,
      "suspicious_reason": null,
      "checks_total": 3,
      "checks_failed": 0,
      "duration_ms": 1,
      "diffs": [],
      "observed": { "resize_1": { "tree": [ … ] } }
    }
  ]
}
```

A diff entry:

```jsonc
{ "at": "resize_0", "probe": "tree", "target": "-5",
  "property": "width", "expected": 202, "actual": 250, "tolerance": 0.5 }
```

`observed` is keyed by step id, then by `probe` or `probe:channel`. Scalar probes nest one level
further, by target:

```jsonc
"observed": {
  "t_0.25": { "float": { "sine_wave": 0.0043 } },      // scalar: [at][probe][target]
  "initial": { "records:semantics": [ { "role": 1 } ] } // structured: [at]["probe:target"]
}
```

> [!IMPORTANT]
> It is nominally optional, but populate it. 250 of the 252 golds assert *values*, not pixels,
> and `observed` is the only thing the HTML report has to show for them — the expected side comes
> from the corpus, the actual side comes from here. A player that omits it renders every
> non-drawing subsystem as a blank page.

It also lets the report explain a failure without re-running anything.

### Scoring

A gold tagged `suspicious` pins reference behaviour that is disputed. Report it with
`status: "SUSPICIOUS"`, keep the real verdict in `raw_status`, and leave it out of the pass
rate. Same for `SKIP`. The denominator is `total − suspicious − skipped`.

---

## 8. Six ways to get a wrong answer

Every one of these was made by the reference implementation first.

> [!CAUTION]
> **Evaluate each check at its step, not at the end of the run.** Most probes read mutable
> state. A colour check bound to a light-theme step must be evaluated before the dark-theme
> step overwrites the slot — otherwise every light-theme assertion quietly compares against
> dark values and you will not notice, because they still *look* like real assertions.

> [!CAUTION]
> **Never let an unimplemented probe pass.** Report `PROBE_NOT_IMPLEMENTED` as a *failure*.
> A runner that skips what it cannot do produces a beautiful, meaningless number — which is
> the exact failure this corpus exists to prevent.

> [!CAUTION]
> **Never let a check bound to an unexecuted step vanish.** Report `STEP_NOT_RUN`. A silently
> dropped check is indistinguishable from a passing one.

> [!CAUTION]
> **Install branch instrumentation *after* the data-operations pass.** That pass walks
> containers unconditionally, so instrumenting earlier charges every branch — taken or not —
> with one execution, and every branch looks taken.

> [!CAUTION]
> **Honour `measure: false` and `repaint: false`.** A measure pass descends into every
> container, including conditional branches that paint would skip, which inflates branch
> counts. The post-gesture repaint clears the transient touch-coordinate slots (13 = x,
> 14 = y) that the interactivity checks read.

> [!CAUTION]
> **`clock_snapshot` requires rebuilding the document** against the frozen clock. The clock is
> consulted at construction, so repainting an existing document will not re-derive the values
> and you will silently compare the same instant three times.

---

## 9. Text: the font, and the metrics

### 9.1 Render with the corpus font — this is required

The corpus ships its own typeface at `fonts/Ahem.ttf`, next to `gold/`. **Register it and draw
every text run with it**, whatever family the document names. This is setup your runner does once,
before it creates any drawing surface.

```js
// node-canvas
import { registerFont } from 'canvas';
registerFont(join(specDir, 'fonts', 'Ahem.ttf'), { family: 'Ahem' });
```

```java
// Android
Typeface ahem = Typeface.createFromFile(new File(specDir, "fonts/Ahem.ttf"));
```

Ahem's glyphs are solid 1em squares, so the pixels a text run produces are a function of where it
was placed and nothing else. Skip this and every text-bearing gold will fail on its raster check
for a reason that has nothing to do with your player: your platform's default font is not the one
the reference drew with, and no tolerance setting distinguishes "wrong glyph shapes" from "wrong
position". Concretely, `text_on_path_glyph_placement` scores **RMSE 9.44** against a Roboto
reference and **0.06** against an Ahem one.

Substituting the family wholesale is intended. `sans-serif`, `serif` and `monospace` all collapse
to Ahem; the corpus asserts placement, not font selection.

> [!TIP]
> Verify the registration rather than assuming it: measure `"ABC"` at 24px. Ahem gives exactly
> `72.0`. Anything else means the font did not load and your text results are meaningless.

### 9.2 `gold.harness.text_metrics` — a declared policy, not a hint

This selects how the gold's **geometry** was measured. It says nothing about the typeface, which
is always Ahem.

| value | meaning |
|---|---|
| `ahem` | The closed-form model: one em of advance per glyph, ascent `0.8em`, descent `0.2em`, greedy word wrap on character counts. Implement this arithmetic directly — no font engine involved — or your tree will not match. |
| `native` | Measured with your real text stack against the Ahem face. |

> [!WARNING]
> Do not apply the closed-form model everywhere. It agrees with real Ahem on a single-line run,
> but not on line breaking, and a gold that declares `native` was measured by a real layout
> engine. Honour the per-gold declaration.

---

## 10. Comparing rasters — the metric, in full

A `raster` check's `tolerance` is a **count of differing pixels**, not an RMSE. The count is
antialiasing-aware: a pixel counts only when it differs perceptibly *and* the difference cannot
be explained as an antialiased edge. Default tolerance is **16**.

> [!IMPORTANT]
> You compute your own verdict, so this algorithm is part of the contract. An approximate
> reimplementation produces scores that are not comparable with anyone else's. Port it exactly.

It is the antialiased-pixel detector from [`mapbox/pixelmatch`](https://github.com/mapbox/pixelmatch)
(Vyšniauskas' method), applied after compositing both images onto white.

```js
const rgb2y = (r, g, b) => r * 0.29889531 + g * 0.58662247 + b * 0.11448223;
const rgb2i = (r, g, b) => r * 0.59597799 - g * 0.2741761  - b * 0.32180189;
const rgb2q = (r, g, b) => r * 0.21147017 - g * 0.52261711 + b * 0.31114694;

// Signed brightness difference when yOnly, else squared perceptual distance.
function colorDelta(a, b, ia, ib, yOnly) {
    const y = rgb2y(a[ia], a[ia+1], a[ia+2]) - rgb2y(b[ib], b[ib+1], b[ib+2]);
    if (yOnly) return y;
    const i = rgb2i(a[ia], a[ia+1], a[ia+2]) - rgb2i(b[ib], b[ib+1], b[ib+2]);
    const q = rgb2q(a[ia], a[ia+1], a[ia+2]) - rgb2q(b[ib], b[ib+1], b[ib+2]);
    const d = 0.5053*y*y + 0.299*i*i + 0.1957*q*q;
    return y > 0 ? -d : d;
}

// More than two identical neighbours => the pixel sits in a flat region, not on an edge.
function hasManySiblings(img, x1, y1, w, h) {
    const x0 = Math.max(x1-1, 0), y0 = Math.max(y1-1, 0);
    const x2 = Math.min(x1+1, w-1), y2 = Math.min(y1+1, h-1);
    const pos = (y1*w + x1) * 3;
    let zeroes = (x1===x0 || x1===x2 || y1===y0 || y1===y2) ? 1 : 0;
    for (let x = x0; x <= x2; x++) for (let y = y0; y <= y2; y++) {
        if (x === x1 && y === y1) continue;
        const p = (y*w + x) * 3;
        if (img[pos]===img[p] && img[pos+1]===img[p+1] && img[pos+2]===img[p+2]) zeroes++;
        if (zeroes > 2) return true;
    }
    return false;
}

function antialiased(img, x1, y1, w, h, other) {
    const x0 = Math.max(x1-1, 0), y0 = Math.max(y1-1, 0);
    const x2 = Math.min(x1+1, w-1), y2 = Math.min(y1+1, h-1);
    const pos = (y1*w + x1) * 3;
    let zeroes = (x1===x0 || x1===x2 || y1===y0 || y1===y2) ? 1 : 0;
    let min = 0, max = 0, minX = 0, minY = 0, maxX = 0, maxY = 0;
    for (let x = x0; x <= x2; x++) for (let y = y0; y <= y2; y++) {
        if (x === x1 && y === y1) continue;
        const delta = colorDelta(img, img, pos, (y*w + x) * 3, true);
        if (delta === 0) { if (++zeroes > 2) return false; }
        else if (delta < min) { min = delta; minX = x; minY = y; }
        else if (delta > max) { max = delta; maxX = x; maxY = y; }
    }
    if (min === 0 || max === 0) return false;
    return (hasManySiblings(img, minX, minY, w, h) && hasManySiblings(other, minX, minY, w, h))
        || (hasManySiblings(img, maxX, maxY, w, h) && hasManySiblings(other, maxX, maxY, w, h));
}

// Composite RGBA onto white so a transparent pixel and a white one are not counted as differing.
function flattenRgb(rgba, n) {
    const out = new Uint8ClampedArray(n * 3);
    for (let i = 0, o = 0; o < out.length; i += 4, o += 3) {
        const a = rgba[i+3] / 255;
        out[o]   = 255 + (rgba[i]   - 255) * a;
        out[o+1] = 255 + (rgba[i+1] - 255) * a;
        out[o+2] = 255 + (rgba[i+2] - 255) * a;
    }
    return out;
}

function countDiff(actualRgba, goldRgba, width, height) {
    const n = width * height;
    const A = flattenRgb(actualRgba, n), B = flattenRgb(goldRgba, n);
    const maxDelta = 35215 * 0.1 * 0.1;          // pixelmatch default threshold of 0.1
    let differing = 0;
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
        const pos = (y*width + x) * 3;
        if (Math.abs(colorDelta(A, B, pos, pos, false)) <= maxDelta) continue;
        if (antialiased(A, x, y, width, height, B)) continue;
        if (antialiased(B, x, y, width, height, A)) continue;
        differing++;
    }
    return differing;
}
```

Compare at the **reference image's own dimensions**. A step that resizes the viewport produces a
frame of a different shape; using the document's declared size instead silently crops both images
to the original box, and a cropped comparison scores zero while proving nothing. If the two images
differ in size, that is itself a failure — do not scale either one.

### Self-check

Comparing any gold's reference image against itself must yield **0**. Comparing
`gold/layout/column_background_border.gold.json`'s image against a copy shifted one pixel right
must yield a number in the hundreds, not single digits — if it does not, `antialiased()` is
excusing real differences and your whole raster channel is permissive.

### Why not RMSE

RMSE averages over the whole canvas, so its verdict depends on how much empty space surrounds the
error. Measured across all 622 raster comparisons in this corpus, an RMSE gate at 8 raised **zero**
false alarms but hid **34** real differences — including `conditional_skip_api_gate`, which renders
a filled square as a stroked one in the wrong place, omits a line and adds a spurious crescent, and
passed at RMSE 5.44 because 93% of its 400×400 canvas is white in both images.

Report RMSE if you like — the reference runner does — but do not gate on it.

---

## 11. Validating your runner

Your runner can be wrong in ways that look like the *player* being wrong. Three cheap checks:

1. **Count your checks.** The corpus declares 867 checks across 252 golds. If your runner
   evaluates fewer, it is dropping some — find out which before believing any score.
2. **Force a failure.** Perturb one expected value and confirm the corresponding check fails.
   A check that cannot fail is not a check.
3. **Diff against a known-good corpus run.** Any `results/<player>/conformance-results.json`
   already published here came from a working runner. You should not match its verdicts —
   different players have different bugs — but the *set of checks evaluated* should be
   identical.

### Scope control while developing

```bash
--core-only                    # skip the 8 extended-profile golds; 244 remain
--category layout              # one subsystem
<spec-dir>/gold/layout/foo.gold.json   # one gold, by path
```

---

## 12. What "conforming" means

The corpus is 252 golds over 18 subsystems: 244 `core`, 8 `extended`, 3 currently tagged
suspicious and excluded.

- **Core profile** — every conforming player is expected to pass these.
- **Extended profile** — optional or experimental operations. Report separately; do not count
  them against a core score.

A partial implementation is welcome and expected. A runner that implements `tree`, `float` and
`raster`, reports `PROBE_NOT_IMPLEMENTED` for everything else, and scores 60% has told you
something true. One that scores 100% by skipping what it cannot do has told you nothing.

---

## See also

- [CONFORMANCE_FORMAT.md](CONFORMANCE_FORMAT.md) — normative schema: every step kind, every
  probe channel, the result format.
- [AREA-17-CONFORMANCE-DEBUGGING.md](../AREA-17-CONFORMANCE-DEBUGGING.md) — the normative
  conformance and diagnostics specification, including hand-written wire-level and RPN test
  vectors. Complementary to this corpus-driven suite.
- [AREA-01-WIRE-PROTOCOL.md](../AREA-01-WIRE-PROTOCOL.md) and the other `AREA-*.md` documents —
  what you need to *play* a document in the first place.

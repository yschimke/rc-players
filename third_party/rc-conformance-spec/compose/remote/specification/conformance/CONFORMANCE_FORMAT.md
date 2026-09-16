# RemoteCompose Conformance Format

**Version 2.** This is the contract between the conformance corpus and a player. A player that
can (a) load a RemoteCompose binary document, (b) drive it through the steps in §3, and
(c) report the observations in §4 can produce a conformance audit without knowing anything
about how the corpus was authored.

`conformance-runner-v2.mjs` is the reference implementation of this document, not the
definition of it. Where the two disagree, this document is wrong and should be fixed.

---

## 1. Design constraints

Three rules shaped the format. They are worth stating because they explain choices that
otherwise look arbitrary.

> [!IMPORTANT]
> **A player never parses the authoring JSON.** The gold file carries the compiled document as
> `document_base64`; the player plays *that*. Nothing is re-derived from `tests/**`. A player
> therefore needs a binary loader and nothing else — no converter, no authoring-format parser.
> This is why the gold file, not the test file, is the player-facing API.

> [!IMPORTANT]
> **Every check must be evaluable.** If the reference engine knows something a player cannot
> observe, it does *not* become a check. It is recorded in `unasserted` (§2.8) with the reason.
> An assertion that cannot fail is worse than no assertion.

> [!IMPORTANT]
> **Checks are evaluated at the step they are bound to, not at the end.** Most probes read
> mutable engine state. A colour check bound to a light-theme step must run before the
> dark-theme step overwrites the slot. A check bound to a step that never executed is a
> failure (`STEP_NOT_RUN`), never a silent pass.

---

## 2. Gold file schema

One JSON file per test, under `gold/<category>/<name>.gold.json`.

```jsonc
{
  "format_version": 2,
  "name": "box_padding_all",
  "category": "layout",
  "description": "…",
  "profile": "core",                  // "core" | "extended"
  "harness": { "text_metrics": "ahem" },
  "parameters": { "tolerance": 0.5 }, // default tolerance + authoring params
  "document_base64": "…",             // the compiled document — play this
  "timeline": [ /* §3 */ ],
  "checks":   [ /* §4 */ ],
  "unasserted": [ /* §2.8 */ ],       // optional
  "tags": ["suspicious"],             // optional, §2.9
  "suspicious_reason": "…"            // optional, §2.9
}
```

### 2.1 `profile`

| value | meaning |
|---|---|
| `core` | Every conforming player is expected to pass. 244 of 252 tests. |
| `extended` | Exercises optional or experimental operations. Reported separately; not counted against a core-profile score. |

This is independent of the `PROFILES` header inside the document, which declares what the
*document* requires. A player may, but need not, refuse to play a document whose declared
profile it does not implement.

### 2.2 The test font

Every gold is rendered in **Ahem**, shipped with the corpus at [`fonts/Ahem.ttf`](fonts/Ahem.ttf).
A player that draws text **must** render with it, substituting it for whatever family a document
names.

Ahem's glyphs are solid 1em squares (1000 units/em, ascent 800, descent 200, one em of advance per
glyph), so a text run's pixels depend only on where the run was placed. That is what these tests
assert. Glyph shape is not: two engines disagreeing on the outline of a lowercase `g` is not a
conformance failure, but in a pixel comparison it is indistinguishable from one. Before the font
was pinned, every text-bearing gold carried a permanent RMSE floor from that mismatch alone — on
`text_on_path_glyph_placement`, pinning it took the error from **9.44 to 0.06**.

The font is public domain (CC0). Registering it is a one-line setup step in every toolkit the
corpus has been run against.

### 2.3 `harness.text_metrics`

Which measurement model produced the gold's **geometry**. It says nothing about the rendered
typeface — that is always Ahem (§2.2).

| value | meaning |
|---|---|
| `ahem` | The closed-form model: one em of advance per glyph, ascent `0.8em`, descent `0.2em`, greedy word wrap on character counts. Reproducible by arithmetic alone, with no font engine. A player replaying such a gold **must** use the same model or its tree will not match. |
| `native` | Measured by the platform's real text stack against the Ahem face. |

The two agree on advance and bounds for a single-line run — that is Ahem's design — but **not on
line breaking**: a real layout engine does not break where greedy character counting does. This is
the remaining source of disagreement on the multi-line `core_text_*` golds, whose trees are `ahem`
while their rasters are `native`.

> [!WARNING]
> A gold whose tree is `ahem` and whose raster is `native` is asserting two things that were
> measured differently. Where those two disagree, the gold is internally inconsistent and the
> failure is in the corpus, not the player.

### 2.4 `parameters.tolerance`

The default numeric tolerance for every check in the file. Individual checks may override it
(§4.1).

### 2.5 `parameters.max_raster_pixels`

The number of differing pixels a `raster` check tolerates. **Default 16.** It is stamped onto
each `raster` check as its `tolerance`, so a player never needs to read this field — it exists
so a gold can ask for a different bar than the default.

The count is **antialiasing-aware**: a pixel counts only if it differs perceptibly *and* the
difference cannot be explained as an antialiased edge. A score of zero therefore means "the same
picture, modulo edge rendering".

Because every player computes its own verdict, the algorithm is part of the contract, not an
implementation detail. It is Vyšniauskas' antialiased-pixel detector as implemented in
`mapbox/pixelmatch`, applied after compositing both images onto **white**:

1. Flatten RGBA onto white: `out = 255 + (c - 255) * (a / 255)`, per channel.
2. For each pixel, compute the YIQ distance
   `0.5053·y² + 0.299·i² + 0.1957·q²` where `y`, `i`, `q` are the per-channel differences under
   `Y = .29889531r + .58662247g + .11448223b`, `I = .59597799r − .2741761g − .32180189b`,
   `Q = .21147017r − .52261711g + .31114694b`.
3. Skip the pixel if that distance is `<= 35215 × 0.1²`.
4. Skip it if it is *antialiasing* in **either** image: it is the brightest or darkest of its
   8-neighbourhood by `Y`, has at most two identical neighbours, and that extreme neighbour has
   more than two identical neighbours in **both** images.
5. Count whatever is left.

A full worked reference implementation is in `PLAYER_IMPLEMENTATION_GUIDE.md` §10.

> [!NOTE]
> This replaced an RMSE tolerance of 8. RMSE averages over the whole canvas, so its verdict
> depended on how much empty space surrounded the error, and it did not rank wrongness: a
> blatantly misplaced element on a large canvas scored *lower* than an invisible one-pixel stroke
> inset on a small one. Measured across all 622 raster comparisons in this corpus, RMSE at 8
> raised **zero** false alarms but hid **34** real differences.
>
> 16 is not a judgement call: the antialiasing-aware score is sharply bimodal here — 402
> comparisons land on exactly 0, only 2 fall in the 1-9 band, 218 sit at 10 or above — and every
> verdict is identical anywhere between 16 and 32.

> [!TIP]
> RMSE is still worth reporting as a diagnostic; it is just no longer the gate. A raster failure
> remains a prompt to look at the diff rather than a measure of how wrong a player is.

### 2.6 `document_base64`

Standard base64 of the compiled RemoteCompose document. Header layout, for reference:

```
[13-byte preamble][entryCount:i32 @13][entry…]     entry = [tag:u16][payloadLen:u16][payload]
```
First entry at offset 17. Tags: `0x0005` width, `0x0006` height, `0x000E` PROFILES,
`0x0C09` contentDescription.

### 2.7 Coordinate conventions

Layout node `x`/`y` are **parent-relative** and reflect only what the layout manager assigned.
Modifier-induced translation (padding, offset) is applied at paint time and is **not** in
`x`/`y`. A padded child reports `x: 0`; the padding shows up as the parent being larger.

### 2.8 `unasserted`

Facts the reference knows that no player can observe. Present so the gap is visible rather
than silently dropped.

```jsonc
"unasserted": [
  { "key": "expected_id_mappings", "reason": "no observable channel: LOOM id remapping is internal" }
]
```

A player ignores this array. It exists for corpus review: each entry is either a genuine
limit of the engine's observability or a missing accessor worth adding.

#### Reserved key: `raster@<at>`

Keys of the form `raster@<capture id>` are written by the reference raster generator and mean
*the document produced a fully transparent frame at that capture, so there is no reference
image to compare against*:

```jsonc
{ "key": "raster@initial",
  "reason": "the document draws nothing at this step, so there is no reference image to compare against" }
```

This distinguishes **"the document draws nothing"** from **"nobody looked"**. Without it, a
missing `raster` check is ambiguous, and report generators end up showing an empty canvas
panel for tests such as `matrix_expression_composition` that are purely computational.

The raster generator owns *only* the `raster@`-prefixed entries: it replaces those and merges
every other entry through untouched, so hand-authored observability notes survive a
regeneration.

### 2.9 `tags` / `suspicious_reason`

`tags: ["suspicious"]` marks a test whose *reference output* is disputed — the gold pins
behaviour believed to be a reference bug. Such tests are excluded from the pass rate and
reported separately.

> [!NOTE]
> `suspicious` is an **exclusion marker**, not a subsystem label. Subsystem tagging is a
> separate concern; a test can be tagged `statelayout` *and* excluded, or tagged and not
> excluded.

### 2.10 `known_divergence` / `known_unimplemented`

Free text, informational. `known_divergence` records that the gold pins behaviour both
engines get wrong in the same way — the expected value is what they *do*, and the field says
what they *should* do. `known_unimplemented` records an operation no engine renders.

Neither affects evaluation. They exist so that a passing test which encodes a known bug says
so out loud instead of looking like a clean result.

### 2.11 Nothing else

A gold file contains **only** the keys documented above. There is no authoring-side annex, no
`expected_*` vocabulary, and nothing for a player to ignore — what you can read is exactly what
is asserted.

This was not always true. Until the corpus moved to `format_version: 2`, golds carried a
parallel set of some forty `expected_*` keys that a separate migration script compiled into
`timeline` + `checks`. Two representations of the same expectation meant they could disagree,
and the older one was the one nobody read. The Java generator now derives `timeline` + `checks`
directly and writes nothing else:

```
Java generator ──derives──▶ timeline + checks ──▶ player
                             (this contract)
```

Anything the reference knows but cannot express as a check is declared in
[`unasserted`](#26-unasserted), so a gap is visible in the file rather than implied by its
absence.


---

## 3. Timeline

An ordered list of steps. Each has a unique `id` (checks bind to it) and a `kind`. Steps not
understood by a player must be treated as an error, not skipped — silently skipping a step
turns its checks into false passes.

Common optional keys: `label` (human-readable), `frames` (how many paint iterations).

| kind | keys | semantics |
|---|---|---|
| `paint` | `frames` (default 2), `measure` (default `true`), `animation_enabled`, `animation_time_seconds` | Measure (unless `measure: false`) then paint, `frames` times, advancing animation time by 1/60 s per frame. |
| `resize` | `width`, `height`, `frames` (default 2) | Set the viewport, write width/height into float slots 5 and 6, then measure + paint `frames` times. |
| `trigger` | `trigger: {type: "resize"\|"click", …}` | A one-shot stimulus followed by a single measure/paint. |
| `frame_sequence` | `total_frames`, `capture: [n…]`, `inclusive` (default `true`), `base_time_millis` | Paint frames `0…total_frames`, writing wall-clock into `currentTime`, elapsed seconds into slot 30 and delta into slot 31. Captures a snapshot at each listed frame under the id `frame_<n>`. |
| `theme` | `theme` | Paint with an explicit theme code: light `-3`, dark `-2`, normal `-1`. |
| `clock_snapshot` | `clock: {…}` | Rebuild the whole document against a frozen clock, then paint. The clock is consulted at construction, so repainting an existing document would not re-derive the values. |
| `settle` | — | Terminal marker. Runs nothing; state is whatever the previous step left. Exists so a check can bind to "after everything". |
| `advance_time` | `advance_millis` | Advance animation time, then paint / measure / paint. |
| `click`, `longPress`, `doubleClick`, `touch_down`, `touch_drag`, `touch_up` | `x`, `y`, `dx`, `dy` (touch_up fling), `advance_millis`, `repaint` (default `true`) | Dispatch the gesture; record whether it was consumed (readable as `trace:handled`). Unless `repaint: false`, follow with paint / measure / paint. |

> [!WARNING]
> Two step flags exist because a naive "always measure, always repaint" harness produces wrong
> answers:
> - **`measure: false`** — a measure pass descends into *every* container, including conditional
>   branches that `paint` would skip, which inflates branch-execution counts.
> - **`repaint: false`** — the post-gesture repaint clears the transient touch-coordinate slots
>   (13 = x, 14 = y) that the interactivity checks read.

---

## 4. Checks

```jsonc
{ "at": "resize_0", "probe": "tree", "expect": [ … ], "tolerance": 0.5 }
{ "at": "initial", "probe": "float", "target": "res_nested_if", "expect": 1, "tolerance": 0.001 }
{ "at": "initial", "probe": "ops", "channel": "counts", "expect": { "DrawRect": 1 } }
```

| field | meaning |
|---|---|
| `at` | The `timeline` step id this check is evaluated immediately after. |
| `probe` | What family of state to read (table below). |
| `channel` | Sub-selector within a probe. Omitted for single-channel probes. |
| `target` | Which slot/variable/component the probe addresses. A numeric id, or a **variable name** for `float` / `int` / `color` / `text` (expression and interactivity tests address by name). |
| `expect` | The reference value. |
| `tolerance` | Optional per-check override. |

### 4.1 Tolerance resolution

```
tolerance = check.tolerance  ??  gold.parameters.tolerance  ??  0
```

Exact match when neither is present. Per-check tolerance exists so one noisy channel in a file
does not force the whole file to be loose.

### 4.2 Probe vocabulary

Class names are compared with any leading underscores stripped (`_BoxLayout` → `BoxLayout`), so
a player's internal name mangling does not leak into the corpus.

#### State probes

| probe / channel | `target` | `expect` |
|---|---|---|
| `float` | slot id or variable name | number |
| `int` | slot id or variable name | integer |
| `color` | slot id or variable name | ARGB as an unsigned 32-bit integer (`4294901760` = opaque red) |
| `text` | text id | string |
| `matrix` | matrix id | array of 9 or 16 numbers, row-major |
| `float_array:dynamic` | list id | array of numbers — the *computed* list |
| `float_array:data` | list id | array of numbers — the *stored* list |

> [!NOTE]
> Matrices live in the object registry, not the float table. The float slot sharing a matrix's
> id holds a dirty tick, not a value — reading it is a common and silent mistake.

#### Structural probes

| probe / channel | `expect` |
|---|---|
| `tree` | Array of layout nodes (§4.3) |
| `raster` | A `data:image/png;base64,…` reference image, compared at the reference image's own dimensions. The check's `tolerance` **is** the maximum number of non-antialiased differing pixels (§2.5). |
| `particles` | `[[x, y, vx, vy], …]` for one simulation frame |

#### Operation-census probes

| channel | `expect` |
|---|---|
| `ops:count` | total operation count (number) |
| `ops:present` | `["DrawRect", …]` — all must be present |
| `ops:absent` | `["DrawCircle", …]` — none may be present |
| `ops:counts` | `{"DrawRect": 1, "DrawCircle": 0}` — exact per-class counts |
| `ops:component_count` | number of layout components |
| `ops:distinct_ids` | `true` — every component id is unique |
| `ops:total_glyphs` | total glyphs emitted across all text runs |

#### Record probes

`records:<kind>` returns a list (or map) of decoded operation records.

| channel | shape |
|---|---|
| `records:components` | `["DrawRect", …]` component class names |
| `records:semantics` | `[{contentDescriptionId, role, textId, stateDescriptionId, mode, enabled, clickable}]` |
| `records:animation_specs` | `[{animationId, motionDuration, motionEasingType, visibilityDuration, visibilityEasingType, enterAnimation, exitAnimation, animationEnabled}]` |
| `records:component_bindings` | animation specs joined to the components they drive, plus `usesDefaultSpec` |
| `records:uniforms` | `[{shaderId, floatNames, integerNames, bitmapNames}]` — names only; bound values live in the compiled program |
| `records:paths` | `{"<id>": {"present": true}}` — the path cache exposes presence only |
| `records:tweens` | `{"<id>": {"present": true}}` |
| `records:impulses` | `[{duration, startAt}]` |
| `records:glyph_runs` | `[{label, text, glyphCount, allRotationsDeg, allDeviceY, firstDeviceX, deviceXOrder}]` |
| `records:anchor_runs` | recorded anchored-text runs in device space |

#### Trace probes

Trace probes observe **transient events**, so their recorders must be installed before the
event can occur.

| channel | shape |
|---|---|
| `trace:handled` | `{"<step id>": true\|false}` — whether each gesture was consumed |
| `trace:host_actions` | `[{name, value}]` — actions dispatched to the host |
| `trace:branches` | `[{path, type, a, b, executed, executedChildOps}]` — §4.4 |

> [!CAUTION]
> Branch instrumentation must be installed **after** the data-operations pass and **before**
> the first paint. The data pass walks containers unconditionally; instrumenting ahead of it
> charges every branch — taken or not — with one execution, and every branch then looks taken.

#### Draw-log and relational probes

| channel | shape |
|---|---|
| `draw_log:commands` | `["paint", "drawArc", "paint", "drawSector"]` — matched as an **ordered subsequence**, because the recorded stream also carries the root layout's own matrix scaffolding |
| `relation:anchor_runs` | `[{kind, axis, runs, why}]` where `kind` ∈ `strictly_increasing`, `strictly_decreasing`, `equal_gaps` — asserts *relationships* between runs rather than absolute device coordinates, so the check survives font differences |

### 4.3 Layout tree encoding

```jsonc
{ "id": -5, "kind": "BoxLayout", "depth": 2, "x": 0, "y": 0,
  "width": 202, "height": 202, "isGone": false, "visibility": "VISIBLE",
  "scroll_x": 0 }
```

- Nodes are sorted by **component id ascending**. Ids are descending negatives in creation
  order, so the array is in **reverse creation order**: a node appears *after* all its
  descendants.
- To reconstruct parentage: for the node at index `i` with depth `d`, walk backwards while
  `depth > d`; its direct children are the entries at exactly `d + 1`.
- `scroll_x` / `scroll_y` are emitted only when non-zero, and then on both sides.
- **Geometry of `isGone` nodes is not compared** — a gone node's bounds are undefined.

### 4.4 Branch records

`path` is the sibling index within its nesting prefix (`"3"`, `"3.0"`). `type` is one of
`eq`, `neq`, `lt`, `lte`, `gt`, `gte`, `changed`. `a`/`b` are the operands as encoded on the
wire. `executed` is whether the guarded body ran; `executedChildOps` is how many child
operations ran.

---

## 5. Result format

A player emits one JSON document. This is what the audit visualiser consumes.

```jsonc
{
  "player": { "name": "my-player", "version": "…" },
  "corpus": { "path": "gold", "tests": 252 },
  "summary": {
    "passed": 223, "failed": 26, "errored": 0, "suspicious": 3,
    "checks_total": 704, "checks_failed": 73
  },
  "results": [
    {
      "name": "animation_spec_field_roundtrip",
      "category": "animationspec",
      "description": "…",
      "profile": "core",
      "status": "PASS",              // PASS | FAIL | ERROR | SKIP
      "raw_status": "PASS",          // status before the suspicious exclusion
      "suspicious": false,
      "suspicious_reason": null,
      "checks_total": 1,
      "checks_failed": 0,
      "duration_ms": 0,
      "diffs": [ /* §5.1 */ ],
      "observed": { "initial": { "records:animation_specs": [ … ] } },
      "attachments": {}              // e.g. {"raster_actual": "data:image/png;base64,…"}
    }
  ]
}
```

`observed` is keyed by step id, then by `probe[:channel]`, and holds what the player actually
saw. It is what lets the visualiser show a failure without re-running anything.

### 5.1 Diff entries

```jsonc
{ "at": "resize_0", "probe": "tree", "target": "-5",
  "property": "width", "expected": 202, "actual": 250, "tolerance": 0.5 }
```

Reserved `property` values for structural failures:

| value | meaning |
|---|---|
| `STEP_NOT_RUN` | The check's `at` step never executed. Always a failure. |
| `PROBE_NOT_IMPLEMENTED` | The player does not implement this probe. Always a failure — never report a value that happens to match. |

---

## 6. Implementing a new player

The practical how-to — what to build first, how much of the corpus each capability unlocks,
and the mistakes that silently produce a wrong score — lives in
**[PLAYER_IMPLEMENTATION_GUIDE.md](PLAYER_IMPLEMENTATION_GUIDE.md)**.

This document stays the normative schema: what the fields *mean*. The guide is how to act on
them. Keeping the two apart is deliberate — the tiering advice changes as the corpus grows,
the schema should not.

The short version:

1. Decode `document_base64` and load it. You never parse the authoring JSON.
2. Install the `harness.text_metrics` policy **before** constructing the document.
3. Install event recorders before the first paint; install branch instrumentation *after* the
   data-operations pass.
4. Run `timeline` in order. After each step, evaluate every check whose `at` matches it.
5. Fail unrun steps as `STEP_NOT_RUN` and unimplemented probes as `PROBE_NOT_IMPLEMENTED`.
6. Emit §5.

`tree` + `float` + `ops:count`, driven by `paint` + `resize`, covers ~84% of the corpus and is
the sensible first milestone.

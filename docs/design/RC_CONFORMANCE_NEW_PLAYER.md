# Adding a conformance lane for a new player

How to score a new Remote Compose player — any language, any toolkit — against the AndroidX
conformance corpus, using this repository's runner or writing your own.

The normative documents are upstream's, and they win wherever this page disagrees:

- [`CONFORMANCE_FORMAT.md`][format] — the gold schema, every step kind, every probe, the result
  format.
- [`PLAYER_IMPLEMENTATION_GUIDE.md`][guide] — upstream's how-to, including the raster metric in full.

This page is the condensed version, plus what running two lanes here (`cmp`, `native-appkit`)
taught that those documents do not say. Background and current scores are in
[`RC_CONFORMANCE.md`](RC_CONFORMANCE.md); golds we believe are wrong are in
[`RC_CONFORMANCE_PUSHBACK.md`](RC_CONFORMANCE_PUSHBACK.md).

## 1. Where the test files are

The corpus is **not on `main`**. It is on the long-lived branch
[`vendor/androidx-rc-conformance`][branch], reconstructed from an unmerged AOSP change
([Gerrit 4302372](https://android-review.googlesource.com/c/platform/frameworks/support/+/4302372)).
Its provenance and `refresh.sh` are in `third_party/rc-conformance-spec/PROVENANCE.md` on that
branch.

```
third_party/rc-conformance-spec/compose/remote/specification/
├── AREA-01-WIRE-PROTOCOL.md … AREA-17-CONFORMANCE-DEBUGGING.md   # what you need to *play* a document
└── conformance/                                                   # ← the spec dir
    ├── CONFORMANCE_FORMAT.md, PLAYER_IMPLEMENTATION_GUIDE.md
    ├── gold/<category>/<name>.gold.json    # ← THE TESTS. One file per test. Your runner reads these.
    ├── tests/<category>/<name>.json        # authoring JSON the golds were compiled from. Ignore it.
    ├── fonts/Ahem.ttf                      # the font every text run must be drawn with (§4)
    ├── results/<player>/conformance-results.json   # published runs, incl. results/typescript/
    └── generate-audit-report.mjs, generate-test-inspector.mjs, …  # player-agnostic HTML reports
```

As vendored (October 2026): **364 golds** in 18 categories — 356 `core`, 8 `extended`, 3 tagged
`suspicious` — carrying **1752 checks**. Layout is half the corpus (182 golds); the rest are
expressions, canvas, interactivity, matrixmath, animationspec, wire, textoperations, shaders,
semantics, scheduling, pathoperations, particles, loom, dataoperations, conditionals, colortheme and
clock. Upstream's prose still quotes the older 252-gold figures.

Check it out next to your clone as a worktree:

```bash
git worktree add ../rc-players-conformance-spec vendor/androidx-rc-conformance
```

> [!IMPORTANT]
> **Take the spec dir as configuration, never a hard-coded path, and never copy `gold/` onto
> `main` or into another repo.** A copy drifts the first time upstream regenerates a gold, and the
> runner keeps passing against the stale expectation. Here the spec dir is `-Prc.specDir`, then
> `RC_SPEC_DIR`, then the sibling worktree above.

### What a gold looks like

```jsonc
{
  "format_version": 2,                     // anything else → SKIP
  "name": "column_basic", "category": "layout", "profile": "core",
  "harness":    { "text_metrics": "ahem" },  // "ahem" | "native" | absent
  "parameters": { "width": 300, "height": 400, "tolerance": 0.5 },
  "document_base64": "…",                  // the compiled .rc document — this is what you play
  "timeline": [
    { "id": "initial",  "kind": "paint",  "frames": 2, "animation_enabled": false },
    { "id": "resize_0", "kind": "resize", "width": 300, "height": 400, "frames": 2 }
  ],
  "checks": [
    { "at": "initial",  "probe": "tree",   "tolerance": 0.5, "expect": [ /* nodes */ ] },
    { "at": "resize_0", "probe": "raster", "advisory": true, "expect": "data:image/png;base64,…" },
    { "at": "initial",  "probe": "float",  "target": "res_nested_if", "expect": 1 }
  ],
  "unasserted": [ … ],                     // facts no player can observe — ignore
  "tags": ["suspicious"]                   // optional — excluded from the pass rate
}
```

**The gold is the player-facing API.** A player only needs a binary `.rc` loader: decode
`document_base64` and play it. Never parse `tests/**`.

## 2. Reuse this runner, or write your own

`rc-conformance/` already implements everything player-agnostic — gold parsing, the timeline loop,
check binding, every comparator, the exact raster metric, the results file and the scorecard:

```
rc-conformance/src/main/kotlin/ee/schimke/composeai/rcconformance/
  corpus/   Gold.kt, Results.kt            # the formats; knows no player
  runner/   ConformanceRunner.kt           # the loop: step → bound checks → diffs
            ConformanceEngine.kt           # ← the interface you implement
            Comparators.kt                 # tree / value / presence / subsequence / relation / raster
            Pixelmatch.kt, DiffHeatmap.kt, Png.kt
  Scorecard.kt
rc-conformance/src/test/kotlin/…/
  RcConformanceHarness.kt                  # entry point; picks the engine from -Prc.player
  engine/CmpEngine.kt                      # Compose Multiplatform lane (in-process)
  engine/NativeSwiftEngine.kt              # Swift/AppKit lane (drives a subprocess)
```

**If your player can be driven from the JVM — in process, or as a subprocess speaking a batch
protocol the way `NativeSwiftEngine` does — add one file to `engine/` and register it in
`RcConformanceHarness`.** That is the cheap path, and it guarantees your score is computed by the
same comparators as every other lane here.

The seam is deliberately narrow:

```kotlin
interface ConformanceEngine {
  val name: String       // also the results/<name>/ directory and the report label
  val version: String
  fun <T> withSession(gold: Gold, block: (ConformanceSession) -> T): T
}
interface ConformanceSession {
  fun execute(step: Step, onCapture: (String) -> Unit = {})  // throw UnsupportedStepKind if you can't
  fun observe(check: Check): Observation                     // Value(json) | Raster(w,h,rgba) | NotImplemented
}
```

> [!IMPORTANT]
> **An engine drives and observes; it never decides pass/fail.** Return what the player saw in the
> corpus's own JSON vocabulary and let the runner compare. A score computed by a second comparator
> is not comparable with anyone else's.

Run it:

```bash
scripts/agent-gradle.sh :rc-conformance:conformance -Prc.player=<your-lane> \
  -Prc.specDir=../rc-players-conformance-spec/third_party/rc-conformance-spec/compose/remote/specification/conformance \
  -Prc.filter=layout          # optional: a category or a gold name
```

Output lands in `rc-conformance/build/conformance/` (`conformance-results.json`, `scorecard.md`).
The task is excluded from `test`/`check` on purpose; the nightly
[`conformance.yml`](../../.github/workflows/conformance.yml) runs it non-gating.

If your player cannot be driven from the JVM, write the runner in your player's own language and
repo — upstream's guide expects exactly that — but follow §3–§7 below to the letter, and port the
raster metric exactly (§5).

## 3. What the harness has to do

### The loop

```
for gold in spec/gold/**/*.gold.json:
    if gold.format_version != 2: SKIP
    install text-metrics policy from gold.harness.text_metrics   # BEFORE constructing the document
    register Ahem as the only font                               # BEFORE any drawing surface
    doc = load(base64decode(gold.document_base64))
    install event recorders (host actions, gesture handled, draw log, glyph runs)  # BEFORE first paint
    run the data-operations pass
    install branch instrumentation                               # AFTER the data pass
    checksByStep = group(gold.checks by .at)
    for step in gold.timeline:
        execute(step)              # frame_sequence also fires capture ids frame_<n>
        evaluate every check bound to step.id (or to the capture id), NOW
    fail every check whose `at` never ran as STEP_NOT_RUN
emit results JSON
```

### Timeline steps

A gold is runnable only if you support every kind in its timeline. Kinds in the current corpus, by
how many golds use them:

| kind | golds | do this |
|---|---:|---|
| `paint` | 364 | `frames` × (measure unless `measure:false`, then paint), advancing animation time 1/60 s per frame. Honour `animation_enabled`, `animation_time_seconds`. |
| `resize` | 316 | Set the viewport **and write width/height into float slots 5 and 6**, then measure+paint `frames` times. Documents read their own size from those slots. |
| `click` | 28 | Dispatch; record whether consumed (`trace:handled`); then paint/measure/paint unless `repaint:false`. |
| `advance_time` | 24 | Advance animation time by `advance_millis`, then paint/measure/paint. |
| `frame_sequence` | 14 | Paint frames `0…total_frames` (`inclusive`), writing wall clock to `currentTime`, elapsed seconds to slot 30, delta to slot 31. **Capture at each frame in `capture` under the synthetic id `frame_<n>`** — checks bind to those ids, not the step's. |
| `touch_down` / `touch_drag` / `touch_up` | 8–9 | As `click`; `touch_up` carries `dx`/`dy` for fling. |
| `settle` | 8 | Nothing. A terminal anchor for "after everything". |
| `time` | 5 | Paint at absolute time `seconds` (honouring `measure`). **Not in the format doc's table** — `CmpEngine` handles it. |
| `trigger` | 5 | One-shot `resize` or `click` stimulus, then a single measure/paint. |
| `clock_snapshot` | 5 | **Rebuild the document** against the frozen `clock`, then paint. The clock is read at construction; repainting compares the same instant three times. |
| `longPress`, `doubleClick` | 2 | As `click`. |
| `theme` | 2 | Paint with theme code light `-3`, dark `-2`, normal `-1`. |

An unknown kind is an error, never a skip: throw `UnsupportedStepKind` and let its checks fail as
`STEP_NOT_RUN`. `paint` + `resize` alone makes ~88% of golds runnable.

### Five rules that silently produce a wrong score

Every one of these was made by a real runner first.

1. **Evaluate each check at its step, not at the end.** Probes read mutable state; a light-theme
   colour check evaluated after the dark-theme step compares against dark values and still looks
   like a real assertion.
2. **Never let an unimplemented probe pass or vanish.** `PROBE_NOT_IMPLEMENTED` and `STEP_NOT_RUN`
   are failures.
3. **Honour `measure:false` and `repaint:false`.** A measure pass walks every container, including
   branches paint would skip (inflating `trace:branches`); the post-gesture repaint clears touch
   slots 13 (x) and 14 (y) that interactivity checks read.
4. **Branch instrumentation goes after the data pass.** Before it, every branch looks taken.
5. **Validate the runner before believing the player.** Count evaluated checks against the corpus
   (1752 now); perturb one `expect` and confirm it fails; compare the *set of checks evaluated*
   with `results/typescript/`. `RunnerAccountingTest` pins the ways this runner can lose a check.

## 4. Probes

A check is `{at, probe, channel?, target?, expect, tolerance?, advisory?}`.

```
tolerance = check.tolerance ?? gold.parameters.tolerance ?? 0     # every probe except raster
raster    = check.tolerance ?? 16                                 # a pixel count; see §5
```

Probes in the current corpus, with check counts, in the order worth implementing:

| probe[:channel] | checks | what the player must report |
|---|---:|---|
| `raster` | 633 | the rendered frame as straight RGBA, at the current viewport — §5 |
| `tree` | 582 | the laid-out component tree — below |
| `float` | 241 | a float slot by numeric id **or variable name** |
| `ops:present` | 106 | operation class names present in the decoded document |
| `particles` | 66 | `[[x, y, vx, vy], …]` for one simulation frame; no `target` — the doc defines one system |
| `text`, `color`, `int` | 21 / 20 / 11 | text by id; ARGB as unsigned 32-bit; int slot by id or name |
| `trace:handled` | 14 | `{stepId: bool}` — was each gesture consumed |
| `ops:count`, `ops:counts`, `ops:absent`, … | ~20 | operation census — see the warning below |
| `draw_log:commands` | 7 | ordered draw calls in the portable vocabulary; matched as an **ordered subsequence** |
| `records:*`, `matrix`, `float_array:*`, `trace:branches`, `trace:host_actions`, `relation:anchor_runs`, … | ~40 | decoded-record field reads and transient-event recorders; long tail |

`tree` + `float` + `ops:present` covers most binding checks. The transient-event channels
(`draw_log:commands`, `records:glyph_runs`, `records:anchor_runs`, `trace:branches`) need the
player to expose a hook that fires during paint; leave them `NotImplemented` until everything else
works.

### `tree`

Each node: `{id, kind, depth, x, y, width, height, isGone, visibility}`, plus `scroll_x`/`scroll_y`
**only when non-zero**.

- Sorted by component id ascending. Ids are descending negatives in creation order, so a node comes
  *after* its descendants.
- `x`/`y` are **parent-relative and exclude modifier translation**. Padding and offset are paint-time;
  a padded child reports `x: 0` and the parent is larger. Counting padding into `x`/`y` was ~17
  failing golds here.
- `kind` is the AndroidX class name with leading underscores stripped (`BoxLayout`,
  `ColumnLayout`, `RootLayoutComponent`). Map your own type names onto it.
- `visibility` is `VISIBLE` / `INVISIBLE` / `GONE` and is **inherited**: a child of a gone container
  is gone. A component the layout measured but never placed (a collapsible dropping a child) is gone
  too. Geometry of `isGone` nodes is not compared.
- `harness.text_metrics: "ahem"` means the gold's geometry came from closed-form arithmetic — 1 em
  advance per glyph, ascent 0.8 em, descent 0.2 em, greedy word wrap on character counts. Install
  that model before constructing the document, or multi-line text trees will not match. `native`
  means measure with your real text stack (against Ahem). Honour it per gold.

### Scalar probes (`float`, `int`, `color`, `text`)

- Resolve a **name** through the document's own `NamedVariable` table. A name the document never
  declared is `NotImplemented` — never fall back to slot 0, which exists and holds a plausible
  number.
- A **numeric** slot the document never wrote reads 0 (the reference's rule). That is an
  observation, not a gap.
- A matrix lives in the object registry. The float slot sharing its id holds a dirty tick, not a
  value.

### Operation census (`ops:*`)

Report names under the AndroidX operation names, and count absent ones as zero. Matching
`ops:count` exactly depends on rules the corpus never states (the header counts; a container holds
its children; linking's expansion counts; a macro definition does not; a referenced-operations
block does). Compute it in the *harness* from the decoded document — do not make the player keep a
census it has no other use for.

### The font

Register `fonts/Ahem.ttf` and draw **every** text run with it, whatever family the document names.
Verify rather than assume: `"ABC"` at 24 px must measure exactly `72.0`. Without it every
text-bearing raster fails for reasons unrelated to your player.

## 5. Image comparisons

`raster` checks carry the reference frame as a `data:image/png;base64,…` URI in `expect`. The
check's tolerance is a **count of differing pixels**, not an RMSE. Read it from the check itself;
when the check has none, the budget is a fixed **16** — never `parameters.tolerance`. (The corpus
generator stamps a gold's `parameters.max_raster_pixels` onto each raster check as its `tolerance`,
so a runner never reads that parameter.)

**Port the metric exactly** — it is part of the contract, because every lane computes its own
verdict. It is `mapbox/pixelmatch`'s antialiased-pixel detector (Vyšniauskas), after flattening
onto white:

1. Flatten RGBA onto white per channel: `out = 255 + (c − 255) · a/255`.
2. Per pixel, YIQ distance `0.5053·Δy² + 0.299·Δi² + 0.1957·Δq²`.
3. Skip if `≤ 35215 × 0.1²`.
4. Skip if the pixel is antialiasing in **either** image (extreme of its 8-neighbourhood by Y, ≤ 2
   identical neighbours, and that extreme neighbour has > 2 identical neighbours in both images).
5. Count what is left; pass if `count ≤ tolerance`.

The reference JS is in the [guide §10][guide]; the Kotlin port is
[`Pixelmatch.kt`](../../rc-conformance/src/main/kotlin/ee/schimke/composeai/rcconformance/runner/Pixelmatch.kt).
If you are on the JVM, use it.

Rules that matter:

- **Compare at the reference image's own dimensions.** A size mismatch is itself a failure (report
  `dimensions`). Never scale either image, and never crop both to the document's declared size — a
  resize step then scores zero while proving nothing.
- **Hand the runner straight (non-premultiplied) RGBA**, row-major, at the current viewport.
- **Honour `"advisory": true`.** 612 of the 633 raster checks are advisory: count and report them,
  but they do not decide a gold's verdict. Only the other 21 are binding. Gating on advisory rasters
  makes your score incomparable with upstream's (this lane did, and it was the wrong call). Binding
  totals should then match upstream's own figure.
- **Self-check the metric.** A gold image against itself must score 0;
  `gold/layout/column_background_border`'s image against a copy shifted 1 px right must score in the
  hundreds. If not, `antialiased()` is excusing real differences and the channel is silently
  permissive. `PixelmatchTest` pins both.
- **Report evidence, not just the verdict.** This runner attaches the player's frame, a diff
  heatmap, raw and AA-aware counts, RMSE and max channel delta to each comparison. RMSE is a
  diagnostic only — at a gate of 8 it hid 34 real differences in this corpus.
- **Know the baseline's provenance.** Rasters are *recorded* by playing the timeline through a
  player, not derived from the reference engine. A 2026-09 regeneration through the View player
  painted resize steps at the initial size; the vendor branch carries a documented correction under
  [`renders/conformance-goldens/`](../../renders/conformance-goldens/). A resize raster that
  disagrees with every lane is worth checking against that before blaming the player.
- **Native toolkits differ legitimately.** Text line breaking, bitmap filtering and animation
  timing that come from the platform's own primitives stay failing as documented divergences
  (see `RC_CONFORMANCE.md` → *Native AppKit text and transition policy*). Do not reimplement drawing
  below the platform's primitives to match Skia.

## 6. Results

Write `results/<your-player>/conformance-results.json` per [`CONFORMANCE_FORMAT.md` §5][format] —
`player`, `summary`, and per gold `status` (`PASS | FAIL | ERROR | SKIP | SUSPICIOUS`),
`raw_status`, `diffs`, and **`observed`** keyed by step id then `probe[:channel]` (scalars nested by
target). Populate `observed`: most golds assert values, and it is the only "actual" side the HTML
report has. Score as passed ÷ (total − suspicious − skipped), core and extended reported
separately.

Then `node generate-audit-report.mjs --player <your-player>` in the spec dir renders the audit.

## 7. Reading the number

Every failing check is one of three things — sort it before fixing anything:

1. **The player is wrong** → fix the player. The usual answer.
2. **The lane cannot observe it** → fix the engine. `PROBE_NOT_IMPLEMENTED` on a field already
   decoded in the document is a runner bug.
3. **The corpus asserts something a player should not reproduce** → record it in
   [`RC_CONFORMANCE_PUSHBACK.md`](RC_CONFORMANCE_PUSHBACK.md) and let it keep failing.

**Never degrade the player's behaviour, correctness or performance to move the score.** A failing
check with a written reason beats a passing one bought with a worse player. And keep player fixes
and measurement fixes apart when you report a change in the number — only the first says anything
about rendering.

[branch]: https://github.com/yschimke/rc-players/tree/vendor/androidx-rc-conformance
[format]: https://github.com/yschimke/rc-players/blob/vendor/androidx-rc-conformance/third_party/rc-conformance-spec/compose/remote/specification/conformance/CONFORMANCE_FORMAT.md
[guide]: https://github.com/yschimke/rc-players/blob/vendor/androidx-rc-conformance/third_party/rc-conformance-spec/compose/remote/specification/conformance/PLAYER_IMPLEMENTATION_GUIDE.md

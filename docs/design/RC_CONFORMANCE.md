# The conformance lane

How this repository answers *"how conformant is this player?"* with a number, and what that number
currently does and does not mean.

## What it measures against

AndroidX publishes a language-neutral **conformance corpus** for Remote Compose: 252 golds over 18
subsystems, asserting 1515 checks. Each gold carries its own compiled document in
`document_base64`, a `timeline` of steps to drive, and a list of `checks` bound to those steps. A
player plays the document, runs the timeline, and reports what it observed — no converter, no
authoring-format parser, no knowledge of how the corpus was made.

The corpus is **not in this repository's main line.** It arrived as an *unmerged* AOSP Gerrit change
([4302372](https://android-review.googlesource.com/c/platform/frameworks/support/+/4302372)), so
there is no public git ref to read it from, and the guide's own instruction — *read `gold/` in
place, do not vendor a copy* — cannot be followed literally yet. The compromise is on the branch
[`vendor/androidx-rc-conformance`](https://github.com/yschimke/rc-players/tree/vendor/androidx-rc-conformance):

* the copy lives on **one long-lived branch**, never on `main`, so a refresh is one commit with a
  readable diff rather than noise inside feature work;
* `:rc-conformance` resolves the corpus as **configuration** (`-Prc.specDir`, or `RC_SPEC_DIR`), so
  nothing finds it by a hard-coded in-repo path;
* `refresh.sh` on that branch re-derives the whole tree from Gerrit, verified byte-identical, so
  *"has upstream moved?"* is a command rather than an archaeology exercise.

When the change merges, that directory should become a checkout of the real ref.

## Running it

```bash
git worktree add ../rc-players-conformance-spec vendor/androidx-rc-conformance

./gradlew :rc-conformance:conformance \
  -Prc.specDir=../rc-players-conformance-spec/third_party/rc-conformance-spec/compose/remote/specification/conformance
```

`-Prc.player` selects the lane (default `cmp`) and `-Prc.filter` narrows to one subsystem or gold
while developing. Output lands in `rc-conformance/build/conformance/`:

* `conformance-results.json` — the standard results file. The corpus's own player-agnostic report
  generators consume it directly: `node generate-audit-report.mjs --player cmp` turns it into the
  visual audit and the interactive test inspector. Each raster comparison carries its full evidence
  beside the verdict — the gold frame, the player's frame, the diff heatmap, and the pixel metric
  (AA-aware count, raw count, RMSE, max channel delta) — so a raster card shows *what* differed,
  not only that it differed.
* `scorecard.md` — the summary below.

[`.github/workflows/conformance.yml`](../../.github/workflows/conformance.yml) runs the same thing
nightly and publishes both as an artifact. It is **not gating**, and the reasons are in the workflow
header: the corpus is upstream-unmerged and may be revised without warning, and most of what the
score currently reports is this runner's own gaps rather than the player's.

Running it on the other Compose targets — iOS, macOS, wasmJs, and why Android cannot be run at all
yet — is worked out in [`RC_CONFORMANCE_PLATFORMS.md`](RC_CONFORMANCE_PLATFORMS.md).

## The rule: never trade the player for a number

A conformance score is a diagnostic, not a target. It is worth saying plainly because the pressure
runs the other way — a failing check is concrete and a regression is abstract, so "make the gold
pass" is always the nearer goal.

**Do not degrade the player's behaviour, correctness or performance to move this number.** If
matching a gold would mean carrying accounting the player has no other use for, holding state it
would otherwise not hold, or doing work on a path that has to be fast, then the right outcome is a
*documented divergence*, not a worse player.

`ops:count` is the standing example. Matching it exactly required deriving five rules the corpus
never states — that the header counts, that a container holds its children rather than sitting beside
them, that linking's expansion is what gets counted, that a macro definition is *not* counted because
the expansion consumes it, and that a referenced-operations block *is* counted even though it is
inlined. The last two are opposites. Reproducing that faithfully inside the player would mean the
player maintaining an operation census it has no other reason to keep, and it is the sort of thing
that is both fiddly and easy to get subtly wrong.

So when a gold and the player disagree, the options are, in order:

1. **The player is wrong** — fix it. Most of the time this is the answer, and this lane has the
   receipts: collapsible `GONE`, `StateLayout` sizing, box alignment and macro linking all looked
   like awkward golds and were all real defects.
2. **The lane cannot observe it yet** — fix the lane. Also common, and cheap: several probes reported
   `PROBE_NOT_IMPLEMENTED` for fields that were already sitting decoded in the document.
3. **The corpus is asserting something a player cannot or should not reproduce** — record it, raise it
   upstream, and let the check keep failing. Never paper over it. The record is
   [`RC_CONFORMANCE_PUSHBACK.md`](RC_CONFORMANCE_PUSHBACK.md).

A permanently failing check with a written reason is a better artefact than a passing one bought with
a worse player. `unasserted` exists in the gold format for exactly this reason (§2.8), and the
corpus's own §1 says an assertion that cannot fail is worse than no assertion — the same logic applies
to an assertion that only passes because the implementation contorted itself to satisfy it.

## The lanes

| lane | what it is | what it observes |
| --- | --- | --- |
| `cmp` | `rc-player-compose`, headless through `SkikoComposeUiTest` | `tree`, `raster`, the scalar state probes, `ops:*` |
| `androidx-jvm` | the vendored AndroidX player, desktop cut — a **reference** | `tree`, `raster` |
| `native-appkit` | the Swift core with the macOS sample's AppKit renderer | `raster`, `tree`, the scalar value probes (`float`, `int`, `text`, `color`) |
| `typescript` | upstream's own run, shipped in the corpus — a **reference** | everything, scored by upstream's runner |

The reference lanes are what make the first interpretable. The corpus was generated by AndroidX's own
engine, so a gold the CMP player fails has two very different explanations — our player is wrong, or
the gold encodes something specific to how AndroidX does it — and one lane cannot tell them apart.

`androidx-jvm` reports a tree because `CoreDocument.paint` is run headless against a measure-only
paint context; the Compose renderer this module ships never assigns `Component` geometry, so before
that the lane could answer `raster` and nothing else, and passed no gold by construction.

`native-appkit` answers `raster` and `tree`. The tree comes from the packaged macOS player: the
batch protocol returns each frame's laid-out component tree beside its PNG, taken from the same view,
so the two channels cannot disagree about what was rendered. The player reports the geometry its
layout manager assigned — padding and offset are modifier translation and are taken back out, per
§2.7 — with the AndroidX class name of each component, its effective visibility and its depth.

Before that channel existed this lane's gold count was zero *by construction*: no gold in the corpus
asserts raster alone. It is 216 of 353 core golds at the 2026-09-23 measurement, and the tree and
value channels are where those passes come from.

The **value probes** read a document's own state rather than its rendering, so the batch protocol
carries a per-frame request for the slots a gold asserts and returns what the core resolved at that
instant. Two rules in the corpus are load-bearing there and both are the reference's, not this
player's: a *numeric* slot the document never wrote reads 0, and a *named* target the document never
declared is unobservable — the player leaves the key out so the runner can report its own gap rather
than a wrong value. A *data-only* document (one that declares values and nothing to draw) is decoded
for a conformance run through an explicit mode, because refusing to paint it is right for a host and
wrong for a probe.

The batch also replays the timeline before each capture. It supports elapsed- and wall-clock steps,
frame sequences, resize/click triggers, click variants, raw touch sequences and deterministic scroll
drag/decay. Input is cumulative because every requested frame opens the document afresh; after each
step the view is rebuilt before the next hit test. That reduced `STEP_NOT_RUN` from 305 binding checks
to the six checks behind explicit light/dark theme switching. Scroll offsets are observable through
`scroll_x`/`scroll_y`; translating the AppKit paint subtree by that offset remains raster work.

The remaining diffs are a work list rather than noise, and at the 2026-09-23 run two causes carried
most of it:

* **The operation census knew three names.** `ops:present` and `ops:counts` read a name table that
  mapped only the three matrix opcodes, so every other operation a gold asserted was reported
  missing. That one table was the *only* failing check in 43 golds. It now covers every opcode in
  the AndroidX manifest under both AndroidX names, as the CMP lane does, with absent operations
  counted as zero. Names only the TypeScript player uses are left to fail
  ([pushback §2](RC_CONFORMANCE_PUSHBACK.md)).
* **The core refused documents over operations it had no case for** — and a refused document takes
  every check in its gold with it. 82 of the 141 failing golds failed every check. The families
  behind them, by golds: impulse and `WAKE_IN` scheduling (9), `ROOT_CONTENT_BEHAVIOR` (8 of the 10
  semantics golds), `ATTRIBUTE_TIME` with the `LongConstant` it reads (7, the clock golds),
  `BooleanConstant`, `IdLookup`, `LoopOperation`, particle compare, path expressions, the text
  operations, and single golds for the rest. Root content behaviour, document-level semantics,
  boolean and long constants, time attributes, debug messages and the sound family now decode; the
  others are still refused.

What is left after those: `StateLayout` transitions and the Ahem text golds on `tree` (~15 golds),
the scheduling, lookup and text operations above, the `records:impulses` probe this lane does not
yet wire, and theme switching.

Several of the golds behind these numbers assert less than their names say. They are logged, with
evidence, in [`RC_CONFORMANCE_PUSHBACK.md`](RC_CONFORMANCE_PUSHBACK.md).

### Native AppKit text and transition policy

The native players use the platform's own rendering, and a difference that comes from that is
accepted rather than imitated. **Do not reimplement drawing below the platform's own primitives to
match Android or Skia.** Keep Core Graphics sampling, Core Text / TextKit line breaking and
rasterization, and Core Animation timing, and record the golds they move rather than porting
Android's algorithms into the renderer. In particular:

- **Glyphs and line breaking.** Text is measured and wrapped by TextKit. Where it breaks a line
  differently from Android's `StaticLayout` — how a wrapped line's trailing space counts toward its
  width, say — the gold stays failing.
- **Bitmap sampling.** A bitmap draws with Core Graphics' interpolation at the paint's filter level.
  Its bilinear filter is not Skia's, so a raster of a strongly scaled bitmap
  (`canvas_bitmap_scaled`) can differ at the edges of each source pixel.
- **Animation timing.** A transition uses the document's spec on the host's clock. AndroidX's own
  frame sampling (`RC_CONFORMANCE_PUSHBACK.md` §11) and easing differences are not reproduced; a
  native transition must still reach the documented state and remain a valid real-time animation.
- **Layout changes at the instant of input.** AndroidX animates a component's bounds by default, so
  a tree read at the instant of a click or touch still shows the layout from before it. The native
  players apply the new layout at once; only a `StateLayout` switch is a Core Animation
  cross-fade. `interaction_click_button`, `interaction_click_toggle_visibility`,
  `interaction_touch_down_up_press` and `state_layout_state_switch` bind to that instant and stay
  failing on native-appkit; their settled frames match.

This is not an exemption for semantics. What a document *asks for* is still the player's job, and
a regression there is a bug: component geometry that is not down to line breaking (a host's own
padding counted as the component's, a line cap ignored), the Ahem-backed conformance layout,
anchored and text-path placement, CoreText autosize, gradient tile modes, and transitions that do
not happen at all.

### Scores

Measured against the corpus as vendored on `vendor/androidx-rc-conformance`, with the advisory flag
honoured — the 2026-09-23 published run at `ee18643`. The corpus has grown since the 2026-09-19
run these figures used to quote (241 core golds then, 353 now), so the two are not comparable:

| | `cmp` | `androidx-jvm` | `native-appkit` | `typescript` |
| --- | ---: | ---: | ---: | ---: |
| Golds passed (core profile) | **236 / 353** | 132 / 353 | 216 / 353 | 352 / 353 |
| Binding checks failing | 311 / 1140 | 690 / 1140 | 266 / 1140 | 9 / 1140 |
| Advisory (raster) disagreeing | 171 / 612 | 261 / 612 | 232 / 612 | 174 / 612 |
| Golds errored | 20 | 43 | 0 | 0 |

The published run is on
[`reports/conformance`](https://github.com/yschimke/rc-players/tree/reports/conformance), and the
per-player visual audits the corpus's own generators produce are published to GitHub Pages.

### The raster baselines, and the recorder that stamps them

A gold's `tree` and scalar checks come from the reference engine; its **per-step rasters are
recorded** — a harness plays the timeline through a player and writes the frames back. Which player
records therefore decides what every lane is measured against, and on 2026-09-18 it went wrong: the
regeneration rendered through the View player (`remote-player-view`), whose `RemoteComposeView.onMeasure`
sizes itself to the document rather than to its parent, so a `resize` step keeps painting the initial
size and the newly exposed area stays white. Measured over the whole corpus, every independent
rendering — CMP, the embedded player, the TypeScript player, the previous software recordings —
agrees with every other on resize at **0.89–0.98**, and that recording was the only outlier at
**0.56–0.64**. A View player *constructed* at the target viewport renders the old gold
pixel-identically, and the embedded player resized live does too.

`vendor/androidx-rc-conformance` therefore carries a **documented correction**: 178 frames (175
resize-affected, 3 animation regressions) restored to the previous recordings, 144 native-recorded
frames kept because they fix genuinely stale baselines (missing children, blank particle frames).
The recorder analysis, the corrected-corpus build script and the upstream patches — record with the
embedded player (`UPDATE_GOLDENS=embedded`), or re-create the View player per viewport — live under
[`renders/conformance-goldens/`](../../renders/conformance-goldens/). It is a temporary local
correction: when a fixed patch set lands upstream, `refresh.sh` replaces it and the note goes.

### Two work lists, not one

The cross-lane split is the output worth acting on, and it has to be read in two halves. Against
`typescript`, 33 golds pass there and fail here — but 7 of those fail *only* on
`PROBE_NOT_IMPLEMENTED` or `STEP_NOT_RUN`, which is this runner admitting it cannot look. A probe to
implement in the lane and a bug to fix in the player are opposite findings, and a list that mixes
them is not a work list.

### The reference is the corpus, not another player

Worth stating plainly, because it is easy to get backwards. The 252 golds were generated by
**AndroidX's own engine** — `ConformanceGoldGeneratorTest` in `remote-creation-core`, replayed
through the reference engine. Matching the corpus *is* matching AndroidX. There is no separate,
earlier step in which this player should be compared against an AndroidX player instead.

The corpus also ships one other implementation's results, at `results/typescript/`. That player is
**not a reference**: it is another implementation scored against the same golds. Its number is useful
as a yardstick for how much of the corpus is realistically reachable, and as a way to *rank* work —
which golds it passes and this lane does not. It is not an oracle, and ranking that way biases toward
whatever that player happens to implement.

Comparing with it used to need care, because the two runners did not score the same way. Its runner
treats `raster` checks as **advisory** — counted (`advisory_total: 605`) but not deciding a verdict —
and 38 of its 205 passing golds carry raster diffs and pass anyway. **This lane gated on them, and
that was the wrong call.**

The argument for gating was §2.5, which describes a `raster` check as a check like any other, and §7,
which excludes only suspicious and skipped golds. That reasoning missed the decisive fact, which is
not in the prose at all: **the golds themselves carry the flag.** 605 of the 1515 checks are written
`"advisory": true`, every one of them `raster`. A flag set per check in the data is a stronger
statement of intent than an inference from a paragraph about the metric, and a runner that ignores it
is not reading the corpus it was given.

So this lane honours it. Binding checks total 910, which is upstream's own figure arrived at
independently — the arithmetic is the confirmation. What the flag protects against is real: the
raster metric is an antialiasing-aware pixel walk, and across two text stacks and two GPU backends it
disagrees for reasons no player can fix.

The cost of having gated was not a harsher number, it was an *incomparable* one. Every score this
lane published was on a basis no other player's runner used, which is the one thing a conformance
number exists to avoid.

### What the score does and does not measure

A gold-level score moves for two unrelated reasons, and conflating them overstates the player.

* **Player fixes** change what the player draws or will accept. `StateLayout` sizing, box alignment,
  macro linking and expansion, the text transform.
* **Measurement fixes** leave the player untouched and correct what this lane could observe. The tree
  coordinate convention, unplaced-means-gone, `draw_log:commands`, the operation census, the record
  probes.

Both are legitimate — a probe reporting `PROBE_NOT_IMPLEMENTED` for a field already decoded in the
document is wrong, and the guide requires scoring it as a failure until it is implemented — but only
the first says anything about rendering. Of the run that took this lane from 117 to 167 golds,
roughly 11 came from player fixes and 39 from measurement.

**The raster channel is the one that measures drawing**, and it is the honest number to quote for the
player: of 208 failing raster checks, one is attributable to this player.

### The finding that matters

Cross-referencing the two lanes, check by check, and **excluding every check the reference lane could
not run**:

> Of the 633 raster checks, exactly **one** fails in the CMP player and passes in AndroidX's own
> player: `canvas_shader_gradient`.

Every other raster disagreement the CMP player has with the corpus, AndroidX's own JVM player has
too. That is a strong statement about the CMP player's drawing: measured against the reference
implementation rather than against the corpus's Android-hosted expectations, its pixels are not the
problem. It is also a warning about single-lane numbers — 143 CMP raster failures look alarming until
110 of them turn out to be shared with the engine that generated the corpus.

### What the corpus found in the player

Nine golds **error** rather than fail — the player refuses the document outright:

| golds | failure |
| --- | --- |
| the five `loom_*` macro/slot golds | `RcLinkException: Unmatched ContainerEnd` |
| `spacer_fixed_sizes`, `spacer_weighted` | `Unknown AndroidX box alignment horizontal=0 vertical=0` |
| `image_layout_sizing_options` | `RcColumnLayout requires LayoutComponentContent` |
| `path_tween_morph` | `Missing path 10` |

Those nine also cost 202 `STEP_NOT_RUN` checks between them, because a document that will not load
takes the rest of its timeline with it. They are the highest-leverage things on this list.

### What is still the runner's own gap

Verified, not assumed — each of these was checked against the reference lane or the raster channel
before being classified:

| gap | scope | evidence it is the runner |
| --- | --- | --- |
| `harness.text_metrics: "ahem"` closed-form model not implemented | ~19 golds, `width`/`height` | The corpus declares the model per gold (§2.3); we measure with the real text stack, so `core_text_multiline_wrap` wraps at 200 where the model says 144 |
| Padding counted into tree `x`/`y` | ~17 golds | §2.7 is explicit that modifier translation is not in `x`/`y`; `box_padding_all` expects 0 and we report the 24 Compose placed the child at |
| Visibility not inherited from a gone ancestor | 12 golds | The **rasters pass** on both `*_container_gone` golds — the pixels are right, only the reported flag is wrong |
| `scroll_x` / `scroll_y` never populated | 4 golds | The seam defaults them to zero and omits them |
| The four transient-event channels | 12 checks | `draw_log:commands`, `records:glyph_runs`, `records:anchor_runs`, `trace:branches` need the player to surface events as they happen |

## Architecture

```
corpus/   the gold model and the result model.   Knows the format, knows no player.
runner/   the timeline loop, check evaluation,   Player-agnostic; talks to an engine only
          and the raster metric.                 through `ConformanceEngine`.
engine/   one adapter per player lane.           The only package allowed to name a player.
```

Two boundaries carry weight.

**An engine drives and observes; it never decides whether an observation passes.** Comparison —
tolerance resolution, the tree diff, the antialiasing-aware raster count — lives in the runner for
every lane alike. A score computed by two different comparators is not a comparison, which is the
whole reason the corpus specifies its pixel metric down to the source.

**Nothing in `corpus/` or `runner/` imports a player.** That is what lets a second lane be one new
file in `engine/` rather than a fork of the runner.

### The inspection seam, and why it is semantics

The CMP lane reads the laid-out tree and the document state through Compose's **semantics** tree,
using custom `SemanticsPropertyKey`s gated on `LocalRcInspection`.

Semantics is not an accessibility-only channel — it is Compose's general description of what the UI
*means*, read by accessibility services, by the testing framework and by autofill, with custom keys
as its documented extension point. Three things follow, and they are why this beat the bespoke
observer it replaced:

* **Geometry is pull-based.** `SemanticsNode.positionInRoot`, `size` and `boundsInRoot` read from the
  layout node on demand, so there is no positioning callback at all. The first version of this seam
  pushed geometry from an `onGloballyPositioned` per component — a callback that fires after every
  layout pass of the whole tree, and the usual way an inspection seam turns into a performance
  problem.
* **Depth is free.** The semantics tree is a tree, so nesting comes from walking it. The bespoke
  version had to thread a `depth` parameter down through `RenderLayoutNode` and its thirteen
  recursive call sites, which every document paid for in change-tracking whether or not anyone was
  inspecting.
* **One reader, many consumers.** A Compose UI test, `ImageComposeScene.semanticsOwners` and a
  standalone harness all read the same data the same way, with no type owned by the player in
  between. The seam's whole public surface is four keys and one composition local.

Read the **unmerged** tree. The merged one folds a subtree's properties into its nearest merging
ancestor, which collapses several components into one entry.

Two further notes. Custom keys are not mapped into platform accessibility node info, so a screen
reader does not see them. And `Modifier.semantics` is applied only when `LocalRcInspection` is true,
so an ordinary document's modifier chain is byte-for-byte what it was before the seam existed — the
one unconditional cost left is a static composition-local read per component, which is unmeasured;
`:rc-player-profile` is the thing to measure it with.

What semantics is *not* the right channel for: `InspectableValue` / `debugInspectorInfo` is Compose's
canonical free-unless-enabled pattern, and its gating idea is the one adopted here, but its payload
goes to the Layout Inspector through the slot table rather than to an in-process typed reader.

### The raster metric is ported exactly

`Pixelmatch.kt` is a direct port of the guide's §10 reference implementation — Vyšniauskas'
antialiased-pixel detector as implemented in `mapbox/pixelmatch`, applied after compositing both
images onto white. Each player computes its own verdict, so the algorithm is part of the contract:
an approximate reimplementation produces a number that is not comparable with anyone else's.

`PixelmatchTest` pins the guide's two self-checks, because a permissive `antialiased()` is silent —
every raster check still passes and the channel proves nothing. `RunnerAccountingTest` pins the
three ways a runner loses a check, all of which make the score look *better*.

## The work list

**CMP: nothing open in the player or the runner.** Measured on 2026-09-24 against
`vendor/androidx-rc-conformance`: 316 / 353 core golds pass and 20 error. Every gold that still
fails or errors has an entry in [`RC_CONFORMANCE_PUSHBACK.md`](RC_CONFORMANCE_PUSHBACK.md), and
#504 maps each one to its entry. The issues this list used to carry are closed: #182 (four
document shapes that crashed the player), #198 (collapsibles not collapsing to `GONE`), #202
(`StateLayout` fill), #281 (weighted collapsible children), #183 (`canvas_shader_gradient`), and on
the runner's side #203 (Ahem metrics) and #199 (gestures). The only "step not run" diffs left are in
the twenty TypeScript-written documents of pushback §1, so #184's observability gaps no longer show
up in the CMP lane.

**The advisory rasters.** 116 of 612 disagree. Each was checked on 2026-09-24: the rest are the
text stack, and every one that is not is a recorded frame that contradicts the same gold's binding
checks (pushback §23). That review found a real bug: CMP refused the int-valued graphics-layer
attributes `remote-creation-compose` writes (`SHAPE` in particular). An absent transform origin is
the declared 0, the top-left, in every player, as AndroidX's embedded player reads it.

**Elsewhere.** The native Swift lane's remaining work needs an AppKit or UIKit host and is tracked
in #431. The reference lane's own gap, the `graphicsLayer` applying only to the modifiers after it
in the embedded port, belongs upstream and is tracked in #98.

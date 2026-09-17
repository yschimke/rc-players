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
  visual audit and the interactive test inspector.
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
   upstream, and let the check keep failing. Never paper over it.

A permanently failing check with a written reason is a better artefact than a passing one bought with
a worse player. `unasserted` exists in the gold format for exactly this reason (§2.8), and the
corpus's own §1 says an assertion that cannot fail is worse than no assertion — the same logic applies
to an assertion that only passes because the implementation contorted itself to satisfy it.

## The two lanes

| lane | what it is | what it observes |
| --- | --- | --- |
| `cmp` | `rc-player-compose`, headless through `ImageComposeScene` | `tree`, `raster`, the scalar state probes, `ops:*` |
| `androidx-jvm` | the vendored AndroidX player, desktop cut — the **reference** | `raster` only; see below |

The second lane is what makes the first interpretable. The corpus was generated by AndroidX's own
engine, so a gold the CMP player fails has two very different explanations — our player is wrong, or
the gold encodes something specific to how AndroidX does it — and one lane cannot tell them apart.

### Scores

Measured against patch set 1 of the Gerrit change:

| | `cmp` | `androidx-jvm` |
| --- | ---: | ---: |
| Golds passed (core profile) | **141 / 241 (58.5%)** | 0 / 241 |
| Raster checks passed | **490 / 633** | 399 / 633 |
| Checks failed | 506 / 1515 | 1240 / 1515 |

The reference lane's gold-level zero is not a defect in it: it drives only `paint` and `resize`, and
reports no tree, so most golds carry a check it cannot answer. Its raster column is the number it
exists for.

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

Comparing with it also needs care, because it does not score the way this lane scores. Its runner
treats `raster` checks as **advisory** — counted (`advisory_total: 605`) but not deciding a verdict —
and 38 of its 205 passing golds carry raster diffs and pass anyway. This lane gates on them. Scoring
both files by the same rule, from their own recorded diffs:

| | rasters **gating** | rasters **advisory** |
| --- | ---: | ---: |
| CMP | **167 / 249** | 188 / 249 |
| TypeScript | 167 / 249 | 213 / 249 |

Which basis is right is a separate question — §2.5 treats a `raster` check as a check like any other,
and §7 excludes only suspicious and skipped golds, which is what this lane implements. Whichever is
used, it has to be the same one on both sides.

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

**In the player.** Each is a measured divergence from the engine that generated the corpus, with a
filed issue carrying the evidence:

| | golds | issue |
| --- | ---: | --- |
| Four document shapes crash it — LOOM linking, box alignment `0/0`, `LayoutComponentContent`, a missing tween path | 9 (+202 downstream checks) | #182 |
| Collapsible layouts do not collapse to `GONE` when nothing fits | ~18 | #198 |
| ~~`StateLayout` honours fill modifiers~~ — **fixed**, +7 golds | — | #202 |
| `canvas_shader_gradient` — the one raster difference attributable to this player alone | 1 | #183 |

**In the runner.**

| | golds | issue |
| --- | ---: | --- |
| The closed-form Ahem text-metrics model | 6 | #203 |
| Gestures dispatched through `ImageComposeScene` have no effect | 5 (+14 `trace:handled` checks) | #199 |
| The four transient-event channels, and scroll offsets | ~8 | #184 |

Two of these were nearly filed the other way round, which is the argument for the reference lane
existing at all: the collapsible and `StateLayout` divergences both *looked* like reporting gaps
until the pixels were checked.

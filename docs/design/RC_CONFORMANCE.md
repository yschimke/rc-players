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

## What the CMP player scores today

Measured against patch set 1 of the Gerrit change:

| | value |
| --- | ---: |
| Golds passed (core profile) | **1 / 241** |
| **Raster checks passed** | **385 / 633 (60.8%)** |
| Golds with no raster disagreement at all | **183 / 252** |
| Checks failed | 1125 / 1515 |

Those two blocks say opposite-looking things, and both are true. The gold-level pass rate is near
zero because a gold passes only when *every* check in it passes, and 88.5% of all failing diffs are
one of two structural reasons:

| cause | share of diffs |
| --- | ---: |
| `PROBE_NOT_IMPLEMENTED` — the runner cannot observe it | 58.5% |
| `STEP_NOT_RUN` — the runner cannot drive it | 30.0% |
| A real disagreement with the reference | 11.5% |

**Those are this runner's gaps, not the player's**, and they are reported as failures deliberately.
The guide is emphatic about it: a runner that skips what it cannot do produces a beautiful,
meaningless number, and one that reports a partial score with named gaps has told you something
true. The pixel result is the part that is already a real measurement — the CMP player draws 61% of
the corpus's raster comparisons pixel-identically to the AndroidX reference, with the corpus's own
Ahem face registered.

### What the corpus already found

Nine golds **error** rather than fail — genuine defects the corpus surfaced, not observability gaps:

| gold(s) | failure |
| --- | --- |
| `loom_macro_define_call`, `loom_macro_multi_instance`, `loom_nested_macros`, `loom_slot_blocks`, `loom_id_remapping_tiers` | `RcLinkException: Unmatched ContainerEnd` — the LOOM macro/slot operations do not link |
| `spacer_fixed_sizes`, `spacer_weighted` | `Unknown AndroidX box alignment horizontal=0 vertical=0` |
| `image_layout_sizing_options` | `RcColumnLayout requires LayoutComponentContent` |
| `path_tween_morph` | `Missing path 10` |

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

### The raster metric is ported exactly

`Pixelmatch.kt` is a direct port of the guide's §10 reference implementation — Vyšniauskas'
antialiased-pixel detector as implemented in `mapbox/pixelmatch`, applied after compositing both
images onto white. Each player computes its own verdict, so the algorithm is part of the contract:
an approximate reimplementation produces a number that is not comparable with anyone else's.

`PixelmatchTest` pins the guide's two self-checks, because a permissive `antialiased()` is silent —
every raster check still passes and the channel proves nothing. `RunnerAccountingTest` pins the
three ways a runner loses a check, all of which make the score look *better*.

## The work list

Ordered by how much of the corpus each unlocks, which is the order the guide recommends:

1. **The `tree` probe — 548 checks, 69% of golds.** It needs a seam on the CMP player exposing the
   laid-out component hierarchy. `Modifier.trackComponentGeometry` publishes geometry only for
   components the *document* binds a `ComponentValue` to, which is a small minority of nodes.
2. **The `float` / `int` / `color` / `text` probes — 190 checks.** `RcPlayerState` already holds all
   of it; the player exposes no host-visible accessor.
3. **The gesture and time step kinds** — `click`, `frame_sequence`, `advance_time`, `touch_*`,
   `clock_snapshot`, `theme`: 83 steps whose checks currently fail as `STEP_NOT_RUN`.
   `ImageComposeScene.sendPointerEvent` covers the gesture half.
4. **A second lane.** The vendored AndroidX JVM cut (`third_party/rc-embedded-player-jvm`) is the
   most diagnostic one to add: the corpus was generated by AndroidX, so that lane's score separates
   "our player is wrong" from "the corpus assumes something our player does differently".

Items 1–3 are all seams on `rc-player-compose`, and each is an ABI change gated by `checkKotlinAbi`.
That is the right shape for them — an inspection API a conformance harness depends on should be
reviewed, not discovered.

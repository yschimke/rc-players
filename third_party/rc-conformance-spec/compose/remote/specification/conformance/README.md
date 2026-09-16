# RemoteCompose Conformance Specification & Visual Audit Suite

This directory contains the canonical language-neutral conformance specifications, golden baselines, and player-agnostic visual audit report generators for RemoteCompose.

> [!IMPORTANT]
> **Writing a player and want to know how conformant it is?** Start with
> **[PLAYER_IMPLEMENTATION_GUIDE.md](PLAYER_IMPLEMENTATION_GUIDE.md)** — what to build, in
> payoff order, and the mistakes that silently produce a wrong score.
> [CONFORMANCE_FORMAT.md](CONFORMANCE_FORMAT.md) is the normative schema behind it.
>
> You do **not** need a converter or an authoring-format parser. Each gold carries its own
> compiled document in `document_base64`; you play that.

The corpus is **252 golds over 18 subsystems** (244 core profile, 8 extended), asserting 867
individual checks:

**Layout & Modifiers** · **Expressions** · **Particles** · **Wire Protocol & Loom** ·
**2D Canvas & Shaders** · **Clock & Time** · **Interactivity & Gestures** · **Text Operations** ·
**Color Theme** · **Data Operations** · **Path Operations** · **Shader Data & AGSL** ·
**Semantics & Accessibility** · **Autonomous Scheduling** · **Conditional Branching** ·
**Matrix Expressions** · **Text on Path & Anchoring** · **Animation Specifications**


---

## Architecture: Decoupled Audit Pipeline

The conformance testing architecture cleanly separates **player test execution** from **visual report generation**:

```
[ Player Engine Test Harness ]            [ AndroidX Specification Repo ]
   (one per implementation)              compose/remote/specification/conformance/
             │                                              │
             │ executes against tests/ & gold/              │
             ▼                                              │
  results/<player>/conformance-results.json ────────────────┤
                                                            ▼
                                                generate-audit-report.mjs
                                              (Zero external npm dependencies)
                                                            │
                                              ┌─────────────┴─────────────┐
                                              ▼                           ▼
                          results/<player>/conformance-audit.html   …/test-inspector.html
```

- **Player Engines**, in any language, are only responsible for running the conformance test suite and outputting a standardized `conformance-results.json`.
- **The Audit Report Generator** (`generate-audit-report.mjs`) lives directly in this AndroidX directory. It has **zero external npm dependencies** (uses only Node.js standard libraries) and converts any player's `conformance-results.json` into the self-contained visual audit report and interactive test inspector.

---

## 1. Recreate Gold Files and Overview

Run this command from the AndroidX repository root (`frameworks/support`):

```bash
PROJECT_PREFIX=:compose:remote: ./gradlew :compose:remote:remote-creation-core:test \
  --tests "*ConformanceGoldGeneratorTest*" \
  --rerun-tasks
```

### What this does
1. Reads each test in `tests/layout/`.
2. Encodes each test into a binary RemoteCompose document.
3. Calculates layout positions using the Android engine.
4. Writes expected component bounding boxes to `gold/layout/<test_name>.gold.json`.
5. Replays the **whole** gold corpus through the reference engine and measures JaCoCo coverage.
6. Writes those coverage figures to `coverage.json`.

It does **not** render `gold-overview.html`. That page covers all 18 subsystems, and this test
only generates three of them (layout, expressions, particles), so it has no way to describe the
rest. It publishes the coverage data — the one thing only a JVM with JaCoCo attached can
produce — and `generate-gold-overview.mjs` renders the page from the corpus plus that file.

> [!NOTE]
> The generator emits each gold's `format_version` / `timeline` / `checks` blocks directly, so
> a generation run leaves the corpus immediately runnable. Re-render the overview afterwards
> with `node generate-gold-overview.mjs` to pick up the refreshed `coverage.json`.

### Coverage option
Coverage measurement is enabled by default. To turn it off:
```bash
PROJECT_PREFIX=:compose:remote: ./gradlew :compose:remote:remote-creation-core:test \
  --tests "*ConformanceGoldGeneratorTest*" \
  -Dgenerate.gold.overview=false \
  --rerun-tasks
```

---

## 2. Run Layout Subsystem Coverage Tests

To run the dedicated layout subsystem unit tests:

```bash
PROJECT_PREFIX=:compose:remote: ./gradlew :compose:remote:remote-core:test \
  --tests "*LayoutSubsystemCoverageTest*" \
  --rerun-tasks
```

To run both new suites and update the combined coverage overview (>80%):

```bash
rm -f /tmp/jacoco.exec
PROJECT_PREFIX=:compose:remote: ./gradlew \
  :compose:remote:remote-core:test --tests "*LayoutSubsystemCoverageTest*" \
  :compose:remote:remote-creation-core:test --tests "*ConformanceGoldGeneratorTest*" \
  --rerun-tasks
```

Together, these suites achieve over 80% instruction coverage and over 82% line coverage across the layout subsystem (`androidx.compose.remote.core.operations.layout`).

---

## 3. Player-Agnostic Visual Audit Report Generation

The visual audit report generator lives directly in this AndroidX directory (`compose/remote/specification/conformance/`) and is **completely decoupled from any specific player implementation**. It has **zero external npm dependencies** (uses only Node.js built-in `fs` and `path` modules).

Any player implementation, in any language, only needs to execute the conformance test suite and output a standardized `conformance-results.json` into `results/<its-name>/`.

To generate the standalone visual audit report and interactive test inspector from any player's results file:

```bash
cd compose/remote/specification/conformance

# Render the results under results/<player>/, writing the reports alongside them:
node generate-audit-report.mjs --player my-player

# Open the report in your browser
open results/my-player/conformance-audit.html
```

---

## 4. What This Directory Owns (and What It Does Not)

This directory is the authoritative source for three things:

1. **The gold corpus** — `gold/` and `tests/`, plus the tooling that produces them
   (`ConformanceGoldGeneratorTest` in `remote-creation-core`, which derives each gold's
   portable `timeline` + `checks` form directly).
2. **The viewers** — `generate-audit-report.mjs`, `generate-test-inspector.mjs` and
   `generate-gold-overview.mjs`, which turn any player's `conformance-results.json` into the
   HTML reports.
3. **The specification** — `CONFORMANCE_FORMAT.md` and `PLAYER_IMPLEMENTATION_GUIDE.md`.

> [!IMPORTANT]
> **It owns no player-specific code, and must not acquire any.** There may be any number of
> third-party player implementations; this directory should not have to know that any of them
> exists. Nothing here imports a player or a player's dependencies.

**The runner is not here.** The program that drives a player across the corpus and emits
`conformance-results.json` has to construct that player's engine, so it is player-specific
and lives in that player's own repository — one per implementation, maintained by whoever
maintains the player. `PLAYER_IMPLEMENTATION_GUIDE.md` specifies what one must do.

A player reads `gold/` in place — never a vendored copy — and writes its results back here,
where the viewers pick them up.

### Where results live

Results are namespaced by player, because there is no such thing as *the* results file once
more than one implementation is being measured:

```
results/
  <player>/
    conformance-results.json    written by that player's runner
    conformance-audit.html      generated from it
    test-inspector.html         generated from it
```

`gold-overview.html` and `gold-overview.json` stay at the top level: they describe the corpus,
not any player, and are the same whoever is being tested. So does `coverage.json`, which the
Java gold generator publishes and the overview renders — it is an **input** to the viewers, not
one of their outputs.

### Running the viewers

Everything here has **no npm dependencies** and needs no configuration: the corpus is a sibling
of the scripts, and results are found under `results/`.

```bash
cd compose/remote/specification/conformance

node generate-audit-report.mjs     # conformance-audit.html + test-inspector.html
node generate-gold-overview.mjs    # gold-overview.html + gold-overview.json

open results/<player>/conformance-audit.html
```

With exactly one player's results present, the viewers use them. With several, they will not
guess — pass `--player NAME`:

```bash
node generate-audit-report.mjs --player my-player
node generate-test-inspector.mjs --player my-player
```

Both also accept `--results FILE` to render a results file from anywhere, and `--out DIR|FILE`
to write the reports somewhere other than beside the results. `generate-gold-overview.mjs`
takes `--out DIR` too, which is how you produce the overview without writing into the spec
tree.


---

## 5. Test Types

The suite verifies four categories of layout behavior:

1. **Static layouts**: verify single-pass bounds and positions across all managers and modifiers.
2. **Dynamic resize**: change viewport size in sequence (`resize_steps`) to verify flow reflow, priority collapsing, and weight scaling.
3. **Layout animation**: measure positions over time across multiple frames (`capture_frames`) to verify motion curves and anchor shifts.
4. **Touch interactions**: apply down, drag, up, and click gestures (`interactions`) to verify scrolling offsets and reactive updates.

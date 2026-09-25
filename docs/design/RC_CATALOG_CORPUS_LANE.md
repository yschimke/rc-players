# Scoring the players against the wear-m3-catalog sticker sheet

**Status:** option A is built — `scripts/check-native-uikit-corpus.sh`, wired into the macOS lane
on pushes to `main`. Option B, the eighth column on the catalog's own page, is still a proposal.

[`preview.coo.ee/remote-m3/compare?format=rc`](https://preview.coo.ee/remote-m3/compare?format=rc)
is a wall of ~700 Remote Compose documents drawn by seven players at once, each row scored against
the Figma node it reproduces. Two of those columns are already ours — `RC · cmp-jvm player` and
`RC · cmp-wasm player` — and the harnesses behind the Android and JVM columns live in **this**
repository. The native Swift/UIKit player is the one Remote Compose player with no column.

This says how to fix that, and argues the two halves of the job should be done in the other order
than the question suggests.

## What already exists, on both sides

The important discovery is that neither side needs a new contract; they already share one.

**This repository defines the contract.** A lane is a directory of `<id>.rc` documents plus a
`manifest.json` of `{id, width, height, density, androidCompatibility}` in, and `<id>.png` —
or `<id>.error`, or `<id>.png` beside `<id>.unsupported` — out.
[`validate-results.mjs`](../../scripts/rc-operation-conformance/validate-results.mjs) holds a lane
to exactly one primary result per manifest id, and
[`rc-multi-lane-score.mjs`](../../scripts/design-artifacts/rc-multi-lane-score.mjs) pixel-diffs any
two lanes that implement it. Five harnesses here already do: `RcCmpRenderHarness`,
`RcEmbeddedRenderHarness`, `RcAndroidxEmbeddedRenderHarness`, `RcViewPlayerRenderHarness`, and —
this is the part that makes the whole proposal cheap —
[`render-native-uikit-lane.sh`](../../scripts/render-native-uikit-lane.sh) with
[`NativeComparisonHarness.swift`](../../samples/apple-player/RemoteComposePlayer/NativeComparisonHarness.swift),
which already reads `density` and `androidCompatibility` per entry and already writes `.error` and
`.unsupported` files.

**The catalog drives its columns from those harnesses.** `wear-m3-catalog`'s
`.github/workflows/design-artifacts.yml` turns columns on with `rc-cmp-wasm-lane`,
`rc-embedded-lane` and a JVM-embedded lane input (whose harness went with the vendored player's JVM
cut, removed on 2026-09-25) to a reusable workflow in
`yschimke/compose-ai-tools`, and its own comment records that those harnesses "had moved to
yschimke/rc-players, where the workflow was not looking".

**The documents are fetchable as bytes.** The compare page carries
`data-rc-neutral="/remote-m3/render/<id>.rc"` per row, and that URL serves the real wire document —
`00 048c0001 …`, a modern header. `catalog.json` on the `design-artifacts/remote-m3` delivery
branch carries every sticker's id and rendered size, which is the manifest. So the corpus is a
data dependency on a pinned commit, not a scraping exercise.

## The corpus, measured

`scripts/rc-catalog-corpus/fetch-corpus.sh` materialises it. At pin `60c4c8af`:

| | |
| --- | --- |
| Documents | **701** across **60** components |
| Total size | 2.9 MB (~4 KB each) |
| Sizes | 454×200 (353), 454×400 (60), then 384/408/432/450 squares |
| Density at generation | 2.0 on 698; three declare none |
| Density behaviour | `LEGACY` (0) on all 701 |
| Distinct opcodes used | **58** |

The manifest is built from each document's **own header**, not from the catalog's rendered image
sizes. They usually agree; the three `widgetcontainer-*` stickers do not, because the catalog frames
them and publishes 567×326 around a 525×283 document. A lane has to draw the document at the size
the document states, or the score measures the manifest rather than the players. Those same three
declare no `DOC_DENSITY_AT_GENERATION`; the manifest supplies 2.0 anyway, since every sticker on
this sheet is a 2.0 Wear capture and leaving them to resolve dp at 1.0 in one lane and 2.0 in
another would be a difference with no meaning.

Two things measured locally, both of which decide the shape of the work:

**The corpus is a fair target.** All 701 documents decode with `RcDocumentCodec` (0 failures) and
all 701 render in the CMP JVM lane with **0 errors and 0 unsupported**. Nothing here is exotic or
broken upstream — a native failure will be a native bug.

**73% of it would reach the native renderer today.** The Swift core implements 84 opcodes, and is
missing exactly five of the 58 this corpus uses. Because no document uses two of them, each
opcode's fix unblocks precisely its own count:

| Opcode | Constant | Documents | Components |
| --- | --- | --- | --- |
| 108 | `MODIFIER_CLIP_RECT` | 64 | `edgebutton` |
| 227 | `VALUE_FLOAT_EXPRESSION_CHANGE_ACTION` | 62 | `button-compact`, `textbutton`, `slider`, `stepper`, the five `iconbutton-*` |
| 224 | `MODIFIER_GRAPHICS_LAYER` | 58 | `linearprogressindicator`, `pageindicator-vertical` |
| 212 | `VALUE_INTEGER_CHANGE_ACTION` | 4 | `radiobutton` |
| 196 | `COLOR_THEME` | 1 | `theme-systemthemeswatches` |
| | **total blocked** | **189 of 701** | |

That table is the backlog, already ranked by documents unblocked per opcode, and it is the answer
to "use each doc to flush out rendering bugs": 512 documents reach the renderer immediately and can
be scored, and the other 189 have a named cause apiece rather than a shrug.

## Two options, and why to do the smaller one first

### A. Compare against them — a corpus lane in this repository

Fetch the corpus, run it through `render-native-uikit-lane.sh` and `RcCmpRenderHarness`, score the
two with `rc-multi-lane-score.mjs`. This is entirely inside this repository, needs no change to any
other, and runs on the macOS job that already exists.

It is also the half that actually finds bugs. A per-document native-vs-CMP score over 701
documents is a far denser signal than the four fixtures
[`check-native-uikit-comparison.sh`](../../scripts/check-native-uikit-comparison.sh) scores today,
and the corpus is adversarial in the useful way: it is real M3 component output, authored by a
different team, against a player that has never seen it.

### B. Include the renderer — an eighth column on the page

This needs an `rc-native-uikit-lane` input on the reusable workflow in
`yschimke/compose-ai-tools`, and it runs into one hard fact: **the catalog's publish job runs on
`ubuntu-latest`, and the native lane needs macOS and an iOS simulator.** Every other player column
is a Gradle test or a headless browser; this one is not. So B is not an input toggle, it is a job
split — a macOS job renders the lane and uploads its PNGs, and the ubuntu publish job consumes them
as a pre-rendered artifact.

That is a real design change to somebody else's workflow, and its value is presentation. **Do A
first.** A produces the artifact B would publish, so A is not thrown away; and if A shows the
native player failing a third of the sheet, an eighth column is a wall of red rather than a
comparison.

## What A cost

Four changes, all landed:

1. **`scripts/rc-catalog-corpus/fetch-corpus.sh`** — validated end to end: fetch → manifest →
   `RcCmpRenderHarness` → `validate-results.mjs` reports
   `cmp-jvm: 701/701 png, 0 error, 0 unsupported`. The pin lives in
   `scripts/rc-catalog-corpus/pin`; the corpus itself is not committed, because it is ~700 files of
   generated output that regenerate upstream and a stale copy would silently score the wrong
   sheet. A corpus already on disk is reused, so a local run iterates without re-fetching.
2. **`scripts/check-native-uikit-corpus.sh`**, beside `check-native-uikit-comparison.sh`, running
   the same two lanes over the corpus instead of four local fixtures — and printing the headline
   this lane exists for: how many documents the native player rendered, how many it declined, and
   how many it drew while naming an operation it does not implement, each grouped by component
   because a flat list of ~700 stickers is unreadable and the component is what a fix is scoped to.
3. **The harness deadline now scales with the manifest.** `render-native-uikit-lane.sh` waited 90
   seconds for `done.json` — a constant sized for four documents, which the corpus would have
   tripped partway through with nothing to say about why. It is now 30 seconds of fixed cost plus a
   second per document: far more than a render takes, which is the point, since it exists to catch
   a harness that has stopped rather than to police its speed.
4. **It is enrichment, not a gate.** A document the native player declines is a finding to read,
   not a build to fail, and a corpus that cannot be fetched is reported and skipped — the sheet is
   generated output this repository does not control, served by a host whose render lane has been
   disabled outright before.

   The script's own fail-soft was not enough to make that true: a crash in either lane still exited
   non-zero, which would have taken `main` red on a step that is explicitly not a gate. The CI step
   carries `continue-on-error: true`, so it still shows as failed and still says why, but cannot
   fail the job — which is the difference between evidence and a gate.

   **It runs on pushes to `main`, not on pull requests.** It adds about ten minutes to a job that
   has hit its 90-minute ceiling once already, and a non-gating score does not need to be on the
   path of every pull request to do its job: main produces the findings continuously, and the
   script runs locally unchanged.

Not in scope for A: scoring against the Figma baseline. The catalog owns that comparison and
already does it well. Ours is player-versus-player, where CMP JVM is the reference because it is
the lane with a rendering test suite behind it.

## What the lane scores: one document per component

The lane scores **60 documents, one per component**, not all 701. `RC_CATALOG_SAMPLE=all` restores
the full sheet. Two reasons, and the second is the one that matters.

**Cost.** The CMP JVM lane renders all 701 in 43 seconds. The native UIKit lane ran 55 minutes
without finishing and took a 90-minute macOS job down with it, so the full sheet was not merely
slow — it was unaffordable on a shared job that also has an XCFramework link, four simulator
validations and a screenshot to get through.

**Balance.** The sheet is not evenly spread:

| Component | Documents |
| --- | --- |
| `pageindicator-vertical` | 100 |
| `circularprogressindicator` | 65 |
| `edgebutton` | 64 |
| `pageindicator-horizontal` | 50 |
| …26 components | 1 each |

Four components are 279 of the 701. A score over the whole sheet is therefore mostly a score of
those four: "14% of documents failed" could mean one component is broken or fourteen are, and the
two have completely different backlogs. One document per component answers *which components can
this player draw*, which is the question a fix is scoped to — and it makes every component count
once, so a rare component is as visible as a well-populated one.

The pick is deterministic, so two runs are comparable: the `ideal__default` variant when a
component publishes one, otherwise any `ideal` variant, otherwise the first by name. Deliberately
not random — a sample that moves between runs turns every change in the score into an
investigation of the sample.

The full sheet stays committed and stays the source the sample is cut from, so widening the score
later is a flag rather than a re-fetch.

## The one weak link, and what it cost

The documents used to come from `preview.coo.ee` at request time, which put a live server in this
repository's CI path. It broke immediately, and in the way that is hardest to argue with.

The first corpus run fetched all 701 documents at generation `60c4c8af`. Ninety minutes later the
same pinned URLs returned 404 for all 701 — the server publishes exactly one generation, the current
one, and answers `?gen=<sha>` for anything else with a 409 reading "this catalog has moved on". So
the pin bought no reproducibility at all: it named a sheet that could not be re-fetched, and the
score it named could not be repeated, let alone reproduced. During the session that fixed this, the
compare page itself went from 200 to 404 between two runs of the refresh script.

**The documents are now committed**, under `scripts/rc-catalog-corpus/corpus`: 701 `.rc` files,
1.7 MB, with the generation recorded beside them in `PIN`. CI reads those bytes and contacts nothing.
`scripts/rc-catalog-corpus/refresh-corpus.sh` is the update process, run by hand, and it is the only
thing in the repository that may reach the catalog at all.

The refresh prefers the delivery branch and falls back to the server, so the weak link closes itself
the moment the catalog publishes wire bytes. That is still one input on the catalog's side and still
nearly free there: the workflow already publishes `documents/<id>.rc.json` — the operation stream as
text — under `rc-document-json: true`, and publishing `documents/<id>.rc` beside it is the same step
with a different serialiser. As of generation `40b842d5` the branch carries the rendered PNGs and the
JSON but no wire bytes, so the refresh still uses the server; the day it does not, the refresh
becomes a pure `git fetch` of a pinned commit with no live dependency anywhere, and it needs no
change here to do so. Worth asking for: the bytes on the branch also make the text form verifiable
against what actually drew the pixels.

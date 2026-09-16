# Scoring the players against the wear-m3-catalog sticker sheet

**Status:** proposal. The corpus fetch (`scripts/rc-catalog-corpus/fetch-corpus.sh`) and the
measurements below are real and reproducible today; the lanes and CI wiring are not built yet.

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
`rc-embedded-lane` and `rc-embedded-jvm-lane` inputs to a reusable workflow in
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

## What A costs

Four changes, none large:

1. **`scripts/rc-catalog-corpus/fetch-corpus.sh`** — written, and validated end to end: fetch →
   manifest → `RcCmpRenderHarness` → `validate-results.mjs` reports
   `cmp-jvm: 701/701 png, 0 error, 0 unsupported`. The pin lives in
   `scripts/rc-catalog-corpus/pin`; the corpus itself is not committed, because it is ~700 files of
   generated output that regenerate upstream and a stale copy would silently score the wrong
   sheet.
2. **A corpus comparison script** beside `check-native-uikit-comparison.sh`, running the same two
   lanes over the fetched corpus instead of four local fixtures.
3. **Raise the harness deadline.** `render-native-uikit-lane.sh` waits 90 seconds for
   `done.json` — a constant sized for four documents. It has to scale with the manifest count.
4. **Decide the gate.** Start as *enrichment*: report the score, do not fail the build. A corpus
   this repository does not control must not be able to break its CI by changing upstream — that is
   what the pin is for, and the pin should move in a reviewable commit that shows the score moving
   with it.

Not in scope for A: scoring against the Figma baseline. The catalog owns that comparison and
already does it well. Ours is player-versus-player, where CMP JVM is the reference because it is
the lane with a rendering test suite behind it.

## The one weak link

The documents come from `preview.coo.ee` at request time, which puts a live server in this
repository's CI path — and the `PROVENANCE.md` note about a `NoSuchMethodError` disabling that
server's whole render lane is a reminder that it does go down.

The fix is one input on the catalog's side, and it is nearly free there: that workflow already
publishes `documents/<id>.rc.json` — the operation stream as text — to the delivery branch under
`rc-document-json: true`. Publishing `documents/<id>.rc` beside it is the same step with a
different serialiser, and it would turn this corpus into a `git fetch` of a pinned commit with no
live dependency at all. Worth asking for before A lands, and worth doing even if A never happens:
the bytes on the branch make the text form verifiable against what actually drew the pixels.

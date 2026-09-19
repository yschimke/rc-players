# Vendored: RemoteCompose conformance specification & gold corpus

The language-neutral **conformance specification** for Remote Compose — 17 `AREA-*.md` normative
area documents, the 252-gold conformance corpus, and the player-agnostic HTML report generators.

This is the artefact that lets the question *"how conformant is this player?"* have a number
attached to it, for every lane in this repository, against one reference rather than against each
other.

## Upstream

- Repository: `platform/frameworks/support` (AOSP), branch `androidx-main`
- Change: <https://android-review.googlesource.com/c/platform/frameworks/support/+/4302372>
  — *"Specification documents"*, by Nicolas Roard
- Revision: `8b6a6f46f8b350f50305ca40a99861b25b6a0198` (patch set 1, 2026-09-09)
- Change-Id: `I401b11c5e656b1ca8feb3e996ba2efb5bf2fdf4a`
- Upstream status at vendoring time: **`NEW` — open, not merged**
- License: Apache-2.0

## Why this is vendored, and why on its own branch

`PLAYER_IMPLEMENTATION_GUIDE.md` §1 says, correctly, *"read `gold/` in place, do not vendor a copy"*
— a copy drifts silently and a runner keeps passing against a stale expectation.

It cannot be read in place yet. The change is **unmerged**; there is no public git ref carrying it,
only a Gerrit patch set, and the only way to obtain the corpus at all is to reconstruct it from that
patch. So the copy is the access mechanism, not a convenience, and it is kept where the drift is
visible:

- it lives on the long-lived branch **`vendor/androidx-rc-conformance`**, never on `main`, so a
  refresh is one commit on one branch with a readable diff rather than noise inside feature work;
- the runners take the corpus directory as **configuration** (`--spec-dir`, or `RC_SPEC_DIR`), as the
  guide requires — nothing resolves it by a hard-coded in-repo path;
- `refresh.sh` re-derives the whole tree from Gerrit, so "has upstream moved?" is a command, not an
  archaeology exercise.

When the change merges into `androidx-main`, this directory should be replaced by a checkout of the
real ref and this file reduced to a pointer.

## Layout

The AOSP paths are mirrored exactly, so a refresh is a straight re-extract with no path rewriting:

```
compose/remote/specification/              AREA-01..17 + AREAS.md — the normative area specs
compose/remote/specification/conformance/  the corpus, the guides, the report generators
  gold/            252 golds, 18 subsystems, each carrying its own compiled document_base64
  tests/           the authoring-side sources the golds were generated from
  fonts/Ahem.ttf   the corpus typeface — required, see the guide §9
  results/         published results, namespaced by player
  *.mjs            the player-agnostic report generators (zero npm dependencies)
compose/remote/remote-core/…               LayoutSubsystemCoverageTest.java  (AOSP-side, FYI)
compose/remote/remote-creation-core/…      ConformanceGoldGeneratorTest.java (AOSP-side, FYI)
```

The two Java files are carried for completeness — they are the AOSP-side gold generator and layout
coverage suite. They are not built here; this repository has no `frameworks/support` to build them
against.

## Refreshing

```bash
third_party/rc-conformance-spec/refresh.sh                 # latest patch set of change 4302372
third_party/rc-conformance-spec/refresh.sh --change 4302372 --revision <sha>
```

It rewrites the tree in place; `git diff` afterwards is the upstream delta. Commit it on this branch,
then re-run the scorecard lane to see which way the numbers moved.

### 2026-09-18 — regenerated gold rasters, and the correction applied here

The 247 raster baselines under `gold/` were replaced from Gerrit
[change 4305834](https://android-review.googlesource.com/c/platform/frameworks/support/+/4305834),
patch set 6 — *"Regenerate conformance golden rasters with native Skia rendering"* — which
regenerates them with `@GraphicsMode(GraphicsMode.Mode.NATIVE)` instead of the software renderer
whose output the old baselines recorded. The initial frames are a genuine improvement: several
software baselines were stale (missing children, blank particle frames), and the native renders
agree with every independent implementation at 0.96–0.99.

**178 frames are then corrected back here, because the regeneration captured them through a player
that does not resize.** The harness's `UPDATE_GOLDENS=true` path renders through the View player
(`remote-player-view`), whose `RemoteComposeView.onMeasure` sizes itself to
`mDocument.getWidth()/getHeight()` rather than to its parent — so a `resize` step keeps painting the
*initial* document size and the newly exposed area stays white. Measured over the whole corpus:
every independent rendering (CMP, the embedded player, the TypeScript player, the old software
recordings) agrees with every other on resize at 0.89–0.98, and the native recording is the only
outlier at 0.56–0.64. A View player *constructed* at the target viewport renders the old gold
pixel-identically (0 px), and the embedded player resized live does too.

The correction, applied to this branch's copy:

* **175 resize-affected frames** (any step at or after a `resize`, including animation frames behind
  a resize trigger) restored to the previous recordings, which re-lay-out at the step's viewport;
* **3 animation frames** (`animation_state_row_to_column`, `animation_state_3_states`,
  `animation_state_transition` at `frame_0`) restored — regressions where CMP agrees with the old
  gold at 0.68–0.79 and with the new one at only 0.20–0.37;
* the remaining **144 native-recorded frames kept**, including the fixes above.

This is a **local correction pending upstream**: the harness patches that record with the embedded
player (`UPDATE_GOLDENS=embedded`) or re-create the View player per viewport are prepared, and the
analysis is committed under `renders/conformance-goldens/` on the `agent/cl3-golden-evidence`
branch. When a corrected patch set lands, re-run `refresh.sh` and this note becomes history.

### 2026-09-18 — regenerated gold rasters (original note, superseded by the correction above)

The 247 raster baselines under `gold/` were replaced from Gerrit
[change 4305834](https://android-review.googlesource.com/c/platform/frameworks/support/+/4305834),
patch set 6 — *"Regenerate conformance golden rasters with native Skia rendering"* — which
regenerates them with `@GraphicsMode(GraphicsMode.Mode.NATIVE)` instead of the software renderer
whose output the old baselines recorded. A gold whose baseline was drawn by a renderer nobody ships
is a wrong expectation: the players were being marked down for drawing correctly.

The patch touches only `checks[].expect` raster data URIs, except `text_merge_and_transform`, whose
`document_base64` also moves (upstream's text-merge byte fix — imported with its raster, so the pair
stays consistent). No timeline, check or parameter changes. The change is stacked on a newer
revision of the corpus than the patch set this branch vendors (change 4308820, "testing only"), so
each patched gold was verified against ours first: **251 of 252 documents are byte-identical apart
from their raster baselines**, which is what makes the raster replacement applicable rather than a
mismatch. The upstream change is unmerged; when it lands, `refresh.sh` is the path and this note
becomes history.

## Local modifications

**None.** This is a verbatim copy of the patch set. Keep it that way: an edit here becomes a
difference between the score this repository reports and the score anyone else would measure, which
is the one property the corpus exists to provide.

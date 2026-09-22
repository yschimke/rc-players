# Vendored: RemoteCompose conformance specification & gold corpus

The language-neutral **conformance specification** for Remote Compose — 17 `AREA-*.md` normative
area documents, the 364-gold conformance corpus, and the player-agnostic HTML report generators.

This is the artefact that lets the question *"how conformant is this player?"* have a number
attached to it, for every lane in this repository, against one reference rather than against each
other.

## Upstream

- Repository: `platform/frameworks/support` (AOSP), branch `androidx-main`
- Change: <https://android-review.googlesource.com/c/platform/frameworks/support/+/4302372>
  — *"Specification documents"*, by Nicolas Roard
- Revision: `bf2ca73a769d60cd11486a856351515d6e317b23` (patch set 2, 2026-09-22)
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
  gold/            364 golds, 18 subsystems, each carrying its own compiled document_base64
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

### 2026-09-22 — use the CL's gold images unchanged

This refresh adopts patch set 2's corpus and its gold images verbatim: 364 golds and 633 raster
checks (612 advisory). It deliberately drops the branch's earlier local raster correction rather
than mixing an older, locally reconstructed image set with the new CL's documents, timelines and
expectations.

The copied generator exercises `CoreDocument` with a headless `RemoteContext`; it does not select
or execute the Compose embedded player to record this corpus. The embedded player remains a useful
comparison lane, but changing the recorder would produce a different expectation set. Use this CL
baseline first; evaluate an embedded-player recorder separately with a complete before/after corpus
comparison rather than silently substituting individual images.

## Local modifications

**None.** This is a verbatim copy of patch set 2. Keep it that way: an edit to a gold becomes a
difference between the score this repository reports and the score anyone else would measure, which
is the one property the corpus exists to provide.

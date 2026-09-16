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

## Local modifications

**None.** This is a verbatim copy of the patch set. Keep it that way: an edit here becomes a
difference between the score this repository reports and the score anyone else would measure, which
is the one property the corpus exists to provide.

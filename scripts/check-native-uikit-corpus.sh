#!/usr/bin/env bash
# Score the native UIKit player against the CMP JVM player over the wear-m3-catalog sticker sheet —
# ~700 real Material 3 documents, authored by another team, that this player has never seen.
#
# This is ENRICHMENT, not a gate. The corpus is generated output this repository does not control,
# fetched at run time from a live server, and a document the native player declines is a finding to
# read rather than a build to fail. So the exit status covers only whether the comparison ran: a
# corpus that cannot be fetched is reported and skipped, and a lane that declines documents still
# publishes its score. See docs/design/RC_CATALOG_CORPUS_LANE.md.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lanes_dir="${1:-$repo_root/build/native-uikit-corpus}"
corpus="${RC_CATALOG_CORPUS:-$repo_root/build/rc-catalog-corpus}"
mkdir -p "$lanes_dir"
lanes_dir="$(cd "$lanes_dir" && pwd)"

# A corpus already on disk is reused, so a local run can iterate without re-fetching ~700 documents
# and a CI job can stage it however it likes.
if [ -f "$corpus/manifest.json" ]; then
  echo "==> reusing the corpus at $corpus"
else
  echo "==> fetching the catalog corpus"
  if ! "$repo_root/scripts/rc-catalog-corpus/fetch-corpus.sh" "$corpus"; then
    # preview.coo.ee is a live dependency, and its render lane has been disabled outright before
    # (see third_party/rc-embedded-player/PROVENANCE.md). An outage there must not read as a
    # regression here.
    echo "warning: the catalog corpus could not be fetched; skipping the corpus comparison" >&2
    exit 0
  fi
fi

documents="$(python3 -c 'import json, sys; print(len(json.load(open(sys.argv[1]))))' \
  "$corpus/manifest.json")"
echo "==> $documents documents, catalog pin $(tr -d '[:space:]' < "$corpus/PIN")"

rm -rf "$lanes_dir/cmp-jvm" "$lanes_dir/native-uikit"
mkdir -p "$lanes_dir/cmp-jvm"

echo "==> CMP JVM corpus lane"
"$repo_root/gradlew" --quiet :rc-player-compose:jvmTest --rerun \
  --tests '*RcCmpRenderHarness*' \
  "-Prc.cmp.input=$corpus" \
  "-Prc.cmp.output=$lanes_dir/cmp-jvm"

echo "==> native UIKit corpus lane"
# A stalled lane still leaves what it rendered behind, and on a ~700-document corpus that partial
# score is the finding — "480 of 701 before it stopped" tells you far more than a bare failure. So
# the lane's exit status is recorded rather than propagated, and the run continues over whatever is
# on disk; only the per-document comparison, which needs both lanes complete, is skipped.
native_complete=true
if ! "$repo_root/scripts/render-native-uikit-lane.sh" "$corpus" "$lanes_dir/native-uikit"; then
  native_complete=false
  echo "warning: the native UIKit lane did not finish; scoring its partial output" >&2
fi

if [ "$native_complete" = true ]; then
  node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" \
    results "$corpus" "$lanes_dir" cmp-jvm native-uikit
fi
if [ ! -d "$repo_root/scripts/design-artifacts/node_modules/pixelmatch" ]; then
  npm --prefix "$repo_root/scripts/design-artifacts" ci --no-audit --no-fund --silent
fi
node "$repo_root/scripts/design-artifacts/rc-multi-lane-score.mjs" \
  "$lanes_dir" cmp-jvm native-uikit --json "$lanes_dir/comparison.json" || \
  echo "warning: scoring the partial corpus output failed" >&2

# The headline the design asks this lane to produce: how much of a real Material 3 sheet the native
# player draws at all, and what it declines. `.error` is a document it refused; `.unsupported` is
# one it drew while naming an operation it does not implement. Both are the backlog.
python3 - "$corpus" "$lanes_dir" <<'PY'
import json, pathlib, sys

corpus, lanes = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
total = len(json.loads(corpus.joinpath("manifest.json").read_text()))
native = lanes / "native-uikit"
errors = sorted(p.stem for p in native.glob("*.error"))
unsupported = sorted(p.stem for p in native.glob("*.unsupported"))
# Counted, not inferred: a stalled lane attempts only part of the manifest, and `total - errors`
# would silently score every document it never reached as rendered.
rendered = len(list(native.glob("*.png")))
attempted = rendered + len(errors)
print(f"native UIKit corpus: {rendered}/{total} rendered, {len(errors)} declined, "
      f"{len(unsupported)} with unsupported operations")
if attempted < total:
    print(f"  incomplete: the lane attempted {attempted} of {total} documents")
for label, names in (("declined", errors), ("unsupported", unsupported)):
    if not names:
        continue
    # Grouped by component rather than listed one by one: ~700 stickers across 60 components means
    # a flat list is unreadable, and the component is what a fix is scoped to anyway.
    components = {}
    for name in names:
        components.setdefault(name.split("__")[0], []).append(name)
    summary = ", ".join(
        f"{component} ({len(items)})" for component, items in sorted(components.items())
    )
    print(f"  {label}: {summary}")
PY
echo "native UIKit corpus comparison: $lanes_dir/comparison.json"

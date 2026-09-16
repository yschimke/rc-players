#!/usr/bin/env bash
# Score the native UIKit player against the CMP JVM player over the wear-m3-catalog sticker sheet —
# ~700 real Material 3 documents, authored by another team, that this player has never seen.
#
# This is ENRICHMENT, not a gate. The corpus is generated output this repository does not control,
# and a document the native player declines is a finding to read rather than a build to fail. So the
# exit status covers only whether the comparison ran, and a lane that declines documents still
# publishes its score. See docs/design/RC_CATALOG_CORPUS_LANE.md.
#
# The documents are committed under scripts/rc-catalog-corpus/corpus and this reads them from disk.
# Nothing here contacts the network: the server publishes only its current generation, so a pinned
# URL stops resolving the moment the catalog moves — which it did, ninety minutes after the first
# corpus run, turning 701 fetched documents into 701 404s. scripts/rc-catalog-corpus/refresh-corpus.sh
# is how the committed copy moves, run by hand.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lanes_dir="${1:-$repo_root/build/native-uikit-corpus}"
corpus="${RC_CATALOG_CORPUS:-$repo_root/scripts/rc-catalog-corpus/corpus}"
mkdir -p "$lanes_dir"
lanes_dir="$(cd "$lanes_dir" && pwd)"

if [ ! -f "$corpus/manifest.json" ]; then
  echo "error: no corpus at $corpus; run scripts/rc-catalog-corpus/refresh-corpus.sh" >&2
  exit 1
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
manifest = json.loads(corpus.joinpath("manifest.json").read_text())
total = len(manifest)
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
    # Which document it stopped ON, not just how many it managed. The harness works the manifest in
    # order, so the first entry with no result is the one it was on when it stopped — and on a
    # stall that document is the prime suspect, which a count alone never names.
    produced = {p.stem for p in native.iterdir() if p.suffix in {".png", ".error", ".unsupported"}}
    for position, entry in enumerate(manifest, start=1):
        if entry["id"] not in produced:
            print(f"  stopped at #{position} of {total}: {entry['id']} "
                  f"({entry['width']}x{entry['height']} @ {entry['density']})")
            break

# The reasons, not just the counts. A component grouping says which stickers the player refused; it
# never says what it refused them for, and that is the whole content of the finding. Grouped by
# message because ~700 documents across 60 components produce a handful of distinct causes, and one
# example id per cause is what makes a cause reproducible.
reasons: dict[str, list[str]] = {}
for path in sorted(native.glob("*.error")):
    reason = path.read_text().strip().splitlines()
    reasons.setdefault(reason[0].strip() if reason else "(empty)", []).append(path.stem)
if reasons:
    print(f"  {len(reasons)} distinct failure reason(s):")
    for reason, names in sorted(reasons.items(), key=lambda item: -len(item[1])):
        print(f"    {len(names):4d}  {reason}")
        print(f"          e.g. {names[0]}")
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

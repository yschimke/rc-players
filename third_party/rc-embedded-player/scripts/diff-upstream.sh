#!/usr/bin/env bash
# Diffs this vendored copy against an androidx checkout, with the package rewrite and import order
# normalised on both sides, so the output is exactly the local patches listed in PROVENANCE.md.
#
# Usage: third_party/rc-embedded-player/scripts/diff-upstream.sh <androidx checkout>
set -euo pipefail

if [ $# -ne 1 ]; then
  sed -n '2,5p' "$0" >&2
  exit 2
fi

module_dir="$(cd "$(dirname "$0")/.." && pwd)"
upstream="$1/compose/remote/remote-player-compose/src/main/java/androidx/compose/remote/player/compose"
ours="$module_dir/src/main/kotlin/ee/schimke/composeai/rcembedded/player"
[ -d "$upstream/embedded" ] || { echo "error: no embedded player under $upstream" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Rename upstream's packages, then sort each file's import block, on both sides.
normalise() {
  sed -e 's/androidx\.compose\.remote\.player\.compose\.embedded/ee.schimke.composeai.rcembedded.player/g' \
    -e 's/androidx\.compose\.remote\.player\.compose\.utils/ee.schimke.composeai.rcembedded.player.utils/g' "$1" |
    awk '/^import / { imports[n++] = $0; next }
         n && !flushed { cmd = "sort"; for (i = 0; i < n; i++) print imports[i] | cmd; close(cmd); flushed = 1 }
         { print }
         END { if (n && !flushed) for (i = 0; i < n; i++) print imports[i] }'
}

mkdir -p "$work/upstream" "$work/ours"
(cd "$upstream/embedded" && find . -name '*.kt') | while read -r f; do
  mkdir -p "$work/upstream/$(dirname "$f")"
  normalise "$upstream/embedded/$f" >"$work/upstream/$f"
done
(cd "$upstream/utils" && find . -name '*.kt') | while read -r f; do
  mkdir -p "$work/upstream/utils/$(dirname "$f")"
  normalise "$upstream/utils/$f" >"$work/upstream/utils/$f"
done
(cd "$ours" && find . -name '*.kt') | while read -r f; do
  mkdir -p "$work/ours/$(dirname "$f")"
  normalise "$ours/$f" >"$work/ours/$f"
done

cd "$work"
diff -ru upstream ours || true

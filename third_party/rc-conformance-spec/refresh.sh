#!/usr/bin/env bash
# Re-derive third_party/rc-conformance-spec/ from its upstream Gerrit change.
#
# The change is unmerged, so there is no ref to fetch: the tree is reconstructed from the patch set,
# plus a direct content fetch for each binary file, which Gerrit's patch output cannot carry.
#
#   ./refresh.sh                                  latest patch set of the pinned change
#   ./refresh.sh --change 4302372 --revision SHA   a specific change / revision
set -euo pipefail

CHANGE=4302372
REVISION=
while [[ $# -gt 0 ]]; do
  case "$1" in
    --change)   CHANGE="$2";   shift 2 ;;
    --revision) REVISION="$2"; shift 2 ;;
    -h|--help)  sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

HOST=https://android-review.googlesource.com
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> resolving change $CHANGE"
# Gerrit prefixes every JSON response with )]}' — strip it.
curl -fsSL "$HOST/changes/$CHANGE/detail?o=ALL_REVISIONS&o=ALL_FILES" | tail -c +6 > "$WORK/detail.json"

SHA="$(REVISION="$REVISION" python3 "$HERE/refresh_helper.py" revision "$WORK/detail.json")"
echo "    revision $SHA"
python3 "$HERE/refresh_helper.py" describe "$WORK/detail.json"

echo "==> fetching patch"
curl -fsSL -o "$WORK/patch.zip" "$HOST/changes/$CHANGE/revisions/$SHA/patch?zip"
unzip -q -o "$WORK/patch.zip" -d "$WORK"
DIFF="$(ls "$WORK"/*.diff)"

echo "==> applying"
mkdir -p "$WORK/tree"
git -C "$WORK/tree" init -q .
# Gerrit's patch output omits the full index line for binaries, so git cannot apply them.
git -C "$WORK/tree" apply --whitespace=nowarn \
  --exclude='*.ttf' --exclude='*.otf' --exclude='*.png' --exclude='*.jpg' --exclude='*.webp' \
  "$DIFF"

echo "==> fetching binary files"
python3 "$HERE/refresh_helper.py" binaries "$WORK/detail.json" "$SHA" "$WORK/tree" "$HOST" "$CHANGE"

echo "==> replacing tree"
rm -rf "$HERE/compose"
cp -R "$WORK/tree/compose" "$HERE/compose"

echo "==> done. Review with: git status && git diff --stat"
echo "    Remember to update the Revision / status lines in PROVENANCE.md."

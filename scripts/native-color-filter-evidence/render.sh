#!/usr/bin/env bash
# Reproduce before/after snapshot-based swatches. Requires Swift 6 and Python Pillow.
set -euo pipefail
repo_root=$(git rev-parse --show-toplevel)
base_ref=${1:?usage: render.sh <before-ref> [output-directory]}
output=${2:-"$repo_root/renders/native-color-filter"}
mkdir -p "$output"
output=$(cd "$output" && pwd)
evidence_tmp=$(mktemp -d)
trap 'rm -rf "$evidence_tmp"' EXIT
mkdir -p "$evidence_tmp/before/rc-players"
git archive "$base_ref" Package.swift Sources Tests | tar -x -C "$evidence_tmp/before/rc-players"
for lane in before after; do
  player_source="$repo_root"
  if [ "$lane" = before ]; then player_source="$evidence_tmp/before/rc-players"; fi
  RC_COMPOSE_PLAYER_NATIVE_ONLY=1 RC_NATIVE_COLOR_FILTER_SOURCE="$player_source" \
    swift run --package-path "$repo_root/scripts/native-color-filter-evidence" \
      --scratch-path "$evidence_tmp/build-$lane" \
      --cache-path "$evidence_tmp/cache" --config-path "$evidence_tmp/config" \
      --security-path "$evidence_tmp/security" --jobs 2 ColorFilterEvidence > "$output/$lane.json"
done
python3 "$repo_root/scripts/native-color-filter-evidence/render.py" "$output"

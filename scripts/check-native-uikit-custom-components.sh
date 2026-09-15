#!/usr/bin/env bash
# Exercise the same editable custom component through CMP and the native UIKit host registry.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-$repo_root/build/native-uikit-custom-components}"
input="$(mktemp -d -t native-uikit-custom-input-XXXXXX)"
trap 'rm -rf "$input"' EXIT

mkdir -p "$output/cmp-jvm"
output="$(cd "$output" && pwd)"
cp "$repo_root/scripts/native-uikit-custom-components/manifest.json" "$input/manifest.json"
"$repo_root/gradlew" :rc-player-demos:writeEditableTextFixture \
  "-Prc.demo.output=$input/editable-text.rc"

RC_DEMO_RENDERS="$output/cmp-jvm" \
  "$repo_root/gradlew" :rc-player-demos:test --rerun \
    --tests '*RcDemoRenderTest.writeDemoRenders'
rm -f "$output/cmp-jvm/editable-text-edited.png" "$output/cmp-jvm/spannable-string.png"
"$repo_root/scripts/render-native-uikit-lane.sh" "$input" "$output/native-uikit"

node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" \
  results "$input" "$output" cmp-jvm native-uikit
if [ ! -d "$repo_root/scripts/design-artifacts/node_modules/pixelmatch" ]; then
  npm --prefix "$repo_root/scripts/design-artifacts" ci --no-audit --no-fund --silent
fi
node "$repo_root/scripts/design-artifacts/rc-multi-lane-score.mjs" \
  "$output" cmp-jvm native-uikit --json "$output/comparison.json"
echo "native UIKit custom-component comparison: $output/comparison.json"

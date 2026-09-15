#!/usr/bin/env bash
# Compare the current pure-Swift native profile against the CMP player.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lanes_dir="${1:-$repo_root/build/native-uikit-comparison}"
mkdir -p "$lanes_dir"
lanes_dir="$(cd "$lanes_dir" && pwd)"
input_dir="$(mktemp -d -t native-uikit-comparison-input-XXXXXX)"
trap 'rm -rf "$input_dir"' EXIT

cp "$repo_root/scripts/native-uikit-comparison/manifest.json" "$input_dir/manifest.json"
cp "$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc" "$input_dir/title-card.rc"
cp "$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/ImageBackgroundRemoteButton-454x200.rc" "$input_dir/image-button.rc"

rm -rf "$lanes_dir/cmp-jvm" "$lanes_dir/native-uikit"
mkdir -p "$lanes_dir/cmp-jvm"

echo "==> CMP JVM comparison lane"
"$repo_root/gradlew" --quiet :rc-player-compose:jvmTest --rerun \
  --tests '*RcCmpRenderHarness*' \
  "-Prc.cmp.input=$input_dir" \
  "-Prc.cmp.output=$lanes_dir/cmp-jvm"

echo "==> native UIKit comparison lane"
"$repo_root/scripts/render-native-uikit-lane.sh" "$input_dir" "$lanes_dir/native-uikit"

node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" \
  results "$input_dir" "$lanes_dir" cmp-jvm native-uikit
if [ ! -d "$repo_root/scripts/design-artifacts/node_modules/pixelmatch" ]; then
  npm --prefix "$repo_root/scripts/design-artifacts" ci --no-audit --no-fund --silent
fi
node "$repo_root/scripts/design-artifacts/rc-multi-lane-score.mjs" \
  "$lanes_dir" cmp-jvm native-uikit --json "$lanes_dir/comparison.json"
echo "native UIKit comparison: $lanes_dir/comparison.json"

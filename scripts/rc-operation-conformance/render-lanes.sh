#!/usr/bin/env bash
# Render one manifest-driven fixture corpus through the available reference and CMP players.
#
# The input contract is the same contract used by rc-compare: an absolute directory containing
# manifest.json and one <id>.rc file per entry. Each lane writes either <id>.png or <id>.error;
# CMP may additionally write <id>.unsupported when it rendered while reporting a capability gap.
#
# Upstream AndroidX can be checked against the released artifact, the pinned androidx.dev snapshot,
# or both. In "both" mode the stable View and vendored Android lanes use released dependencies and
# only the upstream embedded harness is repeated against the snapshot. This keeps a local vendored
# baseline stable while still showing whether an embedded-player difference is already fixed on
# androidx-main.
#
# Usage:
#   scripts/rc-operation-conformance/render-lanes.sh \
#     [--upstream release|snapshot|both] <fixture-dir> [lane-output-dir]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

upstream=release
if [ "${1:-}" = "--upstream" ]; then
  upstream="${2:-}"
  shift 2
fi
case "$upstream" in
  release|snapshot|both) ;;
  *)
    echo "error: --upstream must be release, snapshot, or both" >&2
    exit 2
    ;;
esac
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  sed -n '2,18p' "$0" >&2
  exit 2
fi

input_dir="$(cd "$1" && pwd)"
lanes_dir="${2:-$(mktemp -d -t rc-operation-conformance-XXXXXX)}"
mkdir -p "$lanes_dir"
lanes_dir="$(cd "$lanes_dir" && pwd)"

if [ ! -f "$input_dir/manifest.json" ]; then
  echo "error: no manifest.json in $input_dir" >&2
  exit 1
fi

node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" inputs "$input_dir"

if [ ! -d "$repo_root/scripts/design-artifacts/node_modules/pixelmatch" ]; then
  echo "==> npm install (scripts/design-artifacts, for pixel comparison)"
  (cd "$repo_root/scripts/design-artifacts" && npm install --no-audit --no-fund --silent)
fi

lanes=(view vendored-android vendored-jvm cmp-jvm)
primary_line="$upstream"
if [ "$upstream" = "both" ]; then
  primary_line=release
fi
upstream_lane="upstream-$primary_line"
lanes+=("$upstream_lane")
if [ "$upstream" = "both" ]; then
  lanes+=(upstream-snapshot)
fi

# Reused output directories must not retain a prior successful render when a current harness skips
# or errors. The paths are fully resolved beneath the caller-selected lane directory above.
for lane in "${lanes[@]}"; do
  rm -rf "$lanes_dir/$lane"
done

gradle_line=()
if [ "$primary_line" = "snapshot" ]; then
  gradle_line=(-Pcomposeai.remoteCompose=snapshot)
fi

echo "==> Android View + vendored embedded + upstream embedded ($primary_line)"
./gradlew --quiet :third-party-rc-embedded-player:testDebugUnitTest --rerun \
  --tests '*RcViewPlayerRenderHarness*' \
  --tests '*RcEmbeddedRenderHarness*' \
  --tests '*RcAndroidxEmbeddedRenderHarness*' \
  "${gradle_line[@]}" \
  "-Prc.embedded.input=$input_dir" \
  "-Prc.view.output=$lanes_dir/view" \
  "-Prc.embedded.output=$lanes_dir/vendored-android" \
  "-Prc.androidx.embedded.input=$input_dir" \
  "-Prc.androidx.embedded.output=$lanes_dir/$upstream_lane"

if [ "$upstream" = "both" ]; then
  echo "==> upstream AndroidX embedded (snapshot)"
  ./gradlew --quiet :third-party-rc-embedded-player:testDebugUnitTest --rerun \
    --tests '*RcAndroidxEmbeddedRenderHarness*' \
    -Pcomposeai.remoteCompose=snapshot \
    "-Prc.androidx.embedded.input=$input_dir" \
    "-Prc.androidx.embedded.output=$lanes_dir/upstream-snapshot"
fi

echo "==> vendored embedded JVM"
./gradlew --quiet :third-party-rc-embedded-player-jvm:test --rerun \
  --tests '*RcJvmRenderHarness*' \
  "${gradle_line[@]}" \
  "-Prc.jvm.input=$input_dir" \
  "-Prc.jvm.output=$lanes_dir/vendored-jvm"

echo "==> CMP JVM"
./gradlew --quiet :rc-player-compose:jvmTest --rerun \
  --tests '*RcCmpRenderHarness*' \
  "${gradle_line[@]}" \
  "-Prc.cmp.input=$input_dir" \
  "-Prc.cmp.output=$lanes_dir/cmp-jvm"

node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" \
  results "$input_dir" "$lanes_dir" "${lanes[@]}"

echo "==> pairwise pixel comparison"
node "$repo_root/scripts/design-artifacts/rc-multi-lane-score.mjs" \
  "$lanes_dir" "${lanes[@]}" --json "$lanes_dir/comparison.json"

echo "done. lane renders and comparison.json kept in $lanes_dir"

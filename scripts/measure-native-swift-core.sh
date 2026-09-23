#!/usr/bin/env bash
# Benchmark the pure-Swift document core and judge it against the reviewed budgets.
#
# This is the host-independent half of the native performance evidence: decode, first frame,
# steady-state animation frame cost, document replacement, and retained-session memory growth for
# every supported fixture. `scripts/measure-native-uikit-simulator.sh` covers the UIKit view tree on
# a simulator, and `--measure-native-evidence` in the macOS player covers the AppKit renderer.
#
# The JSON report carries its own budgets, so a CI artifact is reviewable on its own.
#
# The benchmark is `NativeSwiftBenchmark` in the SwiftPM test target. It is skipped unless
# RC_NATIVE_CORE_BENCHMARK_OUTPUT is set, because timings from an unoptimized `swift test` build
# mean nothing; this script sets it and builds the release configuration.
#
# Usage: scripts/measure-native-swift-core.sh [output.json]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-${RC_NATIVE_CORE_BENCHMARK_OUTPUT:-$repo_root/build/native-swift-core-benchmark.json}}"
mkdir -p "$(dirname "$output")"
output="$(cd "$(dirname "$output")" && pwd)/$(basename "$output")"
rm -f "$output"

RC_COMPOSE_PLAYER_NATIVE_ONLY=1 \
RC_NATIVE_CORE_BENCHMARK_OUTPUT="$output" \
RC_SOURCE_REVISION="$(git -C "$repo_root" rev-parse HEAD)" \
  swift test \
    --package-path "$repo_root" \
    -c release -Xswiftc -enable-testing \
    --filter 'NativeSwiftBenchmark'

# A skipped test also exits 0, so the report is the proof that the benchmark ran.
if [ ! -f "$output" ]; then
  echo "the native Swift core benchmark did not write $output" >&2
  exit 1
fi
echo "native Swift core benchmark: $output"

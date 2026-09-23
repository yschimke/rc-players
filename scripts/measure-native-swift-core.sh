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
# Usage: scripts/measure-native-swift-core.sh [output.json]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-${RC_NATIVE_CORE_BENCHMARK_OUTPUT:-$repo_root/build/native-swift-core-benchmark.json}}"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-swift-benchmark.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/module-cache" "$(dirname "$output")"

CLANG_MODULE_CACHE_PATH="$work/module-cache" \
SWIFT_MODULECACHE_PATH="$work/module-cache" \
xcrun swiftc \
  -O -whole-module-optimization \
  "$repo_root/Sources/RcNativePlayerCore/NativeSwiftCore.swift" \
  "$repo_root/Sources/RcNativePlayerCore/NativeSwiftWireReader.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeSwiftBenchmark.swift" \
  -o "$work/native-swift-benchmark"

RC_SOURCE_REVISION="$(git -C "$repo_root" rev-parse HEAD)" \
"$work/native-swift-benchmark" "$output" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/editable-text.rc" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/TitleCardRemote-640x480.rc" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/IndeterminateCircularProgress-400x400.rc" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/CircularProgressRemote-384x384.rc" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/ArcProgressRemote-454x400.rc" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/ImageBackgroundRemoteButton-454x200.rc"
echo "native Swift core benchmark: $output"

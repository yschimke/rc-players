#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-frame-timing.XXXXXX")"
trap 'rm -rf "$output"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerCore/NativeSwiftCore.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeFrameTiming.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeFrameTimingTests.swift" \
  -o "$output/native-frame-timing-tests"
"$output/native-frame-timing-tests"

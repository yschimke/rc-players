#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${TMPDIR:-/tmp}/rc-native-execution-limit-tests"

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeExecutionLimits.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeExecutionLimitsTests.swift" \
  -o "$output"
"$output"

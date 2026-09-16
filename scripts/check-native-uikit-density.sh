#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-density.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeCompatibility.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeDensityPolicy.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeDensityPolicyTests.swift" \
  -o "$work/native-density-tests"
"$work/native-density-tests"

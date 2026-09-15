#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-accessibility.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeAccessibilityPolicy.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeAccessibilityPolicyTests.swift" \
  -o "$work/native-accessibility-tests"
"$work/native-accessibility-tests"

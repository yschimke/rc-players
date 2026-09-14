#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-compatibility.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeCompatibility.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeCompatibilityPolicyTests.swift" \
  -o "$work/native-compatibility-tests"
"$work/native-compatibility-tests"

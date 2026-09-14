#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-text.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeTextPolicy.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeTextPolicyTests.swift" \
  -o "$work/native-text-tests"
"$work/native-text-tests"

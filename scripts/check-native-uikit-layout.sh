#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-layout.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeLayout.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeLayoutTests.swift" \
  -o "$work/native-layout-tests"
"$work/native-layout-tests"

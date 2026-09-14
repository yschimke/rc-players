#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-graphics.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeGraphicsState.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativePath.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeGraphicsStateTests.swift" \
  -o "$work/native-graphics-tests"
"$work/native-graphics-tests"

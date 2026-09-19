#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-texture.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeTexturePolicy.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeTexturePolicyTests.swift" \
  -o "$work/native-texture-tests"
"$work/native-texture-tests"

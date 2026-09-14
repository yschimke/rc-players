#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-resources.XXXXXX")"
trap 'rm -rf "$work"' EXIT

xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeResources.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeResourcePolicyTests.swift" \
  -o "$work/native-resource-tests"
"$work/native-resource-tests"

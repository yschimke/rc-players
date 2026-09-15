#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-swift-core.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/module-cache"

CLANG_MODULE_CACHE_PATH="$work/module-cache" \
SWIFT_MODULECACHE_PATH="$work/module-cache" \
xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeSwiftCore.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeSwiftCoreTests.swift" \
  -o "$work/native-swift-core-tests"
fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/editable-text.rc"
title_fixture="$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc"
"$work/native-swift-core-tests" "$fixture" "$title_fixture"

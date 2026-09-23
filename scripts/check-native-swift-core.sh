#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-swift-core.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/module-cache"

CLANG_MODULE_CACHE_PATH="$work/module-cache" \
SWIFT_MODULECACHE_PATH="$work/module-cache" \
xcrun swiftc \
  "$repo_root/Sources/RcNativePlayerCore/NativeSwiftCore.swift" \
  "$repo_root/Sources/RcNativePlayerCore/NativeSwiftWireReader.swift" \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeSwiftCoreTests.swift" \
  -o "$work/native-swift-core-tests"
fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/editable-text.rc"
title_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/TitleCardRemote-640x480.rc"
progress_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/IndeterminateCircularProgress-400x400.rc"
circular_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/CircularProgressRemote-384x384.rc"
arc_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/ArcProgressRemote-454x400.rc"
image_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/ImageBackgroundRemoteButton-454x200.rc"
# The only fixture here that defers its density instead of folding it in; see RcDemoDocuments.
host_density_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/host-density.rc"
tile_fixture="$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/demo-tile.rc"
"$work/native-swift-core-tests" \
  "$fixture" "$title_fixture" "$progress_fixture" "$circular_fixture" "$arc_fixture" \
  "$image_fixture" "$host_density_fixture" "$tile_fixture"

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
build_root="${RC_REMOTE_APPLE_BUILD_DIR:-$repo_root/build/remote-apple-comparison}"
app="$build_root/Remote Apple Comparison.app"
executable="$app/Contents/MacOS/RemoteAppleComparison"
resources="$app/Contents/Resources"
architecture="$(uname -m)"

case "$architecture" in
  arm64 | x86_64) ;;
  *) echo "unsupported macOS architecture: $architecture" >&2; exit 1 ;;
esac

rm -rf "$app"
mkdir -p "$app/Contents/MacOS" "$resources" "$build_root/module-cache"
cp "$repo_root/prototypes/remote-apple-components/swiftui/Info.plist" \
  "$app/Contents/Info.plist"
cp "$repo_root/renders/remote-apple-components/after-component-set.png" "$resources/"
cp "$repo_root/renders/remote-apple-components/after-controls-set.png" "$resources/"

CLANG_MODULE_CACHE_PATH="$build_root/module-cache" \
SWIFT_MODULECACHE_PATH="$build_root/module-cache" \
xcrun swiftc \
  -parse-as-library \
  -target "$architecture-apple-macos13.0" \
  -O \
  -framework AppKit \
  -framework SwiftUI \
  "$repo_root/prototypes/remote-apple-components/swiftui/RemoteAppleComparisonApp.swift" \
  -o "$executable"

codesign --force --deep --sign - "$app"
echo "$app"

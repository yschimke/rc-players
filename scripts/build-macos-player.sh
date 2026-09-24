#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
configuration="${RC_MACOS_PLAYER_CONFIGURATION:-release}"
version="${RC_MACOS_PLAYER_VERSION:-${PLUGIN_VERSION:-0.0.0}}"
build_root="${RC_MACOS_PLAYER_BUILD_DIR:-$repo_root/build/macos-player}"
framework_root="$repo_root/rc-player/compose/build/XCFrameworks/release/RcComposePlayer.xcframework/macos-arm64"
framework="$framework_root/RcComposePlayer.framework"
app="$build_root/Remote Compose Player.app"

if [ ! -d "$framework" ]; then
  echo "expected a locally assembled macOS framework at: $framework" >&2
  echo "build it with ./gradlew :rc-player-compose:assembleRcComposePlayerReleaseXCFramework --max-workers=1" >&2
  exit 1
fi

case "$configuration" in
  release) optimization=(-O -whole-module-optimization) ;;
  debug) optimization=(-Onone -g) ;;
  *) echo "unsupported RC_MACOS_PLAYER_CONFIGURATION: $configuration" >&2; exit 1 ;;
esac

rm -rf "$app"
mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources"
mkdir -p "$build_root/module-cache"
cp "$repo_root/samples/macos-player/Info.plist" "$app/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $version" "$app/Contents/Info.plist"

# `-swift-version 5`, stated rather than defaulted: this is one module holding the sample app, which
# drives the Kotlin/Native framework's non-Sendable API from detached tasks and blocking bridges.
# The player sources it shares are held to the Swift 6 language mode by `Package.swift` — `swift
# test` compiles them that way on every Apple lane — so their checking does not depend on this app.
CLANG_MODULE_CACHE_PATH="$build_root/module-cache" \
SWIFT_MODULECACHE_PATH="$build_root/module-cache" \
xcrun swiftc \
  -swift-version 5 \
  -parse-as-library \
  -target arm64-apple-macos12.0 \
  "${optimization[@]}" \
  -framework AppKit \
  -framework SwiftUI \
  -F "$framework_root" \
  -framework RcComposePlayer \
  "$repo_root/Sources/RcPlayerAppleFonts/RemoteComposeDownloadableFonts.swift" \
  "$repo_root"/Sources/RcNativePlayerCore/*.swift \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeCompatibility.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeExecutionLimits.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeSession.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeResources.swift" \
  "$repo_root/samples/macos-player/RemoteComposeMacApp.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeMacPolicy.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeTextPolicy.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeGraphicsState.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeTexturePolicy.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeGradientTiling.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeLayout.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeLayoutEngine.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeFrameTiming.swift" \
  "$repo_root/Sources/RcNativePlayerUIKit/NativeAppKitPlayer.swift" \
  -o "$app/Contents/MacOS/RemoteComposePlayer"

codesign --force --deep --sign - "$app"
echo "$app"

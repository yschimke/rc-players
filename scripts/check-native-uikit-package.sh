#!/usr/bin/env bash
# Validate the standalone native UIKit release archive as the artifact a consumer receives.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
archive="${1:-$repo_root/rc-player/compose/build/distributions/RcNativePlayerUIKit.swiftpackage.zip}"
checksum="$archive.sha256"
profile="$(dirname "$archive")/RcNativePlayerUIKit.profile.json"
profile_checksum="$profile.sha256"

if [ ! -f "$archive" ]; then
  echo "expected native UIKit package archive at: $archive" >&2
  echo "build it with build-brief ./gradlew :rc-player-compose:rcNativePlayerUIKitPackageChecksum --max-workers=1" >&2
  exit 1
fi

if [ ! -f "$checksum" ]; then
  echo "expected native UIKit package checksum at: $checksum" >&2
  exit 1
fi

(
  cd "$(dirname "$archive")"
  shasum -a 256 -c "$(basename "$checksum")"
)

if [ ! -f "$profile" ] || [ ! -f "$profile_checksum" ]; then
  echo "expected the versioned profile and checksum beside the native UIKit archive" >&2
  exit 1
fi
(
  cd "$(dirname "$profile")"
  shasum -a 256 -c "$(basename "$profile_checksum")"
)
"$repo_root/scripts/check-native-uikit-profile.sh" "$profile"

work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-player-package.XXXXXX")"
trap 'rm -rf "$work"' EXIT
unzip -q "$archive" -d "$work"
package="$work/RcNativePlayerUIKit"
mkdir -p "$work/module-cache"
export CLANG_MODULE_CACHE_PATH="$work/module-cache"
export SWIFT_MODULECACHE_PATH="$work/module-cache"

test -f "$package/Package.swift"
test -f "$package/Sources/RcNativePlayerCore/NativeSwiftCore.swift"
test -f "$package/Sources/RcNativePlayerUIKit/RemoteComposeNativePlayer.swift"
test ! -e "$package/Artifacts"
if rg -n 'RcComposePlayer|Kotlin' "$package/Package.swift" "$package/Sources"; then
  echo "native UIKit release package still references the Kotlin/CMP implementation" >&2
  exit 1
fi
test -f "$package/PROFILE.json"
cmp "$profile" "$package/PROFILE.json"

swift package --package-path "$package" dump-package >/dev/null

if command -v xcodebuild >/dev/null 2>&1; then
  (
    cd "$package"
    xcodebuild \
      -quiet \
      -scheme RcNativePlayerUIKit \
      -configuration Release \
      -destination "generic/platform=iOS Simulator" \
      -derivedDataPath "$work/DerivedData" \
      CODE_SIGNING_ALLOWED=NO \
      build
  )
fi

echo "native UIKit release package: ok"

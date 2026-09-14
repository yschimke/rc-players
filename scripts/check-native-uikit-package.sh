#!/usr/bin/env bash
# Validate the standalone native UIKit release archive as the artifact a consumer receives.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
archive="${1:-$repo_root/rc-player/compose/build/distributions/RcNativePlayerUIKit.swiftpackage.zip}"
checksum="$archive.sha256"

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

work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-player-package.XXXXXX")"
trap 'rm -rf "$work"' EXIT
unzip -q "$archive" -d "$work"
package="$work/RcNativePlayerUIKit"

test -f "$package/Package.swift"
test -f "$package/Sources/RcNativePlayerUIKit/RemoteComposeNativePlayer.swift"
test -d "$package/Artifacts/RcComposePlayer.xcframework"

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

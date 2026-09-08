#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
project="$repo_root/samples/apple-player/RemoteComposePlayer.xcodeproj"
derived_data="${RC_APPLE_PLAYER_DERIVED_DATA:-$repo_root/build/apple-player-derived-data}"
destination="${RC_APPLE_PLAYER_DESTINATION:-generic/platform=iOS Simulator}"
configuration="${RC_APPLE_PLAYER_CONFIGURATION:-Debug}"
framework="$repo_root/rc-player/compose/build/XCFrameworks/release/RcComposePlayer.xcframework"

if [ ! -d "$framework" ]; then
  echo "expected a locally assembled framework at: $framework" >&2
  echo "build it with ./gradlew :rc-player-compose:assembleRcComposePlayerReleaseXCFramework --max-workers=1" >&2
  exit 1
fi

xcodebuild \
  -project "$project" \
  -scheme RemoteComposePlayer \
  -configuration "$configuration" \
  -destination "$destination" \
  -derivedDataPath "$derived_data" \
  CODE_SIGNING_ALLOWED=NO \
  build

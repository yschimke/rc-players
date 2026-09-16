#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
project="$repo_root/samples/apple-player/RemoteComposePlayer.xcodeproj"
derived_data="${RC_APPLE_PLAYER_DERIVED_DATA:-$repo_root/build/apple-player-derived-data}"
destination="${RC_APPLE_PLAYER_DESTINATION:-generic/platform=iOS Simulator}"
configuration="${RC_APPLE_PLAYER_CONFIGURATION:-Debug}"
framework="$repo_root/rc-player/compose/build/XCFrameworks/release/RcComposePlayer.xcframework"

assemble="./gradlew :rc-player-compose:assembleRcComposePlayerReleaseXCFramework --max-workers=1"

# Every Kotlin/Native framework link extracts Skiko's static libraries into a fresh
# $TMPDIR/included<random> directory of about 141 MB and never removes it. They accumulate silently
# until a link dies with `e: Compilation failed: No space left on device` — which reads like a
# compile error and is not one. One host had 250 of them, 16 GB, and 226 MB of disk left.
#
# Only entries a day old are swept, because a concurrent link's directory is live and indistinguishable
# from a leaked one by name alone.
if [ -d "${TMPDIR:-/tmp}" ]; then
  find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'included*' -mtime +1 -prune \
    -exec rm -rf {} + 2>/dev/null || true
fi

if [ ! -d "$framework" ]; then
  # Assembling it here rather than telling the reader to run one more command. This script already
  # owns building the app, and the framework is the same class of dependency — a lane advertised as
  # a single command otherwise exits in seconds on any fresh checkout. It is a long step, so it
  # announces itself; RC_APPLE_PLAYER_REQUIRE_FRAMEWORK=1 restores the hard failure for callers
  # (CI) where an earlier step is supposed to have built it and a silent rebuild would hide a fault.
  if [ "${RC_APPLE_PLAYER_REQUIRE_FRAMEWORK:-0}" = "1" ]; then
    echo "expected a locally assembled framework at: $framework" >&2
    echo "build it with $assemble" >&2
    exit 1
  fi
  echo "==> no framework at $framework; assembling it (this takes about twenty minutes)" >&2
  (cd "$repo_root" && ./gradlew :rc-player-compose:assembleRcComposePlayerReleaseXCFramework \
    --max-workers=1)
  if [ ! -d "$framework" ]; then
    echo "error: $assemble did not produce $framework" >&2
    exit 1
  fi
fi

xcodebuild \
  -project "$project" \
  -scheme RemoteComposePlayer \
  -configuration "$configuration" \
  -destination "$destination" \
  -derivedDataPath "$derived_data" \
  CODE_SIGNING_ALLOWED=NO \
  build

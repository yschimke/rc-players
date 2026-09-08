#!/usr/bin/env bash
# Type-check the Swift usage sample in docs/design/RC_PLAYER_SWIFT.md against the real XCFramework.
#
# The sample is the *only* documentation of how to call the player from Swift, and everything
# awkward about it — the file-facade calls, required arguments, and `Data` not bridging to
# `ByteArray` — is a property of Kotlin/Native's Objective-C export
# rather than a choice made here. Which means the doc drifts silently whenever the export changes,
# and the first person to notice is a consumer whose project will not build.
#
# So the doc is the tested artifact: the fenced `swift` blocks are extracted and compiled for both
# iOS and macOS, with no
# second copy of the code to keep in sync. Two kinds of block are skipped, both self-describing:
#   * the `Package.swift` manifest fragment — a manifest, not app code;
#   * any block containing `…`, which marks it as illustrative rather than complete.
#
# Type-check only (`-typecheck`): this proves every name, selector and type in the sample exists as
# written, which is the failure mode. Linking a static Kotlin/Native framework would add minutes and
# catch nothing extra at the API level.
#
# Skips loudly, and exits 0, when the toolchain or the framework is absent — the framework only
# builds on macOS, so this is a no-op on a Linux runner rather than a failure.
#
# Usage: scripts/check-swift-sample.sh [path/to/RcComposePlayer.xcframework]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

doc="docs/design/RC_PLAYER_SWIFT.md"
xcframework="${1:-rc-player/compose/build/XCFrameworks/release/RcComposePlayer.xcframework}"

if ! command -v xcrun > /dev/null 2>&1; then
  echo "skip: no xcrun on this host; the Swift sample is only checkable on macOS" >&2
  exit 0
fi

ios_slice="$xcframework/ios-arm64-simulator/RcComposePlayer.framework"
macos_slice="$xcframework/macos-arm64/RcComposePlayer.framework"
if [ ! -d "$ios_slice" ] || [ ! -d "$macos_slice" ]; then
  echo "skip: no complete assembled Apple framework at $xcframework" >&2
  echo "      build one with ./gradlew :rc-player-compose:assembleRcComposePlayerReleaseXCFramework" >&2
  exit 0
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
extract_sample() {
  local platform="$1"
  local sample="$2"
  cat > "$sample" <<'PREAMBLE'
import Foundation

let documentData = Data()
func handle(_ event: RcPlayerEvent) {}
func show(_ message: String) {}
PREAMBLE

  python3 - "$doc" "$platform" >> "$sample" <<'EXTRACT'
import re, sys

text = open(sys.argv[1]).read()
platform = sys.argv[2]
blocks = re.findall(r"```swift\n(.*?)```", text, re.S)
kept = 0
for block in blocks:
    if "// Package.swift" in block:
        continue
    if "…" in block:  # an illustrative block, not complete code
        continue
    if platform == "ios" and "// macOS" in block:
        continue
    if platform == "macos" and "// iOS" in block:
        continue
    sys.stdout.write(block)
    sys.stdout.write("\n")
    kept += 1
if kept == 0:
    sys.exit("error: no compilable swift blocks found in the doc — did the fences change?")
EXTRACT
}

ios_sample="$work/IosSample.swift"
macos_sample="$work/MacosSample.swift"
extract_sample ios "$ios_sample"
extract_sample macos "$macos_sample"

sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
arch="$(uname -m)"
target="$arch-apple-ios13.0-simulator"

echo "type-checking iOS sample against $ios_slice ($target)"
# `-Xcc -Wno-...` is not used: the only warning the export produces today is Compose Multiplatform's
# own nested-type mapping, and it should stay visible rather than be suppressed here.
xcrun -sdk "$sdk" swiftc \
  -target "$target" \
  -F "$(dirname "$ios_slice")" \
  -typecheck "$ios_sample"

macos_sdk="$(xcrun --sdk macosx --show-sdk-path)"
macos_target="$arch-apple-macos12.0"
echo "type-checking macOS sample against $macos_slice ($macos_target)"
xcrun -sdk "$macos_sdk" swiftc \
  -target "$macos_target" \
  -F "$(dirname "$macos_slice")" \
  -typecheck "$macos_sample"

echo "swift sample: ok"

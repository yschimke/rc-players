#!/usr/bin/env bash
# Render one google:-family document through the release AppKit player with downloading off and on.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${1:-$repo_root/build/macos-google-fonts}"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-macos-google-fonts.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/module-cache" "$output_dir"

xcrun swiftc -parse-as-library -module-cache-path "$work/module-cache" \
  "$repo_root/scripts/apple-google-fonts/GenerateFixture.swift" \
  -o "$work/generate-fixture"
"$work/generate-fixture" "$work/google-font.rc"

"$repo_root/scripts/build-macos-player.sh" >/dev/null
executable="$repo_root/build/macos-player/Remote Compose Player.app/Contents/MacOS/RemoteComposePlayer"
"$executable" --render-native-png \
  "$work/google-font.rc" "$output_dir/google-font-fallback.png"
"$executable" --render-native-google-font-png \
  "$work/google-font.rc" "$output_dir/google-font-downloaded.png"

test -s "$output_dir/google-font-fallback.png"
test -s "$output_dir/google-font-downloaded.png"
if cmp -s "$output_dir/google-font-fallback.png" "$output_dir/google-font-downloaded.png"; then
  echo "Downloaded-font render unexpectedly matches the fallback render" >&2
  exit 1
fi
echo "macOS Google Fonts rendering: $output_dir"

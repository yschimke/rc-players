#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="${RC_MACOS_PLAYER_OUTPUT_DIR:-$repo_root/build/distributions}"
archive="$output_dir/RemoteComposePlayer-macOS-arm64.zip"
stage="$(mktemp -d "${TMPDIR:-/tmp}/remote-compose-macos.XXXXXX")"
trap 'rm -rf "$stage"' EXIT

RC_MACOS_PLAYER_CONFIGURATION=release "$repo_root/scripts/build-macos-player.sh"
app="$repo_root/build/macos-player/Remote Compose Player.app"
executable="$app/Contents/MacOS/RemoteComposePlayer"
smoke_document="$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc"
animation_document="$repo_root/rc-player/compose/src/jvmTest/resources/rc-fixtures/IndeterminateCircularProgress-400x400.rc"

mkdir -p "$stage/RemoteComposePlayer-macOS-arm64" "$output_dir"
ditto "$app" "$stage/RemoteComposePlayer-macOS-arm64/Remote Compose Player.app"
cp "$repo_root/samples/macos-player/distribution/README.txt" "$stage/RemoteComposePlayer-macOS-arm64/README.txt"

rm -f "$archive" "$archive.sha256"
(cd "$stage" && zip -qry "$archive" "RemoteComposePlayer-macOS-arm64")
(cd "$output_dir" && shasum -a 256 "$(basename "$archive")" > "$(basename "$archive").sha256")

codesign --verify --deep --strict "$app"
test "$(lipo -archs "$executable")" = "arm64"
"$executable" --validate-native "$smoke_document"
"$executable" --validate-native-animation "$animation_document"
"$executable" --validate-native-scheduling-policy
"$executable" --validate-native-events
unzip -tq "$archive"
echo "$archive"

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
derived_data="${RC_APPLE_PLAYER_DERIVED_DATA:-$repo_root/build/apple-player-derived-data}"
output_dir="${RC_APPLE_PLAYER_OUTPUT_DIR:-$repo_root/build/distributions}"
archive="$output_dir/RemoteComposePlayer-Simulator-arm64.zip"
stage="$(mktemp -d "${TMPDIR:-/tmp}/remote-compose-player.XXXXXX")"
trap 'rm -rf "$stage"' EXIT

RC_APPLE_PLAYER_CONFIGURATION=Release \
  RC_APPLE_PLAYER_DESTINATION="generic/platform=iOS Simulator" \
  "$repo_root/scripts/build-apple-player.sh"

app="$derived_data/Build/Products/Release-iphonesimulator/Remote Compose.app"
if [ ! -d "$app" ]; then
  echo "expected app bundle was not built: $app" >&2
  exit 1
fi

bundle="$stage/RemoteComposePlayer-Simulator-arm64"
mkdir -p "$bundle" "$output_dir"
ditto "$app" "$bundle/Remote Compose.app"
cp "$repo_root/samples/apple-player/distribution/README.txt" "$bundle/README.txt"
cp "$repo_root/samples/apple-player/distribution/install-and-run.sh" "$bundle/install-and-run.sh"
chmod +x "$bundle/install-and-run.sh"

rm -f "$archive" "$archive.sha256"
(cd "$stage" && zip -qry "$archive" "RemoteComposePlayer-Simulator-arm64")
(cd "$output_dir" && shasum -a 256 "$(basename "$archive")" > "$(basename "$archive").sha256")

echo "$archive"

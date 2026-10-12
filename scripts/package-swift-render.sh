#!/usr/bin/env bash
# Build `rc-swift-render` — the native Swift player's AppKit renderer as a command-line tool — and
# package it as the release asset the preview server downloads:
#
#   build/distributions/rc-swift-render-macos-arm64.zip
#   build/distributions/rc-swift-render-macos-arm64.zip.sha256
#
# The archive holds one directory, `rc-swift-render-macos-arm64/`, with the executable at
# `bin/rc-swift-render`. compose-ai-tools' `RcSwiftServerRenderer` relies on that layout; change it
# there in the same release or not at all.
#
# Before packaging, the built binary renders the title-card fixture both ways it is driven — one
# shot and as a pooled worker over the frame protocol — so an asset that cannot draw is never
# uploaded. See docs/design/RC_SWIFT_RENDER.md.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="${RC_SWIFT_RENDER_OUTPUT_DIR:-$repo_root/build/distributions}"
name="rc-swift-render-macos-arm64"
archive="$output_dir/$name.zip"
stage="$(mktemp -d "${TMPDIR:-/tmp}/rc-swift-render.XXXXXX")"
trap 'rm -rf "$stage"' EXIT

if [ "$(uname -s)" != "Darwin" ]; then
  echo "error: rc-swift-render is the AppKit renderer and builds only on macOS" >&2
  exit 1
fi

# Native-only: the executable needs the pure-Swift player, not the Kotlin/Native binary target,
# whose placeholder checksum on `main` would fail the resolve.
cd "$repo_root"
RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift build -c release --arch arm64 --product rc-swift-render
bin_dir="$(RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift build -c release --arch arm64 --show-bin-path)"
binary="$bin_dir/rc-swift-render"
test -x "$binary"
test "$(lipo -archs "$binary")" = "arm64"

"$repo_root/scripts/check-swift-render.sh" "$binary"

mkdir -p "$stage/$name/bin" "$output_dir"
cp "$binary" "$stage/$name/bin/rc-swift-render"
cp "$repo_root/LICENSE" "$stage/$name/LICENSE"
codesign --force --sign - "$stage/$name/bin/rc-swift-render"
codesign --verify --strict "$stage/$name/bin/rc-swift-render"

rm -f "$archive" "$archive.sha256"
(cd "$stage" && zip -qry "$archive" "$name")
(cd "$output_dir" && shasum -a 256 "$(basename "$archive")" > "$(basename "$archive").sha256")
unzip -tq "$archive"
echo "$archive"

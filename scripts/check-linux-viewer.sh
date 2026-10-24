#!/usr/bin/env bash
# Smoke-test the packaged Linux desktop viewer that `:rc-player-desktop:packageRcDesktopLinux` built.
#
# It runs the launcher a user unpacks, not the Gradle classpath, and needs no display: `--render-png`
# draws one frame through Skiko's software raster. `--offline` keeps the check off Google Fonts.
# With an argument, also checks that the launcher reports that version.
#
# Used by ci.yml on every PR and by release.yml before the assets are attached, so the release
# never meets a packaging failure the pull request did not.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
dist="$repo_root/rc-player/desktop/build/rc-desktop-dist"
expected_version="${1:-}"
stage="$(mktemp -d "${TMPDIR:-/tmp}/rc-desktop-smoke.XXXXXX")"
trap 'rm -rf "$stage"' EXIT

tar -xzf "$dist/RemoteComposePlayer-linux-x64.tar.gz" -C "$stage"
launcher="$stage/remote-compose-player/bin/remote-compose-player"
test -x "$launcher"
test -f "$stage/remote-compose-player/README.txt"

if [ -n "$expected_version" ]; then
  # The JVM prints `Picked up JAVA_TOOL_OPTIONS` on stderr on some hosts; only stdout is compared.
  actual="$("$launcher" --version 2>/dev/null)"
  if [ "$actual" != "$expected_version" ]; then
    echo "launcher reports version '$actual', expected '$expected_version'" >&2
    exit 1
  fi
fi

"$launcher" --render-png --offline \
  "$repo_root/rc-player/compose/src/jvmTest/resources/rc-fixtures/TextRemoteButton-454x200.rc" \
  "$stage/smoke.png"
test -s "$stage/smoke.png"

dpkg-deb --field "$dist/RemoteComposePlayer-linux-amd64.deb" Package | grep -qx "remote-compose-player"
echo "Linux viewer OK: $(cd "$dist" && echo *)"

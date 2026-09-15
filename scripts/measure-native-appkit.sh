#!/usr/bin/env bash
# Capture AppKit native-renderer performance evidence from the packaged macOS player.
#
# The AppKit half of the native benchmark set: decode, native view-tree build, a real Core Graphics
# capture, steady-state frame cost on a retained session, and the memory an evidence run retains.
# `scripts/measure-native-uikit-simulator.sh` is the UIKit half and
# `scripts/measure-native-swift-core.sh` is the host-independent core.
#
# Usage: scripts/measure-native-appkit.sh [output.json]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-${RC_NATIVE_APPKIT_EVIDENCE_OUTPUT:-$repo_root/build/native-appkit-evidence.json}}"
app="${RC_MACOS_PLAYER_BUILD_DIR:-$repo_root/build/macos-player}/Remote Compose Player.app"
binary="$app/Contents/MacOS/RemoteComposePlayer"
fixture="$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc"

if [ ! -x "$binary" ]; then
  echo "expected the packaged macOS player at: $binary" >&2
  echo "build it with scripts/package-macos-player.sh" >&2
  exit 1
fi

mkdir -p "$(dirname "$output")"
RC_SOURCE_REVISION="$(git -C "$repo_root" rev-parse HEAD)" \
  "$binary" --measure-native-evidence "$fixture" "$output"

python3 - "$output" <<'PY'
import json, pathlib, sys
report = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert report["schemaVersion"] == 1
assert report["iterations"] >= 5
assert report["frames"] >= 60
assert report["metrics"]["viewCount"] > 1
assert report["metrics"]["labelCount"] > 0
assert report["passed"], f"native AppKit evidence exceeded its reviewed budgets: {report}"
PY
echo "native AppKit evidence: ok ($output)"

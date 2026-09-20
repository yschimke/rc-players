#!/usr/bin/env bash
# Comparative Apple-player evidence on the local Apple-silicon macOS host; no simulator is used.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${RC_APPLE_PLAYER_BENCHMARK_OUTPUT:-$repo_root/build/apple-player-benchmark.json}"
app="${RC_MACOS_PLAYER_BUILD_DIR:-$repo_root/build/macos-player}/Remote Compose Player.app"
binary="$app/Contents/MacOS/RemoteComposePlayer"
fixture="$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc"
native="$repo_root/build/native-appkit-evidence.json"
cmp="$repo_root/build/cmp-macos-startup-evidence.json"

if [ ! -x "$binary" ]; then
  echo "expected the packaged macOS player at: $binary" >&2
  echo "run scripts/package-macos-player.sh first" >&2
  exit 1
fi

scripts/measure-native-appkit.sh "$native"
RC_SOURCE_REVISION="$(git -C "$repo_root" rev-parse HEAD)" \
  "$binary" --measure-cmp-startup-evidence "$fixture" "$cmp"

mkdir -p "$(dirname "$output")"
python3 - "$native" "$cmp" "$output" <<'PY'
import json, pathlib, sys
native = json.loads(pathlib.Path(sys.argv[1]).read_text())
cmp = json.loads(pathlib.Path(sys.argv[2]).read_text())
assert native["schemaVersion"] == cmp["schemaVersion"] == 1
assert native["sourceRevision"] == cmp["sourceRevision"]
report = {
    "schemaVersion": 1,
    "sourceRevision": native["sourceRevision"],
    "host": "macOS",
    "fixture": native["fixture"],
    "nativeAppKit": native,
    "cmp": cmp,
    "methodology": (
        "Native AppKit reports decode/build/capture/retained-frame evidence. "
        "CMP reports window startup and first visible content. Both run in the packaged "
        "Release macOS app on the same Apple-silicon host; no simulator is involved."
    ),
}
pathlib.Path(sys.argv[3]).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
PY
echo "Apple player macOS benchmark: $output"

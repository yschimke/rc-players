#!/usr/bin/env bash
# Comparative Apple-player evidence on the local Apple-silicon macOS host; no simulator is used.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${RC_APPLE_PLAYER_BENCHMARK_OUTPUT:-$repo_root/build/apple-player-benchmark.json}"
app="${RC_MACOS_PLAYER_BUILD_DIR:-$repo_root/build/macos-player}/Remote Compose Player.app"
binary="$app/Contents/MacOS/RemoteComposePlayer"
fixture="$repo_root/third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc"
native="$repo_root/build/native-appkit-evidence.json"
native_startup="$repo_root/build/native-appkit-startup-evidence.json"
cmp="$repo_root/build/cmp-macos-startup-evidence.json"

if [ ! -x "$binary" ]; then
  echo "expected the packaged macOS player at: $binary" >&2
  echo "run scripts/package-macos-player.sh first" >&2
  exit 1
fi

scripts/measure-native-appkit.sh "$native"
RC_SOURCE_REVISION="$(git -C "$repo_root" rev-parse HEAD)" \
  "$binary" --measure-native-startup-evidence "$fixture" "$native_startup"
RC_SOURCE_REVISION="$(git -C "$repo_root" rev-parse HEAD)" \
  "$binary" --measure-cmp-startup-evidence "$fixture" "$cmp"

mkdir -p "$(dirname "$output")"
python3 - "$native" "$native_startup" "$cmp" "$output" <<'PY'
import json, pathlib, sys
native = json.loads(pathlib.Path(sys.argv[1]).read_text())
native_startup = json.loads(pathlib.Path(sys.argv[2]).read_text())
cmp = json.loads(pathlib.Path(sys.argv[3]).read_text())
assert native["schemaVersion"] == cmp["schemaVersion"] == 1
assert native["sourceRevision"] == native_startup["sourceRevision"] == cmp["sourceRevision"]
assert native_startup["renderer"] == "nativeAppKit"
assert cmp["renderer"] == "cmp"
for sample in (native_startup, cmp):
    assert sample["medianWindowStartupMilliseconds"] >= 0
    assert sample["medianFirstPresentationMilliseconds"] >= sample["medianWindowStartupMilliseconds"]
report = {
    "schemaVersion": 1,
    "sourceRevision": native["sourceRevision"],
    "host": "macOS",
    "fixture": native["fixture"],
    "headline": {"cmp": cmp, "nativeAppKit": native_startup},
    "nativeAppKitDiagnostics": native,
    "methodology": (
        "Headline metrics use the same window-start and first-visible contract for both players. "
        "Native AppKit diagnostics additionally report decode/build/capture/retained-frame evidence. "
        "Both run in the packaged Release macOS app on the same Apple-silicon host; no simulator is involved."
    ),
}
pathlib.Path(sys.argv[4]).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
PY
echo "Apple player macOS benchmark: $output"

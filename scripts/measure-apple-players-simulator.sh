#!/usr/bin/env bash
# Compare the CMP and native UIKit players on one iPad simulator. The report is intentionally
# directional: use a physical device plus Instruments before using results as release budgets.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app="$repo_root/build/apple-player-derived-data/Build/Products/Release-iphonesimulator/Remote Compose.app"
output="${RC_APPLE_PLAYER_BENCHMARK_OUTPUT:-$repo_root/build/apple-player-benchmark.json}"
bundle_id="ee.schimke.rcplayers.appleplayer"
. "$repo_root/scripts/simulator-boot.sh"

if [ ! -d "$app" ]; then
  echo "expected the packaged Release simulator app at: $app" >&2
  echo "run scripts/package-apple-player.sh first" >&2
  exit 1
fi

minimum_os="$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$app/Info.plist")"
device="$({ xcrun simctl list devices available --json; } | python3 -c '
import json, re, sys
minimum = tuple(map(int, sys.argv[1].split(".")))
devices = []
for runtime, values in json.load(sys.stdin)["devices"].items():
    match = re.search(r"\.iOS-(\d+)-(\d+)$", runtime)
    if match and tuple(map(int, match.groups())) >= minimum:
        devices.extend(d for d in values if d.get("isAvailable") and d["name"].startswith("iPad"))
preferred = [d for d in devices if "13-inch" in d["name"]]
ordered = [d for d in devices if d["state"] == "Booted"] + preferred + devices
if not ordered:
    raise SystemExit(f"no available iPad simulator supports iOS {sys.argv[1]} or newer")
print(ordered[0]["udid"], ordered[0]["state"], ordered[0]["name"], sep="\t")
' "$minimum_os")"
IFS=$'\t' read -r udid state device_name <<< "$device"
started=false
cleanup() {
  if [ "$started" = true ]; then xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
if [ "$state" != "Booted" ]; then xcrun simctl boot "$udid"; started=true; fi
rc_await_boot "$udid"

xcrun simctl uninstall "$udid" "$bundle_id" >/dev/null 2>&1 || true
xcrun simctl install "$udid" "$app"
revision="$(git -C "$repo_root" rev-parse HEAD)"
SIMCTL_CHILD_RC_SOURCE_REVISION="$revision" \
  SIMCTL_CHILD_RC_BENCHMARK_DEVICE="$device_name" \
  xcrun simctl launch --terminate-running-process "$udid" "$bundle_id" --apple-player-benchmark >/dev/null

container="$(xcrun simctl get_app_container "$udid" "$bundle_id" data)"
report="$container/Documents/apple-player-benchmark.json"
failure="$container/Documents/apple-player-benchmark-error.txt"
deadline=$((SECONDS + 90))
while [ ! -f "$report" ]; do
  if [ -f "$failure" ]; then echo "Apple player benchmark failed: $(cat "$failure")" >&2; exit 1; fi
  if [ "$SECONDS" -ge "$deadline" ]; then echo "Apple player benchmark was not written within 90 seconds" >&2; exit 1; fi
  sleep 1
done

mkdir -p "$(dirname "$output")"
cp "$report" "$output"
python3 - "$output" "$revision" <<'PY'
import json, pathlib, sys
report = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert report["schemaVersion"] == 1
assert report["sourceRevision"] == sys.argv[2]
assert set(report["results"]) == {"cmp", "nativeUIKit"}
for result in report["results"].values():
    assert result["medianPlayerStartupMilliseconds"] >= 0
    assert result["medianFirstPresentationMilliseconds"] >= result["medianPlayerStartupMilliseconds"]
    assert result["animation"]["samples"] == 120
    assert result["animation"]["p95FrameIntervalMilliseconds"] > 0
    assert result["scroll"]["samples"] == 3
    assert result["scroll"]["medianCompletionMilliseconds"] > 0
PY
echo "Apple player simulator benchmark: $output"

#!/usr/bin/env bash
# Render the shared manifest comparison contract through the native UIKit player on an iOS simulator.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$repo_root/scripts/simulator-boot.sh"
. "$repo_root/scripts/lane-progress.sh"
bundle_id="ee.schimke.rcplayers.appleplayer"
app="$repo_root/build/apple-player-derived-data/Build/Products/Release-iphonesimulator/Remote Compose.app"

if [ $# -ne 2 ]; then
  echo "usage: scripts/render-native-uikit-lane.sh <fixture-dir> <lane-output-dir>" >&2
  exit 2
fi
if [ "$(uname -s)" != "Darwin" ]; then
  echo "error: the native UIKit comparison lane requires macOS and an iOS simulator" >&2
  exit 1
fi

input_dir="$(cd "$1" && pwd)"
mkdir -p "$2"
output_dir="$(cd "$2" && pwd)"
node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" inputs "$input_dir"

if [ "${RC_NATIVE_UIKIT_SKIP_BUILD:-0}" != "1" ]; then
  "$repo_root/scripts/package-apple-player.sh"
fi
if [ ! -d "$app" ]; then
  echo "error: expected the packaged simulator app at $app" >&2
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
print(ordered[0]["udid"], ordered[0]["state"], sep="\t")
' "$minimum_os")"
IFS=$'\t' read -r udid state <<< "$device"
started=false
if [ "$state" != "Booted" ]; then
  xcrun simctl boot "$udid"
  started=true
fi

cleanup() {
  xcrun simctl terminate "$udid" "$bundle_id" >/dev/null 2>&1 || true
  if [ "$started" = true ]; then
    xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

rc_await_boot "$udid"
xcrun simctl install "$udid" "$app"
data_container="$(xcrun simctl get_app_container "$udid" "$bundle_id" data)"
harness_root="$data_container/Documents/native-comparison"
rm -rf "$harness_root"
mkdir -p "$harness_root/input" "$harness_root/output"
cp "$input_dir/manifest.json" "$harness_root/input/manifest.json"
cp "$input_dir"/*.rc "$harness_root/input/"

rm -rf "$output_dir"
mkdir -p "$output_dir"
xcrun simctl launch --terminate-running-process "$udid" "$bundle_id" --native-comparison >/dev/null

# Two bounds, because one was not enough. A stall bound catches a harness that has stopped, which a
# per-document budget cannot: guessing the cost low fails a lane that was working, as 30s +
# 1s/document did 731s into the 701-document corpus. But bounding only stalls let a lane that kept
# producing results slowly run 51 minutes of a 90-minute job, so the four validation steps after it
# never ran. The total budget is a cap on this lane's share of the job rather than a guess at a
# render cost. Partial output is copied out on both, so either bound leaves evidence.
documents="$(python3 -c 'import json, sys; print(len(json.load(open(sys.argv[1]))))' \
  "$input_dir/manifest.json")"
if ! progress="$(rc_await_lane_completion "$harness_root" "$documents" \
  "${RC_NATIVE_UIKIT_STALL_BUDGET:-120}" "${RC_NATIVE_UIKIT_LANE_BUDGET:-1200}" \
  "${RC_NATIVE_UIKIT_HEARTBEAT:-60}")"; then
  cp -R "$harness_root/output/." "$output_dir/" 2>/dev/null || true
  echo "error: native UIKit comparison stopped after $progress of $documents documents;" \
    "partial output in $output_dir" >&2
  exit 1
fi

cp -R "$harness_root/output/." "$output_dir/"
lanes_dir="$(dirname "$output_dir")"
lane="$(basename "$output_dir")"
node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" \
  results "$input_dir" "$lanes_dir" "$lane"
echo "native UIKit lane: $output_dir"

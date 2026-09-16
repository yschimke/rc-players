#!/usr/bin/env bash
# Render the shared manifest comparison contract through the native UIKit player on an iOS simulator.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$repo_root/scripts/simulator-boot.sh"
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

# The budget scales with the manifest. Ninety seconds was a constant sized for the four-document
# fixture set; the catalog corpus is ~700, and a fixed deadline would have failed it partway
# through with nothing to say about why. Thirty seconds covers launch and first frame, then a
# second per document — far more than a render costs, which is the point: this exists to catch a
# harness that has stopped, not to police its speed.
documents="$(python3 -c 'import json, sys; print(len(json.load(open(sys.argv[1]))))' \
  "$input_dir/manifest.json")"
budget=$((30 + documents))
deadline=$((SECONDS + budget))
while [ ! -f "$harness_root/done.json" ]; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "error: native UIKit comparison did not finish $documents documents within ${budget}s" >&2
    exit 1
  fi
  sleep 1
done

cp -R "$harness_root/output/." "$output_dir/"
lanes_dir="$(dirname "$output_dir")"
lane="$(basename "$output_dir")"
node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" \
  results "$input_dir" "$lanes_dir" "$lane"
echo "native UIKit lane: $output_dir"

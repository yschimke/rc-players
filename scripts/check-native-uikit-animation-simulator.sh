#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

. "$repo_root/scripts/simulator-boot.sh"
app="$repo_root/build/apple-player-derived-data/Build/Products/Release-iphonesimulator/Remote Compose.app"
first="${RC_NATIVE_ANIMATION_FIRST:-$repo_root/build/native-uikit-animation-a.png}"
second="${RC_NATIVE_ANIMATION_SECOND:-$repo_root/build/native-uikit-animation-b.png}"
bundle_id="ee.schimke.rcplayers.appleplayer"

if [ ! -d "$app" ]; then
  echo "expected the packaged simulator app at: $app" >&2
  exit 1
fi

minimum_os="$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$app/Info.plist")"
devices_json="$(mktemp "${TMPDIR:-/tmp}/rc-native-animation-devices.XXXXXX")"
trap 'rm -f "$devices_json"' EXIT
rc_run_bounded 120 xcrun simctl list devices available --json > "$devices_json"
device="$(python3 -c '
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
' "$minimum_os" < "$devices_json")"
udid="${device%%$'\t'*}"
state="${device#*$'\t'}"
if [ "$state" != "Booted" ]; then
  rc_run_bounded 300 xcrun simctl boot "$udid"
  trap 'rm -f "$devices_json"; xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true' EXIT
fi
rc_await_boot "$udid"
rc_run_bounded 300 xcrun simctl install "$udid" "$app"
rc_run_bounded 120 xcrun simctl launch --terminate-running-process "$udid" "$bundle_id" \
  --native-player '--fixture=Progress'
mkdir -p "$(dirname "$first")" "$(dirname "$second")"
sleep 2
rc_run_bounded 120 xcrun simctl io "$udid" screenshot "$first" > /dev/null
sleep 0.35
rc_run_bounded 120 xcrun simctl io "$udid" screenshot "$second" > /dev/null
xcrun swift "$repo_root/scripts/validate-native-uikit-animation.swift" "$first" "$second"

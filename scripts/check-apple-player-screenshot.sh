#!/usr/bin/env bash
# Boots an available iPad simulator, launches the packaged sample, and proves that its Metal/Skiko
# surface contains the title-card fixture rather than a blank or error fallback.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
app="$repo_root/build/apple-player-derived-data/Build/Products/Release-iphonesimulator/Remote Compose.app"
screenshot="${RC_APPLE_PLAYER_SCREENSHOT:-$repo_root/build/apple-player-screenshot.png}"
bundle_id="ee.schimke.rcplayers.appleplayer"

if [ ! -d "$app" ]; then
  echo "expected the packaged simulator app at: $app" >&2
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
    if not match or tuple(map(int, match.groups())) < minimum:
        continue
    devices.extend(d for d in values
                   if d.get("isAvailable") and d["name"].startswith("iPad"))
preferred = [d for d in devices if "13-inch" in d["name"]]
ordered = [d for d in devices if d["state"] == "Booted"] + preferred + devices
if not ordered:
    raise SystemExit(f"no available iPad simulator supports iOS {sys.argv[1]} or newer")
d = ordered[0]
print(d["udid"], d["state"], sep="\t")
' "$minimum_os")"
udid="${device%%$'\t'*}"
state="${device#*$'\t'}"

if [ "$state" != "Booted" ]; then
  xcrun simctl boot "$udid"
  trap 'xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true' EXIT
fi
xcrun simctl bootstatus "$udid" -b
xcrun simctl install "$udid" "$app"
xcrun simctl launch --terminate-running-process "$udid" "$bundle_id"
mkdir -p "$(dirname "$screenshot")"

# Metal can need several frames after process launch. Validate each capture and retain the last one
# on failure so CI has a useful artifact instead of a timing-only error.
for attempt in $(seq 1 20); do
  xcrun simctl io "$udid" screenshot "$screenshot" >/dev/null
  if xcrun swift "$repo_root/scripts/validate-apple-player-screenshot.swift" "$screenshot"; then
    echo "apple player screenshot: ok ($screenshot)"
    exit 0
  fi
  sleep 1
done

echo "the Apple player never produced the expected rendered fixture: $screenshot" >&2
exit 1

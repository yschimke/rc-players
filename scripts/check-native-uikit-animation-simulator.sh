#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Every simulator command here is bounded. A wedged simctl used to hang this step indefinitely with
# no output at all — one macOS run burned its whole 90-minute budget sitting in the first call —
# and an unattributable timeout is worse than a fast, named failure. This mirrors the deadline
# `measure-native-uikit-simulator.sh` already puts around `bootstatus`.
run_bounded() {
  local seconds="$1"
  shift
  "$@" &
  local pid=$!
  local deadline=$((SECONDS + seconds))
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      kill -9 "$pid" > /dev/null 2>&1 || true
      wait "$pid" > /dev/null 2>&1 || true
      echo "simulator command timed out after ${seconds}s: $*" >&2
      exit 1
    fi
    sleep 1
  done
  wait "$pid"
}
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
run_bounded 120 xcrun simctl list devices available --json > "$devices_json"
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
  run_bounded 300 xcrun simctl boot "$udid"
  trap 'rm -f "$devices_json"; xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true' EXIT
fi
run_bounded 300 xcrun simctl bootstatus "$udid" -b
run_bounded 300 xcrun simctl install "$udid" "$app"
run_bounded 120 xcrun simctl launch --terminate-running-process "$udid" "$bundle_id" \
  --native-player '--fixture=Progress'
mkdir -p "$(dirname "$first")" "$(dirname "$second")"
sleep 2
run_bounded 120 xcrun simctl io "$udid" screenshot "$first" > /dev/null
sleep 0.35
run_bounded 120 xcrun simctl io "$udid" screenshot "$second" > /dev/null
xcrun swift "$repo_root/scripts/validate-native-uikit-animation.swift" "$first" "$second"

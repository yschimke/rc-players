#!/usr/bin/env bash
# Render the native player under UIKit accessibility presentation settings. This complements the
# pure role policy test with a real simulator check for Dynamic Type and Increased Contrast.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$repo_root/scripts/simulator-boot.sh"
app="$repo_root/build/apple-player-derived-data/Build/Products/Release-iphonesimulator/Remote Compose.app"
screenshot="${RC_NATIVE_ACCESSIBILITY_SCREENSHOT:-$repo_root/build/native-uikit-accessibility.png}"

if [ ! -d "$app" ]; then
  echo "expected the packaged simulator app at: $app" >&2
  echo "run scripts/package-apple-player.sh first" >&2
  exit 1
fi

minimum_os="$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$app/Info.plist")"
udid="$({ xcrun simctl list devices available --json; } | python3 -c '
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
print(ordered[0]["udid"])
' "$minimum_os")"

started=false
if ! xcrun simctl list devices | grep -F "$udid" | grep -q '(Booted)'; then
  xcrun simctl boot "$udid"
  started=true
fi
rc_await_boot "$udid"
previous_contrast="$(xcrun simctl ui "$udid" increase_contrast)"
previous_size="$(xcrun simctl ui "$udid" content_size)"

cleanup() {
  xcrun simctl ui "$udid" increase_contrast "$previous_contrast" >/dev/null 2>&1 || true
  xcrun simctl ui "$udid" content_size "$previous_size" >/dev/null 2>&1 || true
  if [ "$started" = true ]; then
    xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

xcrun simctl ui "$udid" increase_contrast enabled
xcrun simctl ui "$udid" content_size accessibility-large
RC_APPLE_PLAYER_RENDERER=native \
  RC_APPLE_PLAYER_SCREENSHOT="$screenshot" \
  "$repo_root/scripts/check-apple-player-screenshot.sh"
echo "native UIKit accessibility simulator: ok ($screenshot)"

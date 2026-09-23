#!/usr/bin/env bash
# Run the native player's SwiftPM test target on an iOS simulator.
#
# `RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift test` covers the macOS host. This is the UIKit half: it
# compiles `RcNativePlayerCore` and `RcNativePlayerUIKit` for the iOS Simulator — including the
# `#if canImport(UIKit)` renderer a macOS build never sees — and runs `RcNativePlayerUIKitTests`
# there. No app host is involved; the simulator UI checks stay in their own scripts.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
derived_data="$repo_root/build/native-swift-ios-tests"
result="$repo_root/build/native-swift-ios-tests.xcresult"
. "$repo_root/scripts/simulator-boot.sh"

devices_json="$(mktemp "${TMPDIR:-/tmp}/rc-native-swift-ios-devices.XXXXXX")"
trap 'rm -f "$devices_json"' EXIT
rc_run_bounded 120 xcrun simctl list devices available --json > "$devices_json"
# The package supports iOS 13, so any available iOS runtime will do. A booted device is reused, and
# an iPhone is preferred only because it is the cheapest to boot.
device="$(python3 -c '
import json, re, sys
devices = []
for runtime, values in json.load(sys.stdin)["devices"].items():
    if re.search(r"\.iOS-\d+-\d+$", runtime):
        devices.extend(d for d in values if d.get("isAvailable"))
ordered = (
    [d for d in devices if d["state"] == "Booted"]
    + [d for d in devices if d["name"].startswith("iPhone")]
    + devices
)
if not ordered:
    raise SystemExit("no available iOS simulator")
print(ordered[0]["udid"], ordered[0]["state"], sep="\t")
' < "$devices_json")"
IFS=$'\t' read -r udid state <<< "$device"

started=false
if [ "$state" != "Booted" ]; then
  rc_run_bounded 300 xcrun simctl boot "$udid"
  started=true
fi
cleanup() {
  rm -f "$devices_json"
  if [ "$started" = true ]; then
    rc_run_bounded 120 xcrun simctl shutdown "$udid" > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
rc_await_boot "$udid"

# The root manifest in native-only mode, so the Kotlin binary target is never resolved. SwiftPM's
# generated `<package>-Package` scheme is the one that carries the test targets.
rm -rf "$result"
cd "$repo_root"
RC_COMPOSE_PLAYER_NATIVE_ONLY=1 xcodebuild test -quiet \
  -scheme RcComposePlayer-Package \
  -destination "platform=iOS Simulator,id=$udid" \
  -derivedDataPath "$derived_data" \
  -resultBundlePath "$result" \
  -only-testing:RcNativePlayerUIKitTests \
  CODE_SIGNING_ALLOWED=NO

echo "native Swift iOS simulator tests: ok ($result)"

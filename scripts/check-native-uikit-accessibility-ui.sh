#!/usr/bin/env bash
# Exercise the native UIKit accessibility tree through XCUITest on a real iOS simulator.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project="$repo_root/samples/apple-player/RemoteComposePlayer.xcodeproj"
framework="$repo_root/rc-player/compose/build/XCFrameworks/release/RcComposePlayer.xcframework"
derived_data="$repo_root/build/apple-player-ui-tests"
result="$repo_root/build/native-uikit-accessibility-ui.xcresult"
first_attempt_result="$repo_root/build/native-uikit-accessibility-ui-first-attempt.xcresult"
. "$repo_root/scripts/simulator-boot.sh"

if [ ! -d "$framework" ]; then
  echo "expected a locally assembled framework at: $framework" >&2
  exit 1
fi

device="$({ xcrun simctl list devices available --json; } | python3 -c '
import json, re, sys
devices = []
for runtime, values in json.load(sys.stdin)["devices"].items():
    match = re.search(r"\.iOS-(\d+)-(\d+)$", runtime)
    if match and tuple(map(int, match.groups())) >= (26, 0):
        devices.extend(d for d in values if d.get("isAvailable") and d["name"].startswith("iPad"))
preferred = [d for d in devices if "13-inch" in d["name"]]
ordered = [d for d in devices if d["state"] == "Booted"] + preferred + devices
if not ordered:
    raise SystemExit("no available iPad simulator supports iOS 26 or newer")
print(ordered[0]["udid"], ordered[0]["state"], sep="\t")
')"
IFS=$'\t' read -r udid state <<< "$device"
started=false
if [ "$state" != "Booted" ]; then
  xcrun simctl boot "$udid"
  started=true
fi

cleanup() {
  if [ "$started" = true ]; then
    rc_run_bounded 120 xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

rc_await_boot "$udid"

run_tests() {
  local output="$1"
  rm -rf "$output"
  xcodebuild test -quiet \
    -project "$project" \
    -scheme RemoteComposePlayer \
    -destination "platform=iOS Simulator,id=$udid" \
    -derivedDataPath "$derived_data" \
    -resultBundlePath "$output" \
    -parallel-testing-enabled NO \
    -only-testing:RemoteComposePlayerUITests/NativeAccessibilityUITests
}

rm -rf "$first_attempt_result"
if ! run_tests "$result"; then
  if [ -d "$result" ]; then
    mv "$result" "$first_attempt_result"
    echo "native UIKit accessibility UI tests failed; first-attempt summary:" >&2
    xcrun xcresulttool get test-results summary --path "$first_attempt_result" >&2 || true
  fi
  echo "resetting simulator and retrying once" >&2
  rc_run_bounded 120 xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  rc_run_bounded 120 xcrun simctl erase "$udid"
  rc_run_bounded 300 xcrun simctl boot "$udid"
  started=true
  rc_await_boot "$udid"
  run_tests "$result"
fi

echo "native UIKit accessibility UI tests: ok ($result)"

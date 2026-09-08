#!/usr/bin/env bash
set -euo pipefail

bundle_dir="$(cd "$(dirname "$0")" && pwd)"
app="$bundle_dir/Remote Compose.app"
bundle_id="ee.schimke.rcplayers.appleplayer"

if ! xcrun simctl list devices booted | grep -q '(Booted)'; then
  echo "No iOS Simulator is booted. Open Simulator, start an iPad, then run this script again." >&2
  exit 1
fi

xcrun simctl install booted "$app"
xcrun simctl launch booted "$bundle_id"

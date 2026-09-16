#!/usr/bin/env bash
# Capture reproducible native-player timing, hierarchy, memory, allocation, and binary-size evidence
# from the packaged Release app on one fixed iPad simulator configuration.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app="$repo_root/build/apple-player-derived-data/Build/Products/Release-iphonesimulator/Remote Compose.app"
output="${RC_NATIVE_EVIDENCE_OUTPUT:-$repo_root/build/native-uikit-evidence.json}"
bundle_id="ee.schimke.rcplayers.appleplayer"

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
boot_logs=()

cleanup() {
  if [ "${#boot_logs[@]}" -gt 0 ]; then
    rm -f "${boot_logs[@]}"
  fi
  if [ "$started" = true ]; then
    xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# Booting the runner's simulator is the least reliable thing in this job. It has now failed twice
# in one day: once here, against this deadline, and once by wedging an unbounded `simctl` call in
# check-native-uikit-animation-simulator.sh until the job's 90-minute ceiling. The deadline stays —
# a boot that never completes must not eat the whole job — but a first failure now escalates and
# tries again rather than failing a run on a daemon that simply was not ready.
#
# The retry is deliberately narrow: it covers a simulator that will not come up, which is the
# runner's state and not something this repository can assert about. A second failure is real.
await_boot() {
  local attempt="$1"
  local log
  log="$(mktemp)"
  boot_logs+=("$log")
  xcrun simctl bootstatus "$udid" -b > "$log" 2>&1 &
  local pid=$!
  local deadline=$((SECONDS + 120))
  while kill -0 "$pid" > /dev/null 2>&1; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      kill "$pid" > /dev/null 2>&1 || true
      wait "$pid" > /dev/null 2>&1 || true
      echo "boot attempt $attempt: $udid did not finish booting within 120 seconds" >&2
      return 1
    fi
    sleep 1
  done
  if ! wait "$pid"; then
    echo "boot attempt $attempt: $udid reported a boot failure" >&2
    return 1
  fi
}

if [ "$state" != "Booted" ]; then
  xcrun simctl boot "$udid"
  started=true
fi

if ! await_boot 1; then
  cat "${boot_logs[@]}" >&2
  # Erase rather than merely reboot: a simulator that hangs on first boot is usually wedged in its
  # own state, and these runners are ephemeral so there is nothing here worth keeping.
  echo "erasing and retrying simulator $udid" >&2
  xcrun simctl shutdown "$udid" > /dev/null 2>&1 || true
  xcrun simctl erase "$udid" > /dev/null 2>&1 || true
  xcrun simctl boot "$udid"
  started=true
  if ! await_boot 2; then
    cat "${boot_logs[@]}" >&2
    echo "simulator $udid did not boot on either attempt; this is a runner failure" >&2
    exit 1
  fi
  # Said out loud so a recurring flake stays visible rather than being absorbed silently.
  echo "simulator $udid booted only on the second attempt, after an erase" >&2
fi

xcrun simctl uninstall "$udid" "$bundle_id" >/dev/null 2>&1 || true
xcrun simctl install "$udid" "$app"
revision="$(git -C "$repo_root" rev-parse HEAD)"
SIMCTL_CHILD_RC_SOURCE_REVISION="$revision" \
  SIMCTL_CHILD_RC_EVIDENCE_DEVICE="$device_name" \
  xcrun simctl launch "$udid" "$bundle_id" --native-evidence >/dev/null

container="$(xcrun simctl get_app_container "$udid" "$bundle_id" data)"
evidence="$container/Documents/native-uikit-evidence.json"
failure="$container/Documents/native-uikit-evidence-error.txt"
deadline=$((SECONDS + 60))
while [ ! -f "$evidence" ]; do
  if [ -f "$failure" ]; then
    echo "native UIKit evidence failed: $(cat "$failure")" >&2
    exit 1
  fi
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "native UIKit evidence was not written within 60 seconds" >&2
    exit 1
  fi
  sleep 1
done

mkdir -p "$(dirname "$output")"
cp "$evidence" "$output"
python3 - "$output" "$revision" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
report = json.loads(path.read_text())
assert report["schemaVersion"] == 1
assert report["sourceRevision"] == sys.argv[2]
assert report["iterations"] >= 5
assert report["metrics"]["nativeViewCount"] > 1
assert report["metrics"]["labelCount"] > 0
assert report["metrics"]["buttonCount"] > 0
assert report["metrics"]["exposedAccessibilityElementCount"] == 1
assert report["metrics"]["executableBytes"] > 0
assert report["metrics"]["appBundleBytes"] > 0
assert report["lifecycle"]["resumedAfterBackground"]
assert report["lifecycle"]["loadedSubviewCount"] > 1
assert report["lifecycle"]["releasedAfterResume"]
assert report["passed"], f"native UIKit evidence exceeded its reviewed budgets: {report}"
PY
echo "native UIKit simulator evidence: ok ($output)"

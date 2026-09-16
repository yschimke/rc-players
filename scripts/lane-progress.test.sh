#!/usr/bin/env bash
# Exercise rc_await_lane_completion against a fake harness directory. No simulator and no macOS:
# the thing under test is the waiting rule, and a directory that gains files on a schedule
# reproduces every case that matters — finishing, finishing slowly, and stopping partway.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$repo_root/scripts/lane-progress.sh"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

expect() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "ok   $label"
  else
    echo "FAIL $label: expected '$expected', got '$actual'" >&2
    failures=$((failures + 1))
  fi
}

# A harness that writes results on a timer, then done.json. `count` results at `delay`s apart.
# Sets `harness_pid` rather than echoing it: a command substitution runs in a subshell, and the
# background job would not be a child of the caller, so `wait` could not reap it.
start_harness() {
  local root="$1" count="$2" delay="$3" finish="$4"
  mkdir -p "$root/output"
  (
    local i=0
    while [ "$i" -lt "$count" ]; do
      sleep "$delay"
      : > "$root/output/doc-$i.png"
      i=$((i + 1))
    done
    if [ "$finish" = true ]; then
      : > "$root/done.json"
    fi
  ) &
  harness_pid=$!
}

# A lane that finishes reports how many results it produced.
root="$work/complete"
start_harness "$root" 3 1 true
pid="$harness_pid"
progress="$(rc_await_lane_completion "$root" 3 10)"
wait "$pid"
expect "a completed lane reports its result count" "3" "$progress"

# The budget is per-stall, not total: eight documents a second apart outlive any total deadline
# this stall budget could stand in for, and still pass.
root="$work/slow"
start_harness "$root" 8 1 true
pid="$harness_pid"
progress="$(rc_await_lane_completion "$root" 8 3)"
wait "$pid"
expect "steady progress survives a budget shorter than the total runtime" "8" "$progress"

# A harness that stops partway fails, and says how far it got rather than only that it failed.
root="$work/stalled"
start_harness "$root" 2 1 false
pid="$harness_pid"
status=0
progress="$(rc_await_lane_completion "$root" 700 3 2>"$work/stalled.err")" || status=$?
wait "$pid"
expect "a stalled lane fails" "1" "$status"
expect "a stalled lane reports its progress" "2" "$progress"
expect "the stall message names how far the lane got" "1" \
  "$(grep -c 'stalled at 2 of 700 documents' "$work/stalled.err")"

# A lane that keeps producing results but never finishes is capped by the total budget. This is the
# case the stall rule alone cannot catch, and the one that ate 51 minutes of a 90-minute macOS job:
# every result resets the stall deadline, so without a total budget the wait never ends.
root="$work/endless"
start_harness "$root" 40 1 false
pid="$harness_pid"
status=0
progress="$(rc_await_lane_completion "$root" 700 10 4 2>"$work/endless.err")" || status=$?
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
expect "a lane that never finishes is capped" "1" "$status"
expect "the cap message names how far the lane got" "1" \
  "$(grep -c 'was still going; stopping so it cannot consume the whole job' "$work/endless.err")"
if [ "$progress" -ge 2 ] && [ "$progress" -le 6 ]; then
  echo "ok   the capped lane reports the progress it had made ($progress)"
else
  echo "FAIL the capped lane reported implausible progress: $progress" >&2
  failures=$((failures + 1))
fi

# A harness that never writes anything at all is a stall from the start, not a hang.
root="$work/silent"
mkdir -p "$root/output"
status=0
progress="$(rc_await_lane_completion "$root" 700 2 2>/dev/null)" || status=$?
expect "a lane that never starts fails" "1" "$status"
expect "a lane that never starts reports zero progress" "0" "$progress"

if [ "$failures" -ne 0 ]; then
  echo "$failures expectation(s) failed" >&2
  exit 1
fi
echo "all lane-progress expectations passed"

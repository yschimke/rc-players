#!/usr/bin/env bash
# Shared simulator-boot handling for the macOS lanes. Source it; do not execute it.
#
# Booting the runner's simulator is the least reliable thing in this job, and it has now failed
# three times on three different scripts — the same wedged UDID each time. Each script had grown its
# own answer: one bounded the wait and retried, one bounded it and gave up, one bounded every call
# but never retried, and three did not bound anything at all. Which behaviour a step got therefore
# depended on which file it lived in, which is not a property anyone chose.
#
# So the behaviour lives here once. A step that boots a simulator calls `rc_await_boot`, and gets
# the deadline and the escalating retry whether or not anyone remembered to write them.

# Runs a command with a hard deadline, so a wedged `simctl` names itself quickly instead of sitting
# until the job's own ceiling with no output at all.
rc_run_bounded() {
  local seconds="$1"
  shift
  "$@" &
  local pid=$!
  local deadline=$((SECONDS + seconds))
  while kill -0 "$pid" > /dev/null 2>&1; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      kill -9 "$pid" > /dev/null 2>&1 || true
      wait "$pid" > /dev/null 2>&1 || true
      echo "simulator command timed out after ${seconds}s: $*" >&2
      return 1
    fi
    sleep 1
  done
  wait "$pid"
}

# Waits for a simulator to finish booting, and escalates once if it will not.
#
# The deadline stays — a boot that never completes must not eat the whole job — but a first failure
# now erases and tries again rather than failing a run on a daemon that simply was not ready. The
# erase is deliberate: a simulator that hangs on first boot is usually wedged in its own state, and
# these runners are ephemeral so there is nothing worth keeping. A second failure is real.
rc_await_boot() {
  local udid="$1"
  local timeout="${2:-120}"
  local log
  log="$(mktemp)"

  if rc_run_bounded "$timeout" xcrun simctl bootstatus "$udid" -b > "$log" 2>&1; then
    rm -f "$log"
    return 0
  fi
  cat "$log" >&2
  echo "simulator $udid did not boot within ${timeout}s; erasing and retrying" >&2

  rc_run_bounded 120 xcrun simctl shutdown "$udid" > /dev/null 2>&1 || true
  rc_run_bounded 120 xcrun simctl erase "$udid" > /dev/null 2>&1 || true
  if ! rc_run_bounded 300 xcrun simctl boot "$udid" > "$log" 2>&1; then
    cat "$log" >&2
    rm -f "$log"
    echo "simulator $udid could not be booted after an erase; this is a runner failure" >&2
    return 1
  fi
  if ! rc_run_bounded "$timeout" xcrun simctl bootstatus "$udid" -b > "$log" 2>&1; then
    cat "$log" >&2
    rm -f "$log"
    echo "simulator $udid did not boot on either attempt; this is a runner failure" >&2
    return 1
  fi
  rm -f "$log"
  # Said out loud so a recurring flake stays visible rather than being absorbed silently.
  echo "simulator $udid booted only on the second attempt, after an erase" >&2
}

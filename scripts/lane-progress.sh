#!/usr/bin/env bash
# Wait for a comparison-lane harness to finish, bounded by how long it goes without making
# progress rather than by how long it runs in total.
#
# A total deadline has to guess a per-document cost, and guessing low fails a lane that was working
# — which is exactly what a 30s + 1s/document budget did to the 701-document catalog corpus: it
# expired mid-run and discarded every rendered document, so the failure said nothing about whether
# the harness was slow or stopped. Progress is the signal that actually matters here. A harness
# that writes a result every few seconds is healthy however long the manifest is; one that writes
# nothing for two minutes is stuck, at four documents or at seven hundred.

# rc_await_lane_completion <harness-root> <documents> [stall-budget-seconds] [total-budget-seconds]
#                          [heartbeat-seconds]
#
# Succeeds once <harness-root>/done.json exists. Fails if <harness-root>/output gains no new entry
# within the stall budget, or if the whole wait outlives the total budget, naming how far the lane
# got either way. Echoes the final progress count on every path so the caller can report it.
#
# It also says where it is as it goes. A 55-minute corpus run produced a log that read, in full:
#
#     ==> native UIKit corpus lane
#     inputs: 701/701 manifest documents
#     ##[error]The operation was canceled.
#
# Fifty-five minutes of macOS runner time, and the only thing derivable from it was that the stall
# deadline never fired — so the lane was somewhere between 28 and 700 documents, which is not a
# measurement. A lane that only speaks when it fails cannot be told apart from one that is stuck.
#
# The total budget is a cap on this lane's share of the job, not a guess at a per-document cost —
# that distinction is the whole point. Bounding only stalls turned out to be half a rule: a lane
# that keeps producing results slowly never trips a stall, and on the catalog corpus one consumed
# 51 minutes of a 90-minute job, which is the four validation steps after it never running. A
# healthy lane finishes far inside this; one that does not has told you something either way.
rc_await_lane_completion() {
  local harness_root="$1"
  local documents="$2"
  local stall_budget="${3:-120}"
  local total_budget="${4:-1200}"
  local heartbeat="${5:-60}"
  local progress=0
  local current
  local elapsed
  local started="$SECONDS"
  local deadline=$((SECONDS + stall_budget))
  local next_heartbeat=$((SECONDS + heartbeat))

  while [ ! -f "$harness_root/done.json" ]; do
    current="$(find "$harness_root/output" -type f 2>/dev/null | wc -l | tr -d '[:space:]')"
    if [ "$current" -gt "$progress" ]; then
      progress="$current"
      deadline=$((SECONDS + stall_budget))
    fi
    # On stderr, because this function's stdout is the progress count the caller captures.
    if [ "$heartbeat" -gt 0 ] && [ "$SECONDS" -ge "$next_heartbeat" ]; then
      elapsed=$((SECONDS - started))
      echo "lane progress: $progress of $documents after ${elapsed}s" >&2
      next_heartbeat=$((SECONDS + heartbeat))
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "error: lane stalled at $progress of $documents documents;" \
        "no new result for ${stall_budget}s" >&2
      echo "$progress"
      return 1
    fi
    if [ $((SECONDS - started)) -ge "$total_budget" ]; then
      echo "error: lane reached $progress of $documents documents in ${total_budget}s" \
        "and was still going; stopping so it cannot consume the whole job" >&2
      echo "$progress"
      return 1
    fi
    sleep 1
  done

  find "$harness_root/output" -type f 2>/dev/null | wc -l | tr -d '[:space:]'
}

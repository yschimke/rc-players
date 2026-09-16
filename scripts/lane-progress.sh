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
#
# Succeeds once <harness-root>/done.json exists. Fails if <harness-root>/output gains no new entry
# within the stall budget, or if the whole wait outlives the total budget, naming how far the lane
# got either way. Echoes the final progress count on every path so the caller can report it.
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
  local progress=0
  local current
  local started="$SECONDS"
  local deadline=$((SECONDS + stall_budget))

  while [ ! -f "$harness_root/done.json" ]; do
    current="$(find "$harness_root/output" -type f 2>/dev/null | wc -l | tr -d '[:space:]')"
    if [ "$current" -gt "$progress" ]; then
      progress="$current"
      deadline=$((SECONDS + stall_budget))
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

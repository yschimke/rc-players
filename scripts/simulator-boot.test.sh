#!/usr/bin/env bash
# Exercises scripts/simulator-boot.sh against a stubbed `xcrun`.
#
# The boot retry only ever runs on a macOS runner, against the one thing in the job nobody can
# reproduce on demand — which is how three scripts ended up with three different answers to it. A
# stub cannot tell us how a real simulator behaves, but it can hold the control flow to its
# contract: when the retry fires, when it does not, and what each path returns. That is the part
# that was actually wrong.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/simulator-boot-test.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"

# `bootstatus` reads its behaviour from a script of outcomes, one word per call: `ok` returns
# immediately, `hang` sleeps past any deadline the caller sets. Every call is appended to a log so a
# test can assert what was actually invoked, which is how "did it erase?" is answered.
cat > "$work/bin/xcrun" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "$RC_STUB_LOG"
if [ "${2:-}" = "bootstatus" ]; then
  outcome="$(head -n 1 "$RC_STUB_PLAN")"
  tail -n +2 "$RC_STUB_PLAN" > "$RC_STUB_PLAN.rest" && mv "$RC_STUB_PLAN.rest" "$RC_STUB_PLAN"
  case "$outcome" in
    hang) sleep 30 ;;
    # Longer than the first deadline, shorter than the post-erase one: only a caller that gives the
    # second attempt its own larger budget gets through this.
    slow) sleep 3 ;;
  esac
  exit 0
fi
exit 0
STUB
chmod +x "$work/bin/xcrun"
PATH="$work/bin:$PATH"
# shellcheck source=scripts/simulator-boot.sh
. "$repo_root/scripts/simulator-boot.sh"

failures=0
check() {
  if [ "$2" = "$3" ]; then
    echo "ok: $1"
  else
    echo "FAIL: $1 — expected '$3', got '$2'" >&2
    failures=$((failures + 1))
  fi
}

run_case() {
  export RC_STUB_LOG="$work/log"
  export RC_STUB_PLAN="$work/plan"
  : > "$RC_STUB_LOG"
  printf '%s\n' "$@" > "$RC_STUB_PLAN"
}

# A simulator that comes up first time is not erased, and nothing is said about it.
run_case ok
status=0
rc_await_boot SIM 2 2> "$work/err" || status=$?
check "clean boot succeeds" "$status" "0"
check "clean boot does not erase" "$(grep -c erase "$work/log" || true)" "0"
check "clean boot is quiet" "$(wc -c < "$work/err" | tr -d ' ')" "0"

# A first failure escalates: shutdown, erase, boot, and a second wait — then succeeds, loudly.
run_case hang ok
status=0
rc_await_boot SIM 2 6 2> "$work/err" || status=$?
check "retried boot succeeds" "$status" "0"
check "retried boot erases once" "$(grep -c 'simctl erase' "$work/log" || true)" "1"
# Matched whole-line: `simctl bootstatus` also contains "simctl boot".
check "retried boot re-boots" "$(grep -cx 'simctl boot SIM' "$work/log" || true)" "1"
check "retried boot says so" \
  "$(grep -c 'booted only on the second attempt' "$work/err" || true)" "1"

# The post-erase wait gets its own, larger budget. An erased device's next boot is the slowest one
# it will ever do, and the first run of this helper in CI failed precisely because the retry was
# held to the first attempt's deadline. A boot that is too slow for the first budget but fine for
# the second must succeed.
run_case hang slow
status=0
rc_await_boot SIM 2 6 2> "$work/err" || status=$?
check "post-erase boot gets a larger budget" "$status" "0"

# A second failure is real, and says which kind of failure it is.
run_case hang hang
status=0
rc_await_boot SIM 2 6 2> "$work/err" || status=$?
check "twice-failed boot fails" "$status" "1"
check "twice-failed boot names the runner" \
  "$(grep -c 'did not boot within 6s even after an erase' "$work/err" || true)" "1"

# A bounded command that finishes inside its deadline returns the command's own status, so a caller
# can still tell a timeout from a genuine non-zero exit.
status=0
rc_run_bounded 5 true || status=$?
check "bounded success returns 0" "$status" "0"
status=0
rc_run_bounded 5 false || status=$?
check "bounded failure propagates" "$status" "1"
status=0
rc_run_bounded 1 sleep 20 2> "$work/err" || status=$?
check "bounded hang times out" "$status" "1"
check "bounded hang names the command" "$(grep -c 'timed out after 1s' "$work/err" || true)" "1"

if [ "$failures" -ne 0 ]; then
  echo "$failures simulator-boot expectation(s) failed" >&2
  exit 1
fi
echo "simulator boot helper: ok"

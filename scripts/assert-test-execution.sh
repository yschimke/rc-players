#!/usr/bin/env bash
#
# Assert that the named test tasks actually executed tests.
#
# Gradle is happy to report success for a test task that ran nothing. `allTests` is an aggregate
# whose members are resolved per target on the runner, so a target that is unavailable there — no
# browser for `wasmJsBrowserTest`, no bootable simulator for `iosSimulatorArm64Test`, a source set
# that silently became NO-SOURCE — drops out of the graph and the job still goes green. That is not
# a hypothetical: it is exactly what made the coverage table in #77 an assumption rather than a
# fact, and the whole point of running the non-JVM lanes is that a silently-skipped one is worse
# than no lane at all, because it reads as coverage.
#
# So: read the JUnit XML each task writes and fail the job when a task the workflow named produced
# no executed test case.
#
#   scripts/assert-test-execution.sh rc-player/compose:jvmTest,macosArm64Test …
#
# Each argument is `<module-directory>:<task-name>[,<task-name>…]`; the reports are read from
# `<module-directory>/build/test-results/<task-name>/*.xml`, which is where both the Kotlin
# Multiplatform and the AGP/Java test tasks put them.

set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <module-directory>:<task>[,<task>…] …" >&2
  exit 2
fi

# Sum one numeric attribute across every `<testsuite …>` element in a directory of reports. The
# attribute appears on `<testsuite>` only — `<testcase>` carries `name`, `classname` and `time` —
# so a whitespace split and an anchored match is enough, and does not need an XML parser on a
# runner that may not have one.
sum_attribute() {
  local directory="$1" attribute="$2"
  cat "$directory"/*.xml \
    | tr -s ' \t\n' '\n' \
    | sed -n "s/^${attribute}=\"\([0-9][0-9]*\)\"\$/\1/p" \
    | awk '{ total += $1 } END { print total + 0 }'
}

status=0
printf '%-50s %8s %8s %8s\n' 'TEST TASK' 'TESTS' 'SKIPPED' 'FAILED'

for specification in "$@"; do
  module="${specification%%:*}"
  tasks="${specification#*:}"
  if [ "$module" = "$specification" ]; then
    echo "  malformed argument '$specification' (expected <module-directory>:<task>[,<task>…])" >&2
    status=1
    continue
  fi

  IFS=',' read -r -a task_names <<< "$tasks"
  for task in "${task_names[@]}"; do
    label="$module:$task"
    directory="$module/build/test-results/$task"

    if ! compgen -G "$directory/*.xml" > /dev/null; then
      printf '%-50s %8s %8s %8s  <- no reports under %s\n' "$label" '-' '-' '-' "$directory"
      status=1
      continue
    fi

    tests="$(sum_attribute "$directory" tests)"
    skipped="$(sum_attribute "$directory" skipped)"
    failures="$(sum_attribute "$directory" failures)"
    errors="$(sum_attribute "$directory" errors)"
    failed=$((failures + errors))
    executed=$((tests - skipped))

    printf '%-50s %8s %8s %8s' "$label" "$tests" "$skipped" "$failed"
    if [ "$executed" -le 0 ]; then
      printf '  <- ran nothing'
      status=1
    fi
    if [ "$failed" -gt 0 ]; then
      # The test task itself would already have failed the build; reaching here means the reports
      # were read after a `continue-on-error` step or a hand-run invocation, and a green summary
      # would be a lie either way.
      printf '  <- reported failures'
      status=1
    fi
    printf '\n'
  done
done

if [ "$status" -ne 0 ]; then
  echo
  echo 'A test task named by the workflow executed no tests (or reported failures). A target that' >&2
  echo 'cannot run on this runner must be removed from the workflow, not left to pass silently.' >&2
fi

exit "$status"

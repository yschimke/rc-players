#!/usr/bin/env bash
# Dry-runs the real `publishPlayers` for the three shapes of `-Pcomposeai.publishSet` the release job
# passes, and checks which `publishToMavenCentral` tasks each schedules. Run from the repository
# root; ci.yml runs it in the `jvm` job.
#
#   absent       no plan ran (a recovery run): every module AND the BOM
#   empty        the plan ran and found nothing: NO uploads, not even `:bom` — an identical BOM at a
#                new version would spend a Central deployment to say nothing (compose-ai-tools#5532)
#   one module   that module and the BOM, which indexes the new coordinate
set -euo pipefail

GRADLEW="${GRADLEW:-./gradlew}"
MANIFEST=publishing-manifest.json
fail=0

# A publish set makes every skipped module read its version from `publishing-manifest.json`, which
# the release job's plan writes and nothing commits. Stand one in, and put back whatever was there.
backup=""
if [ -e "$MANIFEST" ]; then
  backup="$(mktemp)"
  cp "$MANIFEST" "$backup"
fi
restore() {
  if [ -n "$backup" ]; then mv "$backup" "$MANIFEST"; else rm -f "$MANIFEST"; fi
}
trap restore EXIT

# The Central upload tasks `publishPlayers` would run, one project path per line. A Gradle failure
# must fail the test, not read as an empty list — which is exactly the answer the empty case wants.
uploads() {
  local out
  # `--dry-run` executes nothing, but the Central repository still wants credentials to configure;
  # placeholders satisfy it and could not upload even if something did run.
  out="$(ORG_GRADLE_PROJECT_mavenCentralUsername=dry-run ORG_GRADLE_PROJECT_mavenCentralPassword=dry-run \
    "$GRADLEW" publishPlayers --dry-run --no-configuration-cache "$@" 2>&1)" ||
    { printf '%s\n' "$out" >&2; echo "::error::publishPlayers --dry-run $* failed" >&2; exit 1; }
  printf '%s\n' "$out" | sed -n 's/^\(:[^ ]*\):publishToMavenCentral SKIPPED$/\1/p' | sort
}

expect() {
  local name="$1" want="$2" got="$3"
  if [ "$got" = "$want" ]; then
    echo "ok: $name"
  else
    echo "::error::publishPlayers $name"
    echo "  want: $(printf '%s' "$want" | tr '\n' ' ')"
    echo "  got:  $(printf '%s' "$got" | tr '\n' ' ')"
    fail=1
  fi
}

rm -f "$MANIFEST"
all="$(uploads)"
expect "with no publish set uploads the BOM" "yes" \
  "$(grep -qx ':bom' <<<"$all" && echo yes || echo no)"
expect "with no publish set uploads every module" "yes" \
  "$([ "$(grep -cvx ':bom' <<<"$all")" -gt 1 ] && echo yes || echo no)"

# Every module recorded as already published, as the plan's `--write-manifest` would leave it.
ids="$("$GRADLEW" -q printPublishSet)"
{
  echo '{"modules": {'
  sed 's/.*/  "&": "0.0.1"/' <<<"$ids" | paste -sd, -
  echo '}}'
} > "$MANIFEST"

expect "with an empty publish set uploads nothing" "" "$(uploads -Pcomposeai.publishSet=)"

expect "with one module uploads it and the BOM" \
  "$(printf '%s\n' :bom :rc-player-compose | sort)" \
  "$(uploads -Pcomposeai.publishSet=rc-player-compose)"

exit "$fail"

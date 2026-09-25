#!/usr/bin/env bash
# Fixture tests for maven-publish-plan.sh.
#
# Builds a throwaway repository shaped like this one — a published library, an UNPUBLISHED module
# that depends on it, a published dist that packages the unpublished one, and dists that read
# directories outside their own — tags it, then checks which modules the plan publishes after each
# kind of change. Runs against a `--manifest`, so it never talks to Maven Central.
#
# The first two cases are the ones #512 found shipping stale to Central: `rc-player-wasm-dist`
# (through the unpublished `:rc-player-wasm`) and `remote-compose-player-js-dist` (through a sibling
# directory with no build file).

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
plan="${repo_root}/.github/scripts/maven-publish-plan.sh"
fixture=$(mktemp -d)
trap 'rm -rf "${fixture}"' EXIT

g() { git -C "${fixture}/repo" -c user.name=fixture -c user.email=fixture@example.invalid \
  -c commit.gpgsign=false -c tag.gpgsign=false "$@"; }

published() {
  # published <dir> <artifactId> [extra build-file lines...]
  local dir="$1" aid="$2"
  shift 2
  mkdir -p "${fixture}/repo/${dir}/src"
  echo "// ${aid}" >"${fixture}/repo/${dir}/src/Main.kt"
  {
    echo 'plugins { id("composeai.maven-publishing") }'
    printf '%s\n' "$@"
    echo "composeAiMavenPublishing { coordinates(artifactId = \"${aid}\") }"
  } >"${fixture}/repo/${dir}/build.gradle.kts"
}

mkdir -p "${fixture}/repo"
g init -q -b main
cat >"${fixture}/repo/settings.gradle.kts" <<'EOF'
include(":lib")

project(":lib").projectDir = file("lib")

include(":app")

project(":app").projectDir = file("app")

include(":app-dist")

project(":app-dist").projectDir = file("app-dist")

include(":js-dist")

project(":js-dist").projectDir =
  file("third_party/js-dist")

include(":literal-dist")

project(":literal-dist").projectDir = file("literal-dist")

include(":other")

project(":other").projectDir = file("other")
EOF
published lib lib
# Unpublished, like `:rc-player-wasm`: no maven-publishing plugin, depends on a published module.
mkdir -p "${fixture}/repo/app/src"
echo '// app' >"${fixture}/repo/app/src/Main.kt"
echo 'dependencies { implementation(project(":lib")) }' >"${fixture}/repo/app/build.gradle.kts"
# Packages the unpublished module's output, like `:rc-player-wasm-dist`.
published app-dist app-dist 'tasks.register("zip") { dependsOn(":app:dist"); from(project(":app").layout.buildDirectory) }'
# Reads a sibling directory by a path the scan cannot see: covered only by EXTRA_INPUTS.
published third_party/js-dist remote-compose-player-js-dist 'val base = ".."' 'from(layout.projectDirectory.dir(base + "/remote-compose-player/dist"))'
mkdir -p "${fixture}/repo/third_party/remote-compose-player/dist"
echo 'bundle v1' >"${fixture}/repo/third_party/remote-compose-player/dist/bundle.js"
# Reads a sibling directory through a `"../…"` literal, like `:third-party-rc-embedded-player-jvm`.
published literal-dist literal-dist 'sourceSets { main { srcDir("../vendored/src") } }'
mkdir -p "${fixture}/repo/vendored/src"
echo '// vendored' >"${fixture}/repo/vendored/src/V.kt"
published other other
mkdir -p "${fixture}/repo/docs"
echo 'docs' >"${fixture}/repo/docs/README.md"
g add -A
g commit -q -m base
g tag v1.0.0

cat >"${fixture}/manifest.json" <<'EOF'
{"modules": {"lib": "1.0.0", "app-dist": "1.0.0", "remote-compose-player-js-dist": "1.0.0",
             "literal-dist": "1.0.0", "other": "1.0.0"}}
EOF

failures=0
check() {
  # check [--released] <name> <expected, space-separated> <change command...>
  #
  # --released tags the change as v1.1.0 and records every module as published there, so nothing
  # has changed since any baseline: whatever still publishes is publishing because the plan could
  # not tell, which is what the fail-open cases need to isolate.
  local released=false
  if [ "$1" = "--released" ]; then released=true; shift; fi
  local name="$1" expected="$2" manifest="${fixture}/manifest.json"
  shift 2
  g checkout -q --detach v1.0.0
  (cd "${fixture}/repo" && "$@")
  g add -A
  g commit -q -m "${name}"
  if [ "${released}" = true ]; then
    g tag -f v1.1.0 >/dev/null
    manifest="${fixture}/manifest-1.1.0.json"
    sed 's/1\.0\.0/1.1.0/g' "${fixture}/manifest.json" >"${manifest}"
  fi
  local actual
  if ! actual=$(cd "${fixture}/repo" && "${plan}" --head HEAD --manifest "${manifest}" 2>"${fixture}/stderr" | paste -sd' ' -); then
    echo "FAIL ${name}: plan exited non-zero" >&2
    cat "${fixture}/stderr" >&2
    failures=$((failures + 1))
    return
  fi
  if [ "${actual}" = "${expected}" ]; then
    echo "ok   ${name}: ${actual:-<none>}"
  else
    echo "FAIL ${name}: expected '${expected:-<none>}', got '${actual:-<none>}'" >&2
    cat "${fixture}/stderr" >&2
    failures=$((failures + 1))
  fi
}

check "library change reaches a dist through an unpublished module" "app-dist lib" \
  sh -c 'echo "// changed" >>lib/src/Main.kt'
check "unpublished module change reaches the dist that packages it" "app-dist" \
  sh -c 'echo "// changed" >>app/src/Main.kt'
check "declared extra input dirties js-dist" "remote-compose-player-js-dist" \
  sh -c 'echo "bundle v2" >third_party/remote-compose-player/dist/bundle.js'
check "a \"../\" literal in the build file is an input" "literal-dist" \
  sh -c 'echo "// changed" >>vendored/src/V.kt'
check "a change outside every input publishes nothing" "" \
  sh -c 'echo "more docs" >>docs/README.md'
check --released "an unresolvable project edge fails open" "app-dist" \
  sh -c 'echo "dependencies { implementation(project(\":missing\")) }" >>app/build.gradle.kts'
check --released "an input escaping the repository fails open" "other" \
  sh -c 'echo "val x = file(\"../../outside\")" >>other/build.gradle.kts'

if [ "${failures}" -ne 0 ]; then
  echo "${failures} publish-plan fixture case(s) failed" >&2
  exit 1
fi

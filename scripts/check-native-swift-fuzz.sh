#!/usr/bin/env bash
# Mutation-fuzz the pure-Swift document core against every supported comparative fixture.
#
# The corpus is derived, not committed: each bundled fixture plus a handful of synthetic seeds is
# expanded by a deterministic PRNG into truncated, corrupted, spliced, and extreme-valued variants.
# Every case must either decode into a bounded snapshot or fail with a typed NativeSwiftCoreError —
# a trap, an untyped error, or a hang fails the run and the offending bytes are written out.
#
# This runs `NativeSwiftFuzzTests` from the SwiftPM test target. A plain
# `RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift test` runs the same test at the default iteration count;
# this script exists for the knobs below.
#
# Reproducing a failure:
#   RC_NATIVE_FUZZ_SEED=<seed from the failing run> scripts/check-native-swift-fuzz.sh
#
# Longer local soak, and the sanitized pass:
#   RC_NATIVE_FUZZ_ITERATIONS=5000 scripts/check-native-swift-fuzz.sh
#   RC_NATIVE_FUZZ_SANITIZE=address scripts/check-native-swift-fuzz.sh
#
# Environment:
#   RC_NATIVE_FUZZ_ITERATIONS  mutations per seed (default 96)
#   RC_NATIVE_FUZZ_SEED        PRNG seed; the same value replays the same corpus
#   RC_NATIVE_FUZZ_SANITIZE    address | thread | undefined (default: none)
#   RC_NATIVE_FUZZ_CORPUS_OUT  directory failing inputs are written to
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# An unsanitized run is optimized, as the standalone fuzz binary was; a sanitized run is not, so
# the sanitizer's reports point at real lines. `-enable-testing` keeps `@testable import` working
# in the release configuration.
build=(-c release -Xswiftc -enable-testing)
if [ -n "${RC_NATIVE_FUZZ_SANITIZE:-}" ]; then
  build=(-c debug "--sanitize=${RC_NATIVE_FUZZ_SANITIZE}")
fi

RC_COMPOSE_PLAYER_NATIVE_ONLY=1 swift test \
  --package-path "$repo_root" \
  "${build[@]}" \
  --filter 'NativeSwiftFuzzTests'

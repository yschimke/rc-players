#!/usr/bin/env bash
# Mutation-fuzz the pure-Swift document core against every supported comparative fixture.
#
# The corpus is derived, not committed: each fixture below plus a handful of synthetic seeds is
# expanded by a deterministic PRNG into truncated, corrupted, spliced, and extreme-valued variants.
# Every case must either decode into a bounded snapshot or fail with a typed NativeSwiftCoreError —
# a trap, an untyped error, or a hang fails the run and the offending bytes are written out.
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
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-native-swift-fuzz.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/module-cache"

compile=(-O)
if [ -n "${RC_NATIVE_FUZZ_SANITIZE:-}" ]; then
  compile=(-Onone -g "-sanitize=${RC_NATIVE_FUZZ_SANITIZE}")
fi

CLANG_MODULE_CACHE_PATH="$work/module-cache" \
SWIFT_MODULECACHE_PATH="$work/module-cache" \
xcrun swiftc \
  "${compile[@]}" \
  "$repo_root"/Sources/RcNativePlayerCore/*.swift \
  "$repo_root/Tests/RcNativePlayerUIKitTests/NativeSwiftFuzzTests.swift" \
  -o "$work/native-swift-fuzz-tests"

fixtures=(
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/editable-text.rc"
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/TitleCardRemote-640x480.rc"
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/IndeterminateCircularProgress-400x400.rc"
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/CircularProgressRemote-384x384.rc"
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/ArcProgressRemote-454x400.rc"
  "$repo_root/Tests/RcNativePlayerUIKitTests/Fixtures/ImageBackgroundRemoteButton-454x200.rc"
)
for fixture in "${fixtures[@]}"; do
  if [ ! -f "$fixture" ]; then
    echo "missing fuzz seed fixture: $fixture" >&2
    exit 1
  fi
done

"$work/native-swift-fuzz-tests" "${fixtures[@]}"

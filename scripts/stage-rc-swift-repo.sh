#!/usr/bin/env bash
# Stage the pure-Swift SwiftPM mirror repository (`yschimke/rc-swift`) into a directory.
#
# Usage:
#   scripts/stage-rc-swift-repo.sh <output-dir> [profile-json]
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "usage: $0 <output-dir> [profile-json]" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="$1"
profile_json="${2:-}"

rm -rf "$out_dir"
mkdir -p "$out_dir/Sources"

cp "$repo_root/distribution/rc-swift/Package.swift" "$out_dir/Package.swift"
cp "$repo_root/distribution/rc-swift/README.md" "$out_dir/README.md"
cp "$repo_root/LICENSE" "$out_dir/LICENSE"
if [ -f "$repo_root/.swift-format" ]; then
  cp "$repo_root/.swift-format" "$out_dir/.swift-format"
fi
if [ -n "$profile_json" ]; then
  cp "$profile_json" "$out_dir/PROFILE.json"
fi

targets=(
  RcNativePlayerCore
  RcNativePlayerUIKit
  RcPlayerAppleFonts
  RcWireOperations
  RcCreation
  RcCreationFoundation
  RcCreationAppleUI
  RcCreationHost
)

for target in "${targets[@]}"; do
  src="$repo_root/Sources/$target"
  if [ ! -d "$src" ]; then
    echo "missing expected Swift target directory: $src" >&2
    exit 1
  fi
  cp -R "$src" "$out_dir/Sources/$target"
done

# Sanity check: the mirror manifest must never declare a binary target or Kotlin framework.
if grep -En 'binaryTarget|RcComposePlayer\.xcframework|RcComposePlayerSwiftUI' "$out_dir/Package.swift"; then
  echo "rc-swift Package.swift must not reference binary targets or RcComposePlayer" >&2
  exit 1
fi

echo "staged rc-swift package at: $out_dir"

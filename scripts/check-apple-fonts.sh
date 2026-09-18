#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-apple-fonts.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/module-cache"

CLANG_MODULE_CACHE_PATH="$work/module-cache" \
SWIFT_MODULECACHE_PATH="$work/module-cache" \
xcrun swiftc \
  "$repo_root/Sources/RcPlayerAppleFonts/RemoteComposeDownloadableFonts.swift" \
  "$repo_root/Sources/RcPlayerAppleFonts/RemoteComposeFontVariation.swift" \
  "$repo_root/Tests/RcPlayerAppleFontsTests/RemoteComposeDownloadableFontsTests.swift" \
  -o "$work/apple-font-tests"
"$work/apple-font-tests"

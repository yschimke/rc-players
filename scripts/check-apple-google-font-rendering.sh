#!/usr/bin/env bash
# Render the same google:-family document with font downloading disabled and enabled.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${1:-$repo_root/build/apple-google-fonts}"
input_dir="$(mktemp -d -t apple-google-fonts-input-XXXXXX)"
generator="$(mktemp -t apple-google-fonts-generator-XXXXXX)"
module_cache="$(mktemp -d -t apple-google-fonts-modules-XXXXXX)"
trap 'rm -rf "$input_dir" "$generator" "$module_cache"' EXIT

swiftc -parse-as-library -module-cache-path "$module_cache" \
  "$repo_root/scripts/apple-google-fonts/GenerateFixture.swift" -o "$generator"
cp "$repo_root/scripts/apple-google-fonts/manifest.json" "$input_dir/manifest.json"
"$generator" "$input_dir/google-font-fallback.rc"
cp "$input_dir/google-font-fallback.rc" "$input_dir/google-font-downloaded.rc"

mkdir -p "$output_dir"
"$repo_root/scripts/render-native-uikit-lane.sh" "$input_dir" "$output_dir"
echo "Apple Google Fonts rendering: $output_dir"

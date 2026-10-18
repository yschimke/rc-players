#!/usr/bin/env bash
# Maven packages dist/bundle.js, not the interactive viewer's web-player/bundle.js.
set -euo pipefail
cd "$(dirname "$0")/.."
bundle_dir=$(mktemp -d)
trap 'rm -rf "$bundle_dir"' EXIT
third_party/remote-compose-player/node_modules/.bin/esbuild \
  third_party/remote-compose-player/src/web/main.ts \
  --bundle --format=iife --target=es2020 --global-name=RC --external:canvas \
  --outfile="$bundle_dir/bundle.js"
if ! cmp -s "$bundle_dir/bundle.js" third_party/remote-compose-player/dist/bundle.js; then
  echo 'Published TypeScript bundle is stale. Regenerate it using dist/README.md.' >&2
  exit 1
fi
echo 'Published TypeScript bundle matches the player source.'

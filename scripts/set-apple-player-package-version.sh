#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ] || ! [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "usage: $0 <X.Y.Z>" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
project="$repo_root/samples/apple-player/RemoteComposePlayer.xcodeproj/project.pbxproj"
version="$1"

sed -E -i '' \
  "s/(requirement = \{kind = exactVersion; version = )[0-9]+\.[0-9]+\.[0-9]+(;\};)/\\1${version}\\2/" \
  "$project"

grep -q "exactVersion; version = ${version};" "$project"

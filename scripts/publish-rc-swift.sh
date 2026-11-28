#!/usr/bin/env bash
# Sync and tag the pure-Swift SwiftPM mirror repository (`yschimke/rc-swift`) for a release.
#
# Usage:
#   scripts/publish-rc-swift.sh <version> [mirror-remote-url] [profile-json]
#
# Examples:
#   scripts/publish-rc-swift.sh 4.1.0
#   scripts/publish-rc-swift.sh 4.1.0 https://x-access-token:${TOKEN}@github.com/yschimke/rc-swift.git path/to/RcNativePlayerUIKit.profile.json
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 3 ]; then
  echo "usage: $0 <version> [mirror-remote-url] [profile-json]" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version="${1#v}"
remote_url="${2:-https://github.com/yschimke/rc-swift.git}"
profile_json="${3:-}"
source_rev="$(git -C "$repo_root" rev-parse --short HEAD)"

work="$(mktemp -d "${TMPDIR:-/tmp}/rc-swift-publish.XXXXXX")"
trap 'rm -rf "$work"' EXIT

staged="$work/staged"
mirror="$work/mirror"

"$repo_root/scripts/stage-rc-swift-repo.sh" "$staged" ${profile_json:+"$profile_json"}

mkdir -p "$mirror"
git -C "$mirror" init -b main >/dev/null
git -C "$mirror" remote add origin "$remote_url"

if [ -n "${GIT_AUTHOR_NAME:-}" ]; then
  git -C "$mirror" config user.name "$GIT_AUTHOR_NAME"
else
  git -C "$mirror" config user.name "$(git -C "$repo_root" config user.name || echo 'Yuri Schimke')"
fi
if [ -n "${GIT_AUTHOR_EMAIL:-}" ]; then
  git -C "$mirror" config user.email "$GIT_AUTHOR_EMAIL"
else
  git -C "$mirror" config user.email "$(git -C "$repo_root" config user.email || echo 'yuri@schimke.ee')"
fi

# Check whether the tag already exists on the remote (idempotent re-release).
existing_tag="$(git -C "$mirror" ls-remote --refs --tags origin "refs/tags/${version}" | cut -f1)"
if [ -n "$existing_tag" ]; then
  echo "tag ${version} already exists on mirror (${existing_tag}); skipping publish"
  exit 0
fi

# Fetch existing main if the mirror is non-empty so history is linear.
if git -C "$mirror" ls-remote --heads origin main | grep -q 'refs/heads/main'; then
  git -C "$mirror" fetch --depth 1 origin main >/dev/null
  git -C "$mirror" checkout -B main origin/main >/dev/null
  # Clear tracked files before copying the fresh stage so removed files disappear cleanly.
  git -C "$mirror" rm -rfq --ignore-unmatch .
fi

cp -R "$staged"/. "$mirror"/
git -C "$mirror" add -A

if git -C "$mirror" diff --cached --quiet; then
  echo "mirror tree unchanged for ${version}; tagging existing HEAD"
else
  git -C "$mirror" commit -m "chore(release): sync rc-swift ${version} from rc-players@${source_rev}" >/dev/null
fi

git -C "$mirror" tag "$version"
git -C "$mirror" push origin HEAD:refs/heads/main "refs/tags/${version}"

echo "published rc-swift ${version} to ${remote_url}"

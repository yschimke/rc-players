#!/usr/bin/env bash
# Point `Package.swift`'s binary target at a released XCFramework zip.
#
# Swift Package Manager pins a `binaryTarget` by URL *and* SHA-256, and verifies the checksum when a
# consumer resolves the package. So these two values can only be written once the asset exists —
# which is why `release.yml` calls this after uploading the zip, not before.
#
# It rewrites exactly the two values and nothing else, so the comments in `Package.swift` (which
# explain the Intel-simulator gap and the bare `<version>` Swift tag scheme) survive a release.
#
# Usage: scripts/update-package-swift.sh <url> <sha256> [package-swift-path]
#        scripts/update-package-swift.sh --self-test
set -euo pipefail

rewrite() {
  local file="$1" url="$2" checksum="$3"
  local before after
  before="$(cat "$file")"
  # Already describing this exact asset. A `workflow_dispatch` re-release of an existing tag hits
  # this every time — the zip is built reproducibly, so the second run computes the same checksum
  # for the same URL and there is nothing to change. That is success, not the "no binaryTarget to
  # rewrite" failure below; conflating the two aborts the recovery path precisely when it is
  # working. Checked before the rewrite so the distinction does not depend on the regexes.
  if printf '%s' "$before" | grep -qF "\"$url\"" &&
    printf '%s' "$before" | grep -qF "checksum: \"$checksum\""; then
    echo "$file already points at $url; nothing to rewrite"
    return 0
  fi
  # BOTH substitutions are anchored to `.binaryTarget(`. They used to match the first `url:` and
  # the first `checksum:` anywhere in the file, and `Package.swift` opens with a usage comment
  # containing `.package(url: "https://github.com/yschimke/rc-players.git", …)` — so the rewrite
  # landed in that comment and left the real binary target on its `v0.0.0` placeholder. The release
  # then published a manifest with a correct checksum pointing at an asset that does not exist,
  # which fails for a consumer at resolve time with a 404 rather than anything self-explanatory.
  # The self-test below pins this: its fixture carries exactly that leading comment.
  #
  # `.*?` is lazy and `/s` lets it cross newlines, so each match runs from `.binaryTarget(` to the
  # first `url:` / `checksum:` after it. The whitespace after the key is captured and replayed, so
  # a wrapped `url:` (swift-format puts long URLs on their own line) keeps its layout.
  #
  # The replacements come through the environment with `/e` rather than being interpolated into the
  # program text: a `$`, `@` or backslash in a URL would otherwise be read as Perl syntax.
  after="$(
    printf '%s' "$before" |
      RCP_URL="$url" perl -0pe 's{(\.binaryTarget\(.*?\burl:\s*)"[^"]*"}{$1 . q{"} . $ENV{RCP_URL} . q{"}}se' |
      RCP_SUM="$checksum" perl -0pe 's{(\.binaryTarget\(.*?\bchecksum:\s*)"[^"]*"}{$1 . q{"} . $ENV{RCP_SUM} . q{"}}se'
  )"
  if [ "$before" = "$after" ]; then
    echo "error: $file has no binaryTarget url/checksum to rewrite" >&2
    return 1
  fi
  printf '%s\n' "$after" > "$file"
}

self_test() {
  local dir
  dir="$(mktemp -d)"
  trap 'rm -rf "$dir"' RETURN
  # The leading comment is the regression this fixture exists for: it carries a `url:` and a
  # `checksum:` of its own, ahead of the binary target, exactly as the real `Package.swift` does.
  # An unanchored rewrite hits the comment and leaves the binary target on its placeholder.
  cat > "$dir/Package.swift" <<'FIXTURE'
// A comment that must survive.
//     .package(url: "https://github.com/yschimke/rc-players.git", from: "1.60.0")
// The placeholder checksum: "1111111111111111111111111111111111111111111111111111111111111111"
    .binaryTarget(
      name: "RcComposePlayer",
      url:
        "https://example.invalid/old/RcComposePlayer.xcframework.zip",
      checksum: "0000000000000000000000000000000000000000000000000000000000000000"
    )
FIXTURE
  rewrite "$dir/Package.swift" "https://example.invalid/new/RcComposePlayer.xcframework.zip" "abc123"

  grep -q 'https://example.invalid/new/RcComposePlayer.xcframework.zip' "$dir/Package.swift" ||
    { echo "self-test: url was not rewritten" >&2; return 1; }
  grep -q 'checksum: "abc123"' "$dir/Package.swift" ||
    { echo "self-test: checksum was not rewritten" >&2; return 1; }
  grep -q 'A comment that must survive' "$dir/Package.swift" ||
    { echo "self-test: the file was clobbered rather than edited" >&2; return 1; }
  grep -q 'old/RcComposePlayer' "$dir/Package.swift" &&
    { echo "self-test: the previous url is still present" >&2; return 1; }

  # The comment's own url/checksum must be untouched — this is the anchoring regression.
  grep -q 'rc-players.git", from: "1.60.0"' "$dir/Package.swift" ||
    { echo "self-test: the rewrite corrupted the usage comment's url" >&2; return 1; }
  grep -q '1111111111111111111111111111111111111111111111111111111111111111' "$dir/Package.swift" ||
    { echo "self-test: the rewrite corrupted the comment's checksum" >&2; return 1; }
  # And the binary target itself must actually carry the new values.
  sed -n '/\.binaryTarget(/,/)/p' "$dir/Package.swift" |
    grep -q 'https://example.invalid/new/RcComposePlayer.xcframework.zip' ||
    { echo "self-test: the binary target url was not rewritten" >&2; return 1; }
  sed -n '/\.binaryTarget(/,/)/p' "$dir/Package.swift" | grep -q 'checksum: "abc123"' ||
    { echo "self-test: the binary target checksum was not rewritten" >&2; return 1; }

  # Rewriting to the values already present is the `workflow_dispatch` re-release path, and it must
  # succeed rather than trip the "nothing to rewrite" failure below.
  rewrite "$dir/Package.swift" "https://example.invalid/new/RcComposePlayer.xcframework.zip" "abc123" ||
    { echo "self-test: a no-op rewrite should have succeeded" >&2; return 1; }
  grep -q 'checksum: "abc123"' "$dir/Package.swift" ||
    { echo "self-test: the no-op rewrite changed the checksum" >&2; return 1; }

  # A file with nothing to rewrite must fail loudly: a release that silently left the placeholder
  # checksum in place would publish a Package.swift no consumer can resolve.
  printf 'nothing here\n' > "$dir/Empty.swift"
  if rewrite "$dir/Empty.swift" "https://example.invalid/x.zip" "abc" 2>/dev/null; then
    echo "self-test: a file with no binary target should have failed" >&2
    return 1
  fi

  echo "update-package-swift self-test: ok"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test
  exit 0
fi

if [ $# -lt 2 ]; then
  sed -n '2,12p' "$0" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rewrite "${3:-$repo_root/Package.swift}" "$1" "$2"

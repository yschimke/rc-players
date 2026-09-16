#!/usr/bin/env bash
# Refresh the committed copy of the wear-m3-catalog sticker sheet.
#
# This is the only thing in the repository that may contact the catalog at all, and it is run BY
# HAND when the corpus should move — never by CI. Everything else reads the committed bytes under
# `corpus/`.
#
# The documents are committed rather than fetched per run because they are not addressable over
# time. preview.coo.ee publishes exactly one generation, the current one: `?gen=<sha>` answers only
# while that sha is live and returns 409 ("this catalog has moved on") as soon as it is not. The
# first corpus run fetched 701 documents at gen 60c4c8af; ninety minutes later the same pinned URLs
# returned 404 for all 701, so a score could not be reproduced, or even repeated.
#
# Two sources, in order of preference:
#
#   1. THE DELIVERY BRANCH. If the catalog publishes `.rc` wire bytes on design-artifacts/remote-m3,
#      they are read straight from the pinned commit — properly addressable, no server involved, and
#      a refresh is then a pure git operation. This is the path to prefer; it needs the catalog to
#      have `.rc` publishing enabled. As of 40b842d5 the branch carries the rendered PNGs and a
#      decompiled `documents/*.rc.json`, but no wire bytes, so this path finds nothing yet.
#   2. THE RENDER SERVER, for as long as (1) publishes nothing. Fetches the live generation and
#      records which one it got.
#
# Either way the bytes land under `corpus/` with the generation recorded beside them, and nothing in
# CI ever reaches the network for this.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
corpus_dir="$repo_root/scripts/rc-catalog-corpus/corpus"
catalog_repo="https://github.com/yschimke/wear-m3-catalog"
delivery_branch="${RC_CATALOG_BRANCH:-design-artifacts/remote-m3}"
serve_root="${RC_CATALOG_SERVE:-https://preview.coo.ee/remote-m3/render}"
compare_url="${RC_CATALOG_COMPARE:-https://preview.coo.ee/remote-m3/compare?format=rc}"

work="$(mktemp -d "${TMPDIR:-/tmp}/rc-catalog-refresh.XXXXXX")"
trap 'rm -rf "$work"' EXIT

# The live generation is whatever the compare page is currently serving. Asking it, rather than
# assuming the delivery branch head, is the difference between URLs that resolve and URLs that 409:
# the branch head moves independently of what the server has baked.
#
# Buffered to a file and matched with one `sed`, deliberately. Under `pipefail` a `grep | head`
# pipeline fails the whole function with 141 — head closes the pipe on the first match and grep dies
# of SIGPIPE — even though the read succeeded. `sed` reads to the end and prints only the first hit.
current_generation() {
  local page="$work/compare.html"
  curl --silent --show-error --location --fail "$compare_url" > "$page"
  sed -n 's/.*[Gg][Ee][Nn]=\([0-9a-f]\{40\}\).*/\1/p' "$page" | sed -n '1p'
}

git -C "$work" init --quiet

# Source 1: the delivery branch, if it publishes wire bytes. Preferred, because a branch commit is
# addressable forever while a live generation is addressable until the next push.
branch_head=""
wire_bytes="$work/wire-bytes.txt"
: > "$wire_bytes"
if git -C "$work" fetch --quiet --depth 1 "$catalog_repo" "$delivery_branch" 2>/dev/null; then
  branch_head="$(git -C "$work" rev-parse FETCH_HEAD)"
  # The listing goes to a file before it is searched. `ls-tree | grep -q` looks equivalent and is
  # not: grep exits on the first match, ls-tree dies of SIGPIPE, and under `pipefail` the condition
  # reports failure — so the branch would be judged to publish no wire bytes precisely when it does.
  git -C "$work" ls-tree -r --name-only FETCH_HEAD > "$work/tree.txt"
  grep '\.rc$' "$work/tree.txt" > "$wire_bytes" || true
fi

staging="$work/corpus"
mkdir -p "$staging"

if [ -s "$wire_bytes" ]; then
  generation="$branch_head"
  echo "==> $delivery_branch at ${generation:0:12} publishes $(wc -l < "$wire_bytes" | tr -d ' ') wire documents"
  git -C "$work" show "FETCH_HEAD:catalog.json" > "$work/catalog.json"
  # Keyed by basename rather than by a fixed directory: where the catalog chooses to put them is its
  # business, and assuming a path that does not exist yet would be guessing.
  while read -r path; do
    git -C "$work" show "FETCH_HEAD:$path" > "$staging/$(basename "$path")"
  done < "$wire_bytes"
else
  if [ -n "$branch_head" ]; then
    echo "==> $delivery_branch at ${branch_head:0:12} publishes no .rc bytes; using the render server"
  fi
  generation="$(current_generation)"
  [ -n "$generation" ] || {
    echo "error: could not read the live generation from $compare_url" >&2
    exit 1
  }
  echo "==> live generation $generation"

  # The sticker inventory comes from the catalog commit the server is serving, so the names asked
  # for are exactly the names that generation published.
  if ! git -C "$work" fetch --quiet --depth 1 "$catalog_repo" "$generation" 2>/dev/null; then
    echo "error: the catalog repository has no commit $generation" >&2
    exit 1
  fi
  git -C "$work" show "FETCH_HEAD:catalog.json" > "$work/catalog.json"
  python3 - "$work/catalog.json" "$staging" "$serve_root" "$generation" <<'PY'
import json, pathlib, sys

catalog = json.loads(pathlib.Path(sys.argv[1]).read_text())
output, serve, generation = pathlib.Path(sys.argv[2]), sys.argv[3], sys.argv[4]
identifiers = sorted(
    # images/<component-slug>/<variant>__<state>__<size>.png
    f"{image['path'].split('/')[1]}__{image['path'].split('/')[2][: -len('.png')]}"
    for component in catalog["components"]
    for image in component.get("images", [])
)
output.joinpath("urls.txt").write_text(
    "".join(
        f'url = "{serve}/{name}.rc?gen={generation}"\noutput = "{output / name}.rc"\n'
        for name in identifiers
    )
)
print(f"catalog {generation[:12]}: {len(identifiers)} stickers")
PY

# --fail so a non-2xx body is never written as a document: without it the corpus fills with small
# error pages that pass an "is the file non-empty?" check and only fail later as unreadable headers.
# No --fail-early — a sticker the sheet renders but does not publish should drop out of the manifest
# rather than abort the refresh — and every status is recorded so a bad refresh names its own cause.
  curl --silent --show-error --location --fail \
    --parallel --parallel-max 6 --retry 3 --retry-delay 1 \
    --write-out '%{http_code} %{url_effective}\n' \
    --config "$staging/urls.txt" > "$staging/http-status.txt" || true
  rm -f "$staging/urls.txt"

  # Every request above carries `?gen=<generation>`, so a catalog that advances mid-fetch cannot
  # produce a corpus mixed across two sheets: the later requests 409 and those documents drop out
  # rather than arriving from a different generation. So this re-read is a note, not a gate — and it
  # must not be a gate, because the compare page went to 404 between two runs of this script while
  # the pinned document URLs kept answering. Failing a good 701-document fetch over that would be
  # discarding the thing this script exists to produce.
  after="$(current_generation || true)"
  if [ -z "$after" ]; then
    echo "note: could not re-read the live generation to confirm $generation did not move" >&2
  elif [ "$after" != "$generation" ]; then
    echo "note: the live generation moved to $after during the fetch; documents that 409ed dropped" >&2
  fi
fi

"$repo_root/scripts/rc-catalog-corpus/build-manifest.py" "$staging" "$generation"

documents="$(find "$staging" -name '*.rc' | wc -l | tr -d '[:space:]')"
if [ "$documents" -eq 0 ]; then
  echo "error: no usable documents; the committed corpus is unchanged" >&2
  exit 1
fi

rm -rf "$corpus_dir"
mkdir -p "$corpus_dir"
cp "$staging"/*.rc "$staging/manifest.json" "$staging/PIN" "$corpus_dir/"
node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" inputs "$corpus_dir"
echo "==> $documents documents committed to scripts/rc-catalog-corpus/corpus at $generation"
echo "    review the diff and commit it; CI reads these bytes and never contacts the server"

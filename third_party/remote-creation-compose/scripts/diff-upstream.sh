#!/usr/bin/env bash
# Measures how far this fork is from AndroidX's creation sources, and shows the difference.
#
# Usage: third_party/remote-creation-compose/scripts/diff-upstream.sh [report] <androidx checkout>
#
# Reports (default: every report, then the summary):
#   --summary      the counts only: identical / differing / not carried / fork-only, and hunks
#   --files        one line per upstream file: its status and the fork file it is compared with
#   --not-carried  upstream files with no fork file
#   --fork-only    fork files with no upstream file
#   --hunks        the diff of every carried file that differs
#   --unmarked     only the hunks under neither `LOCAL PATCH (rc-players)` nor `KMP (rc-players)`
#
# A hunk that must stay different carries one of two markers, with its reason, in a comment:
#   LOCAL PATCH (rc-players)  something the fork adds, or does differently on purpose
#   KMP (rc-players)          a substitution forced by the non-JVM targets or by the Kotlin writer
# A hunk that changes nothing but `import` lines (and blank ones) follows from the hunks of its file's body, and has
# no place for a comment; it is counted on its own, as neither marked nor unmarked.
#
# Both sides are normalised before comparing: trailing whitespace goes, runs of blank lines
# collapse, each file's imports are sorted and de-duplicated, `@kotlin.jvm.JvmName` and its
# like are written `@JvmName` (common code has to qualify or import them, JVM code does not), and
# a comma that ends a line goes (this repository's ktfmt puts a trailing comma after a lone
# argument that it breaks onto its own line, and AndroidX's does not).
# Upstream's side also has the names in upstream-fqn.tsv rewritten, because the fork's writer
# declares AndroidX's Java classes under the same simple names in another package, and then the
# packages in upstream-packages.tsv, because the fork declares AndroidX's Kotlin in its own package
# (`ee.schimke.composeai.rccreation`). The same package map gives the fork path a file is carried
# at. So a refresh is: copy upstream's file to the mapped path, rewrite its packages, re-apply the
# marked hunks; this script shows whether that was done.
#
# The data beside this script:
#   upstream-roots.tsv    upstream source root -> the fork directory that carries it
#   upstream-renames.tsv  upstream files the fork carries at another path, each with its reason
#   upstream-fqn.tsv      upstream name -> the fork's name for it
#   upstream-packages.tsv upstream package -> the fork's package (text and paths)
#
# Unmarked hunks are counted, not failed on: the fork is not yet at the point where every
# difference is a marked one. See docs/design/RC_CREATION_UPSTREAM.md for the plan.
#
# Needs bash, coreutils, awk, sed and diff. Runs under macOS's bash 3.2.
set -euo pipefail

usage() {
  sed -n '2,18p' "$0" >&2
  exit 2
}

report=all
case "${1-}" in
  --summary | --files | --not-carried | --fork-only | --hunks | --unmarked)
    report="${1#--}"
    shift
    ;;
  --*) usage ;;
esac
[ $# -eq 1 ] || usage

scripts_dir="$(cd "$(dirname "$0")" && pwd)"
module_dir="$(cd "$scripts_dir/.." && pwd)"
upstream="$(cd "$1" && pwd)/compose/remote"
[ -d "$upstream/remote-creation-compose" ] || {
  echo "error: no compose/remote/remote-creation-compose under $1" >&2
  exit 1
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Data files: tab-separated, `#` comments and blank lines ignored.
rows() { grep -v -e '^#' -e '^[[:space:]]*$' "$scripts_dir/$1" || true; }

# upstream-fqn.tsv as a sed script. A name ends at anything that cannot continue an identifier, so
# `…creation.Rc` does not rewrite `…creation.RcPlatformServices`.
rows upstream-fqn.tsv | awk -F'\t' '{
  from = $1; gsub(/\./, "\\.", from)
  printf "s/%s([^A-Za-z0-9_]|$)/%s\\1/g\n", from, $2
}' >"$work/fqn.sed"

# upstream-packages.tsv twice: as a sed script for a file's text (a package ends where an
# identifier cannot continue, so `….creation.compose` does not rewrite `….creation.composeX`), and
# as one for a path under a source root.
rows upstream-packages.tsv | awk -F'\t' '{
  from = $1; gsub(/\./, "\\.", from)
  printf "s/(^|[^A-Za-z0-9_.])%s([^A-Za-z0-9_]|$)/\\1%s\\2/g\n", from, $2
}' >>"$work/fqn.sed"
rows upstream-packages.tsv | awk -F'\t' '{
  from = $1; to = $2; gsub(/\./, "/", from); gsub(/\./, "/", to)
  printf "s|^%s(/\|$)|%s\\1|\n", from, to
}' >"$work/path.sed"
fork_path() { printf '%s\n' "$1" | sed -E -f "$work/path.sed"; }

# Trailing whitespace, a comma ending a line, `kotlin.jvm.` on annotations, blank-line runs, and the
# import block (sorted, unique, where it started).
normalise() {
  sed -E -e 's/[[:space:]]*$//' -e 's/,$//' -e 's/@((file|get|set):)?kotlin\.jvm\.Jvm/@\1Jvm/g' |
    awk '
      /^import kotlin\.jvm\.Jvm[A-Za-z]*$/ { next }
      /^import / { imports[n++] = $0; next }
      n && !flushed {
        cmd = "LC_ALL=C sort -u"
        for (i = 0; i < n; i++) print imports[i] | cmd
        close(cmd); flushed = 1; blank = 0
      }
      /^$/ { if (blank++) next; print; next }
      { blank = 0; print }
      END { if (n && !flushed) { cmd = "LC_ALL=C sort -u"; for (i = 0; i < n; i++) print imports[i] | cmd; close(cmd) } }'
}

# Every fork source file, relative to the module.
(cd "$module_dir" && find . -name '*.kt' -not -path './build/*' | sed 's|^\./||' | LC_ALL=C sort) \
  >"$work/fork-files"

# pairs: root <tab> upstream path under the root <tab> fork path under the module (or `-`).
: >"$work/pairs"
rows upstream-roots.tsv | while IFS="$(printf '\t')" read -r root fork_dir; do
  [ -d "$upstream/$root" ] || { echo "error: no $root under $upstream" >&2; exit 1; }
  (cd "$upstream/$root" && find . -name '*.kt' | sed 's|^\./||' | LC_ALL=C sort) |
    while read -r path; do
      renamed="$(rows upstream-renames.tsv |
        awk -F'\t' -v key="$root/$path" '$1 == key { printf "%s%s", (n++ ? "," : ""), $2 }')"
      if [ -n "$renamed" ]; then
        printf '%s\t%s\t%s\trenamed\n' "$root" "$path" "$renamed"
      elif [ -f "$module_dir/$fork_dir/$(fork_path "$path")" ]; then
        printf '%s\t%s\t%s\tsame\n' "$root" "$path" "$fork_dir/$(fork_path "$path")"
      else
        printf '%s\t%s\t-\t-\n' "$root" "$path"
      fi
    done
done >>"$work/pairs"

# Compare each pair; collect the diffs.
: >"$work/status"
: >"$work/diff"
: >"$work/fork-parts"
while IFS="$(printf '\t')" read -r root path fork how; do
  if [ "$fork" = "-" ]; then
    printf 'not-carried\t%s\t%s\t-\t-\n' "$root" "$path" >>"$work/status"
    continue
  fi
  # A file the fork splits lists every part; the first is compared, the rest are accounted for.
  case "$fork" in
    *,*)
      printf '%s\n' "${fork#*,}" | tr ',' '\n' >>"$work/fork-parts"
      fork="${fork%%,*}"
      ;;
  esac
  [ -f "$module_dir/$fork" ] || {
    echo "error: upstream-renames.tsv names $fork, which is not a file" >&2
    exit 1
  }
  sed -E -f "$work/fqn.sed" "$upstream/$root/$path" | normalise >"$work/a"
  normalise <"$module_dir/$fork" >"$work/b"
  if diff -u -L "upstream/$root/$path" -L "fork/$fork" "$work/a" "$work/b" >"$work/d"; then
    printf 'identical\t%s\t%s\t%s\t%s\n' "$root" "$path" "$fork" "$how" >>"$work/status"
  else
    printf 'differing\t%s\t%s\t%s\t%s\n' "$root" "$path" "$fork" "$how" >>"$work/status"
    cat "$work/d" >>"$work/diff"
  fi
done <"$work/pairs"

# Fork files no upstream file is compared with.
cut -f4 "$work/status" | cat - "$work/fork-parts" | LC_ALL=C sort -u >"$work/fork-used"
LC_ALL=C comm -23 "$work/fork-files" "$work/fork-used" >"$work/fork-only"

# One hunk per record, each tagged marked, import-only or unmarked.
awk '
  function flush() {
    if (hunk == "") return
    if (hunk ~ /LOCAL PATCH \(rc-players\)|KMP \(rc-players\)/) marked++
    else if (changed > 0 && changed == imports) import_only++
    else { unmarked++; if (!header_printed) { printf "%s", header; header_printed = 1 }; printf "%s", hunk }
    hunk = ""; changed = 0; imports = 0
  }
  /^--- / { flush(); header = $0 "\n"; header_printed = 0; next }
  /^\+\+\+ / { header = header $0 "\n"; next }
  /^@@ / { flush(); hunk = $0 "\n"; next }
  /^[-+]./ { changed++; if ($0 ~ /^[-+]import /) imports++ }
  { hunk = hunk $0 "\n" }
  END { flush(); printf "%d\t%d\t%d\t%d\n", marked + import_only + unmarked, marked, import_only, unmarked > counts }
' counts="$work/hunk-counts" "$work/diff" >"$work/unmarked"
[ -f "$work/hunk-counts" ] || printf '0\t0\t0\t0\n' >"$work/hunk-counts"

summary() {
  awk -F'\t' '
    { total[$2]++; n[$2 "\t" $1]++; all[$1]++; files++
      if ($5 == "renamed") { renamed[$2]++; all["renamed"]++ }
      if (!($2 in seen)) { seen[$2] = 1; order[roots++] = $2 } }
    END {
      fmt = "%-55s %5s %9s %9s %11s %12s\n"
      printf fmt, "upstream source root", "files", "identical", "differing", "not carried", "other path"
      for (i = 0; i < roots; i++) { r = order[i]
        printf fmt, r, total[r], n[r "\tidentical"] + 0, n[r "\tdiffering"] + 0, n[r "\tnot-carried"] + 0, renamed[r] + 0 }
      printf fmt, "total", files, all["identical"] + 0, all["differing"] + 0, all["not-carried"] + 0, all["renamed"] + 0
    }' "$work/status"
  echo
  echo "fork-only files: $(wc -l <"$work/fork-only" | tr -d ' ')"
  awk -F'\t' '{ printf "hunks in differing files: %d (marked: %d, import-only: %d, unmarked: %d)\n", $1, $2, $3, $4 }' "$work/hunk-counts"
  echo
  echo "\"other path\": carried under another name, split or merged (upstream-renames.tsv);"
  echo "these are counted under identical or differing as well."
}

files() {
  awk -F'\t' '{ printf "%-11s %s/%s%s\n", $1, $2, $3, ($5 == "renamed" ? "  <-  " $4 : "") }' "$work/status"
}

not_carried() { awk -F'\t' '$1 == "not-carried" { print $2 "/" $3 }' "$work/status"; }

case "$report" in
  summary) summary ;;
  files) files ;;
  not-carried) not_carried ;;
  fork-only) cat "$work/fork-only" ;;
  hunks) cat "$work/diff" ;;
  unmarked) cat "$work/unmarked" ;;
  all)
    echo "== Not carried: upstream files with no fork file"
    not_carried
    echo
    echo "== Fork-only: fork files with no upstream file"
    cat "$work/fork-only"
    echo
    echo "== Hunks: carried files that differ"
    cat "$work/diff"
    echo
    echo "== Summary"
    summary
    ;;
esac

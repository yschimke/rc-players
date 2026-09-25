#!/usr/bin/env bash
# Decide which modules a release actually has to publish.
#
# Until now every release uploaded all 8 coordinates at the tag, whether or not a byte of them
# changed. Each coordinate-publish is 20 files against an org-wide Maven Central file-count limit
# that all of yschimke's publishing repositories share (yschimke/compose-ai-tools#4772), and the
# KMP modules here multiply that: `rc-player-compose` alone ships Android, JVM, wasm and five Apple
# variants. Replaying the whole set 19 times over the last 30 days is most of this repository's
# share of that limit.
#
# Measured over v1.54.0..v1.63.0 — 19 release intervals, 152 module-publications — these rules
# would have published 57 of them: 38%. The remaining 62% was bytes nobody touched.
#
# Ported from compose-preview-daemon, where the same script runs; the rules below are its rules.
#
# A module is published when:
#
#   1. a file under it changed since the tag IT last published at (not since the last release —
#      a module skipped for three releases is compared against its own baseline, so nothing is
#      ever missed by a gap), or
#   2. a module it depends on is being published, or
#   3. a shared build input changed, which can move every artifact at once.
#
# "A file under it" means every input the module packages, not just its own directory (#512). A
# module's inputs are the directories of every project in its transitive project-dependency
# closure — followed through UNPUBLISHED projects too — plus any path outside its directory that it
# reads. Two published modules went stale on Central without this:
#
#   - `rc-player-wasm-dist` zips `:rc-player-wasm`, which is unpublished and depends on
#     `:rc-player-compose`. Rule 2 only walks published edges, so a compose-only release (v1.71.0,
#     v1.75.0) left Central's wasm-dist built from the previous compose code.
#   - `remote-compose-player-js-dist` packages `../remote-compose-player/dist`, a sibling directory
#     with no build file, so no project edge reaches it at all.
#
# Paths outside a project's directory are picked up from `"../…"` literals in its build file, and
# anything a build file cannot express that way is declared in EXTRA_INPUTS below. A dependency on
# a project path this script cannot resolve, or an input path that escapes the repository, makes
# every published module that reaches it publish.
#
# Rule 2 is what keeps the POMs honest, and it is deliberately coarser than it needs to be. A
# published POM names its project dependencies at *their* `project.version`, so a module may only
# be skipped while everything it depends on is also skipped; otherwise it would name a sibling
# version that was never uploaded — exactly the break compose-ai-tools shipped in v2.2.1 and paid
# for in yschimke/wear-m3-catalog#350. Propagating on any change rather than only on an ABI change
# costs about 6 percentage points (73% -> 67%) and needs no assumption about binary compatibility;
# tighten it only when the modules carry ABI dumps to prove the surface held.
#
# Every uncertainty resolves to "publish". Central refuses a second upload of a version, so an
# unnecessary publish costs quota while a wrongly-skipped one is unrepairable.
#
# Usage: maven-publish-plan.sh --head <ref> [--manifest <path>]
# Output: one artifact id per line, on stdout. Diagnostics go to stderr.
set -euo pipefail

HEAD_REF=""
MANIFEST=""
WRITE_MANIFEST=""
while [ $# -gt 0 ]; do
  case "$1" in
    --head) HEAD_REF="$2"; shift 2 ;;
    --manifest) MANIFEST="$2"; shift 2 ;;
    --write-manifest) WRITE_MANIFEST="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[ -n "$HEAD_REF" ] || { echo "--head is required" >&2; exit 2; }
[ -z "$MANIFEST" ] || [ -f "$MANIFEST" ] || { echo "no manifest at $MANIFEST" >&2; exit 2; }

python3 - "$HEAD_REF" "$MANIFEST" "$WRITE_MANIFEST" <<'PY'
import json, os, re, subprocess, sys, collections, urllib.error, urllib.request
from concurrent.futures import ThreadPoolExecutor

GROUP_PATH = "ee/schimke/composeai"
CENTRAL = "https://repo1.maven.org/maven2"

head, manifest_path, write_manifest_path = sys.argv[1], sys.argv[2], sys.argv[3]

def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True).stdout

settings = open("settings.gradle.kts", encoding="utf-8").read()
# `\s*=\s*` rather than a literal " = ": ktfmt wraps a long assignment onto the next line, and
# `:third-party-remote-compose-player-dist` is wrapped. A regex that missed it fell back to
# deriving the directory from the project path, which does not exist on disk — and the module then
# vanished from the plan without a word.
dirs = dict(
    re.findall(r'project\("(:[^"]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)', settings)
)
paths = re.findall(r'^include\("(:[^"]+)"\)', settings, re.M)

# Paths a published module packages that neither its own directory, its project dependencies nor a
# `"../…"` literal in its build file reveal. Keyed by artifact id; repository-relative directories.
# The js-dist entry is also found by the literal scan — it is declared here as well so the input
# survives a rewrite of that build file into a form the scan cannot read.
EXTRA_INPUTS = {
    "remote-compose-player-js-dist": ["third_party/remote-compose-player"],
}

modules = {}   # artifactId -> directory
deps = {}      # artifactId -> [artifactId]
path_to_id = {}
proj_dir = {}      # project path -> directory, for EVERY included project, published or not
proj_deps = {}     # project path -> [project path], every `project(":…")` its build file names
proj_inputs = {}   # project path -> [directory], `"../…"` paths outside its own directory
for p in paths:
    d = dirs.get(p, p.lstrip(":").replace(":", "/"))
    try:
        text = open(d + "/build.gradle.kts", encoding="utf-8").read()
    except OSError as e:
        # Never skip quietly. A module this script cannot read is a module it cannot classify, and
        # the whole point of the plan is that a wrongly-skipped publish is unrepairable — Central
        # refuses a second upload of a version. Failing here costs a red release job; guessing
        # costs a coordinate that never shipped.
        print(f"  cannot read {p}'s build file at {d}: {e}", file=sys.stderr)
        sys.exit(2)
    proj_dir[p] = d
    # Every `project(":…")` reference counts, not only dependency declarations: `:rc-player-wasm`
    # reaches `:rc-player-compat-tests`' outputs through `project(...).layout`, and a coarser edge
    # can only over-publish.
    proj_deps[p] = sorted(set(re.findall(r'project\("(:[^"]+)"\)', text)))
    proj_inputs[p] = sorted(
        {os.path.normpath(os.path.join(d, lit)) for lit in re.findall(r'"(\.\./[^"]*)"', text)}
    )
    if 'id("composeai.maven-publishing")' not in text:
        continue
    # The artifact id is the one the module DECLARES, not the project path flattened: seven of the
    # eight agree, and `:third-party-remote-compose-player-dist` publishes as
    # `remote-compose-player-js-dist`. Reading the declaration means this script, the manifest,
    # `PublishedArtifactIds` and the POM cannot disagree about what a module is called.
    declared = re.search(r'artifactId\s*=\s*"([^"]+)"', text)
    if not declared:
        print(f"  {p}: applies composeai.maven-publishing but declares no artifactId", file=sys.stderr)
        sys.exit(2)
    aid = declared.group(1)
    modules[aid] = d
    path_to_id[p] = aid
    deps[aid] = proj_deps[p]
# Dependencies are collected as PROJECT PATHS above and mapped to artifact ids here, once every
# module has been seen — the same reason the ids are read from the declarations rather than derived.
deps = {a: [path_to_id[d] for d in ds if d in path_to_id] for a, ds in deps.items()}

def module_inputs(project_path):
    """Every directory `project_path`'s artifact can be built from, or None if one is unresolvable.

    Walks project dependencies transitively through published and unpublished projects alike, so
    an unpublished middle module (`:rc-player-wasm`) cannot hide a change from the published module
    that packages it. None means "cannot tell", which the caller resolves to "publish".
    """
    aid = path_to_id[project_path]
    seen, stack, inputs = set(), [project_path], set(EXTRA_INPUTS.get(aid, ()))
    while stack:
        q = stack.pop()
        if q in seen:
            continue
        seen.add(q)
        if q not in proj_dir:
            print(f"  {aid}: depends on {q}, which settings.gradle.kts does not include; "
                  "publishing", file=sys.stderr)
            return None
        inputs.add(proj_dir[q])
        inputs.update(proj_inputs[q])
        stack.extend(proj_deps[q])
    for i in inputs:
        if i == ".." or i.startswith("../") or os.path.isabs(i):
            print(f"  {aid}: input {i} is outside the repository; publishing", file=sys.stderr)
            return None
    return sorted(inputs)

def central_release(aid):
    """The newest version of `aid` on Central, or None if it has never published there.

    `<release>` rather than `<latest>`: `latest` can name a snapshot on repositories that carry
    them, and a baseline that is not a real release would diff against a tag that does not exist.
    """
    url = f"{CENTRAL}/{GROUP_PATH}/{aid}/maven-metadata.xml"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            body = response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None  # never published
        print(f"  {aid}: Central said {e.code}; publishing", file=sys.stderr)
        return None
    except Exception as e:  # noqa: BLE001 - any failure resolves to "publish"
        print(f"  {aid}: could not reach Central ({e}); publishing", file=sys.stderr)
        return None
    m = re.search(r"<release>([^<]+)</release>", body)
    return m.group(1) if m else None


if manifest_path:
    recorded = json.load(open(manifest_path, encoding="utf-8"))["modules"]
    print(f"  baseline: {manifest_path} ({len(recorded)} entries)", file=sys.stderr)
else:
    # Eight at a time: 69 sequential round-trips is most of this script's wall clock, and Central
    # serves these as static files.
    with ThreadPoolExecutor(max_workers=8) as pool:
        found = dict(zip(sorted(modules), pool.map(central_release, sorted(modules))))
    recorded = {aid: v for aid, v in found.items() if v}
    print(f"  baseline: Maven Central ({len(recorded)} of {len(modules)} coordinates)",
          file=sys.stderr)

if write_manifest_path:
    with open(write_manifest_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "_comment": "The version each coordinate is published at on Maven Central. "
                            "Resolved at release time by .github/scripts/maven-publish-plan.sh "
                            "and NOT committed - Central is the source of truth.",
                "modules": dict(sorted(recorded.items())),
            },
            f,
            indent=2,
        )
        f.write("\n")


# A shared build input can change any artifact, so it opens the gate for everything.
SHARED = re.compile(r"^(build-logic/|gradle/|gradlew|settings\.gradle\.kts$|build\.gradle\.kts$)")

def changed_since(aid, version, inputs):
    """Did any of `inputs` move between the tag for `version` and head?"""
    tag = f"v{version}"
    if subprocess.run(["git", "rev-parse", "--verify", "-q", tag + "^{commit}"],
                      capture_output=True).returncode != 0:
        print(f"  {aid}: no tag {tag}; publishing", file=sys.stderr)
        return True
    # `:(top)` anchors each pathspec at the repository root whatever the working directory.
    out = git("diff", "--name-only", f"{tag}..{head}", "--", *(f":(top){i}" for i in inputs))
    return bool(out.strip())

shared_changed = False
for version in sorted(set(recorded.values())):
    tag = f"v{version}"
    if subprocess.run(["git", "rev-parse", "--verify", "-q", tag + "^{commit}"],
                      capture_output=True).returncode != 0:
        shared_changed = True
        break
    files = [f for f in git("diff", "--name-only", f"{tag}..{head}").split("\n") if f]
    if any(SHARED.match(f) for f in files):
        shared_changed = True
        break

if shared_changed:
    print("  a shared build input changed; publishing every module", file=sys.stderr)
    for aid in sorted(modules):
        print(aid)
    sys.exit(0)

dirty = set()
for p, aid in path_to_id.items():
    if aid not in recorded:
        print(f"  {aid}: never published; publishing", file=sys.stderr)
        dirty.add(aid)
        continue
    inputs = module_inputs(p)
    if inputs is None or changed_since(aid, recorded[aid], inputs):
        dirty.add(aid)

# Rule 2: anything depending on a dirty module is dirty too, transitively.
rev = collections.defaultdict(set)
for aid, ds in deps.items():
    for d in ds:
        rev[d].add(aid)
stack = list(dirty)
while stack:
    m = stack.pop()
    for r in rev.get(m, ()):
        if r not in dirty:
            dirty.add(r)
            stack.append(r)

print(f"  {len(dirty)} of {len(modules)} modules publish", file=sys.stderr)
for aid in sorted(dirty):
    print(aid)
PY

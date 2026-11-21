#!/usr/bin/env bash
# Fail fast when the Apple sample app's Xcode project no longer lists every package source it needs.
#
# `samples/apple-player/RemoteComposePlayer.xcodeproj` compiles the SwiftPM targets' sources
# directly, one hand-written file reference each, rather than depending on the package. A Swift file
# added to one of those targets therefore builds under `swift test` and `check-swift-sample.sh`
# (both glob the directories) but is missing from the app, and the first place that shows is
# `package-apple-player.sh`, deep inside the macOS lane, as "cannot find '<type>' in scope".
#
# This runs anywhere (no Xcode): every `*.swift` file under the targets the app compiles must be
# referenced by the project, except files that compile to nothing on iOS — their first line is an
# AppKit / macOS-only guard. It also rejects references to files that no longer exist.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project="${1:-$repo_root/samples/apple-player/RemoteComposePlayer.xcodeproj/project.pbxproj}"

python3 - "$repo_root" "$project" <<'PY'
import pathlib, re, sys

root = pathlib.Path(sys.argv[1])
project = pathlib.Path(sys.argv[2])
text = project.read_text()

# The SwiftPM targets the app compiles from source. `RcComposePlayer` is the Kotlin framework and
# the CLI / test targets are not part of the app.
targets = ["RcComposePlayerSwiftUI", "RcNativePlayerCore", "RcNativePlayerUIKit", "RcPlayerAppleFonts"]

# A file whose first line is one of these guards is empty on iOS, so the app need not list it.
macos_only = (
    "#if canImport(AppKit) && !targetEnvironment(macCatalyst)",
    "#if os(macOS)",
)

def macos_only_file(path: pathlib.Path) -> bool:
    with path.open() as f:
        return f.readline().strip() in macos_only

referenced = {
    (project.parent.parent / m).resolve()
    for m in re.findall(r'path = "([^"]*\.\./Sources/[^"]+\.swift)"', text)
}
# A file reference alone is not enough: it must be compiled by a Sources build phase too.
compiled_names = set(re.findall(r"/\* (\S+\.swift) in Sources \*/", text))

errors = []
for target in targets:
    for source in sorted((root / "Sources" / target).rglob("*.swift")):
        rel = source.relative_to(root)
        if macos_only_file(source):
            continue
        if source.resolve() not in referenced:
            errors.append(f"{rel}: not referenced by {project.relative_to(root)}")
        elif source.name not in compiled_names:
            errors.append(f"{rel}: referenced but not in the app's Sources build phase")
for path in sorted(referenced):
    if not path.exists():
        errors.append(f"{path.relative_to(root)}: referenced by the project but does not exist")

if errors:
    print("Apple sample app project is out of sync with the Swift package sources:", file=sys.stderr)
    for e in errors:
        print(f"  {e}", file=sys.stderr)
    print("Add each file to samples/apple-player/RemoteComposePlayer.xcodeproj (PBXBuildFile, "
          "PBXFileReference, its target group and the app's Sources phase).", file=sys.stderr)
    sys.exit(1)
print(f"apple player project sources: ok ({len(referenced)} package files)")
PY

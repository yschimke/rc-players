#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
profile="${1:-$repo_root/rc-player/compose/build/distributions/RcNativePlayerUIKit.profile.json}"

if [ ! -f "$profile" ]; then
  profile="$repo_root/distribution/native-uikit/profile.json"
fi

python3 - "$profile" "$repo_root" <<'PY'
import json
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
repo = pathlib.Path(sys.argv[2])
profile = json.loads(path.read_text())

assert profile["schemaVersion"] == 1
assert profile["profileId"] == "rc-native-uikit-core-v1"
assert profile["maturity"] == "experimental"
assert profile["product"] == "RcNativePlayerUIKit"
assert profile["replacesCmpPlayer"] is False
assert profile["releaseVersion"]
assert profile["sourceRevision"]
if path.name == "RcNativePlayerUIKit.profile.json":
    assert "@" not in profile["releaseVersion"]
    assert "@" not in profile["sourceRevision"]

platform = profile["platforms"]
assert platform == [{
    "os": "iOS",
    "minimumVersion": "13.0",
    "deviceArchitectures": ["arm64"],
    "simulatorArchitectures": ["arm64"],
}]

assert profile["runtime"] == {
    "ui": "UIKit/CoreGraphics/CoreText",
    "decoderBridge": "RcComposePlayer.xcframework Kotlin/Native",
    "retainedSession": True,
    "frameScheduling": ["static", "continuous", "next-frame", "delayed-wake"],
}, "runtime must match the reviewed core-v1 implementation contract"
assert profile["compatibility"] == {
    "policy": "No source or binary compatibility guarantee before the profile leaves experimental status.",
    "unsupportedBehavior": "Compatible mode renders the supported subset with diagnostics; strict mode refuses it.",
    "migration": "Use RcComposePlayerSwiftUI for documents outside this profile; the native product never replaces or redirects the CMP product.",
}, "compatibility must match the reviewed experimental migration contract"

assert profile["nativeNodeKinds"] == [
    "root", "content", "canvas", "group", "box", "row", "column", "text", "image",
], "nativeNodeKinds must match the reviewed core-v1 capability set"
assert profile["nativeDrawKinds"] == [
    "save", "restore", "translate", "scale", "rotate", "skew", "clip-rect", "clip-path",
    "rect", "oval", "circle", "line", "round-rect", "arc", "sector", "text", "path",
    "image",
], "nativeDrawKinds must match the reviewed core-v1 capability set"
assert profile["interaction"] == {
    "hostNamedValues": ["float", "string", "color"],
    "clickActions": ["click", "single-click"],
    "semanticRoles": ["button", "image", "checkbox", "switch", "unknown"],
}, "interaction must match the reviewed core-v1 capability set"

fixture_entries = profile["verifiedFixtures"]
assert len(fixture_entries) == 2, "verifiedFixtures must contain exactly two evidence entries"
assert len({item["id"] for item in fixture_entries}) == len(fixture_entries), (
    "verifiedFixtures identifiers must be unique"
)
fixtures = {item["id"]: item["coverage"] for item in fixture_entries}
assert fixtures == {
    "TitleCardRemote-640x480": [
        "layout", "native-text", "canvas-paint", "simulator-pixels",
    ],
    "IndeterminateCircularProgress-400x400": [
        "component-geometry", "continuous-time", "arc-paint", "simulator-motion",
    ],
}, "verifiedFixtures must match the reviewed core-v1 evidence set"

limits = profile["defaultLimits"]
required_limits = {
    "wireDocumentBytes", "wireBlobBytes", "wireStringBytes", "wireTableEntries",
    "wirePaintWords", "wirePathWords", "wireCollectionEntries", "wireImageDimension",
    "wireOperations", "containerNesting", "expansionDepth", "expandedNodes",
    "nativeDocumentBytes", "nativeNodes", "nativeNesting", "nativeDrawCommands",
    "nativePathElements", "nativeTextBytes", "nativeCanvasDimension",
    "nativeCoordinateMagnitude", "nativeFrameWork", "nativeResourceBytes",
    "nativeTotalResourceBytes", "nativeResourceImageDimension", "nativeDecodedPixels",
    "nativeDecodedImageBytes", "nativeResourceCount",
}
assert required_limits == set(limits)
assert all(isinstance(value, int) and value > 0 for value in limits.values())

def integer_default(relative, pattern):
    source = (repo / relative).read_text()
    match = re.search(pattern, source)
    assert match, f"profile drift: {pattern!r} missing from {relative}"
    expression = match.group(1)
    assert re.fullmatch(r"[\d_ *]+", expression), f"unsupported limit expression: {expression}"
    return eval(expression.replace("_", ""), {"__builtins__": {}}, {})

wire = "rc-player/protocol/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/protocol/RcWire.kt"
linker = "rc-player/runtime/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/runtime/RcDocumentLinker.kt"
native = "Sources/RcNativePlayerUIKit/NativeExecutionLimits.swift"
resources = "Sources/RcNativePlayerUIKit/NativeResources.swift"
implemented_limits = {
    "wireDocumentBytes": integer_default(wire, r"maxDocumentBytes: Int = ([\d_ *]+)"),
    "wireBlobBytes": integer_default(wire, r"maxBlobBytes: Int = ([\d_ *]+)"),
    "wireStringBytes": integer_default(wire, r"maxStringBytes: Int = ([\d_ *]+)"),
    "wireTableEntries": integer_default(wire, r"maxTableEntries: Int = ([\d_ *]+)"),
    "wirePaintWords": integer_default(wire, r"maxPaintWords: Int = ([\d_ *]+)"),
    "wirePathWords": integer_default(wire, r"maxPathWords: Int = ([\d_ *]+)"),
    "wireCollectionEntries": integer_default(wire, r"maxCollectionEntries: Int = ([\d_ *]+)"),
    "wireImageDimension": integer_default(wire, r"maxImageDimension: Int = ([\d_ *]+)"),
    "wireOperations": integer_default(wire, r"maxOperations: Int = ([\d_ *]+)"),
    "containerNesting": integer_default(linker, r"MAX_NESTING_DEPTH = ([\d_ *]+)"),
    "expansionDepth": integer_default(linker, r"MAX_EXPANSION_DEPTH = ([\d_ *]+)"),
    "expandedNodes": integer_default(linker, r"MAX_EXPANDED_NODES = ([\d_ *]+)"),
    "nativeDocumentBytes": integer_default(native, r"maximumDocumentBytes: Int = ([\d_ *]+)"),
    "nativeNodes": integer_default(native, r"maximumNodeCount: Int = ([\d_ *]+)"),
    "nativeNesting": integer_default(native, r"maximumNestingDepth: Int = ([\d_ *]+)"),
    "nativeDrawCommands": integer_default(native, r"maximumDrawCommandCount: Int = ([\d_ *]+)"),
    "nativePathElements": integer_default(native, r"maximumPathElementCount: Int = ([\d_ *]+)"),
    "nativeTextBytes": integer_default(native, r"maximumTextBytes: Int = ([\d_ *]+)"),
    "nativeCanvasDimension": integer_default(native, r"maximumCanvasDimension: Double = ([\d_ *]+)"),
    "nativeCoordinateMagnitude": integer_default(native, r"maximumCoordinateMagnitude: Double = ([\d_ *]+)"),
    "nativeFrameWork": integer_default(native, r"maximumFrameWork: Int = ([\d_ *]+)"),
    "nativeResourceBytes": integer_default(resources, r"maximumResourceBytes: Int = ([\d_ *]+)"),
    "nativeTotalResourceBytes": integer_default(resources, r"maximumTotalBytes: Int = ([\d_ *]+)"),
    "nativeResourceImageDimension": integer_default(resources, r"maximumImageDimension: Int = ([\d_ *]+)"),
    "nativeDecodedPixels": integer_default(resources, r"maximumDecodedPixels: Int = ([\d_ *]+)"),
    "nativeDecodedImageBytes": integer_default(resources, r"maximumDecodedImageBytes: Int = ([\d_ *]+)"),
    "nativeResourceCount": integer_default(resources, r"maximumResourceCount: Int = ([\d_ *]+)"),
}
assert limits == implemented_limits, (
    f"profile defaultLimits {limits!r} do not match implementation defaults {implemented_limits!r}"
)

distribution = profile["distribution"]
assert distribution == {
    "repositoryProduct": "RcNativePlayerUIKit",
    "standaloneArchive": "RcNativePlayerUIKit.swiftpackage.zip",
    "archiveChecksum": "RcNativePlayerUIKit.swiftpackage.zip.sha256",
    "profileAsset": "RcNativePlayerUIKit.profile.json",
    "profileChecksum": "RcNativePlayerUIKit.profile.json.sha256",
    "buildWorkflow": ".github/workflows/release.yml",
}, "distribution must match the reviewed core-v1 artifact contract"

source_checks = {
    "distribution/native-uikit/Package.swift": ["platforms: [.iOS(.v13)]"],
}
for relative, fragments in source_checks.items():
    source = (repo / relative).read_text()
    for fragment in fragments:
        assert fragment in source, f"profile drift: {fragment!r} missing from {relative}"

print(f"native UIKit profile: ok ({profile['releaseVersion']}, {path})")
PY

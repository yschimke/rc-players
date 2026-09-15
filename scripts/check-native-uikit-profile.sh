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

assert len(profile["nativeNodeKinds"]) == len(set(profile["nativeNodeKinds"]))
assert len(profile["nativeDrawKinds"]) == len(set(profile["nativeDrawKinds"]))
assert {"text", "image", "canvas"} <= set(profile["nativeNodeKinds"])
assert {"arc", "path", "text", "image"} <= set(profile["nativeDrawKinds"])

fixtures = {item["id"]: item["coverage"] for item in profile["verifiedFixtures"]}
assert "simulator-pixels" in fixtures["TitleCardRemote-640x480"]
assert "simulator-motion" in fixtures["IndeterminateCircularProgress-400x400"]

limits = profile["defaultLimits"]
required_limits = {
    "documentBytes", "wireOperations", "containerNesting", "expansionDepth",
    "expandedNodes", "nativeNodes", "nativeNesting", "drawCommands",
    "pathElements", "textBytesPerCommand", "canvasDimension",
    "coordinateMagnitude", "frameWork",
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
implemented_limits = {
    "documentBytes": integer_default(wire, r"maxDocumentBytes: Int = ([\d_ *]+)"),
    "wireOperations": integer_default(wire, r"maxOperations: Int = ([\d_ *]+)"),
    "containerNesting": integer_default(linker, r"MAX_NESTING_DEPTH = ([\d_ *]+)"),
    "expansionDepth": integer_default(linker, r"MAX_EXPANSION_DEPTH = ([\d_ *]+)"),
    "expandedNodes": integer_default(linker, r"MAX_EXPANDED_NODES = ([\d_ *]+)"),
    "nativeNodes": integer_default(native, r"maximumNodeCount: Int = ([\d_ *]+)"),
    "nativeNesting": integer_default(native, r"maximumNestingDepth: Int = ([\d_ *]+)"),
    "drawCommands": integer_default(native, r"maximumDrawCommandCount: Int = ([\d_ *]+)"),
    "pathElements": integer_default(native, r"maximumPathElementCount: Int = ([\d_ *]+)"),
    "textBytesPerCommand": integer_default(native, r"maximumTextBytes: Int = ([\d_ *]+)"),
    "canvasDimension": integer_default(native, r"maximumCanvasDimension: Double = ([\d_ *]+)"),
    "coordinateMagnitude": integer_default(native, r"maximumCoordinateMagnitude: Double = ([\d_ *]+)"),
    "frameWork": integer_default(native, r"maximumFrameWork: Int = ([\d_ *]+)"),
}
assert limits == implemented_limits, (
    f"profile defaultLimits {limits!r} do not match implementation defaults {implemented_limits!r}"
)

distribution = profile["distribution"]
for name in ("standaloneArchive", "archiveChecksum", "profileAsset", "profileChecksum"):
    assert distribution[name]

source_checks = {
    "distribution/native-uikit/Package.swift": ["platforms: [.iOS(.v13)]"],
}
for relative, fragments in source_checks.items():
    source = (repo / relative).read_text()
    for fragment in fragments:
        assert fragment in source, f"profile drift: {fragment!r} missing from {relative}"

print(f"native UIKit profile: ok ({profile['releaseVersion']}, {path})")
PY

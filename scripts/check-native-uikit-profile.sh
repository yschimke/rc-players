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

distribution = profile["distribution"]
for name in ("standaloneArchive", "archiveChecksum", "profileAsset", "profileChecksum"):
    assert distribution[name]

source_checks = {
    "rc-player/protocol/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/protocol/RcWire.kt": [
        "val maxDocumentBytes: Int = 16 * 1024 * 1024",
        "val maxOperations: Int = 100_000",
    ],
    "rc-player/runtime/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/runtime/RcDocumentLinker.kt": [
        "MAX_NESTING_DEPTH = 256",
        "MAX_EXPANSION_DEPTH = 64",
        "MAX_EXPANDED_NODES = 100_000",
    ],
    "Sources/RcNativePlayerUIKit/NativeExecutionLimits.swift": [
        "maximumNodeCount: Int = 20_000",
        "maximumNestingDepth: Int = 256",
        "maximumDrawCommandCount: Int = 50_000",
        "maximumPathElementCount: Int = 100_000",
        "maximumTextBytes: Int = 16 * 1024",
        "maximumCanvasDimension: Double = 16_384",
        "maximumCoordinateMagnitude: Double = 1_000_000",
        "maximumFrameWork: Int = 200_000",
    ],
    "distribution/native-uikit/Package.swift": ["platforms: [.iOS(.v13)]"],
}
for relative, fragments in source_checks.items():
    source = (repo / relative).read_text()
    for fragment in fragments:
        assert fragment in source, f"profile drift: {fragment!r} missing from {relative}"

print(f"native UIKit profile: ok ({profile['releaseVersion']}, {path})")
PY

# Native UIKit player distribution and compatibility

Status: experimental distribution decision for `rc-native-uikit-core-v1`.

## Decision

`RcNativePlayerUIKit` remains an additive, experimental iOS product. It does not replace, wrap, or
silently redirect the CMP player. A host chooses it explicitly and can keep
`RcComposePlayerSwiftUI` as the fallback for documents outside the declared native profile.

The repository SwiftPM product and the standalone downloadable package keep the current Swift API
shape, but neither source nor binary compatibility is promised while the profile is experimental.
The repository follows the project's ordinary semantic version; the independently versioned
profile id changes only when its meaning changes. Moving the bridge behind C/Objective-C or into a
Swift runtime is therefore allowed before stability. Such a move must preserve the high-level
UIKit initializer and event concepts where practical, document source migration, and use a major
release if it breaks an already stable profile.

This is intentionally the conservative choice. The current Kotlin/Native bridge preserves one
codec/link/runtime implementation and has strong conformance value, but it also dominates binary
size and exports Kotlin ABI that is wider than the UIKit layer needs. Coverage and maintenance data
do not yet justify declaring that bridge a permanent public contract.

## Products and platforms

| Deliverable | Purpose | Supported host |
| --- | --- | --- |
| `RcNativePlayerUIKit` repository SwiftPM product | Normal versioned source integration beside the CMP products | iOS 13+, arm64 device and Apple-silicon simulator |
| `RcNativePlayerUIKit.swiftpackage.zip` | Self-contained evaluation/CI package with the exact bridge XCFramework | iOS 13+, arm64 device and Apple-silicon simulator |
| `RcNativePlayerUIKit.profile.json` | Release-versioned, machine-readable capability and policy claim | Any JSON consumer |

UIKit is not claimed on macOS. The shared `RcComposePlayer.xcframework` still contains the macOS
arm64 CMP framework for the other SwiftPM products; that does not make the UIKit source product a
macOS renderer. Intel iOS simulators are not supported because Compose Multiplatform 1.11 does not
publish the required x86_64 Apple variant.

The standalone archive remains useful while the bridge is experimental: it pins Swift sources,
profile, and binary together without a second network fetch. Revisit removing it only after the
native runtime boundary and ordinary SwiftPM binary delivery are stable.

## Versioned profile

[`distribution/native-uikit/profile.json`](../../distribution/native-uikit/profile.json) is the
reviewed template. Release assembly replaces only `@VERSION@` and `@SOURCE_REVISION@`, writes
`RcNativePlayerUIKit.profile.json`, embeds the byte-identical file as `PROFILE.json` in the
standalone package, and writes SHA-256 sidecars for both files.

The profile is a positive claim: listed native node/draw kinds and verified fixtures are covered;
unlisted behavior is not implied. Compatibility mode may render a supported subset while reporting
diagnostics. Strict mode refuses any known unsupported or approximate behavior. The profile also
records the default decode and per-frame limits so a release cannot quietly widen its resource
contract without a reviewable data diff.

`scripts/check-native-uikit-profile.sh` validates the schema and invariants. The package consumer
check verifies the profile checksum and requires the standalone copy to match the release-side copy
byte for byte.

## Provenance and verification

The Apple release job builds from the `vX.Y.Z` source tag, injects that release version and
`GITHUB_SHA` into the profile, creates checksums, validates the extracted package as an iOS consumer,
and publishes GitHub build-provenance attestations for the XCFramework, standalone package, and
profile before uploading them to the Release.

A consumer can verify the downloadable package with:

```sh
shasum -a 256 -c RcNativePlayerUIKit.swiftpackage.zip.sha256
shasum -a 256 -c RcNativePlayerUIKit.profile.json.sha256
gh attestation verify RcNativePlayerUIKit.swiftpackage.zip --repo yschimke/rc-players
```

The profile's `sourceRevision`, the attestation subject digest, and the release tag should describe
the same build. `Package.swift`'s XCFramework URL/checksum remains release-job-owned and is still
updated only by `scripts/update-package-swift.sh`.

## Stability gate and migration story

The profile can leave experimental status only after all of these are true:

- its fixture corpus covers each claimed layout, drawing, resource, input, semantic, and animation
  family with owned diagnostics and CMP comparison evidence;
- fixed-device decode, first-frame, update, view-count, allocation, memory, and binary-size data
  have reviewed budgets and a regression history;
- VoiceOver, Switch Control, Dynamic Type, contrast, lifecycle, Reduce Motion, and deallocation
  checks run on the supported platform matrix;
- the narrow long-term runtime boundary (retained Kotlin API, C/Objective-C facade, or Swift
  runtime) has an owner and an ABI policy;
- at least one released standalone artifact has been verified from outside the repository build.

Until then, migration is explicit and low risk: keep the CMP product installed, select the native
product only for documents inside the released profile, and route unsupported documents back to
`RcComposePlayerSwiftUI`. Removing the native experiment never requires changing or removing the
CMP APIs because the products have remained separate from the first release.

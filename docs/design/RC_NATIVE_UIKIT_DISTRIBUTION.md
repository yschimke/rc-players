# Native UIKit player distribution and compatibility

Status: experimental distribution decision for `rc-native-uikit-core-v1`.

## Decision

`RcNativePlayerUIKit` remains an additive, experimental iOS product. It does not replace, wrap, or
silently redirect the CMP player. A host chooses it explicitly and can keep
`RcComposePlayerSwiftUI` as the fallback for documents outside the declared native profile.

The repository SwiftPM product and the standalone downloadable package keep the current Swift API
shape, but neither source nor binary compatibility is promised while the profile is experimental.
The repository follows the project's ordinary semantic version; the independently versioned
profile id changes only when its meaning changes. Extending or replacing internal Swift runtime
types is therefore allowed before stability. Such a move must preserve the high-level
UIKit initializer and event concepts where practical, document source migration, and use a major
release if it breaks an already stable profile.

The player is deliberately source-only. CMP preserves its value as an independent conformance
oracle, while the native artifact avoids shipping a second runtime and exposes only an idiomatic
Swift surface. Coverage and maintenance data do not yet justify a stability promise.

## Products and platforms

| Deliverable | Purpose | Supported host |
| --- | --- | --- |
| `RcPlayerAppleFonts` repository and archive product | Optional, bounded Google Fonts resolver shared by Apple players | iOS 13+, macOS 12+ |
| `RcNativePlayerUIKit` repository SwiftPM product | Normal versioned source integration beside the CMP products | iOS 13+, arm64 device and Apple-silicon simulator |
| `RcNativePlayerUIKit.swiftpackage.zip` | Self-contained pure-Swift evaluation/CI package | iOS 13+, arm64 device and Apple-silicon simulator |
| `RcNativePlayerUIKit.profile.json` | Release-versioned, machine-readable capability and policy claim | Any JSON consumer |

UIKit is not claimed on macOS. The shared `RcComposePlayer.xcframework` still contains the macOS
arm64 CMP framework for the other SwiftPM products; that does not make the UIKit source product a
macOS renderer. Intel iOS simulators are not supported because Compose Multiplatform 1.11 does not
publish the required x86_64 Apple variant.

The standalone archive pins the Swift sources and profile together without a binary or second
network fetch. Revisit removing it only after ordinary versioned SwiftPM source delivery is stable.

## Versioned profile

[`distribution/native-uikit/profile.json`](../../distribution/native-uikit/profile.json) is the
reviewed template. Release assembly replaces only `@VERSION@` and `@SOURCE_REVISION@`, writes
`RcNativePlayerUIKit.profile.json`, embeds the byte-identical file as `PROFILE.json` in the
standalone package, and writes SHA-256 sidecars for both files.

The profile is a positive claim: listed native node/draw kinds and verified fixtures are covered;
unlisted behavior is not implied. Compatibility mode may render a supported subset while reporting
diagnostics. Strict mode refuses any known unsupported or approximate behavior. The profile also
records the default decode and per-frame limits so a release cannot quietly widen its resource
contract without a reviewable data diff, states the density contract the renderer applies
(`densityContract`), and points at the fuzz corpus and the three benchmark lanes the release was
gated on (`evidence`).

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

### Automated simulator baseline

`scripts/measure-native-uikit-simulator.sh` installs the packaged Release sample on a named iPad
simulator and asks the app to measure seven direct decode/render iterations of
`TitleCardRemote-640x480`. The JSON artifact records its source revision, simulator model and OS,
median decode/first-frame/update time, UIKit hierarchy composition, accessibility elements,
allocation and physical-footprint growth, executable size, and installed app-bundle size. CI
retains `native-uikit-evidence.json` beside the rendered evidence. The same run interrupts a real
public player view's initial load with background/active notifications, requires it to resume to a
native document hierarchy, releases the last strong reference, and requires ARC deallocation.

The initial regression ceilings are deliberately broad: 250 ms decode, 250 ms first frame, 100 ms
update, 500 views, 32 MiB allocation growth, 64 MiB physical-footprint growth, a 200 MiB
executable, and a 300 MiB app bundle. The Title Card must also construct at least two `UILabel`s,
one `UIControl`, one `UIButton`, and three accessibility-capable native views. Its explicit UIKit
accessibility container exposes exactly one combined button target so assistive technology does not
visit the two descendant labels again. This makes accidental removal of native conceptual elements
or reintroduction of duplicate focus stops a failure rather than a smaller-view-count
"improvement."

This is reproducible packaged-artifact evidence, but simulator wall-clock and memory values are not
a physical-device performance claim. The report is a CI regression tripwire and establishes the
measurement format; the stability gate below still requires a reviewed fixed-device series.

The profile can leave experimental status only after all of these are true:

- its fixture corpus covers each claimed layout, drawing, resource, input, semantic, and animation
  family with owned diagnostics and CMP comparison evidence;
- fixed-device decode, first-frame, update, view-count, allocation, memory, and binary-size data
  have reviewed budgets and a regression history;
- VoiceOver, Switch Control, Dynamic Type, contrast, lifecycle, Reduce Motion, and deallocation
  checks run on the supported platform matrix;
- the long-term Swift runtime boundary has an owner and an API/ABI policy;
- at least one released standalone artifact has been verified from outside the repository build.

Until then, migration is explicit and low risk: keep the CMP product installed, select the native
product only for documents inside the released profile, and route unsupported documents back to
`RcComposePlayerSwiftUI`. Removing the native experiment never requires changing or removing the
CMP APIs because the products have remained separate from the first release.

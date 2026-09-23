// swift-tools-version:5.9
//
// Swift Package Manager distribution for the Remote Compose player's Apple framework (#4068).
//
// TO USE THIS PACKAGE, resolve it by a BARE version tag:
//
//     .package(url: "https://github.com/yschimke/rc-players.git", from: "1.60.0")
//
// THE COPY ON `main` IS A PLACEHOLDER AND ALWAYS WILL BE. The two values below are deliberately
// unusable on `main`, and that is not a bug to fix: a manifest cannot state the SHA-256 of an asset
// that has not been built yet, and the asset's URL contains the very tag being released. The real
// values can only exist *after* the release, so they live on the bare `X.Y.Z` tag that
// `release.yml` creates once the XCFramework is uploaded. Resolving `main` fails the checksum
// check, loudly and on purpose, rather than fetching something unverified.
//
// Bare, not `v`-prefixed, and not both: SwiftPM only reads a tag as a semantic version when the
// whole ref is `X.Y.Z` or `vX.Y.Z`, so `swift/1.16.0` is invisible to the syntax above. The
// `v<version>` release tag IS visible to SwiftPM — and its copy of this file is the placeholder,
// because it was cut before the asset existed. That is exactly why the bare tag is published: it
// is the one ref whose manifest describes a real asset. Prefer it, and see
// docs/design/RC_PLAYER_SWIFT.md for how to check a version has one.
//
// `scripts/update-package-swift.sh`, run by `release.yml`, is the only thing that should write the
// two values — by hand is never right, because they have to describe an asset that already exists
// and SPM verifies the checksum at resolve time.
//
// Coverage: `iosArm64` (device), `iosSimulatorArm64` (Apple-silicon simulator), and `macosArm64`.
// Compose Multiplatform 1.11 publishes no Apple x86_64 variants, so Intel simulators and Intel Macs
// cannot build against this. Stated here rather than discovered at link time. See
// docs/design/RC_PLAYER_SWIFT.md.
import Foundation
import PackageDescription

/// Set `RC_COMPOSE_PLAYER_NATIVE_ONLY=1` when resolving the source-overlay wrapper without the
/// Kotlin/Native XCFramework. SwiftPM resolves every declared binary target before compilation, so
/// a conditional import alone cannot provide this mode.
let nativeOnly = ProcessInfo.processInfo.environment["RC_COMPOSE_PLAYER_NATIVE_ONLY"] == "1"

var products: [Product] = [
  .library(name: "RcPlayerAppleFonts", targets: ["RcPlayerAppleFonts"]),
  .library(name: "RcComposePlayerSwiftUI", targets: ["RcComposePlayerSwiftUI"]),
  .library(name: "RcNativePlayerCore", targets: ["RcNativePlayerCore"]),
  .library(name: "RcNativePlayerUIKit", targets: ["RcNativePlayerUIKit"]),
]
if !nativeOnly {
  products.insert(.library(name: "RcComposePlayer", targets: ["RcComposePlayer"]), at: 0)
}

var swiftUIDependencies: [Target.Dependency] = ["RcPlayerAppleFonts", "RcNativePlayerUIKit"]
if !nativeOnly { swiftUIDependencies.insert("RcComposePlayer", at: 0) }

var targets: [Target] = [
  .target(name: "RcPlayerAppleFonts"),
  .target(name: "RcComposePlayerSwiftUI", dependencies: swiftUIDependencies),
  .target(name: "RcNativePlayerCore"),
  .target(name: "RcNativePlayerUIKit", dependencies: ["RcNativePlayerCore", "RcPlayerAppleFonts"]),
]
if !nativeOnly {
  targets.insert(
    .binaryTarget(
      name: "RcComposePlayer",
      url:
        "https://github.com/yschimke/rc-players/releases/download/v0.0.0/RcComposePlayer.xcframework.zip",
      // On `main` these two are the placeholder and a resolve fails loudly on the checksum rather
      // than fetching something unverified. On a bare `X.Y.Z` tag they are the real released
      // values, written by scripts/update-package-swift.sh. Check which you are looking at.
      checksum: "0000000000000000000000000000000000000000000000000000000000000000"
    ), at: 0)
}

let package = Package(
  name: "RcComposePlayer",
  platforms: [.iOS(.v13), .macOS(.v12)],
  products: products,
  targets: targets
)

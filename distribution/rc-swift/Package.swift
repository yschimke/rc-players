// swift-tools-version:6.0
//
// Pure-Swift distribution of the Remote Compose native player and Creation APIs, synced from
// https://github.com/yschimke/rc-players on every release.
//
// Unlike `yschimke/rc-players`'s root manifest, this package declares no Kotlin/Native XCFramework
// binary target, so SwiftPM resolves it without downloading the Compose Multiplatform framework
// and works on Linux as well as Apple platforms.
import PackageDescription

#if os(Linux) || os(Windows)
  let coreOnly = true
#else
  let coreOnly = false
#endif

var products: [Product] = [
  .library(name: "RcNativePlayerCore", targets: ["RcNativePlayerCore"]),
  .library(name: "RcCreation", targets: ["RcCreation"]),
  .library(name: "RcCreationFoundation", targets: ["RcCreationFoundation"]),
  .library(name: "RcCreationAppleUI", targets: ["RcCreationAppleUI"]),
  .library(name: "RcCreationHost", targets: ["RcCreationHost"]),
]

var creationHostDependencies: [Target.Dependency] = ["RcCreation", "RcNativePlayerCore"]

var targets: [Target] = [
  .target(name: "RcNativePlayerCore"),
  .target(name: "RcWireOperations"),
  .target(name: "RcCreation", dependencies: ["RcWireOperations"]),
  .target(name: "RcCreationFoundation", dependencies: ["RcCreation"]),
  .target(name: "RcCreationAppleUI", dependencies: ["RcCreation", "RcCreationFoundation"]),
]

if !coreOnly {
  products += [
    .library(name: "RcPlayerAppleFonts", targets: ["RcPlayerAppleFonts"]),
    .library(name: "RcNativePlayerUIKit", targets: ["RcNativePlayerUIKit"]),
  ]
  creationHostDependencies.append("RcNativePlayerUIKit")
  targets += [
    .target(name: "RcPlayerAppleFonts", resources: [.copy("PrivacyInfo.xcprivacy")]),
    .target(
      name: "RcNativePlayerUIKit",
      dependencies: ["RcNativePlayerCore", "RcPlayerAppleFonts"],
      resources: [.copy("PrivacyInfo.xcprivacy")]
    ),
  ]
}

targets.append(.target(name: "RcCreationHost", dependencies: creationHostDependencies))

let package = Package(
  name: "RcSwift",
  platforms: [.iOS(.v13), .macOS(.v12), .visionOS(.v1)],
  products: products,
  targets: targets,
  swiftLanguageModes: [.v6]
)

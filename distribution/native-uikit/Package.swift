// swift-tools-version:5.9
import PackageDescription

let package = Package(
  name: "RcNativePlayerUIKit",
  platforms: [.iOS(.v13), .macOS(.v12), .visionOS(.v1)],
  products: [
    .library(name: "RcPlayerAppleFonts", targets: ["RcPlayerAppleFonts"]),
    .library(name: "RcNativePlayerCore", targets: ["RcNativePlayerCore"]),
    .library(name: "RcNativePlayerUIKit", targets: ["RcNativePlayerUIKit"]),
  ],
  targets: [
    .target(name: "RcPlayerAppleFonts", resources: [.copy("PrivacyInfo.xcprivacy")]),
    .target(name: "RcNativePlayerCore"),
    .target(
      name: "RcNativePlayerUIKit",
      dependencies: ["RcNativePlayerCore", "RcPlayerAppleFonts"],
      resources: [.copy("PrivacyInfo.xcprivacy")]
    ),
    .testTarget(
      name: "RcNativePlayerUIKitTests",
      dependencies: ["RcNativePlayerCore", "RcNativePlayerUIKit"],
      resources: [.copy("Fixtures")]
    ),
  ]
)

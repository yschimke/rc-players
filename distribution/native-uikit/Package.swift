// swift-tools-version:5.9
import PackageDescription

let package = Package(
  name: "RcNativePlayerUIKit",
  platforms: [.iOS(.v13), .macOS(.v12)],
  products: [
    .library(name: "RcNativePlayerCore", targets: ["RcNativePlayerCore"]),
    .library(name: "RcNativePlayerUIKit", targets: ["RcNativePlayerUIKit"]),
  ],
  targets: [
    .target(name: "RcNativePlayerCore"),
    .target(name: "RcNativePlayerUIKit", dependencies: ["RcNativePlayerCore"]),
  ]
)

// swift-tools-version:5.9
import PackageDescription

let package = Package(
  name: "RcNativePlayerUIKit",
  platforms: [.iOS(.v13)],
  products: [
    .library(name: "RcNativePlayerUIKit", targets: ["RcNativePlayerUIKit"]),
  ],
  targets: [
    .target(name: "RcNativePlayerUIKit"),
  ]
)

// swift-tools-version:6.0
import Foundation
import PackageDescription

let playerPath = ProcessInfo.processInfo.environment["RC_NATIVE_COLOR_FILTER_SOURCE"] ?? "../.."
let package = Package(
  name: "ColorFilterEvidence",
  dependencies: [.package(path: playerPath)],
  targets: [
    .executableTarget(
      name: "ColorFilterEvidence",
      dependencies: [.product(name: "RcNativePlayerCore", package: "rc-players")])
  ])

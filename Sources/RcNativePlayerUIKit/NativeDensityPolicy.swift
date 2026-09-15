import CoreGraphics
import Foundation

/// Whether the native player reproduces the Android density contract for dp-typed geometry.
///
/// A `.rc` document records the density it was generated at plus how dp-typed values are meant to
/// be converted at playback. Apple UI is authored in points and has no equivalent of that
/// conversion, so the native player does not adopt it implicitly: by default dp-typed geometry
/// resolves at a density of 1.0 and therefore scales with the document like every other captured
/// coordinate. A host that is deliberately replaying Android-authored content opts into the
/// document's own contract instead.
///
/// This axis is independent of which renderer the host selected. CMP always applies the Android
/// contract because it is the compatibility oracle; the native renderer states its choice.
public enum RemoteComposeNativePlayerAndroidCompatibility: Equatable, Sendable {
  /// Resolve dp-typed geometry at density 1.0 and report documents that expected otherwise.
  case disabled

  /// Convert dp-typed geometry with the playback density, reproducing the authored Android layout.
  case enabled
}

/// Pure density policy for the native renderers.
///
/// Keeping the decision here rather than inside `layoutSubviews` makes "what does a non-1.0 density
/// document do" answerable by a test that builds no view hierarchy.
enum NativeDensityPolicy {
  /// `CoreDocument.DENSITY_BEHAVIOR_PIXELS`: values are pixels and constraints divide by density.
  static let pixelBehavior = 1

  /// `CoreDocument.DENSITY_BEHAVIOR_DP`: dp-typed values multiply by the playback density.
  static let dpBehavior = 2

  /// The scale dp-typed geometry is resolved with, in document units.
  ///
  /// - Parameter playbackDensityScale: document units per host point, which is what the Android
  ///   contract calls the playback density once the root transform has been applied.
  static func layoutDensityScale(
    androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility,
    playbackDensityScale: CGFloat
  ) -> CGFloat {
    guard androidCompatibility == .enabled else { return 1 }
    guard playbackDensityScale.isFinite, playbackDensityScale > 0 else { return 1 }
    return playbackDensityScale
  }

  /// Whether this document's authored layout depends on a density other than 1.0.
  static func requiresNonUnitDensity(density: Float, densityBehavior: Int) -> Bool {
    guard density.isFinite, density != 1 else { return false }
    return densityBehavior == dpBehavior || densityBehavior == pixelBehavior
  }

  /// Compatibility diagnostics for the document header's density contract.
  ///
  /// A density the native profile does not reproduce is reported rather than applied silently, so
  /// `.strict` refuses the document and `.compatible` renders it with the difference stated.
  static func diagnostics(
    density: Float,
    densityBehavior: Int,
    androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility,
    componentID: Int
  ) -> [RemoteComposeNativePlayerDiagnostic] {
    guard requiresNonUnitDensity(density: density, densityBehavior: densityBehavior) else {
      return []
    }
    if densityBehavior == pixelBehavior {
      return [
        RemoteComposeNativePlayerDiagnostic(
          severity: .warning,
          opcode: 0,
          operationName: "Header",
          componentID: componentID,
          reason:
            "Document declares pixel density behavior at density \(density); the native player "
            + "does not convert pixel-typed constraints")
      ]
    }
    guard androidCompatibility == .disabled else { return [] }
    return [
      RemoteComposeNativePlayerDiagnostic(
        severity: .warning,
        opcode: 0,
        operationName: "Header",
        componentID: componentID,
        reason:
          "Document declares dp layout behavior at density \(density); native rendering resolves "
          + "dp geometry at density 1.0. Set androidCompatibility to .enabled to reproduce the "
          + "authored layout")
    ]
  }
}

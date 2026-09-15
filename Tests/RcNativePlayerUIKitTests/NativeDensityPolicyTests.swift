import CoreGraphics
import Foundation

@main
enum NativeDensityPolicyTests {
  static func main() {
    // A document generated at density 2 with dp layout behavior: the title-card case.
    let dpAtTwo = NativeDensityPolicy.diagnostics(
      density: 2, densityBehavior: NativeDensityPolicy.dpBehavior,
      androidCompatibility: .disabled, componentID: 1)
    precondition(dpAtTwo.count == 1, "a non-unit dp density must be diagnosed by default")
    precondition(dpAtTwo[0].severity == .warning)
    precondition(dpAtTwo[0].opcode == 0 && dpAtTwo[0].operationName == "Header")
    precondition(dpAtTwo[0].componentID == 1)
    precondition(dpAtTwo[0].reason.contains("density 1.0"))

    // Opting in silences the diagnostic because the geometry is then reproduced deliberately.
    precondition(
      NativeDensityPolicy.diagnostics(
        density: 2, densityBehavior: NativeDensityPolicy.dpBehavior,
        androidCompatibility: .enabled, componentID: 1
      ).isEmpty)

    // Density 1 needs no conversion either way, and pixel-typed documents are unaffected by dp.
    precondition(
      NativeDensityPolicy.diagnostics(
        density: 1, densityBehavior: NativeDensityPolicy.dpBehavior,
        androidCompatibility: .disabled, componentID: 1
      ).isEmpty)
    precondition(
      NativeDensityPolicy.diagnostics(
        density: 3, densityBehavior: 0, androidCompatibility: .disabled, componentID: 1
      ).isEmpty)

    // Pixel behavior is not converted by either mode, so it stays visible in both.
    for compatibility in [
      RemoteComposeNativePlayerAndroidCompatibility.disabled, .enabled,
    ] {
      let pixels = NativeDensityPolicy.diagnostics(
        density: 2, densityBehavior: NativeDensityPolicy.pixelBehavior,
        androidCompatibility: compatibility, componentID: 7)
      precondition(pixels.count == 1, "pixel-typed constraints are unimplemented in both modes")
      precondition(pixels[0].componentID == 7 && pixels[0].severity == .warning)
    }

    precondition(
      NativeDensityPolicy.requiresNonUnitDensity(
        density: 2, densityBehavior: NativeDensityPolicy.dpBehavior))
    precondition(
      !NativeDensityPolicy.requiresNonUnitDensity(
        density: 1, densityBehavior: NativeDensityPolicy.dpBehavior))
    precondition(
      !NativeDensityPolicy.requiresNonUnitDensity(density: .nan, densityBehavior: 2))

    // Default native rendering resolves dp geometry at density 1.0 whatever the viewport fit is.
    for scale in [CGFloat(0.5), 1, 2, 3.75] {
      precondition(
        NativeDensityPolicy.layoutDensityScale(
          androidCompatibility: .disabled, playbackDensityScale: scale) == 1)
    }
    // Android compatibility honors the playback density the root transform resolved.
    precondition(
      NativeDensityPolicy.layoutDensityScale(
        androidCompatibility: .enabled, playbackDensityScale: 2) == 2)
    // A degenerate viewport can never scale geometry by zero, infinity, or NaN.
    for scale in [CGFloat(0), -1, .infinity, .nan] {
      precondition(
        NativeDensityPolicy.layoutDensityScale(
          androidCompatibility: .enabled, playbackDensityScale: scale) == 1)
    }

    // A density warning is a difference, so strict refuses it and compatible renders it.
    let diagnostics = RemoteComposeNativePlayerDiagnostics(
      issues: dpAtTwo, unsupportedOpcodes: [], notes: [])
    precondition(diagnostics.isPartial)
    precondition(
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .compatible, diagnostics: diagnostics))
    precondition(
      !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .strict, diagnostics: diagnostics))

    print("native UIKit density policy tests: ok")
  }
}

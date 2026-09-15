import CoreGraphics
import Foundation

@main
enum NativeDensityPolicyTests {
  static func main() {
    // A document generated at density 2 with dp layout behavior: the title-card case.
    let dpAtTwo = NativeDensityPolicy.diagnostics(
      density: 2, densityBehavior: NativeDensityPolicy.dpBehavior,
      androidCompatibility: .disabled, componentID: 1)
    precondition(dpAtTwo.count == 1, "dp geometry must be diagnosed by default")
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

    // The condition is the density *behavior*, not the generation density: a dp document
    // generated at density 1 still differs whenever the host plays it at any other density, and
    // the playback density is not knowable at decode time.
    let dpAtOne = NativeDensityPolicy.diagnostics(
      density: 1, densityBehavior: NativeDensityPolicy.dpBehavior,
      androidCompatibility: .disabled, componentID: 1)
    precondition(dpAtOne.count == 1, "dp geometry is unconverted by default at any density")
    precondition(dpAtOne[0].reason.contains("density 1.0"))

    // Legacy behavior carries no density-typed geometry, whatever density it was generated at.
    precondition(
      NativeDensityPolicy.diagnostics(
        density: 3, densityBehavior: 0, androidCompatibility: .disabled, componentID: 1
      ).isEmpty)
    precondition(
      NativeDensityPolicy.diagnostics(
        density: 1, densityBehavior: 0, androidCompatibility: .enabled, componentID: 1
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

    // A non-finite declared density is still reportable rather than interpolated into the message.
    let invalid = NativeDensityPolicy.diagnostics(
      density: .nan, densityBehavior: NativeDensityPolicy.dpBehavior,
      androidCompatibility: .disabled, componentID: 1)
    precondition(invalid.count == 1 && invalid[0].reason.contains("an invalid density"))

    precondition(
      NativeDensityPolicy.usesDensityTypedGeometry(
        densityBehavior: NativeDensityPolicy.dpBehavior))
    precondition(
      NativeDensityPolicy.usesDensityTypedGeometry(
        densityBehavior: NativeDensityPolicy.pixelBehavior))
    precondition(!NativeDensityPolicy.usesDensityTypedGeometry(densityBehavior: 0))

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

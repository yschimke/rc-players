import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerUIKit

@Suite struct NativeDensityPolicyTests {
  @Test func densityPolicy() throws {
    // A document generated at density 2 with dp layout behavior: the title-card case.
    let dpAtTwo = NativeDensityPolicy.diagnostics(
      density: 2, densityBehavior: NativeDensityPolicy.dpBehavior,
      androidCompatibility: .disabled, componentID: 1)
    try #require(dpAtTwo.count == 1, "dp geometry must be diagnosed by default")
    #expect(dpAtTwo[0].severity == .warning)
    #expect(dpAtTwo[0].opcode == 0 && dpAtTwo[0].operationName == "Header")
    #expect(dpAtTwo[0].componentID == 1)
    #expect(dpAtTwo[0].reason.contains("density 1.0"))

    // Opting in silences the diagnostic because the geometry is then reproduced deliberately.
    #expect(
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
    try #require(dpAtOne.count == 1, "dp geometry is unconverted by default at any density")
    #expect(dpAtOne[0].reason.contains("density 1.0"))

    // Legacy behavior carries no density-typed geometry, whatever density it was generated at.
    #expect(
      NativeDensityPolicy.diagnostics(
        density: 3, densityBehavior: 0, androidCompatibility: .disabled, componentID: 1
      ).isEmpty)
    #expect(
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
      try #require(pixels.count == 1, "pixel-typed constraints are unimplemented in both modes")
      #expect(pixels[0].componentID == 7 && pixels[0].severity == .warning)
    }

    // A non-finite declared density is still reportable rather than interpolated into the message.
    let invalid = NativeDensityPolicy.diagnostics(
      density: .nan, densityBehavior: NativeDensityPolicy.dpBehavior,
      androidCompatibility: .disabled, componentID: 1)
    #expect(invalid.count == 1 && invalid[0].reason.contains("an invalid density"))

    #expect(
      NativeDensityPolicy.usesDensityTypedGeometry(
        densityBehavior: NativeDensityPolicy.dpBehavior))
    #expect(
      NativeDensityPolicy.usesDensityTypedGeometry(
        densityBehavior: NativeDensityPolicy.pixelBehavior))
    #expect(!NativeDensityPolicy.usesDensityTypedGeometry(densityBehavior: 0))

    // Default native rendering resolves dp geometry at density 1.0 whatever the viewport fit is.
    for scale in [CGFloat(0.5), 1, 2, 3.75] {
      #expect(
        NativeDensityPolicy.layoutDensityScale(
          androidCompatibility: .disabled, playbackDensityScale: scale) == 1)
    }
    // Android compatibility honors the playback density the root transform resolved.
    #expect(
      NativeDensityPolicy.layoutDensityScale(
        androidCompatibility: .enabled, playbackDensityScale: 2) == 2)
    // A degenerate viewport can never scale geometry by zero, infinity, or NaN.
    for scale in [CGFloat(0), -1, .infinity, .nan] {
      #expect(
        NativeDensityPolicy.layoutDensityScale(
          androidCompatibility: .enabled, playbackDensityScale: scale) == 1)
    }

    // `DimensionIn` runs the other way round from every other dp-typed field: LEGACY and DP are
    // both dp, and only PIXELS is pixels. A LEGACY document is the one that separates this rule
    // from `layoutUnitScale`'s, and it is what the whole catalog corpus is.
    for behavior in [0, 2] {
      #expect(
        NativeDensityPolicy.dimensionConstraintScale(
          densityBehavior: behavior, layoutDensityScale: 2, documentScale: 1) == 2,
        "a DimensionIn bound is dp under density behavior \(behavior)")
    }
    #expect(
      NativeDensityPolicy.dimensionConstraintScale(
        densityBehavior: NativeDensityPolicy.pixelBehavior,
        layoutDensityScale: 2, documentScale: 1) == 1)

    // A density warning is a difference, so strict refuses it and compatible renders it.
    let diagnostics = RemoteComposeNativePlayerDiagnostics(
      issues: dpAtTwo, unsupportedOpcodes: [], notes: [])
    #expect(diagnostics.isPartial)
    #expect(
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .compatible, diagnostics: diagnostics))
    #expect(
      !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .strict, diagnostics: diagnostics))
  }
}

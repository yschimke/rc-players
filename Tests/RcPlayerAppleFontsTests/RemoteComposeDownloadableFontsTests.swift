import CoreGraphics
import CoreText
import Foundation

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

@main
enum RemoteComposeDownloadableFontsTests {
  static func main() async throws {
    let validated = try RemoteComposeGoogleFontsResolver.validatedFamily("  Roboto Flex ")
    precondition(validated == "Roboto Flex")
    for invalid in ["", "google:Roboto", "Roboto&family=Evil", String(repeating: "a", count: 101)] {
      do {
        _ = try RemoteComposeGoogleFontsResolver.validatedFamily(invalid)
        preconditionFailure("accepted invalid family: \(invalid)")
      } catch RemoteComposeDownloadableFontError.invalidFamily {
      }
    }

    let base = URL(string: "https://fonts.googleapis.com/css2?family=Orbitron")!
    let expected = URL(string: "https://fonts.gstatic.com/s/orbitron/v1/orbitron.ttf")!
    precondition(
      RemoteComposeGoogleFontsResolver.fontURL(
        from: "@font-face { src: url(\(expected)) format('truetype'); }", relativeTo: base)
        == expected)
    // WOFF2 is registrable, so a modern stylesheet's face is usable — and the Latin block is last,
    // because Google emits one @font-face per unicode range and the first would be cyrillic-ext.
    precondition(
      RemoteComposeGoogleFontsResolver.fontURL(
        from: "@font-face { src: url(https://fonts.gstatic.com/font.woff2) format('woff2'); }",
        relativeTo: base) == URL(string: "https://fonts.gstatic.com/font.woff2"))
    let subsets = """
      @font-face { unicode-range: U+0400-045F; src: url(https://fonts.gstatic.com/cyrillic.woff2) format('woff2'); }
      @font-face { unicode-range: U+0000-00FF; src: url(https://fonts.gstatic.com/latin.woff2) format('woff2'); }
      """
    precondition(
      RemoteComposeGoogleFontsResolver.fontURL(from: subsets, relativeTo: base)
        == URL(string: "https://fonts.gstatic.com/latin.woff2"))
    precondition(
      RemoteComposeGoogleFontsResolver.fontURL(
        from: "@font-face { src: url('/font.otf'); }", relativeTo: base)
        == URL(string: "https://fonts.googleapis.com/font.otf"))

    // The variable-axis query leads, so one registered face can render every run's weight; the
    // plain family and the legacy agent are the fallbacks for a family that has no `wght` axis.
    let candidates = RemoteComposeGoogleFontsResolver.candidates(for: "Roboto Flex")
    precondition(candidates.count == 3)
    precondition(candidates[0].query == "Roboto Flex:wght@100..1000")
    precondition(candidates[1].query == "Roboto Flex")
    precondition(candidates[2].userAgent.contains("Android 4.0.3"))

    try testFontVariation()

    if ProcessInfo.processInfo.environment["RC_LIVE_GOOGLE_FONTS"] == "1" {
      let resolver = RemoteComposeGoogleFontsResolver()
      let font = try await resolver.resolve(
        RemoteComposeDownloadableFontRequest(family: "Orbitron"))
      precondition(font.family == "Orbitron")
      precondition(font.data.count > 1_000)
      precondition(font.identity.hasPrefix("google:orbitron#sha256:"))
      // A variable family is the point of the modern-agent candidate: the served face must carry
      // the `wght` axis, so the per-run weights the document declares can be applied.
      let flex = try await resolver.resolve(
        RemoteComposeDownloadableFontRequest(family: "Roboto Flex"))
      precondition(
        RemoteComposeFontVariation.hasWeightAxis(descriptor(for: flex.data)),
        "Google served Roboto Flex without a weight axis")
    }
    print("Apple downloadable-font tests: ok")
  }

  /// The weight a document carries on a text run has to survive resolution.
  ///
  /// Uses the vendored variable face and its static sibling, so the axis/no-axis split is measured
  /// against real fonts rather than against a mock.
  private static func testFontVariation() throws {
    let root = URL(fileURLWithPath: #filePath)
      .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    let fonts = root.appendingPathComponent("rc-player/wasm/dist-assets/fonts")
    let variable = descriptor(
      forFontAt: fonts.appendingPathComponent("RobotoFlex.ttf").path)
    let staticFace = descriptor(
      forFontAt: fonts.appendingPathComponent("Roboto-Regular.ttf").path)

    precondition(RemoteComposeFontVariation.hasWeightAxis(variable))
    precondition(!RemoteComposeFontVariation.hasWeightAxis(staticFace))

    let wght = NSNumber(value: RemoteComposeFontVariation.weightAxisIdentifier)
    guard
      let weighted = RemoteComposeFontVariation.descriptor(variable, applyingWeight: 700)
    else {
      preconditionFailure("the variable face refused a weight")
    }
    let weightedFont = CTFontCreateWithFontDescriptor(weighted, 16, nil)
    let variation = CTFontCopyVariation(weightedFont) as? [NSNumber: NSNumber]
    precondition(
      variation?[wght]?.floatValue == 700,
      "expected the wght axis at 700, got \(String(describing: variation))")

    precondition(
      RemoteComposeFontVariation.descriptor(staticFace, applyingWeight: 700) == nil,
      "a static face must not claim a weight axis")
    precondition(
      RemoteComposeFontVariation.symbolicTraits(forWeight: 700, existing: []) == .traitBold)
    precondition(
      RemoteComposeFontVariation.symbolicTraits(forWeight: 300, existing: []) == [],
      "lighter-than-default weights are left alone, not approximated")
    precondition(
      RemoteComposeFontVariation.symbolicTraits(forWeight: 700, existing: .traitItalic)
        == [.traitItalic, .traitBold])
  }

  private static func descriptor(forFontAt path: String) -> CTFontDescriptor {
    guard let data = FileManager.default.contents(atPath: path) else {
      preconditionFailure("missing test font at \(path)")
    }
    return descriptor(for: data)
  }

  private static func descriptor(for data: Data) -> CTFontDescriptor {
    guard let provider = CGDataProvider(data: data as CFData),
      let font = CGFont(provider)
    else {
      preconditionFailure("test font bytes are not a registrable font")
    }
    return CTFontCopyFontDescriptor(CTFontCreateWithGraphicsFont(font, 16, nil, nil))
  }
}

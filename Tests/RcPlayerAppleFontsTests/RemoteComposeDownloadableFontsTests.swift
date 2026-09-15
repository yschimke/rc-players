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
    precondition(
      RemoteComposeGoogleFontsResolver.fontURL(
        from: "@font-face { src: url(https://fonts.gstatic.com/font.woff2) format('woff2'); }",
        relativeTo: base) == nil)
    precondition(
      RemoteComposeGoogleFontsResolver.fontURL(
        from: "@font-face { src: url('/font.otf'); }", relativeTo: base)
        == URL(string: "https://fonts.googleapis.com/font.otf"))
    if ProcessInfo.processInfo.environment["RC_LIVE_GOOGLE_FONTS"] == "1" {
      let font = try await RemoteComposeGoogleFontsResolver().resolve(
        RemoteComposeDownloadableFontRequest(family: "Orbitron"))
      precondition(font.family == "Orbitron")
      precondition(font.data.count > 1_000)
      precondition(font.identity.hasPrefix("google:orbitron#sha256:"))
    }
    print("Apple downloadable-font tests: ok")
  }
}

import Foundation
import Testing

@testable import RcNativePlayerCore

/// The Swift core links no Kotlin, so it cannot read `RcFloatList.MAX_VALUES`; it keeps its own
/// `NativeSwiftDocumentDecoder.maximumFloatListValues`. This test reads the value from the Kotlin
/// source and from the vendored AndroidX TypeScript port (`DataDynamicListFloat.MAX_FLOAT_ARRAY`;
/// the port's `DataListFloat` has no limit, AndroidX's Java `DataListFloat` holds the same 2000 but
/// is not vendored) and fails when either drifts from the Swift constant — as
/// `scripts/check-native-uikit-profile.sh` reads Kotlin sources for the other limits.
@Suite struct NativeSwiftFloatListLimitParityTests {
  private static let repositoryRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()

  /// The integer literal `pattern`'s single capture group matches in the file at `path`.
  private static func integer(in path: String, matching pattern: String) throws -> Int? {
    let source = try String(
      contentsOf: repositoryRoot.appendingPathComponent(path), encoding: .utf8)
    let regex = try NSRegularExpression(pattern: pattern)
    let range = NSRange(source.startIndex..., in: source)
    guard let match = regex.firstMatch(in: source, range: range),
      let literal = Range(match.range(at: 1), in: source)
    else { return nil }
    return Int(source[literal].replacingOccurrences(of: "_", with: ""))
  }

  @Test func theFloatListLimitMatchesTheKotlinPlayer() throws {
    let kotlin = try Self.integer(
      in: "rc-player/protocol/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/protocol"
        + "/RcModel.kt",
      matching: #"const val MAX_VALUES: Int = ([\d_]+)"#)
    #expect(kotlin == NativeSwiftDocumentDecoder.maximumFloatListValues)
  }

  @Test func theFloatListLimitMatchesTheVendoredAndroidXPlayer() throws {
    let androidX = try Self.integer(
      in: "third_party/remote-compose-player/src/core/operations/DataDynamicListFloat.ts",
      matching: #"MAX_FLOAT_ARRAY = ([\d_]+);"#)
    #expect(androidX == NativeSwiftDocumentDecoder.maximumFloatListValues)
  }
}

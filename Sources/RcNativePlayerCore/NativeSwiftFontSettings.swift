import Foundation

/// One entry of a `CoreText` style's font settings: a four-character OpenType tag and its value.
public struct NativeSwiftFontSetting: Hashable, Sendable {
  public let tag: String
  public let value: Float

  public init(tag: String, value: Float) {
    self.tag = tag
    self.value = value
  }
}

/// An OpenType layout feature and the value it is set to: 0 turns it off, 1 on, and a higher value
/// picks an alternate (`salt 2`).
public struct NativeSwiftFontFeature: Hashable, Sendable {
  public let tag: String
  public let value: Int

  public init(tag: String, value: Int) {
    self.tag = tag
    self.value = value
  }
}

/// Splits a `CoreText` style's font settings into variation axes and layout features.
///
/// Remote Compose writes `fontFeatureSettings` into the same tag/value list as
/// `fontVariationSettings`, so the tag is all that tells them apart. The rules are the Compose
/// player's (`RcComposePlayer.kt`, `isFontFeatureTag`):
///
/// - OpenType registers five axes, all lowercase: `wght`, `wdth`, `opsz`, `ital`, `slnt`. Every
///   other axis is foundry-defined and its tag begins with an uppercase letter (`GRAD`). So any
///   other four-character lowercase tag is a feature, never an axis (`tnum`, `liga`, `ss01`).
/// - `ital` is also the Italics feature, and uppercase tags also name private features (`PKRN`), so
///   those are applied as both whenever the value could be a feature value — a whole number from
///   zero. Each side is a no-op on a face that lacks it.
public enum NativeSwiftFontSettings {
  static let registeredAxes: Set<String> = ["wght", "wdth", "opsz", "ital", "slnt"]

  /// Whether `tag` can only be a layout feature.
  public static func isFeatureTag(_ tag: String) -> Bool {
    let scalars = Array(tag.unicodeScalars)
    guard scalars.count == 4, !registeredAxes.contains(tag), isLowercase(scalars[0]) else {
      return false
    }
    return scalars.allSatisfy { isLowercase($0) || isDigit($0) }
  }

  /// Whether `tag` may be either an axis or a feature: `ital`, and the uppercase tags.
  static func isAxisOrFeatureTag(_ tag: String) -> Bool {
    let scalars = Array(tag.unicodeScalars)
    return tag == "ital" || (scalars.count == 4 && uppercase.contains(scalars[0]))
  }

  /// The variation axes among `settings`: everything that is not a feature only.
  public static func axes(_ settings: [NativeSwiftFontSetting]) -> [NativeSwiftFontSetting] {
    settings.filter { !isFeatureTag($0.tag) }
  }

  /// The layout features among `settings`, in document order. A value is rounded to a whole
  /// number and a negative one is off, as the Compose player writes them.
  public static func features(_ settings: [NativeSwiftFontSetting]) -> [NativeSwiftFontFeature] {
    settings.compactMap { setting in
      let isFeature =
        isFeatureTag(setting.tag)
        // A feature value is a whole number from zero: `GRAD -50` can only be the axis.
        || (isAxisOrFeatureTag(setting.tag) && setting.value >= 0
          && setting.value == setting.value.rounded())
      guard isFeature else { return nil }
      // Clamped before the conversion: an out-of-range float would trap in `Int(_:)`.
      return NativeSwiftFontFeature(
        tag: setting.tag, value: Int(min(max(setting.value.rounded(), 0), 65_535)))
    }
  }

  private static let lowercase: ClosedRange<Unicode.Scalar> = "a"..."z"
  private static let uppercase: ClosedRange<Unicode.Scalar> = "A"..."Z"
  private static let digits: ClosedRange<Unicode.Scalar> = "0"..."9"

  private static func isLowercase(_ scalar: Unicode.Scalar) -> Bool { lowercase.contains(scalar) }

  private static func isDigit(_ scalar: Unicode.Scalar) -> Bool { digits.contains(scalar) }
}

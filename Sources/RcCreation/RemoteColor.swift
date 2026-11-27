import RcWireOperations

/// A literal colour: sRGB components and an opacity, each 0 to 1.
///
/// A literal can be adjusted (``opacity(_:)``) because its value is known while the document is
/// written. A colour that is only known as it plays is a ``RemoteColor``.
public struct RemoteColorValue: Hashable, Sendable, RemoteVariableValue {
  public let red: Double
  public let green: Double
  public let blue: Double
  public let opacity: Double

  /// A colour from sRGB components; each is clamped to 0...1, and a NaN reads as 0.
  public init(red: Double, green: Double, blue: Double, opacity: Double = 1) {
    func clamp(_ value: Double) -> Double { value.isNaN ? 0 : Swift.min(Swift.max(value, 0), 1) }
    self.red = clamp(red)
    self.green = clamp(green)
    self.blue = clamp(blue)
    self.opacity = clamp(opacity)
  }

  /// A colour from a `0xRRGGBB` value.
  public static func hex(_ rgb: UInt32, opacity: Double = 1) -> RemoteColorValue {
    func channel(_ shift: UInt32) -> Double { Double((rgb >> shift) & 0xFF) / 255 }
    return RemoteColorValue(red: channel(16), green: channel(8), blue: channel(0), opacity: opacity)
  }

  /// This colour at another opacity.
  public func opacity(_ opacity: Double) -> RemoteColorValue {
    RemoteColorValue(red: red, green: green, blue: blue, opacity: opacity)
  }

  /// The packed `0xAARRGGBB` word the wire carries.
  var argb: Int32 {
    // Components are clamped when the value is made.
    func channel(_ value: Double, _ shift: UInt32) -> UInt32 {
      UInt32((value * 255).rounded()) << shift
    }
    return Int32(
      bitPattern: channel(opacity, 24) | channel(red, 16) | channel(green, 8) | channel(blue, 0))
  }

  public static let black = RemoteColorValue(red: 0, green: 0, blue: 0)
  public static let white = RemoteColorValue(red: 1, green: 1, blue: 1)
  public static let clear = RemoteColorValue(red: 0, green: 0, blue: 0, opacity: 0)
  public static let gray = RemoteColorValue.hex(0x8E8E93)
  public static let red = RemoteColorValue.hex(0xFF3B30)
  public static let orange = RemoteColorValue.hex(0xFF9500)
  public static let yellow = RemoteColorValue.hex(0xFFCC00)
  public static let green = RemoteColorValue.hex(0x34C759)
  public static let blue = RemoteColorValue.hex(0x007AFF)
  public static let purple = RemoteColorValue.hex(0xAF52DE)
}

/// A colour a view draws with: a literal, a light/dark pair the player picks between, or a
/// ``RemoteVariable`` the host writes.
///
/// Used as a view, a colour fills the space it is offered, as a SwiftUI `Color` does:
///
/// ```swift
/// RemoteZStack(alignment: .leading) {
///   RemoteColor.adaptive(light: .hex(0xDADCE0), dark: .hex(0x3A3A3C))
///   RemoteColor.hex(0x3D5AFE).frame(width: progress * 328)
/// }
/// .frame(maxWidth: .infinity, height: 8)
/// ```
public struct RemoteColor: Hashable, Sendable {
  enum Storage: Hashable, Sendable {
    case fixed(RemoteColorValue)
    case adaptive(light: RemoteColorValue, dark: RemoteColorValue)
    case variable(RemoteVariable<RemoteColorValue>)
  }

  let storage: Storage

  public init(_ value: RemoteColorValue) { storage = .fixed(value) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<RemoteColorValue>) { storage = .variable(variable) }

  /// A literal colour.
  public static func fixed(_ value: RemoteColorValue) -> RemoteColor { RemoteColor(value) }

  /// `light` in a light appearance and `dark` in a dark one, written as a `ColorTheme` the player
  /// resolves against the host's appearance.
  public static func adaptive(light: RemoteColorValue, dark: RemoteColorValue) -> RemoteColor {
    RemoteColor(storage: .adaptive(light: light, dark: dark))
  }

  /// The host's value for `variable`.
  public static func variable(_ variable: RemoteVariable<RemoteColorValue>) -> RemoteColor {
    RemoteColor(variable)
  }

  /// A literal colour from a `0xRRGGBB` value.
  public static func hex(_ rgb: UInt32, opacity: Double = 1) -> RemoteColor {
    RemoteColor(.hex(rgb, opacity: opacity))
  }

  init(storage: Storage) { self.storage = storage }

  public static let black = RemoteColor(.black)
  public static let white = RemoteColor(.white)
  public static let clear = RemoteColor(.clear)
  public static let gray = RemoteColor(.gray)
  public static let red = RemoteColor(.red)
  public static let orange = RemoteColor(.orange)
  public static let yellow = RemoteColor(.yellow)
  public static let green = RemoteColor(.green)
  public static let blue = RemoteColor(.blue)
  public static let purple = RemoteColor(.purple)

  /// Text and symbols: black in a light appearance, white in a dark one (`UIColor.label`).
  public static let primary = adaptive(light: .black, dark: .white)

  /// Secondary text (`UIColor.secondaryLabel`).
  public static let secondary = adaptive(
    light: .hex(0x3C3C43, opacity: 0.6), dark: .hex(0xEBEBF5, opacity: 0.6))
}

extension RemoteColor: _PrimitiveRemoteView {
  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try writer.writeFillingBox(background: self)
  }
}

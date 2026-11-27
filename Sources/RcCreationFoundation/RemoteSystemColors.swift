import RcCreation

/// Apple's system colours (`UIColor`'s semantic and system colours), each a light/dark pair the
/// player picks between by appearance. Values are the iOS defaults Apple documents in the Human
/// Interface Guidelines' Color page.
extension RemoteColor {
  // MARK: Labels

  /// Primary text: `UIColor.label`.
  public static let label = adaptive(light: .black, dark: .white)
  /// Secondary text: `UIColor.secondaryLabel`.
  public static let secondaryLabel = adaptive(
    light: .hex(0x3C3C43, opacity: 0.6), dark: .hex(0xEBEBF5, opacity: 0.6))
  /// Tertiary text: `UIColor.tertiaryLabel`.
  public static let tertiaryLabel = adaptive(
    light: .hex(0x3C3C43, opacity: 0.3), dark: .hex(0xEBEBF5, opacity: 0.3))
  /// Disabled and placeholder text: `UIColor.quaternaryLabel`.
  public static let quaternaryLabel = adaptive(
    light: .hex(0x3C3C43, opacity: 0.18), dark: .hex(0xEBEBF5, opacity: 0.16))

  // MARK: Separators and fills

  /// Hairlines between content: `UIColor.separator`.
  public static let separator = adaptive(
    light: .hex(0x3C3C43, opacity: 0.29), dark: .hex(0x545458, opacity: 0.6))
  /// Thin and small shapes, such as a switch's track: `UIColor.systemFill`.
  public static let systemFill = adaptive(
    light: .hex(0x787880, opacity: 0.2), dark: .hex(0x787880, opacity: 0.36))
  /// Medium shapes: `UIColor.secondarySystemFill`.
  public static let secondarySystemFill = adaptive(
    light: .hex(0x787880, opacity: 0.16), dark: .hex(0x787880, opacity: 0.32))
  /// Large shapes, such as a text field or a segmented control: `UIColor.tertiarySystemFill`.
  public static let tertiarySystemFill = adaptive(
    light: .hex(0x767680, opacity: 0.12), dark: .hex(0x767680, opacity: 0.24))

  // MARK: Backgrounds

  public static let systemBackground = adaptive(light: .white, dark: .black)
  public static let secondarySystemBackground = adaptive(
    light: .hex(0xF2F2F7), dark: .hex(0x1C1C1E))
  public static let tertiarySystemBackground = adaptive(light: .white, dark: .hex(0x2C2C2E))
  /// Behind grouped content, such as a settings list: `UIColor.systemGroupedBackground`.
  public static let systemGroupedBackground = adaptive(light: .hex(0xF2F2F7), dark: .black)
  /// A grouped section's rows.
  public static let secondarySystemGroupedBackground = adaptive(
    light: .white, dark: .hex(0x1C1C1E))

  // MARK: System colours

  public static let systemBlue = adaptive(light: .hex(0x007AFF), dark: .hex(0x0A84FF))
  public static let systemGreen = adaptive(light: .hex(0x34C759), dark: .hex(0x30D158))
  public static let systemRed = adaptive(light: .hex(0xFF3B30), dark: .hex(0xFF453A))
  public static let systemOrange = adaptive(light: .hex(0xFF9500), dark: .hex(0xFF9F0A))
  public static let systemYellow = adaptive(light: .hex(0xFFCC00), dark: .hex(0xFFD60A))
  public static let systemPink = adaptive(light: .hex(0xFF2D55), dark: .hex(0xFF375F))
  public static let systemPurple = adaptive(light: .hex(0xAF52DE), dark: .hex(0xBF5AF2))
  public static let systemIndigo = adaptive(light: .hex(0x5856D6), dark: .hex(0x5E5CE6))
  public static let systemTeal = adaptive(light: .hex(0x30B0C7), dark: .hex(0x40CBE0))
  public static let systemMint = adaptive(light: .hex(0x00C7BE), dark: .hex(0x63E6E2))
  public static let systemCyan = adaptive(light: .hex(0x32ADE6), dark: .hex(0x64D2FF))
  public static let systemBrown = adaptive(light: .hex(0xA2845E), dark: .hex(0xAC8E68))
  public static let systemGray = adaptive(light: .hex(0x8E8E93), dark: .hex(0x8E8E93))
  public static let systemGray5 = adaptive(light: .hex(0xE5E5EA), dark: .hex(0x2C2C2E))
}

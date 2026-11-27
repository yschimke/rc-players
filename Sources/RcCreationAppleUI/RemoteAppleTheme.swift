import RcCreation
import RcCreationFoundation

/// The colours Apple's controls draw with, as `RemoteMaterialTheme` carries Material's. The
/// default is Apple's system palette; a theme can replace any role.
///
/// ```swift
/// SettingsScreen()
///   .appleTheme(RemoteAppleTheme(toggleOn: .systemBlue))
///   .tint(.systemIndigo)
/// ```
///
/// The accent colour is the environment's `tint`, as in SwiftUI; the theme holds every other role.
public struct RemoteAppleTheme: Sendable {
  public var label: RemoteColor
  public var secondaryLabel: RemoteColor
  public var tertiaryLabel: RemoteColor
  public var separator: RemoteColor
  /// A switch's off track and a slider's track.
  public var fill: RemoteColor
  /// A segmented control's and a stepper's background.
  public var tertiaryFill: RemoteColor
  /// Behind a list's sections.
  public var groupedBackground: RemoteColor
  /// A list section's rows.
  public var groupedRowBackground: RemoteColor
  /// A widget's container background.
  public var widgetBackground: RemoteColor
  /// A switch's on track: green, as iOS draws it, unless changed.
  public var toggleOn: RemoteColor
  /// A switch's knob.
  public var thumb: RemoteColor
  /// The selected segment of a segmented control.
  public var selectedSegment: RemoteColor
  /// Destructive buttons and badges.
  public var destructive: RemoteColor
  /// Text on an accent-coloured background.
  public var onAccent: RemoteColor

  public init(
    label: RemoteColor = .label,
    secondaryLabel: RemoteColor = .secondaryLabel,
    tertiaryLabel: RemoteColor = .tertiaryLabel,
    separator: RemoteColor = .separator,
    fill: RemoteColor = .systemFill,
    tertiaryFill: RemoteColor = .tertiarySystemFill,
    groupedBackground: RemoteColor = .systemGroupedBackground,
    groupedRowBackground: RemoteColor = .secondarySystemGroupedBackground,
    widgetBackground: RemoteColor = .systemBackground,
    toggleOn: RemoteColor = .systemGreen,
    thumb: RemoteColor = .white,
    selectedSegment: RemoteColor = .adaptive(light: .white, dark: .hex(0x636366)),
    destructive: RemoteColor = .systemRed,
    onAccent: RemoteColor = .white
  ) {
    self.label = label
    self.secondaryLabel = secondaryLabel
    self.tertiaryLabel = tertiaryLabel
    self.separator = separator
    self.fill = fill
    self.tertiaryFill = tertiaryFill
    self.groupedBackground = groupedBackground
    self.groupedRowBackground = groupedRowBackground
    self.widgetBackground = widgetBackground
    self.toggleOn = toggleOn
    self.thumb = thumb
    self.selectedSegment = selectedSegment
    self.destructive = destructive
    self.onAccent = onAccent
  }

  /// Apple's system palette.
  public static let system = RemoteAppleTheme()

  /// This theme as a disabled control draws: labels and accents in the tertiary label colour,
  /// tracks in the tertiary fill, as iOS dims a control it disables.
  var disabled: RemoteAppleTheme {
    var theme = self
    theme.label = tertiaryLabel
    theme.secondaryLabel = tertiaryLabel
    theme.toggleOn = tertiaryFill
    theme.destructive = tertiaryLabel
    return theme
  }

  /// White on a clear background at decreasing opacities: how WidgetKit draws an accessory widget
  /// (its vibrant rendering mode) on the Lock Screen and in complications.
  public static let vibrant = RemoteAppleTheme(
    label: .white, secondaryLabel: .fixed(.white.opacity(0.7)),
    tertiaryLabel: .fixed(.white.opacity(0.45)), separator: .fixed(.white.opacity(0.3)),
    fill: .fixed(.white.opacity(0.25)), tertiaryFill: .fixed(.white.opacity(0.15)),
    groupedBackground: .clear, groupedRowBackground: .fixed(.white.opacity(0.15)),
    widgetBackground: .clear, toggleOn: .white, thumb: .black,
    selectedSegment: .fixed(.white.opacity(0.4)), destructive: .white, onAccent: .black)
}

private struct AppleThemeKey: RemoteEnvironmentKey {
  static let defaultValue = RemoteAppleTheme.system
}

extension RemoteEnvironmentValues {
  /// The theme Apple controls below a ``RemoteView/appleTheme(_:)`` draw with.
  public var appleTheme: RemoteAppleTheme {
    get { self[AppleThemeKey.self] }
    set { self[AppleThemeKey.self] = newValue }
  }
}

extension RemoteView {
  /// Draws the Apple controls inside this view with `theme`.
  public func appleTheme(_ theme: RemoteAppleTheme) -> RemoteEnvironmentContent<Self> {
    environment(\.appleTheme, theme)
  }
}

/// A view built from the theme and tint it is written under: what every control's body is.
struct ThemedView<Content: RemoteView>: RemoteView {
  let content: @Sendable (RemoteAppleTheme, RemoteColor) -> Content

  init(@RemoteViewBuilder _ content: @escaping @Sendable (RemoteAppleTheme, RemoteColor) -> Content)
  {
    self.content = content
  }

  /// Read under `.disabled()`, the theme and tint are dimmed, so every control draws itself
  /// disabled without its own code for it.
  var body: some RemoteView {
    RemoteEnvironmentReader { environment in
      environment.isEnabled
        ? content(environment.appleTheme, environment.tint)
        : content(environment.appleTheme.disabled, environment.appleTheme.tertiaryLabel)
    }
  }
}

extension RemoteView {
  /// Runs `actions` when tapped, unless the view is inside a disabled one: how every Apple control
  /// takes taps.
  func onTapGestureIfEnabled(_ actions: RemoteAction...) -> some RemoteView {
    let view = self
    return RemoteEnvironmentReader { environment in
      if environment.isEnabled { view.onTapGesture(actions) } else { view }
    }
  }
}

import RcCreation
import RcCreationFoundation

// The HIG's button styles, for RcCreation's `RemoteButton`, as SwiftUI's built-in ones:
//
//   RemoteButton("Play", symbol: .playFill, action: .host("play"))
//     .buttonStyle(.borderedProminent)
//     .controlSize(.large)
//
// Each reads the theme, the tint and the control size from the environment.

/// The accent-coloured label alone: SwiftUI's default for a standalone button.
public struct RemoteBorderlessButtonStyle: RemoteButtonStyle {
  public init() {}

  public func makeBody(configuration: Configuration) -> some RemoteView {
    HIGButton(configuration: configuration, kind: .borderless)
  }
}

/// The accent-coloured label on a tinted rounded rectangle.
public struct RemoteBorderedButtonStyle: RemoteButtonStyle {
  public init() {}

  public func makeBody(configuration: Configuration) -> some RemoteView {
    HIGButton(configuration: configuration, kind: .bordered)
  }
}

/// White on the accent colour: the screen's primary action.
public struct RemoteBorderedProminentButtonStyle: RemoteButtonStyle {
  public init() {}

  public func makeBody(configuration: Configuration) -> some RemoteView {
    HIGButton(configuration: configuration, kind: .borderedProminent)
  }
}

extension RemoteButtonStyle where Self == RemoteBorderlessButtonStyle {
  public static var borderless: Self { Self() }
}

extension RemoteButtonStyle where Self == RemoteBorderedButtonStyle {
  public static var bordered: Self { Self() }
}

extension RemoteButtonStyle where Self == RemoteBorderedProminentButtonStyle {
  public static var borderedProminent: Self { Self() }
}

/// The HIG metrics of each control size.
extension RemoteControlSize {
  var font: RemoteFont {
    switch self {
    case .mini: .caption
    case .small: .subheadline
    case .regular, .large: .body
    }
  }

  var padding: (horizontal: Double, vertical: Double) {
    switch self {
    case .mini: (8, 3)
    case .small: (10, 5)
    case .regular: (14, 7)
    case .large: (20, 14)
    }
  }

  var cornerRadius: Double {
    switch self {
    case .mini: 6
    case .small: 8
    case .regular: 10
    case .large: 12
    }
  }
}

/// A button's label drawn as the HIG draws one of its styles.
struct HIGButton: RemoteView {
  enum Kind { case borderless, bordered, borderedProminent }

  let configuration: RemoteButtonStyleConfiguration
  let kind: Kind

  var body: some RemoteView {
    let button = self
    return RemoteEnvironmentReader { environment in
      let size = environment.controlSize
      let colors = button.colors(
        theme: environment.appleTheme,
        accent: button.configuration.role == .destructive
          ? environment.appleTheme.destructive : environment.tint)
      // A borderless button draws no shape, so its label sits flush, as SwiftUI's does.
      let inset = button.kind != .borderless
      button.configuration.label
        .font(size.font)
        .fontWeight(button.kind == .borderedProminent || size == .large ? .semibold : nil)
        .foregroundStyle(colors.foreground)
        .padding(.horizontal, RemoteFloat(inset ? size.padding.horizontal : 0))
        .padding(.vertical, RemoteFloat(size.padding.vertical))
        .background(colors.background, in: .roundedRectangle(cornerRadius: size.cornerRadius))
    }
  }

  private func colors(theme: RemoteAppleTheme, accent: RemoteColor) -> (
    foreground: RemoteColor, background: RemoteColor
  ) {
    if !configuration.isEnabled {
      return (theme.tertiaryLabel, kind == .borderless ? .clear : theme.tertiaryFill)
    }
    switch kind {
    case .borderless: return (accent, .clear)
    // A tinted fill: the accent at 15%, or the theme's fill for an accent only known as the
    // document plays.
    case .bordered: return (accent, accent.opacity(0.15) ?? theme.tertiaryFill)
    case .borderedProminent: return (theme.onAccent, accent)
    }
  }
}

/// An on/off switch with its title, as SwiftUI's `Toggle`: tapping the row flips `isOn`, in the
/// document, without a round trip to the host.
///
/// ```swift
/// let wifi = RemoteVariable("wifi", default: true)
/// RemoteToggle("Wi-Fi", isOn: wifi)
/// ```
public struct RemoteToggle: RemoteView {
  let title: RemoteString
  let isOn: RemoteVariable<Bool>

  public init(_ title: RemoteString, isOn: RemoteVariable<Bool>) {
    self.title = title
    self.isOn = isOn
  }

  /// The switch's size: iOS's 51 by 31 points, with a 27-point knob.
  static let trackWidth: RemoteFloat = 51
  static let trackHeight: RemoteFloat = 31
  static let knobInset: RemoteFloat = 2

  public var body: some RemoteView {
    let title = title
    let isOn = isOn
    return ThemedView { theme, _ in
      RemoteHStack(alignment: .center, spacing: 12) {
        RemoteText(title).foregroundStyle(theme.label).flexWeight(1)
        // The two states, each drawn whole and shown while it holds: the knob's position and the
        // track's colour change together, with no expression to animate between them.
        RemoteZStack {
          Self.track(theme.toggleOn, knob: theme.thumb, alignment: .trailing)
            .visible(if: RemoteBool(isOn))
          Self.track(theme.fill, knob: theme.thumb, alignment: .leading)
            .visible(if: !isOn)
        }
        .frame(width: Self.trackWidth, height: Self.trackHeight)
      }
      .onTapGesture(.toggle(isOn))
      .accessibilityElement(children: .ignore)
      .accessibilityLabel(title)
      .accessibilityRole(.toggle)
    }
  }

  private static func track(
    _ color: RemoteColor, knob: RemoteColor, alignment: RemoteAlignment
  ) -> some RemoteView {
    let knobSize = trackHeight - 2 * knobInset
    return RemoteZStack(alignment: alignment) {
      color
      knob
        .frame(width: knobSize, height: knobSize)
        .clipShape(.roundedRectangle(cornerRadius: 13.5))
        .padding(knobInset)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .clipShape(.roundedRectangle(cornerRadius: 15.5))
  }
}

/// A title with minus and plus buttons that change `value` by `step` within `range`, as
/// SwiftUI's `Stepper`. Show the value in the title with interpolation:
///
/// ```swift
/// let guests = RemoteVariable("guests", default: 2.0)
/// RemoteStepper("Guests: \(guests)", value: guests, in: 1...8)
/// ```
public struct RemoteStepper: RemoteView {
  let title: RemoteString
  let value: RemoteVariable<Double>
  let range: ClosedRange<Double>
  let step: Double

  public init(
    _ title: RemoteString, value: RemoteVariable<Double>, in range: ClosedRange<Double>,
    step: Double = 1
  ) {
    self.title = title
    self.value = value
    self.range = range
    self.step = step
  }

  public var body: some RemoteView {
    let stepper = self
    return ThemedView { theme, _ in
      RemoteHStack(alignment: .center, spacing: 12) {
        RemoteText(stepper.title).foregroundStyle(theme.label).flexWeight(1)
        RemoteHStack(alignment: .center, spacing: 0) {
          stepper.half(.minus, change: -stepper.step, theme: theme, label: "Decrement")
          RemoteDivider(.vertical, color: theme.separator).padding(.vertical, 8)
          stepper.half(.plus, change: stepper.step, theme: theme, label: "Increment")
        }
        .frame(width: 94, height: 32)
        .background(theme.tertiaryFill, in: .roundedRectangle(cornerRadius: 8))
      }
    }
  }

  private func half(
    _ symbol: RemoteSymbol, change: Double, theme: RemoteAppleTheme, label: RemoteString
  ) -> some RemoteStackContent {
    RemoteZStack { RemoteIcon(symbol, size: 16).foregroundStyle(theme.label) }
      .frame(maxHeight: .infinity)
      .onTapGesture(.increment(value, by: change, in: range))
      .accessibilityLabel(label)
      .accessibilityRole(.button)
      .flexWeight(1)
  }
}

/// Mutually exclusive options in a row, as SwiftUI's `Picker` with the segmented style. Tapping
/// a segment writes its index to `selection`.
///
/// ```swift
/// let unit = RemoteVariable("unit", default: 0)
/// RemoteSegmentedPicker(selection: unit, options: ["Day", "Week", "Month"])
/// ```
public struct RemoteSegmentedPicker: RemoteView {
  let selection: RemoteVariable<Int>
  let options: [String]

  public init(selection: RemoteVariable<Int>, options: [String]) {
    self.selection = selection
    self.options = options
  }

  public var body: some RemoteView {
    let selection = selection
    let options = options
    return ThemedView { theme, _ in
      RemoteHStack(alignment: .center, spacing: 0) {
        RemoteForEach(options.enumerated()) { index, option in
          RemoteZStack {
            theme.selectedSegment
              .clipShape(.roundedRectangle(cornerRadius: 7))
              .visible(if: selection == index)
            RemoteText(option).font(.footnote).fontWeight(.medium).foregroundStyle(theme.label)
          }
          .frame(maxHeight: .infinity)
          .onTapGesture(.set(selection, to: index))
          .accessibilityLabel(RemoteString(option))
          .accessibilityRole(.tab)
          .flexWeight(1)
        }
      }
      .padding(2)
      .frame(maxWidth: .infinity, height: 32)
      .background(theme.tertiaryFill, in: .roundedRectangle(cornerRadius: 9))
    }
  }
}

/// A small count or status on a capsule, as a tab or list badge.
public struct RemoteBadge: RemoteView {
  let text: RemoteString
  var color: RemoteColor?

  public init(_ text: RemoteString) { self.text = text }

  /// A count, as a list row's badge shows it.
  public init(count: Int) { self.init(RemoteString(String(count))) }

  /// The capsule's colour: the theme's destructive red unless given.
  public func background(_ color: RemoteColor) -> RemoteBadge {
    var copy = self
    copy.color = color
    return copy
  }

  public var body: some RemoteView {
    let badge = self
    return ThemedView { theme, _ in
      RemoteText(badge.text)
        .font(.caption2)
        .fontWeight(.semibold)
        .foregroundStyle(theme.onAccent)
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(badge.color ?? theme.destructive, in: .roundedRectangle(cornerRadius: 9))
    }
  }
}

import RcCreation

/// A ``RemoteSymbol`` drawn at a size, in one colour, as SwiftUI's `Image(systemName:)`.
///
/// ```swift
/// RemoteIcon(.heartFill).foregroundStyle(.systemPink)
/// ```
///
/// An icon is decorative to assistive technologies unless given an `accessibilityLabel`.
public struct RemoteIcon: RemoteView {
  let symbol: RemoteSymbol
  var size: Double
  /// The icon's own colour, or `nil` for the environment's foreground style.
  var color: RemoteColor?

  /// - Parameters:
  ///   - symbol: What to draw.
  ///   - size: The side of the square it is drawn in, in points. 20 points, SF Symbols' size
  ///     beside body text, unless given.
  public init(_ symbol: RemoteSymbol, size: Double = 20) {
    self.symbol = symbol
    self.size = size
    color = nil
  }

  public func foregroundStyle(_ color: RemoteColor) -> RemoteIcon {
    var copy = self
    copy.color = color
    return copy
  }

  /// The side of the square the icon is drawn in, in points.
  public func iconSize(_ size: Double) -> RemoteIcon {
    var copy = self
    copy.size = size
    return copy
  }

  public var body: some RemoteView {
    let icon = self
    return RemoteEnvironmentReader { environment in
      icon.canvas(color: icon.color ?? environment.foregroundStyle)
    }
  }

  private func canvas(color: RemoteColor) -> some RemoteView {
    let scale = size / symbol.gridSize
    let parts = symbol.parts
    return RemoteCanvas { context, _ in
      for part in parts {
        let path = part.path.applying(scale: scale)
        switch part.style {
        case .fill:
          context.fill(.path(path), with: color)
        case .stroke(let lineWidth):
          context.stroke(
            .path(path), with: color, lineWidth: lineWidth * scale, lineCap: .round,
            lineJoin: .round)
        }
      }
    }
    .frame(width: RemoteFloat(size), height: RemoteFloat(size))
  }
}

/// A hairline between content, as SwiftUI's `Divider`: across a vertical stack, or down a
/// horizontal one with ``RemoteDivider/Axis/vertical``.
public struct RemoteDivider: RemoteView {
  /// The direction the line runs.
  public enum Axis: Hashable, Sendable {
    case horizontal, vertical
  }

  let axis: Axis
  let color: RemoteColor

  /// The line's thickness: one pixel at Apple's 2× and 3× scales rounds to this.
  static let thickness: RemoteFloat = 0.5

  public init(_ axis: Axis = .horizontal, color: RemoteColor = .separator) {
    self.axis = axis
    self.color = color
  }

  public var body: some RemoteView {
    switch axis {
    case .horizontal: color.frame(maxWidth: .infinity, height: Self.thickness)
    case .vertical: color.frame(width: Self.thickness, maxHeight: .infinity)
    }
  }
}

/// A symbol beside a title, as SwiftUI's `Label`. Its font and colour come from the environment,
/// as a text's do.
///
/// ```swift
/// RemoteLabel("Favourites", symbol: .starFill).foregroundStyle(.systemYellow)
/// RemoteLabel("Alerts", symbol: .bellFill).iconStyle(.systemRed).font(.headline)
/// ```
public struct RemoteLabel: RemoteView {
  let title: RemoteString
  let symbol: RemoteSymbol

  public init(_ title: RemoteString, symbol: RemoteSymbol) {
    self.title = title
    self.symbol = symbol
  }

  @_disfavoredOverload
  public init(_ title: some StringProtocol, symbol: RemoteSymbol) {
    self.init(RemoteString(String(title)), symbol: symbol)
  }

  public var body: some RemoteView {
    let label = self
    return RemoteEnvironmentReader { environment in
      RemoteHStack(alignment: .center, spacing: 8) {
        // SF Symbols are drawn a little larger than their text's point size.
        RemoteIcon(label.symbol, size: environment.font.pointSize * 1.2)
          .foregroundStyle(environment.iconStyle ?? environment.foregroundStyle)
        RemoteText(label.title)
      }
    }
  }
}

private struct IconStyleKey: RemoteEnvironmentKey {
  static var defaultValue: RemoteColor? { nil }
}

extension RemoteEnvironmentValues {
  /// The colour of the labels' symbols inside this view, where it differs from their titles';
  /// `nil` draws them in the foreground style.
  public var iconStyle: RemoteColor? {
    get { self[IconStyleKey.self] }
    set { self[IconStyleKey.self] = newValue }
  }
}

extension RemoteView {
  /// Colours the symbols of the labels inside this view, as a tinted list icon is; their titles
  /// keep the foreground style.
  public func iconStyle(_ color: RemoteColor) -> RemoteEnvironmentContent<Self> {
    environment(\.iconStyle, color)
  }
}

extension RemoteButton where Label == RemoteLabel {
  /// A button labelled with a symbol and `title`, as SwiftUI's `Button(_:systemImage:action:)`.
  public init(
    _ title: RemoteString, symbol: RemoteSymbol, role: RemoteButtonRole? = nil,
    action: RemoteAction...
  ) {
    self.init(role: role, actions: action) { RemoteLabel(title, symbol: symbol) }
  }

  @_disfavoredOverload
  public init(
    _ title: some StringProtocol, symbol: RemoteSymbol, role: RemoteButtonRole? = nil,
    action: RemoteAction...
  ) {
    self.init(role: role, actions: action) { RemoteLabel(title, symbol: symbol) }
  }
}

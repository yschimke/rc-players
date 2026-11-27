// The 4.0 API that 4.1 replaced, kept so code written against 4.0 still compiles, with a warning
// naming the replacement. Each forwards to the new API and writes the same document.

/// 4.0's name for a text style, now a ``RemoteFont``.
@available(*, deprecated, renamed: "RemoteFont")
public typealias RemoteTextStyle = RemoteFont

extension RemoteFont {
  /// A text style of `size` points, as 4.0's `RemoteTextStyle(size:weight:)`.
  @available(*, deprecated, renamed: "RemoteFont.textStyle(size:weight:)")
  public init(size: Double, weight: RemoteFontWeight = .regular) {
    self.init(size: .style(size), weight: weight)
  }

  /// The size in points at the default text size.
  @available(*, deprecated, renamed: "pointSize")
  public var size: Double { pointSize }

  /// The weight the font sets, regular when it sets none.
  @available(*, deprecated, message: "Read the weight from the environment's fontWeight")
  public var weight: RemoteFontWeight { fontWeight ?? .regular }
}

extension RemoteView {
  /// A fixed size in points, as 4.0's `RemoteText.font(size:weight:)`.
  @available(*, deprecated, message: "Use font(.system(size:weight:))")
  public func font(size: Double, weight: RemoteFontWeight = .regular) -> RemoteEnvironmentContent<
    Self
  > {
    font(.system(size: size, weight: weight))
  }
}

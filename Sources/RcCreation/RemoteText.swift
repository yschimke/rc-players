import RcWireOperations

/// A font weight, 1 to 1000; outside that, encoding fails with
/// ``RemoteEncodingError/invalidFontWeight(_:)``.
public struct RemoteFontWeight: Hashable, Sendable {
  public let value: Double

  public init(_ value: Double) { self.value = value }

  public static let ultraLight = RemoteFontWeight(100)
  public static let thin = RemoteFontWeight(200)
  public static let light = RemoteFontWeight(300)
  public static let regular = RemoteFontWeight(400)
  public static let medium = RemoteFontWeight(500)
  public static let semibold = RemoteFontWeight(600)
  public static let bold = RemoteFontWeight(700)
  public static let heavy = RemoteFontWeight(800)
  public static let black = RemoteFontWeight(900)
}

/// A font, as SwiftUI's `Font`: a text style that scales with the reader's text size, or a fixed
/// size, with an optional weight and italic.
///
/// ```swift
/// RemoteText("Title").font(.headline)
/// RemoteText("Price").font(.system(size: 28, weight: .bold))
/// RemoteVStack { … }.font(.footnote.weight(.medium))
/// ```
public struct RemoteFont: Hashable, Sendable {
  enum Size: Hashable, Sendable {
    /// A text style's size at the default text size, scaled with the reader's setting.
    case style(Double)
    /// A fixed size, in points.
    case fixed(Double)
  }

  let size: Size
  /// The font's own weight; a `fontWeight` modifier overrides it.
  let weight: RemoteFontWeight?
  let isItalic: Bool

  init(size: Size, weight: RemoteFontWeight? = nil, isItalic: Bool = false) {
    self.size = size
    self.weight = weight
    self.isItalic = isItalic
  }

  /// A fixed size in points, which does not scale with the reader's text size, as
  /// `Font.system(size:weight:)`.
  public static func system(size: Double, weight: RemoteFontWeight? = nil) -> RemoteFont {
    RemoteFont(size: .fixed(size), weight: weight)
  }

  /// A text style of `size` points at the default text size, scaling with the reader's setting.
  public static func textStyle(size: Double, weight: RemoteFontWeight? = nil) -> RemoteFont {
    RemoteFont(size: .style(size), weight: weight)
  }

  // Apple's text styles at the default (Large) Dynamic Type size.
  public static let largeTitle = textStyle(size: 34)
  public static let title = textStyle(size: 28)
  public static let title2 = textStyle(size: 22)
  public static let title3 = textStyle(size: 20)
  public static let headline = textStyle(size: 17, weight: .semibold)
  public static let body = textStyle(size: 17)
  public static let callout = textStyle(size: 16)
  public static let subheadline = textStyle(size: 15)
  public static let footnote = textStyle(size: 13)
  public static let caption = textStyle(size: 12)
  public static let caption2 = textStyle(size: 11)

  /// This font at `weight`.
  public func weight(_ weight: RemoteFontWeight) -> RemoteFont {
    RemoteFont(size: size, weight: weight, isItalic: isItalic)
  }

  public func bold() -> RemoteFont { weight(.bold) }

  public func italic() -> RemoteFont { RemoteFont(size: size, weight: weight, isItalic: true) }

  /// The size in points at the default text size.
  public var pointSize: Double {
    switch size {
    case .style(let points), .fixed(let points): points
    }
  }

  /// The font size, in sp, that players publish as `RemoteContextIds.idFontSize` at a text scale
  /// of 1, before density: AndroidX's `RemoteContext` default, which every player loads. A text
  /// style's size is scaled by the player's font size over this.
  static let playerBaseFontSize: Float = 14
}

/// How lines of text align with each other.
public enum RemoteTextAlignment: Hashable, Sendable {
  case leading, center, trailing

  var wire: Int32 {
    switch self {
    case .leading: TextLayout.start
    case .center: TextLayout.center
    case .trailing: TextLayout.end
    }
  }
}

/// Where text that does not fit its lines is cut, with an ellipsis.
public enum RemoteTruncationMode: Hashable, Sendable {
  case head, middle, tail

  var wire: Int32 {
    switch self {
    case .head: TextLayout.overflowStartEllipsis
    case .middle: TextLayout.overflowMiddleEllipsis
    case .tail: TextLayout.overflowEllipsis
    }
  }
}

/// Text, as SwiftUI's `Text`. How it is drawn comes from its environment, so `font`,
/// `fontWeight`, `italic`, `foregroundStyle`, `multilineTextAlignment`, `lineLimit` and
/// `truncationMode` apply to it wherever they are set: on the text, after a layout modifier, or
/// on a container or a custom view around it.
///
/// ```swift
/// RemoteVStack(alignment: .leading) {
///   RemoteText("Upload \(progress * 100, decimals: 0)%")
///   RemoteText("of 3 files").foregroundStyle(.secondary)
/// }
/// .font(.footnote)
/// ```
///
/// Without one it is the ``RemoteFont/body`` style in the ``RemoteColor/primary`` colour, so it
/// follows the reader's text size and appearance.
public struct RemoteText: RemoteView, RemotePrimitive {
  public typealias Body = Never

  let text: RemoteString

  public init(_ text: RemoteString) { self.text = text }

  /// A Swift string, as a constant.
  @_disfavoredOverload
  public init(_ content: some StringProtocol) { text = RemoteString(String(content)) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<String>) { text = RemoteString(variable) }

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    let environment = writer.environment
    let font = environment.font
    let weight = environment.fontWeight ?? font.weight ?? .regular
    guard (1...1000).contains(weight.value) else {
      throw RemoteEncodingError.invalidFontWeight(weight.value)
    }
    let componentID = writer.newComponentID()
    let textID = try writer.stringID(text)
    let resolvedColor = try writer.color(environment.foregroundStyle)
    let fontSize: Float
    switch font.size {
    case .fixed(let points):
      fontSize = try writer.pixels(points)
    case .style(let points):
      // The style's size, scaled by the player's font scale: `idFontSize` is the base size in
      // pixels at the player's own density and text scale, so over the base size and that density
      // it is the text scale alone, and the document's display scale still gives the pixels.
      let scale =
        RemoteFloat(node: .system(RemoteContextIds.idFontSize))
        / (RemoteFloat(node: .system(RemoteContextIds.idDensity))
          * RemoteFloat(RemoteFont.playerBaseFontSize))
      fontSize = try writer.floatWord(writer.pixels(RemoteFloat(points)) * scale)
    }
    var textAlign = environment.multilineTextAlignment.wire
    let colorWord: Int32
    switch resolvedColor {
    case .literal(let argb): colorWord = argb
    case .id(let id):
      colorWord = id
      textAlign |=
        Int32(TextLayout.BitLayout.textAlignIsDynamicColorMask)
        << Int32(TextLayout.BitLayout.textAlignIsDynamicColorShift)
    }
    let isItalic = environment.isItalic || font.isItalic
    let lineLimit = try environment.lineLimit.map { limit throws(RemoteEncodingError) in
      try RemoteWriter.int32(Swift.max(limit, 1))
    }
    let layout = TextLayout(
      componentId: componentID, animationId: IdEncoding.noId, textId: textID, color: colorWord,
      fontSize: fontSize, fontStyle: isItalic ? TextLayout.italic : TextLayout.normal,
      fontWeight: Float(weight.value), fontFamilyId: IdEncoding.noId, textAlign: textAlign,
      overflow: environment.truncationMode.wire, maxLines: lineLimit ?? Int32.max)
    try writer.writeLayout(.textLayout(layout), kind: .leaf, content: EmptyRemoteView())
  }
}

private struct FontKey: RemoteEnvironmentKey { static let defaultValue = RemoteFont.body }
private struct FontWeightKey: RemoteEnvironmentKey {
  static let defaultValue: RemoteFontWeight? = nil
}
private struct ItalicKey: RemoteEnvironmentKey { static let defaultValue = false }
private struct LineLimitKey: RemoteEnvironmentKey { static let defaultValue: Int? = nil }
private struct TextAlignmentKey: RemoteEnvironmentKey {
  static let defaultValue = RemoteTextAlignment.leading
}
private struct TruncationModeKey: RemoteEnvironmentKey {
  static let defaultValue = RemoteTruncationMode.tail
}

extension RemoteEnvironmentValues {
  /// The font text takes: ``RemoteFont/body`` unless set.
  public var font: RemoteFont {
    get { self[FontKey.self] }
    set { self[FontKey.self] = newValue }
  }

  /// A weight that overrides the font's own.
  public var fontWeight: RemoteFontWeight? {
    get { self[FontWeightKey.self] }
    set { self[FontWeightKey.self] = newValue }
  }

  public var isItalic: Bool {
    get { self[ItalicKey.self] }
    set { self[ItalicKey.self] = newValue }
  }

  /// The most lines text takes, or `nil` for as many as it needs.
  public var lineLimit: Int? {
    get { self[LineLimitKey.self] }
    set { self[LineLimitKey.self] = newValue }
  }

  public var multilineTextAlignment: RemoteTextAlignment {
    get { self[TextAlignmentKey.self] }
    set { self[TextAlignmentKey.self] = newValue }
  }

  public var truncationMode: RemoteTruncationMode {
    get { self[TruncationModeKey.self] }
    set { self[TruncationModeKey.self] = newValue }
  }
}

extension RemoteView {
  /// The font of the text in this view.
  public func font(_ font: RemoteFont) -> some RemoteView {
    environment(\.font, font)
  }

  /// The weight of the text in this view, over its font's own.
  public func fontWeight(_ weight: RemoteFontWeight?) -> some RemoteView {
    environment(\.fontWeight, weight)
  }

  public func bold(_ isActive: Bool = true) -> some RemoteView {
    environment(\.fontWeight, isActive ? .bold : nil)
  }

  public func italic(_ isActive: Bool = true) -> some RemoteView {
    environment(\.isItalic, isActive)
  }

  /// At most `limit` lines (at least 1) for the text in this view, or as many as it needs.
  public func lineLimit(_ limit: Int?) -> some RemoteView {
    environment(\.lineLimit, limit)
  }

  public func multilineTextAlignment(_ alignment: RemoteTextAlignment)
    -> some RemoteView
  {
    environment(\.multilineTextAlignment, alignment)
  }

  public func truncationMode(_ mode: RemoteTruncationMode) -> some RemoteView {
    environment(\.truncationMode, mode)
  }
}

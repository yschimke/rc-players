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

/// A semantic text style, sized as Apple's default (Large) Dynamic Type size and scaled with the
/// reader's text-size setting as the document plays.
public struct RemoteTextStyle: Hashable, Sendable {
  /// The size at the default text size, in points.
  public let size: Double
  /// The weight the style sets.
  public let weight: RemoteFontWeight

  public init(size: Double, weight: RemoteFontWeight = .regular) {
    self.size = size
    self.weight = weight
  }

  public static let largeTitle = RemoteTextStyle(size: 34)
  public static let title = RemoteTextStyle(size: 28)
  public static let title2 = RemoteTextStyle(size: 22)
  public static let title3 = RemoteTextStyle(size: 20)
  public static let headline = RemoteTextStyle(size: 17, weight: .semibold)
  public static let body = RemoteTextStyle(size: 17)
  public static let callout = RemoteTextStyle(size: 16)
  public static let subheadline = RemoteTextStyle(size: 15)
  public static let footnote = RemoteTextStyle(size: 13)
  public static let caption = RemoteTextStyle(size: 12)
  public static let caption2 = RemoteTextStyle(size: 11)

  /// The font size, in sp, that players publish as `RemoteContextIds.idFontSize` at a text scale
  /// of 1, before density: AndroidX's `RemoteContext` default, which every player loads. A style's
  /// size is scaled by the player's font size over this.
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

/// Text, as SwiftUI's `Text`.
///
/// It defaults to the ``RemoteTextStyle/body`` style in the environment's foreground style
/// (``RemoteColor/primary`` unless a container sets one), so it follows the reader's text size and
/// appearance unless told otherwise.
///
/// ```swift
/// RemoteText("Upload \(progress * 100, decimals: 0)%")
///   .font(.headline)
///   .foregroundStyle(.secondary)
/// ```
///
/// Its own modifiers (`font`, `foregroundStyle`, `lineLimit` …) return a `RemoteText`, so they
/// come before any view modifier such as `padding`.
public struct RemoteText: _PrimitiveRemoteView {
  enum Size: Hashable, Sendable {
    case fixed(Double)
    case style(Double)
  }

  var text: RemoteString
  var size: Size = .style(RemoteTextStyle.body.size)
  var weight: RemoteFontWeight = .regular
  var isItalic = false
  /// The text's own colour, or `nil` for the environment's foreground style.
  var color: RemoteColor? = nil
  var alignment: RemoteTextAlignment = .leading
  var truncation: RemoteTruncationMode = .tail
  var lineLimit: Int? = nil

  public init(_ text: RemoteString) { self.text = text }

  /// A Swift string, as a constant.
  @_disfavoredOverload
  public init(_ content: some StringProtocol) { text = RemoteString(String(content)) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<String>) { text = RemoteString(variable) }

  /// A text style, which scales with the reader's text size and sets the style's weight.
  public func font(_ style: RemoteTextStyle) -> RemoteText {
    var copy = self
    copy.size = .style(style.size)
    copy.weight = style.weight
    return copy
  }

  /// A fixed size in points, which does not scale with the reader's text size.
  public func font(size: Double, weight: RemoteFontWeight = .regular) -> RemoteText {
    var copy = self
    copy.size = .fixed(size)
    copy.weight = weight
    return copy
  }

  public func fontWeight(_ weight: RemoteFontWeight) -> RemoteText {
    var copy = self
    copy.weight = weight
    return copy
  }

  public func italic(_ isActive: Bool = true) -> RemoteText {
    var copy = self
    copy.isItalic = isActive
    return copy
  }

  public func foregroundStyle(_ color: RemoteColor) -> RemoteText {
    var copy = self
    copy.color = color
    return copy
  }

  public func multilineTextAlignment(_ alignment: RemoteTextAlignment) -> RemoteText {
    var copy = self
    copy.alignment = alignment
    return copy
  }

  /// At most `limit` lines (at least 1), or as many as it needs for `nil`.
  public func lineLimit(_ limit: Int?) -> RemoteText {
    var copy = self
    copy.lineLimit = limit.map { Swift.max($0, 1) }
    return copy
  }

  public func truncationMode(_ mode: RemoteTruncationMode) -> RemoteText {
    var copy = self
    copy.truncation = mode
    return copy
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    guard (1...1000).contains(weight.value) else {
      throw RemoteEncodingError.invalidFontWeight(weight.value)
    }
    let componentID = writer.newComponentID()
    let textID = try writer.stringID(text)
    let resolvedColor = try writer.color(color ?? writer.environment.foregroundStyle)
    let fontSize: Float
    switch size {
    case .fixed(let points):
      fontSize = try writer.pixels(points)
    case .style(let points):
      // The style's size, scaled by the player's font scale: `idFontSize` is the base size in
      // pixels at the player's own density and text scale, so over the base size and that density
      // it is the text scale alone, and the document's display scale still gives the pixels.
      let scale =
        RemoteFloat(node: .system(RemoteContextIds.idFontSize))
        / (RemoteFloat(node: .system(RemoteContextIds.idDensity))
          * RemoteFloat(RemoteTextStyle.playerBaseFontSize))
      fontSize = try writer.floatWord(writer.pixels(RemoteFloat(points)) * scale)
    }
    var textAlign = alignment.wire
    let colorWord: Int32
    switch resolvedColor {
    case .literal(let argb): colorWord = argb
    case .id(let id):
      colorWord = id
      textAlign |=
        Int32(TextLayout.BitLayout.textAlignIsDynamicColorMask)
        << Int32(TextLayout.BitLayout.textAlignIsDynamicColorShift)
    }
    let layout = TextLayout(
      componentId: componentID, animationId: IdEncoding.noId, textId: textID, color: colorWord,
      fontSize: fontSize, fontStyle: isItalic ? TextLayout.italic : TextLayout.normal,
      fontWeight: Float(weight.value), fontFamilyId: IdEncoding.noId, textAlign: textAlign,
      overflow: truncation.wire, maxLines: Int32(clamping: lineLimit ?? Int(Int32.max)))
    try writer.writeLayout(.textLayout(layout), kind: .leaf, content: EmptyRemoteView())
  }
}

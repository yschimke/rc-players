/// A font weight, 1...1000 as CSS and Compose's `FontWeight` count it.
public struct RemoteFontWeight: Sendable, Hashable {
  public let value: Float

  public init(_ value: Float) {
    precondition((1...1000).contains(value), "a font weight is 1...1000")
    self.value = value
  }

  public static let thin = RemoteFontWeight(100)
  public static let light = RemoteFontWeight(300)
  public static let normal = RemoteFontWeight(400)
  public static let medium = RemoteFontWeight(500)
  public static let semibold = RemoteFontWeight(600)
  public static let bold = RemoteFontWeight(700)
  public static let black = RemoteFontWeight(900)
}

/// How a text is aligned in its width.
public enum RemoteTextAlign: Sendable {
  case left, right, center, justify, start, end

  var wire: Int {
    switch self {
    case .left: RemoteTextLayoutAlign.left
    case .right: RemoteTextLayoutAlign.right
    case .center: RemoteTextLayoutAlign.center
    case .justify: RemoteTextLayoutAlign.justify
    case .start: RemoteTextLayoutAlign.start
    case .end: RemoteTextLayoutAlign.end
    }
  }
}

/// What a text that does not fit shows.
public enum RemoteTextOverflow: Sendable {
  case clip, visible, ellipsis, startEllipsis, middleEllipsis

  var wire: Int {
    switch self {
    case .clip: RemoteTextLayoutOverflow.clip
    case .visible: RemoteTextLayoutOverflow.visible
    case .ellipsis: RemoteTextLayoutOverflow.ellipsis
    case .startEllipsis: RemoteTextLayoutOverflow.startEllipsis
    case .middleEllipsis: RemoteTextLayoutOverflow.middleEllipsis
    }
  }
}

/// A text, as Compose's `Text`. Its size is in sp.
public struct RemoteText: RemoteView, RemotePrimitiveView {
  let text: RemoteString
  let modifier: RemoteModifier
  let color: RemoteColor
  let fontSize: Float
  let fontWeight: RemoteFontWeight
  let italic: Bool
  let textAlign: RemoteTextAlign
  let overflow: RemoteTextOverflow
  let maxLines: Int

  public init(
    _ text: RemoteString,
    modifier: RemoteModifier = .none,
    color: RemoteColor = .black,
    fontSize: Float = 14,
    fontWeight: RemoteFontWeight = .normal,
    italic: Bool = false,
    textAlign: RemoteTextAlign = .start,
    overflow: RemoteTextOverflow = .clip,
    maxLines: Int = Int(Int32.max)
  ) {
    precondition(fontSize > 0, "fontSize must be positive")
    precondition(maxLines > 0, "maxLines must be positive")
    self.text = text
    self.modifier = modifier
    self.color = color
    self.fontSize = fontSize
    self.fontWeight = fontWeight
    self.italic = italic
    self.textAlign = textAlign
    self.overflow = overflow
    self.maxLines = Swift.min(maxLines, Int(Int32.max))
  }

  /// A constant text; the `String` overload, so a `String` variable needs no conversion.
  public init(
    _ text: String,
    modifier: RemoteModifier = .none,
    color: RemoteColor = .black,
    fontSize: Float = 14,
    fontWeight: RemoteFontWeight = .normal,
    italic: Bool = false,
    textAlign: RemoteTextAlign = .start,
    overflow: RemoteTextOverflow = .clip,
    maxLines: Int = Int(Int32.max)
  ) {
    self.init(
      RemoteString(text), modifier: modifier, color: color, fontSize: fontSize,
      fontWeight: fontWeight, italic: italic, textAlign: textAlign, overflow: overflow,
      maxLines: maxLines)
  }

  public var body: Never { return fatalError("RemoteText writes itself") }

  func write(to context: RemoteCreationContext) {
    let textID = context.stringID(text)
    let resolved = context.color(color)
    var modifierBody = RemoteByteBuffer()
    swap(&modifierBody, &context.body)
    context.write(modifier)
    swap(&modifierBody, &context.body)

    // `TextLayout`, the text component every player profile reads (`CoreText` is AndroidX-only).
    context.op(RemoteOpcode.layoutText)
    context.body.int(context.newComponentID())
    context.body.int(RemoteIDEncoding.noID)  // No animation id.
    context.body.int(textID)
    let alignment: Int
    switch resolved {
    case .literal(let argb):
      context.body.int(Int(Int32(bitPattern: argb)))
      alignment = textAlign.wire
    case .id(let id):
      context.body.int(id)
      alignment = RemoteTextLayoutFlag.isDynamicColor | textAlign.wire
    }
    context.body.float(context.px(fontSize))
    context.body.int(italic ? RemoteFontStyle.italic : RemoteFontStyle.normal)
    context.body.float(fontWeight.value)
    context.body.int(RemoteIDEncoding.noID)  // The default font family.
    context.body.int(alignment)
    context.body.int(overflow.wire)
    context.body.int(maxLines)
    context.body.append(modifierBody)
    context.contentSection()
    context.containerEnd()
    context.containerEnd()
  }
}

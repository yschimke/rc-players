import RcCreation

/// A monochrome vector symbol on a square grid: the shapes a ``RemoteIcon`` draws.
///
/// SF Symbols cannot be copied into a document, so the built-in symbols are simple geometry drawn
/// on SF Symbols' grid conventions (a 24-point square, 2-point round strokes), named after the SF
/// Symbol they stand in for. Any icon can be added from SVG path data:
///
/// ```swift
/// let leaf = RemoteSymbol(svg: "M5 19c8 0 14-6 14-14-8 0-14 6-14 14z", style: .fill)
/// ```
public struct RemoteSymbol: Hashable, Sendable {
  /// How a part of a symbol is drawn.
  public enum Style: Hashable, Sendable {
    case fill
    /// A round-capped, round-joined stroke, `lineWidth` grid units wide.
    case stroke(lineWidth: Double)
  }

  /// One path of a symbol and how it is drawn.
  public struct Part: Hashable, Sendable {
    public let path: RemotePath
    public let style: Style

    public init(_ path: RemotePath, style: Style) {
      self.path = path
      self.style = style
    }
  }

  /// The side of the square grid the parts are drawn on.
  public let gridSize: Double
  public let parts: [Part]

  public init(gridSize: Double = 24, parts: [Part]) {
    self.gridSize = gridSize
    self.parts = parts
  }

  /// A one-part symbol from SVG path data on a `gridSize` square, or `nil` if the data cannot be
  /// read (``RemotePath/init(svg:)``).
  public init?(svg data: String, gridSize: Double = 24, style: Style = .fill) {
    guard let path = RemotePath(svg: data) else { return nil }
    self.init(gridSize: gridSize, parts: [Part(path, style: style)])
  }

  /// A built-in symbol from its SVG. Every built-in symbol's data is checked to parse by
  /// `RemoteFoundationTests`, so the empty fallback is never drawn.
  private static func builtIn(_ parts: (String, Style)...) -> RemoteSymbol {
    RemoteSymbol(
      parts: parts.map { data, style in Part(RemotePath(svg: data) ?? RemotePath(), style: style) })
  }

  private static let line = Style.stroke(lineWidth: 2)

  /// A circle of `radius` about (`cx`, `cy`), as four cubic arcs, in SVG.
  private static func circle(_ cx: Double, _ cy: Double, _ radius: Double) -> String {
    // The control-point distance that makes a quarter cubic closest to a circular arc.
    let k = radius * 0.552_284_749_8
    return """
      M\(cx + radius) \(cy) C\(cx + radius) \(cy + k) \(cx + k) \(cy + radius) \(cx) \(cy + radius) \
      C\(cx - k) \(cy + radius) \(cx - radius) \(cy + k) \(cx - radius) \(cy) \
      C\(cx - radius) \(cy - k) \(cx - k) \(cy - radius) \(cx) \(cy - radius) \
      C\(cx + k) \(cy - radius) \(cx + radius) \(cy - k) \(cx + radius) \(cy) Z
      """
  }

  public static let chevronRight = builtIn(("M9 5 L16 12 L9 19", line))
  public static let chevronLeft = builtIn(("M15 5 L8 12 L15 19", line))
  public static let chevronDown = builtIn(("M5 9 L12 16 L19 9", line))
  public static let chevronUp = builtIn(("M5 15 L12 8 L19 15", line))
  public static let checkmark = builtIn(("M4.5 12.5 L9.5 17.5 L19.5 6.5", line))
  public static let xmark = builtIn(("M6 6 L18 18 M18 6 L6 18", line))
  public static let plus = builtIn(("M12 5 V19 M5 12 H19", line))
  public static let minus = builtIn(("M5 12 H19", line))
  public static let circle = builtIn((circle(12, 12, 9), line))
  public static let circleFill = builtIn((circle(12, 12, 10), .fill))
  public static let checkmarkCircle = builtIn(
    (circle(12, 12, 9), line), ("M8 12.5 L11 15.5 L16.5 9", line))
  public static let xmarkCircle = builtIn(
    (circle(12, 12, 9), line), ("M9 9 L15 15 M15 9 L9 15", line))
  public static let infoCircle = builtIn(
    (circle(12, 12, 9), line), ("M12 11 V17", line), (circle(12, 7.5, 1.25), .fill))
  public static let ellipsis = builtIn(
    (circle(5.5, 12, 1.75), .fill), (circle(12, 12, 1.75), .fill), (circle(18.5, 12, 1.75), .fill))
  public static let playFill = builtIn(("M7 4.5 V19.5 L19.5 12 Z", .fill))
  public static let pauseFill = builtIn(
    ("M6.5 5 H10 V19 H6.5 Z", .fill), ("M14 5 H17.5 V19 H14 Z", .fill))
  public static let boltFill = builtIn(("M13.5 2 L4.5 13.5 H11 L10 22 L19.5 10 H13 Z", .fill))
  public static let heartFill = builtIn(
    (
      "M12 20.5 C12 20.5 3 14.6 3 8.6 C3 5.5 5.4 3.2 8.3 3.2 C10 3.2 11.3 4.1 12 5.3 "
        + "C12.7 4.1 14 3.2 15.7 3.2 C18.6 3.2 21 5.5 21 8.6 C21 14.6 12 20.5 12 20.5 Z",
      .fill
    ))
  public static let starFill = builtIn(
    (
      "M12 2.5 L14.9 8.6 L21.5 9.4 L16.6 13.9 L17.9 20.5 L12 17.2 L6.1 20.5 L7.4 13.9 L2.5 9.4 L9.1 8.6 Z",
      .fill
    ))
  public static let bellFill = builtIn(
    (
      "M12 3 C8.7 3 6.5 5.5 6.5 8.8 V13.5 L4.5 16.5 V17.5 H19.5 V16.5 L17.5 13.5 V8.8 "
        + "C17.5 5.5 15.3 3 12 3 Z",
      .fill
    ),
    ("M9.8 19 C10.2 20.2 11 21 12 21 C13 21 13.8 20.2 14.2 19 Z", .fill))
}

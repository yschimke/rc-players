import RcWireOperations

/// A vector outline, in points, as SwiftUI's `Path`: lines and curves a canvas fills or strokes.
///
/// ```swift
/// let check = RemotePath { path in
///   path.move(to: 4, 12)
///   path.addLine(to: 9, 17)
///   path.addLine(to: 20, 6)
/// }
/// ```
///
/// A path is fixed when the document is written; it is declared once (`PathData`) however many
/// times it is drawn.
public struct RemotePath: Hashable, Sendable {
  enum Element: Hashable, Sendable {
    case move(Double, Double)
    case line(Double, Double)
    case quad(Double, Double, Double, Double)
    case cubic(Double, Double, Double, Double, Double, Double)
    case close
  }

  private(set) var elements: [Element] = []

  public init() {}

  public init(_ build: (inout RemotePath) -> Void) { build(&self) }

  public var isEmpty: Bool { elements.isEmpty }

  public mutating func move(to x: Double, _ y: Double) { elements.append(.move(x, y)) }

  public mutating func addLine(to x: Double, _ y: Double) { elements.append(.line(x, y)) }

  public mutating func addQuadCurve(to x: Double, _ y: Double, control cx: Double, _ cy: Double) {
    elements.append(.quad(cx, cy, x, y))
  }

  public mutating func addCurve(
    to x: Double, _ y: Double, control1 c1x: Double, _ c1y: Double, control2 c2x: Double,
    _ c2y: Double
  ) {
    elements.append(.cubic(c1x, c1y, c2x, c2y, x, y))
  }

  public mutating func closeSubpath() { elements.append(.close) }

  /// This path scaled by `scale` about the origin, then moved by `dx` and `dy`.
  public func applying(scale: Double, dx: Double = 0, dy: Double = 0) -> RemotePath {
    func x(_ value: Double) -> Double { value * scale + dx }
    func y(_ value: Double) -> Double { value * scale + dy }
    var copy = RemotePath()
    copy.elements = elements.map { element in
      switch element {
      case .move(let px, let py): .move(x(px), y(py))
      case .line(let px, let py): .line(x(px), y(py))
      case .quad(let cx, let cy, let px, let py): .quad(x(cx), y(cy), x(px), y(py))
      case .cubic(let ax, let ay, let bx, let by, let px, let py):
        .cubic(x(ax), y(ay), x(bx), y(by), x(px), y(py))
      case .close: .close
      }
    }
    return copy
  }

  /// The `PathData` words: each command's tag, NaN-boxed, then its points. A segment repeats its
  /// start point, as AndroidX's `RemotePath` writes it (`PathCommandLayout`).
  func pathWords(scale: Float) -> [Float] {
    var words: [Float] = []
    var current: (Float, Float) = (0, 0)
    var start: (Float, Float) = (0, 0)
    func tag(_ value: Int32) -> Float { _RemoteWriter.reference(value) }
    func segment(_ command: Int32, size: Int, _ points: [(offset: Int, value: Float)]) {
      var segment = [Float](repeating: 0, count: size)
      segment[0] = tag(command)
      for point in points { segment[point.offset] = point.value }
      words += segment
    }
    for element in elements {
      switch element {
      case .move(let x, let y):
        let point = (Float(x) * scale, Float(y) * scale)
        segment(
          PathCommandTags.move, size: PathCommandLayout.moveWords,
          [(PathCommandLayout.moveXOffset, point.0), (PathCommandLayout.moveYOffset, point.1)])
        (current, start) = (point, point)
      case .line(let x, let y):
        let point = (Float(x) * scale, Float(y) * scale)
        segment(
          PathCommandTags.line, size: PathCommandLayout.lineWords,
          [
            (PathCommandLayout.moveXOffset, current.0), (PathCommandLayout.moveYOffset, current.1),
            (PathCommandLayout.lineXOffset, point.0), (PathCommandLayout.lineYOffset, point.1),
          ])
        current = point
      case .quad(let cx, let cy, let x, let y):
        let point = (Float(x) * scale, Float(y) * scale)
        segment(
          PathCommandTags.quadratic, size: PathCommandLayout.quadraticWords,
          [
            (PathCommandLayout.moveXOffset, current.0), (PathCommandLayout.moveYOffset, current.1),
            (PathCommandLayout.quadraticX1Offset, Float(cx) * scale),
            (PathCommandLayout.quadraticY1Offset, Float(cy) * scale),
            (PathCommandLayout.quadraticX2Offset, point.0),
            (PathCommandLayout.quadraticY2Offset, point.1),
          ])
        current = point
      case .cubic(let ax, let ay, let bx, let by, let x, let y):
        let point = (Float(x) * scale, Float(y) * scale)
        segment(
          PathCommandTags.cubic, size: PathCommandLayout.cubicWords,
          [
            (PathCommandLayout.moveXOffset, current.0), (PathCommandLayout.moveYOffset, current.1),
            (PathCommandLayout.cubicX1Offset, Float(ax) * scale),
            (PathCommandLayout.cubicY1Offset, Float(ay) * scale),
            (PathCommandLayout.cubicX2Offset, Float(bx) * scale),
            (PathCommandLayout.cubicY2Offset, Float(by) * scale),
            (PathCommandLayout.cubicX3Offset, point.0), (PathCommandLayout.cubicY3Offset, point.1),
          ])
        current = point
      case .close:
        words.append(tag(PathCommandTags.close))
        current = start
      }
    }
    words.append(tag(PathCommandTags.done))
    return words
  }
}

extension RemotePath {
  /// A path from SVG path data (`d`): `M L H V C S Q T Z` in absolute and relative forms. Arcs
  /// (`A`) are not supported. Returns `nil` for data it cannot read.
  public init?(svg data: String) {
    var scanner = SVGPathScanner(data)
    var path = RemotePath()
    var current = (x: 0.0, y: 0.0)
    var start = current
    var lastControl: (x: Double, y: Double)? = nil
    var command: Character? = nil
    while let next = scanner.nextCommand(after: command) {
      command = next
      let relative = next.isLowercase
      func point() -> (x: Double, y: Double)? {
        guard let x = scanner.number(), let y = scanner.number() else { return nil }
        return relative ? (current.x + x, current.y + y) : (x, y)
      }
      var control: (x: Double, y: Double)? = nil
      switch next.uppercased().first {
      case "M":
        guard let p = point() else { return nil }
        path.move(to: p.x, p.y)
        (current, start) = (p, p)
        // Further pairs after a move are lines.
        command = relative ? "l" : "L"
      case "L":
        guard let p = point() else { return nil }
        path.addLine(to: p.x, p.y)
        current = p
      case "H":
        guard let x = scanner.number() else { return nil }
        current.x = relative ? current.x + x : x
        path.addLine(to: current.x, current.y)
      case "V":
        guard let y = scanner.number() else { return nil }
        current.y = relative ? current.y + y : y
        path.addLine(to: current.x, current.y)
      case "C":
        guard let a = point(), let b = point(), let p = point() else { return nil }
        path.addCurve(to: p.x, p.y, control1: a.x, a.y, control2: b.x, b.y)
        (current, control) = (p, b)
      case "S":
        let a = lastControl.map { (2 * current.x - $0.x, 2 * current.y - $0.y) } ?? current
        guard let b = point(), let p = point() else { return nil }
        path.addCurve(to: p.x, p.y, control1: a.0, a.1, control2: b.x, b.y)
        (current, control) = (p, b)
      case "Q":
        guard let a = point(), let p = point() else { return nil }
        path.addQuadCurve(to: p.x, p.y, control: a.x, a.y)
        (current, control) = (p, a)
      case "T":
        let a = lastControl.map { (x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
        guard let p = point() else { return nil }
        path.addQuadCurve(to: p.x, p.y, control: a.x, a.y)
        (current, control) = (p, a)
      case "Z":
        path.closeSubpath()
        current = start
        command = nil
      default:
        return nil
      }
      lastControl = control
    }
    guard scanner.isAtEnd, !path.isEmpty else { return nil }
    self = path
  }
}

/// Reads SVG path data: commands, and numbers separated by spaces, commas or signs.
private struct SVGPathScanner {
  private let characters: [Character]
  private var index = 0

  init(_ data: String) { characters = Array(data) }

  var isAtEnd: Bool {
    var probe = self
    probe.skipSeparators()
    return probe.index >= probe.characters.count
  }

  private mutating func skipSeparators() {
    while index < characters.count, characters[index].isWhitespace || characters[index] == "," {
      index += 1
    }
  }

  /// The next command letter, or `previous` again when numbers follow without one.
  mutating func nextCommand(after previous: Character?) -> Character? {
    skipSeparators()
    guard index < characters.count else { return nil }
    let character = characters[index]
    if character.isLetter {
      index += 1
      return character
    }
    return previous
  }

  mutating func number() -> Double? {
    skipSeparators()
    var text = ""
    var seenDot = false
    var seenExponent = false
    while index < characters.count {
      let character = characters[index]
      if character.isNumber {
        text.append(character)
      } else if character == ".", !seenDot, !seenExponent {
        seenDot = true
        text.append(character)
      } else if character == "-" || character == "+",
        text.isEmpty || text.last == "e" || text.last == "E"
      {
        text.append(character)
      } else if character == "e" || character == "E", !seenExponent, !text.isEmpty {
        seenExponent = true
        text.append(character)
      } else {
        break
      }
      index += 1
    }
    return Double(text)
  }
}

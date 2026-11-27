import RcWireOperations

/// A point, in points from a canvas's top-leading corner.
public struct RemotePoint: Hashable, Sendable {
  public var x: RemoteFloat
  public var y: RemoteFloat

  public init(x: RemoteFloat, y: RemoteFloat) {
    self.x = x
    self.y = y
  }

  public static let zero = RemotePoint(x: 0, y: 0)
}

/// A size, in points.
public struct RemoteSize: Hashable, Sendable {
  public var width: RemoteFloat
  public var height: RemoteFloat

  public init(width: RemoteFloat, height: RemoteFloat) {
    self.width = width
    self.height = height
  }
}

/// A rectangle, in points.
public struct RemoteRect: Hashable, Sendable {
  public var origin: RemotePoint
  public var size: RemoteSize

  public init(origin: RemotePoint, size: RemoteSize) {
    self.origin = origin
    self.size = size
  }

  public init(x: RemoteFloat, y: RemoteFloat, width: RemoteFloat, height: RemoteFloat) {
    self.init(origin: RemotePoint(x: x, y: y), size: RemoteSize(width: width, height: height))
  }

  public var center: RemotePoint {
    RemotePoint(x: origin.x + size.width / 2, y: origin.y + size.height / 2)
  }
}

/// A canvas's size, as its layout gives it while the document plays.
public struct RemoteCanvasSize: Hashable, Sendable {
  public let width: RemoteFloat
  public let height: RemoteFloat

  public var center: RemotePoint { RemotePoint(x: width / 2, y: height / 2) }

  /// The whole canvas.
  public var bounds: RemoteRect {
    RemoteRect(origin: .zero, size: RemoteSize(width: width, height: height))
  }
}

/// How a stroke ends, as `CGLineCap`.
public enum RemoteLineCap: Hashable, Sendable {
  case butt, round, square

  var wire: Int32 {
    switch self {
    case .butt: PaintConstants.capButt
    case .round: PaintConstants.capRound
    case .square: PaintConstants.capSquare
    }
  }
}

/// How a stroke turns a corner, as `CGLineJoin`.
public enum RemoteLineJoin: Hashable, Sendable {
  case miter, round, bevel

  var wire: Int32 {
    switch self {
    case .miter: PaintConstants.joinMiter
    case .round: PaintConstants.joinRound
    case .bevel: PaintConstants.joinBevel
    }
  }
}

/// Something a ``RemoteGraphicsContext`` fills or strokes.
public struct RemoteDrawShape: Hashable, Sendable {
  enum Kind: Hashable, Sendable {
    case rect(RemoteRect)
    case roundedRect(RemoteRect, cornerRadius: Double)
    case ellipse(RemoteRect)
    case circle(center: RemotePoint, radius: RemoteFloat)
    case line(from: RemotePoint, to: RemotePoint)
    case path(RemotePath)
    case arc(center: RemotePoint, radius: RemoteFloat, startAngle: RemoteFloat, sweep: RemoteFloat)
  }

  let kind: Kind

  public static func rect(_ rect: RemoteRect) -> RemoteDrawShape {
    RemoteDrawShape(kind: .rect(rect))
  }

  public static func roundedRect(_ rect: RemoteRect, cornerRadius: Double) -> RemoteDrawShape {
    RemoteDrawShape(kind: .roundedRect(rect, cornerRadius: cornerRadius))
  }

  public static func ellipse(in rect: RemoteRect) -> RemoteDrawShape {
    RemoteDrawShape(kind: .ellipse(rect))
  }

  public static func circle(center: RemotePoint, radius: RemoteFloat) -> RemoteDrawShape {
    RemoteDrawShape(kind: .circle(center: center, radius: radius))
  }

  /// Part of a circle's outline: from `startAngle` degrees (0 at three o'clock, increasing
  /// clockwise) through `sweep` degrees. Both may be live: a gauge's sweep follows its value.
  public static func arc(
    center: RemotePoint, radius: RemoteFloat, startAngle: RemoteFloat, sweep: RemoteFloat
  ) -> RemoteDrawShape {
    RemoteDrawShape(
      kind: .arc(center: center, radius: radius, startAngle: startAngle, sweep: sweep))
  }

  /// A vector path, in the canvas's points.
  public static func path(_ path: RemotePath) -> RemoteDrawShape {
    RemoteDrawShape(kind: .path(path))
  }

  /// A segment; it has no area, so only ``RemoteGraphicsContext/stroke(_:with:lineWidth:lineCap:lineJoin:)``
  /// draws it.
  public static func line(from start: RemotePoint, to end: RemotePoint) -> RemoteDrawShape {
    RemoteDrawShape(kind: .line(from: start, to: end))
  }
}

/// What a ``RemoteCanvas`` draws, as SwiftUI's `GraphicsContext` takes it.
public struct RemoteGraphicsContext: Sendable {
  enum Style: Hashable, Sendable {
    case fill
    case stroke(lineWidth: Double, cap: RemoteLineCap, join: RemoteLineJoin)
  }

  struct Command: Hashable, Sendable {
    let shape: RemoteDrawShape
    let color: RemoteColor
    let style: Style

    var readsCanvasSize: Bool {
      let values: [RemoteFloat]
      switch shape.kind {
      case .rect(let rect), .roundedRect(let rect, _), .ellipse(let rect):
        values = [rect.origin.x, rect.origin.y, rect.size.width, rect.size.height]
      case .circle(let center, let radius): values = [center.x, center.y, radius]
      case .line(let start, let end): values = [start.x, start.y, end.x, end.y]
      case .path: values = []
      case .arc(let center, let radius, let startAngle, let sweep):
        values = [center.x, center.y, radius, startAngle, sweep]
      }
      return values.contains { $0.readsCanvasSize }
    }
  }

  private(set) var commands: [Command] = []

  init() {}

  public mutating func fill(_ shape: RemoteDrawShape, with color: RemoteColor) {
    commands.append(Command(shape: shape, color: color, style: .fill))
  }

  public mutating func stroke(
    _ shape: RemoteDrawShape, with color: RemoteColor, lineWidth: Double = 1,
    lineCap: RemoteLineCap = .butt, lineJoin: RemoteLineJoin = .miter
  ) {
    commands.append(
      Command(
        shape: shape, color: color,
        style: .stroke(lineWidth: lineWidth, cap: lineCap, join: lineJoin)))
  }
}

/// Drawing, as SwiftUI's `Canvas`: the closure runs once, while the document is written, and
/// records what to draw; the player draws it, at the size the layout gives the canvas.
///
/// ```swift
/// RemoteCanvas { context, size in
///   context.fill(.circle(center: size.center, radius: 20), with: .hex(0x3D5AFE))
///   context.stroke(.circle(center: size.center, radius: 12), with: .white, lineWidth: 3)
/// }
/// .frame(width: 40, height: 40)
/// ```
///
/// `size` is live: coordinates built from it follow the canvas as it resizes. A canvas fills the
/// space it is offered on any axis its frame does not fix.
public struct RemoteCanvas: _PrimitiveRemoteView {
  let commands: [RemoteGraphicsContext.Command]

  public init(_ draw: (inout RemoteGraphicsContext, RemoteCanvasSize) -> Void) {
    var context = RemoteGraphicsContext()
    draw(
      &context,
      RemoteCanvasSize(
        width: RemoteFloat(node: .canvasSize(.width)),
        height: RemoteFloat(node: .canvasSize(.height))))
    commands = context.commands
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try writer.writeCanvas(commands)
  }
}

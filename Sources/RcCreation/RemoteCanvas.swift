/// A point in dp.
public struct RemotePoint: Sendable {
  public var x: RemoteFloat
  public var y: RemoteFloat

  public init(_ x: RemoteFloat, _ y: RemoteFloat) {
    self.x = x
    self.y = y
  }
}

/// A size in dp.
public struct RemoteSize: Sendable {
  public var width: RemoteFloat
  public var height: RemoteFloat

  public init(_ width: RemoteFloat, _ height: RemoteFloat) {
    self.width = width
    self.height = height
  }
}

/// Whether a shape is filled or outlined.
public enum RemoteDrawStyle: Sendable, Equatable {
  case fill
  /// Outlined with a stroke [width] dp wide.
  case stroke(width: Float)
}

/// The drawing calls of a ``RemoteCanvas``, as Compose's `DrawScope`. Coordinates are in dp from
/// the canvas's top-left corner.
public struct RemoteDrawScope: Sendable {
  enum Shape: Sendable {
    case rect(RemotePoint, RemoteSize)
    case roundRect(RemotePoint, RemoteSize, radius: Float)
    case oval(RemotePoint, RemoteSize)
    case circle(center: RemotePoint, radius: RemoteFloat)
    case line(RemotePoint, RemotePoint)
  }

  struct Command: Sendable {
    let shape: Shape
    let color: RemoteColor
    let style: RemoteDrawStyle
  }

  var commands: [Command] = []

  public mutating func drawRect(
    _ color: RemoteColor, topLeft: RemotePoint = RemotePoint(0, 0), size: RemoteSize,
    style: RemoteDrawStyle = .fill
  ) {
    commands.append(Command(shape: .rect(topLeft, size), color: color, style: style))
  }

  public mutating func drawRoundRect(
    _ color: RemoteColor, topLeft: RemotePoint = RemotePoint(0, 0), size: RemoteSize,
    cornerRadius: Float, style: RemoteDrawStyle = .fill
  ) {
    commands.append(
      Command(shape: .roundRect(topLeft, size, radius: cornerRadius), color: color, style: style))
  }

  public mutating func drawOval(
    _ color: RemoteColor, topLeft: RemotePoint = RemotePoint(0, 0), size: RemoteSize,
    style: RemoteDrawStyle = .fill
  ) {
    commands.append(Command(shape: .oval(topLeft, size), color: color, style: style))
  }

  public mutating func drawCircle(
    _ color: RemoteColor, radius: RemoteFloat, center: RemotePoint,
    style: RemoteDrawStyle = .fill
  ) {
    commands.append(
      Command(shape: .circle(center: center, radius: radius), color: color, style: style))
  }

  public mutating func drawLine(
    _ color: RemoteColor, start: RemotePoint, end: RemotePoint, strokeWidth: Float = 1
  ) {
    commands.append(
      Command(shape: .line(start, end), color: color, style: .stroke(width: strokeWidth)))
  }
}

/// A component that draws, as Compose's `Canvas`. Give it a size through its modifier.
///
/// ```swift
/// RemoteCanvas(modifier: .size(48)) { scope in
///   scope.drawCircle(.blue, radius: 24, center: RemotePoint(24, 24))
/// }
/// ```
public struct RemoteCanvas: RemoteView, RemotePrimitiveView {
  let modifier: RemoteModifier
  let scope: RemoteDrawScope

  public init(modifier: RemoteModifier, draw: (inout RemoteDrawScope) -> Void) {
    self.modifier = modifier
    var scope = RemoteDrawScope()
    draw(&scope)
    self.scope = scope
  }

  public var body: Never { return fatalError("RemoteCanvas writes itself") }

  func write(to context: RemoteCreationContext) {
    // Resolve every value the drawing reads before the canvas opens.
    let draws = scope.commands.map { context.resolve($0) }
    var modifierBody = RemoteByteBuffer()
    swap(&modifierBody, &context.body)
    context.write(modifier)
    swap(&modifierBody, &context.body)

    let canvasID = context.newComponentID()
    context.op(RemoteOpcode.layoutCanvas)
    context.body.int(canvasID)
    context.body.int(remoteNoID)  // No animation id.
    context.body.append(modifierBody)
    context.contentSection()
    // `CanvasContent`: the drawing, in the form every player profile reads.
    context.op(RemoteOpcode.layoutCanvasContent)
    context.body.int(context.newComponentID())
    var current: [Int]? = nil
    for draw in draws {
      if draw.paint != current {
        context.op(RemoteOpcode.paintValues)
        context.body.int(draw.paint.count)
        for word in draw.paint { context.body.int(word) }
        current = draw.paint
      }
      context.op(draw.opcode)
      for word in draw.words { context.body.float(word) }
    }
    context.containerEnd()
    context.containerEnd()
    context.containerEnd()
  }
}

struct RemoteResolvedDraw {
  let opcode: Int
  let words: [Float]
  /// The whole paint, written in full whenever it changes: a delta would depend on the paint
  /// the previous component left, which a player resets per component.
  let paint: [Int]
}

extension RemoteCreationContext {
  func resolve(_ command: RemoteDrawScope.Command) -> RemoteResolvedDraw {
    func point(_ p: RemotePoint) -> [Float] { [pxWord(p.x), pxWord(p.y)] }
    func box(_ origin: RemotePoint, _ size: RemoteSize) -> [Float] {
      [
        pxWord(origin.x), pxWord(origin.y), pxWord(origin.x + size.width),
        pxWord(origin.y + size.height),
      ]
    }
    let opcode: Int
    let words: [Float]
    switch command.shape {
    case .rect(let origin, let size):
      (opcode, words) = (RemoteOpcode.drawRect, box(origin, size))
    case .roundRect(let origin, let size, let radius):
      (opcode, words) = (RemoteOpcode.drawRoundRect, box(origin, size) + [px(radius), px(radius)])
    case .oval(let origin, let size):
      (opcode, words) = (RemoteOpcode.drawOval, box(origin, size))
    case .circle(let center, let radius):
      (opcode, words) = (RemoteOpcode.drawCircle, point(center) + [pxWord(radius)])
    case .line(let start, let end):
      (opcode, words) = (RemoteOpcode.drawLine, point(start) + point(end))
    }

    var paint: [Int] = []
    switch color(command.color) {
    case .literal(let argb):
      paint += [RemotePaintTag.color, Int(Int32(bitPattern: argb))]
    case .id(let id):
      paint += [RemotePaintTag.colorID, id]
    }
    switch command.style {
    case .fill:
      paint.append(RemotePaintTag.style | RemotePaintTag.styleFill << 16)
    case .stroke(let width):
      paint.append(RemotePaintTag.style | RemotePaintTag.styleStroke << 16)
      paint += [RemotePaintTag.strokeWidth, Int(Int32(bitPattern: px(width).bitPattern))]
    }
    paint.append(RemotePaintTag.antiAlias | 1 << 16)
    return RemoteResolvedDraw(opcode: opcode, words: words, paint: paint)
  }
}

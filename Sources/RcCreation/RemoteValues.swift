import RcWireOperations

/// A type a ``RemoteVariable`` can hold: `Double`, `Int`, `String`, ``RemoteColorValue`` and
/// ``RemoteVisibility``.
public protocol RemoteVariableValue: Hashable, Sendable {}

extension Double: RemoteVariableValue {}
extension Int: RemoteVariableValue {}
extension String: RemoteVariableValue {}

/// A value the host writes by name while the document plays, starting from a default.
///
/// A variable is the only thing an action can write (``RemoteAction``'s `set(_:to:)`), so writing
/// to a constant or an expression is a type error rather than a document that ignores the write.
///
/// ```swift
/// let progress = RemoteVariable("progress", default: 0.62)
/// let subtitle = RemoteVariable("subtitle", default: "Composed in Swift")
///
/// RemoteText("Upload \(progress * 100, decimals: 0)%")
/// RemoteText("\(subtitle)")
/// ```
///
/// Two variables with the same name must agree on their type and default; a document that has
/// both fails to encode with ``RemoteEncodingError/conflictingVariable(name:)``.
public struct RemoteVariable<Value: RemoteVariableValue>: Hashable, Sendable {
  /// The name the host writes the value by.
  public let name: String
  /// The value until the host writes one.
  public let defaultValue: Value

  public init(_ name: String, default defaultValue: Value) {
    self.name = name
    self.defaultValue = defaultValue
  }
}

/// A number the document holds: a constant, a ``RemoteVariable``, or arithmetic over those that
/// the player evaluates as it plays.
///
/// Literals convert, so `.frame(width: 48)` takes a constant and `.frame(width: progress * 328)`
/// a live width. Arithmetic on two constants is evaluated here and costs the document nothing.
public struct RemoteFloat: Hashable, Sendable, ExpressibleByFloatLiteral,
  ExpressibleByIntegerLiteral
{
  indirect enum Node: Hashable, Sendable {
    case constant(Float)
    case variable(name: String, defaultValue: Float)
    /// A value the player supplies, by its system id (`RemoteContextIds`).
    case system(Int32)
    /// A float the writer has already declared, by id.
    case declared(Int32)
    /// One axis of the size of the canvas being drawn, in points.
    case canvasSize(RemoteCanvasAxis)
    /// The same, in pixels: what the writer scales a size to.
    case canvasPixels(RemoteCanvasAxis)
    case unary(Operator, Node)
    case binary(Operator, Node, Node)
  }

  /// The `FloatExpression` operators this API writes.
  enum Operator: Hashable, Sendable {
    case add, subtract, multiply, divide, minimum, maximum, absolute

    var code: Int32 {
      switch self {
      case .add: FloatExpressionOperator.add
      case .subtract: FloatExpressionOperator.sub
      case .multiply: FloatExpressionOperator.mul
      case .divide: FloatExpressionOperator.div
      case .minimum: FloatExpressionOperator.min
      case .maximum: FloatExpressionOperator.max
      case .absolute: FloatExpressionOperator.abs
      }
    }
  }

  let node: Node

  init(node: Node) { self.node = node }

  public init(_ value: Double) { node = .constant(Float(value)) }
  public init(_ value: Float) { node = .constant(value) }
  public init(_ value: Int) { node = .constant(Float(value)) }
  public init(floatLiteral value: Double) { node = .constant(Float(value)) }
  public init(integerLiteral value: Int) { node = .constant(Float(value)) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<Double>) {
    node = .variable(name: variable.name, defaultValue: Float(variable.defaultValue))
  }

  public static func + (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteFloat {
    fold(.add, lhs, rhs, +)
  }
  public static func - (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteFloat {
    fold(.subtract, lhs, rhs, -)
  }
  public static func * (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteFloat {
    fold(.multiply, lhs, rhs, *)
  }
  public static func / (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteFloat {
    fold(.divide, lhs, rhs, /)
  }
  public static prefix func - (operand: RemoteFloat) -> RemoteFloat { -1 * operand }

  public static func min(_ lhs: RemoteFloat, _ rhs: RemoteFloat) -> RemoteFloat {
    fold(.minimum, lhs, rhs, Swift.min)
  }
  public static func max(_ lhs: RemoteFloat, _ rhs: RemoteFloat) -> RemoteFloat {
    fold(.maximum, lhs, rhs, Swift.max)
  }

  /// The magnitude of this value.
  public var magnitude: RemoteFloat {
    if case .constant(let value) = node { return RemoteFloat(Swift.abs(value)) }
    return RemoteFloat(node: .unary(.absolute, node))
  }

  /// This value, clamped to `range`.
  public func clamped(to range: ClosedRange<Double>) -> RemoteFloat {
    .min(.max(self, RemoteFloat(range.lowerBound)), RemoteFloat(range.upperBound))
  }

  /// Whether this reads the size of the canvas being drawn.
  var readsCanvasSize: Bool { node.readsCanvasSize }

  var constant: Float? {
    if case .constant(let value) = node { return value }
    return nil
  }

  private static func fold(
    _ operation: Operator, _ lhs: RemoteFloat, _ rhs: RemoteFloat,
    _ evaluate: (Float, Float) -> Float
  ) -> RemoteFloat {
    if let left = lhs.constant, let right = rhs.constant {
      return RemoteFloat(evaluate(left, right))
    }
    return RemoteFloat(node: .binary(operation, lhs.node, rhs.node))
  }
}

extension RemoteFloat.Node {
  var readsCanvasSize: Bool {
    switch self {
    case .canvasSize, .canvasPixels: true
    case .constant, .variable, .system, .declared: false
    case .unary(_, let operand): operand.readsCanvasSize
    case .binary(_, let lhs, let rhs): lhs.readsCanvasSize || rhs.readsCanvasSize
    }
  }
}

/// Arithmetic directly on a `Double` variable: `progress * 100`.
extension RemoteVariable where Value == Double {
  public static func + (lhs: Self, rhs: RemoteFloat) -> RemoteFloat { RemoteFloat(lhs) + rhs }
  public static func + (lhs: RemoteFloat, rhs: Self) -> RemoteFloat { lhs + RemoteFloat(rhs) }
  public static func - (lhs: Self, rhs: RemoteFloat) -> RemoteFloat { RemoteFloat(lhs) - rhs }
  public static func - (lhs: RemoteFloat, rhs: Self) -> RemoteFloat { lhs - RemoteFloat(rhs) }
  public static func * (lhs: Self, rhs: RemoteFloat) -> RemoteFloat { RemoteFloat(lhs) * rhs }
  public static func * (lhs: RemoteFloat, rhs: Self) -> RemoteFloat { lhs * RemoteFloat(rhs) }
  public static func / (lhs: Self, rhs: RemoteFloat) -> RemoteFloat { RemoteFloat(lhs) / rhs }
  public static func / (lhs: RemoteFloat, rhs: Self) -> RemoteFloat { lhs / RemoteFloat(rhs) }
  public static func + (lhs: Self, rhs: Self) -> RemoteFloat { RemoteFloat(lhs) + RemoteFloat(rhs) }
  public static func - (lhs: Self, rhs: Self) -> RemoteFloat { RemoteFloat(lhs) - RemoteFloat(rhs) }
  public static func * (lhs: Self, rhs: Self) -> RemoteFloat { RemoteFloat(lhs) * RemoteFloat(rhs) }
  public static func / (lhs: Self, rhs: Self) -> RemoteFloat { RemoteFloat(lhs) / RemoteFloat(rhs) }
  public static prefix func - (operand: Self) -> RemoteFloat { -RemoteFloat(operand) }

  /// The magnitude of the variable's live value.
  public var magnitude: RemoteFloat { RemoteFloat(self).magnitude }

  /// The variable's live value, clamped to `range`.
  public func clamped(to range: ClosedRange<Double>) -> RemoteFloat {
    RemoteFloat(self).clamped(to: range)
  }
}

/// Text the document holds: a constant, a ``RemoteVariable``, a number formatted as the
/// document plays, or those joined. Write it with string interpolation:
///
/// ```swift
/// let count = RemoteVariable("count", default: 3.0)
/// RemoteText("Items: \(count, decimals: 0)")
/// ```
public struct RemoteString: Hashable, Sendable, ExpressibleByStringInterpolation {
  indirect enum Node: Hashable, Sendable {
    case constant(String)
    case variable(name: String, defaultValue: String)
    case number(RemoteFloat.Node, integerDigits: Int, decimals: Int)
    case concatenation(Node, Node)
  }

  let node: Node

  init(node: Node) { self.node = node }

  public init(_ value: String) { node = .constant(value) }
  public init(stringLiteral value: String) { node = .constant(value) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<String>) {
    node = .variable(name: variable.name, defaultValue: variable.defaultValue)
  }

  /// `number` as text, with up to `integerDigits` digits before the point and exactly `decimals`
  /// after it (`1.50` for 1.5 at two decimals). The player formats it as the value changes.
  ///
  /// Both counts are 0 to 16; outside that, encoding fails with
  /// ``RemoteEncodingError/invalidNumberFormat(integerDigits:decimals:)``.
  public init(_ number: RemoteFloat, decimals: Int = 0, integerDigits: Int = 9) {
    node = .number(number.node, integerDigits: integerDigits, decimals: decimals)
  }

  public init(stringInterpolation: StringInterpolation) {
    self = stringInterpolation.parts.reduce(RemoteString("")) { $0 + $1 }
  }

  public static func + (lhs: RemoteString, rhs: RemoteString) -> RemoteString {
    switch (lhs.node, rhs.node) {
    case (.constant(let left), .constant(let right)): return RemoteString(left + right)
    case (.constant(""), _): return rhs
    case (_, .constant("")): return lhs
    // A constant after a concatenation that ends in one joins it, so `"a\(x)b"` + "c" stays three
    // parts rather than four.
    case (.concatenation(let head, .constant(let left)), .constant(let right)):
      return RemoteString(node: .concatenation(head, .constant(left + right)))
    default: return RemoteString(node: .concatenation(lhs.node, rhs.node))
    }
  }

  /// The pieces of an interpolated string, joined left to right.
  public struct StringInterpolation: StringInterpolationProtocol {
    var parts: [RemoteString] = []

    public init(literalCapacity: Int, interpolationCount: Int) {
      parts.reserveCapacity(2 * interpolationCount + 1)
    }

    public mutating func appendLiteral(_ literal: String) { parts.append(RemoteString(literal)) }

    public mutating func appendInterpolation(_ text: RemoteString) { parts.append(text) }

    public mutating func appendInterpolation(_ variable: RemoteVariable<String>) {
      parts.append(RemoteString(variable))
    }

    /// A number, formatted as ``RemoteString/init(_:decimals:integerDigits:)`` formats it.
    public mutating func appendInterpolation(
      _ number: RemoteFloat, decimals: Int = 0, integerDigits: Int = 9
    ) {
      parts.append(RemoteString(number, decimals: decimals, integerDigits: integerDigits))
    }

    public mutating func appendInterpolation(
      _ variable: RemoteVariable<Double>, decimals: Int = 0, integerDigits: Int = 9
    ) {
      parts.append(
        RemoteString(RemoteFloat(variable), decimals: decimals, integerDigits: integerDigits))
    }

    /// A Swift string, as a constant.
    public mutating func appendInterpolation(_ text: String) { parts.append(RemoteString(text)) }

    /// A Swift integer, as a constant.
    @_disfavoredOverload
    public mutating func appendInterpolation(_ value: some BinaryInteger) {
      parts.append(RemoteString(String(value)))
    }
  }
}

/// Whether a view is shown, as AndroidX's `Visibility` writes it.
public enum RemoteVisibility: Hashable, Sendable, RemoteVariableValue {
  /// Shown.
  case visible
  /// Hidden, keeping its space in the layout.
  case invisible
  /// Hidden, taking no space.
  case gone

  var wire: Int32 {
    switch self {
    case .visible: Int32(WireExpressionConstants.visibilityVisible)
    case .invisible: Int32(WireExpressionConstants.visibilityInvisible)
    case .gone: Int32(WireExpressionConstants.visibilityGone)
    }
  }
}

/// An axis of a canvas's size.
enum RemoteCanvasAxis: Hashable, Sendable {
  case width, height
}

import RcWireOperations

/// A type a ``RemoteVariable`` can hold: `Double`, `Int`, `Bool`, `String`, ``RemoteColorValue``,
/// ``RemoteVisibility`` and a ``RemoteStateValue`` enum.
public protocol RemoteVariableValue: Hashable, Sendable {
  /// The value as a host writes it to the variable while the document plays.
  var remoteHostValue: RemoteHostValue { get }
}

/// A value a host writes to a named variable, in the kind of slot the document declared for it:
/// what a player's `setFloat`, `setInteger`, `setString` or `setColor` takes.
public enum RemoteHostValue: Hashable, Sendable {
  case float(Float)
  case integer(Int)
  case string(String)
  /// An ARGB colour.
  case color(UInt32)
}

extension Double: RemoteVariableValue {
  public var remoteHostValue: RemoteHostValue { .float(Float(self)) }
}

extension Int: RemoteVariableValue {
  public var remoteHostValue: RemoteHostValue { .integer(self) }
}

extension String: RemoteVariableValue {
  public var remoteHostValue: RemoteHostValue { .string(self) }
}

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
    /// A scroll view's offset or how far it can scroll, in points.
    case scroll(RemoteScrollPosition, RemoteScrollPosition.Value)
    /// An integer read through its float view: the players keep each integer as a float as well.
    case integerView(RemoteInt.Node)
    case unary(Operator, Node)
    case binary(Operator, Node, Node)
  }

  /// The `FloatExpression` operators this API writes.
  enum Operator: Hashable, Sendable {
    case add, subtract, multiply, divide, modulo, minimum, maximum, absolute, sign

    var code: Int32 {
      switch self {
      case .add: FloatExpressionOperator.add
      case .subtract: FloatExpressionOperator.sub
      case .multiply: FloatExpressionOperator.mul
      case .divide: FloatExpressionOperator.div
      case .modulo: FloatExpressionOperator.mod
      case .minimum: FloatExpressionOperator.min
      case .maximum: FloatExpressionOperator.max
      case .absolute: FloatExpressionOperator.abs
      case .sign: FloatExpressionOperator.sign
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
  /// The remainder of dividing by `rhs`, as `truncatingRemainder(dividingBy:)`.
  public static func % (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteFloat {
    fold(.modulo, lhs, rhs) { $0.truncatingRemainder(dividingBy: $1) }
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

  /// -1, 0 or 1, as the value is negative, zero or positive.
  func sign() -> RemoteFloat {
    if let value = constant { return RemoteFloat(value > 0 ? 1 : value < 0 ? -1 : 0) }
    return RemoteFloat(node: .unary(.sign, node))
  }

  // Comparisons the player evaluates, as a ``RemoteBool``: `RemoteFloat(.hour) < 12`. Each is the
  // sign of the difference, read through its integer view. There is no `==` on floats, as there
  // is none a float's rounding makes reliable.

  public static func < (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteBool {
    if let left = lhs.constant, let right = rhs.constant { return RemoteBool(left < right) }
    // 1 when `rhs - lhs` is positive, else 0.
    return RemoteBool(int: RemoteInt(truncating: .max((rhs - lhs).sign(), 0)))
  }

  public static func > (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteBool { rhs < lhs }
  public static func <= (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteBool { !(lhs > rhs) }
  public static func >= (lhs: RemoteFloat, rhs: RemoteFloat) -> RemoteBool { !(lhs < rhs) }

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
    case .constant, .variable, .system, .declared, .scroll: false
    case .integerView(let integer): integer.readsCanvasSize
    case .unary(_, let operand): operand.readsCanvasSize
    case .binary(_, let lhs, let rhs): lhs.readsCanvasSize || rhs.readsCanvasSize
    }
  }
}

extension RemoteInt.Node {
  /// Whether the integer reads the size of the canvas being drawn, through a float it truncates.
  var readsCanvasSize: Bool {
    switch self {
    case .constant, .variable: false
    case .floatView(let float): float.readsCanvasSize
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

  public static func < (lhs: Self, rhs: RemoteFloat) -> RemoteBool { RemoteFloat(lhs) < rhs }
  public static func <= (lhs: Self, rhs: RemoteFloat) -> RemoteBool { RemoteFloat(lhs) <= rhs }
  public static func > (lhs: Self, rhs: RemoteFloat) -> RemoteBool { RemoteFloat(lhs) > rhs }
  public static func >= (lhs: Self, rhs: RemoteFloat) -> RemoteBool { RemoteFloat(lhs) >= rhs }

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
    case number(RemoteFloat.Node, integerDigits: Int, decimals: Int, zeroPadded: Bool)
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
  /// With `zeroPadded`, the integer part is padded with zeros to `integerDigits` (`07` for 7 at
  /// two digits), as a clock shows minutes.
  ///
  /// Both counts are 0 to 16; outside that, encoding fails with
  /// ``RemoteEncodingError/invalidNumberFormat(integerDigits:decimals:)``.
  public init(
    _ number: RemoteFloat, decimals: Int = 0, integerDigits: Int = 9, zeroPadded: Bool = false
  ) {
    node = .number(
      number.node, integerDigits: integerDigits, decimals: decimals, zeroPadded: zeroPadded)
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
      _ number: RemoteFloat, decimals: Int = 0, integerDigits: Int = 9, zeroPadded: Bool = false
    ) {
      parts.append(
        RemoteString(
          number, decimals: decimals, integerDigits: integerDigits, zeroPadded: zeroPadded))
    }

    public mutating func appendInterpolation(
      _ variable: RemoteVariable<Double>, decimals: Int = 0, integerDigits: Int = 9
    ) {
      parts.append(
        RemoteString(RemoteFloat(variable), decimals: decimals, integerDigits: integerDigits))
    }

    /// An integer, as its digits: `"\(page + 1) of \(count)"`.
    public mutating func appendInterpolation(_ number: RemoteInt) {
      // Ten digits: every Int32, so none is cut to the formatter's default nine.
      parts.append(RemoteString(RemoteFloat(number), decimals: 0, integerDigits: 10))
    }

    /// An `Int` variable's live value, as its digits.
    public mutating func appendInterpolation(_ variable: RemoteVariable<Int>) {
      appendInterpolation(RemoteInt(variable))
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

  public var remoteHostValue: RemoteHostValue { .integer(Int(wire)) }

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

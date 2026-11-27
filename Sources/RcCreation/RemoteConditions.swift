import RcWireOperations

extension Bool: RemoteVariableValue {}

/// An integer the document holds: a constant, a `RemoteVariable<Int>`, or arithmetic over those
/// that the player evaluates as it plays (`IntegerExpression`).
public struct RemoteInt: Hashable, Sendable, ExpressibleByIntegerLiteral {
  /// What a variable of an integer kind was declared as. The kind is part of a variable's identity:
  /// an `Int` variable and a `Bool` variable with one name are two variables, and a conflict.
  enum Declaration: Hashable, Sendable {
    case int(Int)
    case bool(Bool)
    case visibility(RemoteVisibility)
    /// A case of a ``RemoteStateValue`` enum, by type name and position.
    case state(type: String, ordinal: Int)
  }

  /// Values are `Int` while the document is written and checked against 32 bits when it is
  /// encoded (``RemoteEncodingError/integerOutOfRange(_:)``), so nothing clamps silently.
  indirect enum Node: Hashable, Sendable {
    case constant(Int)
    case variable(name: String, declaration: Declaration)
    case unary(Operator, Node)
    case binary(Operator, Node, Node)
  }

  /// The `IntegerExpression` operators this API writes.
  enum Operator: Hashable, Sendable {
    case add, subtract, multiply, divide, remainder, minimum, maximum, absolute, sign

    var code: Int32 {
      switch self {
      case .add: IntegerExpressionOperator.iAdd
      case .subtract: IntegerExpressionOperator.iSub
      case .multiply: IntegerExpressionOperator.iMul
      case .divide: IntegerExpressionOperator.iDiv
      case .remainder: IntegerExpressionOperator.iMod
      case .minimum: IntegerExpressionOperator.iMin
      case .maximum: IntegerExpressionOperator.iMax
      case .absolute: IntegerExpressionOperator.iAbs
      case .sign: IntegerExpressionOperator.iSign
      }
    }

    /// The operation on two constants, or `nil` where only the player decides it (a division by
    /// zero, or `Int.min / -1`), so the expression is written rather than folded.
    func evaluate(_ lhs: Int, _ rhs: Int) -> Int? {
      switch self {
      case .add: lhs &+ rhs
      case .subtract: lhs &- rhs
      case .multiply: lhs &* rhs
      case .divide:
        rhs == 0 ? nil : lhs.dividedReportingOverflow(by: rhs).overflow ? nil : lhs / rhs
      case .remainder:
        rhs == 0 ? nil : lhs.remainderReportingOverflow(dividingBy: rhs).overflow ? nil : lhs % rhs
      case .minimum: Swift.min(lhs, rhs)
      case .maximum: Swift.max(lhs, rhs)
      case .absolute: lhs == .min ? .max : Swift.abs(lhs)
      case .sign: lhs.signum()
      }
    }
  }

  let node: Node

  init(node: Node) { self.node = node }

  public init(_ value: Int) { node = .constant(value) }
  public init(integerLiteral value: Int) { self.init(value) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<Int>) {
    node = .variable(name: variable.name, declaration: .int(variable.defaultValue))
  }

  public static func + (lhs: RemoteInt, rhs: RemoteInt) -> RemoteInt { fold(.add, lhs, rhs) }
  public static func - (lhs: RemoteInt, rhs: RemoteInt) -> RemoteInt { fold(.subtract, lhs, rhs) }
  public static func * (lhs: RemoteInt, rhs: RemoteInt) -> RemoteInt { fold(.multiply, lhs, rhs) }

  /// Integer division, truncating toward zero. The player reads a division by zero as 0.
  public static func / (lhs: RemoteInt, rhs: RemoteInt) -> RemoteInt { fold(.divide, lhs, rhs) }

  /// The remainder of integer division, with the sign of `lhs`, as Swift's `%`. The player reads a
  /// remainder by zero as 0.
  public static func % (lhs: RemoteInt, rhs: RemoteInt) -> RemoteInt {
    fold(.remainder, lhs, rhs)
  }

  public static func min(_ lhs: RemoteInt, _ rhs: RemoteInt) -> RemoteInt {
    fold(.minimum, lhs, rhs)
  }
  public static func max(_ lhs: RemoteInt, _ rhs: RemoteInt) -> RemoteInt {
    fold(.maximum, lhs, rhs)
  }

  /// The magnitude of this value.
  public var magnitude: RemoteInt { unary(.absolute) }

  /// -1 when negative, 0 when zero, 1 when positive, as `Int.signum()`.
  public func signum() -> RemoteInt { unary(.sign) }

  private func unary(_ operation: Operator) -> RemoteInt {
    if case .constant(let value) = node, let result = operation.evaluate(value, 0) {
      return RemoteInt(node: .constant(result))
    }
    return RemoteInt(node: .unary(operation, node))
  }

  /// This value, clamped to `range`.
  public func clamped(to range: ClosedRange<Int>) -> RemoteInt {
    .min(.max(self, RemoteInt(range.lowerBound)), RemoteInt(range.upperBound))
  }

  public static func == (lhs: RemoteInt, rhs: RemoteInt) -> RemoteBool {
    !(lhs != rhs)
  }

  public static func != (lhs: RemoteInt, rhs: RemoteInt) -> RemoteBool {
    // 0 when equal, else 1: the magnitude of the difference's sign. The 32-bit difference wraps
    // to 0 only when the values are equal, and its sign is exact even at `Int32.min`, whose
    // magnitude is not.
    RemoteBool(int: (lhs - rhs).signum().magnitude)
  }

  // Ordering is the sign of the difference, so it holds while the difference fits in 32 bits:
  // values within ±1 073 741 823 of each other always compare correctly.

  public static func < (lhs: RemoteInt, rhs: RemoteInt) -> RemoteBool {
    // 1 when `rhs - lhs` is positive, else 0.
    RemoteBool(int: .max((rhs - lhs).signum(), 0))
  }

  public static func > (lhs: RemoteInt, rhs: RemoteInt) -> RemoteBool { rhs < lhs }
  public static func <= (lhs: RemoteInt, rhs: RemoteInt) -> RemoteBool { !(lhs > rhs) }
  public static func >= (lhs: RemoteInt, rhs: RemoteInt) -> RemoteBool { !(lhs < rhs) }

  private static func fold(_ operation: Operator, _ lhs: RemoteInt, _ rhs: RemoteInt) -> RemoteInt {
    if case .constant(let left) = lhs.node, case .constant(let right) = rhs.node,
      let result = operation.evaluate(left, right)
    {
      return RemoteInt(node: .constant(result))
    }
    return RemoteInt(node: .binary(operation, lhs.node, rhs.node))
  }
}

extension RemoteVariable where Value == Int {
  /// Compares the variable's live value: `selection == 2`.
  public static func == (lhs: Self, rhs: Int) -> RemoteBool { RemoteInt(lhs) == RemoteInt(rhs) }

  public static func != (lhs: Self, rhs: Int) -> RemoteBool { RemoteInt(lhs) != RemoteInt(rhs) }
  public static func < (lhs: Self, rhs: Int) -> RemoteBool { RemoteInt(lhs) < RemoteInt(rhs) }
  public static func <= (lhs: Self, rhs: Int) -> RemoteBool { RemoteInt(lhs) <= RemoteInt(rhs) }
  public static func > (lhs: Self, rhs: Int) -> RemoteBool { RemoteInt(lhs) > RemoteInt(rhs) }
  public static func >= (lhs: Self, rhs: Int) -> RemoteBool { RemoteInt(lhs) >= RemoteInt(rhs) }
}

/// A condition the player evaluates as it plays: a `RemoteVariable<Bool>`, a comparison of
/// integers, or `!`, `&&` and `||` over those. It is an integer, 1 or 0, on the wire.
///
/// ```swift
/// let isOn = RemoteVariable("wifi", default: true)
/// RemoteText("Connected").visible(if: RemoteBool(isOn))
/// ```
public struct RemoteBool: Hashable, Sendable, ExpressibleByBooleanLiteral {
  let int: RemoteInt

  init(int: RemoteInt) { self.int = int }

  public init(_ value: Bool) { int = RemoteInt(value ? 1 : 0) }
  public init(booleanLiteral value: Bool) { self.init(value) }

  /// The variable's live value.
  public init(_ variable: RemoteVariable<Bool>) {
    int = RemoteInt(
      node: .variable(name: variable.name, declaration: .bool(variable.defaultValue)))
  }

  public static prefix func ! (operand: RemoteBool) -> RemoteBool {
    RemoteBool(int: 1 - operand.int)
  }

  public static func && (lhs: RemoteBool, rhs: RemoteBool) -> RemoteBool {
    RemoteBool(int: .min(lhs.int, rhs.int))
  }

  public static func || (lhs: RemoteBool, rhs: RemoteBool) -> RemoteBool {
    RemoteBool(int: .max(lhs.int, rhs.int))
  }
}

extension RemoteVariable where Value == Bool {
  /// The variable's live value, negated: `!isOn`.
  public static prefix func ! (operand: Self) -> RemoteBool { !RemoteBool(operand) }
}

/// A value the player supplies as it plays: the clock, the calendar, the display.
public enum RemoteSystemValue: Hashable, Sendable {
  /// The hour of the day, 0 to 23.
  case hour
  /// Minutes since midnight: `hour * 60 + minute`.
  case minuteOfDay
  /// Seconds within the hour, whole: `minute * 60 + second`.
  case secondOfHour
  /// Seconds within the hour, with the fraction, for smooth motion.
  case continuousSecondOfHour
  /// The day of the month, 1 to 31.
  case dayOfMonth
  /// The month, 1 to 12.
  case month
  /// The ISO day of the week, 1 (Monday) to 7 (Sunday).
  case weekday
  /// Seconds since the document started playing.
  case animationTime
  /// The width and height of the window the document plays in, in pixels.
  case windowWidth, windowHeight

  var id: Int32 {
    switch self {
    case .hour: RemoteContextIds.idTimeInHr
    case .minuteOfDay: RemoteContextIds.idTimeInMin
    case .secondOfHour: RemoteContextIds.idTimeInSec
    case .continuousSecondOfHour: RemoteContextIds.idContinuousSec
    case .dayOfMonth: RemoteContextIds.idDayOfMonth
    case .month: RemoteContextIds.idCalendarMonth
    case .weekday: RemoteContextIds.idWeekDay
    case .animationTime: RemoteContextIds.idAnimationTime
    case .windowWidth: RemoteContextIds.idWindowWidth
    case .windowHeight: RemoteContextIds.idWindowHeight
    }
  }
}

extension RemoteFloat {
  /// A value the player supplies, live: `RemoteFloat(.hour)`.
  public init(_ value: RemoteSystemValue) { self.init(node: .system(value.id)) }
}

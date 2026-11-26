/// A float the document holds: a constant, a value the host writes by name, or arithmetic over
/// those that the player evaluates while it plays.
///
/// ```swift
/// let progress = RemoteFloat.named("progress", 0.25)
/// RemoteBox(modifier: .width(progress * 200).height(8).background(.blue))
/// ```
///
/// Float and integer literals convert, so `.width(48)` takes a constant.
public struct RemoteFloat: Hashable, Sendable, ExpressibleByFloatLiteral,
  ExpressibleByIntegerLiteral
{
  indirect enum Node: Hashable, Sendable {
    case constant(Float)
    case named(String, Float)
    case unary(Operator, Node)
    case binary(Operator, Node, Node)
  }

  /// AndroidX `AnimatedFloatExpression` operators this API writes.
  enum Operator: Int, Hashable, Sendable {
    case add = 1
    case subtract = 2
    case multiply = 3
    case divide = 4
    case modulo = 5
    case minimum = 6
    case maximum = 7
    case absolute = 10
    case floor = 14
    case round = 17
    case ceil = 31
  }

  let node: Node

  init(node: Node) { self.node = node }

  public init(_ value: Float) { node = .constant(value) }
  public init(floatLiteral value: Float) { node = .constant(value) }
  public init(integerLiteral value: Int) { node = .constant(Float(value)) }

  /// A float the host can write by [name], [initial] until it does.
  public static func named(_ name: String, _ initial: Float) -> RemoteFloat {
    RemoteFloat(node: .named(name, initial))
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
  public static prefix func - (operand: RemoteFloat) -> RemoteFloat { RemoteFloat(-1) * operand }

  public static func min(_ lhs: RemoteFloat, _ rhs: RemoteFloat) -> RemoteFloat {
    fold(.minimum, lhs, rhs, Swift.min)
  }
  public static func max(_ lhs: RemoteFloat, _ rhs: RemoteFloat) -> RemoteFloat {
    fold(.maximum, lhs, rhs, Swift.max)
  }

  /// The magnitude of this value.
  public var absoluteValue: RemoteFloat {
    if case .constant(let value) = node { return RemoteFloat(Swift.abs(value)) }
    return RemoteFloat(node: .unary(.absolute, node))
  }

  /// This value clamped to [range].
  public func clamped(to range: ClosedRange<Float>) -> RemoteFloat {
    .min(.max(self, RemoteFloat(range.lowerBound)), RemoteFloat(range.upperBound))
  }

  /// Two constants are folded here, as a compiler would, so they cost the document nothing.
  private static func fold(
    _ operation: Operator, _ lhs: RemoteFloat, _ rhs: RemoteFloat,
    _ evaluate: (Float, Float) -> Float
  ) -> RemoteFloat {
    if case .constant(let left) = lhs.node, case .constant(let right) = rhs.node {
      return RemoteFloat(evaluate(left, right))
    }
    return RemoteFloat(node: .binary(operation, lhs.node, rhs.node))
  }
}

/// An integer the document holds: a constant or a value the host writes by name.
public struct RemoteInt: Hashable, Sendable, ExpressibleByIntegerLiteral {
  enum Node: Hashable, Sendable {
    case constant(Int)
    case named(String, Int)
  }

  let node: Node

  public init(_ value: Int) { node = .constant(value) }
  public init(integerLiteral value: Int) { node = .constant(value) }

  init(node: Node) { self.node = node }

  /// An integer the host can write by [name], [initial] until it does.
  public static func named(_ name: String, _ initial: Int) -> RemoteInt {
    RemoteInt(node: .named(name, initial))
  }
}

/// A text the document holds: a constant, a value the host writes by name, a number formatted
/// as the document plays, or two of those joined.
///
/// ```swift
/// let count = RemoteFloat.named("count", 3)
/// RemoteText("Items: " + RemoteString(count, decimals: 0))
/// ```
public struct RemoteString: Hashable, Sendable, ExpressibleByStringLiteral {
  indirect enum Node: Hashable, Sendable {
    case constant(String)
    case named(String, String)
    case number(RemoteFloat, before: Int, after: Int, flags: Int)
    case concatenation(Node, Node)
  }

  let node: Node

  init(node: Node) { self.node = node }

  public init(_ value: String) { node = .constant(value) }
  public init(stringLiteral value: String) { node = .constant(value) }

  /// [number] as text with up to [integerDigits] digits before the point and exactly [decimals]
  /// after it, written by `TextFromFloat` so it follows the value as it changes.
  public init(_ number: RemoteFloat, integerDigits: Int = 9, decimals: Int = 0) {
    precondition((0...16).contains(integerDigits) && (0...16).contains(decimals))
    // PAD_PRE_NONE | PAD_AFTER_NONE: no padding either side, as `String(format:)` would print it.
    node = .number(number, before: integerDigits, after: decimals, flags: 4 | 1)
  }

  /// A text the host can write by [name], [initial] until it does.
  public static func named(_ name: String, _ initial: String) -> RemoteString {
    RemoteString(node: .named(name, initial))
  }

  public static func + (lhs: RemoteString, rhs: RemoteString) -> RemoteString {
    if case .constant(let left) = lhs.node, case .constant(let right) = rhs.node {
      return RemoteString(left + right)
    }
    return RemoteString(node: .concatenation(lhs.node, rhs.node))
  }
}

/// A colour: a literal ARGB value or a colour the host writes by name.
public struct RemoteColor: Hashable, Sendable {
  enum Storage: Hashable, Sendable {
    case argb(UInt32)
    case named(String, UInt32)
  }

  let storage: Storage

  /// A colour from its packed `0xAARRGGBB` value.
  public init(argb: UInt32) { storage = .argb(argb) }

  /// A colour from components in 0...1.
  public init(red: Float, green: Float, blue: Float, alpha: Float = 1) {
    func channel(_ value: Float) -> UInt32 {
      UInt32((Swift.min(Swift.max(value, 0), 1) * 255).rounded())
    }
    storage = .argb(
      channel(alpha) << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue))
  }

  /// A colour from a `0xRRGGBB` value, opaque.
  public init(rgb: UInt32) { storage = .argb(0xFF00_0000 | (rgb & 0x00FF_FFFF)) }

  /// A colour the host can write by [name], [initial] until it does.
  public static func named(_ name: String, _ initial: RemoteColor) -> RemoteColor {
    guard case .argb(let argb) = initial.storage else {
      preconditionFailure("a named colour's default must be a literal colour")
    }
    return RemoteColor(storage: .named(name, argb))
  }

  init(storage: Storage) { self.storage = storage }

  /// This colour with its alpha replaced; a literal colour only.
  public func opacity(_ alpha: Float) -> RemoteColor {
    guard case .argb(let argb) = storage else {
      preconditionFailure("opacity(_:) applies to a literal colour")
    }
    let channel = UInt32((Swift.min(Swift.max(alpha, 0), 1) * 255).rounded())
    return RemoteColor(argb: channel << 24 | (argb & 0x00FF_FFFF))
  }

  var literalARGB: UInt32? {
    if case .argb(let argb) = storage { return argb }
    return nil
  }

  public static let black = RemoteColor(argb: 0xFF00_0000)
  public static let white = RemoteColor(argb: 0xFFFF_FFFF)
  public static let clear = RemoteColor(argb: 0x0000_0000)
  public static let gray = RemoteColor(argb: 0xFF88_8888)
  public static let lightGray = RemoteColor(argb: 0xFFCC_CCCC)
  public static let darkGray = RemoteColor(argb: 0xFF44_4444)
  public static let red = RemoteColor(argb: 0xFFFF_0000)
  public static let green = RemoteColor(argb: 0xFF00_FF00)
  public static let blue = RemoteColor(argb: 0xFF00_00FF)
  public static let yellow = RemoteColor(argb: 0xFFFF_FF00)
  public static let cyan = RemoteColor(argb: 0xFF00_FFFF)
  public static let magenta = RemoteColor(argb: 0xFFFF_00FF)
}

/// Values for ``RemoteModifier/visibility(_:)``, as AndroidX's `Visibility` writes them.
public enum RemoteVisibility {
  public static let gone = 0
  public static let visible = 1
  public static let invisible = 2
}

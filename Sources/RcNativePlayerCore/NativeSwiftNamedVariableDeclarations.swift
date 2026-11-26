import Foundation

/// The named variables a document declares, and the rule a host name and value must meet to be
/// written to one.
///
/// A session's named setters apply exactly these checks, so a host that validates a write against
/// a document's declarations before the document's session exists gets the answer the session will
/// give once it does.
public struct NativeSwiftNamedVariableDeclarations: Sendable {
  let variables: [String: ParsedNamedVariable]

  init(variables: [String: ParsedNamedVariable]) {
    self.variables = variables
  }

  /// No declarations: every host write is refused.
  public static let none = NativeSwiftNamedVariableDeclarations(variables: [:])

  /// The declarations of the document in `data`. Throws whatever opening a session on `data` would.
  public static func declared(in data: Data) throws -> NativeSwiftNamedVariableDeclarations {
    NativeSwiftNamedVariableDeclarations(
      variables: try NativeSwiftDocumentDecoder.decode(data).namedVariables)
  }

  /// Every declared name, as the document spells it, sorted.
  public var names: [String] { variables.keys.sorted() }

  /// The declared name a host name addresses, resolved as the Kotlin player resolves one: a name
  /// with a namespace as written, otherwise the same name in `USER:` — which the authoring API
  /// leaves off — when the document declares it there, otherwise the bare name. Nil when the
  /// document declares none of them.
  public func declaredName(for name: String) -> String? {
    let user = "USER:\(name)"
    let resolved = name.contains(":") || variables[user] == nil ? name : user
    return variables[resolved] != nil ? resolved : nil
  }

  /// Whether `value` may be written to the float variable `name`: finite, and `name` is declared
  /// as a float.
  public func acceptsFloat(_ value: Float, for name: String) -> Bool {
    accepts(.float(value), for: name)
  }

  /// Whether `value` may be written to the string variable `name`: no longer than a document
  /// string may be, and `name` is declared as a string.
  public func acceptsString(_ value: String, for name: String) -> Bool {
    accepts(.text(value), for: name)
  }

  /// Whether `name` is declared as a color.
  public func acceptsColor(for name: String) -> Bool {
    accepts(.color(0), for: name)
  }

  /// Whether `name` is declared as an integer — which is also how a document declares a boolean,
  /// holding 1 or 0.
  public func acceptsInteger(for name: String) -> Bool {
    accepts(.integer(0), for: name)
  }

  /// Whether `name` is declared as a long.
  public func acceptsLong(for name: String) -> Bool {
    accepts(.long(0), for: name)
  }

  /// Whether `values` may be written to the float-array variable `name`: every value finite, no
  /// more of them than a document's float list may hold, and `name` is declared as a float array.
  public func acceptsFloatArray(_ values: [Float], for name: String) -> Bool {
    accepts(.floatArray(values), for: name)
  }

  @_spi(RemoteComposeController)
  public func accepts(_ value: NativeSwiftNamedValue, for name: String) -> Bool {
    variable(for: name).map { Self.accepts(value, by: $0) } ?? false
  }

  /// The rule every named write meets, whoever names the variable: the value is of the declared
  /// type, a float (either view of an integer included, and each value of a float array) is
  /// finite, a string is no longer than a document string may be, and a float array is no longer
  /// than a document's float list may be.
  static func accepts(_ value: NativeSwiftNamedValue, by variable: ParsedNamedVariable) -> Bool {
    switch value {
    case .float(let value):
      variable.type == NativeSwiftNamedVariableType.float && value.isFinite
    case .text(let value):
      variable.type == NativeSwiftNamedVariableType.string
        && value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes
    case .color:
      variable.type == NativeSwiftNamedVariableType.color
    case .integer(_, let float):
      variable.type == NativeSwiftNamedVariableType.int && float?.isFinite != false
    case .long:
      variable.type == NativeSwiftNamedVariableType.long
    case .floatArray(let values):
      variable.type == NativeSwiftNamedVariableType.floatArray && isWritableFloatArray(values)
    }
  }

  /// The rule a float array a host writes meets, whatever variable it is for: no more values than
  /// a document's float list may hold (AndroidX `DataListFloat.MAX_FLOAT_ARRAY`, 2000), each one
  /// finite. The one Swift owner of this rule: the native session and the controller's native
  /// backend read it. (The Kotlin backend counts against `maxFloatArrayValues` instead and leaves
  /// the rest to Kotlin's `RcFloatList.canHold`.) The count is checked first, so an oversized
  /// array is refused without reading its values.
  public static func isWritableFloatArray(_ values: [Float]) -> Bool {
    values.count <= NativeSwiftDocumentDecoder.maximumFloatListValues
      && values.allSatisfy(\.isFinite)
  }

  func variable(for name: String) -> ParsedNamedVariable? {
    declaredName(for: name).flatMap { variables[$0] }
  }
}

/// A named variable's value: the kinds a host can write, as a session reads and restores them
/// (``NativeSwiftDocumentSession/writtenNamedValues()``). A boolean is an `.integer` 0 or 1, as on
/// the wire.
///
/// One of three value enums with mappers between them, kept apart because merging them would change
/// a public API: this one is the core's SPI type and alone carries an integer's float view
/// (`.integer(_:float:)`), which only a session restores; `RemoteComposeNativeNamedValue` is
/// `RcNativePlayerUIKit`'s public host type; and the SwiftUI overlay's `ControllerNamedValue` keeps
/// a boolean apart and compiles where no native player is linked.
@_spi(RemoteComposeController)
public enum NativeSwiftNamedValue: Equatable, Sendable {
  case text(String)
  case float(Float)
  /// `float` is the float view a float write gave the same id (the two views share ids), which the
  /// integer holds only truncated; nil when no float write reached it. A host writes nil.
  case integer(Int, float: Float? = nil)
  /// ARGB, as the document stores a colour.
  case color(UInt32)
  /// A long — an epoch-millisecond instant, as the document's `LongConstant` holds one.
  case long(Int64)
  /// A float array, which replaces the list the document keeps at that name's id.
  case floatArray([Float])
}

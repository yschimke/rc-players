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
    value.isFinite && type(of: name) == NativeSwiftNamedVariableType.float
  }

  /// Whether `value` may be written to the string variable `name`: no longer than a document
  /// string may be, and `name` is declared as a string.
  public func acceptsString(_ value: String, for name: String) -> Bool {
    value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes
      && type(of: name) == NativeSwiftNamedVariableType.string
  }

  /// Whether `name` is declared as a color.
  public func acceptsColor(for name: String) -> Bool {
    type(of: name) == NativeSwiftNamedVariableType.color
  }

  /// Whether `name` is declared as an integer — which is also how a document declares a boolean,
  /// holding 1 or 0.
  public func acceptsInteger(for name: String) -> Bool {
    type(of: name) == NativeSwiftNamedVariableType.int
  }

  func variable(for name: String) -> ParsedNamedVariable? {
    declaredName(for: name).flatMap { variables[$0] }
  }

  private func type(of name: String) -> Int? { variable(for: name)?.type }
}

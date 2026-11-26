/// The state of one document being written: ids, the declaration section, the body, and the layout
/// a component is being written into.
///
/// Values a component reads (text, named state, expressions, colours) are declared in their own
/// section, written between the header and the root, so every id is defined before it is read and
/// each distinct value is declared once however many components read it.
final class RemoteCreationContext {
  /// Pixels per dp. Every dimension the API takes is in dp (sp for text) and is written in pixels at
  /// this density, which the header records as the density the document was generated at.
  let density: Float

  private(set) var declarations = RemoteByteBuffer()
  var body = RemoteByteBuffer()

  /// `RemoteComposeState.START_ID`: ids below it are system variables.
  private var nextDataID = 42
  private var nextComponentID = -1

  private var textIDs: [String: Int] = [:]
  private var floatIDs: [RemoteFloat.Node: Int] = [:]
  private var stringIDs: [RemoteString.Node: Int] = [:]
  private var intIDs: [RemoteInt.Node: Int] = [:]
  private var colorIDs: [String: (id: Int, defaultARGB: UInt32)] = [:]
  private var namedTypes: [String: Int] = [:]

  /// The layouts enclosing the component being written, innermost last.
  private(set) var parents: [RemoteParentKind] = []

  init(density: Float) {
    precondition(density > 0 && density.isFinite, "density must be a positive number")
    self.density = density
  }

  func px(_ dp: Float) -> Float { dp * density }

  func newComponentID() -> Int {
    nextComponentID -= 1
    return nextComponentID
  }

  private func newDataID() -> Int {
    defer { nextDataID += 1 }
    return nextDataID
  }

  func withParent(_ kind: RemoteParentKind, _ body: () -> Void) {
    parents.append(kind)
    body()
    parents.removeLast()
  }

  // MARK: - Declarations

  /// The id of the text constant [value], declared on first use.
  func textID(_ value: String) -> Int {
    if let id = textIDs[value] { return id }
    let id = newDataID()
    declarations.byte(RemoteOpcode.dataText)
    declarations.int(id)
    declarations.utf8(value)
    textIDs[value] = id
    return id
  }

  private func declareName(_ name: String, id: Int, type: Int) {
    if let existing = namedTypes[name] {
      precondition(
        existing == type,
        "the name \"\(name)\" is already declared as a value of another type")
      return
    }
    namedTypes[name] = type
    declarations.byte(RemoteOpcode.namedVariable)
    declarations.int(id)
    declarations.int(type)
    declarations.utf8(name)
  }

  /// The wire word for [value]: the literal itself for a constant, else the NaN-boxed id of the
  /// variable or expression that computes it.
  func floatWord(_ value: RemoteFloat) -> Float {
    if case .constant(let literal) = value.node { return literal }
    return RemoteNaN.id(floatID(value.node))
  }

  /// [value] in pixels: a dp constant is scaled here, anything computed is scaled by the document.
  func pxWord(_ value: RemoteFloat) -> Float {
    if case .constant(let literal) = value.node { return px(literal) }
    if density == 1 { return floatWord(value) }
    return floatWord(value * RemoteFloat(density))
  }

  /// The id of a `FloatConstant` holding [value], for a reader that takes an id, not a word.
  func declareFloatConstant(_ value: Float) -> Int { floatID(.constant(value)) }

  private func floatID(_ node: RemoteFloat.Node) -> Int {
    if let id = floatIDs[node] { return id }
    let id: Int
    switch node {
    case .constant(let literal):
      id = newDataID()
      declarations.byte(RemoteOpcode.dataFloat)
      declarations.int(id)
      declarations.float(literal)
    case .named(let name, let initial):
      id = newDataID()
      declarations.byte(RemoteOpcode.dataFloat)
      declarations.int(id)
      declarations.float(initial)
      declareName(name, id: id, type: RemoteNamedVariableType.float)
    case .unary, .binary:
      var words: [Float] = []
      appendRPN(node, to: &words)
      precondition(words.count <= 32, "a float expression is limited to 32 terms")
      id = newDataID()
      declarations.byte(RemoteOpcode.animatedFloat)
      declarations.int(id)
      declarations.int(words.count)
      for word in words { declarations.float(word) }
    }
    floatIDs[node] = id
    return id
  }

  /// Flattens nested arithmetic into one reverse-Polish expression, as AndroidX's writer does.
  private func appendRPN(_ node: RemoteFloat.Node, to words: inout [Float]) {
    switch node {
    case .constant(let literal): words.append(literal)
    case .named: words.append(RemoteNaN.id(floatID(node)))
    case .unary(let operation, let operand):
      appendRPN(operand, to: &words)
      words.append(RemoteNaN.operation(operation.rawValue))
    case .binary(let operation, let lhs, let rhs):
      appendRPN(lhs, to: &words)
      appendRPN(rhs, to: &words)
      words.append(RemoteNaN.operation(operation.rawValue))
    }
  }

  /// The id of the text [value] reads.
  func stringID(_ value: RemoteString) -> Int {
    if case .constant(let literal) = value.node { return textID(literal) }
    if let id = stringIDs[value.node] { return id }
    let id: Int
    switch value.node {
    case .constant(let literal):
      return textID(literal)
    case .named(let name, let initial):
      // A named text needs a definition of its own: a constant shared with another component
      // would change both when the host writes the name.
      id = newDataID()
      declarations.byte(RemoteOpcode.dataText)
      declarations.int(id)
      declarations.utf8(initial)
      declareName(name, id: id, type: RemoteNamedVariableType.string)
    case .number(let number, let before, let after, let flags):
      let word = floatWord(number)
      id = newDataID()
      declarations.byte(RemoteOpcode.textFromFloat)
      declarations.int(id)
      declarations.float(word)
      declarations.short(before)
      declarations.short(after)
      declarations.int(flags)
    case .concatenation(let lhs, let rhs):
      let first = stringID(RemoteString(node: lhs))
      let second = stringID(RemoteString(node: rhs))
      id = newDataID()
      declarations.byte(RemoteOpcode.textMerge)
      declarations.int(id)
      declarations.int(first)
      declarations.int(second)
    }
    stringIDs[value.node] = id
    return id
  }

  /// The id of the integer [value] reads.
  func intID(_ value: RemoteInt) -> Int {
    if let id = intIDs[value.node] { return id }
    let id = newDataID()
    declarations.byte(RemoteOpcode.dataInt)
    declarations.int(id)
    switch value.node {
    case .constant(let literal):
      declarations.int(literal)
    case .named(let name, let initial):
      declarations.int(initial)
      declareName(name, id: id, type: RemoteNamedVariableType.int)
    }
    intIDs[value.node] = id
    return id
  }

  /// The colour [value] as a literal ARGB, or the id of the named colour it reads.
  func color(_ value: RemoteColor) -> RemoteResolvedColor {
    switch value.storage {
    case .argb(let argb): return .literal(argb)
    case .named(let name, let initial):
      if let existing = colorIDs[name] {
        precondition(
          existing.defaultARGB == initial,
          "the colour \"\(name)\" is declared twice with different defaults")
        return .id(existing.id)
      }
      let id = newDataID()
      declarations.byte(RemoteOpcode.colorConstant)
      declarations.int(id)
      declarations.int(Int(Int32(bitPattern: initial)))
      declareName(name, id: id, type: RemoteNamedVariableType.color)
      colorIDs[name] = (id, initial)
      return .id(id)
    }
  }

  // MARK: - Body

  func op(_ opcode: Int) { body.byte(opcode) }

  func containerEnd() { body.byte(RemoteOpcode.containerEnd) }

  /// `LayoutContent`: where a component's modifiers end and its children begin.
  func contentSection() {
    op(RemoteOpcode.layoutContent)
    body.int(newComponentID())
  }
}

/// The layout a component is a child of, for modifiers whose meaning depends on it (`weight`).
enum RemoteParentKind {
  case root, box, row, column
}

enum RemoteResolvedColor {
  case literal(UInt32)
  case id(Int)
}

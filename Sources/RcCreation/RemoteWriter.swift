import RcWireOperations

/// The state of one document being encoded. Views write themselves through it; it is public only
/// only within this package.
///
/// Values a view reads (text, variables, expressions, colours) are declared in their own list,
/// written between the header and the root, so every id is defined before it is read and each
/// distinct value is declared once however many views read it.
package final class RemoteWriter {
  /// Pixels per point.
  let displayScale: Float

  private(set) var declarations: [WireOperation] = []
  private(set) var body: [WireOperation] = []

  private var nextDataID = IdEncoding.startId
  private var nextComponentID: Int32 = 0

  private var textIDs: [String: Int32] = [:]
  private var floatIDs: [RemoteFloat.Node: Int32] = [:]
  private var stringIDs: [RemoteString.Node: Int32] = [:]
  private var intIDs: [Int32: Int32] = [:]
  private var colorIDs: [RemoteColor.Storage: Int32] = [:]
  /// Each variable's declared id, by name, and the type and value it was declared with.
  private var variables: [String: (id: Int32, declaration: VariableDeclaration)] = [:]

  /// What a variable was declared as: its kind and its default. Two uses of one name must agree on
  /// both, so an `Int` 1 and a `Double` 1 are different declarations.
  enum VariableDeclaration: Hashable {
    case float(Float)
    case string(String)
    case int(Int)
    case visibility(RemoteVisibility)
    case bool(Bool)
    case state(type: String, ordinal: Int)
    case color(RemoteColorValue)
  }

  /// The environment the view being written reads.
  private(set) var environment = RemoteEnvironmentValues()

  /// Runs `body` with `transform` applied to the environment.
  func withEnvironment(
    _ transform: (inout RemoteEnvironmentValues) -> Void,
    _ body: () throws(RemoteEncodingError) -> Void
  ) throws(RemoteEncodingError) {
    let saved = environment
    transform(&environment)
    defer { environment = saved }
    try body()
  }

  /// The modifiers the next built-in view applies, outermost first.
  private var pendingModifiers: [RemoteModifierElement] = []
  /// The layouts enclosing the view being written, innermost last.
  private var parents: [ParentKind] = []
  /// The size of the canvas being drawn, as the ids of its `ComponentValue`s.
  private var canvasSize: (width: Int32, height: Int32)?

  enum ParentKind {
    case root, horizontalStack, verticalStack, zStack, leaf
  }

  init(displayScale: Float) { self.displayScale = displayScale }

  func newComponentID() -> Int32 {
    nextComponentID -= 1
    return nextComponentID
  }

  private func newDataID() -> Int32 {
    defer { nextDataID += 1 }
    return nextDataID
  }

  private func declare(_ operation: WireOperation) { declarations.append(operation) }

  private func emit(_ operation: WireOperation) { body.append(operation) }

  // MARK: - Units

  /// `points` in pixels, refused if it is not finite: a NaN on the wire reads as a variable id.
  func pixels(_ points: Double) throws(RemoteEncodingError) -> Float {
    try Self.finite(Float(points) * displayScale)
  }

  /// `value`, refused if it is a NaN or an infinity. A NaN word on the wire is a reference to a
  /// variable, so a NaN constant would silently read some other value.
  /// `value` as a wire integer, refused if it does not fit in 32 bits.
  static func int32(_ value: Int) throws(RemoteEncodingError) -> Int32 {
    guard let narrowed = Int32(exactly: value) else {
      throw RemoteEncodingError.integerOutOfRange(value)
    }
    return narrowed
  }

  static func finite(_ value: Float) throws(RemoteEncodingError) -> Float {
    guard value.isFinite else { throw RemoteEncodingError.nonFiniteValue }
    return value
  }

  /// `points` in pixels: a constant is scaled here, anything computed by the document.
  func pixels(_ points: RemoteFloat) -> RemoteFloat {
    // A canvas's size is pixels to begin with.
    if case .canvasSize(let axis) = points.node { return RemoteFloat(node: .canvasPixels(axis)) }
    if displayScale == 1 { return points }
    return points * RemoteFloat(displayScale)
  }

  // MARK: - Declarations

  /// The NaN word that refers to the value with `id`.
  static func reference(_ id: Int32) -> Float {
    Float(
      bitPattern: UInt32(bitPattern: IdEncoding.nanPrefix)
        | (UInt32(bitPattern: id) & UInt32(bitPattern: IdEncoding.payloadMask)))
  }

  /// The id of the text constant `value`, declared on first use.
  func textID(_ value: String) -> Int32 {
    if let id = textIDs[value] { return id }
    let id = newDataID()
    declare(.textData(TextData(textId: id, text: value)))
    textIDs[value] = id
    return id
  }

  /// The id of `name`, declared by `declaration` the first time. A name is one variable: a second
  /// use must declare it the same way, or a host write could reach only one of them.
  private func variableID(
    _ name: String, _ declaration: VariableDeclaration, type: Int32,
    declare declareValue: (Int32) -> Void
  ) throws(RemoteEncodingError) -> Int32 {
    if let existing = variables[name] {
      guard existing.declaration == declaration else {
        throw RemoteEncodingError.conflictingVariable(name: name)
      }
      return existing.id
    }
    let id = newDataID()
    declareValue(id)
    declare(.namedVariable(NamedVariable(varId: id, varType: type, name: name)))
    variables[name] = (id, declaration)
    return id
  }

  /// The wire word for `value`: the literal itself for a constant, else a reference to the
  /// variable or expression that computes it.
  func floatWord(_ value: RemoteFloat) throws(RemoteEncodingError) -> Float {
    if let literal = value.constant { return try Self.finite(literal) }
    return Self.reference(try floatID(value.node))
  }

  /// `value` in pixels, as a wire word.
  func pixelWord(_ value: RemoteFloat) throws(RemoteEncodingError) -> Float {
    try floatWord(pixels(value))
  }

  /// The id of a float holding `node`, for a field that takes an id rather than a word.
  func floatID(_ node: RemoteFloat.Node) throws(RemoteEncodingError) -> Int32 {
    // A canvas size is this canvas's: resolved before the cache, so another canvas's expression
    // is never reused for it.
    let node = node.readsCanvasSize ? try resolvingCanvasSize(node) : node
    if let id = floatIDs[node] { return id }
    let id: Int32
    switch node {
    case .declared(let declared), .system(let declared):
      return declared
    case .canvasSize, .canvasPixels:
      throw RemoteEncodingError.canvasSizeOutsideCanvas
    case .integerView(let integer):
      // The integer's own id: the players publish its value as a float too. A float read as an
      // integer shares the float's id, so it is copied by an `IntegerExpression` first, whose
      // output holds the truncated value.
      if case .floatView = integer {
        id = try intID(.binary(.add, integer, .constant(0)))
      } else {
        id = try intID(integer)
      }
    case .constant(let literal):
      id = newDataID()
      declare(.floatConstant(FloatConstant(id: id, value: literal)))
    case .variable(let name, let defaultValue):
      id = try variableID(name, .float(defaultValue), type: NamedVariable.float) { id in
        declare(.floatConstant(FloatConstant(id: id, value: defaultValue)))
      }
    case .unary, .binary:
      var words: [Float] = []
      try appendRPN(node, to: &words)
      guard words.count <= WireLimits.maxExpressionSize else {
        throw RemoteEncodingError.expressionTooLong(
          terms: words.count, limit: WireLimits.maxExpressionSize)
      }
      id = newDataID()
      declare(.floatExpression(FloatExpression(id: id, expression: words)))
    }
    floatIDs[node] = id
    return id
  }

  /// Flattens nested arithmetic into one reverse-Polish expression, as AndroidX's writer does.
  private func appendRPN(_ node: RemoteFloat.Node, to words: inout [Float])
    throws(RemoteEncodingError)
  {
    switch node {
    case .constant(let literal): words.append(try Self.finite(literal))
    case .variable, .declared, .system, .canvasSize, .canvasPixels, .integerView:
      words.append(Self.reference(try floatID(node)))
    case .unary(let operation, let operand):
      try appendRPN(operand, to: &words)
      words.append(Self.operatorWord(operation))
    case .binary(let operation, let lhs, let rhs):
      try appendRPN(lhs, to: &words)
      try appendRPN(rhs, to: &words)
      words.append(Self.operatorWord(operation))
    }
  }

  /// `node` with the canvas being drawn's size in place of each read of it: its
  /// `ComponentValue`s, which are pixels, over the scale where the reader wanted points.
  private func resolvingCanvasSize(_ node: RemoteFloat.Node) throws(RemoteEncodingError)
    -> RemoteFloat.Node
  {
    func axisID(_ axis: RemoteCanvasAxis) throws(RemoteEncodingError) -> Int32 {
      guard let canvasSize else { throw RemoteEncodingError.canvasSizeOutsideCanvas }
      return axis == .width ? canvasSize.width : canvasSize.height
    }
    switch node {
    case .canvasPixels(let axis): return .declared(try axisID(axis))
    case .canvasSize(let axis):
      return .binary(.divide, .declared(try axisID(axis)), .constant(displayScale))
    case .constant, .variable, .system, .declared, .integerView: return node
    case .unary(let operation, let operand):
      return .unary(operation, try resolvingCanvasSize(operand))
    case .binary(let operation, let lhs, let rhs):
      return .binary(operation, try resolvingCanvasSize(lhs), try resolvingCanvasSize(rhs))
    }
  }

  private static func operatorWord(_ operation: RemoteFloat.Operator) -> Float {
    reference(FloatExpressionOperator.offset + operation.code)
  }

  /// The id of the text `value` reads.
  func stringID(_ value: RemoteString) throws(RemoteEncodingError) -> Int32 {
    if case .constant(let literal) = value.node { return textID(literal) }
    if let id = stringIDs[value.node] { return id }
    let id: Int32
    switch value.node {
    case .constant(let literal):
      return textID(literal)
    case .variable(let name, let defaultValue):
      // A variable's text needs a definition of its own: a constant shared with another view
      // would change both when the host writes the name.
      id = try variableID(name, .string(defaultValue), type: NamedVariable.string) { id in
        declare(.textData(TextData(textId: id, text: defaultValue)))
      }
    case .number(let number, let integerDigits, let decimals, let zeroPadded):
      guard (0...16).contains(integerDigits), (0...16).contains(decimals) else {
        throw RemoteEncodingError.invalidNumberFormat(
          integerDigits: integerDigits, decimals: decimals)
      }
      let word = try floatWord(RemoteFloat(node: number))
      id = newDataID()
      // Zeros after the point, so the fraction always has `decimals` digits, as
      // `String(format: "%.2f")` prints it; before it, only when asked (a clock's `07`).
      declare(
        .textFromFloat(
          TextFromFloat(
            textId: id, value: word, digitsBefore: Int16(integerDigits),
            digitsAfter: Int16(decimals),
            flags: (zeroPadded ? TextFromFloat.padPreZero : TextFromFloat.padPreNone)
              | TextFromFloat.padAfterZero)))
    case .concatenation(let lhs, let rhs):
      let first = try stringID(RemoteString(node: lhs))
      let second = try stringID(RemoteString(node: rhs))
      id = newDataID()
      declare(.textMerge(TextMerge(textId: id, srcId1: first, srcId2: second)))
    }
    stringIDs[value.node] = id
    return id
  }

  /// The id of an integer constant holding `value`.
  func intID(_ value: Int32) -> Int32 {
    if let id = intIDs[value] { return id }
    let id = newDataID()
    declare(.integerConstant(IntegerConstant(id: id, value: value)))
    intIDs[value] = id
    return id
  }

  private var intNodeIDs: [RemoteInt.Node: Int32] = [:]

  /// The id of an integer holding `node`: a constant, a variable, or an `IntegerExpression`.
  func intID(_ node: RemoteInt.Node) throws(RemoteEncodingError) -> Int32 {
    if case .constant(let value) = node { return intID(try Self.int32(value)) }
    if let id = intNodeIDs[node] { return id }
    let id: Int32
    switch node {
    case .constant(let value):
      return intID(try Self.int32(value))
    case .variable(let name, let declaration):
      let (value, declared): (Int32, VariableDeclaration)
      switch declaration {
      case .int(let int): (value, declared) = (try Self.int32(int), .int(int))
      case .bool(let bool): (value, declared) = (bool ? 1 : 0, .bool(bool))
      case .state(let type, let ordinal):
        (value, declared) = (try Self.int32(ordinal), .state(type: type, ordinal: ordinal))
      case .visibility(let visibility):
        (value, declared) = (visibility.wire, .visibility(visibility))
      }
      id = try variableID(name, declared, type: NamedVariable.int) { id in
        declare(.integerConstant(IntegerConstant(id: id, value: value)))
      }
    case .floatView(let float):
      // An integer reader reads a float's id through its integer view.
      id = try floatID(float)
    case .unary(let operation, let operand):
      id = try declareIntegerExpression(node) { () throws(RemoteEncodingError) in
        [(try intID(operand), true), (IntegerExpressionOperator.offset + operation.code, true)]
      }
    case .binary(let operation, let lhs, let rhs):
      id = try declareIntegerExpression(node) { () throws(RemoteEncodingError) in
        [
          (try intID(lhs), true), (try intID(rhs), true),
          (IntegerExpressionOperator.offset + operation.code, true),
        ]
      }
    }
    intNodeIDs[node] = id
    return id
  }

  /// The words an `IntegerExpression`'s mask can mark: one bit per word.
  private static let integerExpressionMaxWords = Int32.bitWidth

  /// An `IntegerExpression` for `node`, in reverse-Polish order. A word is a literal, or, where
  /// its mask bit is set, an id or an operator; the mask has 32 bits, so a longer expression is
  /// written as `split`: its operands declared as expressions of their own, referenced by id.
  private func declareIntegerExpression(
    _ node: RemoteInt.Node,
    split: () throws(RemoteEncodingError) -> [(value: Int32, isReference: Bool)]
  ) throws(RemoteEncodingError) -> Int32 {
    var words: [(value: Int32, isReference: Bool)] = []
    try appendIntegerRPN(node, to: &words)
    if words.count > Self.integerExpressionMaxWords { words = try split() }
    return declareIntegerWords(words)
  }

  /// Declares an `IntegerExpression` of `words`, marking the references in its mask.
  private func declareIntegerWords(_ words: [(value: Int32, isReference: Bool)]) -> Int32 {
    let mask = words.enumerated().reduce(Int32(0)) { mask, entry in
      entry.element.isReference ? mask | Int32(1) << Int32(entry.offset) : mask
    }
    let id = newDataID()
    declare(.integerExpression(IntegerExpression(id: id, mask: mask, values: words.map(\.value))))
    return id
  }

  private func appendIntegerRPN(
    _ node: RemoteInt.Node, to words: inout [(value: Int32, isReference: Bool)]
  ) throws(RemoteEncodingError) {
    switch node {
    case .constant(let value): words.append((try Self.int32(value), false))
    case .variable, .floatView: words.append((try intID(node), true))
    case .unary(let operation, let operand):
      try appendIntegerRPN(operand, to: &words)
      words.append((IntegerExpressionOperator.offset + operation.code, true))
    case .binary(let operation, let lhs, let rhs):
      try appendIntegerRPN(lhs, to: &words)
      try appendIntegerRPN(rhs, to: &words)
      words.append((IntegerExpressionOperator.offset + operation.code, true))
    }
  }

  func intID(_ variable: RemoteVariable<Int>) throws(RemoteEncodingError) -> Int32 {
    try intID(RemoteInt(variable).node)
  }

  func intID(_ variable: RemoteVariable<RemoteVisibility>) throws(RemoteEncodingError) -> Int32 {
    try intID(.variable(name: variable.name, declaration: .visibility(variable.defaultValue)))
  }

  /// An integer id's long form, as the integer actions take it (`IdEncoding.longIdOffset` + id).
  private static func longID(_ id: Int32) -> Int64 { IdEncoding.longIdOffset + Int64(id) }

  /// The visibility a condition gives: `visible` while it holds, `gone` while it does not.
  private func visibilityID(_ condition: RemoteBool) throws(RemoteEncodingError) -> Int32 {
    let visible = Int32(WireExpressionConstants.visibilityVisible)
    let gone = Int32(WireExpressionConstants.visibilityGone)
    // The condition is 1 or 0; scaled onto the two visibility values. With today's values (1 and
    // 0) that is the condition itself.
    var visibility = condition.int
    if visible - gone != 1 {
      var scaled = RemoteInt(0)
      for _ in 0..<(visible - gone) { scaled = scaled + condition.int }
      visibility = scaled
    }
    return try intID((visibility + RemoteInt(Int(gone))).node)
  }

  enum ResolvedColor {
    case literal(Int32)
    case id(Int32)
  }

  /// `value` as a literal ARGB word, or the id of the colour the player resolves.
  func color(_ value: RemoteColor) throws(RemoteEncodingError) -> ResolvedColor {
    switch value.storage {
    case .hierarchical:
      // A hierarchical style used other than as the foreground (a background, a stroke) is the
      // foreground the view is drawn under.
      return try color(value.resolved(against: environment.foregroundStyle))
    case .variableWithOpacity(let variable, let factor):
      if let id = colorIDs[value.storage] { return .id(id) }
      guard case .id(let source) = try color(RemoteColor(variable)) else {
        preconditionFailure("a variable colour is an id")
      }
      // The variable's channels, and its alpha times `factor`, rebuilt as an ARGB colour whose
      // alpha is a float id (`ColorExpression`'s ID-ARGB mode), as the value changes.
      func channel(_ type: Int32) -> Int32 {
        let id = newDataID()
        declare(.colorAttribute(ColorAttribute(id: id, colorId: source, type: Int16(type))))
        return id
      }
      let red = channel(ColorAttribute.colorRed)
      let green = channel(ColorAttribute.colorGreen)
      let blue = channel(ColorAttribute.colorBlue)
      let alpha = try floatID(
        (RemoteFloat(node: .declared(channel(ColorAttribute.colorAlpha)))
          * RemoteFloat(factor)).node)
      // The alpha's id is written in the mode word's upper 16 bits.
      guard alpha <= Int32(UInt16.max) else {
        throw RemoteEncodingError.integerOutOfRange(Int(alpha))
      }
      func word(_ id: Int32) -> Int32 { Int32(bitPattern: Self.reference(id).bitPattern) }
      let id = newDataID()
      declare(
        .colorExpression(
          ColorExpression(
            id: id, flagsAndMode: ColorExpression.idargbMode | alpha << 16, arg1: word(red),
            arg2: word(green), arg3: word(blue))))
      colorIDs[value.storage] = id
      return .id(id)
    case .fixed(let literal): return .literal(literal.argb)
    case .adaptive(let light, let dark):
      if let id = colorIDs[value.storage] { return .id(id) }
      let id = newDataID()
      // No colour group and no index into one: the player takes the fallback for its
      // appearance, as `GenerateBaselineFixture` writes a themed colour.
      declare(
        .colorTheme(
          ColorTheme(
            id: id, groupId: 0, lightModeIndex: 0, darkModeIndex: 0,
            lightModeFallback: light.argb, darkModeFallback: dark.argb)))
      colorIDs[value.storage] = id
      return .id(id)
    case .variable(let variable):
      let id = try variableID(
        variable.name, .color(variable.defaultValue), type: NamedVariable.color
      ) {
        id in
        declare(.colorConstant(ColorConstant(colorId: id, color: variable.defaultValue.argb)))
      }
      return .id(id)
    }
  }

  // MARK: - Layouts

  /// Writes `content`: a built-in view's own operations, or anything else's `body`.
  func write(_ content: some RemoteStackContent) throws(RemoteEncodingError) {
    if let primitive = content as? any RemotePrimitive {
      try primitive.write(to: self)
    } else if let view = content as? any RemoteView {
      try writeBody(of: view)
    } else {
      throw RemoteEncodingError.unsupportedContent(String(describing: type(of: content)))
    }
  }

  private func writeBody(of view: some RemoteView) throws(RemoteEncodingError) {
    try write(view.body)
  }

  /// Runs `body` with `modifier` applied to the next built-in view it writes.
  func withModifier(
    _ modifier: RemoteModifierElement, _ body: () throws(RemoteEncodingError) -> Void
  ) throws(RemoteEncodingError) {
    pendingModifiers.append(modifier)
    defer { pendingModifiers.removeLast() }
    try body()
  }

  /// The root: every view of the document, under `RootLayout`.
  func writeRoot(_ content: any RemoteView) throws(RemoteEncodingError) {
    emit(.rootLayout(RootLayout(componentId: newComponentID())))
    parents.append(.root)
    try write(content)
    parents.removeLast()
    emit(.containerEnd(ContainerEnd()))
  }

  /// A built-in view: `layout`, the modifiers waiting for it, `LayoutContent`, its children,
  /// then two `ContainerEnd`s, as AndroidX's writer shapes a component.
  func writeLayout<Content: RemoteStackContent>(
    _ layout: WireOperation, kind: ParentKind, content: Content, rowGap: Double = 0,
    drawBehind: DrawBehind? = nil
  ) throws(RemoteEncodingError) {
    let saved = pendingModifiers
    let modifiers = takeModifiers()
    defer { pendingModifiers = saved }

    emit(layout)
    try writeModifiers(modifiers)
    // The draw-behind marker is the component's innermost modifier: its drawing covers the
    // component exactly, with no padding inside it to move or shrink it.
    if drawBehind != nil { emit(.drawContentOperation(DrawContentOperation())) }
    let contentID = newComponentID()
    emit(.layoutContent(LayoutContent(componentId: contentID)))
    try writeDrawBehind(drawBehind.map { [$0] } ?? [], contentID: contentID)
    parents.append(kind)
    if kind == .horizontalStack { rowGaps.append((try pixels(rowGap), 0)) }
    try write(content)
    if kind == .horizontalStack { rowGaps.removeLast() }
    parents.removeLast()
    emit(.containerEnd(ContainerEnd()))
    emit(.containerEnd(ContainerEnd()))
  }

  /// Each open row's gap, in pixels, and how many children it has written.
  private var rowGaps: [(gap: Float, children: Int)] = []

  /// The modifiers the built-in view being written applies, leaving none for its children.
  ///
  /// A child of a row after the first also takes the row's gap, as leading padding outside its
  /// own modifiers. A row writes its spacing this way rather than as `spacedBy`, because a
  /// weighted row measures its children without the gaps and adds them as it places them (as
  /// AndroidX's reference player does), so flexible children would push the last one out by the
  /// total gap.
  private func takeModifiers() -> [RemoteModifierElement] {
    var modifiers = pendingModifiers
    pendingModifiers = []
    if parents.last == .horizontalStack, let last = rowGaps.indices.last {
      if rowGaps[last].children > 0, rowGaps[last].gap > 0 {
        let gap = RemoteFloat(rowGaps[last].gap / displayScale)
        modifiers.insert(.padding(RemoteEdgeInsets(leading: gap)), at: 0)
      }
      rowGaps[last].children += 1
    }
    return modifiers
  }

  /// `content` on a rounded background that does not clip it, as SwiftUI's `background(_:in:)`:
  /// a box whose only modifier is the draw-behind marker, with `content` as its child. The
  /// modifiers outside the background (padding, frame, weight) go on a box around that, so the
  /// background always draws over a component with no padding of its own.
  func writeRoundedBackground(
    _ color: RemoteColor, cornerRadius: Double, content: some RemoteView
  ) throws(RemoteEncodingError) {
    let background = RoundedBackgroundBox(
      drawBehind: DrawBehind(color: color, cornerRadius: try pixels(cornerRadius)), content: content
    )
    try writeInOuterBox(content: background)
  }

  /// `content`, with the modifiers waiting for it (and a row's gap) on a box around it rather than
  /// on it: for a component whose own modifiers would change what it draws, or that a player lays
  /// out without them (a `StateLayout`). Without modifiers, `content` alone.
  func writeInOuterBox(
    alignment: RemoteAlignment = .center, content: some RemoteStackContent
  ) throws(RemoteEncodingError) {
    // A row's gap is padding too, so a later child of a row gets the outer box for it.
    let takesRowGap =
      parents.last == .horizontalStack
      && rowGaps.last.map { $0.children > 0 && $0.gap > 0 } == true
    if pendingModifiers.isEmpty, !takesRowGap { return try write(content) }
    let box = BoxLayout(
      componentId: newComponentID(), animationId: IdEncoding.noId,
      horizontalPositioning: alignment.horizontal.wire,
      verticalPositioning: alignment.vertical.wire)
    try writeLayout(.boxLayout(box), kind: .zStack, content: content)
  }

  func centeredBox() -> BoxLayout {
    BoxLayout(
      componentId: newComponentID(), animationId: IdEncoding.noId,
      horizontalPositioning: BoxLayout.center, verticalPositioning: BoxLayout.center)
  }

  /// A box that fills the space it is offered on every axis its frame does not fix, painted
  /// `background`: a ``RemoteColor`` used as a view.
  func writeFillingBox(background: RemoteColor) throws(RemoteEncodingError) {
    try withModifier(.background(background, .rectangle)) { () throws(RemoteEncodingError) in
      try withFillOnFreeAxes { () throws(RemoteEncodingError) in
        try writeBox()
      }
    }
  }

  private func writeBox() throws(RemoteEncodingError) {
    let layout = BoxLayout(
      componentId: newComponentID(), animationId: IdEncoding.noId,
      horizontalPositioning: BoxLayout.start, verticalPositioning: BoxLayout.top)
    try writeLayout(.boxLayout(layout), kind: .zStack, content: EmptyRemoteView())
  }

  /// Runs `body` with a fill on each axis the waiting frame modifiers leave free. A fill applies
  /// innermost, so a frame or weight from outside still decides the size.
  private func withFillOnFreeAxes(_ body: () throws(RemoteEncodingError) -> Void)
    throws(RemoteEncodingError)
  {
    var widthFixed = false
    var heightFixed = false
    for modifier in pendingModifiers {
      switch modifier {
      case .frame(let width, let height):
        widthFixed = widthFixed || width != nil
        heightFixed = heightFixed || height != nil
      case .flexWeight:
        switch parents.last {
        case .horizontalStack?: widthFixed = true
        case .verticalStack?: heightFixed = true
        default: break
        }
      default: break
      }
    }
    if widthFixed && heightFixed { return try body() }
    try withModifier(
      .frame(
        width: widthFixed ? nil : .fill(1), height: heightFixed ? nil : .fill(1)), body)
  }

  /// A spacer: flexible space along the enclosing stack, or a fixed gap.
  func writeSpacer(length: RemoteFloat?) throws(RemoteEncodingError) {
    let axisIsWidth: Bool?
    switch parents.last {
    case .horizontalStack?: axisIsWidth = true
    case .verticalStack?: axisIsWidth = false
    default: axisIsWidth = nil
    }
    let modifier: RemoteModifierElement
    switch (axisIsWidth, length) {
    case (true?, let length?): modifier = .frame(width: .exact(length), height: nil)
    case (false?, let length?): modifier = .frame(width: nil, height: .exact(length))
    case (nil, let length?): modifier = .frame(width: .exact(length), height: .exact(length))
    case (_, nil): modifier = .flexWeight(1)
    }
    if axisIsWidth == nil, length == nil {
      // Outside a stack there is nothing to share: the spacer takes what it is offered.
      return try withModifier(.frame(width: .fill(1), height: .fill(1))) {
        () throws(RemoteEncodingError) in try writeBox()
      }
    }
    try withModifier(modifier) { () throws(RemoteEncodingError) in try writeBox() }
  }

  /// A canvas: `CanvasLayout`, its modifiers, `LayoutContent`, then `CanvasContent` with the
  /// drawing, in the form every player profile reads.
  func writeCanvas(_ commands: [RemoteGraphicsContext.Command]) throws(RemoteEncodingError) {
    try withFillOnFreeAxes { () throws(RemoteEncodingError) in
      let canvasID = newComponentID()
      let saved = pendingModifiers
      let modifiers = takeModifiers()
      defer { pendingModifiers = saved }

      emit(.canvasLayout(CanvasLayout(componentId: canvasID, animationId: IdEncoding.noId)))
      try writeModifiers(modifiers)
      let layoutContentID = newComponentID()
      emit(.layoutContent(LayoutContent(componentId: layoutContentID)))
      emit(.canvasContent(CanvasContent(componentId: newComponentID())))

      // The size a drawing reads is the canvas's content area, bound as AndroidX binds a
      // component's size: to its `LayoutContent`.
      if commands.contains(where: \.readsCanvasSize) {
        canvasSize = (
          componentValue(ComponentValue.width, layoutContentID),
          componentValue(ComponentValue.height, layoutContentID)
        )
      }
      defer { canvasSize = nil }

      var paint: [Int32]? = nil
      for command in commands {
        let words = try paintWords(command)
        if words != paint {
          emit(.paintData(PaintData(words: words)))
          paint = words
        }
        emit(try drawOperation(command))
      }
      emit(.containerEnd(ContainerEnd()))
      emit(.containerEnd(ContainerEnd()))
      emit(.containerEnd(ContainerEnd()))
    }
  }

  private var bitmapIDs: [[UInt8]: Int32] = [:]

  /// The id of an inline PNG, declared once (`BitmapData`).
  func bitmapID(png: [UInt8], width: Int, height: Int) -> Int32 {
    if let id = bitmapIDs[png] { return id }
    let id = newDataID()
    typealias Layout = BitmapData.BitLayout
    let widthAndType =
      UInt32(width) << Layout.widthAndTypeWidthShift
      | UInt32(bitPattern: BitmapData.typePng8888) << Layout.widthAndTypeImageTypeShift
    let heightAndEncoding =
      UInt32(height) << Layout.heightAndEncodingHeightShift
      | UInt32(bitPattern: BitmapData.encodingInline) << Layout.heightAndEncodingBitmapEncodingShift
    declare(
      .bitmapData(
        BitmapData(
          imageId: id, widthAndType: Int32(bitPattern: widthAndType),
          heightAndEncoding: Int32(bitPattern: heightAndEncoding), bitmap: png)))
    bitmapIDs[png] = id
    return id
  }

  private var pathIDs: [RemotePath: Int32] = [:]

  /// The id of `path`, declared once (`PathData`) in pixels, with the non-zero winding rule.
  private func pathID(_ path: RemotePath) throws(RemoteEncodingError) -> Int32 {
    if let id = pathIDs[path] { return id }
    let words = try path.pathWords(scale: displayScale)
    let id = newDataID()
    let idAndWinding =
      UInt32(bitPattern: id) & PathData.BitLayout.idAndWindingPathIdMask
      | UInt32(bitPattern: PathData.winding) << PathData.BitLayout.idAndWindingWindingShift
    declare(
      .pathData(
        PathData(
          idAndWinding: Int32(bitPattern: idAndWinding),
          pathData: words)))
    pathIDs[path] = id
    return id
  }

  private func componentValue(_ type: Int32, _ componentID: Int32) -> Int32 {
    let id = newDataID()
    declare(.componentValue(ComponentValue(type: type, componentId: componentID, valueId: id)))
    return id
  }

  private func drawOperation(_ command: RemoteGraphicsContext.Command)
    throws(RemoteEncodingError) -> WireOperation
  {
    func box(_ rect: RemoteRect) throws(RemoteEncodingError) -> (Float, Float, Float, Float) {
      (
        try pixelWord(rect.origin.x), try pixelWord(rect.origin.y),
        try pixelWord(rect.origin.x + rect.size.width),
        try pixelWord(rect.origin.y + rect.size.height)
      )
    }
    switch command.shape.kind {
    case .rect(let rect):
      let (left, top, right, bottom) = try box(rect)
      return .drawRect(DrawRect(left: left, top: top, right: right, bottom: bottom))
    case .roundedRect(let rect, let cornerRadius):
      let (left, top, right, bottom) = try box(rect)
      let radius = try pixels(cornerRadius)
      return .drawRoundRect(
        DrawRoundRect(
          left: left, top: top, right: right, bottom: bottom, radiusX: radius, radiusY: radius))
    case .ellipse(let rect):
      let (left, top, right, bottom) = try box(rect)
      return .drawOval(DrawOval(left: left, top: top, right: right, bottom: bottom))
    case .circle(let center, let radius):
      return .drawCircle(
        DrawCircle(
          centerX: try pixelWord(center.x), centerY: try pixelWord(center.y),
          radius: try pixelWord(radius)))
    case .line(let start, let end):
      return .drawLine(
        DrawLine(
          x1: try pixelWord(start.x), y1: try pixelWord(start.y), x2: try pixelWord(end.x),
          y2: try pixelWord(end.y)))
    case .path(let path):
      return .drawPath(DrawPath(id: try pathID(path)))
    case .arc(let center, let radius, let startAngle, let sweep):
      return .drawArc(
        DrawArc(
          left: try pixelWord(center.x - radius), top: try pixelWord(center.y - radius),
          right: try pixelWord(center.x + radius), bottom: try pixelWord(center.y + radius),
          startAngle: try floatWord(startAngle), sweepAngle: try floatWord(sweep)))
    }
  }

  /// The `PaintData` words for `command`: its colour, style and stroke width, anti-aliased.
  private func paintWords(_ command: RemoteGraphicsContext.Command)
    throws(RemoteEncodingError) -> [Int32]
  {
    func inline(_ tag: Int32, _ value: Int32) -> Int32 {
      tag | value << PaintCommandTags.inlineValueShift
    }
    var words: [Int32] = []
    switch try color(command.color) {
    case .literal(let argb): words += [PaintCommandTags.color, argb]
    case .id(let id): words += [PaintCommandTags.colorId, id]
    }
    switch command.style {
    case .fill:
      words.append(inline(PaintCommandTags.style, PaintConstants.styleFill))
    case .stroke(let lineWidth, let cap, let join):
      words.append(inline(PaintCommandTags.style, PaintConstants.styleStroke))
      words += [
        PaintCommandTags.strokeWidth, Int32(bitPattern: try pixels(lineWidth).bitPattern),
      ]
      // Always written: a paint's state carries over from the previous draw, so a default stroke
      // after a round one would otherwise keep its round caps.
      words.append(inline(PaintCommandTags.strokeCap, cap.wire))
      words.append(inline(PaintCommandTags.strokeJoin, join.wire))
    }
    words.append(inline(PaintCommandTags.antiAlias, 1))
    return words
  }

  // MARK: - Modifiers

  private func writeModifiers(_ modifiers: [RemoteModifierElement]) throws(RemoteEncodingError) {
    var semantics = Semantics()
    for modifier in modifiers {
      if semantics.absorb(modifier) { continue }
      if case .tap = modifier { semantics.isClickable = true }
      try write(modifier)
    }
    if let operation = try semantics.operation(self) { emit(operation) }
  }

  /// A rounded background's colour and corner radius, in pixels.
  struct DrawBehind {
    let color: RemoteColor
    let cornerRadius: Float
  }

  /// A component's drawing behind its children, in AndroidX's `drawWithContent` shape:
  /// `CanvasOperations`, the component's size bound to its `LayoutContent`, each background as a
  /// `DrawRoundRect` over that size, then `DrawContent` where the children are drawn.
  private func writeDrawBehind(_ drawBehinds: [DrawBehind], contentID: Int32)
    throws(RemoteEncodingError)
  {
    guard !drawBehinds.isEmpty else { return }
    emit(.canvasOperations(CanvasOperations()))
    let width = RemoteFloat(node: .declared(componentValue(ComponentValue.width, contentID)))
    let height = RemoteFloat(node: .declared(componentValue(ComponentValue.height, contentID)))
    var paint: [Int32]? = nil
    for drawBehind in drawBehinds {
      let words = try paintWords(
        RemoteGraphicsContext.Command(
          shape: .rect(RemoteRect(x: 0, y: 0, width: 0, height: 0)), color: drawBehind.color,
          style: .fill))
      if words != paint {
        emit(.paintData(PaintData(words: words)))
        paint = words
      }
      emit(
        .drawRoundRect(
          DrawRoundRect(
            left: 0, top: 0, right: try floatWord(width), bottom: try floatWord(height),
            radiusX: drawBehind.cornerRadius, radiusY: drawBehind.cornerRadius)))
    }
    emit(.drawContent(DrawContent()))
    emit(.containerEnd(ContainerEnd()))
  }

  /// A width's or a height's type and value; `exact` and `fill` are that operation's own types.
  private func dimension(
    _ length: RemoteModifierElement.Length, exact: Int32, fill: Int32
  ) throws(RemoteEncodingError) -> (type: Int32, value: Float) {
    switch length {
    case .exact(let points): (exact, try pixelWord(points))
    case .fill(let fraction): (fill, try Self.finite(Float(fraction)))
    }
  }

  private func write(_ modifier: RemoteModifierElement) throws(RemoteEncodingError) {
    switch modifier {
    case .frame(let width, let height):
      if let width {
        let (type, value) = try dimension(
          width, exact: WidthModifierOperation.exact, fill: WidthModifierOperation.fill)
        emit(.widthModifierOperation(WidthModifierOperation(type: type, value: value)))
      }
      if let height {
        let (type, value) = try dimension(
          height, exact: HeightModifierOperation.exact, fill: HeightModifierOperation.fill)
        emit(.heightModifierOperation(HeightModifierOperation(type: type, value: value)))
      }
    case .flexWeight(let weight):
      guard weight > 0, weight.isFinite else {
        throw RemoteEncodingError.invalidFlexWeight(weight)
      }
      // Only a stack's builder accepts a flex-weighted view, so the parent is a stack.
      if parents.last == .verticalStack {
        emit(
          .heightModifierOperation(
            HeightModifierOperation(type: HeightModifierOperation.weight, value: Float(weight))))
      } else {
        emit(
          .widthModifierOperation(
            WidthModifierOperation(type: WidthModifierOperation.weight, value: Float(weight))))
      }
    case .padding(let insets):
      emit(
        .paddingModifierOperation(
          PaddingModifierOperation(
            left: try pixelWord(insets.leading), top: try pixelWord(insets.top),
            right: try pixelWord(insets.trailing), bottom: try pixelWord(insets.bottom))))
    case .background(let color, let shape):
      if shape != .rectangle { try write(.clip(shape)) }
      let (flags, colorID, channels) = try colorFields(
        color,
        colorRefFlag: Int32(
          BackgroundModifierOperation.BitLayout.flagsColorRefMask
            << BackgroundModifierOperation.BitLayout.flagsColorRefShift))
      emit(
        .backgroundModifierOperation(
          BackgroundModifierOperation(
            flags: flags, colorId: colorID, r: channels.red, g: channels.green, b: channels.blue,
            a: channels.alpha, shapeType: BackgroundModifierOperation.rectangle)))
    case .border(let color, let width, let cornerRadius):
      let (flags, colorID, channels) = try colorFields(
        color,
        colorRefFlag: Int32(
          BorderModifierOperation.BitLayout.flagsColorRefMask
            << BorderModifierOperation.BitLayout.flagsColorRefShift))
      emit(
        .borderModifierOperation(
          BorderModifierOperation(
            flags: flags, colorId: colorID, reserve1: BorderModifierOperation.version1,
            borderWidth: try pixels(width), roundedCorner: try pixels(cornerRadius),
            r: channels.red,
            g: channels.green, b: channels.blue, a: channels.alpha,
            // The radius is read only for a rounded rectangle: written as a rectangle, it is
            // ignored.
            shapeType: cornerRadius > 0
              ? BorderModifierOperation.roundedRectangle : BorderModifierOperation.rectangle)))
    case .clip(let shape):
      switch shape {
      case .rectangle:
        emit(.clipRectModifierOperation(ClipRectModifierOperation()))
      case .roundedCorners(let topLeading, let topTrailing, let bottomLeading, let bottomTrailing):
        emit(
          .roundedClipRectModifierOperation(
            RoundedClipRectModifierOperation(
              topStart: try pixels(topLeading), topEnd: try pixels(topTrailing),
              bottomStart: try pixels(bottomLeading), bottomEnd: try pixels(bottomTrailing))))
      }
    case .offset(let x, let y):
      emit(
        .offsetModifierOperation(
          OffsetModifierOperation(x: try pixelWord(x), y: try pixelWord(y))))
    case .zIndex(let value):
      emit(.zIndexModifierOperation(ZIndexModifierOperation(value: try Self.finite(Float(value)))))
    case .visibility(let visibility):
      emit(
        .componentVisibilityOperation(
          ComponentVisibilityOperation(visibilityId: intID(visibility.wire))))
    case .visibilityVariable(let variable):
      emit(
        .componentVisibilityOperation(
          ComponentVisibilityOperation(visibilityId: try intID(variable))))
    case .visibleIf(let condition):
      emit(
        .componentVisibilityOperation(
          ComponentVisibilityOperation(visibilityId: try visibilityID(condition))))
    case .tap(let actions):
      // Resolve every id first: a declaration never lands inside the click container, but the
      // ids it needs must exist before the actions are emitted.
      let resolved = try actions.map { action throws(RemoteEncodingError) in
        try self.operation(action)
      }
      emit(.clickModifier(ClickModifier()))
      for operation in resolved { emit(operation) }
      emit(.containerEnd(ContainerEnd()))
    case .accessibilityLabel, .accessibilityValue, .accessibilityRole, .accessibilityChildren:
      preconditionFailure("accessibility modifiers merge into one CoreSemantics")
    }
  }

  /// A colour's fields in a background or border modifier: literal channels, or the colour-id
  /// flag (`colorRefFlag`, the operation's own) and the id.
  private func colorFields(_ color: RemoteColor, colorRefFlag: Int32) throws(RemoteEncodingError)
    -> (
      flags: Int32, colorID: Int32,
      channels: (red: Float, green: Float, blue: Float, alpha: Float)
    )
  {
    // A hierarchical style is the foreground it is drawn under: resolved first, so a literal
    // keeps its exact components.
    let color = color.resolved(against: environment.foregroundStyle)
    switch try self.color(color) {
    case .literal:
      guard case .fixed(let value) = color.storage else { preconditionFailure() }
      return (
        0, 0, (Float(value.red), Float(value.green), Float(value.blue), Float(value.opacity))
      )
    case .id(let id):
      return (colorRefFlag, id, (0, 0, 0, 0))
    }
  }

  private func operation(_ action: RemoteAction) throws(RemoteEncodingError) -> WireOperation {
    switch action.kind {
    case .host(let id):
      return .hostAction(HostAction(actionId: try Self.int32(id)))
    case .hostNamed(let name, let value):
      let nameID = textID(name)
      let type: Int32
      let valueID: Int32
      switch value {
      case .none: (type, valueID) = (HostNamedAction.noneType, IdEncoding.noId)
      case .float(let float): (type, valueID) = (HostNamedAction.floatType, try floatID(float))
      case .int(let int): (type, valueID) = (HostNamedAction.intType, try intID(int))
      case .string(let string):
        (type, valueID) = (HostNamedAction.stringType, try stringID(string))
      }
      return .hostNamedAction(HostNamedAction(textId: nameID, type: type, valueId: valueID))
    case .setDouble(let variable, let value):
      return .valueFloatChangeActionOperation(
        ValueFloatChangeActionOperation(
          targetValueId: try floatID(RemoteFloat(variable).node),
          value: try Self.finite(Float(value))))
    case .setInt(let variable, let value):
      return .valueIntegerChangeActionOperation(
        ValueIntegerChangeActionOperation(
          targetValueId: try intID(variable), value: try Self.int32(value)))
    case .setVisibility(let variable, let value):
      return .valueIntegerChangeActionOperation(
        ValueIntegerChangeActionOperation(targetValueId: try intID(variable), value: value.wire))
    case .setBool(let variable, let value):
      return .valueIntegerChangeActionOperation(
        ValueIntegerChangeActionOperation(
          targetValueId: try intID(RemoteBool(variable).int.node), value: value ? 1 : 0))
    case .toggle(let variable):
      let current = RemoteBool(variable)
      return .valueIntegerExpressionChangeActionOperation(
        ValueIntegerExpressionChangeActionOperation(
          targetValueId: Self.longID(try intID(current.int.node)),
          valueExpressionId: Self.longID(try intID((!current).int.node))))
    case .setDoubleTo(let variable, let value):
      let target = try floatID(RemoteFloat(variable).node)
      if let constant = value.constant {
        return .valueFloatChangeActionOperation(
          ValueFloatChangeActionOperation(targetValueId: target, value: try Self.finite(constant)))
      }
      return .valueFloatExpressionChangeActionOperation(
        ValueFloatExpressionChangeActionOperation(
          targetValueId: target, valueExpressionId: try floatExpressionID(value.node)))
    case .setIntTo(let variable, let value):
      let target = try intID(variable)
      if case .constant(let constant) = value.node {
        return .valueIntegerChangeActionOperation(
          ValueIntegerChangeActionOperation(targetValueId: target, value: try Self.int32(constant)))
      }
      return .valueIntegerExpressionChangeActionOperation(
        ValueIntegerExpressionChangeActionOperation(
          targetValueId: Self.longID(target),
          valueExpressionId: Self.longID(try intExpressionID(value.node)))
      )
    case .setString(let variable, let value):
      let target = try stringID(RemoteString(variable))
      return .valueStringChangeActionOperation(
        ValueStringChangeActionOperation(targetValueId: target, valueId: textID(value)))
    }
  }

  /// The id of a `FloatExpression` for `node`, as an action's source must be: a bare reference
  /// (a variable, a system value) is wrapped in a one-word expression that reads it.
  private func floatExpressionID(_ node: RemoteFloat.Node) throws(RemoteEncodingError) -> Int32 {
    switch node {
    case .unary, .binary: return try floatID(node)
    default:
      if let id = floatReferenceExpressionIDs[node] { return id }
      var words: [Float] = []
      try appendRPN(node, to: &words)
      let id = newDataID()
      declare(.floatExpression(FloatExpression(id: id, expression: words)))
      floatReferenceExpressionIDs[node] = id
      return id
    }
  }

  private var floatReferenceExpressionIDs: [RemoteFloat.Node: Int32] = [:]

  /// The id of an `IntegerExpression` for `node`, as an action's source must be: a variable is
  /// wrapped in a one-word expression that reads it.
  private func intExpressionID(_ node: RemoteInt.Node) throws(RemoteEncodingError) -> Int32 {
    switch node {
    case .unary, .binary: return try intID(node)
    default:
      if let id = intReferenceExpressionIDs[node] { return id }
      var words: [(value: Int32, isReference: Bool)] = []
      try appendIntegerRPN(node, to: &words)
      let id = declareIntegerWords(words)
      intReferenceExpressionIDs[node] = id
      return id
    }
  }

  private var intReferenceExpressionIDs: [RemoteInt.Node: Int32] = [:]

  private func floatID(_ value: RemoteFloat) throws(RemoteEncodingError) -> Int32 {
    try floatID(value.node)
  }

  /// The accessibility modifiers on one view, merged into the one `CoreSemantics` it carries.
  private struct Semantics {
    var label: RemoteString?
    var value: RemoteString?
    var role: RemoteAccessibilityRole?
    var children: RemoteAccessibilityChildBehavior?
    /// Whether the view also has a tap: semantics written for it say it is clickable.
    var isClickable = false

    /// Takes `modifier` if it is an accessibility one. The outermost of each kind wins, as the
    /// last one applied does in SwiftUI.
    mutating func absorb(_ modifier: RemoteModifierElement) -> Bool {
      switch modifier {
      case .accessibilityLabel(let text): label = label ?? text
      case .accessibilityValue(let text): value = value ?? text
      case .accessibilityRole(let kind): role = role ?? kind
      case .accessibilityChildren(let behavior): children = children ?? behavior
      default: return false
      }
      return true
    }

    func operation(_ writer: RemoteWriter) throws(RemoteEncodingError) -> WireOperation? {
      guard label != nil || value != nil || role != nil || children != nil else { return nil }
      // AndroidX writes 0 for a text it does not set, and "no role" as `ROLE_NONE`.
      let labelID = try label.map { text throws(RemoteEncodingError) in try writer.stringID(text) }
      let valueID = try value.map { text throws(RemoteEncodingError) in try writer.stringID(text) }
      return .coreSemantics(
        CoreSemantics(
          contentDescriptionId: labelID ?? 0,
          role: UInt8(truncatingIfNeeded: role?.wire ?? CoreSemantics.roleNone),
          textId: 0, stateDescriptionId: valueID ?? 0,
          mode: UInt8(truncatingIfNeeded: (children ?? .contain).wire),
          enabled: true, clickable: isClickable))
    }
  }
}

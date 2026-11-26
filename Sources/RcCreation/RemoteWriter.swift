import RcWireOperations

/// The state of one document being encoded. Views write themselves through it; it is public only
/// because ``RemoteStackContent/_write(to:)`` is, and has no public members.
///
/// Values a view reads (text, variables, expressions, colours) are declared in their own list,
/// written between the header and the root, so every id is defined before it is read and each
/// distinct value is declared once however many views read it.
public final class _RemoteWriter {
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

  /// A variable's type and default. The type is part of it: `AnyHashable` alone bridges numbers,
  /// so an `Int` 1 and a `Double` 1 would compare equal.
  private struct VariableDeclaration: Hashable {
    let type: Int32
    let defaultValue: AnyHashable
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

  func pixels(_ points: Double) -> Float { Float(points) * displayScale }

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
    _ name: String, _ defaultValue: AnyHashable, type: Int32,
    declare declareValue: (Int32) -> Void
  ) throws(RemoteEncodingError) -> Int32 {
    let declaration = VariableDeclaration(type: type, defaultValue: defaultValue)
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
    if let literal = value.constant { return literal }
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
    case .constant(let literal):
      id = newDataID()
      declare(.floatConstant(FloatConstant(id: id, value: literal)))
    case .variable(let name, let defaultValue):
      id = try variableID(name, defaultValue, type: NamedVariable.float) { id in
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
    case .constant(let literal): words.append(literal)
    case .variable, .declared, .system, .canvasSize, .canvasPixels:
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
    case .constant, .variable, .system, .declared: return node
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
      id = try variableID(name, defaultValue, type: NamedVariable.string) { id in
        declare(.textData(TextData(textId: id, text: defaultValue)))
      }
    case .number(let number, let integerDigits, let decimals):
      guard (0...16).contains(integerDigits), (0...16).contains(decimals) else {
        throw RemoteEncodingError.invalidNumberFormat(
          integerDigits: integerDigits, decimals: decimals)
      }
      let word = try floatWord(RemoteFloat(node: number))
      id = newDataID()
      // No padding before the point; zeros after it, so the fraction always has `decimals`
      // digits, as `String(format: "%.2f")` prints it.
      declare(
        .textFromFloat(
          TextFromFloat(
            textId: id, value: word, digitsBefore: Int16(integerDigits),
            digitsAfter: Int16(decimals),
            flags: TextFromFloat.padPreNone | TextFromFloat.padAfterZero)))
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

  private func intVariableID(_ name: String, _ defaultValue: Int32, declaration: AnyHashable)
    throws(RemoteEncodingError) -> Int32
  {
    try variableID(name, declaration, type: NamedVariable.int) { id in
      declare(.integerConstant(IntegerConstant(id: id, value: defaultValue)))
    }
  }

  func intID(_ variable: RemoteVariable<Int>) throws(RemoteEncodingError) -> Int32 {
    try intVariableID(
      variable.name, Int32(clamping: variable.defaultValue), declaration: variable.defaultValue)
  }

  func intID(_ variable: RemoteVariable<RemoteVisibility>) throws(RemoteEncodingError) -> Int32 {
    try intVariableID(
      variable.name, variable.defaultValue.wire, declaration: variable.defaultValue)
  }

  enum ResolvedColor {
    case literal(Int32)
    case id(Int32)
  }

  /// `value` as a literal ARGB word, or the id of the colour the player resolves.
  func color(_ value: RemoteColor) throws(RemoteEncodingError) -> ResolvedColor {
    switch value.storage {
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
      let id = try variableID(variable.name, variable.defaultValue, type: NamedVariable.color) {
        id in
        declare(.colorConstant(ColorConstant(colorId: id, color: variable.defaultValue.argb)))
      }
      return .id(id)
    }
  }

  // MARK: - Layouts

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
    try content._write(to: self)
    parents.removeLast()
    emit(.containerEnd(ContainerEnd()))
  }

  /// A built-in view: `layout`, the modifiers waiting for it, `LayoutContent`, its children,
  /// then two `ContainerEnd`s, as AndroidX's writer shapes a component.
  func writeLayout<Content: RemoteStackContent>(
    _ layout: WireOperation, kind: ParentKind, content: Content
  ) throws(RemoteEncodingError) {
    let modifiers = pendingModifiers
    pendingModifiers = []
    defer { pendingModifiers = modifiers }

    emit(layout)
    try writeModifiers(modifiers)
    emit(.layoutContent(LayoutContent(componentId: newComponentID())))
    parents.append(kind)
    try content._write(to: self)
    parents.removeLast()
    emit(.containerEnd(ContainerEnd()))
    emit(.containerEnd(ContainerEnd()))
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
      let modifiers = pendingModifiers
      pendingModifiers = []
      defer { pendingModifiers = modifiers }

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
      let radius = pixels(cornerRadius)
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
    case .stroke(let lineWidth):
      words.append(inline(PaintCommandTags.style, PaintConstants.styleStroke))
      words += [
        PaintCommandTags.strokeWidth, Int32(bitPattern: pixels(lineWidth).bitPattern),
      ]
    }
    words.append(inline(PaintCommandTags.antiAlias, 1))
    return words
  }

  // MARK: - Modifiers

  private func writeModifiers(_ modifiers: [RemoteModifierElement]) throws(RemoteEncodingError) {
    var semantics = Semantics()
    for modifier in modifiers {
      if semantics.absorb(modifier) { continue }
      try write(modifier)
    }
    if let operation = try semantics.operation(self) { emit(operation) }
  }

  private func dimension(_ length: RemoteModifierElement.Length) throws(RemoteEncodingError)
    -> (type: Int32, value: Float)
  {
    switch length {
    case .exact(let points): (WidthModifierOperation.exact, try pixelWord(points))
    case .fill(let fraction): (WidthModifierOperation.fill, Float(fraction))
    }
  }

  private func write(_ modifier: RemoteModifierElement) throws(RemoteEncodingError) {
    switch modifier {
    case .frame(let width, let height):
      if let width {
        let (type, value) = try dimension(width)
        emit(.widthModifierOperation(WidthModifierOperation(type: type, value: value)))
      }
      if let height {
        let (type, value) = try dimension(height)
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
      let (flags, colorID, channels) = try colorFields(color)
      emit(
        .backgroundModifierOperation(
          BackgroundModifierOperation(
            flags: flags, colorId: colorID, r: channels.red, g: channels.green, b: channels.blue,
            a: channels.alpha, shapeType: BackgroundModifierOperation.rectangle)))
    case .border(let color, let width, let cornerRadius):
      let (flags, colorID, channels) = try colorFields(color)
      emit(
        .borderModifierOperation(
          BorderModifierOperation(
            flags: flags, colorId: colorID, reserve1: BorderModifierOperation.version1,
            borderWidth: pixels(width), roundedCorner: pixels(cornerRadius), r: channels.red,
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
              topStart: pixels(topLeading), topEnd: pixels(topTrailing),
              bottomStart: pixels(bottomLeading), bottomEnd: pixels(bottomTrailing))))
      }
    case .offset(let x, let y):
      emit(
        .offsetModifierOperation(
          OffsetModifierOperation(x: try pixelWord(x), y: try pixelWord(y))))
    case .zIndex(let value):
      emit(.zIndexModifierOperation(ZIndexModifierOperation(value: Float(value))))
    case .visibility(let visibility):
      emit(
        .componentVisibilityOperation(
          ComponentVisibilityOperation(visibilityId: intID(visibility.wire))))
    case .visibilityVariable(let variable):
      emit(
        .componentVisibilityOperation(
          ComponentVisibilityOperation(visibilityId: try intID(variable))))
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

  private func colorFields(_ color: RemoteColor) throws(RemoteEncodingError) -> (
    flags: Int32, colorID: Int32,
    channels: (red: Float, green: Float, blue: Float, alpha: Float)
  ) {
    switch try self.color(color) {
    case .literal:
      guard case .fixed(let value) = color.storage else { preconditionFailure() }
      return (
        0, 0, (Float(value.red), Float(value.green), Float(value.blue), Float(value.opacity))
      )
    case .id(let id):
      let flag = Int32(
        BackgroundModifierOperation.BitLayout.flagsColorRefMask
          << BackgroundModifierOperation.BitLayout.flagsColorRefShift)
      return (flag, id, (0, 0, 0, 0))
    }
  }

  private func operation(_ action: RemoteAction) throws(RemoteEncodingError) -> WireOperation {
    switch action.kind {
    case .host(let id):
      return .hostAction(HostAction(actionId: id))
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
          targetValueId: try floatID(RemoteFloat(variable).node), value: Float(value)))
    case .setInt(let variable, let value):
      return .valueIntegerChangeActionOperation(
        ValueIntegerChangeActionOperation(
          targetValueId: try intID(variable), value: Int32(clamping: value)))
    case .setVisibility(let variable, let value):
      return .valueIntegerChangeActionOperation(
        ValueIntegerChangeActionOperation(targetValueId: try intID(variable), value: value.wire))
    case .setString(let variable, let value):
      let target = try stringID(RemoteString(variable))
      return .valueStringChangeActionOperation(
        ValueStringChangeActionOperation(targetValueId: target, valueId: textID(value)))
    }
  }

  private func floatID(_ value: RemoteFloat) throws(RemoteEncodingError) -> Int32 {
    try floatID(value.node)
  }

  /// The accessibility modifiers on one view, merged into the one `CoreSemantics` it carries.
  private struct Semantics {
    var label: RemoteString?
    var value: RemoteString?
    var role: RemoteAccessibilityRole?
    var children: RemoteAccessibilityChildBehavior?

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

    func operation(_ writer: _RemoteWriter) throws(RemoteEncodingError) -> WireOperation? {
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
          enabled: true, clickable: false))
    }
  }
}

/// An ordered chain of decorations and layout instructions for a component, as Compose's
/// `Modifier` is: each call appends, and the player applies them outermost first.
///
/// ```swift
/// RemoteBox(modifier: .fillMaxWidth().padding(16).background(.white).clip(.rounded(12)))
/// ```
///
/// Dimensions are in dp. The document writes them in pixels at its density.
public struct RemoteModifier: Sendable {
  enum Element: Sendable {
    case width(Dimension)
    case height(Dimension)
    case weight(Float)
    case padding(RemoteFloat, RemoteFloat, RemoteFloat, RemoteFloat)
    case background(RemoteColor)
    case border(width: Float, color: RemoteColor, cornerRadius: Float)
    case clip(RemoteShape)
    case offset(RemoteFloat, RemoteFloat)
    case zIndex(Float)
    case visibility(RemoteInt)
    case clickable([RemoteAction])
  }

  enum Dimension: Sendable {
    case exact(RemoteFloat)
    case fill(Float)
    case wrap
  }

  var elements: [Element]

  public init() { elements = [] }

  private init(_ elements: [Element]) { self.elements = elements }

  private func then(_ element: Element) -> RemoteModifier { RemoteModifier(elements + [element]) }

  /// An empty chain.
  public static var none: RemoteModifier { RemoteModifier() }

  // MARK: Size

  public func width(_ dp: RemoteFloat) -> RemoteModifier { then(.width(.exact(dp))) }
  public func height(_ dp: RemoteFloat) -> RemoteModifier { then(.height(.exact(dp))) }
  public func size(_ dp: RemoteFloat) -> RemoteModifier { width(dp).height(dp) }
  public func size(width: RemoteFloat, height: RemoteFloat) -> RemoteModifier {
    self.width(width).height(height)
  }
  public func fillMaxWidth(_ fraction: Float = 1) -> RemoteModifier {
    then(.width(.fill(fraction)))
  }
  public func fillMaxHeight(_ fraction: Float = 1) -> RemoteModifier {
    then(.height(.fill(fraction)))
  }
  public func fillMaxSize(_ fraction: Float = 1) -> RemoteModifier {
    fillMaxWidth(fraction).fillMaxHeight(fraction)
  }
  public func wrapContentWidth() -> RemoteModifier { then(.width(.wrap)) }
  public func wrapContentHeight() -> RemoteModifier { then(.height(.wrap)) }

  /// The share of a row's width, or a column's height, left after its other children: as
  /// `RowScope.weight` / `ColumnScope.weight`, resolved by the layout the component is in.
  public func weight(_ weight: Float) -> RemoteModifier {
    precondition(weight > 0, "weight must be positive")
    return then(.weight(weight))
  }

  // MARK: Spacing and position

  public func padding(_ all: RemoteFloat) -> RemoteModifier { padding(all, all, all, all) }
  public func padding(horizontal: RemoteFloat = 0, vertical: RemoteFloat = 0) -> RemoteModifier {
    padding(horizontal, vertical, horizontal, vertical)
  }
  public func padding(
    start: RemoteFloat = 0, top: RemoteFloat = 0, end: RemoteFloat = 0, bottom: RemoteFloat = 0
  ) -> RemoteModifier {
    padding(start, top, end, bottom)
  }
  private func padding(
    _ start: RemoteFloat, _ top: RemoteFloat, _ end: RemoteFloat, _ bottom: RemoteFloat
  ) -> RemoteModifier {
    then(.padding(start, top, end, bottom))
  }

  public func offset(x: RemoteFloat = 0, y: RemoteFloat = 0) -> RemoteModifier {
    then(.offset(x, y))
  }

  public func zIndex(_ value: Float) -> RemoteModifier { then(.zIndex(value)) }

  // MARK: Drawing

  public func background(_ color: RemoteColor) -> RemoteModifier { then(.background(color)) }

  /// A background clipped to [shape]: `clip(shape).background(color)`.
  public func background(_ color: RemoteColor, shape: RemoteShape) -> RemoteModifier {
    clip(shape).background(color)
  }

  public func border(_ width: Float, _ color: RemoteColor, cornerRadius: Float = 0)
    -> RemoteModifier
  {
    then(.border(width: width, color: color, cornerRadius: cornerRadius))
  }

  public func clip(_ shape: RemoteShape) -> RemoteModifier { then(.clip(shape)) }

  // MARK: State and input

  /// Shows, hides or removes the component as [visibility] says: one of ``RemoteVisibility``.
  public func visibility(_ visibility: RemoteInt) -> RemoteModifier {
    then(.visibility(visibility))
  }

  /// Runs [actions] when the component is tapped.
  public func clickable(_ actions: RemoteAction...) -> RemoteModifier { clickable(actions) }

  public func clickable(_ actions: [RemoteAction]) -> RemoteModifier {
    then(.clickable(actions))
  }

  // MARK: Leading-dot entry points

  public static func width(_ dp: RemoteFloat) -> RemoteModifier { none.width(dp) }
  public static func height(_ dp: RemoteFloat) -> RemoteModifier { none.height(dp) }
  public static func size(_ dp: RemoteFloat) -> RemoteModifier { none.size(dp) }
  public static func size(width: RemoteFloat, height: RemoteFloat) -> RemoteModifier {
    none.size(width: width, height: height)
  }
  public static func fillMaxWidth(_ fraction: Float = 1) -> RemoteModifier {
    none.fillMaxWidth(fraction)
  }
  public static func fillMaxHeight(_ fraction: Float = 1) -> RemoteModifier {
    none.fillMaxHeight(fraction)
  }
  public static func fillMaxSize(_ fraction: Float = 1) -> RemoteModifier {
    none.fillMaxSize(fraction)
  }
  public static func wrapContentWidth() -> RemoteModifier { none.wrapContentWidth() }
  public static func wrapContentHeight() -> RemoteModifier { none.wrapContentHeight() }
  public static func weight(_ weight: Float) -> RemoteModifier { none.weight(weight) }
  public static func padding(_ all: RemoteFloat) -> RemoteModifier { none.padding(all) }
  public static func padding(horizontal: RemoteFloat = 0, vertical: RemoteFloat = 0)
    -> RemoteModifier
  {
    none.padding(horizontal: horizontal, vertical: vertical)
  }
  public static func padding(
    start: RemoteFloat = 0, top: RemoteFloat = 0, end: RemoteFloat = 0, bottom: RemoteFloat = 0
  ) -> RemoteModifier {
    none.padding(start: start, top: top, end: end, bottom: bottom)
  }
  public static func offset(x: RemoteFloat = 0, y: RemoteFloat = 0) -> RemoteModifier {
    none.offset(x: x, y: y)
  }
  public static func zIndex(_ value: Float) -> RemoteModifier { none.zIndex(value) }
  public static func background(_ color: RemoteColor) -> RemoteModifier { none.background(color) }
  public static func background(_ color: RemoteColor, shape: RemoteShape) -> RemoteModifier {
    none.background(color, shape: shape)
  }
  public static func border(_ width: Float, _ color: RemoteColor, cornerRadius: Float = 0)
    -> RemoteModifier
  {
    none.border(width, color, cornerRadius: cornerRadius)
  }
  public static func clip(_ shape: RemoteShape) -> RemoteModifier { none.clip(shape) }
  public static func visibility(_ visibility: RemoteInt) -> RemoteModifier {
    none.visibility(visibility)
  }
  public static func clickable(_ actions: RemoteAction...) -> RemoteModifier {
    none.clickable(actions)
  }
  public static func clickable(_ actions: [RemoteAction]) -> RemoteModifier {
    none.clickable(actions)
  }
}

/// A clip outline.
public enum RemoteShape: Sendable, Equatable {
  case rectangle
  /// Corners rounded by a radius in dp each.
  case roundedCorners(topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float)

  /// All four corners rounded by [radius] dp.
  public static func rounded(_ radius: Float) -> RemoteShape {
    .roundedCorners(topStart: radius, topEnd: radius, bottomEnd: radius, bottomStart: radius)
  }
}

/// Something the player does when a component is tapped.
public struct RemoteAction: Sendable {
  enum Kind: Sendable {
    case host(Int)
    case hostNamed(String, HostValue)
    case setInt(RemoteInt, Int)
    case setFloat(RemoteFloat, Float)
    case setString(RemoteString, String)
  }

  enum HostValue: Sendable {
    case none
    case float(RemoteFloat)
    case int(RemoteInt)
    case string(RemoteString)
  }

  let kind: Kind

  /// Tells the host action [id] happened (`HostAction`).
  public static func host(_ id: Int) -> RemoteAction { RemoteAction(kind: .host(id)) }

  /// Tells the host the action named [name] happened (`HostNamedAction`).
  public static func host(_ name: String) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .none))
  }
  public static func host(_ name: String, value: RemoteFloat) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .float(value)))
  }
  public static func host(_ name: String, value: RemoteInt) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .int(value)))
  }
  public static func host(_ name: String, value: RemoteString) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .string(value)))
  }

  /// Writes [value] into the named integer [target], in the player, with no host round trip.
  public static func set(_ target: RemoteInt, to value: Int) -> RemoteAction {
    guard case .named = target.node else {
      preconditionFailure("set(_:to:) needs a named value as its target")
    }
    return RemoteAction(kind: .setInt(target, value))
  }
  public static func set(_ target: RemoteFloat, to value: Float) -> RemoteAction {
    guard case .named = target.node else {
      preconditionFailure("set(_:to:) needs a named value as its target")
    }
    return RemoteAction(kind: .setFloat(target, value))
  }
  public static func set(_ target: RemoteString, to value: String) -> RemoteAction {
    guard case .named = target.node else {
      preconditionFailure("set(_:to:) needs a named value as its target")
    }
    return RemoteAction(kind: .setString(target, value))
  }
}

extension RemoteCreationContext {
  func write(_ modifier: RemoteModifier) {
    for element in modifier.elements { write(element) }
  }

  private func dimension(_ opcode: Int, _ dimension: RemoteModifier.Dimension) {
    op(opcode)
    switch dimension {
    case .exact(let dp):
      body.int(RemoteDimensionType.exact)
      body.float(pxWord(dp))
    case .fill(let fraction):
      body.int(RemoteDimensionType.fill)
      body.float(fraction)
    case .wrap:
      body.int(RemoteDimensionType.wrap)
      body.float(0)
    }
  }

  private func write(_ element: RemoteModifier.Element) {
    switch element {
    case .width(let value): dimension(RemoteOpcode.modifierWidth, value)
    case .height(let value): dimension(RemoteOpcode.modifierHeight, value)
    case .weight(let weight):
      switch parents.last {
      case .row?: op(RemoteOpcode.modifierWidth)
      case .column?: op(RemoteOpcode.modifierHeight)
      default: preconditionFailure("weight(_:) applies only to a child of a row or a column")
      }
      body.int(RemoteDimensionType.weight)
      body.float(weight)
    case .padding(let start, let top, let end, let bottom):
      op(RemoteOpcode.modifierPadding)
      for value in [start, top, end, bottom] { body.float(pxWord(value)) }
    case .background(let color):
      let resolved = self.color(color)
      op(RemoteOpcode.modifierBackground)
      switch resolved {
      case .literal(let argb):
        body.int(0)
        body.int(0)
        body.int(0)
        body.int(0)
        for channel in RemoteCreationContext.components(argb) { body.float(channel) }
      case .id(let id):
        body.int(RemoteModifierColorFlag.colorID)
        body.int(id)
        body.int(0)
        body.int(0)
        for _ in 0..<4 { body.float(0) }
      }
      body.int(RemoteModifierShape.rectangle)  // clip() gives any other outline.
    case .border(let width, let color, let cornerRadius):
      let resolved = self.color(color)
      op(RemoteOpcode.modifierBorder)
      switch resolved {
      case .literal(let argb):
        body.int(0)
        body.int(0)
        body.int(RemoteBorderWireVersion.inside)
        body.int(0)
        body.float(px(width))
        body.float(px(cornerRadius))
        for channel in RemoteCreationContext.components(argb) { body.float(channel) }
      case .id(let id):
        body.int(RemoteModifierColorFlag.colorID)
        body.int(id)
        body.int(RemoteBorderWireVersion.inside)
        body.int(0)
        body.float(px(width))
        body.float(px(cornerRadius))
        for _ in 0..<4 { body.float(0) }
      }
      // The radius is read only for a rounded rectangle: written as a rectangle, it is ignored.
      body.int(cornerRadius > 0 ? RemoteBorderShape.roundedRectangle : RemoteBorderShape.rectangle)
    case .clip(let shape):
      switch shape {
      case .rectangle:
        op(RemoteOpcode.modifierClipRect)
      case .roundedCorners(let topStart, let topEnd, let bottomEnd, let bottomStart):
        op(RemoteOpcode.modifierRoundedClipRect)
        for radius in [topStart, topEnd, bottomStart, bottomEnd] { body.float(px(radius)) }
      }
    case .offset(let x, let y):
      op(RemoteOpcode.modifierOffset)
      body.float(pxWord(x))
      body.float(pxWord(y))
    case .zIndex(let value):
      op(RemoteOpcode.modifierZindex)
      body.float(value)
    case .visibility(let value):
      let id = intID(value)
      op(RemoteOpcode.modifierVisibility)
      body.int(id)
    case .clickable(let actions):
      // Resolve every id first: a declaration must not land inside the click container.
      let resolved = actions.map(resolve)
      op(RemoteOpcode.modifierClick)
      for action in resolved { action(&body) }
      containerEnd()
    }
  }

  private func resolve(_ action: RemoteAction) -> (inout RemoteByteBuffer) -> Void {
    switch action.kind {
    case .host(let id):
      return { buffer in
        buffer.byte(RemoteOpcode.hostAction)
        buffer.int(id)
      }
    case .hostNamed(let name, let value):
      let nameID = textID(name)
      let (type, valueID): (Int, Int)
      switch value {
      case .none: (type, valueID) = (RemoteHostActionValueType.none, remoteNoID)
      case .float(let float):
        (type, valueID) = (RemoteHostActionValueType.float, floatIDForAction(float))
      case .int(let int): (type, valueID) = (RemoteHostActionValueType.int, intID(int))
      case .string(let string):
        (type, valueID) = (RemoteHostActionValueType.string, stringID(string))
      }
      return { buffer in
        buffer.byte(RemoteOpcode.hostNamedAction)
        buffer.int(nameID)
        buffer.int(type)
        buffer.int(valueID)
      }
    case .setInt(let target, let value):
      let id = intID(target)
      return { buffer in
        buffer.byte(RemoteOpcode.valueIntegerChangeAction)
        buffer.int(id)
        buffer.int(value)
      }
    case .setFloat(let target, let value):
      let id = floatIDForAction(target)
      return { buffer in
        buffer.byte(RemoteOpcode.valueFloatChangeAction)
        buffer.int(id)
        buffer.float(value)
      }
    case .setString(let target, let value):
      let id = stringID(target)
      let valueID = textID(value)
      return { buffer in
        buffer.byte(RemoteOpcode.valueStringChangeAction)
        buffer.int(id)
        buffer.int(valueID)
      }
    }
  }

  /// The id of a float an action names: a constant is given a definition of its own.
  private func floatIDForAction(_ value: RemoteFloat) -> Int {
    let word = floatWord(value)
    if case .constant(let literal) = value.node { return declareFloatConstant(literal) }
    return Int(word.bitPattern & RemoteIDEncoding.payloadMask)
  }

  /// [argb] as the red, green, blue and alpha floats in 0...1 a modifier writes.
  static func components(_ argb: UInt32) -> [Float] {
    [16, 8, 0, 24].map { Float((argb >> UInt32($0)) & 0xFF) / 255 }
  }
}

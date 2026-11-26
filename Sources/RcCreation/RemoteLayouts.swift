/// Where a child sits on the cross axis of a row or column, or inside a box.
public enum RemoteAlignment: Sendable {
  /// Horizontal placement, for a column's children.
  public enum Horizontal: Sendable {
    case start, center, end

    var wire: Int {
      switch self {
      case .start: RemotePositioning.start
      case .center: RemotePositioning.center
      case .end: RemotePositioning.end
      }
    }
  }

  /// Vertical placement, for a row's children.
  public enum Vertical: Sendable {
    case top, center, bottom

    var wire: Int {
      switch self {
      case .top: RemotePositioning.top
      case .center: RemotePositioning.center
      case .bottom: RemotePositioning.bottom
      }
    }
  }

  case aligned(Horizontal, Vertical)

  public static let topStart = aligned(.start, .top)
  public static let topCenter = aligned(.center, .top)
  public static let topEnd = aligned(.end, .top)
  public static let centerStart = aligned(.start, .center)
  public static let center = aligned(.center, .center)
  public static let centerEnd = aligned(.end, .center)
  public static let bottomStart = aligned(.start, .bottom)
  public static let bottomCenter = aligned(.center, .bottom)
  public static let bottomEnd = aligned(.end, .bottom)
}

/// How a row or column distributes its children along its main axis, as Compose's `Arrangement`.
public enum RemoteArrangement: Sendable {
  case start, center, end, spaceBetween, spaceEvenly, spaceAround
  /// Children packed at the start with [dp] between each.
  case spacedBy(Float)

  /// The top of a column: `start` by another name.
  public static let top = RemoteArrangement.start
  /// The bottom of a column: `end` by another name.
  public static let bottom = RemoteArrangement.end

  func wire(vertical: Bool) -> (positioning: Int, spacing: Float) {
    switch self {
    case .start: (vertical ? RemotePositioning.top : RemotePositioning.start, 0)
    case .center: (RemotePositioning.center, 0)
    case .end: (vertical ? RemotePositioning.bottom : RemotePositioning.end, 0)
    case .spaceBetween: (RemotePositioning.spaceBetween, 0)
    case .spaceEvenly: (RemotePositioning.spaceEvenly, 0)
    case .spaceAround: (RemotePositioning.spaceAround, 0)
    case .spacedBy(let dp): (vertical ? RemotePositioning.top : RemotePositioning.start, dp)
    }
  }
}

/// Children stacked on top of each other, each placed by [contentAlignment], as Compose's `Box`.
public struct RemoteBox: RemoteView, RemotePrimitiveView {
  let modifier: RemoteModifier
  let contentAlignment: RemoteAlignment
  let content: RemoteContent

  public init(
    modifier: RemoteModifier = .none,
    contentAlignment: RemoteAlignment = .topStart,
    @RemoteContentBuilder content: () -> RemoteContent = { .empty }
  ) {
    self.modifier = modifier
    self.contentAlignment = contentAlignment
    self.content = content()
  }

  public var body: Never { return fatalError("RemoteBox writes itself") }

  func write(to context: RemoteCreationContext) {
    guard case .aligned(let horizontal, let vertical) = contentAlignment else { return }
    context.writeLayout(
      RemoteOpcode.layoutBox, modifier: modifier, kind: .box, content: content
    ) { body in
      body.int(horizontal.wire)
      body.int(vertical.wire)
    }
  }
}

/// Children placed side by side, as Compose's `Row`.
public struct RemoteRow: RemoteView, RemotePrimitiveView {
  let modifier: RemoteModifier
  let horizontalArrangement: RemoteArrangement
  let verticalAlignment: RemoteAlignment.Vertical
  let content: RemoteContent

  public init(
    modifier: RemoteModifier = .none,
    horizontalArrangement: RemoteArrangement = .start,
    verticalAlignment: RemoteAlignment.Vertical = .top,
    @RemoteContentBuilder content: () -> RemoteContent
  ) {
    self.modifier = modifier
    self.horizontalArrangement = horizontalArrangement
    self.verticalAlignment = verticalAlignment
    self.content = content()
  }

  public var body: Never { return fatalError("RemoteRow writes itself") }

  func write(to context: RemoteCreationContext) {
    let (positioning, spacing) = horizontalArrangement.wire(vertical: false)
    context.writeLayout(
      RemoteOpcode.layoutRow, modifier: modifier, kind: .row, content: content
    ) { body in
      body.int(positioning)
      body.int(verticalAlignment.wire)
      body.float(context.px(spacing))
    }
  }
}

/// Children placed one above another, as Compose's `Column`.
public struct RemoteColumn: RemoteView, RemotePrimitiveView {
  let modifier: RemoteModifier
  let verticalArrangement: RemoteArrangement
  let horizontalAlignment: RemoteAlignment.Horizontal
  let content: RemoteContent

  public init(
    modifier: RemoteModifier = .none,
    verticalArrangement: RemoteArrangement = .top,
    horizontalAlignment: RemoteAlignment.Horizontal = .start,
    @RemoteContentBuilder content: () -> RemoteContent
  ) {
    self.modifier = modifier
    self.verticalArrangement = verticalArrangement
    self.horizontalAlignment = horizontalAlignment
    self.content = content()
  }

  public var body: Never { return fatalError("RemoteColumn writes itself") }

  func write(to context: RemoteCreationContext) {
    let (positioning, spacing) = verticalArrangement.wire(vertical: true)
    context.writeLayout(
      RemoteOpcode.layoutColumn, modifier: modifier, kind: .column, content: content
    ) { body in
      body.int(horizontalAlignment.wire)
      body.int(positioning)
      body.float(context.px(spacing))
    }
  }
}

/// Empty space, sized by its modifier: `RemoteSpacer(modifier: .height(8))`.
public struct RemoteSpacer: RemoteView, RemotePrimitiveView {
  let modifier: RemoteModifier

  public init(modifier: RemoteModifier) { self.modifier = modifier }

  public var body: Never { return fatalError("RemoteSpacer writes itself") }

  func write(to context: RemoteCreationContext) {
    RemoteBox(modifier: modifier).write(to: context)
  }
}

extension RemoteCreationContext {
  /// A layout as AndroidX's writer shapes it: the layout operation, its modifiers, a
  /// `LayoutContent` holding its children, and a `ContainerEnd` for each of the two.
  func writeLayout(
    _ opcode: Int, modifier: RemoteModifier, kind: RemoteParentKind, content: RemoteContent,
    fields: (inout RemoteByteBuffer) -> Void
  ) {
    let componentID = newComponentID()
    // Declarations first, so an expression a modifier reads is never written inside the layout.
    var modifierBody = RemoteByteBuffer()
    swap(&modifierBody, &body)
    write(modifier)
    swap(&modifierBody, &body)

    op(opcode)
    body.int(componentID)
    body.int(remoteNoID)  // No animation id.
    fields(&body)
    body.append(modifierBody)
    contentSection()
    withParent(kind) { write(content) }
    containerEnd()
    containerEnd()
  }
}

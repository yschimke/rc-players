import RcWireOperations

/// Where a view sits across a vertical stack, or along the horizontal axis of a z-stack.
public enum RemoteHorizontalAlignment: Hashable, Sendable {
  case leading, center, trailing

  var wire: Int32 {
    switch self {
    case .leading: BoxLayout.start
    case .center: BoxLayout.center
    case .trailing: BoxLayout.end
    }
  }
}

/// Where a view sits across a horizontal stack, or along the vertical axis of a z-stack.
public enum RemoteVerticalAlignment: Hashable, Sendable {
  case top, center, bottom

  var wire: Int32 {
    switch self {
    case .top: BoxLayout.top
    case .center: BoxLayout.center
    case .bottom: BoxLayout.bottom
    }
  }
}

/// A position on both axes, for ``RemoteZStack``.
public struct RemoteAlignment: Hashable, Sendable {
  public var horizontal: RemoteHorizontalAlignment
  public var vertical: RemoteVerticalAlignment

  public init(horizontal: RemoteHorizontalAlignment, vertical: RemoteVerticalAlignment) {
    self.horizontal = horizontal
    self.vertical = vertical
  }

  public static let topLeading = RemoteAlignment(horizontal: .leading, vertical: .top)
  public static let top = RemoteAlignment(horizontal: .center, vertical: .top)
  public static let topTrailing = RemoteAlignment(horizontal: .trailing, vertical: .top)
  public static let leading = RemoteAlignment(horizontal: .leading, vertical: .center)
  public static let center = RemoteAlignment(horizontal: .center, vertical: .center)
  public static let trailing = RemoteAlignment(horizontal: .trailing, vertical: .center)
  public static let bottomLeading = RemoteAlignment(horizontal: .leading, vertical: .bottom)
  public static let bottom = RemoteAlignment(horizontal: .center, vertical: .bottom)
  public static let bottomTrailing = RemoteAlignment(horizontal: .trailing, vertical: .bottom)
}

/// How a horizontal stack places its children along its width when they leave space over.
public enum RemoteHorizontalDistribution: Hashable, Sendable {
  /// Packed at the leading edge (the default).
  case leading
  case center
  case trailing
  /// The first and last at the edges, the rest evenly between.
  case spaceBetween
  /// Equal space around each child, so half as much at the edges.
  case spaceAround
  /// Equal space between the children and at the edges.
  case spaceEvenly

  var wire: Int32 {
    switch self {
    case .leading: RowLayout.start
    case .center: RowLayout.center
    case .trailing: RowLayout.end
    case .spaceBetween: RowLayout.spaceBetween
    case .spaceAround: RowLayout.spaceAround
    case .spaceEvenly: RowLayout.spaceEvenly
    }
  }
}

/// How a vertical stack places its children along its height when they leave space over.
public enum RemoteVerticalDistribution: Hashable, Sendable {
  /// Packed at the top (the default).
  case top
  case center
  case bottom
  case spaceBetween
  case spaceAround
  case spaceEvenly

  var wire: Int32 {
    switch self {
    case .top: ColumnLayout.top
    case .center: ColumnLayout.center
    case .bottom: ColumnLayout.bottom
    case .spaceBetween: ColumnLayout.spaceBetween
    case .spaceAround: ColumnLayout.spaceAround
    case .spaceEvenly: ColumnLayout.spaceEvenly
    }
  }
}

/// Children side by side, as SwiftUI's `HStack` (a Compose `Row`).
///
/// ```swift
/// RemoteHStack(alignment: .center, spacing: 12) {
///   Avatar().frame(width: 40, height: 40)
///   RemoteText("Title").flexWeight(1)
/// }
/// ```
public struct RemoteHStack<Content: RemoteStackContent>: _PrimitiveRemoteView {
  let alignment: RemoteVerticalAlignment
  let spacing: Double
  let distribution: RemoteHorizontalDistribution
  let content: Content

  /// - Parameters:
  ///   - alignment: Where each child sits across the stack's height.
  ///   - spacing: Points between adjacent children.
  ///   - distribution: Where the children sit when they leave width over.
  public init(
    alignment: RemoteVerticalAlignment = .center, spacing: Double = 0,
    distribution: RemoteHorizontalDistribution = .leading,
    @RemoteViewBuilder content: () -> Content
  ) {
    self.alignment = alignment
    self.spacing = spacing
    self.distribution = distribution
    self.content = content()
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    let componentID = writer.newComponentID()
    let layout = RowLayout(
      componentId: componentID, animationId: IdEncoding.noId,
      horizontalPositioning: distribution.wire, verticalPositioning: alignment.wire,
      spacedBy: writer.pixels(spacing))
    try writer.writeLayout(.rowLayout(layout), kind: .horizontalStack, content: content)
  }
}

/// Children one above another, as SwiftUI's `VStack` (a Compose `Column`).
public struct RemoteVStack<Content: RemoteStackContent>: _PrimitiveRemoteView {
  let alignment: RemoteHorizontalAlignment
  let spacing: Double
  let distribution: RemoteVerticalDistribution
  let content: Content

  /// - Parameters:
  ///   - alignment: Where each child sits across the stack's width.
  ///   - spacing: Points between adjacent children.
  ///   - distribution: Where the children sit when they leave height over.
  public init(
    alignment: RemoteHorizontalAlignment = .center, spacing: Double = 0,
    distribution: RemoteVerticalDistribution = .top,
    @RemoteViewBuilder content: () -> Content
  ) {
    self.alignment = alignment
    self.spacing = spacing
    self.distribution = distribution
    self.content = content()
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    let componentID = writer.newComponentID()
    let layout = ColumnLayout(
      componentId: componentID, animationId: IdEncoding.noId,
      horizontalPositioning: alignment.wire, verticalPositioning: distribution.wire,
      spacedBy: writer.pixels(spacing))
    try writer.writeLayout(.columnLayout(layout), kind: .verticalStack, content: content)
  }
}

/// Children on top of each other, later ones in front, as SwiftUI's `ZStack` (a Compose `Box`).
public struct RemoteZStack<Content: RemoteView>: _PrimitiveRemoteView {
  let alignment: RemoteAlignment
  let content: Content

  public init(
    alignment: RemoteAlignment = .center, @RemoteViewBuilder content: () -> Content
  ) {
    self.alignment = alignment
    self.content = content()
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    let componentID = writer.newComponentID()
    let layout = BoxLayout(
      componentId: componentID, animationId: IdEncoding.noId,
      horizontalPositioning: alignment.horizontal.wire,
      verticalPositioning: alignment.vertical.wire)
    try writer.writeLayout(.boxLayout(layout), kind: .zStack, content: content)
  }
}

/// Space. In a stack, `RemoteSpacer()` takes the space the other children leave, shared with any
/// other spacers and flex-weighted views; `RemoteSpacer(length:)` is a fixed gap along the stack.
public struct RemoteSpacer: _PrimitiveRemoteView {
  let length: RemoteFloat?

  /// Flexible space.
  public init() { length = nil }

  /// A gap of `length` points along the enclosing stack.
  public init(length: RemoteFloat) { self.length = length }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try writer.writeSpacer(length: length)
  }
}

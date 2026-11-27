import RcWireOperations

/// The axis a ``RemoteScrollView`` scrolls along.
public enum RemoteAxis: Hashable, Sendable {
  case horizontal, vertical
}

/// Content that scrolls when it is longer than the space offered, as SwiftUI's `ScrollView`.
///
/// ```swift
/// RemoteScrollView {
///   RemoteForEach(rows) { row in RowView(row) }
/// }
///
/// let position = RemoteScrollPosition()
/// RemoteScrollView(.horizontal, position: position, pageCount: 4) {
///   RemoteForEach(cards) { card in CardView(card).frame(width: 300) }
/// }
/// RemoteText("Page \(RemoteInt(truncating: position.offset / 300 + 0.5) + 1) of 4")
/// ```
///
/// The scroll view takes all the space it is offered, as SwiftUI's does. Its children are laid out
/// along its axis, as a ``RemoteVStack`` or ``RemoteHStack`` with no spacing would lay them out,
/// with as much length as they want; the player clips them to the scroll view and scrolls them on
/// a drag, without the host.
public struct RemoteScrollView<Content: RemoteStackContent>: RemoteView, RemotePrimitive {
  public typealias Body = Never

  let configuration: RemoteScrollConfiguration
  let content: Content

  /// - Parameters:
  ///   - axis: The axis the content scrolls along.
  ///   - position: Where the scroll is, for other views to read. A position belongs to one
  ///     scroll view.
  ///   - initialOffset: How far the content starts scrolled, in points.
  ///   - pageCount: Snaps the scroll to this many equal pages of the content, as SwiftUI's paging
  ///     behavior does when each page is the scroll view's length. `nil` scrolls freely.
  public init(
    _ axis: RemoteAxis = .vertical, position: RemoteScrollPosition? = nil,
    initialOffset: Double = 0, pageCount: Int? = nil,
    @RemoteViewBuilder content: () -> Content
  ) {
    configuration = RemoteScrollConfiguration(
      axis: axis, position: position, initialOffset: initialOffset, pageCount: pageCount,
      spacing: 0, alignment: .center)
    self.content = content()
  }

  /// A vertical scroll view whose children are spaced and aligned as a ``RemoteVStack``'s would
  /// be: for a component that would otherwise put a stack inside the scroll view, one level
  /// deeper in the document.
  package init(
    spacing: Double, alignment: RemoteHorizontalAlignment,
    @RemoteViewBuilder content: () -> Content
  ) {
    configuration = RemoteScrollConfiguration(
      axis: .vertical, position: nil, initialOffset: 0, pageCount: nil, spacing: spacing,
      alignment: alignment)
    self.content = content()
  }

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    try writer.writeScrollView(configuration, content: content)
  }
}

/// Where a ``RemoteScrollView`` is scrolled, as the player moves it: values other views read, as
/// SwiftUI's `ScrollPosition` and `onScrollGeometryChange` give.
///
/// ```swift
/// let position = RemoteScrollPosition()
///
/// RemoteZStack(alignment: .top) {
///   RemoteScrollView(position: position) { … }
///   Header().opacity(position.offset / 100)   // fades in over the first 100 points
/// }
/// ```
///
/// Each position is its own scroll, by identity: make one per scroll view and keep it in a `let`.
/// The player moves the position; a document cannot set it.
public final class RemoteScrollPosition: Hashable, Sendable {
  /// What a position reads.
  enum Value: Hashable, Sendable {
    case offset, maximumOffset, contentLength
  }

  public init() {}

  /// How far the content is scrolled, in points: 0 at the start.
  public var offset: RemoteFloat { RemoteFloat(node: .scroll(self, .offset)) }

  /// How far the content can scroll, in points: its length less the scroll view's.
  public var maximumOffset: RemoteFloat { RemoteFloat(node: .scroll(self, .maximumOffset)) }

  /// The length of the content along the scroll's axis, in points.
  public var contentLength: RemoteFloat { RemoteFloat(node: .scroll(self, .contentLength)) }

  public static func == (lhs: RemoteScrollPosition, rhs: RemoteScrollPosition) -> Bool {
    lhs === rhs
  }

  public func hash(into hasher: inout Hasher) { hasher.combine(ObjectIdentifier(self)) }
}

/// A scroll view's settings, as its modifier writes them.
struct RemoteScrollConfiguration: Hashable, Sendable {
  let axis: RemoteAxis
  let position: RemoteScrollPosition?
  let initialOffset: Double
  let pageCount: Int?
  /// Points between children, and where a vertical scroll's children sit across its width.
  let spacing: Double
  let alignment: RemoteHorizontalAlignment
}

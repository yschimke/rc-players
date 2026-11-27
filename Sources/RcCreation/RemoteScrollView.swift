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
/// RemoteScrollView(.horizontal) {
///   RemoteHStack(spacing: 12) { … }
/// }
/// ```
///
/// The scroll view takes all the space it is offered, as SwiftUI's does. Its children are laid out
/// along its axis, as a ``RemoteVStack`` or ``RemoteHStack`` with no spacing would lay them out,
/// with as much length as they want; the player clips them to the scroll view and scrolls them on
/// a drag, without the host.
public struct RemoteScrollView<Content: RemoteStackContent>: RemoteView, RemotePrimitive {
  public typealias Body = Never

  let axis: RemoteAxis
  let initialOffset: Double
  let content: Content

  /// - Parameters:
  ///   - axis: The axis the content scrolls along.
  ///   - initialOffset: How far the content starts scrolled, in points.
  public init(
    _ axis: RemoteAxis = .vertical, initialOffset: Double = 0,
    @RemoteViewBuilder content: () -> Content
  ) {
    self.axis = axis
    self.initialOffset = initialOffset
    self.content = content()
  }

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    try writer.writeScrollView(axis: axis, initialOffset: initialOffset, content: content)
  }
}

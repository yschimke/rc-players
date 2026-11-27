/// A reusable modifier, as SwiftUI's `ViewModifier`: a function from a view to a new one, applied
/// with ``RemoteView/modifier(_:)``.
///
/// ```swift
/// struct Card: RemoteViewModifier {
///   func body(content: Content) -> some RemoteView {
///     content
///       .padding(16)
///       .background(.white, in: .roundedRectangle(cornerRadius: 12))
///   }
/// }
///
/// RemoteText("Hello").modifier(Card())
/// ```
public protocol RemoteViewModifier: Sendable {
  /// The type of view the modifier makes.
  associatedtype Body: RemoteView

  /// The view the modifier is applied to.
  typealias Content = RemoteViewModifierContent

  @RemoteViewBuilder func body(content: Content) -> Body
}

/// The view a ``RemoteViewModifier`` is applied to, inside its `body`.
public struct RemoteViewModifierContent: RemoteView, RemotePrimitive {
  public typealias Body = Never

  let view: any RemoteView

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    try writer.write(view)
  }
}

/// A view and a ``RemoteViewModifier``: what ``RemoteView/modifier(_:)`` returns.
public struct RemoteModifiedByViewModifier<Content: RemoteView, Modifier: RemoteViewModifier>:
  RemoteView
{
  let content: Content
  let modifier: Modifier

  public var body: Modifier.Body {
    modifier.body(content: RemoteViewModifierContent(view: content))
  }
}

extension RemoteView {
  /// Applies `modifier` to this view.
  public func modifier<Modifier: RemoteViewModifier>(_ modifier: Modifier)
    -> RemoteModifiedByViewModifier<Self, Modifier>
  {
    RemoteModifiedByViewModifier(content: self, modifier: modifier)
  }
}

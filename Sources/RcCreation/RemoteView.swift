/// A piece of remote UI, composed the way a SwiftUI `View` or a Compose `@Composable` is.
///
/// The built-in components (``RemoteBox``, ``RemoteRow``, ``RemoteColumn``, ``RemoteText``,
/// ``RemoteSpacer``, ``RemoteCanvas``) write operations. Your own components are structs whose
/// ``body`` composes those:
///
/// ```swift
/// struct Badge: RemoteView {
///   let label: RemoteString
///   var body: some RemoteView {
///     RemoteBox(modifier: .background(.red, shape: .rounded(8)).padding(horizontal: 8, vertical: 2)) {
///       RemoteText(label, color: .white, fontSize: 12)
///     }
///   }
/// }
/// ```
public protocol RemoteView {
  associatedtype Body: RemoteView
  @RemoteContentBuilder var body: Body { get }
}

extension Never: RemoteView {
  public var body: Never { return fatalError("Never has no body") }
}

/// The components that write operations themselves rather than through a ``RemoteView/body``.
protocol RemotePrimitiveView {
  func write(to context: RemoteCreationContext)
}

extension RemoteCreationContext {
  func write(_ view: any RemoteView) {
    if let primitive = view as? any RemotePrimitiveView {
      primitive.write(to: self)
    } else {
      write(view.body)
    }
  }
}

/// A sequence of views, as a result builder produces it.
public struct RemoteContent: RemoteView, RemotePrimitiveView {
  let views: [any RemoteView]

  init(_ views: [any RemoteView]) { self.views = views }

  /// No views: a layout with no children.
  public static var empty: RemoteContent { RemoteContent([]) }

  public var body: Never { return fatalError("RemoteContent writes itself") }

  func write(to context: RemoteCreationContext) {
    for view in views { context.write(view) }
  }
}

/// Builds the children of a layout from statements, `if`, `switch` and `for`, as `@ViewBuilder`
/// does.
@resultBuilder
public enum RemoteContentBuilder {
  public static func buildExpression(_ view: any RemoteView) -> [any RemoteView] { [view] }
  public static func buildExpression(_ views: [any RemoteView]) -> [any RemoteView] { views }
  public static func buildBlock(_ parts: [any RemoteView]...) -> [any RemoteView] {
    parts.flatMap { $0 }
  }
  public static func buildOptional(_ part: [any RemoteView]?) -> [any RemoteView] { part ?? [] }
  public static func buildEither(first part: [any RemoteView]) -> [any RemoteView] { part }
  public static func buildEither(second part: [any RemoteView]) -> [any RemoteView] { part }
  public static func buildArray(_ parts: [[any RemoteView]]) -> [any RemoteView] {
    parts.flatMap { $0 }
  }
  public static func buildLimitedAvailability(_ part: [any RemoteView]) -> [any RemoteView] {
    part
  }
  public static func buildFinalResult(_ parts: [any RemoteView]) -> RemoteContent {
    RemoteContent(parts)
  }
}

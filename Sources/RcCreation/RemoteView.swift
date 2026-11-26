/// Anything a stack's content builder accepts: every ``RemoteView``, and a view given a
/// ``RemoteView/flexWeight(_:)``, which only ``RemoteHStack`` and ``RemoteVStack`` can lay out.
///
/// A flex-weighted view is stack content but not a view, so placing one anywhere else is a
/// compile-time error rather than a document a player cannot lay out.
public protocol RemoteStackContent: Sendable {
  /// Writes this content's operations. The encoder calls it; it is public only so that the
  /// protocol can be, and it is not part of the API.
  func _write(to writer: _RemoteWriter) throws(RemoteEncodingError)
}

/// A piece of remote UI, composed as a SwiftUI `View` is.
///
/// The built-in views (``RemoteVStack``, ``RemoteHStack``, ``RemoteZStack``, ``RemoteText``,
/// ``RemoteCanvas``, ``RemoteSpacer``, ``RemoteColor``) write operations. Your own views are
/// structs whose `body` composes those, and every modifier applies to them as it does to a
/// built-in view:
///
/// ```swift
/// struct Badge: RemoteView {
///   let label: RemoteString
///   var body: some RemoteView {
///     RemoteText(label)
///       .font(.caption)
///       .foregroundStyle(.white)
///       .padding(.horizontal, 8)
///       .background(.red, in: .roundedRectangle(cornerRadius: 8))
///   }
/// }
///
/// Badge(label: "New").padding(4)
/// ```
///
/// The tree is static: each view's type records its children's types, as SwiftUI's does, and
/// nothing is boxed or downcast while a document is written.
public protocol RemoteView: RemoteStackContent {
  /// The type of view this view's body is.
  associatedtype Body: RemoteView

  /// The view's content.
  @RemoteViewBuilder var body: Body { get }
}

extension RemoteView {
  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try body._write(to: writer)
  }
}

extension Never: RemoteView {
  public var body: Never { fatalError("Never has no body") }
}

/// A view that writes its operations itself rather than through a `body`.
///
/// Every built-in view is one. Its `body` is `Never`, and is never read.
public protocol _PrimitiveRemoteView: RemoteView where Body == Never {}

extension _PrimitiveRemoteView {
  public var body: Never { fatalError("\(Self.self) writes its own operations") }
}

/// No view: what an empty builder produces.
public struct EmptyRemoteView: _PrimitiveRemoteView {
  public init() {}

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {}
}

/// Two pieces of content, one after the other: what the builder makes of consecutive statements.
public struct _PairRemoteView<First: RemoteStackContent, Second: RemoteStackContent>:
  RemoteStackContent
{
  let first: First
  let second: Second

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try first._write(to: writer)
    try second._write(to: writer)
  }
}

extension _PairRemoteView: RemoteView, _PrimitiveRemoteView
where First: RemoteView, Second: RemoteView {}

/// One of two pieces of content, as an `if`–`else` or a `switch` chose.
public struct _ConditionalRemoteView<First: RemoteStackContent, Second: RemoteStackContent>:
  RemoteStackContent
{
  enum Storage: Sendable {
    case first(First)
    case second(Second)
  }

  let storage: Storage

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    switch storage {
    case .first(let content): try content._write(to: writer)
    case .second(let content): try content._write(to: writer)
    }
  }
}

extension _ConditionalRemoteView: RemoteView, _PrimitiveRemoteView
where First: RemoteView, Second: RemoteView {}

extension Optional: RemoteStackContent where Wrapped: RemoteStackContent {
  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    if let content = self { try content._write(to: writer) }
  }
}

extension Optional: RemoteView, _PrimitiveRemoteView where Wrapped: RemoteView {}

extension Array: RemoteStackContent where Element: RemoteStackContent {
  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    for content in self { try content._write(to: writer) }
  }
}

extension Array: RemoteView, _PrimitiveRemoteView where Element: RemoteView {}

/// A view whose type is hidden: for `if #available` branches, and for storing views of
/// different types together.
public struct AnyRemoteView: _PrimitiveRemoteView {
  let view: any RemoteView

  public init(_ view: some RemoteView) { self.view = view }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try view._write(to: writer)
  }
}

/// Builds a view's content from statements, `if`, `switch` and `for`, as SwiftUI's
/// `@ViewBuilder` does.
///
/// Consecutive statements fold pairwise (`buildPartialBlock`), so a block of any length is one
/// static type. Parameter packs would give the same tree, but a variadic generic type needs the
/// Swift 5.9 runtime (iOS 17, macOS 14), above this package's floor.
@resultBuilder
public enum RemoteViewBuilder {
  public static func buildExpression<Content: RemoteStackContent>(_ content: Content) -> Content {
    content
  }

  public static func buildBlock() -> EmptyRemoteView { EmptyRemoteView() }

  public static func buildPartialBlock<Content: RemoteStackContent>(first content: Content)
    -> Content
  {
    content
  }

  public static func buildPartialBlock<First: RemoteStackContent, Next: RemoteStackContent>(
    accumulated: First, next: Next
  ) -> _PairRemoteView<First, Next> {
    _PairRemoteView(first: accumulated, second: next)
  }

  public static func buildOptional<Content: RemoteStackContent>(_ content: Content?) -> Content? {
    content
  }

  public static func buildEither<First: RemoteStackContent, Second: RemoteStackContent>(
    first content: First
  ) -> _ConditionalRemoteView<First, Second> {
    _ConditionalRemoteView(storage: .first(content))
  }

  public static func buildEither<First: RemoteStackContent, Second: RemoteStackContent>(
    second content: Second
  ) -> _ConditionalRemoteView<First, Second> {
    _ConditionalRemoteView(storage: .second(content))
  }

  public static func buildArray<Content: RemoteStackContent>(_ content: [Content]) -> [Content] {
    content
  }

  public static func buildLimitedAvailability(_ content: some RemoteView) -> AnyRemoteView {
    AnyRemoteView(content)
  }
}

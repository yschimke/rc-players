/// Anything a stack's content builder accepts: every ``RemoteView``, and a view given a
/// ``RemoteView/flexWeight(_:)``, which only ``RemoteHStack`` and ``RemoteVStack`` can lay out.
///
/// A flex-weighted view is stack content but not a view, so placing one anywhere else is a
/// compile-time error rather than a document a player cannot lay out.
public protocol RemoteStackContent: Sendable {}

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
/// The tree is static: each view's type records its children's types, as SwiftUI's does.
public protocol RemoteView: RemoteStackContent {
  /// The type of view this view's body is.
  associatedtype Body: RemoteView

  /// The view's content.
  @RemoteViewBuilder var body: Body { get }
}

extension Never: RemoteView {
  public typealias Body = Never
}

extension RemoteView where Body == Never {
  /// A built-in view writes its own operations; it has no body to read.
  public var body: Never { fatalError("\(Self.self) writes its own operations") }
}

/// Content that writes its own operations: every built-in view, and the builder's own types.
/// Package-internal, so the writer stays out of the public API; a view outside this package is
/// written through its `body`.
package protocol RemotePrimitive: RemoteStackContent {
  func write(to writer: RemoteWriter) throws(RemoteEncodingError)
}

/// Content that can tell whether it holds a flex-weighted child, through the builder's own
/// types: `flexWeight` is stack content, so it is only ever a stack's direct child.
package protocol RemoteFlexWeightReporting {
  var containsFlexWeight: Bool { get }
}

/// A wrapper that writes its content with something added (a modifier, an environment value):
/// what the stacks look through to find a spacer.
package protocol RemoteContentWrapping {
  var wrappedContent: any RemoteStackContent { get }
}

/// Whether `content` holds a child that shares a stack's leftover space: one given a
/// ``RemoteView/flexWeight(_:)``, or a flexible ``RemoteSpacer``, however modified.
func containsFlexWeight(_ content: some RemoteStackContent) -> Bool {
  if let reporting = content as? any RemoteFlexWeightReporting {
    return reporting.containsFlexWeight
  }
  if let wrapper = content as? any RemoteContentWrapping {
    return containsFlexWeight(wrapper.wrappedContent)
  }
  // A view of your own is what its body is.
  if !(content is any RemotePrimitive), let view = content as? any RemoteView {
    return containsFlexWeight(body(of: view))
  }
  return false
}

private func body(of view: some RemoteView) -> any RemoteStackContent { view.body }

/// Whether `content` is a ``RemoteSpacer``, however modified: it has no children to draw.
func isSpacer(_ content: some RemoteStackContent) -> Bool {
  if content is RemoteSpacer { return true }
  if let wrapper = content as? any RemoteContentWrapping { return isSpacer(wrapper.wrappedContent) }
  return false
}

extension RemoteTupleView: RemoteFlexWeightReporting {
  package var containsFlexWeight: Bool {
    RcCreation.containsFlexWeight(first) || RcCreation.containsFlexWeight(second)
  }
}

extension RemoteConditionalContent: RemoteFlexWeightReporting {
  package var containsFlexWeight: Bool {
    switch storage {
    case .first(let content): RcCreation.containsFlexWeight(content)
    case .second(let content): RcCreation.containsFlexWeight(content)
    }
  }
}

extension Optional: RemoteFlexWeightReporting where Wrapped: RemoteStackContent {
  package var containsFlexWeight: Bool { map { RcCreation.containsFlexWeight($0) } ?? false }
}

extension RemoteForEach: RemoteFlexWeightReporting {
  package var containsFlexWeight: Bool { contents.contains { RcCreation.containsFlexWeight($0) } }
}

/// No view: what an empty builder produces.
public struct EmptyRemoteView: RemoteView, RemotePrimitive {
  public typealias Body = Never

  public init() {}

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {}
}

/// Two pieces of content, one after the other: what the builder makes of consecutive statements.
public struct RemoteTupleView<First: RemoteStackContent, Second: RemoteStackContent>:
  RemoteStackContent, RemotePrimitive
{
  let first: First
  let second: Second

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    try writer.write(first)
    try writer.write(second)
  }
}

extension RemoteTupleView: RemoteView where First: RemoteView, Second: RemoteView {
  public typealias Body = Never
}

/// One of two pieces of content, as an `if`–`else` or a `switch` chose.
public struct RemoteConditionalContent<First: RemoteStackContent, Second: RemoteStackContent>:
  RemoteStackContent, RemotePrimitive
{
  enum Storage: Sendable {
    case first(First)
    case second(Second)
  }

  let storage: Storage

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    switch storage {
    case .first(let content): try writer.write(content)
    case .second(let content): try writer.write(content)
    }
  }
}

extension RemoteConditionalContent: RemoteView where First: RemoteView, Second: RemoteView {
  public typealias Body = Never
}

extension Optional: RemoteStackContent where Wrapped: RemoteStackContent {}

extension Optional: RemotePrimitive where Wrapped: RemoteStackContent {
  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    if let content = self { try writer.write(content) }
  }
}

extension Optional: RemoteView where Wrapped: RemoteView {
  public typealias Body = Never
}

/// One piece of content for each element of a collection, as SwiftUI's `ForEach`: the way to
/// repeat views in a builder.
///
/// ```swift
/// RemoteHStack(spacing: 8) {
///   RemoteForEach(["VStack", "HStack", "ZStack"]) { label in
///     Chip(label: label)
///   }
/// }
/// ```
///
/// The collection is read when the view is built, and each element's content is written in
/// order. Content can be stack content, so a stack's repeated children can take a flex weight.
public struct RemoteForEach<Content: RemoteStackContent>: RemoteStackContent, RemotePrimitive {
  let contents: [Content]

  public init<Data: Sequence>(
    _ data: Data, @RemoteViewBuilder content: (Data.Element) -> Content
  ) {
    contents = data.map(content)
  }

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    for content in contents { try writer.write(content) }
  }
}

extension RemoteForEach: RemoteView where Content: RemoteView {
  public typealias Body = Never
}

/// A view whose type is hidden: for `if #available` branches, and for storing views of
/// different types together.
public struct AnyRemoteView: RemoteView, RemotePrimitive {
  public typealias Body = Never

  let view: any RemoteView

  public init(_ view: some RemoteView) { self.view = view }

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    try writer.write(view)
  }
}

/// Builds a view's content from statements, `if` and `switch`, as SwiftUI's `@ViewBuilder` does.
/// Repeated content is a ``RemoteForEach``.
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
  ) -> RemoteTupleView<First, Next> {
    RemoteTupleView(first: accumulated, second: next)
  }

  public static func buildOptional<Content: RemoteStackContent>(_ content: Content?) -> Content? {
    content
  }

  public static func buildEither<First: RemoteStackContent, Second: RemoteStackContent>(
    first content: First
  ) -> RemoteConditionalContent<First, Second> {
    RemoteConditionalContent(storage: .first(content))
  }

  public static func buildEither<First: RemoteStackContent, Second: RemoteStackContent>(
    second content: Second
  ) -> RemoteConditionalContent<First, Second> {
    RemoteConditionalContent(storage: .second(content))
  }

  public static func buildLimitedAvailability(_ content: some RemoteView) -> AnyRemoteView {
    AnyRemoteView(content)
  }
}

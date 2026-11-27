/// A value views read from their surroundings, as SwiftUI's `EnvironmentKey`. Component libraries
/// define keys for their themes; views below a ``RemoteView/environment(_:_:)`` modifier see the
/// value it sets.
///
/// ```swift
/// struct AccentKey: RemoteEnvironmentKey {
///   static let defaultValue = RemoteColor.blue
/// }
/// extension RemoteEnvironmentValues {
///   var accent: RemoteColor {
///     get { self[AccentKey.self] }
///     set { self[AccentKey.self] = newValue }
///   }
/// }
/// ```
public protocol RemoteEnvironmentKey {
  associatedtype Value: Sendable
  static var defaultValue: Value { get }
}

/// The values a view's surroundings set, by key.
public struct RemoteEnvironmentValues: Sendable {
  private var values: [ObjectIdentifier: any Sendable] = [:]

  public init() {}

  public subscript<Key: RemoteEnvironmentKey>(key: Key.Type) -> Key.Value {
    get { values[ObjectIdentifier(key)] as? Key.Value ?? Key.defaultValue }
    set { values[ObjectIdentifier(key)] = newValue }
  }
}

private struct TintKey: RemoteEnvironmentKey {
  /// Apple's system blue, light and dark, as SwiftUI's default accent.
  static let defaultValue = RemoteColor.adaptive(light: .hex(0x007AFF), dark: .hex(0x0A84FF))
}

private struct ForegroundStyleKey: RemoteEnvironmentKey {
  static let defaultValue = RemoteColor.primary
}

extension RemoteEnvironmentValues {
  /// The colour text and icons take unless they set their own, as SwiftUI's `foregroundStyle`.
  public var foregroundStyle: RemoteColor {
    get { self[ForegroundStyleKey.self] }
    set { self[ForegroundStyleKey.self] = newValue }
  }

  /// The accent colour controls take, as SwiftUI's `tint`.
  public var tint: RemoteColor {
    get { self[TintKey.self] }
    set { self[TintKey.self] = newValue }
  }
}

/// A view and an environment change for it and everything inside it.
public struct _EnvironmentWritingView<Content: RemoteView>: _PrimitiveRemoteView {
  let content: Content
  let transform: @Sendable (inout RemoteEnvironmentValues) -> Void

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try writer.withEnvironment(transform) { () throws(RemoteEncodingError) in
      try content._write(to: writer)
    }
  }
}

extension RemoteView {
  /// Sets an environment value for this view and everything inside it.
  public func environment<Value: Sendable>(
    _ keyPath: WritableKeyPath<RemoteEnvironmentValues, Value> & Sendable, _ value: Value
  ) -> _EnvironmentWritingView<Self> {
    _EnvironmentWritingView(content: self) { $0[keyPath: keyPath] = value }
  }

  /// The colour of the text and icons inside this view that do not set their own.
  public func foregroundStyle(_ color: RemoteColor) -> _EnvironmentWritingView<Self> {
    environment(\.foregroundStyle, color)
  }

  /// The accent colour for controls inside this view.
  public func tint(_ color: RemoteColor) -> _EnvironmentWritingView<Self> {
    environment(\.tint, color)
  }
}

/// Builds its content from the environment it is written in, as a SwiftUI view reads
/// `@Environment`:
///
/// ```swift
/// RemoteEnvironmentReader { environment in
///   RemoteColor.fixed(.white).background(environment.tint)
/// }
/// ```
public struct RemoteEnvironmentReader<Content: RemoteView>: _PrimitiveRemoteView {
  let content: @Sendable (RemoteEnvironmentValues) -> Content

  public init(@RemoteViewBuilder content: @escaping @Sendable (RemoteEnvironmentValues) -> Content)
  {
    self.content = content
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try content(writer.environment)._write(to: writer)
  }
}

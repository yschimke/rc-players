/// A control that runs actions when tapped, as SwiftUI's `Button`: its label is any view, drawn by
/// the ``RemoteButtonStyle`` in the environment, and assistive technologies read it as a button.
///
/// ```swift
/// RemoteButton(action: .toggle(isOn)) {
///   RemoteText("Wi-Fi")
/// }
///
/// RemoteButton("Delete", role: .destructive, action: .host("delete"))
///   .buttonStyle(.borderedProminent)   // RcCreationAppleUI's styles
///   .disabled(isEmpty)
/// ```
///
/// The actions run on the player, in order, without the host: writing variables, or telling the
/// host with ``RemoteAction/host(id:)``. A disabled button runs none.
public struct RemoteButton<Label: RemoteView>: RemoteView {
  let role: RemoteButtonRole?
  let actions: [RemoteAction]
  let label: Label

  public init(
    role: RemoteButtonRole? = nil, action: RemoteAction...,
    @RemoteViewBuilder label: () -> Label
  ) {
    self.role = role
    actions = action
    self.label = label()
  }

  /// A button running `actions`, an array: for actions built up as data.
  public init(
    role: RemoteButtonRole? = nil, actions: [RemoteAction],
    @RemoteViewBuilder label: () -> Label
  ) {
    self.role = role
    self.actions = actions
    self.label = label()
  }

  public var body: some RemoteView {
    let button = self
    return RemoteEnvironmentReader { environment in
      let configuration = RemoteButtonStyleConfiguration(
        label: AnyRemoteView(button.label), role: button.role,
        isEnabled: environment.isEnabled)
      let styled = environment.buttonStyle.body(configuration: configuration)
        .accessibilityElement(children: .combine)
        .accessibilityRole(.button)
      if environment.isEnabled {
        styled.onTapGesture(button.actions)
      } else {
        styled
      }
    }
  }
}

extension RemoteButton where Label == RemoteText {
  /// A button labelled with `title`.
  public init(_ title: RemoteString, role: RemoteButtonRole? = nil, action: RemoteAction...) {
    self.init(role: role, actions: action) { RemoteText(title) }
  }

  @_disfavoredOverload
  public init(_ title: some StringProtocol, role: RemoteButtonRole? = nil, action: RemoteAction...)
  {
    self.init(role: role, actions: action) { RemoteText(String(title)) }
  }
}

/// What a button does, which a style may draw differently, as SwiftUI's `ButtonRole`.
public enum RemoteButtonRole: Hashable, Sendable {
  /// Deletes or discards.
  case destructive
  /// Dismisses without acting.
  case cancel
}

/// How a control is sized, as SwiftUI's `ControlSize`: styles read it from the environment.
public enum RemoteControlSize: Hashable, Sendable, CaseIterable {
  case mini, small, regular, large
}

/// How buttons are drawn, as SwiftUI's `ButtonStyle`: set with ``RemoteView/buttonStyle(_:)`` for
/// every button inside a view.
///
/// ```swift
/// struct Pill: RemoteButtonStyle {
///   func makeBody(configuration: Configuration) -> some RemoteView {
///     configuration.label
///       .padding(.horizontal, 12)
///       .background(.blue, in: .roundedRectangle(cornerRadius: 16))
///   }
/// }
/// ```
public protocol RemoteButtonStyle: Sendable {
  associatedtype Body: RemoteView
  typealias Configuration = RemoteButtonStyleConfiguration

  @RemoteViewBuilder func makeBody(configuration: Configuration) -> Body
}

extension RemoteButtonStyle {
  /// The style's body with its type hidden, as the environment stores any style.
  func body(configuration: Configuration) -> AnyRemoteView {
    AnyRemoteView(makeBody(configuration: configuration))
  }
}

/// What a ``RemoteButtonStyle`` draws: the button's label and role, and whether it is enabled.
public struct RemoteButtonStyleConfiguration: Sendable {
  /// The button's label, as its author wrote it.
  public let label: AnyRemoteView
  public let role: RemoteButtonRole?
  /// Whether the button runs its actions: a style draws a disabled button dimmed.
  public let isEnabled: Bool
}

/// The label as written, with nothing added: the default style.
public struct RemotePlainButtonStyle: RemoteButtonStyle {
  public init() {}

  public func makeBody(configuration: Configuration) -> AnyRemoteView { configuration.label }
}

extension RemoteButtonStyle where Self == RemotePlainButtonStyle {
  /// The label as written.
  public static var plain: RemotePlainButtonStyle { RemotePlainButtonStyle() }
}

private struct ButtonStyleKey: RemoteEnvironmentKey {
  static var defaultValue: any RemoteButtonStyle { RemotePlainButtonStyle() }
}

private struct IsEnabledKey: RemoteEnvironmentKey {
  static var defaultValue: Bool { true }
}

private struct ControlSizeKey: RemoteEnvironmentKey {
  static var defaultValue: RemoteControlSize { .regular }
}

extension RemoteEnvironmentValues {
  /// How the buttons in this view are drawn.
  public var buttonStyle: any RemoteButtonStyle {
    get { self[ButtonStyleKey.self] }
    set { self[ButtonStyleKey.self] = newValue }
  }

  /// Whether the controls in this view respond to taps.
  public var isEnabled: Bool {
    get { self[IsEnabledKey.self] }
    set { self[IsEnabledKey.self] = newValue }
  }

  /// The size of the controls in this view.
  public var controlSize: RemoteControlSize {
    get { self[ControlSizeKey.self] }
    set { self[ControlSizeKey.self] = newValue }
  }
}

extension RemoteView {
  /// Draws every button inside this view with `style`.
  public func buttonStyle(_ style: some RemoteButtonStyle) -> RemoteEnvironmentContent<Self> {
    environment(\.buttonStyle, style as any RemoteButtonStyle)
  }

  /// Disables the controls inside this view: drawn dimmed, they run no actions. Disabling is
  /// decided when the document is written, as a Swift `Bool`.
  public func disabled(_ isDisabled: Bool = true) -> RemoteEnvironmentContent<Self> {
    // A view inside a disabled one stays disabled, as in SwiftUI.
    RemoteEnvironmentContent(content: self) { $0.isEnabled = $0.isEnabled && !isDisabled }
  }

  /// The size of the controls inside this view.
  public func controlSize(_ size: RemoteControlSize) -> RemoteEnvironmentContent<Self> {
    environment(\.controlSize, size)
  }
}

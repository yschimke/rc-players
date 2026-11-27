/// A control that runs actions when tapped, as SwiftUI's `Button`: its label is any view, and
/// assistive technologies read it as a button.
///
/// ```swift
/// RemoteButton(action: .toggle(isOn)) {
///   RemoteText("Wi-Fi")
///     .padding(.horizontal, 12)
///     .background(.blue, in: .roundedRectangle(cornerRadius: 8))
/// }
///
/// RemoteButton("Next", action: .increment(page, in: 0...9))
/// ```
///
/// The actions run on the player, in order, without the host: writing variables, or telling the
/// host with ``RemoteAction/host(id:)``.
public struct RemoteButton<Label: RemoteView>: RemoteView {
  let actions: [RemoteAction]
  let label: Label

  public init(action: RemoteAction..., @RemoteViewBuilder label: () -> Label) {
    actions = action
    self.label = label()
  }

  public var body: some RemoteView {
    label.onTapGesture(actions).accessibilityRole(.button)
  }
}

extension RemoteButton where Label == RemoteText {
  /// A button labelled with `title`.
  public init(_ title: RemoteString, action: RemoteAction...) {
    actions = action
    label = RemoteText(title)
  }
}

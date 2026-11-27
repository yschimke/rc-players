import RcWireOperations

/// The outline of a background or a clip.
public enum RemoteShape: Hashable, Sendable {
  case rectangle
  /// A rectangle with each corner rounded by its own radius, in points.
  case roundedCorners(
    topLeading: Double, topTrailing: Double, bottomLeading: Double, bottomTrailing: Double)

  /// A rectangle with every corner rounded by `cornerRadius` points.
  public static func roundedRectangle(cornerRadius: Double) -> RemoteShape {
    .roundedCorners(
      topLeading: cornerRadius, topTrailing: cornerRadius, bottomLeading: cornerRadius,
      bottomTrailing: cornerRadius)
  }
}

/// A view's edges, for ``RemoteView/padding(_:_:)``.
public struct RemoteEdgeSet: OptionSet, Hashable, Sendable {
  public let rawValue: UInt8
  public init(rawValue: UInt8) { self.rawValue = rawValue }

  public static let top = RemoteEdgeSet(rawValue: 1 << 0)
  public static let leading = RemoteEdgeSet(rawValue: 1 << 1)
  public static let bottom = RemoteEdgeSet(rawValue: 1 << 2)
  public static let trailing = RemoteEdgeSet(rawValue: 1 << 3)
  public static let horizontal: RemoteEdgeSet = [.leading, .trailing]
  public static let vertical: RemoteEdgeSet = [.top, .bottom]
  public static let all: RemoteEdgeSet = [.horizontal, .vertical]
}

/// Insets on each edge, in points.
public struct RemoteEdgeInsets: Hashable, Sendable {
  public var top: RemoteFloat
  public var leading: RemoteFloat
  public var bottom: RemoteFloat
  public var trailing: RemoteFloat

  public init(
    top: RemoteFloat = 0, leading: RemoteFloat = 0, bottom: RemoteFloat = 0,
    trailing: RemoteFloat = 0
  ) {
    self.top = top
    self.leading = leading
    self.bottom = bottom
    self.trailing = trailing
  }
}

/// The largest a ``RemoteView/frame(maxWidth:maxHeight:)`` lets a view grow: all the space it
/// is offered, or a fraction of it.
public struct RemoteMaxLength: Hashable, Sendable {
  let fraction: Double

  /// All the space the parent offers.
  public static let infinity = RemoteMaxLength(fraction: 1)

  /// `fraction` (0 to 1) of the space the parent offers.
  public static func fraction(_ fraction: Double) -> RemoteMaxLength {
    RemoteMaxLength(fraction: fraction)
  }
}

/// What a tap does.
public struct RemoteAction: Hashable, Sendable {
  enum Kind: Hashable, Sendable {
    case host(id: Int)
    case hostNamed(String, HostValue)
    case setDouble(RemoteVariable<Double>, Double)
    case setInt(RemoteVariable<Int>, Int)
    case setString(RemoteVariable<String>, String)
    case setVisibility(RemoteVariable<RemoteVisibility>, RemoteVisibility)
  }

  enum HostValue: Hashable, Sendable {
    case none
    case float(RemoteFloat)
    case int(RemoteVariable<Int>)
    case string(RemoteString)
  }

  let kind: Kind

  /// Tells the host action `id` happened (`HostAction`). An id outside 32 bits fails to encode
  /// with ``RemoteEncodingError/integerOutOfRange(_:)``.
  public static func host(id: Int) -> RemoteAction { RemoteAction(kind: .host(id: id)) }

  /// Tells the host the action `name` happened (`HostNamedAction`).
  public static func host(_ name: String) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .none))
  }

  /// Tells the host the action `name` happened, with the value of `value` when it did.
  public static func host(_ name: String, value: RemoteFloat) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .float(value)))
  }

  public static func host(_ name: String, value: RemoteVariable<Int>) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .int(value)))
  }

  public static func host(_ name: String, value: RemoteString) -> RemoteAction {
    RemoteAction(kind: .hostNamed(name, .string(value)))
  }

  /// Writes `value` to `variable`, in the document.
  public static func set(_ variable: RemoteVariable<Double>, to value: Double) -> RemoteAction {
    RemoteAction(kind: .setDouble(variable, value))
  }

  public static func set(_ variable: RemoteVariable<Int>, to value: Int) -> RemoteAction {
    RemoteAction(kind: .setInt(variable, value))
  }

  public static func set(_ variable: RemoteVariable<String>, to value: String) -> RemoteAction {
    RemoteAction(kind: .setString(variable, value))
  }

  public static func set(
    _ variable: RemoteVariable<RemoteVisibility>, to value: RemoteVisibility
  ) -> RemoteAction {
    RemoteAction(kind: .setVisibility(variable, value))
  }
}

/// A view's accessibility role, as `CoreSemantics` records it.
public enum RemoteAccessibilityRole: Hashable, Sendable {
  case button, checkbox, toggle, radioButton, tab, image, popUpButton, picker, carousel

  var wire: Int32 {
    switch self {
    case .button: CoreSemantics.roleButton
    case .checkbox: CoreSemantics.roleCheckbox
    case .toggle: CoreSemantics.roleSwitch
    case .radioButton: CoreSemantics.roleRadioButton
    case .tab: CoreSemantics.roleTab
    case .image: CoreSemantics.roleImage
    case .popUpButton: CoreSemantics.roleDropdownList
    case .picker: CoreSemantics.rolePicker
    case .carousel: CoreSemantics.roleCarousel
    }
  }
}

/// How a view's accessibility element relates to its children's.
public enum RemoteAccessibilityChildBehavior: Hashable, Sendable {
  /// The children are separate elements (`CoreSemantics` `MODE_SET`).
  case contain
  /// The children's labels merge into this one (`MODE_MERGE`).
  case combine
  /// The children are hidden; only this element's own label is read (`MODE_CLEAR_AND_SET`).
  case ignore

  var wire: Int32 {
    switch self {
    case .contain: CoreSemantics.modeSet
    case .combine: CoreSemantics.modeMerge
    case .ignore: CoreSemantics.modeClearAndSet
    }
  }
}

/// One modifier, as the writer applies it to the next built-in view.
enum RemoteModifierElement: Sendable {
  enum Length: Sendable {
    case exact(RemoteFloat)
    case fill(Double)
  }

  case frame(width: Length?, height: Length?)
  case padding(RemoteEdgeInsets)
  case background(RemoteColor, RemoteShape)
  case border(RemoteColor, width: Double, cornerRadius: Double)
  case clip(RemoteShape)
  case offset(x: RemoteFloat, y: RemoteFloat)
  case zIndex(Double)
  case visibility(RemoteVisibility)
  case visibilityVariable(RemoteVariable<RemoteVisibility>)
  case tap([RemoteAction])
  case accessibilityLabel(RemoteString)
  case accessibilityValue(RemoteString)
  case accessibilityRole(RemoteAccessibilityRole)
  case accessibilityChildren(RemoteAccessibilityChildBehavior)
  case flexWeight(Double)
}

/// A view and one modifier. Modifiers nest outermost-first, as SwiftUI's do:
/// `.padding(16).background(.blue)` paints the padding blue.
public struct _ModifiedRemoteView<Content: RemoteView>: _PrimitiveRemoteView {
  let content: Content
  let modifier: RemoteModifierElement

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try writer.withModifier(modifier) { () throws(RemoteEncodingError) in
      try content._write(to: writer)
    }
  }
}

/// A view a stack shares its leftover space with: what ``RemoteView/flexWeight(_:)`` makes. It is
/// stack content, not a view, so only a stack's builder accepts it.
public struct _FlexWeightedView<Content: RemoteView>: RemoteStackContent {
  let content: Content
  let weight: Double

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    try writer.withModifier(.flexWeight(weight)) { () throws(RemoteEncodingError) in
      try content._write(to: writer)
    }
  }
}

extension RemoteView {
  private func modifier(_ element: RemoteModifierElement) -> _ModifiedRemoteView<Self> {
    _ModifiedRemoteView(content: self, modifier: element)
  }

  // MARK: Size

  /// A fixed width, height or both, in points; a `nil` axis keeps the view's own size.
  public func frame(width: RemoteFloat? = nil, height: RemoteFloat? = nil) -> _ModifiedRemoteView<
    Self
  > {
    modifier(.frame(width: width.map { .exact($0) }, height: height.map { .exact($0) }))
  }

  /// Grows to the space the parent offers on each given axis: `.frame(maxWidth: .infinity)`.
  public func frame(maxWidth: RemoteMaxLength? = nil, maxHeight: RemoteMaxLength? = nil)
    -> _ModifiedRemoteView<Self>
  {
    modifier(
      .frame(
        width: maxWidth.map { .fill($0.fraction) }, height: maxHeight.map { .fill($0.fraction) }))
  }

  /// The full width the parent offers, and a fixed height.
  public func frame(maxWidth: RemoteMaxLength, height: RemoteFloat) -> _ModifiedRemoteView<Self> {
    modifier(.frame(width: .fill(maxWidth.fraction), height: .exact(height)))
  }

  /// A fixed width, and the full height the parent offers.
  public func frame(width: RemoteFloat, maxHeight: RemoteMaxLength) -> _ModifiedRemoteView<Self> {
    modifier(.frame(width: .exact(width), height: .fill(maxHeight.fraction)))
  }

  /// Takes a share of the space a stack has left after its other children, in proportion to
  /// `weight` (above 0) among the weighted children. Stack content only.
  public func flexWeight(_ weight: Double) -> _FlexWeightedView<Self> {
    _FlexWeightedView(content: self, weight: weight)
  }

  // MARK: Spacing and position

  /// `length` points on every edge. 16 points, as SwiftUI usually pads, unless given.
  public func padding(_ length: RemoteFloat = 16) -> _ModifiedRemoteView<Self> {
    padding(.all, length)
  }

  /// `length` points on the given edges.
  public func padding(_ edges: RemoteEdgeSet, _ length: RemoteFloat = 16) -> _ModifiedRemoteView<
    Self
  > {
    func inset(_ edge: RemoteEdgeSet) -> RemoteFloat { edges.contains(edge) ? length : 0 }
    return padding(
      RemoteEdgeInsets(
        top: inset(.top), leading: inset(.leading), bottom: inset(.bottom),
        trailing: inset(.trailing)))
  }

  /// Each edge's own inset. Leading is the left edge: the wire's padding is not mirrored for a
  /// right-to-left layout.
  public func padding(_ insets: RemoteEdgeInsets) -> _ModifiedRemoteView<Self> {
    modifier(.padding(insets))
  }

  /// Moves the view by `x` and `y` points without changing its layout.
  public func offset(x: RemoteFloat = 0, y: RemoteFloat = 0) -> _ModifiedRemoteView<Self> {
    modifier(.offset(x: x, y: y))
  }

  /// Draws this view above siblings with a lower value.
  public func zIndex(_ value: Double) -> _ModifiedRemoteView<Self> { modifier(.zIndex(value)) }

  // MARK: Drawing

  /// Fills the view's bounds behind its content.
  public func background(_ color: RemoteColor) -> _ModifiedRemoteView<Self> {
    modifier(.background(color, .rectangle))
  }

  /// Fills `shape` behind the content. A rounded shape also clips the content to it, which is
  /// how the wire rounds a background.
  public func background(_ color: RemoteColor, in shape: RemoteShape) -> _ModifiedRemoteView<Self> {
    modifier(.background(color, shape))
  }

  /// Clips the view and its content to `shape`.
  public func clipShape(_ shape: RemoteShape) -> _ModifiedRemoteView<Self> {
    modifier(.clip(shape))
  }

  /// Strokes the view's outline, `width` points wide, with corners rounded by `cornerRadius`.
  public func border(_ color: RemoteColor, width: Double = 1, cornerRadius: Double = 0)
    -> _ModifiedRemoteView<Self>
  {
    modifier(.border(color, width: width, cornerRadius: cornerRadius))
  }

  // MARK: State and input

  /// Shows, hides or removes the view.
  public func visibility(_ visibility: RemoteVisibility) -> _ModifiedRemoteView<Self> {
    modifier(.visibility(visibility))
  }

  /// Shows, hides or removes the view as the host writes `variable`.
  public func visibility(_ variable: RemoteVariable<RemoteVisibility>) -> _ModifiedRemoteView<Self>
  {
    modifier(.visibilityVariable(variable))
  }

  /// Runs `actions`, in order, when the view is tapped.
  public func onTap(_ actions: RemoteAction...) -> _ModifiedRemoteView<Self> { onTap(actions) }

  public func onTap(_ actions: [RemoteAction]) -> _ModifiedRemoteView<Self> {
    modifier(.tap(actions))
  }

  // MARK: Accessibility

  /// What VoiceOver and the other assistive technologies read for this view.
  public func accessibilityLabel(_ label: RemoteString) -> _ModifiedRemoteView<Self> {
    modifier(.accessibilityLabel(label))
  }

  /// The view's current value or state, read after its label ("50 percent", "On").
  public func accessibilityValue(_ value: RemoteString) -> _ModifiedRemoteView<Self> {
    modifier(.accessibilityValue(value))
  }

  /// What kind of control the view is.
  public func accessibilityRole(_ role: RemoteAccessibilityRole) -> _ModifiedRemoteView<Self> {
    modifier(.accessibilityRole(role))
  }

  /// Makes the view one accessibility element, keeping, merging or hiding its children's.
  public func accessibilityElement(children: RemoteAccessibilityChildBehavior = .ignore)
    -> _ModifiedRemoteView<Self>
  {
    modifier(.accessibilityChildren(children))
  }
}

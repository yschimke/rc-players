import Foundation

#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

enum NativeAccessibilityRole: Int, Sendable {
  case button = 0
  case checkbox = 1
  case toggle = 2
  case radioButton = 3
  case tab = 4
  case image = 5
  case dropdownList = 6
  case picker = 7
  case carousel = 8
  case unknown = 9
}

enum NativeAccessibilityMode: Int, Sendable {
  case set = 0
  case clearAndSet = 1
  case merge = 2
}

enum NativeAccessibilityElementKind: Equatable, Sendable {
  case button
  case checkbox
  case toggle
  case radioButton
  case tab
  case image
  case dropdownList
  case picker
  case carousel
  case generic
}

struct NativeAccessibilityDescriptor: Equatable, Sendable {
  let role: NativeAccessibilityRole?
  let mode: NativeAccessibilityMode
  let contentDescription: String?
  let text: String?
  let stateDescription: String?
  let isEnabled: Bool
  let isClickable: Bool

  var elementKind: NativeAccessibilityElementKind {
    switch role {
    case .button: .button
    case .checkbox: .checkbox
    case .toggle: .toggle
    case .radioButton: .radioButton
    case .tab: .tab
    case .image: .image
    case .dropdownList: .dropdownList
    case .picker: .picker
    case .carousel: .carousel
    case .unknown, nil: isClickable ? .button : .generic
    }
  }

  var derivesLabelFromDescendants: Bool {
    guard Self.nonBlank(contentDescription) == nil, Self.nonBlank(text) == nil else { return false }
    switch elementKind {
    case .button, .checkbox, .toggle, .radioButton, .tab, .dropdownList, .picker, .carousel:
      return true
    case .image, .generic:
      return false
    }
  }

  var hidesDescendants: Bool {
    mode == .clearAndSet || mode == .merge || derivesLabelFromDescendants
  }

  func mergingBehavior(from descendant: NativeAccessibilityDescriptor) -> Self {
    guard mode == .merge else { return self }
    return NativeAccessibilityDescriptor(
      role: mergeRole ?? descendant.mergeRole,
      mode: mode,
      contentDescription: contentDescription,
      text: text,
      stateDescription: stateDescription ?? descendant.stateDescription,
      isEnabled: isEnabled && descendant.isEnabled,
      isClickable: isClickable || descendant.isClickable)
  }

  private var mergeRole: NativeAccessibilityRole? {
    role == .unknown ? nil : role
  }

  func resolvedLabel(descendantLabels: [String]) -> String? {
    var fragments = [contentDescription, text].compactMap(Self.nonBlank)
    if mode == .merge || derivesLabelFromDescendants {
      fragments += descendantLabels.compactMap(Self.nonBlank)
    }
    var seen = Set<String>()
    let unique = fragments.filter { seen.insert($0).inserted }
    return unique.isEmpty ? nil : unique.joined(separator: ", ")
  }

  private static func nonBlank(_ value: String?) -> String? {
    guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      return nil
    }
    return value
  }
}

/// What a renderer announces for one semantic node: the (possibly merged) descriptor, and the
/// component whose tap an activation dispatches.
struct NativeAccessibilityBehavior: Equatable, Sendable {
  let descriptor: NativeAccessibilityDescriptor
  /// The component an activation taps: the node itself, or for a merging node without an action of
  /// its own, the first merged descendant that has one.
  let componentID: Int
  let clickActionTypes: [NativeSwiftGestureKind]
  /// Whether the node itself accepts the pointer action, rather than a merged descendant.
  let acceptsPointerAction: Bool

  /// Whether activating this node dispatches a tap: an enabled node with a tap action.
  var activatesTap: Bool {
    descriptor.isEnabled && clickActionTypes.contains(.tap)
  }
}

/// A document node as the accessibility policy reads it, so each host resolves roles, labels,
/// merging and hiding identically from its own node model.
protocol NativeAccessibilityNode {
  var semanticComponentID: Int { get }
  /// Nil when the node carries no accessibility semantics and is not clickable.
  var semanticDescriptor: NativeAccessibilityDescriptor? { get }
  /// The text and image descriptions the node itself draws, before any descendant's.
  var semanticLocalLabels: [String] { get }
  /// The gestures the node accepts; empty when it is disabled.
  var semanticClickActionTypes: [NativeSwiftGestureKind] { get }
  var isSemanticallyVisible: Bool { get }
  var semanticChildren: [Self] { get }
}

extension NativeAccessibilityNode {
  /// The labels this subtree contributes to an ancestor that merges or derives its label.
  var effectiveSemanticLabels: [String] {
    guard isSemanticallyVisible else { return [] }
    let descendants = descendantSemanticLabels
    guard let descriptor = semanticDescriptor else {
      return semanticLocalLabels + descendants
    }
    switch descriptor.mode {
    case .clearAndSet:
      return [descriptor.resolvedLabel(descendantLabels: [])].compactMap { $0 }
    case .merge:
      return [descriptor.resolvedLabel(descendantLabels: semanticLocalLabels + descendants)]
        .compactMap { $0 }
    case .set:
      return [descriptor.resolvedLabel(descendantLabels: [])].compactMap { $0 }
        + semanticLocalLabels + descendants
    }
  }

  var descendantSemanticLabels: [String] {
    semanticChildren.flatMap { $0.effectiveSemanticLabels }
  }

  /// The label the node's own element announces, or nil when it has no semantic behavior or
  /// nothing to say.
  var resolvedSemanticLabel: String? {
    guard let descriptor = semanticBehavior?.descriptor else { return nil }
    return descriptor.resolvedLabel(
      descendantLabels: semanticLocalLabels + descendantSemanticLabels)
  }

  /// The node's semantic element, if it has one: its own descriptor, or for a merging node, the
  /// descriptor with its descendants' role, state, enablement and action folded in.
  var semanticBehavior: NativeAccessibilityBehavior? {
    guard let own = semanticDescriptor else { return nil }
    guard own.mode == .merge else {
      return NativeAccessibilityBehavior(
        descriptor: own, componentID: semanticComponentID,
        clickActionTypes: semanticClickActionTypes,
        acceptsPointerAction: !semanticClickActionTypes.isEmpty)
    }
    let descendants = semanticChildren.flatMap { $0.effectiveSemanticBehaviors }
    let mergedDescriptor = descendants.reduce(own) { descriptor, descendant in
      descriptor.mergingBehavior(from: descendant.descriptor)
    }
    let ownsAction = !semanticClickActionTypes.isEmpty
    let descendantAction = descendants.first { !$0.clickActionTypes.isEmpty }
    return NativeAccessibilityBehavior(
      descriptor: mergedDescriptor,
      componentID: ownsAction
        ? semanticComponentID : descendantAction?.componentID ?? semanticComponentID,
      clickActionTypes: ownsAction
        ? semanticClickActionTypes : descendantAction?.clickActionTypes ?? [],
      acceptsPointerAction: ownsAction)
  }

  private var effectiveSemanticBehaviors: [NativeAccessibilityBehavior] {
    guard isSemanticallyVisible else { return [] }
    if let semanticBehavior {
      if semanticBehavior.descriptor.mode == .set {
        return [semanticBehavior] + semanticChildren.flatMap { $0.effectiveSemanticBehaviors }
      }
      return [semanticBehavior]
    }
    return semanticChildren.flatMap { $0.effectiveSemanticBehaviors }
  }
}

/// The document core's resolved node, read the way the UIKit host's `NativeNode` reads it.
extension NativeSwiftNodeSnapshot: NativeAccessibilityNode {
  var semanticComponentID: Int { componentID }

  var semanticDescriptor: NativeAccessibilityDescriptor? {
    let clickable = isClickable || accessibility?.isClickable == true
    guard accessibility != nil || clickable else { return nil }
    // A clickable node without semantics of its own is a button, as on UIKit.
    let role: NativeAccessibilityRole?
    if let accessibility {
      role = NativeAccessibilityRole(rawValue: accessibility.role)
    } else {
      role = clickable ? .button : nil
    }
    return NativeAccessibilityDescriptor(
      role: role,
      mode: accessibility.flatMap { NativeAccessibilityMode(rawValue: $0.mode) } ?? .set,
      contentDescription: accessibility?.contentDescription,
      text: accessibility?.text ?? text?.value,
      stateDescription: accessibility?.stateDescription,
      isEnabled: accessibility?.isEnabled ?? true,
      isClickable: clickable)
  }

  /// The UIKit host's order: the node's own description and text, then what its draw commands
  /// say, then its promoted text, which the UIKit host appends as one more draw command.
  var semanticLocalLabels: [String] {
    let own: [String?] = [accessibility?.contentDescription, accessibility?.text ?? text?.value]
    let drawn: [String] = commands.flatMap { command -> [String] in
      [command.text, command.image?.contentDescription].compactMap { $0 }
    }
    let promoted: [String?] = [text?.value]
    return own.compactMap { $0 } + drawn + promoted.compactMap { $0 }
  }

  var semanticClickActionTypes: [NativeSwiftGestureKind] {
    (accessibility?.isEnabled ?? true) ? supportedGestures : []
  }

  var isSemanticallyVisible: Bool { visibility == NativeSwiftVisibility.visible }

  var semanticChildren: [NativeSwiftNodeSnapshot] { children }
}

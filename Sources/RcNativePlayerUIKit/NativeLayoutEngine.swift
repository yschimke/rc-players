import CoreGraphics
import Foundation

#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

// The native renderers' one layout engine (#399).
//
// UIKit and AppKit used to carry near-identical copies of this code, each measuring through its
// own views, and the copies drifted. This file is Foundation and CoreGraphics only: it measures and
// places components from their resolved `NativeLayoutNode`s, and asks the host for exactly one
// thing it cannot know — the size of what a leaf draws itself (text, image, host view) — through
// `NativeLayoutItem.layoutContentSize(fitting:)`. Text measurement therefore stays with each
// platform's text stack, and everything else is shared and testable without a window.
//
// A renderer drives it per container, from its own layout pass, with the bounds its view actually
// has: `arrange(_:in:)` returns the frames and visibility a container gives the components it lays
// out, and the renderer writes them onto its views. `frameTree(root:size:)` composes the same
// passes over a whole tree into an immutable frame tree, which is what the tests read.

/// Padding, in document units before any density scale is applied.
struct NativeLayoutInsets: Equatable {
  var top: CGFloat = 0
  var left: CGFloat = 0
  var bottom: CGFloat = 0
  var right: CGFloat = 0

  static let zero = NativeLayoutInsets()
}

/// The part of a resolved component that layout reads, free of any UI framework type.
///
/// Built from the core's `NativeSwiftNodeSnapshot` by `init(snapshot:)`; the memberwise
/// initializer (every field but `kind` has a default) exists for tests.
struct NativeLayoutNode: Equatable {
  typealias Kind = NativeSwiftNodeSnapshot.Kind

  var kind: Kind
  var componentID: Int = 0
  /// The AndroidX class name — `BoxLayout`, `FitBoxLayout`, … — empty for a structural wrapper.
  var componentKind: String = ""
  var widthType: Int = NativeSwiftDimensionType.wrap
  var widthValue: CGFloat = 0
  var heightType: Int = NativeSwiftDimensionType.wrap
  var heightValue: CGFloat = 0
  var minimumWidth: CGFloat = 0
  var maximumWidth: CGFloat? = nil
  var minimumHeight: CGFloat = 0
  var maximumHeight: CGFloat? = nil
  var padding: NativeLayoutInsets = .zero
  var horizontalPositioning: Int = NativeSwiftPositioning.start
  var verticalPositioning: Int = NativeSwiftPositioning.top
  var spacing: CGFloat = 0
  /// Set on `FlowLayout` only.
  var flowMaximumItems: Int? = nil
  var flowMaximumLines: Int? = nil
  var isCollapsible: Bool = false
  var collapsiblePriority: Float? = nil
  var collapsiblePriorityOrientation: Int? = nil
  var offset: CGPoint = .zero
  var zIndex: CGFloat = 0
  var visibility: Int = NativeSwiftVisibility.visible
  var scrollDirection: NativeSwiftScrollDirection? = nil
  /// Whether the component carries a marquee, which lets its content run past the box along x.
  var marquee: Bool = false
  /// The axis this container's content is measured unbounded along: its scroll's, or x under a
  /// marquee, which lays its content out at its natural width and slides it into view.
  var contentAxis: NativeSwiftScrollDirection? {
    scrollDirection ?? (marquee ? .horizontal : nil)
  }
  /// Whether the component paints anything itself: draw commands or text.
  var drawsContent: Bool = false
  /// Whether the component is a host custom component.
  var hasCustom: Bool = false
  var hasBackground: Bool = false
  /// Whether the component takes pointer actions of its own — the UIKit renderer's
  /// `NativeAccessibilityNode.semanticBehavior?.acceptsPointerAction`.
  var acceptsPointerAction: Bool = false
  /// The component's `LayoutComputeOperation` modifiers, which a box parent runs over its box.
  var layoutComputes: [NativeSwiftLayoutComputeSnapshot] = []
  /// Whether a canvas holds a `CanvasContent`, directly or inside its content wrapper — the
  /// reference's `mHasCanvasLayoutContent`, which makes `CanvasLayout` fill every child to its
  /// content size instead of laying them out as a `BoxLayout`.
  var hasCanvasContent: Bool = false
}

extension NativeLayoutNode {
  init(snapshot: NativeSwiftNodeSnapshot) {
    self.init(kind: snapshot.kind)
    componentID = snapshot.componentID
    componentKind = snapshot.componentKind
    widthType = snapshot.widthType
    widthValue = CGFloat(snapshot.widthValue)
    heightType = snapshot.heightType
    heightValue = CGFloat(snapshot.heightValue)
    minimumWidth = CGFloat(snapshot.minimumWidth)
    maximumWidth = snapshot.maximumWidth < 0 ? nil : CGFloat(snapshot.maximumWidth)
    minimumHeight = CGFloat(snapshot.minimumHeight)
    maximumHeight = snapshot.maximumHeight < 0 ? nil : CGFloat(snapshot.maximumHeight)
    padding = NativeLayoutInsets(
      top: CGFloat(snapshot.padding.top), left: CGFloat(snapshot.padding.left),
      bottom: CGFloat(snapshot.padding.bottom), right: CGFloat(snapshot.padding.right))
    horizontalPositioning = snapshot.horizontalPositioning
    verticalPositioning = snapshot.verticalPositioning
    spacing = CGFloat(snapshot.spacing)
    flowMaximumItems = snapshot.flowMaximumItems
    flowMaximumLines = snapshot.flowMaximumLines
    isCollapsible = snapshot.isCollapsible
    collapsiblePriority = snapshot.collapsiblePriority
    collapsiblePriorityOrientation = snapshot.collapsiblePriorityOrientation
    offset = CGPoint(x: CGFloat(snapshot.offsetX), y: CGFloat(snapshot.offsetY))
    zIndex = CGFloat(snapshot.zIndex)
    visibility = snapshot.visibility
    scrollDirection = snapshot.scrollDirection
    marquee = snapshot.marquee != nil
    drawsContent = !snapshot.commands.isEmpty || snapshot.text != nil
    hasCustom = snapshot.custom != nil
    hasBackground = snapshot.backgroundARGB != nil
    // A component owns a pointer action when it has semantics (explicit accessibility, or a click
    // modifier) and, being enabled, declares a gesture — the same rule the UIKit renderer's semantic
    // behaviour applies, in both its merge and non-merge modes.
    let hasSemantics = snapshot.accessibility != nil || snapshot.isClickable
    let isEnabled = snapshot.accessibility?.isEnabled ?? true
    acceptsPointerAction = hasSemantics && isEnabled && !snapshot.supportedGestures.isEmpty
    layoutComputes = snapshot.layoutComputes
    hasCanvasContent =
      snapshot.kind == .canvas
      && snapshot.children.contains { child in
        child.isCanvasContent
          || (child.kind == .content && child.children.contains { $0.isCanvasContent })
      }
  }
}

/// The scales and direction one layout pass resolves document units with.
///
/// The defaults are the identity: the AppKit renderer lays out in document units, left to right.
struct NativeLayoutContext: Equatable {
  /// Exact (non-dp) dimensions and component offsets.
  var documentScale: CGFloat = 1
  /// `EXACT_DP` dimensions.
  var layoutDensityScale: CGFloat = 1
  /// Padding and spacing — see `NativeDensityPolicy`.
  var layoutUnitScale: CGFloat = 1
  /// `widthIn` / `heightIn` bounds — see `NativeDensityPolicy.dimensionConstraintScale`.
  var dimensionConstraintScale: CGFloat = 1
  var layoutDirection: NativeLayoutDirection = .leftToRight
}

/// Measurements a component has already answered, keyed on the exact constraint.
///
/// Rows, flows and collapsible containers ask a child for its size several times per pass with the
/// same constraint. A measurement depends only on the subtree's nodes, the context and the
/// constraint, so the owner keeps it until it invalidates the cache (on a node update, a scale or
/// direction change, or a trait change). A weighted child's final allocation is a separate entry.
final class NativeLayoutSizeCache {
  private struct Key: Hashable {
    let width: CGFloat
    let height: CGFloat
  }

  private var sizes: [Key: CGSize] = [:]

  init() {}

  func size(for available: CGSize) -> CGSize? {
    sizes[Key(width: available.width, height: available.height)]
  }

  func store(_ size: CGSize, for available: CGSize) {
    sizes[Key(width: available.width, height: available.height)] = size
  }

  func removeAll() {
    sizes.removeAll(keepingCapacity: true)
  }
}

/// A component the engine measures and places: a renderer's view, or a plain tree node in tests
/// and in `NativeLayoutSnapshotItem`.
protocol NativeLayoutItem: AnyObject {
  var layoutNode: NativeLayoutNode { get }
  /// The component's children, in document order, one per child node.
  var layoutChildren: [Self] { get }
  /// Where this component's measurements are kept.
  var layoutCache: NativeLayoutSizeCache { get }
  /// The size of what a text, image or custom component draws itself, within the content box
  /// `available`. Only asked of those three kinds; `.zero` when there is nothing to draw.
  func layoutContentSize(fitting available: CGSize) -> CGSize
}

/// What a container gives the components it lays out, for the renderer to write onto its views.
struct NativeLayoutArrangement<Item: AnyObject> {
  struct Placement {
    let item: Item
    /// In the container's own coordinate space, offset modifier included.
    let frame: CGRect
  }

  struct VisibilityChange {
    let item: Item
    let isHidden: Bool
    /// The component is drawn fully opaque whatever its own visibility modifier says (a FitBox
    /// alternative).
    let resetsAlpha: Bool
    /// A FitBox chose this component, ignoring its own visibility modifier.
    let isFitBoxAlternative: Bool
  }

  /// Applied in order; later changes to the same item win.
  var visibilityChanges: [VisibilityChange] = []
  /// Nil leaves the container's own hidden state alone.
  var containerIsHidden: Bool? = nil
  var placements: [Placement] = []
  /// The content extent a scrolled container arranged against, along its scroll axis.
  var scrollExtent: CGFloat? = nil
}

struct NativeLayoutEngine {
  enum Axis {
    case horizontal, vertical
  }

  let context: NativeLayoutContext

  init(context: NativeLayoutContext = NativeLayoutContext()) {
    self.context = context
  }

  // MARK: - Classification

  /// A structural content wrapper is transparent to layout: its children are flattened into its
  /// parent's arrangement and it is sized to the parent's bounds.
  static func isStructural(_ node: NativeLayoutNode) -> Bool {
    (node.kind == .content || (node.kind == .canvas && !node.drawsContent))
      && !node.hasCustom
      && !node.acceptsPointerAction
      && !node.hasBackground
      && node.visibility == NativeSwiftVisibility.visible
      && node.widthType == NativeSwiftDimensionType.wrap
      && node.heightType == NativeSwiftDimensionType.wrap
      && node.minimumWidth == 0 && node.minimumHeight == 0
      && node.maximumWidth == nil && node.maximumHeight == nil
      && node.padding == .zero && node.offset == .zero && node.zIndex == 0
      && node.layoutComputes.isEmpty
  }

  /// Whether a container runs its children's `LayoutComputeOperation`s. The reference's
  /// `BoxLayout` does, from its measure and layout passes, and `CanvasLayout` inherits it only
  /// when it delegates to `BoxLayout`: a canvas holding a `CanvasContent` sizes every child to its
  /// content and never runs the computations. No other layout manager calls
  /// `applyComputedLayout`, so a computation under a row or column is inert.
  static func runsLayoutComputes(_ container: NativeLayoutNode) -> Bool {
    if container.componentKind == "BoxLayout" { return true }
    return container.componentKind == "CanvasLayout" && !container.hasCanvasContent
  }

  /// The components a container lays out: its non-GONE children, looking through structural ones.
  static func flattenedLayoutItems<Item: NativeLayoutItem>(of item: Item) -> [Item] {
    item.layoutChildren.filter { $0.layoutNode.visibility != NativeSwiftVisibility.gone }
      .flatMap { child in
        isStructural(child.layoutNode) ? flattenedLayoutItems(of: child) : [child]
      }
  }

  /// A host custom component can change its fitting size without a document update, so neither it
  /// nor any ancestor keeps a measurement.
  static func hasMutableContent<Item: NativeLayoutItem>(_ item: Item) -> Bool {
    item.layoutNode.kind == .custom || item.layoutChildren.contains { hasMutableContent($0) }
  }

  /// A `FitBox`: it shows the first alternative whose natural size fits, and shows nothing at all
  /// when none does.
  static func isFitBox(_ node: NativeLayoutNode) -> Bool {
    node.componentKind == "FitBoxLayout"
  }

  /// Whether a node is a `FitBox`'s own content node — the wrapper the box looks through.
  ///
  /// `isStructural` is a *layout* classification and it requires the wrapper to be visible, so a
  /// content switched off is not structural by that test. A FitBox has to recognise its content by
  /// kind instead: otherwise a GONE wrapper is treated as an alternative, selected, and unhidden,
  /// and content that is meant to switch the whole box off is rendered.
  static func isFitBoxContent(_ node: NativeLayoutNode) -> Bool {
    node.kind == .content || (node.kind == .canvas && !node.drawsContent)
  }

  // MARK: - Units

  func scaledPadding(_ node: NativeLayoutNode) -> NativeLayoutInsets {
    NativeLayoutInsets(
      top: node.padding.top * context.layoutUnitScale,
      left: node.padding.left * context.layoutUnitScale,
      bottom: node.padding.bottom * context.layoutUnitScale,
      right: node.padding.right * context.layoutUnitScale)
  }

  func scaledSpacing(_ node: NativeLayoutNode) -> CGFloat {
    node.spacing * context.layoutUnitScale
  }

  /// `bounds` less the component's padding — exactly `UIEdgeInsetsInsetRect`, which does not clamp.
  func contentRect(of node: NativeLayoutNode, in bounds: CGRect) -> CGRect {
    let insets = scaledPadding(node)
    return CGRect(
      x: bounds.origin.x + insets.left, y: bounds.origin.y + insets.top,
      width: bounds.size.width - (insets.left + insets.right),
      height: bounds.size.height - (insets.top + insets.bottom))
  }

  private func widthDimension(_ node: NativeLayoutNode) -> NativeLayoutDimension {
    NativeLayoutDimension(
      type: node.widthType,
      value: node.widthValue
        * (node.widthType == NativeSwiftDimensionType.exactDp
          ? context.layoutDensityScale : context.documentScale),
      minimum: node.minimumWidth * context.dimensionConstraintScale,
      maximum: node.maximumWidth.map { $0 * context.dimensionConstraintScale })
  }

  private func heightDimension(_ node: NativeLayoutNode) -> NativeLayoutDimension {
    NativeLayoutDimension(
      type: node.heightType,
      value: node.heightValue
        * (node.heightType == NativeSwiftDimensionType.exactDp
          ? context.layoutDensityScale : context.documentScale),
      minimum: node.minimumHeight * context.dimensionConstraintScale,
      maximum: node.maximumHeight.map { $0 * context.dimensionConstraintScale })
  }

  func applyDimensions(_ node: NativeLayoutNode, to intrinsic: CGSize, available: CGSize) -> CGSize
  {
    CGSize(
      width: widthDimension(node).resolve(intrinsic: intrinsic.width, available: available.width),
      height: heightDimension(node).resolve(
        intrinsic: intrinsic.height, available: available.height))
  }

  private func offsetFrame(_ node: NativeLayoutNode, _ frame: CGRect) -> CGRect {
    frame.offsetBy(
      dx: node.offset.x * context.documentScale,
      dy: node.offset.y * context.documentScale)
  }

  private func alignedFrame(size: CGSize, in rect: CGRect, container: NativeLayoutNode) -> CGRect {
    let x = horizontalOrigin(for: size.width, in: rect, container: container)
    let y: CGFloat
    switch container.verticalPositioning {
    case NativeSwiftPositioning.center: y = rect.midY - size.height / 2
    case NativeSwiftPositioning.bottom: y = rect.maxY - size.height
    default: y = rect.minY
    }
    return CGRect(origin: CGPoint(x: x, y: y), size: size)
  }

  private func horizontalOrigin(
    for width: CGFloat, in rect: CGRect, container: NativeLayoutNode
  ) -> CGFloat {
    switch container.horizontalPositioning {
    case NativeSwiftPositioning.center: return rect.midX - width / 2
    case NativeSwiftPositioning.end:
      return context.layoutDirection == .rightToLeft ? rect.minX : rect.maxX - width
    default: return context.layoutDirection == .rightToLeft ? rect.maxX - width : rect.minX
    }
  }

  // MARK: - Measurement

  /// The size `item` asks for within `available`, cached on the item.
  func preferredSize<Item: NativeLayoutItem>(of item: Item, in available: CGSize) -> CGSize {
    if Self.hasMutableContent(item) { return measure(item, in: available) }
    if let cached = item.layoutCache.size(for: available) { return cached }
    let size = measure(item, in: available)
    item.layoutCache.store(size, for: available)
    return size
  }

  private func measure<Item: NativeLayoutItem>(_ item: Item, in available: CGSize) -> CGSize {
    let node = item.layoutNode
    let insets = scaledPadding(node)
    // Every width type, `WRAP` included. A wrapping component still carries its `widthIn`
    // bounds, and passing children the full available width instead let an edge button's label
    // measure 209px wide against a 161px bound. `resolve` with the available width as the
    // intrinsic degrades to exactly that width when there are no bounds to apply.
    let widthConstraint = widthDimension(node).resolve(
      intrinsic: available.width, available: available.width)
    // The container's own height, resolved the same way, because a collapsible container's
    // retention decision is about *its* bound and not about the space its parent offered: a
    // 50-point collapsible column holding a 60-point child keeps nothing, whatever the parent
    // has to spare.
    let heightConstraint = heightDimension(node).resolve(
      intrinsic: available.height, available: available.height)
    let contentAvailable = CGSize(
      width: max(min(available.width, widthConstraint) - insets.left - insets.right, 0),
      height: max(min(available.height, heightConstraint) - insets.top - insets.bottom, 0))
    // A FitBox wraps to the alternative it shows, not to the largest of everything it holds.
    if Self.isFitBox(node) {
      let fitting = fitBoxAlternativesAndSizes(of: item, in: contentAvailable).first {
        $0.size.width <= contentAvailable.width + 0.5
          && $0.size.height <= contentAvailable.height + 0.5
      }
      // Nothing fits: the box is GONE and takes no space, the way a collapsible container that
      // keeps nothing does, so its parent does not reserve a box the reference hides.
      let intrinsic = fitting?.size ?? .zero
      return applyDimensions(
        node,
        to: CGSize(
          width: intrinsic.width + insets.left + insets.right,
          height: intrinsic.height + insets.top + insets.bottom),
        available: available)
    }
    let allItems = Self.flattenedLayoutItems(of: item)
    // A collapsible container wraps to what it keeps, not to everything it holds — and reports
    // nothing at all when it keeps nothing, so a parent does not reserve a box the reference
    // treats as GONE.
    let items: [Item]
    if node.isCollapsible,
      let kept = collapsibleKeptFlags(
        container: node, items: allItems, available: contentAvailable,
        axis: node.kind == .column ? .vertical : .horizontal)
    {
      let keptItems = allItems.enumerated().filter { kept[$0.offset] }.map(\.element)
      if keptItems.isEmpty { return .zero }
      items = keptItems
    } else {
      items = allItems
    }
    let spacing = scaledSpacing(node)
    let intrinsic: CGSize
    switch node.kind {
    case .text, .image, .custom:
      intrinsic = item.layoutContentSize(fitting: contentAvailable)
    case .column:
      let sizes = items.map { preferredSize(of: $0, in: contentAvailable) }
      intrinsic = CGSize(
        width: sizes.map(\.width).max() ?? 0,
        height: sizes.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(sizes.count - 1, 0)))
    case .row:
      if node.flowMaximumItems != nil {
        intrinsic = wrappedFlowSize(container: node, items: items, in: contentAvailable)
      } else {
        let sizes = items.map { preferredSize(of: $0, in: contentAvailable) }
        intrinsic = CGSize(
          width: sizes.reduce(0) { $0 + $1.width } + spacing * CGFloat(max(sizes.count - 1, 0)),
          height: sizes.map(\.height).max() ?? 0)
      }
    default:
      // Zero, not the available space: a wrapping box with nothing visible in it has no size of
      // its own. A fill box still resolves to the available space through its own dimension.
      // A box wraps to its children's sizes after their measure computations, as `BoxLayout`'s
      // `computeWrapSize` applies them before taking the largest.
      let runsComputes = Self.runsLayoutComputes(node)
      let parent = CGSize(
        width: min(available.width, widthConstraint),
        height: min(available.height, heightConstraint))
      let sizes = items.map { child -> CGSize in
        let size = preferredSize(of: child, in: contentAvailable)
        guard runsComputes else { return size }
        return computedSize(of: child.layoutNode, measured: size, parent: parent)
      }
      intrinsic = CGSize(
        width: sizes.map(\.width).max() ?? 0,
        height: sizes.map(\.height).max() ?? 0)
    }
    return applyDimensions(
      node,
      to: CGSize(
        width: intrinsic.width + insets.left + insets.right,
        height: intrinsic.height + insets.top + insets.bottom),
      available: available)
  }

  // MARK: - Layout computations

  /// `size` after the component's `LayoutComputeOperation`s of type measure, in the order it
  /// declared them. `parent` is the box's own size, which the reference passes as bounds 4 and 5.
  ///
  /// Each computation sees the size the previous one produced. The reference measures with the
  /// component at its measured size and x/y as last placed; a measure pass here has not placed it,
  /// so x and y read as 0. A result the computation cannot produce leaves the size alone, and a
  /// negative one is clamped to 0 rather than handed to a view frame.
  func computedSize(of node: NativeLayoutNode, measured size: CGSize, parent: CGSize) -> CGSize {
    guard !node.layoutComputes.isEmpty else { return size }
    let scale = context.documentScale > 0 ? context.documentScale : 1
    var result = size
    for compute in node.layoutComputes where compute.type == NativeSwiftLayoutComputeType.measure {
      guard
        let bounds = compute.evaluate(
          x: 0, y: 0, width: Float(result.width / scale), height: Float(result.height / scale),
          parentWidth: Float(parent.width / scale), parentHeight: Float(parent.height / scale))
      else { continue }
      let width = CGFloat(bounds[NativeSwiftLayoutComputeBound.width]) * scale
      let height = CGFloat(bounds[NativeSwiftLayoutComputeBound.height]) * scale
      result = CGSize(width: max(width, 0), height: max(height, 0))
    }
    return result
  }

  /// `frame` after the component's `LayoutComputeOperation`s of type position. Positions are
  /// relative to `content`, the box's padded content rectangle, as `BoxLayout` places a child;
  /// `parent` is the box's own size.
  func computedFrame(
    of node: NativeLayoutNode, frame: CGRect, content: CGRect, parent: CGSize
  ) -> CGRect {
    guard !node.layoutComputes.isEmpty else { return frame }
    let scale = context.documentScale > 0 ? context.documentScale : 1
    var origin = CGPoint(x: frame.minX - content.minX, y: frame.minY - content.minY)
    let positions = node.layoutComputes.filter {
      $0.type == NativeSwiftLayoutComputeType.position
    }
    for compute in positions {
      guard
        let bounds = compute.evaluate(
          x: Float(origin.x / scale), y: Float(origin.y / scale),
          width: Float(frame.width / scale), height: Float(frame.height / scale),
          parentWidth: Float(parent.width / scale), parentHeight: Float(parent.height / scale))
      else { continue }
      origin = CGPoint(
        x: CGFloat(bounds[NativeSwiftLayoutComputeBound.x]) * scale,
        y: CGFloat(bounds[NativeSwiftLayoutComputeBound.y]) * scale)
    }
    return CGRect(
      x: content.minX + origin.x, y: content.minY + origin.y, width: frame.width,
      height: frame.height)
  }

  /// The size a child contributes to its container.
  ///
  /// Inside a **scrolled** container the axis that scrolls is measured unbounded: the viewport
  /// clips the content, it does not size it. A child that fills that axis is the exception — it has
  /// no natural size, so it keeps the viewport's bound.
  private func measuredSize<Item: NativeLayoutItem>(
    of child: Item, in available: CGSize, axis: Axis, container: NativeLayoutNode
  ) -> CGSize {
    guard container.contentAxis != nil else { return preferredSize(of: child, in: available) }
    let type = axis == .vertical ? child.layoutNode.heightType : child.layoutNode.widthType
    guard NativeSwiftCollapsible.measuresUnbounded(mainAxisType: type) else {
      return preferredSize(of: child, in: available)
    }
    let space =
      axis == .vertical
      ? CGSize(width: available.width, height: .greatestFiniteMagnitude)
      : CGSize(width: .greatestFiniteMagnitude, height: available.height)
    return preferredSize(of: child, in: space)
  }

  /// The extent a scrolled container arranges its children in along one axis, or nil when it does
  /// not scroll.
  ///
  /// A scrolled container's children are laid out against the content they make, not against the
  /// viewport that clips them: the corpus's `collapsible_column_scroll` records its survivors
  /// centred in the pre-collapse 240 points even though the viewport is 200 — the collapse frees no
  /// space, it only moves the content — and `box_child_scroll` records a 250-point child in a
  /// 150-point viewport. The viewport decides what is *visible*; the content decides where things
  /// sit. Every child counts towards it, including the ones a collapse dropped.
  ///
  /// A row or column **stacks** its children, so the content is their sum; a box overlays them, so
  /// it is the largest. Summing an overlay's children inflated the extent and misaligned every
  /// centred or end-aligned child in it.
  private func scrolledExtent<Item: NativeLayoutItem>(
    of items: [Item], in viewport: CGSize, axis: Axis, stacking: Bool,
    container: NativeLayoutNode
  ) -> CGFloat? {
    guard container.contentAxis != nil else { return nil }
    let extents = items.map { child -> CGFloat in
      let size = measuredSize(of: child, in: viewport, axis: axis, container: container)
      return axis == .vertical ? size.height : size.width
    }
    guard stacking else { return extents.max() ?? 0 }
    return extents.reduce(0, +) + scaledSpacing(container) * CGFloat(max(extents.count - 1, 0))
  }

  // MARK: - Collapsible

  /// Which of `items` a collapsible container keeps, or nil when it is not collapsible.
  private func collapsibleKeptFlags<Item: NativeLayoutItem>(
    container: NativeLayoutNode, items: [Item], available: CGSize, axis: Axis
  ) -> [Bool]? {
    guard container.isCollapsible else { return nil }
    let orientation = axis == .vertical ? 1 : 0
    let children = items.map { child -> NativeSwiftCollapsible.Child in
      let node = child.layoutNode
      // The fit test measures each child with its *main* axis unbounded: the reference measures
      // with the constraints the container received from its parent, so a child taller than the
      // container is measured at its natural size and then dropped, rather than clamped to fit and
      // kept. A child that *fills* that axis is the exception — it has no natural size, so an
      // unbounded measurement resolves it to infinity and it would be dropped from any container.
      let mainAxisType = axis == .vertical ? node.heightType : node.widthType
      let measuring: CGSize
      if NativeSwiftCollapsible.measuresUnbounded(mainAxisType: mainAxisType) {
        measuring =
          axis == .vertical
          ? CGSize(width: available.width, height: .greatestFiniteMagnitude)
          : CGSize(width: .greatestFiniteMagnitude, height: available.height)
      } else {
        measuring = available
      }
      let size = preferredSize(of: child, in: measuring)
      let weightValue = axis == .vertical ? node.heightValue : node.widthValue
      let priority =
        node.collapsiblePriorityOrientation == orientation ? node.collapsiblePriority : nil
      return NativeSwiftCollapsible.Child(
        mainSize: Float(axis == .vertical ? size.height : size.width),
        weight: mainAxisType == NativeSwiftDimensionType.weight ? Float(max(weightValue, 0)) : 0,
        priority: priority)
    }
    let extent = axis == .vertical ? available.height : available.width
    return NativeSwiftCollapsible.keptChildren(
      children, available: Float(extent), spacing: Float(scaledSpacing(container)))
  }

  /// The children a row or column lays out.
  ///
  /// A collapsible container hides the children that do not fit, in the order their
  /// `CollapsiblePriority` modifiers give, and is itself hidden when nothing fits — the
  /// reference's container GONE. Every other container returns its children unchanged.
  private func collapsibleItems<Item: NativeLayoutItem>(
    _ item: Item, in content: CGRect, axis: Axis, into result: inout NativeLayoutArrangement<Item>
  ) -> [Item] {
    let node = item.layoutNode
    let items = Self.flattenedLayoutItems(of: item)
    guard
      let kept = collapsibleKeptFlags(
        container: node, items: items, available: content.size, axis: axis)
    else { return items }
    for (index, child) in items.enumerated() {
      result.visibilityChanges.append(
        .init(item: child, isHidden: !kept[index], resetsAlpha: false, isFitBoxAlternative: false))
    }
    let visible = items.enumerated().filter { kept[$0.offset] }.map(\.element)
    result.containerIsHidden = node.visibility == NativeSwiftVisibility.gone || visible.isEmpty
    return visible
  }

  // MARK: - FitBox

  /// A `FitBox`'s alternatives, in document order.
  ///
  /// Unlike every other container this does **not** drop the children whose own visibility
  /// modifier hides them: a document switches between alternatives with that modifier, and the
  /// reference ignores it — the fit test sees the alternative's real size and the winner is drawn.
  /// Taking the modifier at face value measured a hidden child as 0x0, so it "fitted" and displaced
  /// the alternative that actually fits (`fitbox_child_visibility`).
  static func fitBoxAlternatives<Item: NativeLayoutItem>(of item: Item) -> [Item] {
    item.layoutChildren.flatMap { child in
      isFitBoxContent(child.layoutNode) ? fitBoxAlternatives(of: child) : [child]
    }
  }

  /// Whether the box's *content* is switched on. An alternative's own modifier is ignored, but the
  /// content's is the box's own switch: a GONE content shows nothing.
  private static func fitBoxContentVisible<Item: NativeLayoutItem>(_ item: Item) -> Bool {
    !item.layoutChildren.contains {
      isFitBoxContent($0.layoutNode) && $0.layoutNode.visibility == NativeSwiftVisibility.gone
    }
  }

  /// An alternative's natural size: the size it asks for when nothing forces it to fill.
  private func fitBoxNaturalSize<Item: NativeLayoutItem>(_ child: Item, in available: CGSize)
    -> CGSize
  {
    // The rule the collapsible fit test also uses: a dimension that fills has no natural size, so
    // it keeps the box's bound rather than resolving to infinity.
    preferredSize(
      of: child,
      in: CGSize(
        width: NativeSwiftCollapsible.measuresUnbounded(mainAxisType: child.layoutNode.widthType)
          ? .greatestFiniteMagnitude : available.width,
        height: NativeSwiftCollapsible.measuresUnbounded(mainAxisType: child.layoutNode.heightType)
          ? .greatestFiniteMagnitude : available.height))
  }

  private func fitBoxAlternativesAndSizes<Item: NativeLayoutItem>(
    of item: Item, in available: CGSize
  ) -> [(item: Item, size: CGSize)] {
    Self.fitBoxAlternatives(of: item).map { (item: $0, size: fitBoxNaturalSize($0, in: available)) }
  }

  // MARK: - Arrangement

  /// The frames and visibility `item` gives the components it lays out, within its `bounds`.
  ///
  /// Empty for a structural wrapper (its parent lays its children out) and for a text, image or
  /// custom component (the renderer sizes its own content to its bounds).
  func arrange<Item: NativeLayoutItem>(_ item: Item, in bounds: CGRect)
    -> NativeLayoutArrangement<Item>
  {
    let node = item.layoutNode
    guard !Self.isStructural(node) else { return NativeLayoutArrangement() }
    switch node.kind {
    case .column:
      return arrangeColumn(item, in: bounds)
    case .row:
      return node.flowMaximumItems == nil
        ? arrangeRow(item, in: bounds) : arrangeFlow(item, in: bounds)
    // The root arranges its children the way a box does: a child that fills still covers the
    // canvas, and a child that wraps takes its own size instead of being stretched to the frame.
    case .box, .root:
      // A FitBox is the one box that does not stack what it holds: it shows the first alternative
      // whose natural size fits, and nothing at all when none does.
      return Self.isFitBox(node)
        ? arrangeFitBox(item, in: bounds) : arrangeOverlay(item, in: bounds, aligned: true)
    case .text, .image, .custom:
      return NativeLayoutArrangement()
    default:
      return arrangeOverlay(item, in: bounds, aligned: false)
    }
  }

  private func arrangeOverlay<Item: NativeLayoutItem>(
    _ item: Item, in bounds: CGRect, aligned: Bool
  ) -> NativeLayoutArrangement<Item> {
    let node = item.layoutNode
    var result = NativeLayoutArrangement<Item>()
    let content = contentRect(of: node, in: bounds)
    let items = Self.flattenedLayoutItems(of: item)
    let axis: Axis = node.contentAxis == .horizontal ? .horizontal : .vertical
    let extent = scrolledExtent(
      of: items, in: content.size, axis: axis, stacking: false, container: node)
    result.scrollExtent = extent
    let space =
      node.contentAxis == .horizontal
      ? CGRect(
        x: content.minX, y: content.minY, width: extent ?? content.width, height: content.height)
      : CGRect(
        x: content.minX, y: content.minY, width: content.width, height: extent ?? content.height)
    let runsComputes = Self.runsLayoutComputes(node)
    for child in items {
      let size = measuredSize(of: child, in: content.size, axis: axis, container: node)
      var frame =
        aligned
        ? alignedFrame(size: size, in: space, container: node)
        : CGRect(origin: content.origin, size: content.size)
      if runsComputes, !child.layoutNode.layoutComputes.isEmpty {
        // `BoxLayout` measures the child, applies its measure computations, aligns the result and
        // then applies its position computations. A canvas without a canvas content lays such a
        // child out the same way, as `CanvasLayout` delegates to `BoxLayout` then.
        let computed = computedSize(of: child.layoutNode, measured: size, parent: bounds.size)
        frame = computedFrame(
          of: child.layoutNode, frame: alignedFrame(size: computed, in: space, container: node),
          content: content, parent: bounds.size)
      }
      result.placements.append(.init(item: child, frame: offsetFrame(child.layoutNode, frame)))
    }
    return result
  }

  /// Lays a `FitBox` out: the first alternative whose natural size fits is drawn, aligned inside
  /// the box; the others are hidden. When none fits the box shows nothing at all — the box itself
  /// is GONE, which is what the reference does and what `fitbox_fit` asserts.
  private func arrangeFitBox<Item: NativeLayoutItem>(_ item: Item, in bounds: CGRect)
    -> NativeLayoutArrangement<Item>
  {
    let node = item.layoutNode
    var result = NativeLayoutArrangement<Item>()
    let content = contentRect(of: node, in: bounds)
    result.containerIsHidden = node.visibility == NativeSwiftVisibility.gone
    let measured = fitBoxAlternativesAndSizes(of: item, in: content.size)
    for alternative in measured {
      // The reference ignores an alternative's own visibility modifier: it is the document's
      // switch between alternatives, not something the box obeys.
      result.visibilityChanges.append(
        .init(
          item: alternative.item, isHidden: true,
          resetsAlpha: alternative.item.layoutNode.visibility != NativeSwiftVisibility.gone,
          isFitBoxAlternative: true))
    }
    let fitting = measured.first {
      $0.size.width <= content.width + 0.5 && $0.size.height <= content.height + 0.5
    }
    // A content that is switched off is the box's own switch: the box stays, its content does not.
    guard Self.fitBoxContentVisible(item), let winner = fitting else {
      // Nothing fits: the reference hides the box, background included. The alternative keeps the
      // geometry it would have had, which the corpus does not compare for a gone node.
      if let first = measured.first {
        result.placements.append(
          .init(
            item: first.item,
            frame: offsetFrame(
              first.item.layoutNode,
              alignedFrame(size: first.size, in: content, container: node))))
      }
      if fitting == nil { result.containerIsHidden = true }
      return result
    }
    // `INVISIBLE` starts at alpha 0, and the FitBox ignores an alternative's own visibility —
    // unhiding alone would leave the winner drawn at zero alpha.
    result.visibilityChanges.append(
      .init(item: winner.item, isHidden: false, resetsAlpha: true, isFitBoxAlternative: true))
    result.placements.append(
      .init(
        item: winner.item,
        frame: offsetFrame(
          winner.item.layoutNode, alignedFrame(size: winner.size, in: content, container: node))))
    return result
  }

  private func arrangeColumn<Item: NativeLayoutItem>(_ item: Item, in bounds: CGRect)
    -> NativeLayoutArrangement<Item>
  {
    let node = item.layoutNode
    var result = NativeLayoutArrangement<Item>()
    let content = contentRect(of: node, in: bounds)
    let spacing = scaledSpacing(node)
    let items = collapsibleItems(item, in: content, axis: .vertical, into: &result)
    // A scrolled column arranges against its content rather than its viewport, and the extent
    // counts every child — including the ones a collapse dropped, which is why the survivors are
    // centred in the pre-collapse total. The axis that scrolls is the *modifier's*, which need not
    // be the arrangement axis: a horizontally scrolled column still stacks, but each child is
    // measured unbounded in width.
    let scrollAxis: Axis = node.contentAxis == .horizontal ? .horizontal : .vertical
    let scrolled =
      node.contentAxis == .vertical
      ? scrolledExtent(
        of: Self.flattenedLayoutItems(of: item), in: content.size, axis: .vertical,
        stacking: true, container: node)
      : nil
    result.scrollExtent = scrolled
    let extent = scrolled ?? content.height
    let sizes = items.map {
      measuredSize(of: $0, in: content.size, axis: scrollAxis, container: node)
    }
    let weightedHeights = NativeLinearLayout.allocateWeighted(
      available: NativeLinearLayout.collapsibleWeightSpace(
        extent: extent, count: items.count, spacing: node.isCollapsible ? spacing : 0),
      naturalSizes: sizes.map(\.height),
      weights: items.map { child in
        guard child.layoutNode.heightType == NativeSwiftDimensionType.weight else { return nil }
        return max(child.layoutNode.heightValue, .leastNonzeroMagnitude)
      })
    let heights = zip(items, zip(sizes, weightedHeights)).map { child, values in
      applyDimensions(
        child.layoutNode, to: values.0,
        available: CGSize(width: content.width, height: values.1)
      ).height
    }
    let positions = NativeLinearLayout.positions(
      total: extent, sizes: heights, positioning: node.verticalPositioning, spacing: spacing)
    for (index, child) in items.enumerated() {
      let size = CGSize(width: sizes[index].width, height: heights[index])
      let x = horizontalOrigin(for: size.width, in: content, container: node)
      result.placements.append(
        .init(
          item: child,
          frame: offsetFrame(
            child.layoutNode,
            CGRect(
              x: x, y: content.minY + positions[index], width: size.width,
              height: size.height))))
    }
    return result
  }

  private func arrangeRow<Item: NativeLayoutItem>(_ item: Item, in bounds: CGRect)
    -> NativeLayoutArrangement<Item>
  {
    let node = item.layoutNode
    var result = NativeLayoutArrangement<Item>()
    let content = contentRect(of: node, in: bounds)
    let spacing = scaledSpacing(node)
    let items = collapsibleItems(item, in: content, axis: .horizontal, into: &result)
    // As in `arrangeColumn`: a scrolled row arranges against its content, not its viewport, and
    // the axis that scrolls is the modifier's — a vertically scrolled row still places left to
    // right.
    let scrollAxis: Axis = node.contentAxis == .horizontal ? .horizontal : .vertical
    let scrolled =
      node.contentAxis == .horizontal
      ? scrolledExtent(
        of: Self.flattenedLayoutItems(of: item), in: content.size, axis: .horizontal,
        stacking: true, container: node)
      : nil
    result.scrollExtent = scrolled
    let extent = scrolled ?? content.width
    let natural = items.map {
      measuredSize(of: $0, in: content.size, axis: scrollAxis, container: node)
    }
    let allocatedWidths = NativeLinearLayout.allocateWeighted(
      available: NativeLinearLayout.collapsibleWeightSpace(
        extent: extent, count: items.count, spacing: node.isCollapsible ? spacing : 0),
      naturalSizes: natural.map(\.width),
      weights: items.map { child in
        guard child.layoutNode.widthType == NativeSwiftDimensionType.weight else { return nil }
        return max(child.layoutNode.widthValue, .leastNonzeroMagnitude)
      })
    let widths = zip(items, zip(natural, allocatedWidths)).map { child, values in
      applyDimensions(
        child.layoutNode, to: values.0,
        available: CGSize(width: values.1, height: content.height)
      ).width
    }
    let positions = NativeLinearLayout.positions(
      total: extent, sizes: widths, positioning: node.horizontalPositioning, spacing: spacing,
      direction: context.layoutDirection)
    for (index, child) in items.enumerated() {
      // Weighted children must see their final main-axis constraint before cross-axis placement;
      // wrapping text can be taller at its allocated width than at the row's full width.
      let remeasured = preferredSize(
        of: child, in: CGSize(width: widths[index], height: content.height))
      let size = CGSize(width: widths[index], height: remeasured.height)
      let y: CGFloat
      switch node.verticalPositioning {
      case NativeSwiftPositioning.center: y = content.midY - size.height / 2
      case NativeSwiftPositioning.bottom: y = content.maxY - size.height
      default: y = content.minY
      }
      result.placements.append(
        .init(
          item: child,
          frame: offsetFrame(
            child.layoutNode,
            CGRect(
              x: content.minX + positions[index], y: y, width: size.width,
              height: size.height))))
    }
    return result
  }

  // MARK: - Flow

  /// A flow container's children: left to right, wrapping onto a further line when the next one
  /// does not fit, bounded by the layout's maximum items per line and maximum lines.
  ///
  /// Two passes, because an item is aligned within *its line* and the block of lines is aligned
  /// within the container: the first measures the lines, the second places each item at the
  /// line's top, centre or bottom according to the container's vertical positioning.
  ///
  /// A weighted child does not force a wrap — its main-axis size is the row's leftover, not the
  /// whole container — so it is measured with its siblings and allocated a share of the space
  /// they left, exactly as the reference's `FlowRow` does.
  private func arrangeFlow<Item: NativeLayoutItem>(_ item: Item, in bounds: CGRect)
    -> NativeLayoutArrangement<Item>
  {
    let node = item.layoutNode
    var result = NativeLayoutArrangement<Item>()
    let content = contentRect(of: node, in: bounds)
    let wrapped = wrappedFlow(
      container: node, items: Self.flattenedLayoutItems(of: item), in: content.size)
    for child in wrapped.discarded {
      result.visibilityChanges.append(
        .init(item: child, isHidden: true, resetsAlpha: false, isFitBoxAlternative: false))
    }
    // Lines are stacked without a gap, matching the reference's `FlowRow`, which passes 0 as its
    // vertical arrangement spacing while the horizontal `spacedBy` separates items on a line.
    let blockHeight = wrapped.lines.map(\.height).reduce(0, +)
    var y = content.minY
    switch node.verticalPositioning {
    case NativeSwiftPositioning.center: y += (content.height - blockHeight) / 2
    case NativeSwiftPositioning.bottom: y += content.height - blockHeight
    default: break
    }
    for line in wrapped.lines {
      let positions = NativeLinearLayout.positions(
        total: content.width, sizes: line.items.map(\.size.width),
        positioning: node.horizontalPositioning, spacing: scaledSpacing(node),
        direction: context.layoutDirection)
      for (index, entry) in line.items.enumerated() {
        let offset: CGFloat
        switch node.verticalPositioning {
        case NativeSwiftPositioning.center: offset = (line.height - entry.size.height) / 2
        case NativeSwiftPositioning.bottom: offset = line.height - entry.size.height
        default: offset = 0
        }
        result.visibilityChanges.append(
          .init(item: entry.item, isHidden: false, resetsAlpha: false, isFitBoxAlternative: false))
        result.placements.append(
          .init(
            item: entry.item,
            frame: offsetFrame(
              entry.item.layoutNode,
              CGRect(
                x: content.minX + positions[index], y: y + offset, width: entry.size.width,
                height: entry.size.height))))
      }
      y += line.height
    }
    return result
  }

  /// The block a flow's children occupy when wrapped within `available`, for wrap sizing.
  private func wrappedFlowSize<Item: NativeLayoutItem>(
    container: NativeLayoutNode, items: [Item], in available: CGSize
  ) -> CGSize {
    let wrapped = wrappedFlow(container: container, items: items, in: available)
    // No vertical gap between lines: the horizontal `spacedBy` is not a line height.
    return CGSize(
      width: wrapped.lines.map(\.width).max() ?? 0,
      height: wrapped.lines.map(\.height).reduce(0, +))
  }

  /// Wraps `items` into lines, measuring weighted children against their row's leftover space.
  ///
  /// The line breaks come from `NativeSwiftFlow`, which reserves a weighted child's `widthIn`
  /// minimum; a weighted child is then measured at its share of what the row left, never below
  /// that minimum.
  private func wrappedFlow<Item: NativeLayoutItem>(
    container: NativeLayoutNode, items: [Item], in available: CGSize
  ) -> (lines: [NativeLayoutFlowLine<Item>], discarded: [Item]) {
    let maximumItems = container.flowMaximumItems.flatMap { $0 > 0 ? $0 : nil } ?? Int.max
    let maximumLines = container.flowMaximumLines.flatMap { $0 > 0 ? $0 : nil } ?? Int.max
    let spacing = scaledSpacing(container)
    let children = items.map { child in
      NativeSwiftFlow.Child(
        measuredWidth: Float(preferredSize(of: child, in: available).width),
        weight: child.layoutNode.widthType == NativeSwiftDimensionType.weight
          ? Float(max(child.layoutNode.widthValue, 0)) : 0,
        minimumWidth: Float(child.layoutNode.minimumWidth * context.dimensionConstraintScale))
    }
    let segmented = NativeSwiftFlow.segment(
      children, available: Float(available.width), spacing: Float(spacing),
      maximumItems: maximumItems, maximumLines: maximumLines)
    var lines: [NativeLayoutFlowLine<Item>] = []
    for indices in segmented.lines {
      var lineItems: [(item: Item, size: CGSize)] = []
      let gaps = spacing * CGFloat(max(indices.count - 1, 0))
      let weights = indices.map { index -> CGFloat? in
        children[index].weight > 0
          ? max(CGFloat(children[index].weight), .leastNonzeroMagnitude) : nil
      }
      let allocated = NativeLinearLayout.allocateWeighted(
        available: max(available.width - gaps, 0),
        naturalSizes: indices.map { CGFloat(children[$0].measuredWidth) },
        weights: weights)
      var width = gaps
      for (position, index) in indices.enumerated() {
        let child = items[index]
        let size: CGSize
        if weights[position] != nil {
          // The allocator splits the leftover; the child's own minimum still bounds it, because a
          // weighted child that cannot reach its minimum has to overflow its row rather than be
          // drawn narrower than the document allows.
          let minimum = CGFloat(children[index].minimumWidth)
          let share = max(max(allocated[position], minimum), 0)
          let measured = preferredSize(
            of: child, in: CGSize(width: share, height: available.height))
          size = CGSize(width: share, height: measured.height)
        } else {
          size = preferredSize(of: child, in: available)
        }
        lineItems.append((item: child, size: size))
        width += size.width
      }
      let height = lineItems.map(\.size.height).max() ?? 0
      lines.append(NativeLayoutFlowLine(items: lineItems, width: width, height: height))
    }
    return (lines, segmented.discarded.map { items[$0] })
  }
}

/// One wrapped line of a flow container.
struct NativeLayoutFlowLine<Item> {
  let items: [(item: Item, size: CGSize)]
  let width: CGFloat
  let height: CGFloat
}

// MARK: - Frame tree

/// Where one component ended up after a whole-tree pass.
struct NativeLayoutFrame: Equatable {
  let componentID: Int
  let depth: Int
  /// In the parent component's coordinate space, offset modifier included. `.zero` for a component
  /// no container placed (a GONE child, say).
  let frame: CGRect
  /// This component's own bounds less its padding, in its own coordinate space.
  let contentFrame: CGRect
  /// The effective visibility after every container's collapse, flow and FitBox decisions.
  let isHidden: Bool
  /// The content extent a scrolled container arranged against, along its scroll axis.
  let scrollExtent: CGFloat?
}

/// The immutable result of laying a whole tree out: one frame per component, in document
/// (pre-)order.
struct NativeLayoutFrameTree: Equatable {
  let frames: [NativeLayoutFrame]

  /// The first component with this ID, in document order.
  func frame(ofComponent componentID: Int) -> NativeLayoutFrame? {
    frames.first { $0.componentID == componentID }
  }
}

extension NativeLayoutEngine {
  /// Lays out the whole tree under `root` within `size`, the way a renderer's views do pass by
  /// pass: each container arranges against the bounds its parent gave it, and structural wrappers
  /// take their parent's bounds.
  func frameTree<Item: NativeLayoutItem>(root: Item, size: CGSize) -> NativeLayoutFrameTree {
    var frames: [ObjectIdentifier: CGRect] = [
      ObjectIdentifier(root): CGRect(origin: .zero, size: size)
    ]
    var hidden: [ObjectIdentifier: Bool] = [:]
    var output: [NativeLayoutFrame] = []

    func prepareStructuralChildren(of item: Item, in bounds: CGRect) {
      for child in item.layoutChildren where Self.isStructural(child.layoutNode) {
        frames[ObjectIdentifier(child)] = bounds
        prepareStructuralChildren(of: child, in: CGRect(origin: .zero, size: bounds.size))
      }
    }

    // A component under a hidden ancestor is hidden too, whatever its own visibility says.
    func visit(_ item: Item, depth: Int, ancestorIsHidden: Bool) {
      let id = ObjectIdentifier(item)
      let node = item.layoutNode
      let frame = frames[id] ?? .zero
      let bounds = CGRect(origin: .zero, size: frame.size)
      prepareStructuralChildren(of: item, in: bounds)
      var extent: CGFloat?
      if !Self.isStructural(node) {
        let arrangement = arrange(item, in: bounds)
        for change in arrangement.visibilityChanges {
          hidden[ObjectIdentifier(change.item)] = change.isHidden
        }
        if let containerIsHidden = arrangement.containerIsHidden { hidden[id] = containerIsHidden }
        for placement in arrangement.placements {
          frames[ObjectIdentifier(placement.item)] = placement.frame
        }
        extent = arrangement.scrollExtent
      }
      let isHidden =
        ancestorIsHidden || (hidden[id] ?? (node.visibility == NativeSwiftVisibility.gone))
      output.append(
        NativeLayoutFrame(
          componentID: node.componentID, depth: depth, frame: frame,
          contentFrame: contentRect(of: node, in: bounds), isHidden: isHidden,
          scrollExtent: extent))
      for child in item.layoutChildren {
        visit(child, depth: depth + 1, ancestorIsHidden: isHidden)
      }
    }

    visit(root, depth: 0, ancestorIsHidden: false)
    return NativeLayoutFrameTree(frames: output)
  }
}

// MARK: - Core snapshots

/// What a whole-tree pass over core snapshots asks the host to measure.
protocol NativeLayoutContentMeasuring {
  /// The size `text` draws at within `maximumWidth`.
  func size(of text: NativeSwiftTextSnapshot, maximumWidth: CGFloat) -> CGSize
  /// An image's intrinsic size, or nil when it is not loaded.
  func imageSize(imageID: Int) -> CGSize?
  /// A host custom component's fitting size, or nil when none is registered for it.
  func customSize(of custom: NativeSwiftCustomSnapshot, fitting available: CGSize) -> CGSize?
}

extension NativeLayoutContentMeasuring {
  func imageSize(imageID: Int) -> CGSize? { nil }
  func customSize(of custom: NativeSwiftCustomSnapshot, fitting available: CGSize) -> CGSize? {
    nil
  }
}

/// A core snapshot node as a layout item, for a whole-tree pass with no views.
final class NativeLayoutSnapshotItem: NativeLayoutItem {
  let snapshot: NativeSwiftNodeSnapshot
  let layoutNode: NativeLayoutNode
  let layoutChildren: [NativeLayoutSnapshotItem]
  let layoutCache = NativeLayoutSizeCache()
  private let measurer: any NativeLayoutContentMeasuring

  init(snapshot: NativeSwiftNodeSnapshot, measurer: any NativeLayoutContentMeasuring) {
    self.snapshot = snapshot
    self.measurer = measurer
    layoutNode = NativeLayoutNode(snapshot: snapshot)
    layoutChildren = snapshot.children.map {
      NativeLayoutSnapshotItem(snapshot: $0, measurer: measurer)
    }
  }

  func layoutContentSize(fitting available: CGSize) -> CGSize {
    switch layoutNode.kind {
    case .text:
      return snapshot.text.map { measurer.size(of: $0, maximumWidth: available.width) } ?? .zero
    case .image:
      // The first bitmap whose image is loaded, as a renderer's first promoted image view.
      for command in snapshot.commands where command.kind == NativeSwiftDrawKind.bitmap {
        if let draw = command.image, let size = measurer.imageSize(imageID: draw.imageID) {
          return size
        }
      }
      return .zero
    case .custom:
      return snapshot.custom.flatMap { measurer.customSize(of: $0, fitting: available) } ?? .zero
    default:
      return .zero
    }
  }
}

extension NativeLayoutEngine {
  /// Lays out a core snapshot tree within `size`, measuring text, images and custom components
  /// through `measurer`. Each call measures afresh.
  func frameTree(
    root: NativeSwiftNodeSnapshot, size: CGSize, measurer: any NativeLayoutContentMeasuring
  ) -> NativeLayoutFrameTree {
    frameTree(root: NativeLayoutSnapshotItem(snapshot: root, measurer: measurer), size: size)
  }
}

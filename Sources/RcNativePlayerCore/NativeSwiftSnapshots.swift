import Foundation

/// `ATTRIBUTE_TEXT`'s selector: every value AndroidX's `TextAttribute` defines.
public enum NativeSwiftTextAttributeType {
  public static let measureWidth = 0
  public static let measureHeight = 1
  public static let measureLeft = 2
  public static let measureRight = 3
  public static let measureTop = 4
  public static let measureBottom = 5
  public static let length = 6
}

/// One conditional container as evaluated while linking a document.
public struct NativeSwiftConditionalTraceSnapshot: Sendable {
  public let type: Int
  public let left: Float
  public let right: Float
  public let executed: Bool
  public let executedChildOps: Int
  public let path: String
}

/// Immutable, platform-neutral output from the Swift wire/runtime path.
///
/// Keeping this model free of UIKit and foreign-runtime objects lets decoding and document state remain
/// actor-isolated while the final UIView model is materialized on the main actor.
public struct NativeSwiftDocumentSnapshot: Sendable {
  public let width: Int
  public let height: Int
  public let density: Float
  public let densityBehavior: Int
  public let root: NativeSwiftNodeSnapshot
  public let images: [NativeSwiftImageResourceSnapshot]
  public let needsContinuousFrames: Bool
  /// Whether the document reads a wall-clock variable that only changes on a second boundary.
  ///
  /// The discrete fields — `TIME_IN_SEC`, `TIME_IN_MIN`, `TIME_IN_HR`, `CALENDAR_MONTH`,
  /// `OFFSET_TO_UTC`, `WEEK_DAY`, `DAY_OF_MONTH`, `DAY_OF_YEAR`, `YEAR` — are constant within a
  /// second, so a host that idles after the first frame shows a frozen clock. A host that supplies
  /// a wall clock should re-resolve at least once a second while this is true.
  public let needsWallClockRefresh: Bool
  /// Components whose measured geometry a document binds to a float.
  ///
  /// Empty for almost every document, and that is the point: a host only has to measure and feed
  /// back what is named here, so the refinement costs nothing where nothing depends on it.
  public let boundComponents: Set<Int>
  /// Layout transition specifications declared by the document, keyed by animation id.
  public let animationSpecs: [Int: NativeSwiftAnimationSpec]
  /// Animation ids in declaration order, for truthful operation-record observations.
  public let animationSpecOrder: [Int]
  /// Path resource ids declared by the document, including procedural path construction.
  public let pathIDs: Set<Int>
  /// Output ids declared by `PATH_TWEEN` operations.
  public let pathTweenIDs: Set<Int>
  /// Every declared accessibility operation, including root-attached and repeated modifiers.
  public let accessibilityRecords: [NativeSwiftAccessibilitySnapshot]
  /// Runtime shader uniform names keyed by shader id. Values remain unobserved by design.
  public let shaderUniformNames: [Int: Set<String>]
  /// Conditional containers evaluated while linking the document.
  public let conditionalTraces: [NativeSwiftConditionalTraceSnapshot]
}

/// The native timing metadata a layout component names on the Remote Compose wire.
public struct NativeSwiftAnimationSpec: Sendable {
  public let motionDuration: Float
  public let motionEasingType: Int
  public let visibilityDuration: Float
  public let visibilityEasingType: Int
  public let enterAnimation: Int
  public let exitAnimation: Int

  public init(
    motionDuration: Float, motionEasingType: Int, visibilityDuration: Float,
    visibilityEasingType: Int, enterAnimation: Int, exitAnimation: Int
  ) {
    self.motionDuration = motionDuration
    self.motionEasingType = motionEasingType
    self.visibilityDuration = visibilityDuration
    self.visibilityEasingType = visibilityEasingType
    self.enterAnimation = enterAnimation
    self.exitAnimation = exitAnimation
  }
}

public struct NativeSwiftImageResourceSnapshot: Sendable {
  public let id: Int
  public let width: Int
  public let height: Int
  public let type: Int
  public let encoding: Int
  public let data: Data
}

public struct NativeSwiftNodeSnapshot: Sendable {
  public enum Kind: Sendable {
    case root, content, canvas, box, row, column, text, image, custom
  }

  public let kind: Kind
  /// The AndroidX class name of the operation that produced this node — `BoxLayout`, `CoreText`,
  /// `StateLayout` — as the conformance corpus's `tree` probe spells it. Empty for a structural
  /// content wrapper, which the corpus never names.
  public let componentKind: String
  public let componentID: Int
  public let children: [NativeSwiftNodeSnapshot]
  public let commands: [NativeSwiftDrawCommandSnapshot]
  public let isClickable: Bool
  public let supportedGestures: [NativeSwiftGestureKind]
  public let accessibility: NativeSwiftAccessibilitySnapshot?
  public let widthType: Int
  public let widthValue: Float
  public let heightType: Int
  public let heightValue: Float
  public let padding: NativeSwiftInsets
  public let minimumWidth: Float
  public let maximumWidth: Float
  public let minimumHeight: Float
  public let maximumHeight: Float
  public let cornerRadius: Float
  public let clipsToBounds: Bool
  public let graphicsLayer: NativeSwiftGraphicsLayerSnapshot?
  public let offsetX: Float
  public let offsetY: Float
  public let zIndex: Float
  /// 0 gone, 1 visible, 2 invisible — the protocol's own values, after its override bits.
  public let visibility: Int
  public let backgroundARGB: UInt32?
  public let borderARGB: UInt32?
  public let borderWidth: Float
  public let horizontalPositioning: Int
  public let verticalPositioning: Int
  /// The animation specification this component names, if it has one.
  public let animationID: Int?
  public let spacing: Float
  /// The child a `StateLayout` is showing, clamped to its children, or nil when this node is not a
  /// state layout. The inactive children arrive GONE, which is what the reference does.
  public let stateIndex: Int?
  /// A `FlowLayout`'s wrap bounds, nil for every other node. Zero or less means unlimited, which is
  /// how the reference reads them.
  public let flowMaximumItems: Int?
  public let flowMaximumLines: Int?
  /// True for the collapsible row/column family: a container that hides the children that do not
  /// fit, in the order their `CollapsiblePriority` modifiers give.
  public let isCollapsible: Bool
  /// The priority this component's own `CollapsiblePriority` modifier declared, when it carries one
  /// for the container's axis. Nil means the component sorts as "no priority", which the reference
  /// keeps ahead of every explicit one.
  public let collapsiblePriority: Float?
  /// The axis that priority applies to: 0 horizontal, 1 vertical.
  public let collapsiblePriorityOrientation: Int?
  /// The scroll modifier's direction — 0 vertical, 1 horizontal — or nil when this component is
  /// not scrollable.
  ///
  /// A scroll does not move the layout: its translation is applied at paint time, which is why the
  /// corpus keeps it out of a node's `x`/`y` and reports it as `scroll_x`/`scroll_y` instead. What
  /// a renderer needs from the document is where the scroll currently is.
  public let scrollDirection: NativeSwiftScrollDirection?
  /// The slot the scroll's position lives in, so a gesture can move it: a scroll is a modifier
  /// *translation* the layout never sees, and the document's own float is its state.
  public let scrollPositionID: Int?
  /// The scroll offset, resolved from the document's float at snapshot time. Zero until the document
  /// or a gesture moves it.
  public let scrollOffset: Float
  /// How far the scroll may travel, from the modifier's own maximum. Zero means the document
  /// has not had its measured travel written yet; a renderer can derive it from content measurement.
  public let scrollMaximum: Float
  public let text: NativeSwiftTextSnapshot?
  public let custom: NativeSwiftCustomSnapshot?
}

/// The 2D subset of MODIFIER_GRAPHICS_LAYER this player applies. Rotation about X and Y, Z
/// translation, camera distance, shadow elevation and blur are parsed and deliberately not applied:
/// nothing on the catalog sheet uses them, and a wrong 3D transform is worse than an absent one.
public struct NativeSwiftGraphicsLayerSnapshot: Sendable, Equatable {
  public let scaleX: Float
  public let scaleY: Float
  public let translationX: Float
  public let translationY: Float
  public let rotationZ: Float
  public let alpha: Float

  public init(
    scaleX: Float, scaleY: Float, translationX: Float, translationY: Float, rotationZ: Float,
    alpha: Float
  ) {
    self.scaleX = scaleX
    self.scaleY = scaleY
    self.translationX = translationX
    self.translationY = translationY
    self.rotationZ = rotationZ
    self.alpha = alpha
  }

  public var isIdentity: Bool {
    scaleX == 1 && scaleY == 1 && translationX == 0 && translationY == 0 && rotationZ == 0
      && alpha == 1
  }
}

/// A component's laid-out size, as the host measured it.
///
/// Supplied back to `snapshot(timeSeconds:measuredComponents:)` so a document that binds a
/// component's geometry to a float resolves against what was actually laid out rather than against
/// this core's pre-layout estimate. The estimate cannot match: a host applies density scaling and
/// layout rules the core does not model, and the difference is not small — an icon whose scale is
/// derived from its own measured width rendered at 454 rather than 48 before this existed.
public struct NativeSwiftMeasuredSize: Sendable, Equatable {
  public let width: Float
  public let height: Float

  public init(width: Float, height: Float) {
    self.width = width
    self.height = height
  }
}

/// One operation's byte extent in a decoded document, as the decoder itself walked it.
///
/// Exposed so a mutation fuzzer can edit the operation stream as a structure — perturbing operands,
/// duplicating and dropping whole operations — rather than as flat bytes. The spans come from the
/// same walk that validates the document, so a mutator and the decoder cannot disagree about
/// framing: `endOffset` is exactly where the decoder stopped reading the operation.
public struct NativeSwiftOperationSpan: Sendable, Equatable {
  public let opcode: Int
  public let offset: Int
  public let endOffset: Int

  public init(opcode: Int, offset: Int, endOffset: Int) {
    self.opcode = opcode
    self.offset = offset
    self.endOffset = endOffset
  }

  public var byteCount: Int { endOffset - offset }
}

/// The current values of one decoded particle system. The outer array is ordered by particle index;
/// values within each particle use the definition's declared variable order.
public struct NativeSwiftParticleSystemSnapshot: Sendable, Equatable {
  public let id: Int
  public let variableIDs: [Int]
  public let particles: [[Float]]

  public init(id: Int, variableIDs: [Int], particles: [[Float]]) {
    self.id = id
    self.variableIDs = variableIDs
    self.particles = particles
  }
}

public struct NativeSwiftAccessibilitySnapshot: Sendable {
  public let contentDescriptionID: Int
  public let role: Int
  public let textID: Int
  public let stateDescriptionID: Int
  public let mode: Int
  public let contentDescription: String?
  public let text: String?
  public let stateDescription: String?
  public let isEnabled: Bool
  public let isClickable: Bool
}

public struct NativeSwiftDrawCommandSnapshot: Sendable {
  public let kind: Int
  public let values: [Float]
  public let colorARGB: UInt32
  public let alpha: Float
  public let strokeWidth: Float
  public let isStroke: Bool
  public let strokeCap: Int
  public let strokeJoin: Int
  public let blendMode: Int
  public let path: [NativeSwiftPathElementSnapshot]
  public let pathWinding: Int
  public let image: NativeSwiftImageDrawSnapshot?
  public let textureImageID: Int?
  public let textureTileModeX: Int
  public let textureTileModeY: Int
  /// The shader's local matrix as AndroidX's `MatrixAccess` 3x3 layout —
  /// `[scaleX, skewX, translateX, skewY, scaleY, translateY, persp0, persp1, persp2]` — or nil
  /// when the paint never named one. A texture drawn without it tiles at its natural size.
  public let shaderMatrix: [Float]?
  /// How the paint samples an image or texture: 0 none, 1 low, 2 medium, 3 high. Nil when the
  /// paint never said, which leaves the renderer's own default in place.
  public let filterQuality: Int?
  public let usesComponentGeometry: Bool
  public let gradient: NativeSwiftGradientSnapshot?
  public let text: String?
  public let textSize: Float
  public let textFlags: Int
}

/// A resolved paint gradient: colours as ARGB, stops and coordinates as floats, ready to draw.
/// `kind` is 0 linear, 1 radial, 2 sweep, matching the wire.
public struct NativeSwiftGradientSnapshot: Sendable, Equatable {
  public let kind: Int
  public let colorsARGB: [UInt32]
  public let stops: [Float]
  public let values: [Float]
  public let tileMode: Int

  public init(kind: Int, colorsARGB: [UInt32], stops: [Float], values: [Float], tileMode: Int) {
    self.kind = kind
    self.colorsARGB = colorsARGB
    self.stops = stops
    self.values = values
    self.tileMode = tileMode
  }
}

public struct NativeSwiftImageDrawSnapshot: Sendable {
  public let imageID: Int
  public let sourceLeft: Float
  public let sourceTop: Float
  public let sourceRight: Float
  public let sourceBottom: Float
  public let destinationLeft: Float
  public let destinationTop: Float
  public let destinationRight: Float
  public let destinationBottom: Float
  public let scaleType: Int
  public let scaleFactor: Float
  public let contentDescription: String?
}

public struct NativeSwiftPathElementSnapshot: Sendable {
  public let kind: Int
  public let values: [Float]
}

public enum NativeSwiftEvent: Equatable, Sendable {
  case namedAction(name: String, value: NativeSwiftActionValue)
}

public enum NativeSwiftGestureKind: Int, CaseIterable, Equatable, Sendable {
  case tap
  case longPress
  case doubleTap
  case touchDown
  case touchUp
  case touchCancel
}

/// The axis a scroll modifier moves along.
public enum NativeSwiftScrollDirection: Int, Sendable {
  case vertical = 0
  case horizontal = 1
}

/// How a scroll moves under a drag.
///
/// A scroll is a modifier *translation*, so the layout never sees it: the content follows the finger
/// and the offset is what the paint and the corpus's `scroll_x`/`scroll_y` read. Shared here so both
/// renderers move a scroll the same way — the corpus asserts the arithmetic through
/// `interaction_scroll_row`, whose 40-point drag reports `scroll_x: -40`.
public enum NativeSwiftScrollGesture {
  /// The offset after dragging by `delta` points along the scroll's axis.
  ///
  /// Dragging *towards* the start of the content (a negative delta on a horizontal scroll) moves the
  /// content with the finger, which *increases* the offset; the travel clamps at both ends.
  public static func offset(afterDragging current: Float, delta: Float, maximum: Float) -> Float {
    guard maximum > 0 else { return 0 }
    return min(max(current - delta, 0), maximum)
  }
}

/// The values a document holds at one instant, for the conformance corpus's value probes.
///
/// The corpus's `float`, `int`, `text` and `color` probes read a document's *state* rather than its
/// rendering: an expression's result, a variable a gesture wrote, a colour an expression built. A
/// target is either a numeric slot or the name of a variable the document declared.
public struct NativeSwiftProbeValues: Sendable {
  public let floats: [Int: Float]
  public let integers: [Int: Int]
  public let texts: [Int: String]
  /// ARGB, as the corpus spells it: an unsigned 32-bit integer, so opaque red is 4294901760 rather
  /// than the negative Int the same bits mean here.
  public let colors: [Int: UInt32]
}

/// A matrix declaration exactly as it appeared on the wire. Matrix probes deliberately retain a
/// 3x3 declaration as nine values rather than exposing the runtime's expanded 4x4 representation.
public struct NativeSwiftMatrixSnapshot: Sendable {
  public let id: Int
  public let values: [Float]
}

public struct NativeSwiftPointerSample: Equatable, Sendable {
  public let x: Float
  public let y: Float
  public let velocityX: Float
  public let velocityY: Float

  public init(x: Float, y: Float, velocityX: Float = 0, velocityY: Float = 0) {
    self.x = x
    self.y = y
    self.velocityX = velocityX
    self.velocityY = velocityY
  }
}

public enum NativeSwiftActionValue: Equatable, Sendable {
  case none
  case float(Float)
  case integer(Int)
  case text(String)
}

public struct NativeSwiftInsets: Sendable {
  public var left: Float = 0
  public var top: Float = 0
  public var right: Float = 0
  public var bottom: Float = 0
}

public struct NativeSwiftTextSnapshot: Sendable {
  public let value: String
  public let colorARGB: UInt32
  public let size: Float
  public let style: Int
  public let weight: Float
  public let familyID: Int
  public let familyName: String?
  public let alignment: Int
  public let overflow: Int
  public let maximumLines: Int

  public init(
    value: String, colorARGB: UInt32, size: Float, style: Int, weight: Float,
    familyID: Int, familyName: String? = nil, alignment: Int, overflow: Int, maximumLines: Int
  ) {
    self.value = value
    self.colorARGB = colorARGB
    self.size = size
    self.style = style
    self.weight = weight
    self.familyID = familyID
    self.familyName = familyName
    self.alignment = alignment
    self.overflow = overflow
    self.maximumLines = maximumLines
  }
}

public struct NativeSwiftCustomSnapshot: Sendable {
  public let config: String
  public let properties: [NativeSwiftCustomPropertySnapshot]
}

public struct NativeSwiftCustomPropertySnapshot: Sendable {
  public let id: Int
  public let dataType: Int
  public let floatValue: Float
  public let integerValue: Int
  public let textValue: String?
}

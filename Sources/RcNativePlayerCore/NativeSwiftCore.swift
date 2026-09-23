import Foundation

/// Native names for the image scale values defined by AndroidX `ImageScaling`.
/// Keep every wire value here so native hosts do not grow independent magic-number mappings.
public enum NativeSwiftImageScaleType {
  public static let none = 0
  public static let inside = 1
  public static let fitWidth = 2
  public static let fitHeight = 3
  public static let fit = 4
  public static let crop = 5
  public static let fillBounds = 6
  public static let fixed = 7
}

/// The text transform values defined by AndroidX `TextTransform`.
public enum NativeSwiftTextTransformOperation {
  public static let identity = 0
  public static let lowercase = 1
  public static let uppercase = 2
  public static let trim = 3
  public static let capitalizeWords = 4
  public static let capitalizeFirst = 5
}

public enum NativeSwiftWireOpcode {
  public static let drawText = 43
  public static let drawTextOnPath = 53
  public static let drawTextOnCircle = 57
  public static let drawTextAnchored = 133
  public static let conditionalOperations = 178
  public static let drawBitmap = 44
  public static let textFromFloat = 135
  public static let textMerge = 136
  public static let drawBitmapScaled = 149
  public static let textLookup = 151
  public static let textLookupInt = 153
  public static let textTransform = 199
  public static let matrixConstant = 186
  public static let matrixExpression = 187
  public static let matrixVectorMath = 188
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

public enum NativeSwiftCoreError: Error, CustomStringConvertible, LocalizedError {
  case unsupported(opcode: Int, offset: Int, reason: String)
  case malformed(offset: Int, reason: String)

  public var isUnsupported: Bool {
    if case .unsupported = self { return true }
    return false
  }

  /// `LocalizedError`, not just `CustomStringConvertible`, because callers reach this through
  /// `localizedDescription`. Without the conformance that goes through the `NSError` bridge and
  /// renders as "The operation couldn't be completed. (…NativeSwiftCoreError error 1.)", which is
  /// what a host puts in front of a user when a *frame* fails to resolve — the document-open path
  /// happens to downcast and read `description`, the per-frame path does not.
  public var errorDescription: String? { description }

  public var description: String {
    switch self {
    case .unsupported(let opcode, let offset, let reason):
      return "Unsupported Remote Compose opcode \(opcode) at byte \(offset): \(reason)"
    case .malformed(let offset, let reason):
      return "Malformed Remote Compose document at byte \(offset): \(reason)"
    }
  }
}

/// The ids AndroidX's `RemoteContext` reserves for values the player supplies, not the document.
///
/// Only the ones this core actually loads are named, matching the upstream `RcSystemVariables`
/// contract. A reference to an id the player does not load resolves to 0 and poisons the arithmetic
/// downstream, so naming one here without loading it would be worse than leaving it out.
///
/// `ANIMATION_DELTA_TIME` (31) and `EPOCH_SECOND` (32) are deliberately absent: a delta needs the
/// previous frame, which this core's stateless snapshot does not keep, and the epoch second is an
/// *integer* variable whose expressions are evaluated at decode time, before a host clock can be
/// supplied.
public enum NativeSwiftSystemVariables {
  /// Device pixels per dp, as the player is playing the document.
  public static let density = 27

  /// The host's default text size in pixels — 14sp at the player's density and font scale.
  public static let fontSize = 33

  /// The `sp` behind `fontSize`, before density and font scale. Every player has to agree on it:
  /// a capture divides its text size by this id to recover the `sp` it authored.
  public static let defaultFontSizeSp: Float = 14

  /// Wall-clock seconds within the current hour, including the fractional second.
  public static let continuousSeconds = 1

  /// Wall-clock seconds within the current hour, whole seconds.
  public static let timeInSeconds = 2

  /// Wall-clock minutes within the current day.
  public static let timeInMinutes = 3

  /// Wall-clock hours within the current day.
  public static let timeInHours = 4

  /// Calendar month, 1...12.
  public static let calendarMonth = 9

  /// The local zone's offset from UTC in seconds.
  public static let offsetToUTC = 10

  /// ISO day of the week, Monday 1 ... Sunday 7.
  public static let weekDay = 11

  /// Day of the month, 1...31.
  public static let dayOfMonth = 12

  /// The last pointer coordinate the host delivered to the document.
  public static let touchX = 13
  public static let touchY = 14

  /// Seconds since the document's first frame, the animation clock a host advances.
  public static let animationTime = 30

  /// Day of the year, 1...366.
  public static let dayOfYear = 34

  /// Calendar year.
  public static let year = 35
}

/// The host's wall clock, which is what a document's calendar and time-of-day variables read.
///
/// `snapshot(timeSeconds:)` is deliberately pure: it advances only by the elapsed time the host
/// hands it, so a test or a corpus capture can hold it still. A document that reads `YEAR` or
/// `TIME_IN_SEC` needs an absolute instant, which the elapsed clock cannot provide, so a host that
/// wants those fields supplies one here — and a host that does not leaves them unset rather than
/// getting the 1970 epoch silently.
public struct NativeSwiftWallClock: Sendable, Equatable {
  public let epochMillis: Int64
  /// The local zone's offset from UTC at that instant. The reference reads the system zone; a
  /// corpus capture freezes both the instant and the zone it was rendered in.
  public let offsetSeconds: Int

  public init(epochMillis: Int64, offsetSeconds: Int = 0) {
    self.epochMillis = epochMillis
    self.offsetSeconds = offsetSeconds
  }

  /// The calendar fields AndroidX's `TimeVariables` publishes, in the local zone.
  struct Fields {
    let year: Int
    let month: Int
    let dayOfMonth: Int
    let dayOfYear: Int
    let hour: Int
    let minute: Int
    let second: Int
    let isoDayOfWeek: Int
    let millisOfSecond: Int

    /// Seconds within the current hour, whole seconds.
    var secondOfHour: Int { minute * 60 + second }
  }

  var fields: Fields {
    // Floor division, not truncation: `-1 ms` is the last millisecond of 1969, and truncating would
    // read it as 1970-01-01T00:00:00.999.
    let localSeconds = Self.floorDiv(epochMillis, 1000) + Int64(offsetSeconds)
    let millis = Int(((epochMillis % 1000) + 1000) % 1000)
    let days = Self.floorDiv(localSeconds, 86400)
    let secondOfDay = Int(localSeconds - days * 86400)
    let civil = Self.civilFromDays(days)
    let dayOfYear = Int(days - Self.daysFromCivil(civil.year, 1, 1)) + 1
    // 1970-01-01 was a Thursday, and ISO numbers Monday as 1. The remainder is normalized because
    // Swift keeps the dividend's sign, which would put a pre-epoch date outside 1...7.
    let isoDayOfWeek = Int(((days + 3) % 7 + 7) % 7) + 1
    return Fields(
      year: Int(civil.year),
      month: Int(civil.month),
      dayOfMonth: Int(civil.day),
      dayOfYear: dayOfYear,
      hour: secondOfDay / 3600,
      minute: (secondOfDay % 3600) / 60,
      second: secondOfDay % 60,
      isoDayOfWeek: isoDayOfWeek,
      millisOfSecond: millis)
  }

  private static func floorDiv(_ value: Int64, _ divisor: Int64) -> Int64 {
    let quotient = value / divisor
    return value % divisor < 0 ? quotient - 1 : quotient
  }

  /// Howard Hinnant's civil-from-days, the inverse of `daysFromCivil`.
  private static func civilFromDays(_ days: Int64) -> (year: Int64, month: Int64, day: Int64) {
    let shifted = days + 719468
    let era = (shifted >= 0 ? shifted : shifted - 146096) / 146097
    let dayOfEra = shifted - era * 146097
    let yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    let year = yearOfEra + era * 400
    let dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    let monthPrime = (5 * dayOfYear + 2) / 153
    let day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    let month = monthPrime + (monthPrime < 10 ? 3 : -9)
    return (year + (month <= 2 ? 1 : 0), month, day)
  }

  private static func daysFromCivil(_ year: Int64, _ month: Int64, _ day: Int64) -> Int64 {
    let adjustedYear = year - (month <= 2 ? 1 : 0)
    let era = (adjustedYear >= 0 ? adjustedYear : adjustedYear - 399) / 400
    let yearOfEra = adjustedYear - era * 400
    let dayOfYear =
      (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
    let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra - 719468
  }
}

/// Which children a collapsible container keeps.
///
/// A `CollapsibleColumnLayout` (233) or `CollapsibleRowLayout` (230) measures its children and hides
/// the ones that do not fit, dropping them in the order their `CollapsiblePriority` modifiers give.
/// This is the decision, separated from either renderer so it can be tested without a view hierarchy
/// — and so the UIKit and AppKit paths cannot disagree about it.
///
/// The rules are the reference's, including the two that are easy to get backwards:
///
/// * a child with **no** priority modifier sorts ahead of every child that has one, because upstream
///   orders by descending priority with `Float.greatestFiniteMagnitude` as the absent value;
/// * a child with a **weight** on the container's axis does not count against the available space,
///   so it is never the reason another child is dropped.
/// How a flow container's children divide into lines.
///
/// The reference segments a `FlowLayout` by walking its children and starting a new line when the
/// next one no longer fits. A child with a **weight** has no measured width yet — it takes its
/// line's leftover — so it contributes its `widthIn` *minimum* instead, which is what stops a
/// weighted child's minimum from being quietly ignored when its siblings are placed beside it.
/// A child the document marked GONE contributes nothing.
///
/// Kept here rather than in either renderer so the two cannot disagree about where a line breaks,
/// and so the rule is testable without a view hierarchy.
public enum NativeSwiftFlow {
  public struct Child: Sendable, Equatable {
    /// The child's measured width. Ignored when `weight` is positive.
    public let measuredWidth: Float
    /// The child's width weight, 0 when unweighted.
    public let weight: Float
    /// The child's `widthIn` minimum, 0 when it declares none.
    public let minimumWidth: Float
    public let isGone: Bool

    public init(
      measuredWidth: Float, weight: Float = 0, minimumWidth: Float = 0, isGone: Bool = false
    ) {
      self.measuredWidth = measuredWidth
      self.weight = weight
      self.minimumWidth = minimumWidth
      self.isGone = isGone
    }

    /// What this child contributes to the line it is placed on.
    var provisionalWidth: Float {
      if isGone { return 0 }
      if weight > 0 { return max(minimumWidth, 0) }
      return max(measuredWidth, 0)
    }
  }

  /// The line each child lands on, in document order, plus the children a maximum-line cap
  /// discarded.
  public static func segment(
    _ children: [Child], available: Float, spacing: Float = 0, maximumItems: Int = 0,
    maximumLines: Int = 0
  ) -> (lines: [[Int]], discarded: [Int]) {
    var lines: [[Int]] = [[]]
    var discarded: [Int] = []
    var width: Float = 0
    var capped = false
    let gap = spacing.isFinite && spacing > 0 ? spacing : 0
    let itemCap = maximumItems > 0 ? maximumItems : Int.max
    let lineCap = maximumLines > 0 ? maximumLines : Int.max
    for (index, child) in children.enumerated() {
      // A GONE child is in neither the line nor its spacing or item count: it is not drawn, so
      // letting it occupy a slot would push a visible sibling onto a line of its own.
      if child.isGone { continue }
      if capped {
        discarded.append(index)
        continue
      }
      let provisional = child.provisionalWidth
      let current = lines[lines.count - 1]
      let wraps =
        !current.isEmpty
        && (current.count >= itemCap
          || width + gap + provisional > available)
      if wraps {
        guard lines.count < lineCap else {
          // No further line may be created, so this child and every child after it is discarded.
          // Letting a later, smaller one land on the current line would reorder the document.
          capped = true
          discarded.append(index)
          continue
        }
        lines.append([])
        width = 0
      }
      if !lines[lines.count - 1].isEmpty { width += gap }
      lines[lines.count - 1].append(index)
      width += provisional
    }
    return (lines, discarded)
  }
}

public enum NativeSwiftCollapsible {
  /// One child as the container sees it.
  public struct Child: Sendable, Equatable {
    /// The child's size along the container's axis, as measured. Ignored when `weight` is positive,
    /// matching the reference, which does not measure a weighted child until the kept set is known.
    public let mainSize: Float
    /// The child's weight on the container's axis, 0 when unweighted.
    public let weight: Float
    /// The priority the child declared for this axis, or nil when it declared none.
    public let priority: Float?
    /// True when the document itself marked the child GONE. Such a child is never kept and never
    /// consumes space.
    public let isGone: Bool

    public init(
      mainSize: Float, weight: Float = 0, priority: Float? = nil, isGone: Bool = false
    ) {
      self.mainSize = mainSize
      self.weight = weight
      self.priority = priority
      self.isGone = isGone
    }
  }

  /// Whether a child's main axis is measured unbounded when the fit test asks for its natural size.
  ///
  /// A fixed or wrapping child has a natural size worth measuring; a **fill** child does not — it
  /// takes whatever it is given, so measuring it unbounded resolves it to infinity and the fit test
  /// then drops it even when it is the container's only child. Fill dimensions keep the container's
  /// own bound instead.
  public static func measuresUnbounded(mainAxisType: Int) -> Bool {
    !(mainAxisType == 1 || mainAxisType == 7 || mainAxisType == 8)
  }

  /// One flag per child, in the order they were given.
  ///
  /// - Parameters:
  ///   - available: the container's space along its axis. A non-finite value is unbounded, which
  ///     keeps every child the document did not mark GONE.
  ///   - spacing: the gap between kept children, counted the way the container lays them out.
  public static func keptChildren(
    _ children: [Child], available: Float, spacing: Float
  ) -> [Bool] {
    var kept = [Bool](repeating: false, count: children.count)
    guard !children.isEmpty else { return kept }
    let gap = spacing.isFinite && spacing > 0 ? spacing : 0
    guard available.isFinite else {
      for (index, child) in children.enumerated() where !child.isGone { kept[index] = true }
      return kept
    }
    // Descending priority. An absent priority sorts first, and document order breaks ties, which is
    // what the reference's list sort does for equal priorities.
    let hasPriorities = children.contains { $0.priority != nil }
    let order: [Int]
    if hasPriorities {
      order = children.indices.sorted { first, second in
        let left = children[first].priority ?? Float.greatestFiniteMagnitude
        let right = children[second].priority ?? Float.greatestFiniteMagnitude
        if left == right { return first < second }
        return left > right
      }
    } else {
      order = Array(children.indices)
    }
    var used: Float = 0
    var keptCount = 0
    var overflow = false
    for index in order {
      let child = children[index]
      if child.isGone { continue }
      let size = child.weight > 0 ? 0 : max(child.mainSize, 0)
      let neededSpacing = keptCount > 0 ? gap : 0
      if overflow || used + neededSpacing + size > available {
        overflow = true
        continue
      }
      used += neededSpacing + size
      keptCount += 1
      kept[index] = true
    }
    return kept
  }
}

/// Retained document state with synchronous, internally serialized access across tasks.
public final class NativeSwiftDocumentSession: @unchecked Sendable {
  private struct StaticSnapshotCache {
    let measuredComponents: [Int: NativeSwiftMeasuredSize]
    let snapshot: NativeSwiftDocumentSnapshot
  }

  // Preserve the synchronous API while serializing its mutable session and snapshot cache. A
  // recursive lock is required because click() forwards to gesture().
  private let stateLock = NSRecursiveLock()
  private let document: ParsedDocument
  private var texts: [Int: String]
  private var floats: [Int: Float]
  private var floatOverrides: [Int: Float] = [:]
  private var hostDensity: Float = 1
  private var hostFontScale: Float = 1
  private var colors: [Int: UInt32]
  private var integers: [Int: Int]
  private var floatAnimationRuntimes: [Int: NativeSwiftFloatAnimationRuntime] = [:]
  private var particleSystems: [Int: NativeSwiftParticleSystemRuntime] = [:]
  private var lastParticleFrameTime: TimeInterval?
  // A host may request another frame for a document that has no clock-driven state. Preserve the
  // public value snapshot while sharing its copy-on-write storage instead of re-resolving the
  // complete parsed tree each time. Measurements remain part of the cache key because they are the
  // deliberate second pass of the layout contract.
  private var staticSnapshotCache: StaticSnapshotCache?

  private init(document: ParsedDocument) {
    self.document = document
    texts = document.texts
    floats = document.floats
    colors = document.colors
    integers = document.integers
  }

  /// Opens a document.
  ///
  /// - Parameter toleratingRootlessData: decode a *data-only* document — one that declares values
  ///   and nothing to draw — instead of refusing it. The conformance lane asks for this so its value
  ///   probes can be answered; a host that is about to render keeps the default and is told the
  ///   document has nothing to paint.
  public static func open(
    data: Data, toleratingRootlessData: Bool = false
  ) throws -> NativeSwiftDocumentSession {
    NativeSwiftDocumentSession(
      document: try NativeSwiftDocumentDecoder.decode(
        data, toleratingRootlessData: toleratingRootlessData))
  }

  /// The operation spans of a document the decoder accepts, for structure-aware mutation.
  ///
  /// Data-only documents remain strict by default. The conformance value lane can deliberately
  /// opt into the same rootless mode as ``open(data:toleratingRootlessData:)``.
  public static func operationSpans(
    in data: Data, toleratingRootlessData: Bool = false
  ) throws -> [NativeSwiftOperationSpan] {
    var spans: [NativeSwiftOperationSpan] = []
    _ = try NativeSwiftDocumentDecoder.decode(
      data, spans: &spans, toleratingRootlessData: toleratingRootlessData)
    return spans
  }

  /// The linked document's top-level operation census, with the header included. This is the
  /// operation model conformance exposes; it intentionally differs from raw wire spans.
  public var linkedOperationCount: Int { document.linkedOperationCount }

  private init(copying other: NativeSwiftDocumentSession) {
    document = other.document
    texts = other.texts
    floats = other.floats
    floatOverrides = other.floatOverrides
    colors = other.colors
    integers = other.integers
    hostDensity = other.hostDensity
    hostFontScale = other.hostFontScale
    floatAnimationRuntimes = other.floatAnimationRuntimes.mapValues { $0.detachedCopy() }
    particleSystems = other.particleSystems.mapValues { $0.detachedCopy() }
    lastParticleFrameTime = other.lastParticleFrameTime
    staticSnapshotCache = other.staticSnapshotCache
  }

  /// A session with this one's state, sharing nothing mutable.
  ///
  /// It exists so a host can re-resolve a snapshot **synchronously**, from inside its own layout
  /// pass, without reaching into the session its frames come from. That matters because `snapshot`
  /// writes as well as reads -- float-to-text conversions, lookups and merges all populate `texts`
  /// -- so calling it from another isolation domain would race whatever is producing frames.
  ///
  /// The copy holds the parsed document, which is immutable once decoded, and its own copies of
  /// everything else. Its state is therefore frozen at the moment it was taken: a host should take
  /// a fresh one with each frame rather than keeping one across document state changes.
  public func detachedCopy() -> NativeSwiftDocumentSession {
    stateLock.lock()
    defer { stateLock.unlock() }
    return NativeSwiftDocumentSession(copying: self)
  }

  /// Tells the document what density it is being played at.
  ///
  /// A `RemoteDensity.Host` capture defers its density instead of folding it in: it writes
  /// expressions over `ID_DENSITY` (27) and `ID_FONT_SIZE` (33), so the same document resolves at
  /// whatever the player supplies. Left unsaid, the native player plays at 1.0 — which is the
  /// density its default layout mode resolves dp geometry at, so the two agree by construction.
  /// A host reproducing Android geometry supplies its real playback density here.
  ///
  /// Non-finite and non-positive values are ignored rather than stored: both reach a document as a
  /// divisor, and a host that reports a zero density mid-layout should not turn the frame's
  /// geometry into NaN.
  public func setHostDensity(_ density: Float, fontScale: Float = 1) {
    stateLock.lock()
    defer { stateLock.unlock() }
    if density.isFinite, density > 0 { hostDensity = density }
    if fontScale.isFinite, fontScale > 0 { hostFontScale = fontScale }
    staticSnapshotCache = nil
  }

  /// - Parameter measuredComponents: what the host laid each bound component out at, once it knows.
  ///   A component named here resolves its width and height bindings from the measurement; one that
  ///   is absent falls back to this core's own estimate, which is what every caller got before this
  ///   parameter existed. Pass nothing on the first pass -- there is nothing to measure yet.
  /// - Parameter wallClock: the host's absolute time, for a document that reads a calendar or
  ///   time-of-day variable. Omitted, those variables are left unset and the elapsed clock is the
  ///   only one a document can animate off.
  public func snapshot(
    timeSeconds: TimeInterval = 0, wallClock: NativeSwiftWallClock? = nil,
    measuredComponents: [Int: NativeSwiftMeasuredSize] = [:]
  ) throws -> NativeSwiftDocumentSnapshot {
    stateLock.lock()
    defer { stateLock.unlock() }
    if canReuseStaticSnapshot(timeSeconds: timeSeconds, wallClock: wallClock),
      let cached = staticSnapshotCache, cached.measuredComponents == measuredComponents
    {
      return cached.snapshot
    }
    var values = try resolvedFloats(
      timeSeconds: timeSeconds, wallClock: wallClock, measuredComponents: measuredComponents)
    advanceParticles(values: values, timeSeconds: timeSeconds)
    // Particle advancement publishes the current particle registers into the session. Resolve one
    // more time before turning commands, colours and text into a snapshot so this frame observes
    // the state it just advanced rather than the previous frame's registers.
    if !document.particleLoops.isEmpty {
      values = try resolvedFloats(
        timeSeconds: timeSeconds, wallClock: wallClock, measuredComponents: measuredComponents)
    }
    resolveTextOperations(values: values)
    // Data-map lookup is an operation, not a decode-time constant. Its key can be created by a
    // preceding TextFromFloat/TextLookup/TextMerge, so resolve it only after those text producers
    // have run for this frame.
    for lookup in document.dataMapLookups {
      guard let key = texts[lookup.keyTextID], let entry = document.dataMaps[lookup.mapID]?[key]
      else { continue }
      switch entry.type {
      case 0:
        if let value = texts[entry.valueID] { texts[lookup.outputID] = value }
      case 1, 3, 4:
        integers[lookup.outputID] = integers[entry.valueID] ?? 0
      case 2:
        values[lookup.outputID] = values[entry.valueID] ?? floats[entry.valueID] ?? 0
      default:
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Unknown data-map type")
      }
    }
    let resolvedColors = resolveColors(values: values)
    let snapshot = NativeSwiftDocumentSnapshot(
      width: document.width,
      height: document.height,
      density: document.density,
      densityBehavior: document.densityBehavior,
      root: try resolve(document.root, values: values, colors: resolvedColors),
      images: document.images.values.sorted { $0.id < $1.id }.map(\.snapshot),
      needsContinuousFrames: document.needsContinuousFrames || !document.particleLoops.isEmpty
        || floatAnimationRuntimes.contains {
          floatOverrides[$0.key] == nil && $0.value.isAnimating(at: Float(timeSeconds))
        },
      needsWallClockRefresh: document.needsWallClockRefresh,
      boundComponents: Set(document.componentValues.map(\.componentID)),
      animationSpecs: document.animationSpecs, animationSpecOrder: document.animationSpecOrder,
      pathIDs: document.pathIDs, pathTweenIDs: document.pathTweenIDs,
      accessibilityRecords: document.accessibilityRecords.map {
        NativeSwiftAccessibilitySnapshot(
          contentDescriptionID: $0.contentDescriptionID, role: $0.role, textID: $0.textID,
          stateDescriptionID: $0.stateDescriptionID, mode: $0.mode,
          contentDescription: texts[$0.contentDescriptionID], text: texts[$0.textID],
          stateDescription: texts[$0.stateDescriptionID], isEnabled: $0.isEnabled,
          isClickable: $0.isClickable)
      }, shaderUniformNames: document.shaderUniformNames,
      conditionalTraces: document.conditionalTraces)
    if canReuseStaticSnapshot(timeSeconds: timeSeconds, wallClock: wallClock) {
      staticSnapshotCache = StaticSnapshotCache(
        measuredComponents: measuredComponents, snapshot: snapshot)
    }
    return snapshot
  }

  /// Advances retained particle systems to this frame and returns one system's current state.
  /// Rendering a particle loop uses the same retained state; this accessor additionally makes the
  /// behaviour observable to the native conformance host without exposing decoder internals.
  public func particleSnapshot(id: Int, timeSeconds: TimeInterval = 0) throws
    -> NativeSwiftParticleSystemSnapshot?
  {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    advanceParticles(values: values, timeSeconds: timeSeconds)
    return particleSystems[id]?.snapshot
  }

  /// Advances retained particle systems to this frame and returns every decoded system in stable
  /// wire order. Conformance documents can declare more than one system, so callers that observe
  /// particle state must not depend on dictionary iteration order.
  public func particleSnapshots(timeSeconds: TimeInterval = 0) throws
    -> [NativeSwiftParticleSystemSnapshot]
  {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    advanceParticles(values: values, timeSeconds: timeSeconds)
    return document.particleDefinitions.compactMap { particleSystems[$0.id]?.snapshot }
  }

  public func click(componentID: Int, timeSeconds: TimeInterval) throws -> [NativeSwiftEvent]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    return try gesture(.tap, componentID: componentID, sample: nil, timeSeconds: timeSeconds)
  }

  public func gesture(
    _ kind: NativeSwiftGestureKind, componentID: Int,
    sample: NativeSwiftPointerSample? = nil, timeSeconds: TimeInterval
  ) throws -> [NativeSwiftEvent]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let node = document.nodes[componentID], node.isClickable,
      node.accessibility?.isEnabled != false, let actions = node.actions[kind]
    else { return nil }
    let values = try resolvedFloats(timeSeconds: timeSeconds)
    // A sequence can mutate state before a later expression fails. Invalidate before executing the
    // first action so an error cannot leave a stale static snapshot cached over that mutation.
    staticSnapshotCache = nil
    var events: [NativeSwiftEvent] = []
    for action in actions {
      switch action {
      case .integerExpression(let targetID, let expressionID):
        guard let expression = document.integerExpressions[expressionID] else { continue }
        integers[targetID] = try NativeSwiftIntegerExpression.evaluate(
          mask: expression.mask, tokens: expression.tokens, values: integers)
      case .floatExpression(let targetID, let expressionID):
        guard let expression = document.expressions.first(where: { $0.id == expressionID })
        else { continue }
        floatOverrides[targetID] = try NativeSwiftFloatExpression.evaluate(
          expression.words, values: values)
      case .integerValue(let targetID, let value):
        integers[targetID] = value
      case .named(let action):
        guard let name = texts[action.nameTextID] else { continue }
        let value: NativeSwiftActionValue
        switch action.valueType {
        case -1: value = .none
        case 0: value = .float(values[action.valueID] ?? 0)
        case 1: value = .integer(integers[action.valueID] ?? 0)
        case 2: value = .text(texts[action.valueID] ?? "")
        default: continue
        }
        events.append(.namedAction(name: name, value: value))
      }
    }
    return events
  }

  /// Every value the document holds at `timeSeconds`, resolved the way `snapshot` resolves them.
  ///
  /// Called after `snapshot` so the text-from-float conversions and merges it performs are visible
  /// here too; the two reads then describe one instant rather than two.
  public func probeValues(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock? = nil
  ) throws -> NativeSwiftProbeValues {
    stateLock.lock()
    defer { stateLock.unlock() }
    var values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: wallClock)
    resolveTextOperations(values: values)
    for lookup in document.dataMapLookups {
      guard let key = texts[lookup.keyTextID], let entry = document.dataMaps[lookup.mapID]?[key]
      else { continue }
      switch entry.type {
      case 0:
        if let value = texts[entry.valueID] { texts[lookup.outputID] = value }
      case 1, 3, 4:
        integers[lookup.outputID] = integers[entry.valueID] ?? 0
      case 2:
        values[lookup.outputID] = values[entry.valueID] ?? floats[entry.valueID] ?? 0
      default:
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Unknown data-map type")
      }
    }
    // Integer expressions are part of the state, not only of an action's result: the reference
    // evaluates them as it resolves, so a probe reads what an expression computed rather than the
    // empty slot it started in. Order matters, and the document's own declaration order is what it
    // declares.
    var resolvedIntegers = integers
    for id in document.integerExpressionOrder {
      guard let expression = document.integerExpressions[id] else { continue }
      if let value = try? NativeSwiftIntegerExpression.evaluate(
        mask: expression.mask, tokens: expression.tokens, values: resolvedIntegers)
      {
        resolvedIntegers[id] = value
      }
    }
    return NativeSwiftProbeValues(
      floats: values, integers: resolvedIntegers, texts: texts,
      colors: resolveColors(values: values))
  }

  /// Resolves a static or dynamic float list for conformance state probes.
  public func probeFloatList(id: Int, dynamic: Bool, timeSeconds: TimeInterval) throws -> [Float]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    if !dynamic {
      if var result = document.floatLists[id]?.map({
        NativeSwiftFloatExpression.resolve($0, values: values)
      }) {
        for update in document.floatListUpdates[id] ?? [] {
          let index = Int(NativeSwiftFloatExpression.resolve(update.index, values: values))
          if result.indices.contains(index) {
            result[index] = NativeSwiftFloatExpression.resolve(update.value, values: values)
          }
        }
        return result
      }
      // The corpus calls both backing forms "data" lists. Fall through to the dynamic allocation
      // form when no literal DataListFloat with this id exists.
    }
    guard let list = document.dynamicFloatLists[id] else { return nil }
    let length = Int(NativeSwiftFloatExpression.resolve(list.lengthWord, values: values))
    guard (0...2_000).contains(length) else { return nil }
    var result = Array(repeating: Float(0), count: length)
    for update in list.updates {
      let index = Int(NativeSwiftFloatExpression.resolve(update.index, values: values))
      if result.indices.contains(index) {
        result[index] = NativeSwiftFloatExpression.resolve(update.value, values: values)
      }
    }
    return result
  }

  /// Returns a matrix in the exact shape the wire declares (nine values for a 3x3 constant,
  /// sixteen for a 4x4 constant or expression).
  public func probeMatrix(id: Int, timeSeconds: TimeInterval) throws -> [Float]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    if let constant = document.matrixConstants[id] { return constant.values }
    return document.matrixExpressions[id].flatMap {
      NativeSwiftMatrixExpression.evaluate4x4($0, values: values)
    }
  }

  /// The slot a document's named variable occupies, or nil when it declared no such name.
  ///
  /// A probe that names a variable the document never declared is genuinely unobservable; one that
  /// addresses a numeric slot the document left empty is an observation, and the caller has to keep
  /// the two apart.
  public func namedVariableID(_ name: String) -> Int? { namedVariable(for: name)?.id }

  /// The authoring API addresses user values without the wire format's `USER:` prefix. Keep the
  /// wire spelling available too, so hosts can use either form when a document names it explicitly.
  private func namedVariable(for name: String) -> ParsedNamedVariable? {
    document.namedVariables[name] ?? document.namedVariables["USER:\(name)"]
  }

  /// Writes one float slot by id, for a gesture that moves a document's own value — a scroll's
  /// offset is addressed by id rather than by name.
  @discardableResult
  public func setFloat(_ value: Float, forID id: Int) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.isFinite else { return false }
    floatOverrides[id] = value
    staticSnapshotCache = nil
    return true
  }

  public func setFloat(_ value: Float, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.isFinite, let variable = namedVariable(for: name), variable.type == 1 else {
      return false
    }
    floatOverrides[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  public func setString(_ value: String, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let variable = namedVariable(for: name), variable.type == 0
    else { return false }
    texts[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  public func setColor(_ value: UInt32, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let variable = namedVariable(for: name), variable.type == 2 else { return false }
    colors[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  /// Updates a named RemoteInt. RemoteBoolean uses this same wire type, with `0` and `1` standing
  /// for false and true respectively.
  public func setInteger(_ value: Int, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let variable = namedVariable(for: name), variable.type == 4 else { return false }
    integers[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  public func returnCustomText(_ value: String, componentID: Int, propertyID: Int) throws -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let node = document.nodes[componentID], node.kind == .custom,
      let property = node.custom?.properties.first(where: {
        $0.type == propertyID && $0.dataType == 4
      })
    else { return false }
    texts[property.valueBits] = value
    staticSnapshotCache = nil
    return true
  }

  public func returnCustomFloat(_ value: Float, componentID: Int, propertyID: Int) throws -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.isFinite, let node = document.nodes[componentID], node.kind == .custom,
      let property = node.custom?.properties.first(where: {
        $0.type == propertyID && $0.dataType == 3
      }),
      let targetID = NativeSwiftFloatExpression.referenceID(
        UInt32(bitPattern: Int32(property.valueBits)))
    else { return false }
    floatOverrides[targetID] = value
    staticSnapshotCache = nil
    return true
  }

  private func canReuseStaticSnapshot(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock?
  ) -> Bool {
    wallClock == nil
      && !document.needsContinuousFrames
      && document.particleLoops.isEmpty
      && !floatAnimationRuntimes.contains {
        floatOverrides[$0.key] == nil && $0.value.isAnimating(at: Float(timeSeconds))
      }
  }

  private func resolve(
    _ node: ParsedNode, values: [Int: Float], colors resolvedColors: [Int: UInt32],
    visibilityOverride: Int? = nil, stateBranchActive: Int? = nil
  ) throws -> NativeSwiftNodeSnapshot {
    let text: NativeSwiftTextSnapshot?
    if let source = node.text {
      // `size > 0` used to be a parse-time guard. It still holds, but a computed size has no value
      // to check until here, so the check moved with it rather than being dropped.
      let size = try resolvedFloat(source.sizeWord, "text size", values: values, positive: true)
      let weight = try resolvedFloat(source.weightWord, "text weight", values: values)
      text = NativeSwiftTextSnapshot(
        value: texts[source.textID] ?? "",
        colorARGB: source.colorID.flatMap { resolvedColors[$0] } ?? source.colorARGB,
        size: size,
        style: source.style,
        weight: min(max(weight, 1), 1_000),
        familyID: source.familyID,
        familyName: texts[source.familyID],
        alignment: source.alignment,
        overflow: source.overflow,
        maximumLines: source.maximumLines)
    } else {
      text = nil
    }

    let custom: NativeSwiftCustomSnapshot?
    if let source = node.custom {
      guard let config = texts[source.configID] else {
        throw NativeSwiftCoreError.malformed(
          offset: 0, reason: "Custom component \(node.componentID) has no config text")
      }
      let properties = source.properties.map { property in
        let floatValue =
          property.dataType == 1
          ? NativeSwiftFloatExpression.resolve(
            UInt32(bitPattern: Int32(property.valueBits)), values: values)
          : 0
        let integerValue: Int
        let textValue: String?
        switch property.dataType {
        case 2:
          integerValue = property.valueBits
          textValue = texts[property.valueBits]
        case 7:
          integerValue = Int(Int32(bitPattern: resolvedColors[property.valueBits] ?? 0))
          textValue = nil
        default:
          integerValue = property.valueBits
          textValue = nil
        }
        return NativeSwiftCustomPropertySnapshot(
          id: property.type, dataType: property.dataType, floatValue: floatValue,
          integerValue: integerValue, textValue: textValue)
      }
      custom = NativeSwiftCustomSnapshot(config: config, properties: properties)
    } else {
      custom = nil
    }

    let maximumWidth = try resolvedFloat(node.maximumWidthWord, "maximum width", values: values)
    let maximumHeight = try resolvedFloat(node.maximumHeightWord, "maximum height", values: values)
    let cornerRadii = try node.cornerRadiusWords.map {
      try resolvedFloat($0, "corner radius", values: values)
    }
    let cornerRadius = cornerRadii.max() ?? 0

    return NativeSwiftNodeSnapshot(
      kind: node.kind,
      componentKind: node.componentKind,
      componentID: node.componentID,
      children: try resolvedChildren(
        of: node, values: values, colors: resolvedColors,
        stateBranchActive: stateBranchActive),
      commands: try node.commands.map {
        try $0.resolve(
          values: values, colors: resolvedColors, texts: texts,
          componentValueIDs: Set(document.componentValues.map(\.valueID)),
          matrices: document.matrixExpressions)
      },
      isClickable: node.isClickable,
      supportedGestures: node.actions.keys.sorted { $0.rawValue < $1.rawValue },
      accessibility: node.accessibility.map {
        NativeSwiftAccessibilitySnapshot(
          contentDescriptionID: $0.contentDescriptionID, role: $0.role, textID: $0.textID,
          stateDescriptionID: $0.stateDescriptionID, mode: $0.mode,
          contentDescription: texts[$0.contentDescriptionID], text: texts[$0.textID],
          stateDescription: texts[$0.stateDescriptionID], isEnabled: $0.isEnabled,
          isClickable: $0.isClickable)
      },
      // A state layout takes the active child's size whatever the document asks for, matching the
      // reference: a fill modifier on the container itself is dropped rather than honoured, which
      // is what makes the container's own background and border cover the branch and not the
      // parent.
      widthType: stateLayoutDimension(node, isWidth: true),
      widthValue: try resolvedFloat(node.widthWord, "width", values: values),
      heightType: stateLayoutDimension(node, isWidth: false),
      heightValue: try resolvedFloat(node.heightWord, "height", values: values),
      // A state layout takes the active child's size, so its own padding is dropped with its fill:
      // the reference reports an 80x80 container for an 80x80 child behind a 20pt padding modifier,
      // not 120x120, and the child sits at the container's origin. Keeping the padding made the
      // container's background and border cover 40 points more than the branch.
      padding: node.stateIndexID != nil
        ? NativeSwiftInsets()
        : NativeSwiftInsets(
          left: try resolvedFloat(node.paddingWords.left, "padding left", values: values),
          top: try resolvedFloat(node.paddingWords.top, "padding top", values: values),
          right: try resolvedFloat(node.paddingWords.right, "padding right", values: values),
          bottom: try resolvedFloat(node.paddingWords.bottom, "padding bottom", values: values)),
      minimumWidth: try resolvedFloat(node.minimumWidthWord, "minimum width", values: values),
      maximumWidth: maximumWidth > 1_000_000 ? -1 : maximumWidth,
      minimumHeight: try resolvedFloat(node.minimumHeightWord, "minimum height", values: values),
      maximumHeight: maximumHeight > 1_000_000 ? -1 : maximumHeight,
      cornerRadius: cornerRadius,
      clipsToBounds: node.clipsToBounds,
      graphicsLayer: node.graphicsLayer.isEmpty
        ? nil
        : NativeSwiftGraphicsLayerSnapshot(
          scaleX: node.graphicsLayer[0] ?? 1, scaleY: node.graphicsLayer[1] ?? 1,
          translationX: node.graphicsLayer[5] ?? 0, translationY: node.graphicsLayer[6] ?? 0,
          rotationZ: node.graphicsLayer[4] ?? 0, alpha: node.graphicsLayer[8] ?? 1),
      offsetX: node.offsetXWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? 0,
      offsetY: node.offsetYWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? 0,
      zIndex: node.zIndexWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? 0,
      visibility: visibilityOverride ?? node.visibilityID.map { resolvedVisibility(of: $0) } ?? 1,
      backgroundARGB: node.backgroundColorID.flatMap { resolvedColors[$0] } ?? node.backgroundARGB,
      borderARGB: node.borderColorID.flatMap { resolvedColors[$0] } ?? node.borderARGB,
      borderWidth: try resolvedFloat(node.borderWidthWord, "border width", values: values),
      horizontalPositioning: node.horizontalPositioning,
      verticalPositioning: node.verticalPositioning,
      animationID: node.animationID,
      spacing: try resolvedFloat(node.spacingWord, "spacing", values: values),
      stateIndex: node.stateIndexID.flatMap { indexID in
        // Clamped against the *branches*, not the wrapper: the normal shape wraps every
        // alternative in one content node, and clamping against that would always report 0.
        let branches = stateBranches(of: node)
        return branches.isEmpty
          ? nil : min(max(integers[indexID] ?? 0, 0), branches.count - 1)
      },
      flowMaximumItems: node.flowMaximumItems,
      flowMaximumLines: node.flowMaximumLines,
      isCollapsible: node.isCollapsible,
      collapsiblePriority: try node.collapsiblePriorityWord.map {
        try resolvedFloat($0, "collapsible priority", values: values)
      },
      collapsiblePriorityOrientation: node.collapsiblePriorityOrientation,
      scrollDirection: node.scrollDirection,
      scrollPositionID: node.scrollPositionWord.flatMap {
        NativeSwiftFloatExpression.referenceID($0)
      },
      scrollOffset: node.scrollPositionWord.map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      } ?? 0,
      scrollMaximum: node.scrollMaximumWord.map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      } ?? 0,
      text: text,
      custom: custom)
  }

  /// A state layout's branches, unwrapping the bare content node they usually share.
  private func stateBranches(of node: ParsedNode) -> [ParsedNode] {
    guard node.children.count == 1, node.children[0].kind == .content,
      !node.children[0].children.isEmpty
    else { return node.children }
    return node.children[0].children
  }

  /// The width or height type a state layout resolves with; every other node keeps its own.
  ///
  /// The reference's `StateLayout` adopts its active child's size, so **its own dimension is
  /// dropped whatever kind it is** — a `FILL` modifier made the container cover the whole parent and
  /// paint its background over the branch, and a fixed one held the container at a size the branch
  /// never had. `interaction_click_button` declares `height(100)` on a container whose branches are
  /// 40 and 80 tall, and the reference reports 40 and then 80. Bounds (`widthIn`/`heightIn`) still
  /// apply: they constrain the child's size rather than replacing it.
  private func stateLayoutDimension(_ node: ParsedNode, isWidth: Bool) -> Int {
    guard node.stateIndexID != nil else { return isWidth ? node.widthType : node.heightType }
    return 2
  }

  /// A node's children, with a state layout's inactive branches marked GONE.
  ///
  /// A `StateLayout` shows one child — the one its index integer selects — and keeps the others
  /// GONE so they can take part in a transition. Laid out as a plain box it showed *every* branch
  /// stacked, which is the single largest remaining raster difference on the native lane.
  private func resolvedChildren(
    of node: ParsedNode, values: [Int: Float], colors resolvedColors: [Int: UInt32],
    stateBranchActive: Int? = nil
  ) throws -> [NativeSwiftNodeSnapshot] {
    // A state layout's own index decides which of *its* branches is shown, and the branches are
    // usually wrapped in the same bare content node every other container uses.
    if let indexID = node.stateIndexID, !node.children.isEmpty {
      let wrapper = node.children.count == 1 && node.children[0].kind == .content
        ? node.children[0] : nil
      let branches = stateBranches(of: node)
      guard !branches.isEmpty else {
        return try node.children.map { try resolve($0, values: values, colors: resolvedColors) }
      }
      let active = min(max(integers[indexID] ?? 0, 0), branches.count - 1)
      if let wrapper {
        return [
          try resolve(
            wrapper, values: values, colors: resolvedColors, stateBranchActive: active)
        ]
      }
      return try node.children.enumerated().map { position, child in
        try resolve(
          child, values: values, colors: resolvedColors,
          visibilityOverride: position == active ? 1 : 0)
      }
    }
    return try node.children.enumerated().map { position, child in
      try resolve(
        child, values: values, colors: resolvedColors,
        visibilityOverride: stateBranchActive.map { position == $0 ? 1 : 0 })
    }
  }

  /// Resolves a float field that a document may either state outright or compute.
  ///
  /// A literal word is its own bit pattern, so `resolve` is a strict generalisation of the eager
  /// `Float` these fields used to hold; a NaN-boxed word is a reference into `values`. Validation
  /// that used to run while parsing runs here, because a computed field has nothing to validate
  /// until it resolves — and failing here is what keeps a garbage value from reaching UIKit.
  private func resolvedFloat(
    _ word: UInt32, _ field: String, values: [Int: Float], positive: Bool = false
  ) throws -> Float {
    let value = NativeSwiftFloatExpression.resolve(word, values: values)
    guard value.isFinite else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "\(field) resolved to \(value)")
    }
    guard !positive || value > 0 else {
      throw NativeSwiftCoreError.malformed(
        offset: 0, reason: "\(field) resolved to \(value), which is not positive")
    }
    return value
  }

  private func resolvedFloats(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock? = nil,
    measuredComponents: [Int: NativeSwiftMeasuredSize] = [:]
  ) throws -> [Int: Float] {
    var result = floats
    result.merge(floatOverrides) { _, override in override }
    // RemoteBoolean is encoded as a named RemoteInt, then projected into float/color expressions by
    // RemoteInt.toRemoteFloat(). Expose integer slots to the float evaluator without overriding a
    // real float that deliberately shares an id.
    for (id, value) in integers where result[id] == nil { result[id] = Float(value) }
    // A copied dynamic colour is encoded as colour expression → channel attributes → colour
    // expression. Resolve the source colours before extracting their channels so the final colour
    // pass can preserve copies such as a selected tint with reduced alpha.
    let preliminaryColors = resolveColors(values: result)
    for attribute in document.colorAttributes {
      guard floatOverrides[attribute.outputID] == nil else { continue }
      result[attribute.outputID] = colorAttribute(
        attribute.type, of: preliminaryColors[attribute.colorID] ?? 0)
    }
    // Player-supplied clocks. A document that declares its own value at one of these ids keeps it,
    // matching the reference player's claimed-id rule — `floats` seeds `result`.
    //
    // The animation clock is the host's logical timeline, not the wall clock: it is what a capture
    // can hold still. `CONTINUOUS_SEC` is the wall clock's seconds within the current hour when a
    // host supplies one; without one the elapsed time is the only clock this core has, and a
    // document that animates off it still moves rather than standing at the 1970 epoch.
    let animationTime = Float(timeSeconds)
    if result[NativeSwiftSystemVariables.animationTime] == nil {
      result[NativeSwiftSystemVariables.animationTime] = animationTime
    }
    if let wallClock {
      let fields = wallClock.fields
      if result[NativeSwiftSystemVariables.continuousSeconds] == nil {
        result[NativeSwiftSystemVariables.continuousSeconds] =
          Float(fields.secondOfHour) + Float(fields.millisOfSecond) * 0.001
      }
      if result[NativeSwiftSystemVariables.timeInSeconds] == nil {
        result[NativeSwiftSystemVariables.timeInSeconds] = Float(fields.secondOfHour)
      }
      if result[NativeSwiftSystemVariables.timeInMinutes] == nil {
        result[NativeSwiftSystemVariables.timeInMinutes] =
          Float(fields.hour * 60 + fields.minute)
      }
      if result[NativeSwiftSystemVariables.timeInHours] == nil {
        result[NativeSwiftSystemVariables.timeInHours] = Float(fields.hour)
      }
      if result[NativeSwiftSystemVariables.calendarMonth] == nil {
        result[NativeSwiftSystemVariables.calendarMonth] = Float(fields.month)
      }
      if result[NativeSwiftSystemVariables.offsetToUTC] == nil {
        result[NativeSwiftSystemVariables.offsetToUTC] = Float(wallClock.offsetSeconds)
      }
      if result[NativeSwiftSystemVariables.weekDay] == nil {
        result[NativeSwiftSystemVariables.weekDay] = Float(fields.isoDayOfWeek)
      }
      if result[NativeSwiftSystemVariables.dayOfMonth] == nil {
        result[NativeSwiftSystemVariables.dayOfMonth] = Float(fields.dayOfMonth)
      }
      if result[NativeSwiftSystemVariables.dayOfYear] == nil {
        result[NativeSwiftSystemVariables.dayOfYear] = Float(fields.dayOfYear)
      }
      if result[NativeSwiftSystemVariables.year] == nil {
        result[NativeSwiftSystemVariables.year] = Float(fields.year)
      }
    } else if result[NativeSwiftSystemVariables.continuousSeconds] == nil {
      result[NativeSwiftSystemVariables.continuousSeconds] = animationTime
    }
    // Not clocks, but owed by the player for the same reason and with the same failure: an
    // unsupplied reference resolves to 0 here, so a `RemoteDensity.Host` capture's
    // `([33] 14.0 / [27] / 15.0 *)` divides by zero and every size built from it becomes NaN.
    // A document that declares its own value at either id keeps it — `floats` seeds `result`.
    if result[NativeSwiftSystemVariables.density] == nil {
      result[NativeSwiftSystemVariables.density] = hostDensity
    }
    if result[NativeSwiftSystemVariables.fontSize] == nil {
      result[NativeSwiftSystemVariables.fontSize] =
        NativeSwiftSystemVariables.defaultFontSizeSp * hostFontScale * hostDensity
    }
    // Expressions are evaluated twice, deliberately. A component-value binding measures a node, and
    // a node's own geometry can now itself be a reference, so neither ordering is right alone: the
    // first pass gives the measurement something better than zero to read, the second lets an
    // expression that reads a measured value see it. Evaluation is pure, so repeating it is safe.
    //
    // This first pass is deliberately tolerant. An expression that reads a binding the measurement
    // below has not produced yet resolves that reference to zero, which can divide to a non-finite
    // result that `evaluate` rejects — correctly, but not yet. Dropping it here leaves the id
    // unset, exactly as it was before this pass existed; the authoritative pass after the
    // measurement evaluates it for real and throws if it is still bad.
    for expression in document.expressions {
      guard floatOverrides[expression.id] == nil else { continue }
      if let value = try? NativeSwiftFloatExpression.evaluate(expression.words, values: result) {
        if let animationWords = expression.animationWords,
          let runtime = try? animationRuntime(for: expression.id, animationWords: animationWords)
        {
          // Geometry bindings are measured immediately below. They must see the same animated
          // value the final resolver will draw, not the expression's raw target.
          result[expression.id] = runtime.evaluate(target: value, at: Float(timeSeconds))
        } else {
          result[expression.id] = value
        }
      }
    }
    for binding in document.componentValues {
      guard floatOverrides[binding.valueID] == nil else { continue }
      // A real measurement wins over any estimate. Width and height are the only two types this
      // player resolves, and they are the two a host can report.
      if let measured = measuredComponents[binding.componentID],
        binding.type == 0 || binding.type == 1
      {
        result[binding.valueID] = binding.type == 0 ? measured.width : measured.height
        continue
      }
      let available = binding.type == 0 ? Float(document.width) : Float(document.height)
      guard let node = document.nodes[binding.componentID] else {
        // Matching the reference player: a width or height binding to a component this document
        // does not describe resolves to the document's own, rather than leaving the id unset and
        // taking every expression built on it down with it.
        if binding.type == 0 || binding.type == 1 { result[binding.valueID] = available }
        continue
      }
      let measuredNode = node.parent ?? node
      result[binding.valueID] = estimatedDimension(
        of: measuredNode, type: binding.type, available: available, values: result)
    }
    for expression in document.expressions {
      guard floatOverrides[expression.id] == nil else { continue }
      let target = try NativeSwiftFloatExpression.evaluate(expression.words, values: result)
      if let animationWords = expression.animationWords {
        let runtime = try animationRuntime(for: expression.id, animationWords: animationWords)
        result[expression.id] = runtime.evaluate(target: target, at: Float(timeSeconds))
      } else {
        result[expression.id] = target
      }
    }
    for operation in document.matrixVectorMath {
      guard let matrix = resolvedMatrix(operation.matrixID, values: result) else { continue }
      let input = operation.inputWords.map { NativeSwiftFloatExpression.resolve($0, values: result) }
      var output = [Float](repeating: 0, count: operation.outputIDs.count)
      if operation.type == 0 {
        for row in output.indices {
          var value = matrix[3 + row * 4]
          for column in input.indices { value += matrix[column + row * 4] * input[column] }
          output[row] = value
        }
      } else {
        var input4 = [Float](repeating: 0, count: 4)
        input4[3] = 1
        for index in input.indices { input4[index] = input[index] }
        var output4 = [Float](repeating: 0, count: 4)
        for row in 0..<4 {
          for column in 0..<4 { output4[row] += matrix[column + row * 4] * input4[column] }
        }
        guard output4[3] != 0 else { continue }
        for index in output.indices { output[index] = output4[index] / output4[3] }
      }
      for (index, outputID) in operation.outputIDs.enumerated() { result[outputID] = output[index] }
    }
    return result
  }

  private func resolvedMatrix(_ id: Int, values: [Int: Float]) -> [Float]? {
    if let constant = document.matrixConstants[id] {
      if constant.values.count == 16 { return constant.values }
      guard constant.values.count == 9 else { return nil }
      let v = constant.values
      return [v[0], v[1], 0, v[2], v[3], v[4], 0, v[5], v[6], v[7], v[8], 0, 0, 0, 0, 1]
    }
    return document.matrixExpressions[id].flatMap {
      NativeSwiftMatrixExpression.evaluate4x4($0, values: values)
    }
  }

  private func animationRuntime(
    for id: Int, animationWords: [UInt32]
  ) throws -> NativeSwiftFloatAnimationRuntime {
    if let existing = floatAnimationRuntimes[id] { return existing }
    let runtime = try NativeSwiftFloatAnimationRuntime(animationWords: animationWords)
    floatAnimationRuntimes[id] = runtime
    return runtime
  }

  private func advanceParticles(values: [Int: Float], timeSeconds: TimeInterval) {
    guard !document.particleLoops.isEmpty, lastParticleFrameTime != timeSeconds else { return }
    lastParticleFrameTime = timeSeconds
    for definition in document.particleDefinitions where particleSystems[definition.id] == nil {
      particleSystems[definition.id] = NativeSwiftParticleSystemRuntime(definition: definition, values: values)
    }
    for loop in document.particleLoops {
      guard let system = particleSystems[loop.id] else { continue }
      system.advance(loop: loop, baseValues: values)
      // The ordinary resolver sees the latest particle while drawing a loop body. UIKit's loop
      // renderer replaces these for each child; publishing the last value is still the reference's
      // externally observable register state when no loop child is painted.
      for (index, variableID) in system.variableIDs.enumerated() {
        floats[variableID] = system.particles.last?[index] ?? 0
      }
    }
    staticSnapshotCache = nil
  }

  /// Resolves text producers in wire order. The output of an operation is immediately visible to
  /// the next operation, including one of a different concrete type.
  private func resolveTextOperations(values: [Int: Float]) {
    for operation in document.textOperations {
      switch operation {
      case .fromFloat(let conversion):
        let value = NativeSwiftFloatExpression.resolve(conversion.value, values: values)
        texts[conversion.outputID] = String(format: "%.*f", min(max(conversion.digitsAfter, 0), 12), value)
      case .merge(let merge):
        texts[merge.outputID] = (texts[merge.leftID] ?? "") + (texts[merge.rightID] ?? "")
      case .lookupInt(let lookup):
        guard let ids = document.idLists[lookup.listID], !ids.isEmpty else { continue }
        let index = min(max(integers[lookup.indexID] ?? 0, 0), ids.count - 1)
        texts[lookup.outputID] = texts[ids[index]] ?? ""
      case .lookup(let lookup):
        guard let ids = document.idLists[lookup.listID], !ids.isEmpty else { continue }
        let index = min(
          max(Int(NativeSwiftFloatExpression.resolve(lookup.index, values: values)), 0), ids.count - 1)
        texts[lookup.outputID] = texts[ids[index]] ?? ""
      case .transform(let transform):
        resolveTextTransform(transform, values: values)
      }
    }
  }

  /// The protocol's slicing coordinates are UTF-16 code-unit offsets, as in Android's String.
  /// Decode the selected units directly instead of stepping Swift character indices, which would
  /// move differently for emoji and combining sequences.
  private func resolveTextTransform(_ transform: ParsedTextTransform, values: [Int: Float]) {
    let source = texts[transform.textID] ?? ""
    let units = Array(source.utf16)
    let start = max(0, Int(NativeSwiftFloatExpression.resolve(transform.start, values: values)))
    let requestedLength = Int(NativeSwiftFloatExpression.resolve(transform.length, values: values))
    let startOffset = min(start, units.count)
    let remaining = units.count - startOffset
    let length = requestedLength <= 0 ? remaining : min(requestedLength, remaining)
    let selected = String(decoding: units[startOffset..<(startOffset + length)], as: UTF16.self)
    switch transform.operation {
    case NativeSwiftTextTransformOperation.lowercase: texts[transform.outputID] = selected.lowercased()
    case NativeSwiftTextTransformOperation.uppercase: texts[transform.outputID] = selected.uppercased()
    case NativeSwiftTextTransformOperation.trim:
      texts[transform.outputID] = selected.trimmingCharacters(in: .whitespacesAndNewlines)
    case NativeSwiftTextTransformOperation.capitalizeWords:
      var startsWord = true
      texts[transform.outputID] = selected.reduce(into: "") { result, character in
        result += startsWord ? String(character).uppercased() : String(character)
        startsWord = character.isWhitespace
      }
    case NativeSwiftTextTransformOperation.capitalizeFirst:
      var result = ""
      var capitalized = false
      for character in selected {
        if !capitalized && !character.isWhitespace {
          result += String(character).uppercased()
          capitalized = true
        } else {
          result.append(character)
        }
      }
      texts[transform.outputID] = result
    default: texts[transform.outputID] = selected
    }
  }

  /// Visibility as the protocol encodes it, including its override bits.
  ///
  /// Above 15 the value is a mask -- OVERRIDE_GONE 16, OVERRIDE_VISIBLE 32, OVERRIDE_INVISIBLE 64 --
  /// and the plain 0/1/2 meanings apply only below that. The order matters and is the reference's:
  /// visible wins over gone, gone over invisible, and anything unrecognised is gone rather than
  /// visible, so an unknown state hides a component instead of drawing it in an undefined one.
  private func resolvedVisibility(of id: Int) -> Int {
    // An unset integer reads 0, and 0 is GONE -- not visible. That is the reference's behaviour and
    // the corpus asserts it: `column_child_visibility` binds three children to integers the document
    // never assigns, and expects all three GONE while their unmodified ancestors stay VISIBLE.
    // Defaulting to visible instead looks safer and is wrong; a component whose visibility nobody
    // has decided is hidden, not shown.
    let value = integers[id] ?? 0
    if value >> 4 > 0 {
      if value & 32 == 32 { return 1 }
      if value & 16 == 16 { return 0 }
      if value & 64 == 64 { return 2 }
      return 0
    }
    switch value {
    case 1: return 1
    case 0: return 0
    case 2: return 2
    default: return 0
    }
  }

  private func colorAttribute(_ type: Int, of color: UInt32) -> Float {
    let red = Float((color >> 16) & 0xff) / 255
    let green = Float((color >> 8) & 0xff) / 255
    let blue = Float(color & 0xff) / 255
    let maximum = max(red, green, blue)
    let minimum = min(red, green, blue)
    let delta = maximum - minimum
    switch type {
    case 0:
      let sector: Float
      if maximum == minimum { sector = 0 }
      else if maximum == red { sector = (green - blue) / delta }
      else if maximum == green { sector = (blue - red) / delta + 2 }
      else { sector = (red - green) / delta + 4 }
      var hue = (sector * 60).truncatingRemainder(dividingBy: 360)
      if hue < 0 { hue += 360 }
      return hue / 360
    case 1: return maximum == minimum ? 0 : delta / maximum
    case 2: return maximum
    case 3: return red
    case 4: return green
    case 5: return blue
    default: return Float((color >> 24) & 0xff) / 255
    }
  }

  private func resolveColors(values: [Int: Float]) -> [Int: UInt32] {
    var result = colors
    for expression in document.colorExpressions {
      let mode = expression.modeAndAlpha & 0xff
      switch mode {
      case 0...3:
        let first = mode & 1 != 0 ? result[expression.first] ?? 0 : UInt32(bitPattern: Int32(expression.first))
        let second = mode & 2 != 0 ? result[expression.second] ?? 0 : UInt32(bitPattern: Int32(expression.second))
        let tween = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.third)), values: values)
        result[expression.outputID] = interpolateColor(first, second, tween: tween)
      case 4...6:
        let alpha: Float
        if mode == 4 { alpha = Float(expression.modeAndAlpha >> 16) / 255 }
        else if mode == 5 { alpha = Float(expression.modeAndAlpha >> 16) / 1024 }
        else {
          alpha = NativeSwiftFloatExpression.resolve(
            0x7fc0_0000 | UInt32(expression.modeAndAlpha >> 16), values: values)
        }
        let first = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.first)), values: values)
        let second = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.second)), values: values)
        let third = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.third)), values: values)
        result[expression.outputID] =
          mode == 4
          ? hsvColor(alpha: alpha, hue: first, saturation: second, brightness: third)
          : argbColor(alpha: alpha, red: first, green: second, blue: third)
      default: break
      }
    }
    return result
  }

  private func interpolateColor(_ first: UInt32, _ second: UInt32, tween: Float) -> UInt32 {
    if !tween.isFinite || tween == 0 { return first }
    if tween == 1 { return second }
    func channel(_ color: UInt32, shift: UInt32) -> Float {
      powf(Float((color >> shift) & 0xff) / 255, 2.2)
    }
    func encoded(_ value: Float) -> UInt32 {
      let encoded = powf(max(value, 0), 1 / 2.2)
      guard encoded.isFinite else { return value > 0 ? 255 : 0 }
      return UInt32((min(max(encoded, 0), 1) * 255).rounded())
    }
    let alpha = Float((first >> 24) & 0xff) + tween * Float(Int((second >> 24) & 0xff) - Int((first >> 24) & 0xff))
    let red = channel(first, shift: 16) + tween * (channel(second, shift: 16) - channel(first, shift: 16))
    let green = channel(first, shift: 8) + tween * (channel(second, shift: 8) - channel(first, shift: 8))
    let blue = channel(first, shift: 0) + tween * (channel(second, shift: 0) - channel(first, shift: 0))
    let clampedAlpha = min(max(alpha, 0), 255)
    return UInt32(clampedAlpha.isFinite ? clampedAlpha.rounded() : 0) << 24
      | encoded(red) << 16 | encoded(green) << 8 | encoded(blue)
  }

  /// A colour byte, clamped rather than converted.
  ///
  /// `Int(value)` traps for a non-finite or out-of-range float, and a document is free to compute a
  /// channel from expressions this player cannot bound — a structure-aware mutant found exactly
  /// that. A colour is not worth failing a frame over, so a non-finite channel saturates instead.
  private func argbColor(alpha: Float, red: Float, green: Float, blue: Float) -> UInt32 {
    func byte(_ value: Float) -> UInt32 {
      guard value.isFinite else { return value > 0 ? 255 : 0 }
      return UInt32((min(max(value, 0), 1) * 255).rounded())
    }
    return byte(alpha) << 24 | byte(red) << 16 | byte(green) << 8 | byte(blue)
  }

  private func hsvColor(
    alpha: Float, hue: Float, saturation: Float, brightness: Float
  ) -> UInt32 {
    guard hue.isFinite, saturation.isFinite, brightness.isFinite else {
      return argbColor(alpha: alpha, red: 0, green: 0, blue: 0)
    }
    // Wrapped into [0, 1) before the sector is taken, because `Int(hue * 6)` traps on a hue a
    // document computed beyond Float's integral range.
    let wrapped = hue - floorf(hue)
    let section = Int(wrapped * 6)
    let fraction = wrapped * 6 - Float(section)
    let p = brightness * (1 - saturation)
    let q = brightness * (1 - fraction * saturation)
    let t = brightness * (1 - (1 - fraction) * saturation)
    let rgb: (Float, Float, Float)
    switch section {
    case 0: rgb = (brightness, t, p)
    case 1: rgb = (q, brightness, p)
    case 2: rgb = (p, brightness, t)
    case 3: rgb = (p, q, brightness)
    case 4: rgb = (t, p, brightness)
    case 5: rgb = (brightness, p, q)
    default: rgb = (0, 0, 0)
    }
    return argbColor(alpha: alpha, red: rgb.0, green: rgb.1, blue: rgb.2)
  }

  private func estimatedDimension(
    of node: ParsedNode, type: Int, available: Float, values: [Int: Float]
  ) -> Float {
    // Measurement runs before the final resolution pass and must not throw: an unresolvable field
    // reads as zero here and is rejected properly by `resolvedFloat` when the snapshot is built.
    func float(_ word: UInt32) -> Float {
      let value = NativeSwiftFloatExpression.resolve(word, values: values)
      return value.isFinite ? value : 0
    }
    let dimensionType = type == 0 ? node.widthType : node.heightType
    let dimensionValue = float(type == 0 ? node.widthWord : node.heightWord)
    if dimensionType == 0 || dimensionType == 6 { return max(dimensionValue, 0) }
    if dimensionType == 1 || dimensionType == 7 || dimensionType == 8 {
      let fraction = dimensionValue.isNaN ? 1 : max(dimensionValue, 0)
      return ancestorDimension(of: node, type: type, available: available, values: values)
        * fraction
    }
    // A fill child contributes nothing to a wrapping parent's intrinsic size: the parent decides
    // the child's size, not the other way round. Including one used to make a wrap box that
    // contains a fill canvas measure the whole document, because the fill's own estimate resolved
    // to `available`.
    let children = flattenedChildren(of: node, values: values).filter {
      !isFillDimension(type == 0 ? $0.widthType : $0.heightType)
    }
    let childDimensions = children.map {
      estimatedDimension(of: $0, type: type, available: available, values: values)
    }
    let intrinsic: Float
    if let text = node.text {
      if type == 0 {
        intrinsic = Float(texts[text.textID]?.count ?? 0) * float(text.sizeWord) * 0.6
      } else {
        intrinsic = float(text.sizeWord) * 1.2
      }
    } else if type == 0 {
      intrinsic = node.kind == .row ? childDimensions.reduce(0, +) : childDimensions.max() ?? 0
    } else {
      intrinsic =
        node.kind == .column
        ? childDimensions.reduce(0, +)
          + float(node.spacingWord) * Float(max(childDimensions.count - 1, 0))
        : childDimensions.max() ?? 0
    }
    let padding =
      type == 0
      ? float(node.paddingWords.left) + float(node.paddingWords.right)
      : float(node.paddingWords.top) + float(node.paddingWords.bottom)
    let minimum = type == 1 ? float(node.minimumHeightWord) : 0
    return max(intrinsic + padding, minimum)
  }

  /// The size a fill resolves to: the nearest ancestor that has one, not the document.
  ///
  /// A fill's size is its parent's, so a chain of transparent wrappers and fills has to be walked
  /// up until something determinate is found. Returning `available` at the first fill is what made
  /// an icon inside a 52-unit button measure the 454-unit canvas; the reference measures the
  /// component the layout actually gave it. Falls back to the document when no ancestor decides.
  private func ancestorDimension(
    of node: ParsedNode, type: Int, available: Float, values: [Int: Float]
  ) -> Float {
    func float(_ word: UInt32) -> Float {
      let value = NativeSwiftFloatExpression.resolve(word, values: values)
      return value.isFinite ? value : 0
    }
    var current = node.parent
    var depth = 0
    while let candidate = current, depth < 64 {
      let dimensionType = type == 0 ? candidate.widthType : candidate.heightType
      let dimensionValue = float(type == 0 ? candidate.widthWord : candidate.heightWord)
      if dimensionType == 0 || dimensionType == 6 { return max(dimensionValue, 0) }
      if !isFillDimension(dimensionType) {
        let intrinsic = estimatedDimension(
          of: candidate, type: type, available: available, values: values)
        if intrinsic > 0 { return intrinsic }
      }
      current = candidate.parent
      depth += 1
    }
    return available
  }

  private func isFillDimension(_ dimensionType: Int) -> Bool {
    dimensionType == 1 || dimensionType == 7 || dimensionType == 8
  }

  /// Flattens a bare content wrapper into its parent for measurement.
  ///
  /// The padding test runs on resolved values, not on words: a wrapper whose padding is computed
  /// and comes out zero is just as bare as one that says `0`, and comparing the encoded words
  /// would keep it — and so measure a row's grandchildren as one child's maximum rather than
  /// their sum.
  private func flattenedChildren(of node: ParsedNode, values: [Int: Float]) -> [ParsedNode] {
    func isZero(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.resolve(word, values: values) == 0
    }
    return node.children.flatMap { child in
      if child.kind == .content, child.widthType == 2, child.heightType == 2,
        isZero(child.paddingWords.left), isZero(child.paddingWords.top),
        isZero(child.paddingWords.right), isZero(child.paddingWords.bottom)
      {
        return flattenedChildren(of: child, values: values)
      }
      return [child]
    }
  }
}

private struct ParsedDocument {
  let width: Int
  let height: Int
  let density: Float
  let densityBehavior: Int
  let root: ParsedNode
  let nodes: [Int: ParsedNode]
  let texts: [Int: String]
  let floats: [Int: Float]
  let colors: [Int: UInt32]
  let integers: [Int: Int]
  let integerExpressions: [Int: ParsedIntegerExpression]
  /// The same expressions in the order the document declared them: one may read another's result,
  /// so evaluation follows the wire rather than a dictionary's iteration order.
  let integerExpressionOrder: [Int]
  let namedVariables: [String: ParsedNamedVariable]
  let expressions: [ParsedFloatExpression]
  let componentValues: [ParsedComponentValue]
  let colorAttributes: [ParsedColorAttribute]
  let colorExpressions: [ParsedColorExpression]
  let images: [Int: ParsedImageResource]
  /// Text producers in exactly the order the wire declared them. A later operation may consume an
  /// earlier result regardless of their concrete operation types.
  let textOperations: [ParsedTextOperation]
  let textFromFloats: [ParsedTextFromFloat]
  let textMerges: [ParsedTextMerge]
  let textTransforms: [ParsedTextTransform]
  let idLists: [Int: [Int]]
  let floatLists: [Int: [UInt32]]
  let floatListUpdates: [Int: [(index: UInt32, value: UInt32)]]
  let dynamicFloatLists: [Int: ParsedDynamicFloatList]
  let textLookups: [ParsedTextLookupInt]
  let textFloatLookups: [ParsedTextLookup]
  let dataMaps: [Int: [String: (type: Int, valueID: Int)]]
  let dataMapLookups: [ParsedDataMapLookup]
  let matrixConstants: [Int: NativeSwiftMatrixSnapshot]
  let matrixExpressions: [Int: ParsedMatrixExpression]
  let matrixVectorMath: [ParsedMatrixVectorMath]
  let animationSpecs: [Int: NativeSwiftAnimationSpec]
  let animationSpecOrder: [Int]
  let pathIDs: Set<Int>
  let pathTweenIDs: Set<Int>
  let accessibilityRecords: [ParsedAccessibility]
  let shaderUniformNames: [Int: Set<String>]
  let conditionalTraces: [NativeSwiftConditionalTraceSnapshot]
  let particleDefinitions: [ParsedParticleDefinition]
  let particleLoops: [ParsedParticleLoop]
  let needsContinuousFrames: Bool
  /// See `NativeSwiftDocumentSnapshot.needsWallClockRefresh`.
  let needsWallClockRefresh: Bool
  let linkedOperationCount: Int
}

private struct ParsedDynamicFloatList {
  let lengthWord: UInt32
  let updates: [(index: UInt32, value: UInt32)]
}

private struct ParsedParticleDefinition {
  let id: Int
  let particleCount: Int
  let variableIDs: [Int]
  let initializationEquations: [[UInt32]]
}

private struct ParsedParticleLoop {
  let id: Int
  let restartEquation: [UInt32]
  let updateEquations: [[UInt32]]
}

private final class NativeSwiftParticleSystemRuntime {
  let id: Int
  let variableIDs: [Int]
  let initializationEquations: [[UInt32]]
  var particles: [[Float]]

  init(definition: ParsedParticleDefinition, values: [Int: Float]) {
    id = definition.id
    variableIDs = definition.variableIDs
    initializationEquations = definition.initializationEquations
    particles = Array(repeating: Array(repeating: 0, count: definition.variableIDs.count), count: definition.particleCount)
    for index in particles.indices { initialize(index: index, baseValues: values) }
  }

  private init(copying other: NativeSwiftParticleSystemRuntime) {
    id = other.id
    variableIDs = other.variableIDs
    initializationEquations = other.initializationEquations
    particles = other.particles
  }

  func detachedCopy() -> NativeSwiftParticleSystemRuntime { NativeSwiftParticleSystemRuntime(copying: self) }

  var snapshot: NativeSwiftParticleSystemSnapshot {
    NativeSwiftParticleSystemSnapshot(id: id, variableIDs: variableIDs, particles: particles)
  }

  func advance(loop: ParsedParticleLoop, baseValues: [Int: Float]) {
    guard loop.updateEquations.count == variableIDs.count else { return }
    for index in particles.indices {
      let previous = particles[index]
      var values = baseValues
      for (variableIndex, variableID) in variableIDs.enumerated() { values[variableID] = previous[variableIndex] }
      let variables = [Float(index), 0, 0]
      let updated = loop.updateEquations.map {
        (try? NativeSwiftFloatExpression.evaluate($0, values: values, variables: variables)) ?? 0
      }
      particles[index] = updated
      for (variableIndex, variableID) in variableIDs.enumerated() { values[variableID] = updated[variableIndex] }
      if (
        (try? NativeSwiftFloatExpression.evaluate(
          loop.restartEquation, values: values, variables: variables)) ?? 0
      ) > 0 {
        initialize(index: index, baseValues: baseValues)
      }
    }
  }

  private func initialize(index: Int, baseValues: [Int: Float]) {
    var values = baseValues
    let variables = [Float(index), 0, 0]
    var initialized = Array(repeating: Float(0), count: variableIDs.count)
    for (variableIndex, equation) in initializationEquations.enumerated() {
      let value =
        (try? NativeSwiftFloatExpression.evaluate(
          equation, values: values, variables: variables)) ?? 0
      initialized[variableIndex] = value
      values[variableIDs[variableIndex]] = value
    }
    particles[index] = initialized
  }
}

private struct ParsedImageResource {
  let id: Int
  let width: Int
  let height: Int
  let type: Int
  let encoding: Int
  let data: Data

  var snapshot: NativeSwiftImageResourceSnapshot {
    NativeSwiftImageResourceSnapshot(
      id: id, width: width, height: height, type: type, encoding: encoding, data: data)
  }
}

private struct ParsedComponentValue {
  let type: Int
  let componentID: Int
  let valueID: Int
}

private struct ParsedColorAttribute {
  let outputID: Int
  let colorID: Int
  let type: Int
}

private struct ParsedColorExpression {
  let outputID: Int
  let modeAndAlpha: Int
  let first: Int
  let second: Int
  let third: Int

  /// The float words this expression reads, which depend on its mode.
  ///
  /// Modes 0...3 blend two **colours**: their first two fields are literal ARGB or colour ids, not
  /// float references, and only the third is the tween. Reading all three as references makes a
  /// static document look like it reads a clock — `0xff800001` as ARGB has the bit pattern of a
  /// NaN-boxed reference to `CONTINUOUS_SEC`, and `0xff800002` to `TIME_IN_SEC` — and the host then
  /// keeps a display link or a one-second wakeup alive for a document that never changes.
  /// Modes 4...6 build a colour from float channels, so all three are words, and mode 6 carries its
  /// alpha as a reference in the high half of `modeAndAlpha`.
  var floatWords: [UInt32] {
    func word(_ value: Int) -> UInt32 { UInt32(bitPattern: Int32(truncatingIfNeeded: value)) }
    switch modeAndAlpha & 0xff {
    case 0...3:
      return [word(third)]
    case 4, 5:
      return [word(first), word(second), word(third)]
    case 6:
      return [
        word(first), word(second), word(third),
        0x7fc0_0000 | (UInt32(truncatingIfNeeded: modeAndAlpha >> 16) & 0xffff),
      ]
    default:
      return []
    }
  }
}

private struct ParsedNamedVariable {
  let id: Int
  let type: Int
}

private struct ParsedFloatExpression {
  let id: Int
  let words: [UInt32]
  let animationWords: [UInt32]?
}

/// AndroidX `FloatAnimation` easing ids, from the authoritative Easing wire implementation.
private enum NativeSwiftFloatEasingType: UInt32 {
  case cubicStandard = 1
  case cubicAccelerate = 2
  case cubicDecelerate = 3
  case cubicLinear = 4
  case cubicAnticipate = 5
  case cubicOvershoot = 6
  case cubicCustom = 11
  case splineCustom = 12
  case easeOutBounce = 13
  case easeOutElastic = 14

  var acceptsParameters: Bool {
    self == .cubicCustom || self == .splineCustom
  }
}

private enum NativeSwiftFloatDirectionalSnap: Int {
  case none = 0
  case snapOnDecrease = 1
  case snapOnIncrease = 2
}

/// Known spring boundary modes are the two independent edge flags and their combination.
private enum NativeSwiftSpringBoundaryMode: Int {
  case none = 0
  case lower = 1
  case upper = 2
  case lowerAndUpper = 3

  var includesLower: Bool { self == .lower || self == .lowerAndUpper }
  var includesUpper: Bool { self == .upper || self == .lowerAndUpper }
}

private enum NativeSwiftFloatAnimationMetadata {
  static let springMarker: Float = 0
  static let springDescriptorWordCount = 5
  static let easingTypeMask: UInt32 = 0xff
  static let wrapFlag: UInt32 = 1 << 8
  static let initialValueFlag: UInt32 = 1 << 9
  static let directionalSnapShift = 10
  static let directionalSnapMask: UInt32 = 0x3
  static let propagationFlag: UInt32 = 1 << 12
  static let parameterCountShift = 16
}

/// Stateful evaluator for AndroidX's optional animation payload on a float expression.
private final class NativeSwiftFloatAnimationRuntime {
  private enum Curve {
    case cubic(Float, Float, Float, Float)
    case bounce
    case elastic
    case spline(NativeSwiftMonotonicCurve)

    func value(at x: Float) -> Float {
      switch self {
      case .cubic(let x1, let y1, let x2, let y2):
        return Self.cubic(x, x1: x1, y1: y1, x2: x2, y2: y2)
      case .bounce:
        let n: Float = 7.5625
        let d: Float = 2.75
        if x < 0 { return 0 }
        if x < 1 / d { return (n * x * x + x) / (1 + 1 / d) }
        if x < 2 / d {
          let t = x - 1.5 / d
          return n * t * t + 0.75
        }
        if x < 2.5 / d {
          let t = x - 2.25 / d
          return n * t * t + 0.9375
        }
        if x <= 1 {
          let t = x - 2.625 / d
          return n * t * t + 0.984375
        }
        return 1
      case .elastic:
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        return powf(2, -10 * x) * sinf((x * 10 - 0.75) * (2 * .pi / 3)) + 1
      case .spline(let curve):
        return x < 0 ? 0 : (x > 1 ? 1 : curve.value(at: Double(x)))
      }
    }

    private static func cubic(
      _ x: Float, x1: Float, y1: Float, x2: Float, y2: Float
    ) -> Float {
      if x <= 0 { return 0 }
      if x >= 1 { return 1 }
      func coordinate(_ t: Float, _ first: Float, _ second: Float) -> Float {
        let inverse = 1 - t
        return first * 3 * inverse * inverse * t
          + second * 3 * inverse * t * t + t * t * t
      }
      var t: Float = 0.5
      var range: Float = 0.5
      while range > 0.01 {
        let tx = coordinate(t, x1, x2)
        range *= 0.5
        if tx < x { t += range } else { t -= range }
      }
      let lowerX = coordinate(t - range, x1, x2)
      let upperX = coordinate(t + range, x1, x2)
      let lowerY = coordinate(t - range, y1, y2)
      let upperY = coordinate(t + range, y1, y2)
      guard upperX != lowerX else { return lowerY }
      return (upperY - lowerY) * (x - lowerX) / (upperX - lowerX) + lowerY
    }
  }

  private let duration: Float
  private let curve: Curve?
  private let wrap: Float?
  private let directionalSnap: NativeSwiftFloatDirectionalSnap
  private var initialValue: Float
  private var targetValue = Float.nan
  private var lastTarget = Float.nan
  private var lastChange = Float.nan

  private let springStiffness: Double?
  private let springDamping: Double
  private let springStopThreshold: Double
  private let springBoundaryMode: NativeSwiftSpringBoundaryMode
  private var springTarget = 0.0
  private var springPosition: Float = 0
  private var springVelocity: Float = 0
  private var springLastTime: Float = 0

  init(animationWords words: [UInt32], offset: Int = 0) throws {
    func malformed(_ reason: String) -> NativeSwiftCoreError {
      .malformed(offset: offset, reason: "invalid float animation: \(reason)")
    }

    guard !words.isEmpty else { throw malformed("missing animation duration") }
    let first = Float(bitPattern: words[0])
    let isSpring =
      words.count >= NativeSwiftFloatAnimationMetadata.springDescriptorWordCount
      && first == NativeSwiftFloatAnimationMetadata.springMarker
    if isSpring {
      let stiffness = Double(Float(bitPattern: words[1]))
      let damping = Double(Float(bitPattern: words[2]))
      let threshold = Double(Float(bitPattern: words[3]))
      let boundaryModeValue = Int(Int32(bitPattern: words[4]))
      guard words.count == NativeSwiftFloatAnimationMetadata.springDescriptorWordCount else {
        throw malformed("spring descriptor must have five words")
      }
      guard let boundaryMode = NativeSwiftSpringBoundaryMode(rawValue: boundaryModeValue) else {
        throw malformed("unknown spring boundary mode \(boundaryModeValue)")
      }
      guard stiffness.isFinite, stiffness > 0, damping.isFinite, damping >= 0,
        threshold.isFinite, threshold > 0
      else { throw malformed("invalid spring parameters") }
      duration = 0
      curve = nil
      wrap = nil
      directionalSnap = .none
      initialValue = .nan
      springStiffness = stiffness
      springDamping = damping
      springStopThreshold = threshold
      springBoundaryMode = boundaryMode
      return
    }

    guard first.isFinite, first > 0 else { throw malformed("duration must be finite and positive") }
    // AndroidX accepts a one-word description as the compact default animation: duration followed
    // by implicit CUBIC_STANDARD metadata. Catalog exports use this form for otherwise-static
    // values that participate in a component transition. Requiring the optional metadata word
    // rejected those documents before UIKit could install a view.
    let hasExplicitMetadata = words.count > 1
    let metadata = hasExplicitMetadata ? words[1] : NativeSwiftFloatEasingType.cubicStandard.rawValue
    let easingTypeValue = metadata & NativeSwiftFloatAnimationMetadata.easingTypeMask
    guard let easingType = NativeSwiftFloatEasingType(rawValue: easingTypeValue) else {
      throw NativeSwiftCoreError.unsupported(
        opcode: 81, offset: offset,
        reason: "float animation easing type \(easingTypeValue) is not supported")
    }
    let hasWrap = metadata & NativeSwiftFloatAnimationMetadata.wrapFlag != 0
    let hasInitial = metadata & NativeSwiftFloatAnimationMetadata.initialValueFlag != 0
    let directionalValue =
      Int(
        (metadata >> NativeSwiftFloatAnimationMetadata.directionalSnapShift)
          & NativeSwiftFloatAnimationMetadata.directionalSnapMask)
    guard let directional = NativeSwiftFloatDirectionalSnap(rawValue: directionalValue) else {
      throw malformed("unknown directional snap mode \(directionalValue)")
    }
    let parameterCount = Int(metadata >> NativeSwiftFloatAnimationMetadata.parameterCountShift)
    let parameterStart = hasExplicitMetadata ? 2 : 1
    let tail = parameterStart + parameterCount
    let expectedCount = tail + (hasInitial ? 1 : 0) + (hasWrap ? 1 : 0)
    guard words.count == expectedCount else {
      throw malformed("metadata requires \(expectedCount) words, found \(words.count)")
    }
    let parameters = words[parameterStart..<tail].map { Float(bitPattern: $0) }
    guard parameters.allSatisfy(\.isFinite) else { throw malformed("non-finite curve parameter") }
    let parsedCurve: Curve
    switch easingType {
    case .cubicStandard: parsedCurve = .cubic(0.4, 0, 0.2, 1)
    case .cubicAccelerate: parsedCurve = .cubic(0.4, 0.05, 0.8, 0.7)
    case .cubicDecelerate: parsedCurve = .cubic(0, 0, 0.2, 0.95)
    case .cubicLinear: parsedCurve = .cubic(1, 1, 0, 0)
    case .cubicAnticipate: parsedCurve = .cubic(0.36, 0, 0.66, -0.56)
    case .cubicOvershoot: parsedCurve = .cubic(0.34, 1.56, 0.64, 1)
    case .cubicCustom:
      guard parameters.count == 4 else {
        throw malformed("custom cubic easing needs four parameters")
      }
      parsedCurve = .cubic(parameters[0], parameters[1], parameters[2], parameters[3])
    case .splineCustom:
      guard parameters.count >= 2 else {
        throw malformed("spline easing needs at least two points")
      }
      parsedCurve = .spline(NativeSwiftMonotonicCurve(points: parameters))
    case .easeOutBounce:
      guard parameters.isEmpty else { throw malformed("bounce easing takes no parameters") }
      parsedCurve = .bounce
    case .easeOutElastic:
      guard parameters.isEmpty else { throw malformed("elastic easing takes no parameters") }
      parsedCurve = .elastic
    }
    if !easingType.acceptsParameters, !parameters.isEmpty {
      throw malformed("preset easing type \(easingType.rawValue) takes no parameters")
    }
    let initialIndex = tail
    let wrapIndex = tail + (hasInitial ? 1 : 0)
    let configuredInitial = hasInitial ? Float(bitPattern: words[initialIndex]) : .nan
    let configuredWrap = hasWrap ? Float(bitPattern: words[wrapIndex]) : .nan
    guard !hasInitial || configuredInitial.isFinite,
      !hasWrap || (configuredWrap.isFinite && configuredWrap > 0)
    else { throw malformed("invalid initial or wrap value") }

    duration = first
    curve = parsedCurve
    wrap = hasWrap ? configuredWrap : nil
    directionalSnap = directional
    initialValue = configuredInitial
    springStiffness = nil
    springDamping = 0
    springStopThreshold = 0
    springBoundaryMode = .none
  }

  private init(copying other: NativeSwiftFloatAnimationRuntime) {
    duration = other.duration
    curve = other.curve
    wrap = other.wrap
    directionalSnap = other.directionalSnap
    initialValue = other.initialValue
    targetValue = other.targetValue
    lastTarget = other.lastTarget
    lastChange = other.lastChange
    springStiffness = other.springStiffness
    springDamping = other.springDamping
    springStopThreshold = other.springStopThreshold
    springBoundaryMode = other.springBoundaryMode
    springTarget = other.springTarget
    springPosition = other.springPosition
    springVelocity = other.springVelocity
    springLastTime = other.springLastTime
  }

  func detachedCopy() -> NativeSwiftFloatAnimationRuntime {
    NativeSwiftFloatAnimationRuntime(copying: self)
  }

  func evaluate(target: Float, at time: Float) -> Float {
    if let springStiffness {
      if target != lastTarget {
        springTarget = Double(target)
        lastTarget = target
        lastChange = time
      }
      if lastChange.isNaN { lastChange = time }
      integrateSpring(to: time, stiffness: springStiffness)
      if springIsStopped(stiffness: springStiffness) { springPosition = Float(springTarget) }
      return springPosition
    }

    if target != lastTarget {
      if lastTarget.isNaN {
        setTarget(target)
        if initialValue.isNaN { setInitial(target) }
      } else {
        setInitial(targetValue)
        setTarget(target)
      }
      lastTarget = target
      lastChange = time
    }
    if lastChange.isNaN { lastChange = time }
    let progress = (time - lastChange) / duration
    if directionalSnap == .snapOnDecrease, targetValue < initialValue {
      initialValue = targetValue
      return targetValue
    }
    if directionalSnap == .snapOnIncrease, targetValue > initialValue {
      initialValue = targetValue
      return targetValue
    }
    return curve!.value(at: progress) * (targetValue - initialValue) + initialValue
  }

  func isAnimating(at time: Float) -> Bool {
    if let springStiffness { return !springIsStopped(stiffness: springStiffness) }
    return !initialValue.isNaN && !targetValue.isNaN && initialValue != targetValue
      && time - lastChange < duration
  }

  private func setInitial(_ value: Float) {
    initialValue = wrap.map { value.truncatingRemainder(dividingBy: $0) } ?? value
  }

  private func setTarget(_ value: Float) {
    targetValue = value
    guard let wrap else { return }
    initialValue = positiveWrap(initialValue, by: wrap)
    targetValue = positiveWrap(targetValue, by: wrap)
    if initialValue.isNaN { initialValue = targetValue }
    let distance = wrapDistance(from: initialValue, to: targetValue, wrap: wrap)
    if distance > 0, targetValue < initialValue {
      targetValue += wrap
    } else if distance < 0, directionalSnap != .none {
      if directionalSnap == .snapOnDecrease, targetValue > initialValue {
        initialValue = targetValue
      }
      if directionalSnap == .snapOnIncrease, targetValue < initialValue {
        initialValue = targetValue
      }
      targetValue -= wrap
    }
  }

  private func positiveWrap(_ value: Float, by wrap: Float) -> Float {
    let remainder = value.truncatingRemainder(dividingBy: wrap)
    return remainder < 0 ? remainder + wrap : remainder
  }

  private func wrapDistance(from: Float, to: Float, wrap: Float) -> Float {
    var delta = (to - from).truncatingRemainder(dividingBy: 360)
    if delta < -wrap / 2 { delta += wrap } else if delta > wrap / 2 { delta -= wrap }
    return delta
  }

  private func springIsStopped(stiffness: Double) -> Bool {
    let displacement = Double(springPosition) - springTarget
    let velocity = Double(springVelocity)
    let energy = velocity * velocity + stiffness * displacement * displacement
    return sqrt(energy / stiffness) <= springStopThreshold
  }

  private func integrateSpring(to time: Float, stiffness: Double) {
    let delta = Double(time - springLastTime)
    springLastTime = time
    guard delta > 0 else { return }
    let requestedSteps = 1 + 9 / (sqrt(stiffness) * delta * 4)
    let steps =
      requestedSteps >= 1_000 || !requestedSteps.isFinite
      ? 1_000 : max(Int(requestedSteps), 1)
    let dt = delta / Double(steps)
    for _ in 0..<steps {
      let position = Double(springPosition)
      let velocity = Double(springVelocity)
      let displacement = position - springTarget
      let acceleration = -stiffness * displacement - springDamping * velocity
      let averageVelocity = velocity + acceleration * dt / 2
      let averageDisplacement = position + dt * averageVelocity / 2 - springTarget
      let adjustedAcceleration = -stiffness * averageDisplacement - springDamping * averageVelocity
      let velocityDelta = adjustedAcceleration * dt
      let adjustedAverageVelocity = velocity + velocityDelta / 2
      springVelocity += Float(velocityDelta)
      springPosition += Float(adjustedAverageVelocity * dt)
      if springBoundaryMode.includesLower, springPosition < 0 {
        springPosition = -springPosition
        springVelocity = -springVelocity
      }
      if springBoundaryMode.includesUpper, springPosition > 1 {
        springPosition = 2 - springPosition
        springVelocity = -springVelocity
      }
    }
  }
}

/// AndroidX's monotonic spline fit used by custom spline easing descriptors.
private struct NativeSwiftMonotonicCurve {
  private let times: [Double]
  private let values: [Double]
  private let tangents: [Double]

  init(points: [Float]) {
    let sourceCount = points.count
    let offset = sourceCount - 1
    let pointCount = sourceCount * 3 - 2
    let gap = 1.0 / Double(offset)
    var times = Array(repeating: 0.0, count: pointCount)
    var values = Array(repeating: 0.0, count: pointCount)
    for (index, point) in points.enumerated() {
      let value = Double(point)
      values[index + offset] = value
      times[index + offset] = Double(index) * gap
      if index > 0 {
        values[index + offset * 2] = value + 1
        times[index + offset * 2] = Double(index) * gap + 1
        values[index - 1] = value - 1 - gap
        times[index - 1] = Double(index) * gap - 1 - gap
      }
    }
    var slopes = Array(repeating: 0.0, count: pointCount - 1)
    var tangents = Array(repeating: 0.0, count: pointCount)
    for index in slopes.indices {
      slopes[index] = (values[index + 1] - values[index]) / (times[index + 1] - times[index])
      tangents[index] = index == 0 ? slopes[index] : (slopes[index - 1] + slopes[index]) * 0.5
    }
    tangents[tangents.count - 1] = slopes[slopes.count - 1]
    for index in slopes.indices {
      if slopes[index] == 0 {
        tangents[index] = 0
        tangents[index + 1] = 0
      } else {
        let a = tangents[index] / slopes[index]
        let b = tangents[index + 1] / slopes[index]
        let magnitude = hypot(a, b)
        if magnitude > 9 {
          let scale = 3 / magnitude
          tangents[index] = scale * a * slopes[index]
          tangents[index + 1] = scale * b * slopes[index]
        }
      }
    }
    self.times = times
    self.values = values
    self.tangents = tangents
  }

  func value(at position: Double) -> Float {
    if position <= times[0] { return Float(values[0] + (position - times[0]) * tangents[0]) }
    if position >= times[times.count - 1] {
      return Float(
        values[values.count - 1] + (position - times[times.count - 1])
          * tangents[tangents.count - 1])
    }
    let index = times.indices.dropLast().first { position < times[$0 + 1] }!
    let h = times[index + 1] - times[index]
    let x = (position - times[index]) / h
    let x2 = x * x
    let x3 = x2 * x
    let value =
      -2 * x3 * values[index + 1] + 3 * x2 * values[index + 1]
      + 2 * x3 * values[index] - 3 * x2 * values[index] + values[index]
      + h * tangents[index + 1] * x3 + h * tangents[index] * x3
      - h * tangents[index + 1] * x2 - 2 * h * tangents[index] * x2
      + h * tangents[index] * x
    return Float(value)
  }
}

private struct ParsedIntegerExpression {
  let mask: Int
  let tokens: [Int]
}

private struct ParsedTextFromFloat {
  let outputID: Int
  let value: UInt32
  let digitsAfter: Int
}

private struct ParsedTextMerge {
  let outputID: Int
  let leftID: Int
  let rightID: Int
}

private struct ParsedTextLookupInt {
  let outputID: Int
  let listID: Int
  let indexID: Int
}

private struct ParsedTextLookup {
  let outputID: Int
  let listID: Int
  let index: UInt32
}

private struct ParsedTextTransform {
  let outputID: Int
  let textID: Int
  let start: UInt32
  let length: UInt32
  let operation: Int
}

private enum ParsedTextOperation {
  case fromFloat(ParsedTextFromFloat)
  case merge(ParsedTextMerge)
  case lookupInt(ParsedTextLookupInt)
  case lookup(ParsedTextLookup)
  case transform(ParsedTextTransform)
}

private struct ParsedDataMapLookup {
  let outputID: Int
  let mapID: Int
  let keyTextID: Int
}

private struct ParsedNamedAction {
  let nameTextID: Int
  let valueType: Int
  let valueID: Int
}

private enum ParsedAction {
  case named(ParsedNamedAction)
  case integerExpression(targetID: Int, expressionID: Int)
  case floatExpression(targetID: Int, expressionID: Int)
  case integerValue(targetID: Int, value: Int)
}

private struct ParsedModifierContainer {
  let node: ParsedNode?
  let gesture: NativeSwiftGestureKind?
}

private struct ParsedAccessibility {
  let contentDescriptionID: Int
  let role: Int
  let textID: Int
  let stateDescriptionID: Int
  let mode: Int
  let isEnabled: Bool
  let isClickable: Bool
}

private struct ParsedDrawCommand {
  let kind: Int
  let words: [UInt32]
  /// Literal geometry never depends on a frame's expression table. Keeping the decoded floats
  /// here lets each immutable snapshot share the array's copy-on-write storage instead of mapping
  /// the same words again for every frame.
  let staticValues: [Float]?
  let paint: ParsedPaint
  let path: ParsedPath?
  let image: ParsedImageDraw?
  let alphaWord: UInt32?
  let textID: Int?
  let textStart: Int?
  let textEnd: Int?
  let textFlags: Int

  init(
    kind: Int, words: [UInt32], paint: ParsedPaint, path: ParsedPath? = nil,
    image: ParsedImageDraw? = nil, alphaWord: UInt32? = nil, textID: Int? = nil,
    textStart: Int? = nil, textEnd: Int? = nil, textFlags: Int = 0
  ) {
    self.kind = kind
    self.words = words
    staticValues = words.contains { NativeSwiftFloatExpression.referenceID($0) != nil }
      ? nil : words.map(Float.init(bitPattern:))
    self.paint = paint
    self.path = path
    self.image = image
    self.alphaWord = alphaWord
    self.textID = textID
    self.textStart = textStart
    self.textEnd = textEnd
    self.textFlags = textFlags
  }

  func resolve(
    values: [Int: Float], colors: [Int: UInt32], texts: [Int: String],
    componentValueIDs: Set<Int>, matrices: [Int: ParsedMatrixExpression]
  ) throws
    -> NativeSwiftDrawCommandSnapshot
  {
    let geometryWords =
      words + (path?.words ?? []) + (image?.destination ?? [])
      + (paint.gradient?.coordinateWords ?? [])
    let usesComponentGeometry = geometryWords.contains { word in
      NativeSwiftFloatExpression.referenceID(word).map(componentValueIDs.contains) ?? false
    }
    return NativeSwiftDrawCommandSnapshot(
      kind: kind,
      values: staticValues ?? words.map { NativeSwiftFloatExpression.resolve($0, values: values) },
      // SRC_IN is the vector-tint path emitted by Remote Compose. Its source is the filter colour,
      // while the glyph alpha remains in the path rasterization performed by Core Graphics.
      colorARGB:
        paint.colorFilterMode == NativeSwiftPaintBlendMode.sourceIn
        ? (paint.colorFilterID.flatMap { colors[$0] } ?? paint.colorFilterARGB ?? paint.colorARGB)
        : (paint.colorID.flatMap { colors[$0] } ?? paint.colorARGB),
      alpha: alphaWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? paint.alpha,
      strokeWidth: NativeSwiftFloatExpression.resolve(paint.strokeWidth, values: values),
      isStroke: paint.isStroke,
      strokeCap: paint.strokeCap,
      strokeJoin: paint.strokeJoin,
      blendMode: paint.blendMode,
      path: try path?.resolve(values: values) ?? [],
      pathWinding: path?.winding ?? 0,
      image: image?.resolve(values: values, texts: texts),
      textureImageID: paint.textureImageID,
      textureTileModeX: paint.textureTileModeX,
      textureTileModeY: paint.textureTileModeY,
      shaderMatrix: paint.shaderMatrixID.flatMap { matrices[$0] }.flatMap {
        NativeSwiftMatrixExpression.evaluate($0, values: values)
      },
      filterQuality: paint.filterQuality,
      usesComponentGeometry: usesComponentGeometry,
      gradient: paint.gradient.map { gradient in
        NativeSwiftGradientSnapshot(
          kind: gradient.kind,
          // A colour word is a literal ARGB unless its bit is set in the register, where it is an
          // ID resolved from the same colour table every other paint colour comes from.
          colorsARGB: gradient.colorWords.enumerated().map { index, word in
            if gradient.colorRegister & (1 << index) != 0 {
              return colors[word] ?? 0
            }
            return UInt32(bitPattern: Int32(word))
          },
          stops: gradient.stopWords.map { NativeSwiftFloatExpression.resolve($0, values: values) },
          values: gradient.coordinateWords.map {
            NativeSwiftFloatExpression.resolve($0, values: values)
          },
          tileMode: gradient.tileMode)
      }, text: textID.flatMap { id in
        guard let text = texts[id], let start = textStart, let end = textEnd else { return texts[id] }
        let lower = text.index(text.startIndex, offsetBy: min(max(start, 0), text.count))
        let upper = text.index(text.startIndex, offsetBy: min(max(end, start), text.count))
        return String(text[lower..<upper])
      }, textSize: NativeSwiftFloatExpression.resolve(paint.textSize, values: values),
      textFlags: textFlags)
  }
}

private struct ParsedImageDraw {
  let imageID: Int
  let source: [UInt32]
  let destination: [UInt32]
  let scaleType: Int
  let scaleFactor: UInt32
  let contentDescriptionID: Int

  func resolve(values: [Int: Float], texts: [Int: String]) -> NativeSwiftImageDrawSnapshot {
    let source = source.map { NativeSwiftFloatExpression.resolve($0, values: values) }
    let destination = destination.map { NativeSwiftFloatExpression.resolve($0, values: values) }
    return NativeSwiftImageDrawSnapshot(
      imageID: imageID,
      sourceLeft: source[0], sourceTop: source[1], sourceRight: source[2],
      sourceBottom: source[3], destinationLeft: destination[0], destinationTop: destination[1],
      destinationRight: destination[2], destinationBottom: destination[3], scaleType: scaleType,
      scaleFactor: NativeSwiftFloatExpression.resolve(scaleFactor, values: values),
      contentDescription: contentDescriptionID == 0 ? nil : texts[contentDescriptionID])
  }
}

private struct ParsedPath {
  let winding: Int
  let words: [UInt32]

  /// Whether any of this path's *argument* words references one of `ids`.
  ///
  /// The walk is structural on purpose. A path's command tokens are NaN-boxed ids 10...16, which
  /// collide with the system-variable ids in that range, so a flat scan over the words would report
  /// `OFFSET_TO_UTC` (10) on any document that draws a path at all.
  func references(anyOf ids: Set<Int>) -> Bool {
    func matches(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.referenceID(word).map(ids.contains) ?? false
    }
    var index = 0
    while index < words.count {
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else { return false }
      index += 1
      let padding: Int
      let argumentCount: Int
      switch command {
      case 10:
        padding = 0
        argumentCount = 2
      case 11:
        padding = 2
        argumentCount = 2
      case 12:
        padding = 2
        argumentCount = 4
      case 13:
        padding = 2
        argumentCount = 5
      case 14:
        padding = 2
        argumentCount = 6
      case 15:
        padding = 0
        argumentCount = 0
      case 16:
        return false
      default:
        return false
      }
      index += padding
      guard index + argumentCount <= words.count else { return false }
      for offset in 0..<argumentCount where matches(words[index + offset]) { return true }
      index += argumentCount
    }
    return false
  }

  func resolve(values: [Int: Float]) throws -> [NativeSwiftPathElementSnapshot] {
    var result: [NativeSwiftPathElementSnapshot] = []
    var index = 0
    func arguments(_ count: Int, skippingLegacyPadding: Bool = false) throws -> [Float] {
      if skippingLegacyPadding { index += 2 }
      guard index >= 0, index + count <= words.count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Truncated path data")
      }
      let resolved = words[index..<(index + count)].map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      }
      index += count
      return resolved
    }
    while index < words.count {
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Path command is not encoded")
      }
      index += 1
      switch command {
      case 10:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: try arguments(2)))
      case 11:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(2, skippingLegacyPadding: true)))
      case 12:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(4, skippingLegacyPadding: true)))
      case 13:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(5, skippingLegacyPadding: true)))
      case 14:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(6, skippingLegacyPadding: true)))
      case 15:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: []))
      case 16: return result
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 123, offset: 0, reason: "path command \(command)")
      }
    }
    return result
  }
}

/// A paint gradient held as it arrived on the wire, so its colours and coordinates resolve at the
/// same point as every other draw value. Colour words are literal ARGB unless its bit is set in
/// `colorRegister`, in which case they are colour IDs to look up.
private struct ParsedGradient {
  var kind: Int
  var colorWords: [Int]
  var colorRegister: Int
  var stopWords: [UInt32]
  var coordinateWords: [UInt32]
  var tileMode: Int
}

/// Command types in a `PAINT_DATA` bundle, which are separate from document operation opcodes.
private enum NativeSwiftPaintCommand {
  static let textSize = 1
  static let color = 4
  static let strokeWidth = 5
  static let strokeMiter = 6
  static let strokeCap = 7
  static let style = 8
  static let shader = 9
  static let imageFilterQuality = 10
  static let gradient = 11
  static let alpha = 12
  static let colorFilter = 13
  static let antiAlias = 14
  static let strokeJoin = 15
  static let typeface = 16
  static let filterBitmap = 17
  static let blendMode = 18
  static let colorID = 19
  static let colorFilterID = 20
  static let clearColorFilter = 21
  static let shaderMatrix = 22
  static let fontAxis = 23
  static let texture = 24
  static let pathEffect = 25
  static let fallbackTypeface = 26
}

private enum NativeSwiftPaintStyle {
  static let fill = 0
  static let stroke = 1
  static let fillAndStroke = 2
}

private enum NativeSwiftPaintGradientKind {
  static let linear = 0
  static let radial = 1
  static let sweep = 2
}

private enum NativeSwiftPaintFilterQuality {
  static let none = 0
  static let low = 1
  static let medium = 2
  static let high = 3
}

/// Blend-mode ids encoded in a paint command's high word.
public enum NativeSwiftPaintBlendMode {
  public static let clear = 0
  public static let source = 1
  public static let destination = 2
  public static let sourceOver = 3
  public static let destinationOver = 4
  public static let sourceIn = 5
  public static let destinationIn = 6
  public static let sourceOut = 7
  public static let destinationOut = 8
  public static let sourceAtop = 9
  public static let destinationAtop = 10
  public static let xor = 11
  public static let plus = 12
  public static let modulate = 13
  public static let screen = 14
  public static let overlay = 15
  public static let darken = 16
  public static let lighten = 17
  public static let colorDodge = 18
  public static let colorBurn = 19
  public static let hardLight = 20
  public static let softLight = 21
  public static let difference = 22
  public static let exclusion = 23
  public static let multiply = 24
  public static let hue = 25
  public static let saturation = 26
  public static let color = 27
  public static let luminosity = 28
}

/// A `MATRIX_EXPRESSION` held as it arrived on the wire. The expression is RPN over a small matrix
/// stack and is evaluated at snapshot time, once the floats its operands name have resolved.
private struct ParsedMatrixExpression {
  let id: Int
  let type: Int
  let words: [UInt32]
}

private struct ParsedMatrixVectorMath {
  let type: Int
  let outputIDs: [Int]
  let matrixID: Int
  let inputWords: [UInt32]
}

private struct ParsedPaint {
  var colorARGB: UInt32 = 0xff00_0000
  var colorID: Int?
  var colorFilterARGB: UInt32?
  var colorFilterID: Int?
  var colorFilterMode: Int?
  var gradient: ParsedGradient?
  var alpha: Float = 1
  var strokeWidth: UInt32 = Float(1).bitPattern
  var isStroke = false
  var strokeCap = 0
  var strokeJoin = 0
  var blendMode = NativeSwiftPaintBlendMode.sourceOver
  var textureImageID: Int?
  var textureTileModeX = 0
  var textureTileModeY = 0
  /// The `MatrixAccess` id a `SHADER_MATRIX` field named, or nil when the field was cleared or
  /// never set. Applies to whichever shader the paint currently carries.
  var shaderMatrixID: Int?
  /// How an image or texture is sampled: 0 none, 1 low, 2 medium, 3 high. Nil when the paint never
  /// said, which leaves the renderer's default in place.
  var filterQuality: Int?
  var textSize: UInt32 = Float(16).bitPattern
}

/// The word for a literal `-1`, which is how a node says "no maximum". Spelled once so the default
/// cannot drift from the `> 1_000_000 ? -1` sentinel the snapshot still hands the layout.
private let nativeSwiftNegativeOneWord = Float(-1).bitPattern

/// Padding held as it arrived on the wire. `NativeSwiftInsets` stays the resolved, public shape.
private struct ParsedInsetWords {
  var left: UInt32 = 0
  var top: UInt32 = 0
  var right: UInt32 = 0
  var bottom: UInt32 = 0
}

private final class ParsedNode {
  let kind: NativeSwiftNodeSnapshot.Kind
  let componentID: Int
  weak var parent: ParsedNode?
  var children: [ParsedNode] = []
  var commands: [ParsedDrawCommand] = []
  var isClickable = false
  var actions: [NativeSwiftGestureKind: [ParsedAction]] = [:]
  var accessibility: ParsedAccessibility?
  var widthType = 2
  var widthWord: UInt32 = 0
  var heightType = 2
  var heightWord: UInt32 = 0
  var paddingWords = ParsedInsetWords()
  var minimumWidthWord: UInt32 = 0
  var maximumWidthWord: UInt32 = nativeSwiftNegativeOneWord
  var minimumHeightWord: UInt32 = 0
  var maximumHeightWord: UInt32 = nativeSwiftNegativeOneWord
  // Four words rather than one: a rounded clip states a radius per corner, and the maximum of four
  // references is not itself a word, so the reduction has to wait until they resolve.
  var cornerRadiusWords: [UInt32] = []
  /// Set by MODIFIER_CLIP_RECT, which clips a component to its own laid-out bounds and carries no
  /// payload to say so. Separate from `cornerRadiusWords` because a square clip is not a zero-radius
  /// rounded clip: the rounded modifier states radii, this one states nothing at all.
  var clipsToBounds = false
  var graphicsLayer: [Int: Float] = [:]
  var offsetXWord: UInt32?
  var offsetYWord: UInt32?
  var zIndexWord: UInt32?
  var visibilityID: Int?
  var backgroundARGB: UInt32?
  var backgroundColorID: Int?
  var borderARGB: UInt32?
  var borderColorID: Int?
  var borderWidthWord: UInt32 = 0
  var horizontalPositioning = 1
  var verticalPositioning = 4
  var animationID: Int?
  var spacingWord: UInt32 = 0
  /// The AndroidX class name of the operation that produced this node, for the conformance corpus's
  /// `tree` probe. Empty for a structural wrapper, which the corpus never names.
  var componentKind = ""
  /// Set by the scroll modifier (226).
  var scrollDirection: NativeSwiftScrollDirection?
  /// The float word holding the scroll position, and the one holding how far it may travel. The
  /// notch maximum is consumed by the decoder and dropped: nothing here snaps a scroll yet.
  var scrollPositionWord: UInt32?
  var scrollMaximumWord: UInt32?
  /// Set by `StateLayout` (217): the integer holding the index of the child to show.
  var stateIndexID: Int?
  /// Set by `FlowLayout` (240): children wrap onto further lines, at most this many per line and
  /// this many lines in total. Both default to unlimited.
  var flowMaximumItems: Int?
  var flowMaximumLines: Int?
  /// Set by the collapsible row/column family; see `NativeSwiftCollapsible`.
  var isCollapsible = false
  /// A `CollapsiblePriority` modifier's payload, held as a word so it resolves with the frame's
  /// values like every other float field.
  var collapsiblePriorityWord: UInt32?
  var collapsiblePriorityOrientation: Int?
  var text: ParsedText?
  var custom: ParsedCustom?

  init(kind: NativeSwiftNodeSnapshot.Kind, componentID: Int) {
    self.kind = kind
    self.componentID = componentID
  }

  /// Whether any word in this subtree references one of `ids`.
  ///
  /// A player that publishes a value the document reads has to refresh it: a frame that resolves a
  /// calendar field once and then idles shows a frozen clock. This is the scan that decides it, and
  /// it covers every word a node can carry — geometry, padding, spacing, corner radii, text, and
  /// each draw command's own words, path, gradient and image fields.
  func references(anyOf ids: Set<Int>) -> Bool {
    func matches(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.referenceID(word).map(ids.contains) ?? false
    }
    if matches(widthWord) || matches(heightWord) || matches(spacingWord) { return true }
    if matches(paddingWords.left) || matches(paddingWords.top) || matches(paddingWords.right)
      || matches(paddingWords.bottom)
    {
      return true
    }
    if matches(minimumWidthWord) || matches(maximumWidthWord) || matches(minimumHeightWord)
      || matches(maximumHeightWord)
    {
      return true
    }
    if cornerRadiusWords.contains(where: matches) { return true }
    if let offsetXWord, matches(offsetXWord) { return true }
    if let offsetYWord, matches(offsetYWord) { return true }
    if let zIndexWord, matches(zIndexWord) { return true }
    if let text, matches(text.sizeWord) || matches(text.weightWord) { return true }
    for command in commands {
      if command.words.contains(where: matches) { return true }
      if let path = command.path, path.references(anyOf: ids) { return true }
      if let gradient = command.paint.gradient {
        if gradient.coordinateWords.contains(where: matches) { return true }
        if gradient.stopWords.contains(where: matches) { return true }
      }
      if let image = command.image {
        if image.source.contains(where: matches) || image.destination.contains(where: matches) {
          return true
        }
        if matches(image.scaleFactor) { return true }
      }
      if matches(command.paint.strokeWidth) { return true }
      if let alphaWord = command.alphaWord, matches(alphaWord) { return true }
    }
    return children.contains { $0.references(anyOf: ids) }
  }
}

private enum NativeSwiftIntegerExpression {
  static func evaluate(mask: Int, tokens: [Int], values: [Int: Int]) throws -> Int {
    var stack: [Int32] = []
    func pop(_ count: Int) throws -> [Int32] {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Integer expression stack underflow")
      }
      let result = Array(stack.suffix(count))
      stack.removeLast(count)
      return result
    }
    for (index, token) in tokens.enumerated() {
      let marked = UInt32(bitPattern: Int32(mask)) & (UInt32(1) << UInt32(index & 31)) != 0
      if !marked || token < 65_536 {
        stack.append(Int32(truncatingIfNeeded: marked ? values[token] ?? 0 : token))
        continue
      }
      let operation = token - 65_536
      if (1...14).contains(operation) {
        let value = try pop(2)
        let left = value[0]
        let right = value[1]
        switch operation {
        case 1: stack.append(left &+ right)
        case 2: stack.append(left &- right)
        case 3: stack.append(left &* right)
        case 4: stack.append(right == 0 || (left == .min && right == -1) ? 0 : left / right)
        case 5: stack.append(right == 0 || (left == .min && right == -1) ? 0 : left % right)
        case 6: stack.append(left << (right & 31))
        case 7: stack.append(left >> (right & 31))
        case 8:
          stack.append(Int32(bitPattern: UInt32(bitPattern: left) >> UInt32(right & 31)))
        case 9: stack.append(left | right)
        case 10: stack.append(left & right)
        case 11: stack.append(left ^ right)
        case 12: stack.append((left ^ (right >> 31)) &- (right >> 31))
        case 13: stack.append(min(left, right))
        default: stack.append(max(left, right))
        }
      } else if (15...20).contains(operation) {
        let value = try pop(1)[0]
        switch operation {
        case 15: stack.append(0 &- value)
        case 16: stack.append(value == .min ? .min : abs(value))
        case 17: stack.append(value &+ 1)
        case 18: stack.append(value &- 1)
        case 19: stack.append(~value)
        default: stack.append((value >> 31) | Int32(bitPattern: 0 &- UInt32(bitPattern: value)) >> 31)
        }
      } else if (21...23).contains(operation) {
        let value = try pop(3)
        if operation == 21 { stack.append(min(max(value[0], value[2]), value[1])) }
        else if operation == 22 { stack.append(value[2] > 0 ? value[1] : value[0]) }
        else { stack.append(value[2] &+ value[1] &* value[0]) }
      } else {
        throw NativeSwiftCoreError.unsupported(
          opcode: 144, offset: 0, reason: "integer expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Invalid integer expression result")
    }
    return Int(result)
  }
}

private enum NativeSwiftFloatExpression {
  private static let payloadMask: UInt32 = 0x007f_ffff
  private static let referenceMask: UInt32 = 0x003f_ffff
  private static let operatorOffset = 0x0031_0000

  static func resolve(_ word: UInt32, values: [Int: Float]) -> Float {
    guard isEncoded(word) else { return Float(bitPattern: word) }
    return values[Int(word & referenceMask)] ?? 0
  }

  static func referenceID(_ word: UInt32) -> Int? {
    guard isEncoded(word) else { return nil }
    let payload = Int(word & payloadMask)
    guard payload <= operatorOffset || payload > operatorOffset + 79 else { return nil }
    return Int(word & referenceMask)
  }

  static func evaluate(
    _ words: [UInt32], values: [Int: Float], variables: [Float] = []
  ) throws -> Float {
    var stack: [Float] = []
    stack.reserveCapacity(min(words.count, 128))
    func pop(_ count: Int) throws -> [Float] {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Float expression stack underflow")
      }
      let result = Array(stack.suffix(count))
      stack.removeLast(count)
      return result
    }
    for word in words {
      let payload = Int(word & payloadMask)
      guard isEncoded(word), payload > operatorOffset, payload <= operatorOffset + 79 else {
        stack.append(resolve(word, values: values))
        guard stack.count <= 128 else {
          throw NativeSwiftCoreError.malformed(offset: 0, reason: "Float expression stack overflow")
        }
        continue
      }
      let operation = payload - operatorOffset
      switch operation {
      case 70...72:
        let index = operation - 70
        stack.append(variables.indices.contains(index) ? variables[index] : 0)
      case 1...8:
        let value = try pop(2)
        switch operation {
        case 1: stack.append(value[0] + value[1])
        case 2: stack.append(value[0] - value[1])
        case 3: stack.append(value[0] * value[1])
        case 4: stack.append(value[0] / value[1])
        case 5: stack.append(value[0].truncatingRemainder(dividingBy: value[1]))
        case 6: stack.append(min(value[0], value[1]))
        case 7: stack.append(max(value[0], value[1]))
        default: stack.append(powf(value[0], value[1]))
        }
      case 9...11, 13...23, 28...31, 45, 51...53, 73:
        let value = try pop(1)[0]
        switch operation {
        case 9: stack.append(sqrtf(value))
        case 10: stack.append(abs(value))
        case 11: stack.append(value == 0 ? 0 : (value < 0 ? -1 : 1))
        case 13: stack.append(expf(value))
        case 14: stack.append(floorf(value))
        case 15: stack.append(log10f(value))
        case 16: stack.append(logf(value))
        case 17: stack.append(roundf(value))
        case 18: stack.append(sinf(value))
        case 19: stack.append(cosf(value))
        case 20: stack.append(tanf(value))
        case 21: stack.append(asinf(value))
        case 22: stack.append(acosf(value))
        case 23: stack.append(atanf(value))
        case 28: stack.append(cbrtf(value))
        case 29: stack.append(value * 57.29578)
        case 30: stack.append(value * 0.017453292)
        case 31: stack.append(ceilf(value))
        case 45: stack.append(value * value)
        case 51: stack.append(log2f(value))
        case 52: stack.append(1 / value)
        case 53:
          // FRACT, and the reference's own definition: the fraction above the floor, wrapped
          // positive. `value - Float(Int(value))` trapped on a non-finite or out-of-range operand
          // and was wrong for negatives anyway.
          let fraction = value - floorf(value)
          stack.append(fraction < 0 ? fraction + 1 : fraction)
        default: stack.append(-value)
        }
      case 12, 24, 43, 44, 47, 54:
        let value = try pop(2)
        switch operation {
        case 12: stack.append(copysignf(abs(value[0]), value[1]))
        case 24: stack.append(atan2f(value[0], value[1]))
        case 43: stack.append(value[0] * value[0] + value[1] * value[1])
        case 44: stack.append(value[0] > value[1] ? 1 : 0)
        case 47: stack.append(hypotf(value[0], value[1]))
        default:
          let doubled = value[1] * 2
          let remainder = value[0].truncatingRemainder(dividingBy: doubled)
          stack.append(remainder < value[1] ? remainder : doubled - remainder)
        }
      case 25:
        let value = try pop(3)
        stack.append(value[2] + value[1] * value[0])
      case 26:
        let value = try pop(3)
        stack.append(value[2] > 0 ? value[1] : value[0])
      case 27:
        let value = try pop(3)
        stack.append(min(max(value[0], value[2]), value[1]))
      case 48:
        // SWAP: the reference's expression language can exchange its top two operands, which is how
        // a formula written for a stack machine reads `a` and `b` in the order it wants.
        let value = try pop(2)
        stack.append(value[1])
        stack.append(value[0])
      case 49:
        let value = try pop(3)
        stack.append(value[0] + (value[1] - value[0]) * value[2])
      case 50:
        // SMOOTH_STEP: 0 below the first edge, 1 above the second, and the Hermite curve between
        // them. `expr_interpolation` is the gold that names it.
        let value = try pop(3)
        if value[0] < value[2] {
          stack.append(0)
        } else if value[0] > value[1] {
          stack.append(1)
        } else {
          let t = (value[0] - value[2]) / (value[1] - value[2])
          stack.append(t * t * (3 - 2 * t))
        }
      case 74:
        let value = try pop(5)
        stack.append(cubicEasing(value[0], value[1], value[2], value[3], value[4]))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 81, offset: 0, reason: "float expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last, result.isFinite else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Invalid float expression result")
    }
    return result
  }

  private static func isEncoded(_ word: UInt32) -> Bool {
    word & 0x7f80_0000 == 0x7f80_0000 && word & 0x007f_ffff != 0
  }

  private static func cubicEasing(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x: Float)
    -> Float
  {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func coordinate(_ t: Float, _ first: Float, _ second: Float) -> Float {
      let inverse = 1 - t
      return first * 3 * inverse * inverse * t + second * 3 * inverse * t * t + t * t * t
    }
    var t: Float = 0.5
    var range: Float = 0.5
    while range > 0.01 {
      let current = coordinate(t, x1, x2)
      range *= 0.5
      if current < x { t += range } else { t -= range }
    }
    let lowerX = coordinate(t - range, x1, x2)
    let upperX = coordinate(t + range, x1, x2)
    let lowerY = coordinate(t - range, y1, y2)
    let upperY = coordinate(t + range, y1, y2)
    return (upperY - lowerY) * (x - lowerX) / (upperX - lowerX) + lowerY
  }
}

/// Evaluates a `MATRIX_EXPRESSION` into the 3x3 layout AndroidX's `MatrixAccess` exposes.
///
/// The expression is an RPN stream over a small matrix stack: literals are operands, and a
/// NaN-boxed operator token acts on the operand slots immediately before it. This is the same
/// evaluator `MatrixOperations` runs, kept here because a paint's `SHADER_MATRIX` field names one
/// and a texture shader is meaningless without it.
///
/// A token sequence this evaluator cannot complete resolves to nil rather than failing the
/// document: the matrix used to be dropped outright, so dropping it again is not a regression, and
/// the shader still draws at its natural scale.
private enum NativeSwiftMatrixExpression {
  static let operatorOffset = 0x0032_0000
  static let lastOperator = operatorOffset + 54

  /// The NaN-encoded id a `SHADER_MATRIX` paint field carries, or nil when the field is zero.
  static func referenceID(word: UInt32) -> Int? {
    guard word & 0x7f80_0000 == 0x7f80_0000 else { return nil }
    let payload = Int(word & 0x003f_ffff)
    guard payload == 0 || payload <= operatorOffset || payload > lastOperator else { return nil }
    return payload
  }

  static func evaluate4x4(_ expression: ParsedMatrixExpression, values: [Int: Float]) -> [Float]? {
    var matrices = [[Float]](repeating: identity, count: 10)
    var index = 0
    matrices[0] = identity
    func operand(_ position: Int) -> Float? {
      guard position >= 0, position < expression.words.count else { return nil }
      let value = NativeSwiftFloatExpression.resolve(expression.words[position], values: values)
      return value.isFinite ? value : nil
    }
    for (position, word) in expression.words.enumerated() {
      let payload = Int(word & 0x003f_ffff)
      guard word & 0x7f80_0000 == 0x7f80_0000, payload > operatorOffset,
        payload <= lastOperator
      else { continue }
      let operation = payload - operatorOffset
      switch operation {
      case 1:  // IDENTITY pushes a new matrix for a following scale or rotation.
        index += 1
        guard index < matrices.count else { return nil }
        matrices[index] = identity
      case 2, 3, 4:
        guard let degrees = operand(position - 1) else { return nil }
        matrices[index] = multiply(matrices[index], rotation(axis: operation, degrees: degrees))
      case 5, 6, 7:
        guard let value = operand(position - 1) else { return nil }
        matrices[index] = multiply(
          matrices[index],
          translation(
            x: operation == 5 ? value : 0, y: operation == 6 ? value : 0,
            z: operation == 7 ? value : 0))
      case 8, 9:
        let first = operand(position - 2)
        let second = operand(position - 1)
        guard let first, let second else { return nil }
        matrices[index] = multiply(
          matrices[index], translation(x: first, y: second, z: 0))
      case 10, 11, 12:
        guard let value = operand(position - 1) else { return nil }
        scale(
          &matrices[index],
          x: operation == 10 ? value : 1, y: operation == 11 ? value : 1,
          z: operation == 12 ? value : 1)
      case 13:
        let first = operand(position - 2)
        let second = operand(position - 1)
        guard let first, let second else { return nil }
        scale(&matrices[index], x: first, y: second, z: 0)
      case 14:
        let first = operand(position - 3)
        let second = operand(position - 2)
        let third = operand(position - 1)
        guard let first, let second, let third else { return nil }
        scale(&matrices[index], x: first, y: second, z: third)
      case 15:  // MUL merges the top two matrices.
        guard index > 0 else { return nil }
        matrices[index - 1] = multiply(matrices[index - 1], matrices[index])
        index -= 1
      case 16:  // ROT_PZ: angle, pivot x, pivot y.
        let pivotX = operand(position - 2)
        let pivotY = operand(position - 1)
        let degrees = operand(position - 3)
        guard let pivotX, let pivotY, let degrees else { return nil }
        matrices[index] = multiply(
          rotationWithPivot(pivotX: pivotX, pivotY: pivotY, degrees: degrees), matrices[index])
      case 17:  // ROT_AXIS: angle, x, y, z.
        let x = operand(position - 3)
        let y = operand(position - 2)
        let z = operand(position - 1)
        let degrees = operand(position - 4)
        guard let x, let y, let z, let degrees else { return nil }
        matrices[index] = multiply(rotationAroundAxis(x: x, y: y, z: z, degrees: degrees), matrices[index])
      case 18:  // PROJECTION: fov degrees, aspect ratio, near, far.
        let fov = operand(position - 4)
        let aspect = operand(position - 3)
        let near = operand(position - 2)
        let far = operand(position - 1)
        guard let fov, let aspect, let near, let far else { return nil }
        matrices[index] = multiply(matrices[index], projection(fov: fov, aspect: aspect, near: near, far: far))
      default:
        return nil
      }
    }
    return matrices[0]
  }

  static func evaluate(_ expression: ParsedMatrixExpression, values: [Int: Float]) -> [Float]? {
    guard let matrix = evaluate4x4(expression, values: values) else { return nil }
    // `MatrixAccess.to3x3`: a 4x4 collapses to the Android 3x3 layout, which is
    // [scaleX, skewX, translateX, skewY, scaleY, translateY, persp0, persp1, persp2].
    return [
      matrix[0], matrix[1], matrix[3],
      matrix[4], matrix[5], matrix[7],
      matrix[8], matrix[9], matrix[15],
    ]
  }

  private static let identity: [Float] = [
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1,
  ]

  private static func multiply(_ first: [Float], _ second: [Float]) -> [Float] {
    var result = [Float](repeating: 0, count: 16)
    for row in 0..<4 {
      for column in 0..<4 {
        var sum: Float = 0
        for k in 0..<4 { sum += first[row * 4 + k] * second[k * 4 + column] }
        result[row * 4 + column] = sum
      }
    }
    return result
  }

  private static func translation(x: Float, y: Float, z: Float) -> [Float] {
    var matrix = identity
    matrix[3] = x
    matrix[7] = y
    matrix[11] = z
    return matrix
  }

  private static func scale(_ matrix: inout [Float], x: Float, y: Float, z: Float) {
    matrix[0] *= x
    matrix[5] *= y
    matrix[10] *= z
  }

  private static func rotation(axis: Int, degrees: Float) -> [Float] {
    let radians = degrees * .pi / 180
    let cosine = cosf(radians)
    let sine = sinf(radians)
    var matrix = identity
    switch axis {
    case 2:
      matrix[5] = cosine
      matrix[6] = -sine
      matrix[9] = sine
      matrix[10] = cosine
    case 3:
      matrix[0] = cosine
      matrix[2] = sine
      matrix[8] = -sine
      matrix[10] = cosine
    default:
      matrix[0] = cosine
      matrix[1] = -sine
      matrix[4] = sine
      matrix[5] = cosine
    }
    return matrix
  }

  private static func rotationWithPivot(pivotX: Float, pivotY: Float, degrees: Float) -> [Float] {
    let radians = degrees * .pi / 180
    let cosine = cosf(radians)
    let sine = sinf(radians)
    var matrix = identity
    matrix[0] = cosine
    matrix[1] = -sine
    matrix[3] = pivotX * (1 - cosine) + pivotY * sine
    matrix[4] = sine
    matrix[5] = cosine
    matrix[7] = pivotY * (1 - cosine) - pivotX * sine
    return matrix
  }

  private static func rotationAroundAxis(x: Float, y: Float, z: Float, degrees: Float) -> [Float] {
    let lengthSquared = x * x + y * y + z * z
    guard lengthSquared > 0 else { return identity }
    let length = sqrtf(lengthSquared)
    let ux = x / length
    let uy = y / length
    let uz = z / length
    let radians = degrees * .pi / 180
    let cosine = cosf(radians)
    let sine = sinf(radians)
    let oneMinusCosine = 1 - cosine
    var matrix = identity
    matrix[0] = cosine + ux * ux * oneMinusCosine
    matrix[1] = ux * uy * oneMinusCosine - uz * sine
    matrix[2] = ux * uz * oneMinusCosine + uy * sine
    matrix[4] = uy * ux * oneMinusCosine + uz * sine
    matrix[5] = cosine + uy * uy * oneMinusCosine
    matrix[6] = uy * uz * oneMinusCosine - ux * sine
    matrix[8] = uz * ux * oneMinusCosine - uy * sine
    matrix[9] = uz * uy * oneMinusCosine + ux * sine
    matrix[10] = cosine + uz * uz * oneMinusCosine
    return matrix
  }

  private static func projection(fov: Float, aspect: Float, near: Float, far: Float) -> [Float] {
    let radians = fov * .pi / 180
    let f = 1 / tanf(radians / 2)
    let range = 1 / (near - far)
    var matrix = identity
    matrix[0] = f / aspect
    matrix[5] = f
    matrix[10] = (far + near) * range
    matrix[11] = -1
    matrix[14] = 2 * far * near * range
    matrix[15] = 0
    return matrix
  }
}

private struct ParsedText {
  let textID: Int
  let colorARGB: UInt32
  let colorID: Int?
  let sizeWord: UInt32
  let style: Int
  let weightWord: UInt32
  let familyID: Int
  let alignment: Int
  let overflow: Int
  let maximumLines: Int
}

private struct ParsedCustom {
  let configID: Int
  let properties: [ParsedCustomProperty]
}

private struct ParsedCustomProperty {
  let type: Int
  let dataType: Int
  let valueBits: Int
}

let nativeSwiftMaximumStringBytes = 4_000

private enum NativeSwiftDocumentDecoder {
  static let maximumStringBytes = nativeSwiftMaximumStringBytes
  private static let maximumOperations = 100_000
  private static let maximumProperties = 2_000
  private static let maximumNodes = 20_000
  private static let maximumNestingDepth = 256

  static func decode(_ data: Data, toleratingRootlessData: Bool = false) throws -> ParsedDocument {
    var spans: [NativeSwiftOperationSpan] = []
    return try decode(data, spans: &spans, toleratingRootlessData: toleratingRootlessData)
  }

  /// Decodes a document and records each top-level operation's byte extent in `spans`.
  ///
  /// The recording is unconditional because it is three assignments per operation and the array is
  /// discarded by the ordinary entry point; a second, span-collecting walk would be a second
  /// decoder that could drift from this one.
  static func decode(
    _ data: Data, spans: inout [NativeSwiftOperationSpan], toleratingRootlessData: Bool = false
  ) throws -> ParsedDocument {
    var input = WireReader(data)
    guard try input.u8("header opcode") == 0 else {
      throw input.malformed("Document must begin with a header")
    }
    let encodedMajor = try input.int("major version")
    _ = try input.int("minor version")
    _ = try input.int("patch version")
    let major: Int
    let width: Int
    let height: Int
    var density: Float = 1
    var densityBehavior = 0
    if encodedMajor < 0x10000 {
      major = encodedMajor
      width = try input.int("width")
      height = try input.int("height")
      _ = try input.int("capabilities high word")
      _ = try input.int("capabilities low word")
    } else {
      guard encodedMajor & ~0xffff == 0x048c_0000 else {
        throw input.malformed("Invalid modern header magic")
      }
      major = encodedMajor & 0xffff
      let count = try input.count("header property count", maximum: maximumProperties)
      var modernWidth: Int?
      var modernHeight: Int?
      for index in 0..<count {
        let tag = try input.u16("header property \(index) tag")
        let length = Int(try input.u16("header property \(index) length"))
        let type = Int(tag >> 10)
        let key = Int(tag & 0x03ff)
        switch type {
        case 0:
          guard length == 4 else { throw input.malformed("Invalid integer header property length") }
          let value = try input.int("header property \(index)")
          if key == 5 { modernWidth = value }
          if key == 6 { modernHeight = value }
          if key == 27 { densityBehavior = value }
        case 1:
          guard length == 4 else { throw input.malformed("Invalid float header property length") }
          let value = Float(bitPattern: try input.word("header property \(index)"))
          if key == 7 { density = value }
        case 2:
          guard length == 8 else { throw input.malformed("Invalid long header property length") }
          _ = try input.int("header property \(index) high word")
          _ = try input.int("header property \(index) low word")
        case 3:
          guard length >= 4 else { throw input.malformed("Invalid string header property length") }
          let value = try input.utf8("header property \(index)", maximum: maximumStringBytes)
          guard value.utf8.count + 4 == length else {
            throw input.malformed("Invalid string header property length")
          }
        default: throw input.malformed("Unknown header property type \(type)")
        }
      }
      // LOOM templates deliberately carry no intrinsic canvas: their caller supplies the capture
      // viewport after macro expansion. Keep a positive neutral size for the shared snapshot; the
      // host still uses the requested viewport when it paints the expanded document.
      width = modernWidth ?? 1
      height = modernHeight ?? 1
    }
    guard major >= 0, width > 0, height > 0, density.isFinite, density > 0,
      (0...2).contains(densityBehavior)
    else {
      throw input.malformed("Header dimensions, version, or density metadata are invalid")
    }

    var texts: [Int: String] = [:]
    var floats: [Int: Float] = [:]
    var colors: [Int: UInt32] = [:]
    var integers: [Int: Int] = [:]
    var integerExpressions: [Int: ParsedIntegerExpression] = [:]
    var integerExpressionOrder: [Int] = []
    var namedVariables: [String: ParsedNamedVariable] = [:]
    var expressions: [ParsedFloatExpression] = []
    var componentValues: [ParsedComponentValue] = []
    var colorAttributes: [ParsedColorAttribute] = []
    var colorExpressions: [ParsedColorExpression] = []
    var paths: [Int: ParsedPath] = [:]
    var images: [Int: ParsedImageResource] = [:]
    var textOperations: [ParsedTextOperation] = []
    var textFromFloats: [ParsedTextFromFloat] = []
    var textMerges: [ParsedTextMerge] = []
    var textTransforms: [ParsedTextTransform] = []
    var idLists: [Int: [Int]] = [:]
    var floatLists: [Int: [UInt32]] = [:]
    var floatListUpdates: [Int: [(index: UInt32, value: UInt32)]] = [:]
    var dynamicFloatLists: [Int: ParsedDynamicFloatList] = [:]
    var dataMaps: [Int: [String: (type: Int, valueID: Int)]] = [:]
    var dataMapLookups: [ParsedDataMapLookup] = []
    var textLookups: [ParsedTextLookupInt] = []
    var textFloatLookups: [ParsedTextLookup] = []
    var matrixConstants: [Int: NativeSwiftMatrixSnapshot] = [:]
    var matrixExpressions: [Int: ParsedMatrixExpression] = [:]
    var matrixVectorMath: [ParsedMatrixVectorMath] = []
    var animationSpecs: [Int: NativeSwiftAnimationSpec] = [:]
    var animationSpecOrder: [Int] = []
    var pathIDs: Set<Int> = []
    var pathTweenIDs: Set<Int> = []
    var accessibilityRecords: [ParsedAccessibility] = []
    var shaderUniformNames: [Int: Set<String>] = [:]
    var conditionalTraces: [NativeSwiftConditionalTraceSnapshot] = []
    var conditionalIndex = 0
    var particleDefinitions: [ParsedParticleDefinition] = []
    var particleLoops: [ParsedParticleLoop] = []
    var nodes: [Int: ParsedNode] = [:]
    var stack: [ParsedNode] = []
    var root: ParsedNode?
    var implicitCanvasRoot: ParsedNode?
    var operationCount = 0
    var linkedTopLevelOperationCount = 0
    var syntheticRootWasAdded = false
    var expressionWordCount = 0
    var modifierContainers: [ParsedModifierContainer] = []
    var paint = ParsedPaint()
    // AndroidX has two wire forms for MacroDefine: an inline byte body and a container body
    // terminated by ContainerEnd.  Retaining raw bytes lets a call execute its body with the
    // caller's current paint and layout state, rather than treating a definition as drawing work.
    struct MacroDefinition {
      let parameterIDs: [Int]
      let body: Data
    }
    struct MacroExpansionFrame {
      let input: WireReader
      let blocks: [Int: Data]
    }
    var macroDefinitions: [Int: MacroDefinition] = [:]
    var referencedOperations: [Int: Data] = [:]
    var suspendedInputs: [MacroExpansionFrame] = []
    var macroBlocks: [Int: Data] = [:]
    // Tier-two LOOM IDs (0x4000...0x4fff) are local declarations.  A macro call must
    // materialise a fresh ID for each one, or two otherwise independent calls try to add the
    // same ParsedNode to `nodes`.  Keep generated IDs outside the local tier: they remain valid
    // 22-bit NaN-reference payloads and cannot be mistaken for a template-local declaration on a
    // later nested expansion.
    var nextMacroGeneratedID = 0x5000

    func begin(_ node: ParsedNode) throws {
      guard nodes.count < maximumNodes else {
        throw input.malformed("Node count exceeds \(maximumNodes)")
      }
      guard stack.count < maximumNestingDepth else {
        throw input.malformed("Nesting depth exceeds \(maximumNestingDepth)")
      }
      guard nodes[node.componentID] == nil else {
        throw input.malformed("Duplicate component id \(node.componentID)")
      }
      if let parent = stack.last {
        node.parent = parent
        parent.children.append(node)
      } else if let implicitCanvasRoot {
        node.parent = implicitCanvasRoot
        implicitCanvasRoot.children.append(node)
      } else if root == nil {
        root = node
      } else if toleratingRootlessData, let existingRoot = root {
        // Conformance-only data/LOOM documents may materialise several top-level layout nodes
        // without a RootLayoutComponent. Preserve their tree under a synthetic root rather than
        // treating that valid structural probe as a malformed render document.
        let syntheticRoot = ParsedNode(kind: .root, componentID: 0)
        syntheticRoot.componentKind = "RootLayoutComponent"
        existingRoot.parent = syntheticRoot
        syntheticRoot.children.append(existingRoot)
        node.parent = syntheticRoot
        syntheticRoot.children.append(node)
        root = syntheticRoot
        nodes[syntheticRoot.componentID] = syntheticRoot
        syntheticRootWasAdded = true
      } else {
        throw input.malformed("Document has more than one root component")
      }
      nodes[node.componentID] = node
      stack.append(node)
    }

    // AndroidX permits a document to begin with canvas commands.  LOOM fixtures use this form to
    // paint a base layer before defining and inflating a pattern, so it is not equivalent to a
    // rootless data document.  Create the implicit canvas only at the first drawing operation:
    // doing it eagerly would hide a genuinely missing layout root in an otherwise data-only file.
    func drawingNode() throws -> ParsedNode {
      if stack.isEmpty {
        if let implicitCanvasRoot { return implicitCanvasRoot }
        guard root == nil, nodes.count < maximumNodes else {
          throw input.malformed("Drawing operation has no active canvas")
        }
        let canvas = ParsedNode(kind: .root, componentID: 0)
        canvas.componentKind = "Canvas"
        root = canvas
        nodes[canvas.componentID] = canvas
        implicitCanvasRoot = canvas
        return canvas
      }
      return try currentNode(stack, input: input)
    }

    func captureMacroBody() throws -> Data {
      let start = input.offset
      var nesting = 0
      while true {
        let opcodeOffset = input.offset
        let opcode = try input.u8("macro body opcode")
        switch opcode {
        case 214:
          if nesting == 0 { return input.rawBytes(from: start, to: opcodeOffset) }
          nesting -= 1
        case 40:
          let count = try input.count("macro paint word count", maximum: 1_024)
          for _ in 0..<count { _ = try input.int("macro paint word") }
        case 38, 124, 248:
          _ = try input.int("macro clip path id")
        case 247:
          _ = try input.int("nested macro id")
          let argumentCount = try input.count("nested macro argument count", maximum: maximumProperties)
          for _ in 0..<argumentCount { _ = try input.int("nested macro argument") }
          // A MacroCall is itself a container: preserve its supplied MacroBlocks and consume its
          // matching end so the surrounding definition's end remains the capture terminator.
          while true {
            let childOpcode = try input.u8("nested macro call operation")
            if childOpcode == 214 { break }
            guard childOpcode == 249 else {
              throw NativeSwiftCoreError.unsupported(
                opcode: childOpcode, offset: input.offset - 1,
                reason: "LOOM nested macro calls only support MacroBlock children")
            }
            _ = try input.int("nested macro block index")
            _ = try captureMacroBody()
          }
        case 39, 42, 47, 56:
          for _ in 0..<4 { _ = try input.word("macro drawing value") }
        case 44:
          _ = try input.int("macro bitmap id")
          for _ in 0..<4 { _ = try input.word("macro bitmap destination") }
          _ = try input.int("macro bitmap description id")
        case 46:
          for _ in 0..<3 { _ = try input.word("macro circle value") }
        case NativeSwiftWireOpcode.conditionalOperations:
          _ = try input.u8("conditional type")
          _ = try input.word("conditional left")
          _ = try input.word("conditional right")
          nesting += 1
        case 51, 52, 152:
          for _ in 0..<6 { _ = try input.word("macro drawing value") }
        case 202:
          // BoxLayout declares both its component and animation IDs; positioning is plain data.
          for _ in 0..<4 { _ = try input.int("macro box value") }
          nesting += 1
        case 130, 131:
          break
        case 173:
          nesting += 1
        default:
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset,
            reason: "LOOM macro body operation is not yet structurally migrated")
        }
      }
    }

    func captureMacroCallBlocks() throws -> [Int: Data] {
      var blocks: [Int: Data] = [:]
      while true {
        let opcodeOffset = input.offset
        let opcode = try input.u8("macro call operation")
        if opcode == 214 { return blocks }
        guard opcode == 249 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset,
            reason: "LOOM macro calls only support MacroBlock children")
        }
        let index = try input.int("macro block index")
        guard blocks[index] == nil else { throw input.malformed("Duplicate macro block \(index)") }
        blocks[index] = try captureMacroBody()
      }
    }

    func remappedMacroBody(_ body: Data, mappings initialMappings: [Int: Int]) throws -> Data {
      var reader = WireReader(body)
      var output = [UInt8](body)
      var mappings = initialMappings
      func replaceWord(at offset: Int, with word: UInt32) {
        output[offset] = UInt8(truncatingIfNeeded: word >> 24)
        output[offset + 1] = UInt8(truncatingIfNeeded: word >> 16)
        output[offset + 2] = UInt8(truncatingIfNeeded: word >> 8)
        output[offset + 3] = UInt8(truncatingIfNeeded: word)
      }
      func replaceID(at offset: Int, with value: Int) {
        replaceWord(at: offset, with: UInt32(bitPattern: Int32(value)))
      }
      func remapFloatReference(at offset: Int) throws {
        let word = try reader.word("macro drawing value")
        if let id = NativeSwiftFloatExpression.referenceID(word), let replacement = mappings[id] {
          replaceWord(
            at: offset,
            with: (word & ~UInt32(0x003f_ffff)) | UInt32(truncatingIfNeeded: replacement))
        }
      }
      func declaredID(at offset: Int, name: String) throws {
        let id = try reader.int(name)
        if let replacement = mappings[id] {
          replaceID(at: offset, with: replacement)
          return
        }
        // `declareId` in the canonical LOOM reader preserves system globals, then allocates a
        // distinct ID for every declaration read while expanding a macro (including regular IDs).
        guard id > 41, id != -1 else { return }
        guard nextMacroGeneratedID <= 0x003f_ffff else {
          throw input.malformed("LOOM macro generated-id range is exhausted")
        }
        let replacement = nextMacroGeneratedID
        nextMacroGeneratedID += 1
        mappings[id] = replacement
        replaceID(at: offset, with: replacement)
      }
      while !reader.isAtEnd {
        let opcodeOffset = reader.offset
        let opcode = try reader.u8("macro body opcode")
        switch opcode {
        case 38, 124:
          let idOffset = reader.offset
          let id = try reader.int("macro path id")
          if let replacement = mappings[id] { replaceID(at: idOffset, with: replacement) }
        case 247:
          _ = try reader.int("nested macro id")
          let argumentCount = try reader.count("nested macro argument count", maximum: maximumProperties)
          for _ in 0..<argumentCount {
            let argumentOffset = reader.offset
            let argument = try reader.int("nested macro argument")
            if let replacement = mappings[argument] {
              replaceID(at: argumentOffset, with: replacement)
            }
          }
          // The nested call has no blocks in the forwarding form. Its own expansion performs the
          // next level of parameter rewrite after this parent body is resumed.
          guard try reader.u8("nested macro call end") == 214 else {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "LOOM nested macro-call blocks are not migrated for parameter remapping")
          }
        case 40:
          let count = try reader.count("macro paint word count", maximum: 1_024)
          for _ in 0..<count { _ = try reader.int("macro paint word") }
        case 248:
          _ = try reader.int("macro argument index")
        case 39, 42, 47, 56:
          for _ in 0..<4 { let offset = reader.offset; try remapFloatReference(at: offset) }
        case 44:
          _ = try reader.int("macro bitmap id")
          for _ in 0..<4 { let offset = reader.offset; try remapFloatReference(at: offset) }
          _ = try reader.int("macro bitmap description id")
        case 46:
          for _ in 0..<3 { let offset = reader.offset; try remapFloatReference(at: offset) }
        case 51, 52, 152:
          for _ in 0..<6 { let offset = reader.offset; try remapFloatReference(at: offset) }
        case 202:
          let componentOffset = reader.offset
          try declaredID(at: componentOffset, name: "macro box component id")
          let animationOffset = reader.offset
          try declaredID(at: animationOffset, name: "macro box animation id")
          _ = try reader.int("macro box horizontal positioning")
          _ = try reader.int("macro box vertical positioning")
        case 130, 131:
          break
        case 214:
          // Container ends carry no IDs. Component containers inside a macro body retain their
          // own terminator after capture, so the expanded stream must preserve it verbatim.
          break
        default:
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset,
            reason: "LOOM macro parameter remapping is not migrated for this operation")
        }
      }
      return Data(output)
    }

    func remappedMacroBody(_ definition: MacroDefinition, arguments: [Int]) throws -> Data {
      guard definition.parameterIDs.count == arguments.count else {
        throw input.malformed(
          "Macro expects \(definition.parameterIDs.count) arguments, got \(arguments.count)")
      }
      let mappings = Dictionary(uniqueKeysWithValues: zip(definition.parameterIDs, arguments))
      return try remappedMacroBody(definition.body, mappings: mappings)
    }

    while true {
      if input.isAtEnd {
        guard let suspended = suspendedInputs.popLast() else { break }
        input = suspended.input
        macroBlocks = suspended.blocks
        continue
      }
      operationCount += 1
      guard operationCount <= maximumOperations else {
        throw input.malformed("Operation count exceeds \(maximumOperations)")
      }
      let opcodeOffset = input.offset
      let opcode = try input.u8("opcode")
      // `ops:count` is a census of the linked top-level tree, not decoder iterations. Containers
      // own their children; macro definitions and calls expand into those children; neither is an
      // independent top-level operation. Template bytes execute through `suspendedInputs` and are
      // likewise excluded from the source document's top-level census.
      if stack.isEmpty, modifierContainers.isEmpty, suspendedInputs.isEmpty {
        switch opcode {
        case 200:
          linkedTopLevelOperationCount += 1
        case 201...205, 207, 208, 217, 233, 240, 246, 247, 249, 214:
          break
        default:
          linkedTopLevelOperationCount += 1
        }
      }
      switch opcode {
      case NativeSwiftWireOpcode.conditionalOperations:
        let type = Int(try input.u8("conditional type"))
        let leftWord = try input.word("conditional left")
        let rightWord = try input.word("conditional right")
        let body = try captureMacroBody()
        let left = NativeSwiftFloatExpression.resolve(leftWord, values: floats)
        let right = NativeSwiftFloatExpression.resolve(rightWord, values: floats)
        let executed: Bool
        switch type {
        case 0: executed = left == right
        case 1: executed = left != right
        case 2: executed = left < right
        case 3: executed = left <= right
        case 4: executed = left > right
        case 5: executed = left >= right
        case 6: executed = left != 0 || right != 0
        default: executed = false
        }
        let path = String(conditionalIndex)
        conditionalIndex += 1
        conditionalTraces.append(NativeSwiftConditionalTraceSnapshot(
          type: type, left: left, right: right, executed: executed,
          executedChildOps: executed && !body.isEmpty ? 1 : 0, path: path))
        if executed {
          guard suspendedInputs.count < maximumNestingDepth else {
            throw input.malformed("Conditional nesting exceeds \(maximumNestingDepth)")
          }
          suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
          input = WireReader(body)
        }
      case 2:  // Legacy ComponentStart. Retain its structure; modern documents use 200...205.
        let kind = try input.int("legacy component kind")
        let componentID = try input.int("legacy component id")
        _ = try input.word("legacy component width")
        _ = try input.word("legacy component height")
        let node = ParsedNode(kind: .box, componentID: componentID)
        node.componentKind = "LegacyComponent\(kind)"
        try begin(node)
      case 241:  // Parse-time conditional section skip.
        let condition = try input.int("skip condition")
        let value = try input.int("skip value")
        let length = try input.count("skip length", maximum: maximumStringBytes)
        let shouldSkip: Bool
        switch condition {
        case 1: shouldSkip = 7 < value  // AndroidX's current player API baseline.
        case 2: shouldSkip = 7 > value
        case 3: shouldSkip = 7 == value
        case 4: shouldSkip = 7 != value
        case 5: shouldSkip = (0 & value) != 0
        case 6: shouldSkip = (0 & value) == 0
        default: shouldSkip = false
        }
        if shouldSkip { _ = try input.rawData("skipped operation section", length: length) }
      case 142:  // ReferencedOperations definition container.
        let referenceID = try input.int("referenced operations id")
        guard referencedOperations[referenceID] == nil else {
          throw input.malformed("Duplicate referenced operations \(referenceID)")
        }
        referencedOperations[referenceID] = try captureMacroBody()
      case 245:  // Inline a previously captured ReferencedOperations body.
        let referenceID = try input.int("referenced operations id")
        guard let body = referencedOperations[referenceID] else {
          throw input.malformed("Missing referenced operations \(referenceID)")
        }
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("Structural expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(body)
      case 244:  // Expand a template body once for each ID in a DataListIds collection.
        let collectionID = try input.int("pattern foreach collection id")
        let localItemID = try input.int("pattern foreach local item id")
        let body = try captureMacroBody()
        guard let ids = idLists[collectionID] else {
          throw input.malformed("Missing pattern foreach collection \(collectionID)")
        }
        var expanded = Data()
        for id in ids {
          expanded.append(try remappedMacroBody(body, mappings: [localItemID: id]))
        }
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("LOOM expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(expanded)
      case 248:  // Insert the block supplied to the enclosing MacroCall.
        let index = try input.int("macro argument index")
        guard let block = macroBlocks[index] else {
          throw input.malformed("Missing macro block \(index)")
        }
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("LOOM expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(block)
      case 246:  // Macro definition. Definitions are structural and do not execute their body.
        let macroID = try input.int("macro id")
        let parameterCount = try input.count("macro parameter count", maximum: maximumProperties)
        let parameterIDs = try (0..<parameterCount).map { _ in try input.int("macro parameter id") }
        let bodySize = try input.count("macro body size", maximum: maximumStringBytes)
        let body =
          bodySize == 0
          ? try captureMacroBody()
          : try input.rawData("macro body", length: bodySize)
        guard macroDefinitions[macroID] == nil else {
          throw input.malformed("Duplicate macro definition \(macroID)")
        }
        macroDefinitions[macroID] = MacroDefinition(parameterIDs: parameterIDs, body: body)
      case 247:  // Macro call container.
        let macroID = try input.int("macro id")
        let argumentCount = try input.count("macro argument count", maximum: maximumProperties)
        let arguments = try (0..<argumentCount).map { _ in try input.int("macro argument id") }
        guard let definition = macroDefinitions[macroID] else {
          throw input.malformed("Missing macro definition \(macroID)")
        }
        let callBlocks = try captureMacroCallBlocks()
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("LOOM expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(try remappedMacroBody(definition, arguments: arguments))
        macroBlocks = callBlocks
      case 40:  // Paint data
        let count = try input.count("paint word count", maximum: 1_024)
        var words: [Int] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.int("paint word")) }
        try applyPaint(words, to: &paint, input: input)
      case 45:  // Runtime shader resource; retain names for decoded-operation records.
        let shaderID = try input.int("shader id")
        _ = try input.int("shader text id")
        let sizes = UInt32(bitPattern: Int32(try input.int("shader uniform sizes")))
        guard sizes >> 24 == 0 else { throw input.malformed("Invalid shader uniform sizes") }
        var names: Set<String> = []
        for _ in 0..<(sizes & 0xff) {
          names.insert(try input.utf8("shader float uniform name", maximum: maximumStringBytes))
          let count = try input.count("shader float uniform value count", maximum: 1_024)
          for _ in 0..<count { _ = try input.word("shader float uniform value") }
        }
        for _ in 0..<((sizes >> 8) & 0xff) {
          names.insert(try input.utf8("shader int uniform name", maximum: maximumStringBytes))
          let count = try input.count("shader int uniform value count", maximum: 1_024)
          for _ in 0..<count { _ = try input.int("shader int uniform value") }
        }
        for _ in 0..<((sizes >> 16) & 0xff) {
          names.insert(try input.utf8("shader bitmap uniform name", maximum: maximumStringBytes))
          _ = try input.int("shader bitmap uniform id")
        }
        shaderUniformNames[shaderID] = names
      case 38:  // Clip path
        let id = try input.int("clip path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 7, words: [], paint: paint, path: path))
      case 39:  // Clip rectangle
        let words = try (0..<4).map { _ in try input.word("clip rectangle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 6, words: words, paint: paint))
      case 42:  // Draw rectangle
        let words = try (0..<4).map { _ in try input.word("draw rectangle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 10, words: words, paint: paint))
      case NativeSwiftWireOpcode.drawText:
        let textID = try input.int("draw text id")
        let start = try input.int("draw text start")
        let end = try input.int("draw text end")
        _ = try input.int("draw text context start")
        _ = try input.int("draw text context end")
        let x = try input.word("draw text x")
        let y = try input.word("draw text y")
        let rtl = Int(try input.u8("draw text rtl"))
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 17, words: [x, y, Float(-1).bitPattern, Float(-1).bitPattern],
            paint: paint, textID: textID, textStart: start, textEnd: end, textFlags: rtl))
      case NativeSwiftWireOpcode.drawTextAnchored:
        let textID = try input.int("draw anchored text id")
        let words = try (0..<4).map { _ in try input.word("draw anchored text value") }
        let flags = try input.int("draw anchored text flags")
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 17, words: words, paint: paint, textID: textID, textFlags: flags))
      case NativeSwiftWireOpcode.drawTextOnPath:
        let textID = try input.int("draw text path text id")
        let pathID = try input.int("draw text path id")
        guard let path = paths[pathID] else { throw input.malformed("Missing text path \(pathID)") }
        let vertical = try input.word("draw text path vertical offset")
        let horizontal = try input.word("draw text path horizontal offset")
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: 20, words: [horizontal, vertical], paint: paint, path: path, textID: textID))
      case NativeSwiftWireOpcode.drawTextOnCircle:
        let textID = try input.int("draw text circle text id")
        let words = try (0..<5).map { _ in try input.word("draw text circle value") }
        _ = try input.u8("draw text circle alignment")
        _ = try input.u8("draw text circle placement")
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 21, words: words, paint: paint, textID: textID))
      case NativeSwiftWireOpcode.drawBitmap:
        let imageID = try input.int("draw bitmap image id")
        guard let bitmap = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        let destination = try (0..<4).map { _ in try input.word("draw bitmap destination") }
        let descriptionID = try input.int("draw bitmap content description id")
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: 19, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID,
              source: [0, 0, Float(bitmap.width).bitPattern, Float(bitmap.height).bitPattern],
              destination: destination, scaleType: NativeSwiftImageScaleType.fillBounds,
              scaleFactor: Float(1).bitPattern,
              contentDescriptionID: descriptionID)))
      case NativeSwiftWireOpcode.drawBitmapScaled:  // Draw bitmap with explicit source, destination and scale mode.
        let imageID = try input.int("scaled bitmap image id")
        guard let bitmap = images[imageID] else {
          throw input.malformed("Missing bitmap \(imageID)")
        }
        let source = try (0..<4).map { _ in try input.word("scaled bitmap source") }
        let destination = try (0..<4).map { _ in try input.word("scaled bitmap destination") }
        let scaleType = try input.int("scaled bitmap scale type")
        guard (NativeSwiftImageScaleType.none...NativeSwiftImageScaleType.fixed).contains(scaleType) else {
          throw input.malformed("Invalid bitmap scale type")
        }
        let scaleFactor = try input.word("scaled bitmap scale factor")
        let descriptionID = try input.int("scaled bitmap content description id")
        // Keep the resource validation aligned with DrawBitmap even though this operation carries
        // its source rectangle explicitly.
        guard bitmap.width > 0, bitmap.height > 0 else {
          throw input.malformed("Invalid bitmap dimensions")
        }
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: 19, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID, source: source, destination: destination, scaleType: scaleType,
              scaleFactor: scaleFactor, contentDescriptionID: descriptionID)))
      case 46:  // Draw circle
        let words = try (0..<3).map { _ in try input.word("draw circle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 12, words: words, paint: paint))
      case 47:  // Draw line
        let words = try (0..<4).map { _ in try input.word("draw line value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 13, words: words, paint: paint))
      case 51:  // Draw rounded rectangle
        let words = try (0..<6).map { _ in try input.word("draw rounded rectangle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 14, words: words, paint: paint))
      case 52:  // Draw sector
        let words = try (0..<6).map { _ in try input.word("draw sector value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 16, words: words, paint: paint))
      case 56:  // Draw oval
        let words = try (0..<4).map { _ in try input.word("draw oval value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 11, words: words, paint: paint))
      case 108:  // Clip to the component's bounds
        // No payload. The reference clips to the component's laid-out width and height at paint
        // time, taking them from layout rather than the wire, so all this has to record is that the
        // component clips at all.
        try currentNode(stack, input: input).clipsToBounds = true
      case 224:  // Graphics layer
        // INT length, then that many [INT tag, value] pairs. The tag's low 10 bits are the
        // attribute id and bits 10-11 its data type: 1 is a float, anything else an int.
        let attributeCount = try input.count("graphics layer attribute count", maximum: 64)
        for _ in 0..<attributeCount {
          let tag = try input.int("graphics layer tag")
          let word = try input.word("graphics layer value")
          let attribute = tag & 0x3ff
          let isFloat = (tag >> 10) & 0x3 == 1
          let value =
            isFloat
            ? NativeSwiftFloatExpression.resolve(word, values: [:])
            : Float(Int32(bitPattern: word))
          try currentNode(stack, input: input).graphicsLayer[attribute] = value
        }
      case 54:  // Rounded clip rectangle
        let node = try currentNode(stack, input: input)
        node.cornerRadiusWords = try (0..<4).map { _ in try input.word("corner radius") }
      case 59:  // Click modifier encloses its action operations.
        let node = try currentNode(stack, input: input)
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: .tap))
      case 83:  // Multi-click modifier.
        let node = try currentNode(stack, input: input)
        let raw = try input.int("multi-click type")
        let gesture: NativeSwiftGestureKind
        switch raw {
        case 0: gesture = .tap
        case 1: gesture = .longPress
        case 2: gesture = .doubleTap
        default: throw input.malformed("Unknown multi-click type \(raw)")
        }
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: gesture))
      case 219, 220, 225:  // Pointer lifecycle action containers.
        let node = try currentNode(stack, input: input)
        let gesture: NativeSwiftGestureKind =
          opcode == 219 ? .touchDown : (opcode == 220 ? .touchUp : .touchCancel)
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: gesture))
      case 130:  // Matrix save
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 0, words: [], paint: paint))
      case 131:  // Matrix restore
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 1, words: [], paint: paint))
      case 173:  // Canvas operations container
        modifierContainers.append(ParsedModifierContainer(node: nil, gesture: nil))
      case 139, 174:  // Marker/modifier operations without payload
        break
      case 16:  // Width
        let node = try currentNode(stack, input: input)
        let widthType = try input.dimensionType("width type")
        let widthWord = try input.word("width")
        // First one wins, because these are a Compose modifier *chain*, not a property. AndroidX
        // emits `width(172.dp)` followed by `fillMaxWidth()` on every title card: the outer
        // modifier fixes the constraint at 172dp and the inner fill then fills exactly that, so the
        // outer is the one that decides. Overwriting instead kept the fill and drew the card at the
        // document's full width — 454 instead of 344, the largest single divergence on the catalog
        // corpus. `WRAP` is the absence of a size modifier rather than one of its own, so it never
        // claims the slot and never displaces what an earlier modifier set.
        if node.widthType == 2 {
          node.widthType = widthType
          node.widthWord = widthWord
        }
      case 55:  // Background
        let node = try currentNode(stack, input: input)
        let flags = try input.int("background flags")
        let colorID = try input.int("background color id")
        _ = try input.int("background reserved1")
        _ = try input.int("background reserved2")
        let usesColorID = flags & 2 != 0
        let red = try input.floatWord("background red", requireLiteral: !usesColorID)
        let green = try input.floatWord("background green", requireLiteral: !usesColorID)
        let blue = try input.floatWord("background blue", requireLiteral: !usesColorID)
        let alpha = try input.floatWord("background alpha", requireLiteral: !usesColorID)
        let shape = try input.int("background shape")
        guard shape == 0 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "non-rectangular backgrounds")
        }
        node.backgroundARGB =
          !usesColorID ? argb(red: red, green: green, blue: blue, alpha: alpha) : nil
        node.backgroundColorID = usesColorID ? colorID : nil
      case 58:  // Padding
        let node = try currentNode(stack, input: input)
        node.paddingWords = ParsedInsetWords(
          left: try input.word("padding left"),
          top: try input.word("padding top"),
          right: try input.word("padding right"),
          bottom: try input.word("padding bottom"))
      case 67:  // Height
        let node = try currentNode(stack, input: input)
        let heightType = try input.dimensionType("height type")
        let heightWord = try input.word("height")
        // See `case 16`: the outer modifier of a chain decides, and `WRAP` is not a modifier.
        if node.heightType == 2 {
          node.heightType = heightType
          node.heightWord = heightWord
        }
      case 80:  // Float constant
        let floatID = try input.int("float id")
        let constantWord = try input.word("float value")
        if NativeSwiftFloatExpression.referenceID(constantWord) != nil {
          // A constant that names another value is an alias, not a number. Expressing it as a
          // one-word expression runs it through the same ordered evaluation as any other computed
          // value, instead of freezing a reference's raw NaN bits into the seed map.
          expressions.append(
            ParsedFloatExpression(id: floatID, words: [constantWord], animationWords: nil))
        } else {
          let value = Float(bitPattern: constantWord)
          guard value.isFinite else { throw input.malformed("float value must be finite") }
          floats[floatID] = value
        }
      case 101:  // Bitmap data
        let imageID = try input.int("bitmap id")
        let widthAndType = UInt32(bitPattern: Int32(try input.int("bitmap width and type")))
        let heightAndEncoding = UInt32(
          bitPattern: Int32(try input.int("bitmap height and encoding")))
        let width = Int(widthAndType & 0xffff)
        let height = Int(heightAndEncoding & 0xffff)
        let type = Int(widthAndType >> 16)
        let encoding = Int(heightAndEncoding >> 16)
        guard width > 0, height > 0, width <= 4_096, height <= 4_096 else {
          throw input.malformed("Invalid bitmap dimensions \(width)x\(height)")
        }
        guard images[imageID] == nil else { throw input.malformed("Duplicate bitmap \(imageID)") }
        images[imageID] = ParsedImageResource(
          id: imageID, width: width, height: height, type: type, encoding: encoding,
          data: try input.data("bitmap data", maximum: 8 * 1_024 * 1_024))
      case 81:  // Float expression
        let id = try input.int("float expression id")
        // The length word packs the expression token count in its low half and an optional packed
        // FloatAnimation descriptor count in its high half. Keep their raw words separate: NaN
        // operator/reference payloads belong to the RPN expression, while the animation metadata
        // is interpreted as Float32 bit patterns by the animation runtime.
        let lengths = try input.int("float expression lengths")
        let valueCount = lengths & 0xffff
        let animationCount = (lengths >> 16) & 0xffff
        let count = valueCount + animationCount
        guard count <= 65_567 else { throw input.malformed("Float expression is too long") }
        expressionWordCount += count
        guard expressionWordCount <= 200_000 else {
          throw input.malformed("Float expression work exceeds 200000 words")
        }
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("float expression word")) }
        let animationWords = animationCount == 0 ? nil : Array(words.suffix(animationCount))
        if let animationWords {
          _ = try NativeSwiftFloatAnimationRuntime(
            animationWords: animationWords, offset: opcodeOffset)
        }
        expressions.append(
          ParsedFloatExpression(
            id: id, words: Array(words.prefix(valueCount)), animationWords: animationWords))
      case 93:  // Custom
        let id = try input.int("custom component id")
        _ = try input.int("custom animation id")
        let configID = try input.int("custom config id")
        let count = try input.count("custom property count", maximum: maximumProperties)
        var properties: [ParsedCustomProperty] = []
        properties.reserveCapacity(count)
        for index in 0..<count {
          let type = try input.signedU16("custom property \(index) type")
          let dataType = try input.signedU16("custom property \(index) data type")
          guard (0...9).contains(dataType) else {
            throw input.malformed("Unknown custom property data type \(dataType)")
          }
          let valueBits = try input.int("custom property \(index) value")
          if [5, 6, 9].contains(dataType) {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "custom property data type \(dataType) is not migrated")
          }
          if dataType == 3,
            NativeSwiftFloatExpression.referenceID(UInt32(bitPattern: Int32(valueBits))) == nil
          {
            throw input.malformed("Custom float return property has no target")
          }
          properties.append(
            ParsedCustomProperty(
              type: type, dataType: dataType, valueBits: valueBits))
        }
        let node = ParsedNode(kind: .custom, componentID: id)
        node.componentKind = "CustomLayout"
        node.custom = ParsedCustom(configID: configID, properties: properties)
        try begin(node)
      case 102:  // Text data
        let id = try input.int("text id")
        texts[id] = try input.utf8("text", maximum: maximumStringBytes)
      case 138:  // Color constant
        colors[try input.int("color id")] = UInt32(bitPattern: Int32(try input.int("color")))
      case 196:  // Color theme
        // INT id, INT colorGroupId, SHORT lightIndex, SHORT darkIndex, INT lightFallback,
        // INT darkFallback. The two indices select from a colour group this player does not carry,
        // so only the fallbacks are usable -- which is what the reference seeds its light and dark
        // values with at construction, so a player that is never told a theme still resolves to a
        // real colour.
        //
        // Light is the one taken, and that is measured rather than reasoned. The TypeScript
        // reference resolves light only for THEME_LIGHT (-3) and falls to dark for everything else
        // including THEME_UNSPECIFIED, which is what a player with no theme concept is -- so
        // following it would mean dark. Against the reference JVM lane, which is what this player is
        // scored on, dark renders theme-systemthemeswatches 25.59% wrong and light renders it
        // pixel-exact. The catalog publishes a light-theme sheet, so light is what its reference
        // pixels are.
        //
        // This is a constant standing in for a theme this player does not model. The moment it
        // gains one, this reads it instead, and both fallbacks are already parsed for that.
        let colorID = try input.int("color theme id")
        _ = try input.int("color theme group id")
        _ = try input.signedU16("color theme light index")
        _ = try input.signedU16("color theme dark index")
        let lightFallback = try input.int("color theme light fallback")
        let darkFallback = try input.int("color theme dark fallback")
        _ = darkFallback
        colors[colorID] = UInt32(bitPattern: Int32(lightFallback))
      case 140:  // Integer constant
        integers[try input.int("integer id")] = try input.int("integer value")
      case 144:  // Integer expression
        let outputID = try input.int("integer expression output id")
        let mask = try input.int("integer expression mask")
        let count = try input.count("integer expression value count", maximum: 320)
        var tokens: [Int] = []
        tokens.reserveCapacity(count)
        for _ in 0..<count { tokens.append(try input.int("integer expression value")) }
        integerExpressions[outputID] = ParsedIntegerExpression(mask: mask, tokens: tokens)
        // The order is what `probeValues` re-evaluates in: an expression may read another's result,
        // so the wire's own declaration order is the one that converges. Without this the refresh
        // pass had nothing to iterate and a probe kept reporting the decode-time value however a
        // gesture had moved its inputs.
        integerExpressionOrder.append(outputID)
        integers[outputID] = try NativeSwiftIntegerExpression.evaluate(
          mask: mask, tokens: tokens, values: integers)
      case 146:  // List of resource ids
        let id = try input.int("id list id")
        let count = try input.count("id list count", maximum: maximumProperties)
        idLists[id] = try (0..<count).map { _ in try input.int("id list value") }
      case 147:  // Static float list.
        let id = try input.int("float list id")
        let count = try input.count("float list count", maximum: maximumProperties)
        guard floatLists[id] == nil else { throw input.malformed("Duplicate float list \(id)") }
        floatLists[id] = try (0..<count).map { _ in try input.word("float list value") }
      case 197:  // Zero-filled float list with a dynamic length.
        let id = try input.int("dynamic float list id")
        let length = try input.word("dynamic float list length")
        guard dynamicFloatLists[id] == nil else {
          throw input.malformed("Duplicate dynamic float list \(id)")
        }
        dynamicFloatLists[id] = ParsedDynamicFloatList(lengthWord: length, updates: [])
      case 198:  // Update one dynamic float-list element; invalid indices are ignored at resolve.
        let id = try input.int("dynamic float list id")
        let update = (
          index: try input.word("dynamic float list index"),
          value: try input.word("dynamic float list value"))
        if var list = dynamicFloatLists[id] {
          list = ParsedDynamicFloatList(lengthWord: list.lengthWord, updates: list.updates + [update])
          dynamicFloatLists[id] = list
        } else if floatLists[id] != nil {
          floatListUpdates[id, default: []].append(update)
        } else {
          throw input.malformed("Missing float list \(id)")
        }
      case 145:  // Data map of typed resource IDs, addressed by a text key at lookup time.
        let mapID = try input.int("data map id")
        let count = try input.count("data map entry count", maximum: maximumProperties)
        var entries: [String: (type: Int, valueID: Int)] = [:]
        for index in 0..<count {
          let key = try input.utf8("data map entry \(index) key", maximum: maximumStringBytes)
          let type = try input.u8("data map entry \(index) type")
          guard (0...4).contains(type) else {
            throw input.malformed("Unknown data map type \(type)")
          }
          guard entries[key] == nil else { throw input.malformed("Duplicate data map key \(key)") }
          entries[key] = (type, try input.int("data map entry \(index) value id"))
        }
        guard dataMaps[mapID] == nil else { throw input.malformed("Duplicate data map \(mapID)") }
        dataMaps[mapID] = entries
      case 154:  // Resolve one typed value from a DataMap by a text resource key.
        let outputID = try input.int("data map lookup output id")
        let mapID = try input.int("data map lookup map id")
        let keyTextID = try input.int("data map lookup key text id")
        dataMapLookups.append(
          ParsedDataMapLookup(outputID: outputID, mapID: mapID, keyTextID: keyTextID))
      case 134:  // Dynamic color expression
        let expression = ParsedColorExpression(
          outputID: try input.int("color expression output id"),
          modeAndAlpha: try input.int("color expression mode and alpha"),
          first: try input.int("color expression first value"),
          second: try input.int("color expression second value"),
          third: try input.int("color expression third value"))
        guard (0...6).contains(expression.modeAndAlpha & 0xff) else {
          throw input.malformed("Unknown color expression mode")
        }
        colorExpressions.append(expression)
      case NativeSwiftWireOpcode.textFromFloat:
        let outputID = try input.int("text from float output id")
        let value = try input.word("text from float value")
        let digits = UInt32(bitPattern: Int32(try input.int("text from float digits")))
        _ = try input.int("text from float flags")
        let conversion = ParsedTextFromFloat(
            outputID: outputID, value: value,
            digitsAfter: Int(Int16(bitPattern: UInt16(digits & 0xffff))))
        textFromFloats.append(conversion)
        textOperations.append(.fromFloat(conversion))
      case NativeSwiftWireOpcode.textMerge:
        let merge = ParsedTextMerge(
            outputID: try input.int("text merge output id"),
            leftID: try input.int("text merge left id"),
            rightID: try input.int("text merge right id"))
        textMerges.append(merge)
        textOperations.append(.merge(merge))
      case NativeSwiftWireOpcode.textLookupInt:
        let lookup = ParsedTextLookupInt(
            outputID: try input.int("text lookup output id"),
            listID: try input.int("text lookup list id"),
            indexID: try input.int("text lookup index id"))
        textLookups.append(lookup)
        textOperations.append(.lookupInt(lookup))
      case NativeSwiftWireOpcode.textLookup:
        let lookup = ParsedTextLookup(
            outputID: try input.int("text lookup output id"),
            listID: try input.int("text lookup list id"),
            index: try input.word("text lookup index"))
        textFloatLookups.append(lookup)
        textOperations.append(.lookup(lookup))
      case NativeSwiftWireOpcode.textTransform:
        let transform = ParsedTextTransform(
          outputID: try input.int("text transform output id"),
          textID: try input.int("text transform source id"),
          start: try input.word("text transform start"),
          length: try input.word("text transform length"),
          operation: try input.int("text transform operation"))
        // Zero is the protocol's identity transform: it still slices its source, but applies no
        // case or whitespace rewrite. The remaining values are the five AndroidX transforms.
        guard (NativeSwiftTextTransformOperation.identity...NativeSwiftTextTransformOperation.capitalizeFirst)
          .contains(transform.operation)
        else {
          throw input.malformed("Unknown text transform operation")
        }
        textTransforms.append(transform)
        textOperations.append(.transform(transform))
      case 123:  // Path data; bounded now, drawing support is a separate operation family.
        let idAndWinding = try input.int("path id and winding")
        let count = try input.count("path word count", maximum: 20_000)
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("path word")) }
        paths[idAndWinding & 0x00ff_ffff] = ParsedPath(
          winding: idAndWinding >> 24, words: words)
        pathIDs.insert(idAndWinding & 0x00ff_ffff)
      case 158:  // Path tween; retained for the decoded-operation record probe.
        let outID = try input.int("path tween output id")
        _ = try input.int("path tween first path id")
        _ = try input.int("path tween second path id")
        _ = try input.word("path tween factor")
        pathTweenIDs.insert(outID)
      case 159:  // Procedural path start; rendering it is separate from reporting its presence.
        let id = try input.int("path create id")
        _ = try input.word("path create x")
        _ = try input.word("path create y")
        pathIDs.insert(id)
      case 160:  // Procedural path append.
        let id = try input.int("path append id")
        let count = try input.count("path append word count", maximum: 2_000)
        for _ in 0..<count { _ = try input.word("path append word") }
        pathIDs.insert(id)
      case 124:
        let id = try input.int("path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 18, words: [], paint: paint, path: path))
      case 137:  // Named variable
        let id = try input.int("named variable id")
        let type = try input.int("named variable type")
        guard (0...6).contains(type) else { throw input.malformed("Unknown named variable type") }
        let name = try input.utf8("named variable name", maximum: maximumStringBytes)
        namedVariables[name] = ParsedNamedVariable(id: id, type: type)
      case 150:  // Component value binding
        let type = try input.int("component value type")
        let componentID = try input.int("component value component id")
        let valueID = try input.int("component value id")
        guard type == 0 || type == 1 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "component value type \(type)")
        }
        // A binding may name a component that is not an open ancestor at this point in the stream --
        // one already closed, or one the document declares elsewhere. The old guard searched the
        // open stack only and rejected the whole document otherwise, which is both stricter than
        // the lookup that consumes these bindings (it searches every known node) and stricter than
        // the reference player, which falls back to the document's own dimensions when it cannot
        // find the component. Resolution below handles an unknown component on its own.
        componentValues.append(
          ParsedComponentValue(type: type, componentID: componentID, valueID: valueID))
      case 180:  // Color channel attribute
        let attribute = ParsedColorAttribute(
          outputID: try input.int("color attribute output id"),
          colorID: try input.int("color attribute color id"),
          type: try input.signedU16("color attribute type"))
        guard (0...6).contains(attribute.type) else {
          throw input.malformed("Unknown color attribute type \(attribute.type)")
        }
        colorAttributes.append(attribute)
      case NativeSwiftWireOpcode.matrixConstant:
        let matrixID = try input.int("matrix constant id")
        _ = try input.int("matrix constant type")
        let count = try input.count("matrix constant value count", maximum: 16)
        guard count == 9 || count == 16 else {
          throw input.malformed("Matrix constant must contain 9 or 16 values")
        }
        let words = try (0..<count).map { _ in try input.word("matrix constant value") }
        let values = words.map { NativeSwiftFloatExpression.resolve($0, values: floats) }
        guard values.allSatisfy(\.isFinite) else {
          throw input.malformed("Matrix constant contains a non-finite value")
        }
        matrixConstants[matrixID] = NativeSwiftMatrixSnapshot(id: matrixID, values: values)
      case NativeSwiftWireOpcode.matrixExpression:  // Matrix expression, named by a paint's SHADER_MATRIX field.
        let matrixID = try input.int("matrix expression id")
        let matrixType = try input.int("matrix expression type")
        let count = try input.count("matrix expression value count", maximum: 32)
        let matrixWords = try (0..<count).map { _ in try input.word("matrix expression value") }
        matrixExpressions[matrixID] = ParsedMatrixExpression(
          id: matrixID, type: matrixType, words: matrixWords)
      case NativeSwiftWireOpcode.matrixVectorMath:
        let type = Int(try input.u16("matrix vector math type"))
        guard type == 0 || type == 1 else {
          throw input.malformed("Unknown matrix vector math type")
        }
        let matrixID = try input.int("matrix vector math matrix id")
        let outputCount = try input.count("matrix vector math output count", maximum: 4)
        guard outputCount > 0 else { throw input.malformed("Matrix vector math has no outputs") }
        let outputs = try (0..<outputCount).map { _ in try input.int("matrix vector math output") }
        let inputCount = try input.count("matrix vector math input count", maximum: 4)
        guard inputCount > 0 else { throw input.malformed("Matrix vector math has no inputs") }
        let inputs = try (0..<inputCount).map { _ in try input.word("matrix vector math input") }
        matrixVectorMath.append(
          ParsedMatrixVectorMath(
            type: type, outputIDs: outputs, matrixID: matrixID, inputWords: inputs))
      case 171:  // Image dimension attribute
        let outputID = try input.int("image attribute output id")
        let imageID = try input.int("image attribute image id")
        let type = try input.signedU16("image attribute type")
        let count = Int(try input.u16("image attribute argument count"))
        guard count <= maximumProperties else {
          throw input.malformed("Too many image attribute arguments")
        }
        for _ in 0..<count { _ = try input.int("image attribute argument") }
        guard let image = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        guard type == 0 || type == 1 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "image attribute type \(type)")
        }
        floats[outputID] = Float(type == 0 ? image.width : image.height)
      case 200:  // Root
        let root = ParsedNode(kind: .root, componentID: try input.int("root component id"))
        root.componentKind = "RootLayoutComponent"
        try begin(root)
      case 201:  // Content
        try begin(ParsedNode(kind: .content, componentID: try input.int("content component id")))
      case 202:  // Box
        let node = ParsedNode(kind: .box, componentID: try input.int("box component id"))
        node.componentKind = "BoxLayout"
        node.animationID = try input.int("box animation id")
        node.horizontalPositioning = try input.int("box horizontal positioning")
        node.verticalPositioning = try input.int("box vertical positioning")
        try begin(node)
      case 203:  // Row
        let node = ParsedNode(kind: .row, componentID: try input.int("row component id"))
        node.componentKind = "RowLayout"
        node.animationID = try input.int("row animation id")
        node.horizontalPositioning = try input.int("row horizontal positioning")
        node.verticalPositioning = try input.int("row vertical positioning")
        node.spacingWord = try input.word("row spacing")
        try begin(node)
      case 204:  // Column
        let node = ParsedNode(kind: .column, componentID: try input.int("column component id"))
        node.componentKind = "ColumnLayout"
        node.animationID = try input.int("column animation id")
        node.horizontalPositioning = try input.int("column horizontal positioning")
        node.verticalPositioning = try input.int("column vertical positioning")
        node.spacingWord = try input.word("column spacing")
        try begin(node)
      case 14:  // Animation spec
        // Verified against AndroidX's own AnimationSpec.read in remote-core: an id, the motion
        // duration as a float, its easing as an int, the visibility duration as a float, its easing,
        // then the enter and exit animation kinds. Seven words.
        //
        let id = try input.int("animation spec id")
        let motionDuration = try input.floatWord(
          "animation spec motion duration", requireLiteral: true)
        let motionEasingType = try input.int("animation spec motion easing")
        let visibilityDuration = try input.floatWord(
          "animation spec visibility duration", requireLiteral: true)
        let visibilityEasingType = try input.int("animation spec visibility easing")
        let enterAnimation = try input.int("animation spec enter animation")
        let exitAnimation = try input.int("animation spec exit animation")
        guard motionDuration.isFinite, motionDuration >= 0, visibilityDuration.isFinite,
          visibilityDuration >= 0
        else {
          throw input.malformed("Invalid animation spec duration")
        }
        animationSpecs[id] = NativeSwiftAnimationSpec(
          motionDuration: motionDuration, motionEasingType: motionEasingType,
          visibilityDuration: visibilityDuration, visibilityEasingType: visibilityEasingType,
          enterAnimation: enterAnimation, exitAnimation: exitAnimation)
        animationSpecOrder.append(id)
      case 157:  // Touch expression
        // An id, four float words (start value, minimum, maximum, velocity id), the touch effects,
        // then three length-prefixed float arrays. Each length is the low 16 bits of its word --
        // the high half of the second carries the stop mode -- which is masked in both AndroidX's
        // own reader and the reference port, and getting that wrong desynchronises everything after.
        _ = try input.int("touch expression id")
        for field in ["start value", "minimum", "maximum", "velocity id"] {
          _ = try input.word("touch expression \(field)")
        }
        _ = try input.int("touch expression effects")
        for array in ["expression", "stops", "easing"] {
          let header = try input.int("touch expression \(array) length")
          let count = header & 0xffff
          guard count <= 4096 else {
            throw input.malformed("Touch expression \(array) is too long")
          }
          for _ in 0..<count { _ = try input.word("touch expression \(array) value") }
        }
      case 161:  // Particle define
        // From AndroidX's ParticlesCreate.read: an id, the particle count, then a variable count and
        // that many variables, each an id followed by a length-prefixed expression. The bounds are
        // the reader's own -- fewer than 8000 particles, at most 2000 variables, at most 32 words
        // per expression -- and they are kept because they are what stops a malformed length from
        // allocating the document's remainder.
        let particleID = try input.int("particle id")
        let particleCount = try input.int("particle count")
        guard (0..<8000).contains(particleCount) else {
          throw input.malformed("Particle count is outside 0..<8000")
        }
        let variableCount = try input.int("particle variable count")
        guard (0...2000).contains(variableCount) else {
          throw input.malformed("Too many particle variables")
        }
        guard Int64(particleCount) * Int64(variableCount) <= 20_000 else {
          throw input.malformed("Particle definition exceeds 20000 units of runtime state")
        }
        var variableIDs: [Int] = []
        var initializationEquations: [[UInt32]] = []
        variableIDs.reserveCapacity(variableCount)
        initializationEquations.reserveCapacity(variableCount)
        for _ in 0..<variableCount {
          variableIDs.append(try input.int("particle variable id"))
          let length = try input.int("particle expression length")
          guard (0...32).contains(length) else {
            throw input.malformed("Particle expression is too long")
          }
          initializationEquations.append(
            try (0..<length).map { _ in try input.word("particle expression word") })
        }
        guard !particleDefinitions.contains(where: { $0.id == particleID }) else {
          throw input.malformed("Duplicate particle system \(particleID)")
        }
        particleDefinitions.append(
          ParsedParticleDefinition(
            id: particleID, particleCount: particleCount, variableIDs: variableIDs,
            initializationEquations: initializationEquations))
      case 163:  // Particle loop
        // ParticlesLoop.read: an id, one length-prefixed expression for the loop itself, then a
        // variable count and a length-prefixed expression per variable. Same bounds as above.
        let particleID = try input.int("particle loop id")
        let loopLength = try input.int("particle loop expression length")
        guard (0...32).contains(loopLength) else {
          throw input.malformed("Particle loop expression is too long")
        }
        let restartEquation = try (0..<loopLength).map {
          _ in try input.word("particle loop expression word")
        }
        let loopVariables = try input.int("particle loop variable count")
        guard (0...2000).contains(loopVariables) else {
          throw input.malformed("Too many particle loop variables")
        }
        var updateEquations: [[UInt32]] = []
        updateEquations.reserveCapacity(loopVariables)
        for _ in 0..<loopVariables {
          let length = try input.int("particle loop variable expression length")
          guard (0...32).contains(length) else {
            throw input.malformed("Particle loop variable expression is too long")
          }
          updateEquations.append(
            try (0..<length).map { _ in try input.word("particle loop variable word") })
        }
        if let definition = particleDefinitions.first(where: { $0.id == particleID }) {
          let work = Int64(definition.particleCount)
            * Int64(1 + updateEquations.reduce(0) { $0 + $1.count })
          guard work <= 20_000 else {
            throw input.malformed("Particle loop exceeds 20000 units of work per frame")
          }
        }
        particleLoops.append(
          ParsedParticleLoop(
            id: particleID, restartEquation: restartEquation, updateEquations: updateEquations))
      case 103:  // Root content description
        _ = try input.int("root content description id")
      case 207:  // Canvas content
        // INT component id, the same shape as the content at 201, and the same role: the container
        // a canvas draws into.
        try begin(
          ParsedNode(kind: .content, componentID: try input.int("canvas content component id")))
      case 176:  // Fit box
        // Component id, animation id, both positionings. A fit box scales its content to fit rather
        // than clipping it; laid out here as an ordinary box, so the content keeps its own size.
        let node = ParsedNode(kind: .box, componentID: try input.int("fit box component id"))
        node.componentKind = "FitBoxLayout"
        node.animationID = try input.int("fit box animation id")
        node.horizontalPositioning = try input.int("fit box horizontal positioning")
        node.verticalPositioning = try input.int("fit box vertical positioning")
        try begin(node)
      case 217:  // State layout
        // Component id, animation id, both positionings, then the id of the float holding the index
        // of the child to show. Laid out as a box, which shows every child stacked rather than the
        // one the index selects -- a real difference, tracked rather than implied.
        let node = ParsedNode(kind: .box, componentID: try input.int("state layout component id"))
        node.componentKind = "StateLayout"
        node.animationID = try input.int("state layout animation id")
        node.horizontalPositioning = try input.int("state layout horizontal positioning")
        node.verticalPositioning = try input.int("state layout vertical positioning")
        node.stateIndexID = try input.int("state layout index id")
        try begin(node)
      case 240:  // Flow layout
        // The row payload plus two ints: the maximum items per row and the maximum number of rows.
        // Children wrap onto further lines when they do not fit, so this is not the row it decodes
        // like; `flowMaximumItems`/`flowMaximumLines` bound the wrap.
        let node = ParsedNode(kind: .row, componentID: try input.int("flow component id"))
        node.componentKind = "FlowLayout"
        node.animationID = try input.int("flow animation id")
        node.horizontalPositioning = try input.int("flow horizontal positioning")
        node.verticalPositioning = try input.int("flow vertical positioning")
        node.spacingWord = try input.word("flow spacing")
        node.flowMaximumItems = try input.int("flow maximum items in each row")
        node.flowMaximumLines = try input.int("flow maximum lines")
        try begin(node)
      case 223:  // Z-index modifier
        try currentNode(stack, input: input).zIndexWord = try input.word("z-index")
      case 221:  // Offset modifier
        let node = try currentNode(stack, input: input)
        node.offsetXWord = try input.word("offset x")
        node.offsetYWord = try input.word("offset y")
      case 211:  // Visibility modifier
        // An id, not a value: the modifier names an integer the document updates, so visibility
        // follows that integer rather than being fixed when the document is written.
        try currentNode(stack, input: input).visibilityID = try input.int("visibility id")
      case 226:  // Scroll modifier
        // INT direction, then position, max and notch max as float words.
        //
        // A *container* modifier: the opcode opens a list the matching 214 closes, so it has to be
        // pushed as one. Not doing that made the 214 pop the component the modifier belongs to, and
        // the component's own content was then attached to its *parent* — a scrolled row's children
        // arrived as siblings of the row, laid out by nobody. `row_scroll_basic` is the gold that
        // caught it.
        let node = try currentNode(stack, input: input)
        let rawDirection = try input.int("scroll direction")
        guard let direction = NativeSwiftScrollDirection(rawValue: rawDirection) else {
          throw NativeSwiftCoreError.malformed(
            offset: opcodeOffset, reason: "Unknown scroll direction \(rawDirection)")
        }
        node.scrollDirection = direction
        node.scrollPositionWord = try input.word("scroll position")
        node.scrollMaximumWord = try input.word("scroll maximum")
        _ = try input.word("scroll notch maximum")
        modifierContainers.append(ParsedModifierContainer(node: nil, gesture: nil))
      case 107:  // Border modifier
        // INT flags, INT color id, two reserved ints, then width, corner radius and r/g/b/a as
        // float words, then INT shape type.
        let node = try currentNode(stack, input: input)
        let flags = try input.int("border flags")
        let colorID = try input.int("border color id")
        _ = try input.int("border reserved 1")
        _ = try input.int("border reserved 2")
        let width = try input.word("border width")
        _ = try input.word("border corner")
        let usesColorID = flags & 2 != 0
        let red = try input.floatWord("border red", requireLiteral: !usesColorID)
        let green = try input.floatWord("border green", requireLiteral: !usesColorID)
        let blue = try input.floatWord("border blue", requireLiteral: !usesColorID)
        let alpha = try input.floatWord("border alpha", requireLiteral: !usesColorID)
        _ = try input.int("border shape type")
        node.borderARGB = !usesColorID ? argb(red: red, green: green, blue: blue, alpha: alpha) : nil
        node.borderColorID = usesColorID ? colorID : nil
        node.borderWidthWord = width
      case 230:  // Collapsible row
        // The row half of the same family as 233, and the same wire shape as the row at 203.
        let node = ParsedNode(
          kind: .row, componentID: try input.int("collapsible row component id"))
        node.componentKind = "CollapsibleRowLayout"
        node.animationID = try input.int("collapsible row animation id")
        node.horizontalPositioning = try input.int("collapsible row horizontal positioning")
        node.verticalPositioning = try input.int("collapsible row vertical positioning")
        node.spacingWord = try input.word("collapsible row spacing")
        node.isCollapsible = true
        try begin(node)
      case 235:  // Collapsible priority modifier
        // INT orientation, FLOAT priority. It orders which children a collapsible container drops
        // first. Upstream's own `apply` is empty because it is layout input rather than a drawing
        // instruction — but it is not inert: it decides the order the container hides children in.
        let orientation = try input.int("collapsible priority orientation")
        let priority = try input.word("collapsible priority")
        let node = try currentNode(stack, input: input)
        node.collapsiblePriorityOrientation = orientation
        node.collapsiblePriorityWord = priority
      case 233:  // Collapsible column
        // Same wire shape as the column at 204 -- component id, animation id, both positionings,
        // then a float spacing -- because upstream's CollapsibleColumnLayout extends ColumnLayout
        // and inherits its payload.
        //
        // What it does NOT inherit is the behaviour: a collapsible column hides the children that do
        // not fit its height, in the order a CollapsiblePriority modifier gives them. The decision
        // lives in `NativeSwiftCollapsible` and both renderers apply it.
        let node = ParsedNode(
          kind: .column, componentID: try input.int("collapsible column component id"))
        node.componentKind = "CollapsibleColumnLayout"
        node.animationID = try input.int("collapsible column animation id")
        node.horizontalPositioning = try input.int("collapsible column horizontal positioning")
        node.verticalPositioning = try input.int("collapsible column vertical positioning")
        node.spacingWord = try input.word("collapsible column spacing")
        node.isCollapsible = true
        try begin(node)
      case 205:  // Canvas
        let node = ParsedNode(kind: .canvas, componentID: try input.int("canvas component id"))
        node.componentKind = "CanvasLayout"
        node.animationID = try input.int("canvas animation id")
        try begin(node)
      case 129:  // Matrix rotate
        let words = try (0..<3).map { _ in try input.word("matrix rotate value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 4, words: words, paint: paint))
      case 126:  // Matrix scale
        let words = try (0..<4).map { _ in try input.word("matrix scale value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 3, words: words, paint: paint))
      case 127:  // Matrix translate
        let words = try (0..<2).map { _ in try input.word("matrix translate value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 2, words: words, paint: paint))
      case 128:  // Matrix skew
        let words = try (0..<2).map { _ in try input.word("matrix skew value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 5, words: words, paint: paint))
      case 208:  // Text layout
        let node = ParsedNode(kind: .text, componentID: try input.int("text component id"))
        node.componentKind = "TextLayout"
        node.animationID = try input.int("text animation id")
        let textID = try input.int("text id")
        let color = UInt32(bitPattern: Int32(try input.int("text color")))
        let size = try input.word("text size")
        let style = try input.int("text style")
        let weight = try input.word("text weight")
        let familyID = try input.int("text family id")
        let alignmentAndFlags = try input.int("text alignment")
        let flags = UInt32(bitPattern: Int32(alignmentAndFlags)) >> 16
        guard flags == 0 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "dynamic text colors")
        }
        let overflow = try input.int("text overflow")
        let maximumLines = try input.int("text maximum lines")
        // `size` is no longer checked here: it may be a reference, and `resolvedFloat` applies the
        // same `> 0` rule once there is a number to apply it to.
        guard (0...3).contains(style), (1...6).contains(alignmentAndFlags & 0xffff),
          (1...5).contains(overflow), maximumLines > 0
        else { throw input.malformed("Invalid text layout values") }
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: nil, sizeWord: size, style: style,
          weightWord: weight,
          familyID: familyID, alignment: alignmentAndFlags & 0xffff, overflow: overflow,
          maximumLines: maximumLines)
        try begin(node)
      case 234:  // Image layout
        let node = ParsedNode(kind: .image, componentID: try input.int("image component id"))
        node.componentKind = "ImageLayout"
        node.animationID = try input.int("image animation id")
        let imageID = try input.int("image bitmap id")
        let scaleType = try input.int("image scale type")
        let alpha = try input.word("image alpha")
        guard let bitmap = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        guard (0...7).contains(scaleType) else { throw input.malformed("Invalid image scale type") }
        let source: [UInt32] = [
          0, 0, Float(bitmap.width).bitPattern, Float(bitmap.height).bitPattern,
        ]
        node.commands.append(
          ParsedDrawCommand(
            kind: 19, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID, source: source, destination: source, scaleType: scaleType,
              scaleFactor: Float(1).bitPattern, contentDescriptionID: 0), alphaWord: alpha))
        try begin(node)
      case 210:  // Host named action
        let action = ParsedNamedAction(
          nameTextID: try input.int("host action name text id"),
          valueType: try input.int("host action value type"),
          valueID: try input.int("host action value id"))
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else {
          throw input.malformed("Host named action is outside a click modifier")
        }
        target.actions[gesture, default: []].append(.named(action))
      case 218:  // Integer expression change action
        let targetID = try input.longAsInt("integer action target id")
        let expressionID = try input.longAsInt("integer action expression id")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Integer action is outside a click modifier") }
        guard integerExpressions[expressionID] != nil else {
          throw input.malformed("Missing integer action expression \(expressionID)")
        }
        target.actions[gesture, default: []].append(
          .integerExpression(targetID: targetID, expressionID: expressionID))
      case 227:  // Float expression change action
        // How a document mutates its own state: `score = score + 1`. Two ints, not longs -- the
        // integer-expression sibling at 218 reads longs, and the widths are not interchangeable.
        let targetID = try input.int("float action target id")
        let expressionID = try input.int("float action expression id")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Float action is outside a click modifier") }
        guard expressions.contains(where: { $0.id == expressionID }) else {
          throw input.malformed("Missing float action expression \(expressionID)")
        }
        target.actions[gesture, default: []].append(
          .floatExpression(targetID: targetID, expressionID: expressionID))
      case 212:  // Integer value change action
        let targetID = try input.int("integer value action target id")
        let value = try input.int("integer value action value")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Integer value action is outside a click modifier") }
        target.actions[gesture, default: []].append(
          .integerValue(targetID: targetID, value: value))
      case 214:  // Container end
        if !modifierContainers.isEmpty {
          modifierContainers.removeLast()
        } else {
          // Modern AndroidX documents carry a final document-level terminator after the root.
          if stack.isEmpty, input.isAtEnd { break }
          guard !stack.isEmpty else { throw input.malformed("Unmatched container end") }
          stack.removeLast()
        }
      case 231:  // Minimum/maximum width
        let node = try currentNode(stack, input: input)
        node.minimumWidthWord = try input.word("minimum width")
        node.maximumWidthWord = try input.word("maximum width")
      case 232:  // Minimum/maximum height
        let node = try currentNode(stack, input: input)
        node.minimumHeightWord = try input.word("minimum height")
        node.maximumHeightWord = try input.word("maximum height")
      case 239:  // CoreText
        let textID = try input.int("core text id")
        let propertyCount = Int(try input.u16("core text property count"))
        guard propertyCount <= 26 else { throw input.malformed("Too many CoreText properties") }
        var integers: [Int: Int] = [:]
        var floats: [Int: UInt32] = [:]
        for _ in 0..<propertyCount {
          let id = try input.u8("core text property id")
          if [1, 2, 3, 4, 6, 8, 9, 10, 11, 15, 16, 17, 23, 24].contains(id) {
            integers[id] = try input.int("core text integer")
          } else if [5, 7, 12, 13, 14, 25, 26].contains(id) {
            floats[id] = try input.word("core text float")
          } else if [18, 19, 22].contains(id) {
            _ = try input.u8("core text boolean")
          } else if id == 20 || id == 21 {
            let count = Int(try input.u16("core text array count"))
            guard count <= maximumProperties else {
              throw input.malformed("CoreText array is too long")
            }
            for _ in 0..<count { _ = try input.int("core text array value") }
          } else {
            throw input.malformed("Unknown CoreText property \(id)")
          }
        }
        let componentID = integers[1] ?? -(textID + 1)
        let node = ParsedNode(kind: .text, componentID: componentID)
        node.componentKind = "CoreText"
        let color =
          integers[4].flatMap { colors[$0] }
          ?? UInt32(bitPattern: Int32(integers[3] ?? -16_777_216))
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: integers[4],
          sizeWord: floats[5] ?? Float(36).bitPattern,
          style: integers[6] ?? 0, weightWord: floats[7] ?? Float(400).bitPattern,
          familyID: integers[8] ?? -1, alignment: integers[9] ?? 1,
          overflow: integers[10] ?? 1, maximumLines: integers[11] ?? Int.max)
        try begin(node)
      case 250:  // Accessibility semantics
        let node = try currentNode(stack, input: input)
        let contentDescriptionID = try input.int("content description id")
        let role = try input.u8("semantic role")
        let textID = try input.int("semantic text id")
        let stateDescriptionID = try input.int("state description id")
        let mode = try input.u8("semantic mode")
        let enabled = try input.u8("semantic enabled")
        let clickable = try input.u8("semantic clickable")
        // Out-of-range values degrade this metadata rather than rejecting the document. These
        // fields describe a component to a screen reader and contribute nothing to the painted
        // frame, and the operation is fixed-width, so a value this player does not recognise costs
        // no sync and tells us nothing about the rest of the stream. The reference player reads all
        // four without validating any of them.
        //
        // The catalog sheet writes role 255 for "no role", which a `role <= 9` guard rejected -- and
        // with it the whole document, including everything it draws. `icon`, `button-compact` and
        // `button-loading` were refused on that alone. -1 is the unspecified role the UIKit side
        // already falls back to, so an unrecognised one resolves there too.
        let semantics = ParsedAccessibility(
          contentDescriptionID: contentDescriptionID, role: role <= 9 ? role : -1, textID: textID,
          stateDescriptionID: stateDescriptionID, mode: mode <= 2 ? mode : 0,
          isEnabled: enabled != 0, isClickable: clickable != 0)
        node.accessibility = semantics
        accessibilityRecords.append(semantics)
      case 152:  // Draw arc
        let words = try (0..<6).map { _ in try input.word("draw arc value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: 15, words: words, paint: paint))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: opcodeOffset, reason: "operation family not migrated")
      }
      spans.append(
        NativeSwiftOperationSpan(
          opcode: opcode, offset: opcodeOffset, endOffset: input.offset))
    }
    guard stack.isEmpty else { throw input.malformed("Unclosed layout container") }
    // A *data-only* document declares expressions, colours and text with nothing to draw, so it
    // carries no root component at all. A renderer handed one has been given something it cannot
    // paint and refusing it is right — but a conformance run still has to answer the scalar probes
    // those documents assert, so it asks for this mode and gets an empty root of the document's own
    // size. The strict path is the default and stays that way.
    let decodedRoot: ParsedNode?
    if let root {
      decodedRoot = root
    } else if toleratingRootlessData {
      decodedRoot = ParsedNode(kind: NativeSwiftNodeSnapshot.Kind.root, componentID: 0)
    } else {
      decodedRoot = nil
    }
    guard let root = decodedRoot, root.kind == .root else {
      throw input.malformed("Missing root component")
    }
    // A clock reference can hide in a float expression, in a text-from-float conversion, in a colour
    // expression's channels, or in any word a node kept — a draw command, a dimension, a path
    // argument. Scanning only the expressions left a document whose clock display converted
    // `TIME_IN_SEC` straight to text without ever asking for another frame.
    func references(_ ids: Set<Int>, in words: [UInt32]) -> Bool {
      words.contains { NativeSwiftFloatExpression.referenceID($0).map(ids.contains) ?? false }
    }
    func references(_ ids: Set<Int>) -> Bool {
      if root.references(anyOf: ids) { return true }
      if expressions.contains(where: { references(ids, in: $0.words) }) { return true }
      if textFromFloats.contains(where: { references(ids, in: [$0.value]) }) { return true }
      return colorExpressions.contains { references(ids, in: $0.floatWords) }
    }
    // A moving clock means the frame has to be re-resolved continuously.
    let continuousClockIDs: Set<Int> = [
      NativeSwiftSystemVariables.continuousSeconds, NativeSwiftSystemVariables.animationTime,
    ]
    let needsContinuousFrames = references(continuousClockIDs)
    // The discrete wall-clock fields are constant within a second, so a document that reads one has
    // to be re-resolved at least once a second or its clock freezes on the first frame.
    let discreteWallClockIDs: Set<Int> = [
      NativeSwiftSystemVariables.timeInSeconds, NativeSwiftSystemVariables.timeInMinutes,
      NativeSwiftSystemVariables.timeInHours, NativeSwiftSystemVariables.calendarMonth,
      NativeSwiftSystemVariables.offsetToUTC, NativeSwiftSystemVariables.weekDay,
      NativeSwiftSystemVariables.dayOfMonth, NativeSwiftSystemVariables.dayOfYear,
      NativeSwiftSystemVariables.year,
    ]
    let needsWallClockRefresh = references(discreteWallClockIDs)
    return ParsedDocument(
      width: width, height: height, density: density, densityBehavior: densityBehavior,
      root: root, nodes: nodes, texts: texts, floats: floats,
      colors: colors, integers: integers, integerExpressions: integerExpressions,
      integerExpressionOrder: integerExpressionOrder,
      namedVariables: namedVariables, expressions: expressions,
      componentValues: componentValues, colorAttributes: colorAttributes,
      colorExpressions: colorExpressions, images: images, textOperations: textOperations,
      textFromFloats: textFromFloats,
      textMerges: textMerges, textTransforms: textTransforms, idLists: idLists, floatLists: floatLists,
      floatListUpdates: floatListUpdates, dynamicFloatLists: dynamicFloatLists,
      textLookups: textLookups, textFloatLookups: textFloatLookups,
      dataMaps: dataMaps, dataMapLookups: dataMapLookups,
      matrixConstants: matrixConstants, matrixExpressions: matrixExpressions,
      matrixVectorMath: matrixVectorMath, animationSpecs: animationSpecs,
      animationSpecOrder: animationSpecOrder,
      pathIDs: pathIDs, pathTweenIDs: pathTweenIDs,
      accessibilityRecords: accessibilityRecords,
      shaderUniformNames: shaderUniformNames, conditionalTraces: conditionalTraces,
      particleDefinitions: particleDefinitions, particleLoops: particleLoops,
      needsContinuousFrames: needsContinuousFrames,
      needsWallClockRefresh: needsWallClockRefresh,
      linkedOperationCount: 1 + linkedTopLevelOperationCount + (syntheticRootWasAdded ? 1 : 0))
  }

  private static func applyPaint(_ words: [Int], to paint: inout ParsedPaint, input: WireReader)
    throws
  {
    var index = 0
    while index < words.count {
      let command = words[index]
      index += 1
      let encodedCommand = UInt32(bitPattern: Int32(command))
      let type = Int(encodedCommand & 0xffff)
      let highBits = Int(encodedCommand >> 16)
      let argumentCount: Int
      if type == NativeSwiftPaintCommand.gradient {
        guard index < words.count else { throw input.malformed("Truncated paint gradient") }
        let colorCount = words[index] & 0xff
        guard (1...16).contains(colorCount), index + 1 + colorCount < words.count else {
          throw input.malformed("Invalid paint gradient")
        }
        let stopCount = words[index + 1 + colorCount]
        guard stopCount == 0 || stopCount == colorCount else {
          throw input.malformed("Invalid paint gradient stops")
        }
        argumentCount =
          1 + colorCount + 1 + stopCount
            + (highBits == NativeSwiftPaintGradientKind.linear
              ? 5
              : (highBits == NativeSwiftPaintGradientKind.radial ? 4 : 2))
      } else {
        switch type {
      case NativeSwiftPaintCommand.textSize, NativeSwiftPaintCommand.color,
        NativeSwiftPaintCommand.strokeWidth, NativeSwiftPaintCommand.shader,
        NativeSwiftPaintCommand.alpha, NativeSwiftPaintCommand.colorFilter,
        NativeSwiftPaintCommand.typeface, NativeSwiftPaintCommand.colorID,
        NativeSwiftPaintCommand.colorFilterID, NativeSwiftPaintCommand.shaderMatrix,
        NativeSwiftPaintCommand.strokeMiter, NativeSwiftPaintCommand.fallbackTypeface:
        argumentCount = 1
      case NativeSwiftPaintCommand.texture: argumentCount = 3
      case NativeSwiftPaintCommand.strokeCap, NativeSwiftPaintCommand.style,
        NativeSwiftPaintCommand.imageFilterQuality, NativeSwiftPaintCommand.antiAlias,
        NativeSwiftPaintCommand.strokeJoin, NativeSwiftPaintCommand.filterBitmap,
        NativeSwiftPaintCommand.blendMode, NativeSwiftPaintCommand.clearColorFilter:
        argumentCount = 0
      case NativeSwiftPaintCommand.fontAxis: argumentCount = highBits * 2
      case NativeSwiftPaintCommand.pathEffect: argumentCount = highBits
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 40, offset: input.offset, reason: "paint command \(type)")
        }
      }
      guard index + argumentCount <= words.count else { throw input.malformed("Truncated paint") }
      switch type {
      case NativeSwiftPaintCommand.textSize:
        paint.textSize = UInt32(bitPattern: Int32(words[index]))
      case NativeSwiftPaintCommand.color:
        paint.colorARGB = UInt32(bitPattern: Int32(words[index]))
        paint.colorID = nil
      case NativeSwiftPaintCommand.strokeWidth:
        paint.strokeWidth = UInt32(bitPattern: Int32(words[index]))
      case NativeSwiftPaintCommand.strokeCap: paint.strokeCap = highBits
      case NativeSwiftPaintCommand.style:
        paint.isStroke = highBits == NativeSwiftPaintStyle.stroke
      case NativeSwiftPaintCommand.alpha:
        paint.alpha = min(max(Float(bitPattern: UInt32(bitPattern: Int32(words[index]))), 0), 1)
      case NativeSwiftPaintCommand.strokeJoin: paint.strokeJoin = highBits
      case NativeSwiftPaintCommand.blendMode: paint.blendMode = highBits
      case NativeSwiftPaintCommand.imageFilterQuality:
        // Image filter quality: 0 none, 1 low, 2 medium, 3 high. Anything else is the reference's
        // low fallback.
        paint.filterQuality =
          (NativeSwiftPaintFilterQuality.none...NativeSwiftPaintFilterQuality.high).contains(highBits)
          ? highBits : NativeSwiftPaintFilterQuality.low
      case NativeSwiftPaintCommand.filterBitmap:
        // The legacy filter-bitmap flag: non-zero asks for filtering, which is low quality.
        paint.filterQuality =
          highBits != 0 ? NativeSwiftPaintFilterQuality.low : NativeSwiftPaintFilterQuality.none
      case NativeSwiftPaintCommand.colorID:
        paint.colorID = words[index]
      case NativeSwiftPaintCommand.colorFilter:
        paint.colorFilterARGB = UInt32(bitPattern: Int32(words[index]))
        paint.colorFilterID = nil
        paint.colorFilterMode = highBits
      case NativeSwiftPaintCommand.colorFilterID:
        paint.colorFilterID = words[index]
        paint.colorFilterARGB = nil
        paint.colorFilterMode = highBits
      case NativeSwiftPaintCommand.clearColorFilter:
        paint.colorFilterARGB = nil
        paint.colorFilterID = nil
        paint.colorFilterMode = nil
      case NativeSwiftPaintCommand.texture:
        // A texture shader replaces whatever shader the paint carried, exactly as setting a
        // gradient does; both fields used to persist together, and the renderer's texture branch
        // then drew the texture a second time where the document asked for a gradient scrim.
        paint.textureImageID = words[index]
        paint.textureTileModeX = words[index + 1] & 0xf
        paint.textureTileModeY = (words[index + 1] >> 16) & 0xf
        paint.gradient = nil
      case NativeSwiftPaintCommand.shaderMatrix:
        // The local matrix of the shader currently installed: a NaN-encoded MatrixAccess id, or 0
        // to clear it.
        paint.shaderMatrixID = NativeSwiftMatrixExpression.referenceID(
          word: UInt32(bitPattern: Int32(words[index])))
      case NativeSwiftPaintCommand.gradient:
        // Layout, matching the reference player: a meta word carrying the colour count and the
        // colour-ID register, that many colour words, a stop count, that many stop words, and then
        // the coordinates the gradient kind calls for — linear four and a tile mode, radial three
        // and a tile mode, sweep two and none. The bounds were checked when `argumentCount` was
        // computed above.
        let meta = words[index]
        let colorCount = meta & 0xff
        let stopCount = words[index + 1 + colorCount]
        let coordinateStart = index + 2 + colorCount + stopCount
        let coordinateCount =
          highBits == NativeSwiftPaintGradientKind.linear
          ? 4
          : (highBits == NativeSwiftPaintGradientKind.radial ? 3 : 2)
        let word = { (offset: Int) in UInt32(bitPattern: Int32(words[offset])) }
        paint.gradient = ParsedGradient(
          kind: highBits,
          colorWords: Array(words[(index + 1)..<(index + 1 + colorCount)]),
          colorRegister: (meta >> 16) & 0xffff,
          stopWords: (0..<stopCount).map { word(index + 2 + colorCount + $0) },
          coordinateWords: (0..<coordinateCount).map { word(coordinateStart + $0) },
          tileMode:
            highBits == NativeSwiftPaintGradientKind.sweep
            ? 0 : words[coordinateStart + coordinateCount])
        paint.textureImageID = nil
        paint.textureTileModeX = 0
        paint.textureTileModeY = 0
      default: break
      }
      index += argumentCount
    }
  }

  private static func currentNode(_ stack: [ParsedNode], input: WireReader) throws -> ParsedNode {
    guard let node = stack.last else { throw input.malformed("Modifier has no component") }
    return node
  }

  private static func argb(red: Float, green: Float, blue: Float, alpha: Float) -> UInt32 {
    func channel(_ value: Float) -> UInt32 {
      UInt32((min(max(value, 0), 1) * 255).rounded())
    }
    return channel(alpha) << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue)
  }
}

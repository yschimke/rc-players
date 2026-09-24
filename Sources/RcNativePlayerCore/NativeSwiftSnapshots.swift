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

/// Path data's command tokens: every value AndroidX's `PathData` defines, NaN-boxed in the stream.
public enum NativeSwiftPathCommand {
  public static let move = 10
  public static let line = 11
  public static let quadratic = 12
  public static let conic = 13
  public static let cubic = 14
  public static let close = 15
  public static let done = 16
  public static let reset = 17
}

/// A path command token as the wire spells it: the command id in a quiet NaN's payload.
func pathCommandWord(_ command: Int) -> UInt32 {
  0x7fc0_0000 | UInt32(command)
}

/// The themes a host can request and a `THEME` operation can scope to: every value AndroidX defines.
public enum NativeSwiftTheme {
  /// Follow the platform's own light or dark setting.
  public static let system = 0
  public static let unspecified = -1
  public static let dark = -2
  public static let light = -3
}

/// AndroidX's `StringUtils.floatToString`, as `TEXT_FROM_FLOAT` formats a number: padding before
/// and after the point, grouping, separators, rounding and sign options, and the legacy mode.
///
/// A port of the CMP player's `RcTextFormatter`, which a compatibility test holds to AndroidX's own
/// Java utility; the arithmetic is kept in `Float` because the reference's is.
public enum NativeSwiftTextFormatter {
  /// `TEXT_FROM_FLOAT`'s flag bits: every one AndroidX defines.
  public enum Flag {
    public static let padAfterSpace = 0
    public static let padAfterNone = 1
    public static let padAfterZero = 3
    public static let padBeforeSpace = 0
    public static let padBeforeNone = 4
    public static let padBeforeZero = 12
    public static let groupingNone = 0
    public static let groupingBy3 = 1 << 4
    public static let groupingBy4 = 2 << 4
    public static let groupingBy32 = 3 << 4
    public static let separatorCommaPeriod = 0
    public static let separatorPeriodComma = 1 << 6
    public static let separatorSpaceComma = 2 << 6
    public static let separatorUnderscorePeriod = 3 << 6
    public static let optionsNegativeParentheses = 1 << 8
    public static let optionsRounding = 2 << 8
    public static let legacyMode = 1 << 10
    public static let fullFormat = 1 << 12

    /// The two-bit fields the values above occupy.
    static let padAfterMask = 3
    static let padBeforeMask = 3 << 2
    static let groupingMask = 3 << 4
    static let separatorMask = 3 << 6
    static let optionsMask = 3 << 8
  }

  public static func format(
    _ input: Float, digitsBefore: Int, digitsAfter: Int, flags: Int
  ) -> String {
    if flags & Flag.fullFormat != 0 { return javaFloatString(input) }
    let post: Character? =
      switch flags & Flag.padAfterMask {
      case Flag.padAfterNone: nil
      case Flag.padAfterZero: "0"
      default: " "
      }
    let pre: Character? =
      switch flags & Flag.padBeforeMask {
      case Flag.padBeforeNone: nil
      case Flag.padBeforeZero: "0"
      default: " "
      }
    if flags & Flag.legacyMode != 0 {
      return legacy(input, before: digitsBefore, after: digitsAfter, pre: pre, post: post)
    }
    return modern(
      input, before: digitsBefore, requestedAfter: digitsAfter, pre: pre, post: post,
      separator: flags & Flag.separatorMask, grouping: flags & Flag.groupingMask,
      options: flags & Flag.optionsMask)
  }

  private static func legacy(
    _ input: Float, before: Int, after: Int, pre: Character?, post: Character?
  ) -> String {
    var value = input
    let negative = value < 0
    if negative { value = -value }
    let integer = pad(String(javaInt(value)), to: before, with: pre)
    if after == 0 { return (negative ? "-" : "") + integer }
    var text = fractionDigits(value, digits: after, keep: after)
    while text.last == "0" { text.removeLast() }
    if let post, text.count < after { text += String(repeating: post, count: after - text.count) }
    return (negative ? "-" : "") + integer + "." + text
  }

  private static func modern(
    _ input: Float, before: Int, requestedAfter: Int, pre: Character?, post: Character?,
    separator: Int, grouping: Int, options: Int
  ) -> String {
    let separators: (group: Character, decimal: Character) =
      switch separator {
      case Flag.separatorPeriodComma: (".", ",")
      case Flag.separatorSpaceComma: (" ", ",")
      case Flag.separatorUnderscorePeriod: ("_", ".")
      default: (",", ".")  // Flag.separatorCommaPeriod
      }
    var value = input
    let negative = value < 0
    if negative { value = -value }
    let raw = characters(
      value, before: before, after: requestedAfter, rounding: options & Flag.optionsRounding != 0)
    let grouped = group(
      String(raw.prefix { $0 != "." }), grouping: grouping, separator: separators.group)
    let integerLength = grouped.count
    let integer = pad(grouped, to: before, with: pre)
    let trimAfter =
      integerLength + requestedAfter > 9 ? max(1, 9 - integerLength) : requestedAfter
    let after = post == nil ? trimAfter : requestedAfter
    let parentheses = options & Flag.optionsNegativeParentheses != 0
    if after == 0 { return sign(integer, negative: negative, parentheses: parentheses) }
    var text = fractionDigits(value, digits: trimAfter, keep: after)
    while text.count > 1, text.last == "0" { text.removeLast() }
    if let post, text.count < after { text += String(repeating: post, count: after - text.count) }
    return sign(
      integer + String(separators.decimal) + text, negative: negative, parentheses: parentheses)
  }

  /// The reference rounds the fraction to `digits` places in Float arithmetic, prints the Float
  /// and keeps up to `keep` characters after its "0.". A negative count repeats nothing, as
  /// Kotlin's `repeat` does; where the reference's `substring` would then throw, this keeps none.
  private static func fractionDigits(_ value: Float, digits: Int, keep: Int) -> String {
    var fraction = value.truncatingRemainder(dividingBy: 1)
    for _ in 0..<max(digits, 0) { fraction *= 10 }
    fraction = Float(javaRound(fraction))
    for _ in 0..<max(digits, 0) { fraction *= 0.1 }
    let text = Array(javaFloatString(fraction))
    let end = min(text.count, keep + 2)
    guard end > 2 else { return "" }
    return String(text[2..<end])
  }

  private static func pad(_ text: String, to width: Int, with character: Character?) -> String {
    if text.count < width, let character {
      return String(repeating: character, count: width - text.count) + text
    }
    if text.count > width { return String(text.suffix(max(width, 0))) }
    return text
  }

  private static func group(_ value: String, grouping: Int, separator: Character) -> String {
    guard grouping != Flag.groupingNone else { return value }
    var result = Array(value)
    let step = grouping == Flag.groupingBy4 ? 4 : grouping == Flag.groupingBy32 ? 2 : 3
    var index = value.count - (grouping == Flag.groupingBy4 ? 4 : 3)
    while index > 0 {
      result.insert(separator, at: index)
      index -= step
    }
    return String(result)
  }

  private static func sign(_ value: String, negative: Bool, parentheses: Bool) -> String {
    !negative ? value : parentheses ? "(\(value))" : "-\(value)"
  }

  private static func characters(_ value: Float, before: Int, after: Int, rounding: Bool) -> String {
    var adjusted = value
    // Kotlin's Long arithmetic wraps; a document asking for 19 or more places must not trap here.
    var power: Int64 = 1
    for _ in 0..<max(after, 0) { power = power &* 10 }
    if rounding {
      var factor: Float = 0.5
      for _ in 0..<max(after, 0) { factor /= 10 }
      adjusted += factor
    }
    let integer = javaLong(adjusted)
    let integerText = String(String(integer).suffix(max(min(before, String(integer).count), 0)))
    var fractional = javaLong((adjusted - Float(integer)) * Float(power))
    var fractionLength = 0
    if after > 0 {
      if fractional == 0 {
        fractionLength = 1
      } else {
        var trimmed = fractional
        while trimmed > 0, trimmed % 10 == 0 { trimmed /= 10 }
        while trimmed > 0 {
          trimmed /= 10
          fractionLength += 1
        }
      }
    }
    let count = min(after, fractionLength)
    var digits = [Character](repeating: "0", count: max(count, 0))
    for index in stride(from: count - 1, through: 0, by: -1) {
      // `'0' + remainder`, as the reference writes it: a wrapped, negative power gives a negative
      // remainder and so a character below '0', never a trap.
      digits[index] = Character(UnicodeScalar(UInt8(48 + Int(fractional % 10))))
      fractional /= 10
    }
    return integerText + "." + String(digits)
  }

  /// Java's `Math.round`-style `floor(x + 0.5)`, as the reference writes it.
  private static func javaRound(_ value: Float) -> Int { javaInt((value + 0.5).rounded(.down)) }

  /// Java's float-to-int conversion: truncating, saturating, NaN to 0.
  private static func javaInt(_ value: Float) -> Int {
    guard !value.isNaN else { return 0 }
    return Int(max(min(value, Float(Int32.max)), Float(Int32.min)).rounded(.towardZero))
  }

  private static func javaLong(_ value: Float) -> Int64 {
    guard !value.isNaN else { return 0 }
    if value >= Float(Int64.max) { return Int64.max }
    if value <= Float(Int64.min) { return Int64.min }
    return Int64(value.rounded(.towardZero))
  }

  /// Java's `Float.toString`: the shortest round-tripping digits (which Swift's `description` also
  /// finds), spelled as Java does. Fixed notation for magnitudes in [1e-3, 1e7), always with a
  /// fractional digit; otherwise one leading digit and an unsigned-if-positive `E` exponent.
  static func javaFloatString(_ value: Float) -> String {
    if value.isNaN { return "NaN" }
    if value.isInfinite { return value < 0 ? "-Infinity" : "Infinity" }
    if value == 0 { return value.sign == .minus ? "-0.0" : "0.0" }
    var text = "\(value.magnitude)"
    var exponent = 0
    if let marker = text.firstIndex(where: { $0 == "e" || $0 == "E" }) {
      exponent = Int(text[text.index(after: marker)...]) ?? 0
      text = String(text[..<marker])
    }
    let point = text.firstIndex(of: ".").map { text.distance(from: text.startIndex, to: $0) }
    var digits = Array(text.filter { $0 != "." })
    // The decimal exponent of the digit string read as 0.d1d2d3...
    var decimalExponent = (point ?? digits.count) + exponent
    while digits.count > 1, digits.first == "0" {
      digits.removeFirst()
      decimalExponent -= 1
    }
    while digits.count > 1, digits.last == "0" { digits.removeLast() }
    let sign = value < 0 ? "-" : ""
    let magnitude = value.magnitude
    if magnitude >= 1e-3, magnitude < 1e7 {
      if decimalExponent <= 0 {
        return sign + "0." + String(repeating: "0", count: -decimalExponent) + String(digits)
      }
      let integerCount = min(decimalExponent, digits.count)
      let integer =
        String(digits[..<integerCount])
        + String(repeating: "0", count: decimalExponent - integerCount)
      let fraction = integerCount < digits.count ? String(digits[integerCount...]) : "0"
      return sign + integer + "." + fraction
    }
    let fraction = digits.count > 1 ? String(digits[1...]) : "0"
    return sign + String(digits[0]) + "." + fraction + "E" + String(decimalExponent - 1)
  }
}

/// One conditional container as evaluated while linking a document.
@_spi(Conformance) public struct NativeSwiftConditionalTraceSnapshot: Sendable {
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
  @_spi(Conformance) public var animationSpecOrder: [Int] { conformanceAnimationSpecOrder }
  /// Path resource ids declared by the document, including procedural path construction.
  @_spi(Conformance) public var pathIDs: Set<Int> { conformancePathIDs }
  /// Output ids declared by `PATH_TWEEN` operations.
  @_spi(Conformance) public var pathTweenIDs: Set<Int> { conformancePathTweenIDs }
  /// Every declared accessibility operation, including root-attached and repeated modifiers.
  public let accessibilityRecords: [NativeSwiftAccessibilitySnapshot]
  /// Runtime shader uniform names keyed by shader id. Values remain unobserved by design.
  @_spi(Conformance) public var shaderUniformNames: [Int: Set<String>] {
    conformanceShaderUniformNames
  }
  /// Conditional containers evaluated while linking the document.
  @_spi(Conformance) public var conditionalTraces: [NativeSwiftConditionalTraceSnapshot] {
    conformanceConditionalTraces
  }
  /// Every `IMPULSE_START` in declaration order, with its window resolved for this frame.
  public let impulses: [NativeSwiftImpulseSnapshot]
  /// When the document asked to be resolved again, in seconds from this frame: the shortest
  /// `WAKE_IN`, an impulse still waiting for its window, or 0 for one inside it. Nil when nothing
  /// asked.
  public let wakeAfter: TimeInterval?
  /// Seconds since this session was first painted, which is the clock AndroidX's marquee runs on:
  /// it latches the wall clock at the first paint rather than reading the document's animation
  /// time. Zero on the first frame.
  public let marqueeElapsedSeconds: TimeInterval

  // Storage for the conformance-only records above. They are `@_spi(Conformance)` computed
  // properties over internal storage rather than SPI stored properties, so the struct's layout
  // stays independent of which clients import the SPI.
  let conformanceAnimationSpecOrder: [Int]
  let conformancePathIDs: Set<Int>
  let conformancePathTweenIDs: Set<Int>
  let conformanceShaderUniformNames: [Int: Set<String>]
  let conformanceConditionalTraces: [NativeSwiftConditionalTraceSnapshot]

  /// The wake a host should schedule: the document's own request, or the once-a-second refresh a
  /// document reading a discrete wall-clock field needs, whichever comes first.
  public var hostWakeAfter: TimeInterval? {
    [needsWallClockRefresh ? 1 : nil, wakeAfter].compactMap { $0 }.min()
  }
}

/// One `IMPULSE_START`: a window of `duration` seconds opening at `startAt` on the animation clock.
public struct NativeSwiftImpulseSnapshot: Equatable, Sendable {
  public let duration: Float
  public let startAt: Float
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
  /// The `AnimationSpec` this component adopts: the last one among its own operations, as the
  /// reference binds a spec by position rather than by id. Nil when it has none and so uses the
  /// default.
  public let animationSpecID: Int?
  /// The spec adopted under `animationSpecID`, as this component read it.
  public let animationSpec: NativeSwiftAnimationSpec?
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
  /// The marquee modifier this component carries, or nil. A marquee is a scroll the clock drives:
  /// like a scroll it moves the paint, not the layout, and the corpus reads its offset as `scroll_x`.
  public let marquee: NativeSwiftMarqueeSnapshot?
  public let text: NativeSwiftTextSnapshot?
  public let custom: NativeSwiftCustomSnapshot?
}

/// The 2D subset of MODIFIER_GRAPHICS_LAYER this player applies. Rotation about X and Y, Z
/// translation, camera distance, shadow elevation and blur are parsed and deliberately not applied:
/// nothing on the catalog sheet uses them, and a wrong 3D transform is worse than an absent one.
///
/// Each field is read from the id AndroidX's `GraphicsLayerModifierOperation` gives it
/// (`RcGraphicsLayerModifier` in `rc-player-protocol`): scale 0/1, rotation Z 4, transform origin
/// 5/6, translation 7/8 and alpha 11.
public struct NativeSwiftGraphicsLayerSnapshot: Sendable, Equatable {
  public let scaleX: Float
  public let scaleY: Float
  public let translationX: Float
  public let translationY: Float
  public let rotationZ: Float
  public let alpha: Float
  /// The pivot for scale and rotation as a fraction of the component's width: 0 is the left edge,
  /// 1 the right. An absent attribute is the centre, 0.5 — `RcGraphicsLayerValues` in
  /// `rc-player-runtime` explains why that, and not the declared `0f`, is what AndroidX draws.
  public let transformOriginX: Float
  /// The pivot for scale and rotation as a fraction of the component's height.
  public let transformOriginY: Float

  /// A layer pivoting about its centre.
  public init(
    scaleX: Float, scaleY: Float, translationX: Float, translationY: Float, rotationZ: Float,
    alpha: Float
  ) {
    self.init(
      scaleX: scaleX, scaleY: scaleY, translationX: translationX, translationY: translationY,
      rotationZ: rotationZ, alpha: alpha, transformOriginX: 0.5, transformOriginY: 0.5)
  }

  public init(
    scaleX: Float, scaleY: Float, translationX: Float, translationY: Float, rotationZ: Float,
    alpha: Float, transformOriginX: Float, transformOriginY: Float
  ) {
    self.scaleX = scaleX
    self.scaleY = scaleY
    self.translationX = translationX
    self.translationY = translationY
    self.rotationZ = rotationZ
    self.alpha = alpha
    self.transformOriginX = transformOriginX
    self.transformOriginY = transformOriginY
  }

  /// True when the layer changes nothing. The transform origin is not consulted: a pivot moves
  /// nothing without a scale or rotation to apply about it.
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
@_spi(Conformance) public struct NativeSwiftOperationSpan: Sendable, Equatable {
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
@_spi(Conformance) public struct NativeSwiftParticleSystemSnapshot: Sendable, Equatable {
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
  /// Positions in `values` that hold NaN as the wire's own "no value" sentinel rather than a
  /// number: `DrawTextAnchored`'s `panY` when it is the id-0 NaN, which the reference reads as
  /// "leave the baseline where it is".
  public let unsetValueIndices: [Int]

  /// The values a host validates as geometry: every value except those sentinels. A NaN anywhere
  /// else is still a document error.
  public var geometryValues: [Float] {
    guard !unsetValueIndices.isEmpty else { return values }
    return values.enumerated().compactMap { index, value in
      unsetValueIndices.contains(index) ? nil : value
    }
  }
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
/// AndroidX's `MarqueeModifierOperation`, with its float fields resolved for the frame.
///
/// A marquee does not set `needsContinuousFrames`: content that fits its box never moves, and only
/// the host's measurement knows whether it does. A host keeps the frames coming while a marquee's
/// overflow is positive, as the Compose player acquires frame demand only then.
public struct NativeSwiftMarqueeSnapshot: Equatable, Sendable {
  /// How many sweeps to run; -1 repeats forever. The reference's sinusoidal timeline ignores it.
  public let iterations: Int
  /// 0 starts immediately, 1 only while focused.
  public let animationMode: Int
  public let repeatDelayMillis: Float
  public let initialDelayMillis: Float
  /// The gap after the content before it repeats, in the document's dimension units.
  public let spacing: Float
  /// Speed in dp per second.
  public let velocity: Float

  public init(
    iterations: Int, animationMode: Int, repeatDelayMillis: Float, initialDelayMillis: Float,
    spacing: Float, velocity: Float
  ) {
    self.iterations = iterations
    self.animationMode = animationMode
    self.repeatDelayMillis = repeatDelayMillis
    self.initialDelayMillis = initialDelayMillis
    self.spacing = spacing
    self.velocity = velocity
  }

  /// The horizontal offset the content is drawn at, `elapsedSeconds` after the first paint.
  ///
  /// The reference's (`androidXMarqueeOffset` in `RcMarqueeTimeline.kt`) sinusoidal timeline:
  /// `MarqueeModifierOperation` latches its start as *first paint + initial delay*, holds still
  /// until a further initial delay has passed, and then takes its phase from that latched start —
  /// so the content rests for twice the delay, and the cycle it then joins is already one delay in.
  /// Rebasing the phase to the moment motion begins would run a delay behind AndroidX throughout.
  ///
  /// - Parameter overflowDistance: how far the content, plus `spacing`, overruns the component.
  public func offset(
    overflowDistance: Float, density: Float, elapsedSeconds: TimeInterval
  ) -> Float {
    guard overflowDistance > 0 else { return 0 }
    let initialDelaySeconds = initialDelayMillis / 1_000
    let sinceStartSeconds = Float(elapsedSeconds) - initialDelaySeconds
    guard sinceStartSeconds > initialDelaySeconds else { return 0 }
    let durationSeconds = overflowDistance / (density * velocity)
    guard durationSeconds.isFinite, durationSeconds > 0 else { return 0 }
    let phase = sinceStartSeconds.truncatingRemainder(dividingBy: durationSeconds) / durationSeconds
    return -overflowDistance * ((1 - cos(phase * 2 * Float.pi)) / 2)
  }
}

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
@_spi(Conformance) public struct NativeSwiftProbeValues: Sendable {
  public let floats: [Int: Float]
  public let integers: [Int: Int]
  public let texts: [Int: String]
  /// ARGB, as the corpus spells it: an unsigned 32-bit integer, so opaque red is 4294901760 rather
  /// than the negative Int the same bits mean here.
  public let colors: [Int: UInt32]
}

/// A matrix declaration exactly as it appeared on the wire. Matrix probes deliberately retain a
/// 3x3 declaration as nine values rather than exposing the runtime's expanded 4x4 representation.
@_spi(Conformance) public struct NativeSwiftMatrixSnapshot: Sendable {
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

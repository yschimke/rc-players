import Foundation

struct ParsedDocument {
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
  /// Epoch-millisecond instants declared with `LongConstant`.
  let longConstants: [Int: Int64]
  /// `BooleanConstant` values; retained as the reference retains them, read by nothing yet.
  let booleanConstants: [Int: Bool]
  let timeAttributes: [ParsedTimeAttribute]
  let idLookups: [ParsedIdLookup]
  let textLengths: [ParsedTextLength]
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
  let impulses: [ParsedImpulse]
  let darkColors: [Int: UInt32]
  /// `WAKE_IN` requests, as the words the document wrote.
  let wakeWords: [UInt32]
  let particleDefinitions: [ParsedParticleDefinition]
  let particleLoops: [ParsedParticleLoop]
  let needsContinuousFrames: Bool
  /// See `NativeSwiftDocumentSnapshot.needsWallClockRefresh`.
  let needsWallClockRefresh: Bool
  let linkedOperationCount: Int
  /// The opcodes of every operation on the wire, once each, header excluded.
  let operationCensus: [Int]
}

struct ParsedDynamicFloatList {
  let lengthWord: UInt32
  var updates: [(index: UInt32, value: UInt32)]
}

struct ParsedImageResource {
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

struct ParsedComponentValue {
  let type: Int
  let componentID: Int
  let valueID: Int
}

/// `ATTRIBUTE_TIME`: which clock field an attribute publishes, and from what instant.
struct ParsedTimeAttribute {
  let outputID: Int
  /// A `LongConstant` holding epoch milliseconds, or an id naming none, which means now.
  let timeID: Int
  /// The low byte of the wire's short, which is all the reference executes on.
  let type: Int
  let argumentIDs: [Int]
}

struct ParsedColorAttribute {
  let outputID: Int
  let colorID: Int
  let type: Int
}

struct ParsedColorExpression {
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
    case NativeSwiftColorExpressionMode.colorColorInterpolate...NativeSwiftColorExpressionMode.idIDInterpolate:
      return [word(third)]
    case NativeSwiftColorExpressionMode.hsv, NativeSwiftColorExpressionMode.argb:
      return [word(first), word(second), word(third)]
    case NativeSwiftColorExpressionMode.idARGB:
      return [
        word(first), word(second), word(third),
        0x7fc0_0000 | (UInt32(truncatingIfNeeded: modeAndAlpha >> 16) & 0xffff),
      ]
    default:
      return []
    }
  }
}

struct ParsedNamedVariable {
  let id: Int
  let type: Int
}

struct ParsedDataMapLookup {
  let outputID: Int
  let mapID: Int
  let keyTextID: Int
}

struct ParsedNamedAction {
  let nameTextID: Int
  let valueType: Int
  let valueID: Int
}

enum ParsedAction {
  case named(ParsedNamedAction)
  case integerExpression(targetID: Int, expressionID: Int)
  case floatExpression(targetID: Int, expressionID: Int)
  case integerValue(targetID: Int, value: Int)
  /// `VALUE_FLOAT_CHANGE_ACTION`: set a float to a (possibly computed) value.
  case floatValue(targetID: Int, value: UInt32)
  /// `VALUE_STRING_CHANGE_ACTION`: set a text to another text's value.
  case textValue(targetID: Int, textID: Int)
}

struct ParsedModifierContainer {
  let node: ParsedNode?
  let gesture: NativeSwiftGestureKind?
}

struct ParsedAccessibility {
  let contentDescriptionID: Int
  let role: Int
  let textID: Int
  let stateDescriptionID: Int
  let mode: Int
  let isEnabled: Bool
  let isClickable: Bool
}

/// `IMPULSE_START`'s two words, and which of its `IMPULSE_PROCESS` containers is the process
/// body: AndroidX runs the last child as the body when it is one, and everything else once, on the
/// first frame inside the window.
struct ParsedImpulse {
  let durationWord: UInt32
  let startAtWord: UInt32
  var processSegment: Int?
}

/// Where a draw command sits in an impulse: its setup (`segment` -1) or its n-th process container.
struct ParsedImpulseGate {
  let impulse: Int
  let segment: Int
}

/// AndroidX `ImpulseOperation`'s phase at one frame.
enum NativeSwiftImpulsePhase {
  case waiting, initialize, process, idle
}

struct ParsedDrawCommand {
  /// Set when the command is drawn by an impulse, which shows it only in the matching phase.
  var impulseGate: ParsedImpulseGate?
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
      pathWinding: path?.winding ?? NativeSwiftPathWinding.nonZero,
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
        // DrawTextRun offsets are UTF-16 code units, as in Android's String. As in the AndroidX
        // player, an end of -1 or past the text runs to its end; every other bound is clamped so
        // a malformed run selects an empty or shorter slice instead of trapping.
        let units = Array(text.utf16)
        let lower = min(max(start, 0), units.count)
        let upper =
          end == nativeSwiftDrawTextRunEndOfText || end > units.count
          ? units.count : max(end, lower)
        return String(decoding: units[lower..<upper], as: UTF16.self)
      }, textSize: NativeSwiftFloatExpression.resolve(paint.textSize, values: values),
      textFlags: textFlags)
  }
}

struct ParsedImageDraw {
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

/// The word for a literal `-1`, which is how a node says "no maximum". Spelled once so the default
/// cannot drift from the `> 1_000_000 ? -1` sentinel the snapshot still hands the layout.
let nativeSwiftNegativeOneWord = Float(-1).bitPattern

/// Padding held as it arrived on the wire. `NativeSwiftInsets` stays the resolved, public shape.
struct ParsedInsetWords {
  var left: UInt32 = 0
  var top: UInt32 = 0
  var right: UInt32 = 0
  var bottom: UInt32 = 0
}

final class ParsedNode {
  let kind: NativeSwiftNodeSnapshot.Kind
  let componentID: Int
  weak var parent: ParsedNode?
  var children: [ParsedNode] = []
  var commands: [ParsedDrawCommand] = []
  var isClickable = false
  var actions: [NativeSwiftGestureKind: [ParsedAction]] = [:]
  var accessibility: ParsedAccessibility?
  var widthType = NativeSwiftDimensionType.wrap
  var widthWord: UInt32 = 0
  var heightType = NativeSwiftDimensionType.wrap
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
  var horizontalPositioning = NativeSwiftPositioning.start
  var verticalPositioning = NativeSwiftPositioning.top
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

struct ParsedCustom {
  let configID: Int
  let properties: [ParsedCustomProperty]
}

struct ParsedCustomProperty {
  let type: Int
  let dataType: Int
  let valueBits: Int
}

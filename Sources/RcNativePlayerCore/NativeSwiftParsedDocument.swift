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

  // Per-document facts the frame resolver would otherwise recompute on every frame. They depend
  // only on what was decoded, so the decoder settles them once.

  /// `images` as snapshots in id order, shared by every frame's snapshot.
  let imageSnapshots: [NativeSwiftImageResourceSnapshot]
  /// The components `componentValues` bind, as `NativeSwiftDocumentSnapshot.boundComponents`.
  let boundComponentIDs: Set<Int>
  /// Whether the tolerant first expression pass can change what the authoritative pass computes:
  /// a component-value binding reads between them, or an expression reads an id that the same or
  /// a later expression writes. See `resolvedFloats`.
  let needsTolerantExpressionPass: Bool
  /// Whether any layout word reads a component-value binding's output, which invalidates a
  /// subtree estimate once that binding is written. See `resolvedFloats`.
  let layoutReadsComponentValues: Bool
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
  /// The `DATA_MAP_LOOKUP` operation's byte offset, for resolution failures.
  let offset: Int
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
  /// Whether a geometry word reads a component-value binding. The bindings are only all known once
  /// the whole document has decoded, so the decoder settles it then with
  /// `markComponentGeometry(_:)` rather than every frame rebuilding the set and the word list.
  private(set) var usesComponentGeometry = false
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
  /// Word positions where the id-0 NaN is a "no value" sentinel rather than a reference, and so
  /// resolves to NaN: `DrawTextAnchored`'s `panY`, which the reference reads that way to leave the
  /// baseline where it is.
  let nanSentinelIndices: Set<Int>

  init(
    kind: Int, words: [UInt32], paint: ParsedPaint, path: ParsedPath? = nil,
    image: ParsedImageDraw? = nil, alphaWord: UInt32? = nil, textID: Int? = nil,
    textStart: Int? = nil, textEnd: Int? = nil, textFlags: Int = 0,
    nanSentinelIndices: Set<Int> = []
  ) {
    self.kind = kind
    self.words = words
    self.nanSentinelIndices = nanSentinelIndices.filter {
      words.indices.contains($0) && NativeSwiftFloatExpression.referenceID(words[$0]) == 0
    }
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

  /// Decides `usesComponentGeometry` against the document's component-value output ids. The words
  /// scanned are the command's own, its path's (command tokens included, as a flat scan), its
  /// image destination and its gradient coordinates.
  mutating func markComponentGeometry(_ componentValueIDs: Set<Int>) {
    func reads(_ words: [UInt32]) -> Bool {
      words.contains { word in
        NativeSwiftFloatExpression.referenceID(word).map(componentValueIDs.contains) ?? false
      }
    }
    usesComponentGeometry =
      reads(words) || reads(path?.words ?? []) || reads(image?.destination ?? [])
      || reads(paint.gradient?.coordinateWords ?? [])
  }

  func resolve(
    values: [Int: Float], colors: [Int: UInt32], texts: [Int: String],
    matrices: [Int: ParsedMatrixExpression]
  ) throws
    -> NativeSwiftDrawCommandSnapshot
  {
    return NativeSwiftDrawCommandSnapshot(
      kind: kind,
      values: staticValues
        ?? words.enumerated().map { index, word in
          nanSentinelIndices.contains(index)
            ? .nan : NativeSwiftFloatExpression.resolve(word, values: values)
        },
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

// `ParsedNode` itself is declared in NativeSwiftDocumentDecoder.swift, beside the only code that
// may build it; see the ownership note there. What reads a node lives here.
extension ParsedNode {
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
  /// The `LAYOUT_CUSTOM` operation's byte offset, for resolution failures.
  let offset: Int
}

struct ParsedCustomProperty {
  let type: Int
  let dataType: Int
  let valueBits: Int
}

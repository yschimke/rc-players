import Foundation

let nativeSwiftMaximumStringBytes = 4_000

enum NativeSwiftDocumentDecoder {
  static let maximumStringBytes = nativeSwiftMaximumStringBytes
  private static let maximumOperations = 100_000
  private static let maximumProperties = 2_000
  private static let maximumNodes = 20_000
  private static let maximumNestingDepth = 256
  /// AndroidX's own bound on one loop's passes.
  private static let maximumLoopPasses = 10_000
  /// AndroidX's own bounds for a sound resource and a sound expression's parameters.
  private static let maximumSoundBytes = 256 * 1024
  private static let maximumSoundParameters = 64

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
    guard try input.u8("header opcode") == NativeSwiftWireOpcode.header else {
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
        case NativeSwiftHeaderValueType.int:
          guard length == 4 else { throw input.malformed("Invalid integer header property length") }
          let value = try input.int("header property \(index)")
          if key == NativeSwiftHeaderKey.documentWidth { modernWidth = value }
          if key == NativeSwiftHeaderKey.documentHeight { modernHeight = value }
          if key == NativeSwiftHeaderKey.densityBehavior { densityBehavior = value }
        case NativeSwiftHeaderValueType.float:
          guard length == 4 else { throw input.malformed("Invalid float header property length") }
          let value = Float(bitPattern: try input.word("header property \(index)"))
          if key == NativeSwiftHeaderKey.densityAtGeneration { density = value }
        case NativeSwiftHeaderValueType.long:
          guard length == 8 else { throw input.malformed("Invalid long header property length") }
          _ = try input.int("header property \(index) high word")
          _ = try input.int("header property \(index) low word")
        case NativeSwiftHeaderValueType.string:
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
      (NativeSwiftDensityBehavior.legacy...NativeSwiftDensityBehavior.dp).contains(densityBehavior)
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
    // The IDs in `expressions`, so a click action's existence check is not a scan per operation.
    var expressionIDs: Set<Int> = []
    var componentValues: [ParsedComponentValue] = []
    var colorAttributes: [ParsedColorAttribute] = []
    var longConstants: [Int: Int64] = [:]
    var booleanConstants: [Int: Bool] = [:]
    var timeAttributes: [ParsedTimeAttribute] = []
    var documentAccessibility: ParsedAccessibility?
    var idLookups: [ParsedIdLookup] = []
    var textLengths: [ParsedTextLength] = []
    /// Declared text styles by id, for `CoreText` to inherit from.
    var textStyles: [Int: ParsedTextProperties] = [:]
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
    var impulses: [ParsedImpulse] = []
    /// A `ColorTheme`'s dark fallback, by colour id; its light one seeds `colors`.
    var darkColors: [Int: UInt32] = [:]
    var wakeWords: [UInt32] = []
    /// An open impulse container: its setup (-1) or one of its process containers, closed when
    /// `modifierContainers` returns to `depth`.
    struct ImpulseScope {
      let impulse: Int
      let segment: Int
      let depth: Int
    }
    var impulseScopes: [ImpulseScope] = []
    var impulseSegmentCounts: [Int: Int] = [:]
    /// The process container an impulse's setup most recently ended with, if nothing followed it.
    var impulseTrailingProcess: [Int: Int] = [:]
    /// Nodes an operation drew into while an impulse was open, with their command count before.
    var impulseDrawTargets: [(node: ParsedNode, count: Int)] = []
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
    /// The sparse property list `TEXT_STYLE` and `CoreText` share. Booleans and font-axis arrays
    /// are read past; nothing in this core renders them yet.
    func textProperties(_ label: String) throws -> ParsedTextProperties {
      let count = Int(try input.u16("\(label) property count"))
      guard count <= 26 else { throw input.malformed("Too many \(label) properties") }
      var properties = ParsedTextProperties()
      for _ in 0..<count {
        let id = try input.u8("\(label) property id")
        typealias Property = NativeSwiftTextProperty
        if [
          Property.componentID, Property.animationID, Property.color, Property.colorID,
          Property.fontStyle, Property.fontFamily, Property.textAlign, Property.overflow,
          Property.maxLines, Property.breakStrategy, Property.hyphenationFrequency,
          Property.justificationMode, Property.flags, Property.textStyleID,
        ].contains(id) {
          properties.integers[id] = try input.int("\(label) integer")
        } else if [
          Property.fontSize, Property.fontWeight, Property.letterSpacing, Property.lineHeightAdd,
          Property.lineHeightMultiplier, Property.minFontSize, Property.maxFontSize,
        ].contains(id) {
          properties.floats[id] = try input.word("\(label) float")
        } else if [Property.underline, Property.strikethrough, Property.autosize].contains(id) {
          _ = try input.u8("\(label) boolean")
        } else if id == Property.fontAxis || id == Property.fontAxisValues {
          let count = Int(try input.u16("\(label) array count"))
          guard count <= maximumProperties else {
            throw input.malformed("\(label) array is too long")
          }
          for _ in 0..<count { _ = try input.int("\(label) array value") }
        } else {
          throw input.malformed("Unknown \(label) property \(id)")
        }
      }
      return properties
    }

    /// A style's properties with its parent chain folded in, nearest last. The identity fields —
    /// component, animation, style and parent ids — describe the declaration, not the text, and
    /// are not inherited.
    func inheritedTextProperties(
      _ styleID: Int, visiting: Set<Int> = []
    ) throws -> ParsedTextProperties {
      guard !visiting.contains(styleID) else {
        throw input.malformed("Cyclic TextStyle parent at id \(styleID)")
      }
      guard let style = textStyles[styleID] else {
        throw input.malformed("Missing TextStyle id \(styleID)")
      }
      var merged = ParsedTextProperties()
      if let parent = style.integers[NativeSwiftTextProperty.textStyleID], parent != -1 {
        merged = try inheritedTextProperties(parent, visiting: visiting.union([styleID]))
      }
      let identity = [
        NativeSwiftTextProperty.componentID, NativeSwiftTextProperty.animationID,
        NativeSwiftTextProperty.flags, NativeSwiftTextProperty.textStyleID,
      ]
      for (id, value) in style.integers where !identity.contains(id) {
        merged.integers[id] = value
      }
      merged.floats.merge(style.floats) { _, own in own }
      return merged
    }

    func drawingNode() throws -> ParsedNode {
      let node = try drawingTarget()
      if !impulseScopes.isEmpty, !impulseDrawTargets.contains(where: { $0.node === node }) {
        impulseDrawTargets.append((node: node, count: node.commands.count))
      }
      return node
    }

    func drawingTarget() throws -> ParsedNode {
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

    // Capture recurses once per nested MacroCall block, so a malformed chain of 247 -> 249 would
    // otherwise exhaust the stack. Captured operations are not charged to the operation budget
    // here: a captured body is counted when it executes, and the capture itself is bounded by the
    // input length.
    func captureMacroBody(depth: Int = 0) throws -> Data {
      guard depth < maximumNestingDepth else {
        throw input.malformed("Macro body nesting exceeds \(maximumNestingDepth)")
      }
      let start = input.offset
      var nesting = 0
      while true {
        let opcodeOffset = input.offset
        let opcode = try input.u8("macro body opcode")
        switch opcode {
        case NativeSwiftWireOpcode.containerEnd:
          if nesting == 0 { return input.rawBytes(from: start, to: opcodeOffset) }
          nesting -= 1
        case NativeSwiftWireOpcode.paintValues:
          let count = try input.count("macro paint word count", maximum: 1_024)
          for _ in 0..<count { _ = try input.int("macro paint word") }
        case NativeSwiftWireOpcode.clipPath, NativeSwiftWireOpcode.drawPath,
          NativeSwiftWireOpcode.macroArgument:
          _ = try input.int("macro clip path id")
        case NativeSwiftWireOpcode.macroCall:
          _ = try input.int("nested macro id")
          let argumentCount = try input.count("nested macro argument count", maximum: maximumProperties)
          for _ in 0..<argumentCount { _ = try input.int("nested macro argument") }
          // A MacroCall is itself a container: preserve its supplied MacroBlocks and consume its
          // matching end so the surrounding definition's end remains the capture terminator.
          while true {
            let childOpcode = try input.u8("nested macro call operation")
            if childOpcode == NativeSwiftWireOpcode.containerEnd { break }
            guard childOpcode == NativeSwiftWireOpcode.macroBlock else {
              throw NativeSwiftCoreError.unsupported(
                opcode: childOpcode, offset: input.offset - 1,
                reason: "LOOM nested macro calls only support MacroBlock children")
            }
            _ = try input.int("nested macro block index")
            _ = try captureMacroBody(depth: depth + 1)
          }
        case NativeSwiftOpcodeGroup.fourWordDraws:
          for _ in 0..<4 { _ = try input.word("macro drawing value") }
        case NativeSwiftWireOpcode.drawBitmap:
          _ = try input.int("macro bitmap id")
          for _ in 0..<4 { _ = try input.word("macro bitmap destination") }
          _ = try input.int("macro bitmap description id")
        case NativeSwiftWireOpcode.drawCircle:
          for _ in 0..<3 { _ = try input.word("macro circle value") }
        case NativeSwiftWireOpcode.conditionalOperations:
          _ = try input.u8("conditional type")
          _ = try input.word("conditional left")
          _ = try input.word("conditional right")
          nesting += 1
        case NativeSwiftOpcodeGroup.sixWordDraws:
          for _ in 0..<6 { _ = try input.word("macro drawing value") }
        case NativeSwiftWireOpcode.layoutBox:
          // BoxLayout declares both its component and animation IDs; positioning is plain data.
          for _ in 0..<4 { _ = try input.int("macro box value") }
          nesting += 1
        case NativeSwiftOpcodeGroup.matrixStack:
          break
        case NativeSwiftWireOpcode.canvasOperations:
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
        if opcode == NativeSwiftWireOpcode.containerEnd { return blocks }
        guard opcode == NativeSwiftWireOpcode.macroBlock else {
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
        case NativeSwiftWireOpcode.clipPath, NativeSwiftWireOpcode.drawPath:
          let idOffset = reader.offset
          let id = try reader.int("macro path id")
          if let replacement = mappings[id] { replaceID(at: idOffset, with: replacement) }
        case NativeSwiftWireOpcode.macroCall:
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
          guard try reader.u8("nested macro call end") == NativeSwiftWireOpcode.containerEnd else {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "LOOM nested macro-call blocks are not migrated for parameter remapping")
          }
        case NativeSwiftWireOpcode.paintValues:
          let count = try reader.count("macro paint word count", maximum: 1_024)
          for _ in 0..<count { _ = try reader.int("macro paint word") }
        case NativeSwiftWireOpcode.macroArgument:
          _ = try reader.int("macro argument index")
        case NativeSwiftOpcodeGroup.fourWordDraws:
          for _ in 0..<4 { let offset = reader.offset; try remapFloatReference(at: offset) }
        case NativeSwiftWireOpcode.drawBitmap:
          _ = try reader.int("macro bitmap id")
          for _ in 0..<4 { let offset = reader.offset; try remapFloatReference(at: offset) }
          _ = try reader.int("macro bitmap description id")
        case NativeSwiftWireOpcode.drawCircle:
          for _ in 0..<3 { let offset = reader.offset; try remapFloatReference(at: offset) }
        case NativeSwiftOpcodeGroup.sixWordDraws:
          for _ in 0..<6 { let offset = reader.offset; try remapFloatReference(at: offset) }
        case NativeSwiftWireOpcode.layoutBox:
          let componentOffset = reader.offset
          try declaredID(at: componentOffset, name: "macro box component id")
          let animationOffset = reader.offset
          try declaredID(at: animationOffset, name: "macro box animation id")
          _ = try reader.int("macro box horizontal positioning")
          _ = try reader.int("macro box vertical positioning")
        case NativeSwiftOpcodeGroup.matrixStack:
          break
        case NativeSwiftWireOpcode.containerEnd:
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
      let impulseScope = impulseScopes.last
      // `ops:count` is a census of the linked top-level tree, not decoder iterations. Containers
      // own their children; macro definitions and calls expand into those children; neither is an
      // independent top-level operation. Template bytes execute through `suspendedInputs` and are
      // likewise excluded from the source document's top-level census.
      if stack.isEmpty, modifierContainers.isEmpty, suspendedInputs.isEmpty {
        switch opcode {
        case NativeSwiftWireOpcode.layoutRoot:
          linkedTopLevelOperationCount += 1
        case NativeSwiftWireOpcode.layoutContent...NativeSwiftWireOpcode.layoutCanvas,
          NativeSwiftWireOpcode.layoutCanvasContent, NativeSwiftWireOpcode.layoutText,
          NativeSwiftWireOpcode.layoutState, NativeSwiftWireOpcode.layoutCollapsibleColumn,
          NativeSwiftWireOpcode.layoutFlow, NativeSwiftWireOpcode.macroDefine,
          NativeSwiftWireOpcode.macroCall, NativeSwiftWireOpcode.macroBlock,
          NativeSwiftWireOpcode.containerEnd:
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
        case NativeSwiftConditionalType.equal: executed = left == right
        case NativeSwiftConditionalType.notEqual: executed = left != right
        case NativeSwiftConditionalType.lessThan: executed = left < right
        case NativeSwiftConditionalType.lessThanOrEqual: executed = left <= right
        case NativeSwiftConditionalType.greaterThan: executed = left > right
        case NativeSwiftConditionalType.greaterThanOrEqual: executed = left >= right
        case NativeSwiftConditionalType.changed: executed = left != 0 || right != 0
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
      case NativeSwiftWireOpcode.componentStart:
        // Legacy ComponentStart. Retain its structure; modern documents use 200...205.
        let kind = try input.int("legacy component kind")
        let componentID = try input.int("legacy component id")
        _ = try input.word("legacy component width")
        _ = try input.word("legacy component height")
        let node = ParsedNode(kind: .box, componentID: componentID)
        node.componentKind = "LegacyComponent\(kind)"
        try begin(node)
      case NativeSwiftWireOpcode.skip:  // Parse-time conditional section skip.
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
      case NativeSwiftWireOpcode.referencedOperations:
        // ReferencedOperations definition container.
        let referenceID = try input.int("referenced operations id")
        guard referencedOperations[referenceID] == nil else {
          throw input.malformed("Duplicate referenced operations \(referenceID)")
        }
        referencedOperations[referenceID] = try captureMacroBody()
      case NativeSwiftWireOpcode.includeReferencedOperations:
        // Inline a previously captured ReferencedOperations body.
        let referenceID = try input.int("referenced operations id")
        guard let body = referencedOperations[referenceID] else {
          throw input.malformed("Missing referenced operations \(referenceID)")
        }
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("Structural expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(body)
      case NativeSwiftWireOpcode.macroForEach:
        // Expand a template body once for each ID in a DataListIds collection.
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
      case NativeSwiftWireOpcode.loopStart:
        // AndroidX's ascending loop: `index < until`, stepping by `step`, with the index variable
        // set before each pass. This core unrolls it: each pass is the body with the index bound
        // to a constant of its own, so a draw in pass n reads n rather than the final value.
        // Bounds are read when the loop is; a bound that is not known then refuses the document
        // rather than unrolling to the wrong count.
        let indexID = try input.int("loop index id")
        let words = try (0..<3).map { _ in try input.word("loop bound") }
        let body = try captureMacroBody()
        let bounds = try words.map { word -> Float in
          if let id = NativeSwiftFloatExpression.referenceID(word), floats[id] == nil {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset, reason: "loop bound \(id) is not a constant")
          }
          return NativeSwiftFloatExpression.resolve(word, values: floats)
        }
        let (from, step, until) = (bounds[0], bounds[1], bounds[2])
        guard from.isFinite, step.isFinite, until.isFinite else {
          throw input.malformed("Loop bounds and step must be finite")
        }
        var expanded = Data()
        func appendConstant(_ id: Int, _ value: Float) {
          var bytes = [UInt8(NativeSwiftWireOpcode.dataFloat)]
          for word in [UInt32(bitPattern: Int32(id)), value.bitPattern] {
            bytes += [24, 16, 8, 0].map { UInt8(truncatingIfNeeded: word >> $0) }
          }
          expanded.append(contentsOf: bytes)
        }
        if from < until {
          guard step > 0 else { throw input.malformed("Loop step must be positive") }
          var value = from
          var passes = 0
          while value < until {
            passes += 1
            guard passes <= maximumLoopPasses else {
              throw input.malformed("Loop exceeds \(maximumLoopPasses) passes")
            }
            if indexID == 0 {
              expanded.append(body)
            } else {
              guard nextMacroGeneratedID <= 0x003f_ffff else {
                throw input.malformed("LOOM macro generated-id range is exhausted")
              }
              let passID = nextMacroGeneratedID
              nextMacroGeneratedID += 1
              appendConstant(passID, value)
              expanded.append(try remappedMacroBody(body, mappings: [indexID: passID]))
            }
            let next = value + step
            guard next > value else { throw input.malformed("Loop step does not advance") }
            // After the loop the index holds the last value it was given, as in the reference.
            if indexID != 0, next >= until { appendConstant(indexID, value) }
            value = next
          }
        }
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("LOOM expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(expanded)
      case NativeSwiftWireOpcode.macroArgument:
        // Insert the block supplied to the enclosing MacroCall.
        let index = try input.int("macro argument index")
        guard let block = macroBlocks[index] else {
          throw input.malformed("Missing macro block \(index)")
        }
        guard suspendedInputs.count < maximumNestingDepth else {
          throw input.malformed("LOOM expansion nesting exceeds \(maximumNestingDepth)")
        }
        suspendedInputs.append(MacroExpansionFrame(input: input, blocks: macroBlocks))
        input = WireReader(block)
      case NativeSwiftWireOpcode.macroDefine:
        // Macro definition. Definitions are structural and do not execute their body.
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
      case NativeSwiftWireOpcode.macroCall:  // Macro call container.
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
      case NativeSwiftWireOpcode.paintValues:  // Paint data
        let count = try input.count("paint word count", maximum: 1_024)
        var words: [Int] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.int("paint word")) }
        try applyPaint(words, to: &paint, input: input)
      case NativeSwiftWireOpcode.dataShader:
        // Runtime shader resource; retain names for decoded-operation records.
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
      case NativeSwiftWireOpcode.clipPath:  // Clip path
        let id = try input.int("clip path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.clipPath, words: [], paint: paint, path: path))
      case NativeSwiftWireOpcode.clipRect:  // Clip rectangle
        let words = try (0..<4).map { _ in try input.word("clip rectangle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.clipRect, words: words, paint: paint))
      case NativeSwiftWireOpcode.drawRect:  // Draw rectangle
        let words = try (0..<4).map { _ in try input.word("draw rectangle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.rect, words: words, paint: paint))
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
          ParsedDrawCommand(
            kind: NativeSwiftDrawKind.text,
            words: [x, y, Float(-1).bitPattern, Float(-1).bitPattern],
            paint: paint, textID: textID, textStart: start, textEnd: end, textFlags: rtl))
      case NativeSwiftWireOpcode.drawTextAnchored:
        let textID = try input.int("draw anchored text id")
        let words = try (0..<4).map { _ in try input.word("draw anchored text value") }
        let flags = try input.int("draw anchored text flags")
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: NativeSwiftDrawKind.text, words: words, paint: paint, textID: textID,
            textFlags: flags))
      case NativeSwiftWireOpcode.drawTextOnPath:
        let textID = try input.int("draw text path text id")
        let pathID = try input.int("draw text path id")
        guard let path = paths[pathID] else { throw input.malformed("Missing text path \(pathID)") }
        let vertical = try input.word("draw text path vertical offset")
        let horizontal = try input.word("draw text path horizontal offset")
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: NativeSwiftDrawKind.textOnPath, words: [horizontal, vertical], paint: paint,
            path: path, textID: textID))
      case NativeSwiftWireOpcode.drawTextOnCircle:
        let textID = try input.int("draw text circle text id")
        let words = try (0..<5).map { _ in try input.word("draw text circle value") }
        _ = try input.u8("draw text circle alignment")
        _ = try input.u8("draw text circle placement")
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: NativeSwiftDrawKind.textOnCircle, words: words, paint: paint, textID: textID))
      case NativeSwiftWireOpcode.drawBitmap:
        let imageID = try input.int("draw bitmap image id")
        guard let bitmap = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        let destination = try (0..<4).map { _ in try input.word("draw bitmap destination") }
        let descriptionID = try input.int("draw bitmap content description id")
        try drawingNode().commands.append(
          ParsedDrawCommand(
            kind: NativeSwiftDrawKind.bitmap, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID,
              source: [0, 0, Float(bitmap.width).bitPattern, Float(bitmap.height).bitPattern],
              destination: destination, scaleType: NativeSwiftImageScaleType.fillBounds,
              scaleFactor: Float(1).bitPattern,
              contentDescriptionID: descriptionID)))
      case NativeSwiftWireOpcode.drawBitmapScaled:
        // Draw bitmap with explicit source, destination and scale mode.
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
            kind: NativeSwiftDrawKind.bitmap, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID, source: source, destination: destination, scaleType: scaleType,
              scaleFactor: scaleFactor, contentDescriptionID: descriptionID)))
      case NativeSwiftWireOpcode.drawCircle:  // Draw circle
        let words = try (0..<3).map { _ in try input.word("draw circle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.circle, words: words, paint: paint))
      case NativeSwiftWireOpcode.drawLine:  // Draw line
        let words = try (0..<4).map { _ in try input.word("draw line value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.line, words: words, paint: paint))
      case NativeSwiftWireOpcode.drawRoundRect:  // Draw rounded rectangle
        let words = try (0..<6).map { _ in try input.word("draw rounded rectangle value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.roundRect, words: words, paint: paint))
      case NativeSwiftWireOpcode.drawSector:  // Draw sector
        let words = try (0..<6).map { _ in try input.word("draw sector value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.sector, words: words, paint: paint))
      case NativeSwiftWireOpcode.drawOval:  // Draw oval
        let words = try (0..<4).map { _ in try input.word("draw oval value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.oval, words: words, paint: paint))
      case NativeSwiftWireOpcode.modifierClipRect:  // Clip to the component's bounds
        // No payload. The reference clips to the component's laid-out width and height at paint
        // time, taking them from layout rather than the wire, so all this has to record is that the
        // component clips at all.
        try currentNode(stack, input: input).clipsToBounds = true
      case NativeSwiftWireOpcode.modifierGraphicsLayer:  // Graphics layer
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
      case NativeSwiftWireOpcode.modifierRoundedClipRect:  // Rounded clip rectangle
        let node = try currentNode(stack, input: input)
        node.cornerRadiusWords = try (0..<4).map { _ in try input.word("corner radius") }
      case NativeSwiftWireOpcode.modifierClick:  // Click modifier encloses its action operations.
        let node = try currentNode(stack, input: input)
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: .tap))
      case NativeSwiftWireOpcode.modifierMultiClick:  // Multi-click modifier.
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
      case NativeSwiftWireOpcode.modifierTouchDown, NativeSwiftWireOpcode.modifierTouchUp,
        NativeSwiftWireOpcode.modifierTouchCancel:
        // Pointer lifecycle action containers.
        let node = try currentNode(stack, input: input)
        let gesture: NativeSwiftGestureKind =
          opcode == NativeSwiftWireOpcode.modifierTouchDown
          ? .touchDown
          : (opcode == NativeSwiftWireOpcode.modifierTouchUp ? .touchUp : .touchCancel)
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: gesture))
      case NativeSwiftWireOpcode.matrixSave:  // Matrix save
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.matrixSave, words: [], paint: paint))
      case NativeSwiftWireOpcode.matrixRestore:  // Matrix restore
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.matrixRestore, words: [], paint: paint))
      case NativeSwiftWireOpcode.canvasOperations:  // Canvas operations container
        modifierContainers.append(ParsedModifierContainer(node: nil, gesture: nil))
      case NativeSwiftWireOpcode.drawContent, NativeSwiftWireOpcode.modifierDrawContent:
        // Marker/modifier operations without payload
        break
      case NativeSwiftWireOpcode.modifierWidth:  // Width
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
        if node.widthType == NativeSwiftDimensionType.wrap {
          node.widthType = widthType
          node.widthWord = widthWord
        }
      case NativeSwiftWireOpcode.modifierBackground:  // Background
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
      case NativeSwiftWireOpcode.modifierPadding:  // Padding
        let node = try currentNode(stack, input: input)
        node.paddingWords = ParsedInsetWords(
          left: try input.word("padding left"),
          top: try input.word("padding top"),
          right: try input.word("padding right"),
          bottom: try input.word("padding bottom"))
      case NativeSwiftWireOpcode.modifierHeight:  // Height
        let node = try currentNode(stack, input: input)
        let heightType = try input.dimensionType("height type")
        let heightWord = try input.word("height")
        // See `case 16`: the outer modifier of a chain decides, and `WRAP` is not a modifier.
        if node.heightType == NativeSwiftDimensionType.wrap {
          node.heightType = heightType
          node.heightWord = heightWord
        }
      case NativeSwiftWireOpcode.dataFloat:  // Float constant
        let floatID = try input.int("float id")
        let constantWord = try input.word("float value")
        if NativeSwiftFloatExpression.referenceID(constantWord) != nil {
          // A constant that names another value is an alias, not a number. Expressing it as a
          // one-word expression runs it through the same ordered evaluation as any other computed
          // value, instead of freezing a reference's raw NaN bits into the seed map.
          expressions.append(
            ParsedFloatExpression(id: floatID, words: [constantWord], animationWords: nil))
          expressionIDs.insert(floatID)
        } else {
          let value = Float(bitPattern: constantWord)
          guard value.isFinite else { throw input.malformed("float value must be finite") }
          floats[floatID] = value
        }
      case NativeSwiftWireOpcode.dataBitmap:  // Bitmap data
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
      case NativeSwiftWireOpcode.animatedFloat:  // Float expression
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
        expressionIDs.insert(id)
      case NativeSwiftWireOpcode.layoutCustom:  // Custom
        let id = try input.int("custom component id")
        _ = try input.int("custom animation id")
        let configID = try input.int("custom config id")
        let count = try input.count("custom property count", maximum: maximumProperties)
        var properties: [ParsedCustomProperty] = []
        properties.reserveCapacity(count)
        for index in 0..<count {
          let type = try input.signedU16("custom property \(index) type")
          let dataType = try input.signedU16("custom property \(index) data type")
          guard
            (NativeSwiftCustomPropertyType.intProperty...NativeSwiftCustomPropertyType.intIDProperty)
              .contains(dataType)
          else {
            throw input.malformed("Unknown custom property data type \(dataType)")
          }
          let valueBits = try input.int("custom property \(index) value")
          if [
            NativeSwiftCustomPropertyType.intReturn, NativeSwiftCustomPropertyType.colorReturn,
            NativeSwiftCustomPropertyType.intIDProperty,
          ].contains(dataType) {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "custom property data type \(dataType) is not migrated")
          }
          if dataType == NativeSwiftCustomPropertyType.floatReturn,
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
      case NativeSwiftWireOpcode.dataText:  // Text data
        let id = try input.int("text id")
        texts[id] = try input.utf8("text", maximum: maximumStringBytes)
      case NativeSwiftWireOpcode.colorConstant:  // Color constant
        colors[try input.int("color id")] = UInt32(bitPattern: Int32(try input.int("color")))
      case NativeSwiftWireOpcode.colorTheme:  // Color theme
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
        colors[colorID] = UInt32(bitPattern: Int32(lightFallback))
        darkColors[colorID] = UInt32(bitPattern: Int32(darkFallback))
      case NativeSwiftWireOpcode.dataInt:  // Integer constant
        integers[try input.int("integer id")] = try input.int("integer value")
      case NativeSwiftWireOpcode.integerExpression:  // Integer expression
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
      case NativeSwiftWireOpcode.idList:  // List of resource ids
        let id = try input.int("id list id")
        let count = try input.count("id list count", maximum: maximumProperties)
        idLists[id] = try (0..<count).map { _ in try input.int("id list value") }
      case NativeSwiftWireOpcode.floatList:  // Static float list.
        let id = try input.int("float list id")
        let count = try input.count("float list count", maximum: maximumProperties)
        guard floatLists[id] == nil else { throw input.malformed("Duplicate float list \(id)") }
        floatLists[id] = try (0..<count).map { _ in try input.word("float list value") }
      case NativeSwiftWireOpcode.dynamicFloatList:  // Zero-filled float list with a dynamic length.
        let id = try input.int("dynamic float list id")
        let length = try input.word("dynamic float list length")
        guard dynamicFloatLists[id] == nil else {
          throw input.malformed("Duplicate dynamic float list \(id)")
        }
        dynamicFloatLists[id] = ParsedDynamicFloatList(lengthWord: length, updates: [])
      case NativeSwiftWireOpcode.updateDynamicFloatList:
        // Update one dynamic float-list element; invalid indices are ignored at resolve.
        let id = try input.int("dynamic float list id")
        let update = (
          index: try input.word("dynamic float list index"),
          value: try input.word("dynamic float list value"))
        if dynamicFloatLists[id] != nil {
          // Appended in place: rebuilding the list per update was quadratic in a long update run.
          dynamicFloatLists[id]?.updates.append(update)
        } else if floatLists[id] != nil {
          floatListUpdates[id, default: []].append(update)
        } else {
          throw input.malformed("Missing float list \(id)")
        }
      case NativeSwiftWireOpcode.idMap:
        // Data map of typed resource IDs, addressed by a text key at lookup time.
        let mapID = try input.int("data map id")
        let count = try input.count("data map entry count", maximum: maximumProperties)
        var entries: [String: (type: Int, valueID: Int)] = [:]
        for index in 0..<count {
          let key = try input.utf8("data map entry \(index) key", maximum: maximumStringBytes)
          let type = try input.u8("data map entry \(index) type")
          guard (NativeSwiftDataMapType.string...NativeSwiftDataMapType.boolean).contains(type) else {
            throw input.malformed("Unknown data map type \(type)")
          }
          guard entries[key] == nil else { throw input.malformed("Duplicate data map key \(key)") }
          entries[key] = (type, try input.int("data map entry \(index) value id"))
        }
        guard dataMaps[mapID] == nil else { throw input.malformed("Duplicate data map \(mapID)") }
        dataMaps[mapID] = entries
      case NativeSwiftWireOpcode.dataMapLookup:
        // Resolve one typed value from a DataMap by a text resource key.
        let outputID = try input.int("data map lookup output id")
        let mapID = try input.int("data map lookup map id")
        let keyTextID = try input.int("data map lookup key text id")
        dataMapLookups.append(
          ParsedDataMapLookup(outputID: outputID, mapID: mapID, keyTextID: keyTextID))
      case NativeSwiftWireOpcode.colorExpressions:  // Dynamic color expression
        let expression = ParsedColorExpression(
          outputID: try input.int("color expression output id"),
          modeAndAlpha: try input.int("color expression mode and alpha"),
          first: try input.int("color expression first value"),
          second: try input.int("color expression second value"),
          third: try input.int("color expression third value"))
        guard (NativeSwiftColorExpressionMode.colorColorInterpolate...NativeSwiftColorExpressionMode.idARGB)
          .contains(expression.modeAndAlpha & 0xff)
        else {
          throw input.malformed("Unknown color expression mode")
        }
        colorExpressions.append(expression)
      case NativeSwiftWireOpcode.textFromFloat:
        let outputID = try input.int("text from float output id")
        let value = try input.word("text from float value")
        let digits = UInt32(bitPattern: Int32(try input.int("text from float digits")))
        let flags = try input.int("text from float flags")
        let conversion = ParsedTextFromFloat(
            outputID: outputID, value: value,
            digitsBefore: Int(Int16(bitPattern: UInt16(digits >> 16))),
            digitsAfter: Int(Int16(bitPattern: UInt16(digits & 0xffff))), flags: flags)
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
      case NativeSwiftWireOpcode.dataPath:
        // Path data; bounded now, drawing support is a separate operation family.
        let idAndWinding = try input.int("path id and winding")
        let count = try input.count("path word count", maximum: 20_000)
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("path word")) }
        paths[idAndWinding & 0x00ff_ffff] = ParsedPath(
          winding: idAndWinding >> 24, words: words)
        pathIDs.insert(idAndWinding & 0x00ff_ffff)
      case NativeSwiftWireOpcode.pathTween:
        // Path tween; retained for the decoded-operation record probe.
        let outID = try input.int("path tween output id")
        _ = try input.int("path tween first path id")
        _ = try input.int("path tween second path id")
        _ = try input.word("path tween factor")
        pathTweenIDs.insert(outID)
      case NativeSwiftWireOpcode.pathCreate:
        // A path built in steps: a move to the start point, which later appends extend. It is a
        // path like any `PATH_DATA`, so drawing and text on a path can name it.
        let id = try input.int("path create id")
        let x = try input.word("path create x")
        let y = try input.word("path create y")
        paths[id] = ParsedPath(winding: 0, words: [pathCommandWord(NativeSwiftPathCommand.move), x, y])
        pathIDs.insert(id)
      case NativeSwiftWireOpcode.pathAdd:
        // Appends path commands in `PATH_DATA`'s encoding; a leading RESET empties the path first.
        let id = try input.int("path append id")
        let count = try input.count("path append word count", maximum: 2_000)
        let words = try (0..<count).map { _ in try input.word("path append word") }
        if words.first.flatMap(NativeSwiftFloatExpression.referenceID) == NativeSwiftPathCommand.reset {
          paths[id] = ParsedPath(winding: paths[id]?.winding ?? 0, words: [])
        } else {
          paths[id] = ParsedPath(
            winding: paths[id]?.winding ?? 0, words: (paths[id]?.words ?? []) + words)
        }
        pathIDs.insert(id)
      case NativeSwiftWireOpcode.drawPath:
        let id = try input.int("path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.path, words: [], paint: paint, path: path))
      case NativeSwiftWireOpcode.namedVariable:  // Named variable
        let id = try input.int("named variable id")
        let type = try input.int("named variable type")
        guard
          (NativeSwiftNamedVariableType.string...NativeSwiftNamedVariableType.floatArray).contains(type)
        else {
          throw input.malformed("Unknown named variable type")
        }
        let name = try input.utf8("named variable name", maximum: maximumStringBytes)
        namedVariables[name] = ParsedNamedVariable(id: id, type: type)
      case NativeSwiftWireOpcode.componentValue:  // Component value binding
        let type = try input.int("component value type")
        let componentID = try input.int("component value component id")
        let valueID = try input.int("component value id")
        guard
          type == NativeSwiftComponentValueType.width || type == NativeSwiftComponentValueType.height
        else {
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
      case NativeSwiftWireOpcode.attributeColor:  // Color channel attribute
        let attribute = ParsedColorAttribute(
          outputID: try input.int("color attribute output id"),
          colorID: try input.int("color attribute color id"),
          type: try input.signedU16("color attribute type"))
        guard
          (NativeSwiftColorAttributeType.hue...NativeSwiftColorAttributeType.alpha)
            .contains(attribute.type)
        else {
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
      case NativeSwiftWireOpcode.matrixExpression:
        // Matrix expression, named by a paint's SHADER_MATRIX field.
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
      case NativeSwiftWireOpcode.attributeImage:  // Image dimension attribute
        let outputID = try input.int("image attribute output id")
        let imageID = try input.int("image attribute image id")
        let type = try input.signedU16("image attribute type")
        let count = Int(try input.u16("image attribute argument count"))
        guard count <= maximumProperties else {
          throw input.malformed("Too many image attribute arguments")
        }
        for _ in 0..<count { _ = try input.int("image attribute argument") }
        guard let image = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        guard
          type == NativeSwiftImageAttributeType.width || type == NativeSwiftImageAttributeType.height
        else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "image attribute type \(type)")
        }
        floats[outputID] = Float(type == NativeSwiftImageAttributeType.width ? image.width : image.height)
      case NativeSwiftWireOpcode.layoutRoot:  // Root
        let root = ParsedNode(kind: .root, componentID: try input.int("root component id"))
        root.componentKind = "RootLayoutComponent"
        try begin(root)
      case NativeSwiftWireOpcode.layoutContent:  // Content
        try begin(ParsedNode(kind: .content, componentID: try input.int("content component id")))
      case NativeSwiftWireOpcode.layoutBox:  // Box
        let node = ParsedNode(kind: .box, componentID: try input.int("box component id"))
        node.componentKind = "BoxLayout"
        node.animationID = try input.int("box animation id")
        node.horizontalPositioning = try input.int("box horizontal positioning")
        node.verticalPositioning = try input.int("box vertical positioning")
        try begin(node)
      case NativeSwiftWireOpcode.layoutRow:  // Row
        let node = ParsedNode(kind: .row, componentID: try input.int("row component id"))
        node.componentKind = "RowLayout"
        node.animationID = try input.int("row animation id")
        node.horizontalPositioning = try input.int("row horizontal positioning")
        node.verticalPositioning = try input.int("row vertical positioning")
        node.spacingWord = try input.word("row spacing")
        try begin(node)
      case NativeSwiftWireOpcode.layoutColumn:  // Column
        let node = ParsedNode(kind: .column, componentID: try input.int("column component id"))
        node.componentKind = "ColumnLayout"
        node.animationID = try input.int("column animation id")
        node.horizontalPositioning = try input.int("column horizontal positioning")
        node.verticalPositioning = try input.int("column vertical positioning")
        node.spacingWord = try input.word("column spacing")
        try begin(node)
      case NativeSwiftWireOpcode.animationSpec:  // Animation spec
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
      case NativeSwiftWireOpcode.touchExpression:  // Touch expression
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
      case NativeSwiftWireOpcode.particleDefine:  // Particle define
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
      case NativeSwiftWireOpcode.particleLoop:  // Particle loop
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
      case NativeSwiftWireOpcode.rootContentDescription:  // Root content description
        _ = try input.int("root content description id")
      case NativeSwiftWireOpcode.layoutCanvasContent:  // Canvas content
        // INT component id, the same shape as the content at 201, and the same role: the container
        // a canvas draws into.
        try begin(
          ParsedNode(kind: .content, componentID: try input.int("canvas content component id")))
      case NativeSwiftWireOpcode.layoutFitBox:  // Fit box
        // Component id, animation id, both positionings. A fit box scales its content to fit rather
        // than clipping it; laid out here as an ordinary box, so the content keeps its own size.
        let node = ParsedNode(kind: .box, componentID: try input.int("fit box component id"))
        node.componentKind = "FitBoxLayout"
        node.animationID = try input.int("fit box animation id")
        node.horizontalPositioning = try input.int("fit box horizontal positioning")
        node.verticalPositioning = try input.int("fit box vertical positioning")
        try begin(node)
      case NativeSwiftWireOpcode.layoutState:  // State layout
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
      case NativeSwiftWireOpcode.layoutFlow:  // Flow layout
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
      case NativeSwiftWireOpcode.modifierAlignBy:  // Align-by (baseline) modifier
        // FLOAT line, INT flags. It aligns a Row child by a text baseline; a child with no text has
        // no baseline, and the reference then leaves it at the row's own vertical positioning. That
        // is all this player does with it: baseline alignment of text children is not modelled yet.
        _ = try currentNode(stack, input: input)
        _ = try input.word("align by line")
        _ = try input.int("align by flags")
      case NativeSwiftWireOpcode.clickArea:  // Legacy click area
        // INT id, INT content description id, four FLOAT bounds words, INT metadata id. A
        // pre-layout document registers these for the host to hit-test. They draw nothing, and
        // hosts do not hit-test them yet, so a click inside one is not reported.
        _ = try input.int("click area id")
        _ = try input.int("click area content description id")
        for _ in 0..<4 { _ = try input.word("click area bounds") }
        _ = try input.int("click area metadata id")
      case NativeSwiftWireOpcode.modifierZindex:  // Z-index modifier
        try currentNode(stack, input: input).zIndexWord = try input.word("z-index")
      case NativeSwiftWireOpcode.modifierOffset:  // Offset modifier
        let node = try currentNode(stack, input: input)
        node.offsetXWord = try input.word("offset x")
        node.offsetYWord = try input.word("offset y")
      case NativeSwiftWireOpcode.modifierVisibility:  // Visibility modifier
        // An id, not a value: the modifier names an integer the document updates, so visibility
        // follows that integer rather than being fixed when the document is written.
        try currentNode(stack, input: input).visibilityID = try input.int("visibility id")
      case NativeSwiftWireOpcode.modifierScroll:  // Scroll modifier
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
      case NativeSwiftWireOpcode.modifierBorder:  // Border modifier
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
      case NativeSwiftWireOpcode.layoutCollapsibleRow:  // Collapsible row
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
      case NativeSwiftWireOpcode.modifierCollapsiblePriority:  // Collapsible priority modifier
        // INT orientation, FLOAT priority. It orders which children a collapsible container drops
        // first. Upstream's own `apply` is empty because it is layout input rather than a drawing
        // instruction — but it is not inert: it decides the order the container hides children in.
        let orientation = try input.int("collapsible priority orientation")
        let priority = try input.word("collapsible priority")
        let node = try currentNode(stack, input: input)
        node.collapsiblePriorityOrientation = orientation
        node.collapsiblePriorityWord = priority
      case NativeSwiftWireOpcode.layoutCollapsibleColumn:  // Collapsible column
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
      case NativeSwiftWireOpcode.layoutCanvas:  // Canvas
        let node = ParsedNode(kind: .canvas, componentID: try input.int("canvas component id"))
        node.componentKind = "CanvasLayout"
        node.animationID = try input.int("canvas animation id")
        try begin(node)
      case NativeSwiftWireOpcode.matrixRotate:  // Matrix rotate
        let words = try (0..<3).map { _ in try input.word("matrix rotate value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.matrixRotate, words: words, paint: paint))
      case NativeSwiftWireOpcode.matrixScale:  // Matrix scale
        let words = try (0..<4).map { _ in try input.word("matrix scale value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.matrixScale, words: words, paint: paint))
      case NativeSwiftWireOpcode.matrixTranslate:  // Matrix translate
        let words = try (0..<2).map { _ in try input.word("matrix translate value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.matrixTranslate, words: words, paint: paint))
      case NativeSwiftWireOpcode.matrixSkew:  // Matrix skew
        let words = try (0..<2).map { _ in try input.word("matrix skew value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.matrixSkew, words: words, paint: paint))
      case NativeSwiftWireOpcode.layoutText:  // Text layout
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
        // An overflow outside the five AndroidX defines renders as clip there, rather than refusing
        // the document; `text_style_and_layout_text` writes 0.
        let wireOverflow = try input.int("text overflow")
        let overflow =
          (NativeSwiftTextOverflow.clip...NativeSwiftTextOverflow.middleEllipsis).contains(wireOverflow)
          ? wireOverflow : NativeSwiftTextOverflow.clip
        let maximumLines = try input.int("text maximum lines")
        // `size` is no longer checked here: it may be a reference, and `resolvedFloat` applies the
        // same `> 0` rule once there is a number to apply it to.
        guard (0...3).contains(style),
          (NativeSwiftTextAlignment.left...NativeSwiftTextAlignment.end).contains(alignmentAndFlags & 0xffff),
          maximumLines > 0
        else { throw input.malformed("Invalid text layout values") }
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: nil, sizeWord: size, style: style,
          weightWord: weight,
          familyID: familyID, alignment: alignmentAndFlags & 0xffff, overflow: overflow,
          maximumLines: maximumLines)
        try begin(node)
      case NativeSwiftWireOpcode.layoutImage:  // Image layout
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
            kind: NativeSwiftDrawKind.bitmap, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID, source: source, destination: source, scaleType: scaleType,
              scaleFactor: Float(1).bitPattern, contentDescriptionID: 0), alphaWord: alpha))
        try begin(node)
      case NativeSwiftWireOpcode.hostNamedAction:  // Host named action
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
      case NativeSwiftWireOpcode.valueIntegerExpressionChangeAction:
        // Integer expression change action
        let targetID = try input.longAsInt("integer action target id")
        let expressionID = try input.longAsInt("integer action expression id")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Integer action is outside a click modifier") }
        guard integerExpressions[expressionID] != nil || integers[expressionID] != nil else {
          throw input.malformed("Missing integer action expression \(expressionID)")
        }
        target.actions[gesture, default: []].append(
          .integerExpression(targetID: targetID, expressionID: expressionID))
      case NativeSwiftWireOpcode.valueFloatExpressionChangeAction:
        // Float expression change action
        // How a document mutates its own state: `score = score + 1`. Two ints, not longs -- the
        // integer-expression sibling at 218 reads longs, and the widths are not interchangeable.
        let targetID = try input.int("float action target id")
        let expressionID = try input.int("float action expression id")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Float action is outside a click modifier") }
        guard expressionIDs.contains(expressionID) else {
          throw input.malformed("Missing float action expression \(expressionID)")
        }
        target.actions[gesture, default: []].append(
          .floatExpression(targetID: targetID, expressionID: expressionID))
      case NativeSwiftWireOpcode.valueIntegerChangeAction:  // Integer value change action
        let targetID = try input.int("integer value action target id")
        let value = try input.int("integer value action value")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Integer value action is outside a click modifier") }
        target.actions[gesture, default: []].append(
          .integerValue(targetID: targetID, value: value))
      case NativeSwiftWireOpcode.containerEnd:  // Container end
        if !modifierContainers.isEmpty {
          if let scope = impulseScopes.last, scope.depth == modifierContainers.count {
            impulseScopes.removeLast()
            if scope.segment >= 0 {
              impulseTrailingProcess[scope.impulse] = scope.segment
            } else {
              impulses[scope.impulse].processSegment = impulseTrailingProcess[scope.impulse]
            }
          }
          modifierContainers.removeLast()
        } else {
          // Modern AndroidX documents carry a final document-level terminator after the root.
          if stack.isEmpty, input.isAtEnd { break }
          guard !stack.isEmpty else { throw input.malformed("Unmatched container end") }
          stack.removeLast()
        }
      case NativeSwiftWireOpcode.modifierWidthIn:  // Minimum/maximum width
        let node = try currentNode(stack, input: input)
        node.minimumWidthWord = try input.word("minimum width")
        node.maximumWidthWord = try input.word("maximum width")
      case NativeSwiftWireOpcode.modifierHeightIn:  // Minimum/maximum height
        let node = try currentNode(stack, input: input)
        node.minimumHeightWord = try input.word("minimum height")
        node.maximumHeightWord = try input.word("maximum height")
      case NativeSwiftWireOpcode.coreText:  // CoreText
        let textID = try input.int("core text id")
        let own = try textProperties("CoreText")
        // A named style supplies defaults; the text's own properties override them.
        var properties = ParsedTextProperties()
        if let styleID = own.integers[NativeSwiftTextProperty.textStyleID], styleID != -1 {
          properties = try inheritedTextProperties(styleID)
        }
        properties.integers.merge(own.integers) { _, own in own }
        properties.floats.merge(own.floats) { _, own in own }
        let integers = properties.integers
        let floats = properties.floats
        let componentID = integers[NativeSwiftTextProperty.componentID] ?? -(textID + 1)
        let node = ParsedNode(kind: .text, componentID: componentID)
        node.componentKind = "CoreText"
        let color =
          integers[NativeSwiftTextProperty.colorID].flatMap { colors[$0] }
          ?? UInt32(bitPattern: Int32(integers[NativeSwiftTextProperty.color] ?? -16_777_216))
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: integers[NativeSwiftTextProperty.colorID],
          sizeWord: floats[NativeSwiftTextProperty.fontSize] ?? Float(36).bitPattern,
          style: integers[NativeSwiftTextProperty.fontStyle] ?? 0,
          weightWord: floats[NativeSwiftTextProperty.fontWeight] ?? Float(400).bitPattern,
          familyID: integers[NativeSwiftTextProperty.fontFamily] ?? -1,
          alignment: integers[NativeSwiftTextProperty.textAlign] ?? NativeSwiftTextAlignment.left,
          overflow: integers[NativeSwiftTextProperty.overflow] ?? NativeSwiftTextOverflow.clip,
          maximumLines: integers[NativeSwiftTextProperty.maxLines] ?? Int.max)
        try begin(node)
      case NativeSwiftWireOpcode.accessibilitySemantics:  // Accessibility semantics
        // Outside any component the semantics describe the document itself, beside its root
        // content description; they attach to the root once there is one. Refusing them refused
        // every document that describes itself this way.
        let node = stack.last
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
        if let node { node.accessibility = semantics } else { documentAccessibility = semantics }
        accessibilityRecords.append(semantics)
      case NativeSwiftWireOpcode.rootContentBehavior:
        // How a host should scroll, align and size the root. The native hosts already fit the
        // document to their view, so the four fields are read and validated but steer nothing yet;
        // refusing the operation refused every document that declared it, drawing included.
        for field in ["scroll", "alignment", "sizing", "mode"] {
          _ = try input.int("root content behavior \(field)")
        }
      case NativeSwiftWireOpcode.dataBoolean:
        let id = try input.int("boolean constant id")
        switch try input.u8("boolean constant value") {
        case 0: booleanConstants[id] = false
        case 1: booleanConstants[id] = true
        case let value: throw input.malformed("Invalid boolean byte \(value)")
        }
      case NativeSwiftWireOpcode.dataLong:
        let id = try input.int("long constant id")
        let high = UInt64(try input.word("long constant high word"))
        let low = UInt64(try input.word("long constant low word"))
        longConstants[id] = Int64(bitPattern: high << 32 | low)
      case NativeSwiftWireOpcode.attributeTime:
        let outputID = try input.int("time attribute output id")
        let timeID = try input.int("time attribute time id")
        let type = try input.signedU16("time attribute type")
        let count = try input.signedU16("time attribute argument count")
        guard (0...32).contains(count) else {
          throw input.malformed("Invalid time argument count \(count)")
        }
        let arguments = try (0..<count).map { _ in try input.int("time attribute argument") }
        timeAttributes.append(ParsedTimeAttribute(
          outputID: outputID, timeID: timeID, type: type & 0xff, argumentIDs: arguments))
      case NativeSwiftWireOpcode.debugMessage:
        // A diagnostic for the host's log. AndroidX prints it and changes nothing else; this core
        // has no log channel, so reading it past is the whole of its effect.
        _ = try input.int("debug message text id")
        _ = try input.word("debug message value")
        _ = try input.int("debug message flags")
      case NativeSwiftWireOpcode.dataSound:
        // Sound is a host capability these players do not have. A document that carries a cue
        // still draws, silently, rather than being refused for it.
        _ = try input.int("sound data id")
        _ = try input.data("sound data", maximum: maximumSoundBytes)
      case NativeSwiftWireOpcode.playSound:
        _ = try input.int("play sound id")
      case NativeSwiftWireOpcode.soundExpression:
        _ = try input.int("sound expression id")
        for field in ["left volume", "right volume", "rate"] {
          _ = try input.word("sound expression \(field)")
        }
        let count = try input.count(
          "sound expression parameter count", maximum: maximumSoundParameters)
        for _ in 0..<count { _ = try input.word("sound expression parameter") }
      case NativeSwiftWireOpcode.impulseStart:
        // A window of time on the animation clock. Its children run once, on the first frame
        // inside the window; a trailing IMPULSE_PROCESS runs on every frame after that until the
        // window closes. Draw commands inside carry that gate; see `impulseAllows`.
        guard impulseScopes.isEmpty else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "nested impulses are not migrated")
        }
        let duration = try input.word("impulse duration")
        let startAt = try input.word("impulse start")
        impulses.append(
          ParsedImpulse(durationWord: duration, startAtWord: startAt, processSegment: nil))
        modifierContainers.append(ParsedModifierContainer(node: nil, gesture: nil))
        impulseScopes.append(
          ImpulseScope(impulse: impulses.count - 1, segment: -1, depth: modifierContainers.count))
      case NativeSwiftWireOpcode.impulseProcess:
        guard let scope = impulseScopes.last, scope.segment == -1 else {
          throw input.malformed("ImpulseProcess outside an ImpulseStart")
        }
        let segment = impulseSegmentCounts[scope.impulse, default: 0]
        impulseSegmentCounts[scope.impulse] = segment + 1
        modifierContainers.append(ParsedModifierContainer(node: nil, gesture: nil))
        impulseScopes.append(
          ImpulseScope(impulse: scope.impulse, segment: segment, depth: modifierContainers.count))
      case NativeSwiftWireOpcode.wakeIn:
        // Asks the host to resolve the document again after this many seconds.
        wakeWords.append(try input.word("wake in seconds"))
      case NativeSwiftWireOpcode.valueFloatChangeAction, NativeSwiftWireOpcode.valueStringChangeAction,
        NativeSwiftWireOpcode.hostAction, NativeSwiftWireOpcode.hostMetadataAction,
        NativeSwiftWireOpcode.hapticFeedback:
        // Actions run when the click modifier that encloses them fires. Outside one there is
        // nothing to fire them, and the reference leaves them inert rather than rejecting the
        // document. Host actions and haptics need an event the hosts do not have yet, so inside a
        // click modifier they still refuse rather than being dropped silently.
        let container = modifierContainers.reversed().first(where: { $0.node != nil })
        let action: ParsedAction?
        switch opcode {
        case NativeSwiftWireOpcode.valueFloatChangeAction:
          action = .floatValue(
            targetID: try input.int("float value action target id"),
            value: try input.word("float value action value"))
        case NativeSwiftWireOpcode.valueStringChangeAction:
          action = .textValue(
            targetID: try input.int("text value action target id"),
            textID: try input.int("text value action text id"))
        case NativeSwiftWireOpcode.hostAction:
          _ = try input.int("host action id")
          action = nil
        case NativeSwiftWireOpcode.hostMetadataAction:
          _ = try input.int("host metadata action id")
          _ = try input.int("host metadata action text id")
          action = nil
        default:
          _ = try input.int("haptic feedback type")
          action = nil
        }
        if let container, let target = container.node, let gesture = container.gesture {
          guard let action else {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "host actions and haptics need a host event")
          }
          target.actions[gesture, default: []].append(action)
        }
      case NativeSwiftWireOpcode.textStyle:
        let style = try textProperties("TextStyle")
        // A style names itself with the property that names a component in CoreText.
        guard let styleID = style.integers[NativeSwiftTextProperty.componentID] else {
          throw input.malformed("TextStyle declares no style id")
        }
        textStyles[styleID] = style
      case NativeSwiftWireOpcode.idLookup:
        idLookups.append(ParsedIdLookup(
          outputID: try input.int("id lookup output id"),
          listID: try input.int("id lookup list id"),
          index: try input.word("id lookup index")))
      case NativeSwiftWireOpcode.textLength:
        textLengths.append(ParsedTextLength(
          outputID: try input.int("text length output id"),
          textID: try input.int("text length text id")))
      case NativeSwiftWireOpcode.textSubtext:
        let subtext = ParsedTextTransform(
          outputID: try input.int("text subtext output id"),
          textID: try input.int("text subtext source id"),
          start: try input.word("text subtext start"),
          length: try input.word("text subtext length"),
          operation: NativeSwiftTextTransformOperation.identity)
        textOperations.append(.subtext(subtext))
      case NativeSwiftWireOpcode.attributeText:
        let outputID = try input.int("text attribute output id")
        let textID = try input.int("text attribute text id")
        let type = try input.signedU16("text attribute type")
        _ = try input.u16("text attribute reserved")
        // Selectors 0-5 read the text's bounds under the current paint, which is host metrics
        // this core does not have yet, and leave the output as it was; 6 is its length.
        if type & 0xff == NativeSwiftTextAttributeType.length {
          textLengths.append(ParsedTextLength(outputID: outputID, textID: textID))
        }
      case NativeSwiftWireOpcode.textMeasure:
        // Bounds under the current paint are host text metrics, which this core does not have yet.
        // The operation is read and its output left as it was, which is what the reference does
        // for a selector it cannot serve, rather than refusing everything else the document draws.
        _ = try input.int("text measure output id")
        _ = try input.int("text measure text id")
        let type = try input.int("text measure type")
        guard (NativeSwiftTextAttributeType.measureWidth...NativeSwiftTextAttributeType.measureBottom)
          .contains(type & 0xff)
        else { throw input.malformed("Unknown text measurement \(type & 0xff)") }
      case NativeSwiftWireOpcode.theme:
        // Operations after a THEME belong to that theme until the next one. A host that requests
        // no theme — the only kind this player is today — shows every theme's operations, as the
        // reference does for THEME_UNSPECIFIED, so the marker is read and scopes nothing yet.
        _ = try input.int("theme")
      case NativeSwiftWireOpcode.drawArc:  // Draw arc
        let words = try (0..<6).map { _ in try input.word("draw arc value") }
        try drawingNode().commands.append(
          ParsedDrawCommand(kind: NativeSwiftDrawKind.arc, words: words, paint: paint))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: opcodeOffset, reason: "operation family not migrated")
      }
      if let impulseScope {
        let structural = [
          NativeSwiftWireOpcode.impulseStart, NativeSwiftWireOpcode.impulseProcess,
          NativeSwiftWireOpcode.containerEnd,
        ].contains(opcode)
        if impulseScope.segment == -1, !structural {
          impulseTrailingProcess.removeValue(forKey: impulseScope.impulse)
        }
        // An impulse gates what it draws. Anything else inside one — state, layout, a wake —
        // would run on every frame here rather than only in its window, so it is refused rather
        // than run at the wrong time.
        if impulseDrawTargets.isEmpty, !structural {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset,
            reason: "only drawing is migrated inside an impulse")
        }
        let gate = ParsedImpulseGate(impulse: impulseScope.impulse, segment: impulseScope.segment)
        for target in impulseDrawTargets {
          for index in target.count..<target.node.commands.count {
            target.node.commands[index].impulseGate = gate
          }
        }
        impulseDrawTargets.removeAll()
      }
      spans.append(
        NativeSwiftOperationSpan(
          opcode: opcode, offset: opcodeOffset, endOffset: input.offset))
    }
    guard impulseScopes.isEmpty else { throw input.malformed("Unclosed impulse") }
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
    if let documentAccessibility, root.accessibility == nil {
      root.accessibility = documentAccessibility
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
    // A time attribute measured from now, or from load, moves with the clock by itself; the
    // reference asks for continuous frames for exactly those three types.
    let continuousTimeTypes: Set<Int> = [
      NativeSwiftTimeAttributeType.fromNowSeconds, NativeSwiftTimeAttributeType.fromNowMinutes,
      NativeSwiftTimeAttributeType.fromLoadSeconds,
    ]
    let needsContinuousFrames = references(continuousClockIDs)
      || timeAttributes.contains { continuousTimeTypes.contains($0.type) }
    // The discrete wall-clock fields are constant within a second, so a document that reads one has
    // to be re-resolved at least once a second or its clock freezes on the first frame.
    let discreteWallClockIDs: Set<Int> = [
      NativeSwiftSystemVariables.timeInSeconds, NativeSwiftSystemVariables.timeInMinutes,
      NativeSwiftSystemVariables.timeInHours, NativeSwiftSystemVariables.calendarMonth,
      NativeSwiftSystemVariables.offsetToUTC, NativeSwiftSystemVariables.weekDay,
      NativeSwiftSystemVariables.dayOfMonth, NativeSwiftSystemVariables.dayOfYear,
      NativeSwiftSystemVariables.year, NativeSwiftSystemVariables.epochSecond,
    ]
    let needsWallClockRefresh = references(discreteWallClockIDs)
      || timeAttributes.contains { longConstants[$0.timeID] == nil }
    return ParsedDocument(
      width: width, height: height, density: density, densityBehavior: densityBehavior,
      root: root, nodes: nodes, texts: texts, floats: floats,
      colors: colors, integers: integers, integerExpressions: integerExpressions,
      integerExpressionOrder: integerExpressionOrder,
      namedVariables: namedVariables, expressions: expressions,
      componentValues: componentValues, colorAttributes: colorAttributes,
      longConstants: longConstants, booleanConstants: booleanConstants,
      timeAttributes: timeAttributes, idLookups: idLookups, textLengths: textLengths,
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
      impulses: impulses, darkColors: darkColors, wakeWords: wakeWords,
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
          opcode: NativeSwiftWireOpcode.paintValues, offset: input.offset, reason: "paint command \(type)")
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
          // Only linear and radial reserve a tile-mode word in `argumentCount`; an unknown kind is
          // sized like a sweep, so reading one past its coordinates would index past the words.
          tileMode:
            highBits == NativeSwiftPaintGradientKind.linear
              || highBits == NativeSwiftPaintGradientKind.radial
            ? words[coordinateStart + coordinateCount] : 0)
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

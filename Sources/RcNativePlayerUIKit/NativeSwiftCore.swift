import Foundation

/// Immutable, platform-neutral output from the Swift wire/runtime path.
///
/// Keeping this model free of UIKit and foreign-runtime objects lets decoding and document state remain
/// actor-isolated while the final UIView model is materialized on the main actor.
struct NativeSwiftDocumentSnapshot: Sendable {
  let width: Int
  let height: Int
  let root: NativeSwiftNodeSnapshot
  let needsContinuousFrames: Bool
}

struct NativeSwiftNodeSnapshot: Sendable {
  enum Kind: Sendable {
    case root, content, canvas, box, row, column, text, custom
  }

  let kind: Kind
  let componentID: Int
  let children: [NativeSwiftNodeSnapshot]
  let commands: [NativeSwiftDrawCommandSnapshot]
  let isClickable: Bool
  let accessibility: NativeSwiftAccessibilitySnapshot?
  let widthType: Int
  let widthValue: Float
  let heightType: Int
  let heightValue: Float
  let padding: NativeSwiftInsets
  let minimumHeight: Float
  let cornerRadius: Float
  let backgroundARGB: UInt32?
  let horizontalPositioning: Int
  let verticalPositioning: Int
  let spacing: Float
  let text: NativeSwiftTextSnapshot?
  let custom: NativeSwiftCustomSnapshot?
}

struct NativeSwiftAccessibilitySnapshot: Sendable {
  let role: Int
  let mode: Int
  let contentDescription: String?
  let text: String?
  let stateDescription: String?
  let isEnabled: Bool
  let isClickable: Bool
}

struct NativeSwiftDrawCommandSnapshot: Sendable {
  let kind: Int
  let values: [Float]
  let colorARGB: UInt32
  let alpha: Float
  let strokeWidth: Float
  let isStroke: Bool
  let strokeCap: Int
  let strokeJoin: Int
  let blendMode: Int
  let path: [NativeSwiftPathElementSnapshot]
  let pathWinding: Int
}

struct NativeSwiftPathElementSnapshot: Sendable {
  let kind: Int
  let values: [Float]
}

enum NativeSwiftEvent: Equatable, Sendable {
  case namedAction(name: String, value: NativeSwiftActionValue)
}

enum NativeSwiftActionValue: Equatable, Sendable {
  case none
  case float(Float)
  case integer(Int)
  case text(String)
}

struct NativeSwiftInsets: Sendable {
  var left: Float = 0
  var top: Float = 0
  var right: Float = 0
  var bottom: Float = 0
}

struct NativeSwiftTextSnapshot: Sendable {
  let value: String
  let colorARGB: UInt32
  let size: Float
  let style: Int
  let weight: Float
  let familyID: Int
  let alignment: Int
  let overflow: Int
  let maximumLines: Int
}

struct NativeSwiftCustomSnapshot: Sendable {
  let config: String
  let properties: [NativeSwiftCustomPropertySnapshot]
}

struct NativeSwiftCustomPropertySnapshot: Sendable {
  let id: Int
  let dataType: Int
  let floatValue: Float
  let integerValue: Int
  let textValue: String?
}

enum NativeSwiftCoreError: Error, CustomStringConvertible {
  case unsupported(opcode: Int, offset: Int, reason: String)
  case malformed(offset: Int, reason: String)

  var isUnsupported: Bool {
    if case .unsupported = self { return true }
    return false
  }

  var description: String {
    switch self {
    case .unsupported(let opcode, let offset, let reason):
      return "Unsupported Remote Compose opcode \(opcode) at byte \(offset): \(reason)"
    case .malformed(let offset, let reason):
      return "Malformed Remote Compose document at byte \(offset): \(reason)"
    }
  }
}

/// Retained document state for the first pure-Swift operation family.
final class NativeSwiftDocumentSession: @unchecked Sendable {
  private let document: ParsedDocument
  private var texts: [Int: String]
  private var floats: [Int: Float]
  private var colors: [Int: UInt32]

  private init(document: ParsedDocument) {
    self.document = document
    texts = document.texts
    floats = document.floats
    colors = document.colors
  }

  static func open(data: Data) throws -> NativeSwiftDocumentSession {
    NativeSwiftDocumentSession(document: try NativeSwiftDocumentDecoder.decode(data))
  }

  func snapshot(timeSeconds: TimeInterval = 0) throws -> NativeSwiftDocumentSnapshot {
    let values = try resolvedFloats(timeSeconds: timeSeconds)
    return NativeSwiftDocumentSnapshot(
      width: document.width,
      height: document.height,
      root: try resolve(document.root, values: values),
      needsContinuousFrames: document.needsContinuousFrames)
  }

  func click(componentID: Int, timeSeconds: TimeInterval) throws -> [NativeSwiftEvent]? {
    guard let node = document.nodes[componentID], node.isClickable,
      node.accessibility?.isEnabled != false
    else { return nil }
    let values = try resolvedFloats(timeSeconds: timeSeconds)
    return node.actions.compactMap { action in
      guard let name = texts[action.nameTextID] else { return nil }
      let value: NativeSwiftActionValue
      switch action.valueType {
      case -1: value = .none
      case 0: value = .float(values[action.valueID] ?? 0)
      case 1: value = .integer(document.integers[action.valueID] ?? 0)
      case 2: value = .text(texts[action.valueID] ?? "")
      default: return nil
      }
      return .namedAction(name: name, value: value)
    }
  }

  func setFloat(_ value: Float, for name: String) -> Bool {
    guard value.isFinite, let variable = document.namedVariables[name], variable.type == 1 else {
      return false
    }
    floats[variable.id] = value
    return true
  }

  func setString(_ value: String, for name: String) -> Bool {
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let variable = document.namedVariables[name], variable.type == 0
    else { return false }
    texts[variable.id] = value
    return true
  }

  func setColor(_ value: UInt32, for name: String) -> Bool {
    guard let variable = document.namedVariables[name], variable.type == 2 else { return false }
    colors[variable.id] = value
    return true
  }

  func returnCustomText(_ value: String, componentID: Int, propertyID: Int) throws -> Bool {
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let node = document.nodes[componentID], node.kind == .custom,
      let property = node.custom?.properties.first(where: {
        $0.type == propertyID && $0.dataType == 4
      })
    else { return false }
    texts[property.valueBits] = value
    return true
  }

  func returnCustomFloat(_ value: Float, componentID: Int, propertyID: Int) throws -> Bool {
    guard value.isFinite, let node = document.nodes[componentID], node.kind == .custom,
      node.custom?.properties.contains(where: {
        $0.type == propertyID && $0.dataType == 3
      }) == true
    else { return false }
    // Float return storage lands in a later operation-family slice. Rejecting it is safer than
    // claiming an update whose dependent expressions cannot yet be resolved by this runtime.
    return false
  }

  private func resolve(_ node: ParsedNode, values: [Int: Float]) throws -> NativeSwiftNodeSnapshot {
    let text: NativeSwiftTextSnapshot?
    if let source = node.text {
      text = NativeSwiftTextSnapshot(
        value: texts[source.textID] ?? "",
        colorARGB: source.colorID.flatMap { colors[$0] } ?? source.colorARGB,
        size: source.size,
        style: source.style,
        weight: min(max(source.weight, 1), 1_000),
        familyID: source.familyID,
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
          ? Float(bitPattern: UInt32(bitPattern: Int32(property.valueBits))) : 0
        let integerValue: Int
        let textValue: String?
        switch property.dataType {
        case 2:
          integerValue = property.valueBits
          textValue = texts[property.valueBits]
        case 7:
          integerValue = Int(Int32(bitPattern: colors[property.valueBits] ?? 0))
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

    return NativeSwiftNodeSnapshot(
      kind: node.kind,
      componentID: node.componentID,
      children: try node.children.map { try resolve($0, values: values) },
      commands: try node.commands.map { try $0.resolve(values: values, colors: colors) },
      isClickable: node.isClickable,
      accessibility: node.accessibility.map {
        NativeSwiftAccessibilitySnapshot(
          role: $0.role, mode: $0.mode,
          contentDescription: texts[$0.contentDescriptionID], text: texts[$0.textID],
          stateDescription: texts[$0.stateDescriptionID], isEnabled: $0.isEnabled,
          isClickable: $0.isClickable)
      },
      widthType: node.widthType,
      widthValue: node.widthValue,
      heightType: node.heightType,
      heightValue: node.heightValue,
      padding: node.padding,
      minimumHeight: node.minimumHeight,
      cornerRadius: node.cornerRadius,
      backgroundARGB: node.backgroundColorID.flatMap { colors[$0] } ?? node.backgroundARGB,
      horizontalPositioning: node.horizontalPositioning,
      verticalPositioning: node.verticalPositioning,
      spacing: node.spacing,
      text: text,
      custom: custom)
  }

  private func resolvedFloats(timeSeconds: TimeInterval) throws -> [Int: Float] {
    var result = floats
    for binding in document.componentValues {
      guard let node = document.nodes[binding.componentID] else { continue }
      let measuredNode = node.parent ?? node
      result[binding.valueID] = estimatedDimension(
        of: measuredNode, type: binding.type,
        available: binding.type == 0 ? Float(document.width) : Float(document.height))
    }
    // Player-supplied monotonic animation time. This fixture family uses both ids interchangeably
    // as moving clocks; keeping them tied to the injected logical timeline makes captures stable.
    result[1] = Float(timeSeconds)
    result[30] = Float(timeSeconds)
    for expression in document.expressions {
      result[expression.id] = try NativeSwiftFloatExpression.evaluate(
        expression.words, values: result)
    }
    return result
  }

  private func estimatedDimension(of node: ParsedNode, type: Int, available: Float) -> Float {
    let dimensionType = type == 0 ? node.widthType : node.heightType
    let dimensionValue = type == 0 ? node.widthValue : node.heightValue
    if dimensionType == 0 || dimensionType == 6 { return max(dimensionValue, 0) }
    if dimensionType == 1 || dimensionType == 7 || dimensionType == 8 {
      return available * (dimensionValue.isNaN ? 1 : max(dimensionValue, 0))
    }
    let children = flattenedChildren(of: node)
    let childDimensions = children.map {
      estimatedDimension(of: $0, type: type, available: available)
    }
    let intrinsic: Float
    if let text = node.text {
      if type == 0 {
        intrinsic = Float(texts[text.textID]?.count ?? 0) * text.size * 0.6
      } else {
        intrinsic = text.size * 1.2
      }
    } else if type == 0 {
      intrinsic = node.kind == .row ? childDimensions.reduce(0, +) : childDimensions.max() ?? 0
    } else {
      intrinsic =
        node.kind == .column
        ? childDimensions.reduce(0, +)
          + node.spacing * Float(max(childDimensions.count - 1, 0))
        : childDimensions.max() ?? 0
    }
    let padding =
      type == 0
      ? node.padding.left + node.padding.right
      : node.padding.top + node.padding.bottom
    let minimum = type == 1 ? node.minimumHeight : 0
    return max(intrinsic + padding, minimum)
  }

  private func flattenedChildren(of node: ParsedNode) -> [ParsedNode] {
    node.children.flatMap { child in
      if child.kind == .content, child.widthType == 2, child.heightType == 2,
        child.padding.left == 0, child.padding.top == 0, child.padding.right == 0,
        child.padding.bottom == 0
      {
        return flattenedChildren(of: child)
      }
      return [child]
    }
  }
}

private struct ParsedDocument {
  let width: Int
  let height: Int
  let root: ParsedNode
  let nodes: [Int: ParsedNode]
  let texts: [Int: String]
  let floats: [Int: Float]
  let colors: [Int: UInt32]
  let integers: [Int: Int]
  let namedVariables: [String: ParsedNamedVariable]
  let expressions: [ParsedFloatExpression]
  let componentValues: [ParsedComponentValue]
  let needsContinuousFrames: Bool
}

private struct ParsedComponentValue {
  let type: Int
  let componentID: Int
  let valueID: Int
}

private struct ParsedNamedVariable {
  let id: Int
  let type: Int
}

private struct ParsedFloatExpression {
  let id: Int
  let words: [UInt32]
}

private struct ParsedNamedAction {
  let nameTextID: Int
  let valueType: Int
  let valueID: Int
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
  let paint: ParsedPaint
  let path: ParsedPath?

  init(kind: Int, words: [UInt32], paint: ParsedPaint, path: ParsedPath? = nil) {
    self.kind = kind
    self.words = words
    self.paint = paint
    self.path = path
  }

  func resolve(values: [Int: Float], colors: [Int: UInt32]) throws
    -> NativeSwiftDrawCommandSnapshot
  {
    return NativeSwiftDrawCommandSnapshot(
      kind: kind,
      values: words.map { NativeSwiftFloatExpression.resolve($0, values: values) },
      colorARGB: paint.colorID.flatMap { colors[$0] } ?? paint.colorARGB,
      alpha: paint.alpha,
      strokeWidth: NativeSwiftFloatExpression.resolve(paint.strokeWidth, values: values),
      isStroke: paint.isStroke,
      strokeCap: paint.strokeCap,
      strokeJoin: paint.strokeJoin,
      blendMode: paint.blendMode,
      path: try path?.resolve(values: values) ?? [],
      pathWinding: path?.winding ?? 0)
  }
}

private struct ParsedPath {
  let winding: Int
  let words: [UInt32]

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

private struct ParsedPaint {
  var colorARGB: UInt32 = 0xff00_0000
  var colorID: Int?
  var alpha: Float = 1
  var strokeWidth: UInt32 = Float(1).bitPattern
  var isStroke = false
  var strokeCap = 0
  var strokeJoin = 0
  var blendMode = 3
}

private final class ParsedNode {
  let kind: NativeSwiftNodeSnapshot.Kind
  let componentID: Int
  weak var parent: ParsedNode?
  var children: [ParsedNode] = []
  var commands: [ParsedDrawCommand] = []
  var isClickable = false
  var actions: [ParsedNamedAction] = []
  var accessibility: ParsedAccessibility?
  var widthType = 2
  var widthValue: Float = 0
  var heightType = 2
  var heightValue: Float = 0
  var padding = NativeSwiftInsets()
  var minimumHeight: Float = 0
  var cornerRadius: Float = 0
  var backgroundARGB: UInt32?
  var backgroundColorID: Int?
  var horizontalPositioning = 1
  var verticalPositioning = 4
  var spacing: Float = 0
  var text: ParsedText?
  var custom: ParsedCustom?

  init(kind: NativeSwiftNodeSnapshot.Kind, componentID: Int) {
    self.kind = kind
    self.componentID = componentID
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

  static func evaluate(_ words: [UInt32], values: [Int: Float]) throws -> Float {
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
      case 26:
        let value = try pop(3)
        stack.append(value[2] > 0 ? value[1] : value[0])
      case 49:
        let value = try pop(3)
        stack.append(value[0] + (value[1] - value[0]) * value[2])
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

private struct ParsedText {
  let textID: Int
  let colorARGB: UInt32
  let colorID: Int?
  let size: Float
  let style: Int
  let weight: Float
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

private enum NativeSwiftDocumentDecoder {
  static let maximumStringBytes = 4_000
  private static let maximumOperations = 100_000
  private static let maximumProperties = 2_000
  private static let maximumNodes = 20_000
  private static let maximumNestingDepth = 256

  static func decode(_ data: Data) throws -> ParsedDocument {
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
        case 1:
          guard length == 4 else { throw input.malformed("Invalid float header property length") }
          _ = try input.int("header property \(index)")
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
      guard let modernWidth, let modernHeight else {
        throw input.malformed("Modern header has no document dimensions")
      }
      width = modernWidth
      height = modernHeight
    }
    guard major >= 0, width > 0, height > 0 else {
      throw input.malformed("Header dimensions and version must be positive")
    }

    var texts: [Int: String] = [:]
    var floats: [Int: Float] = [:]
    var colors: [Int: UInt32] = [:]
    let integers: [Int: Int] = [:]
    var namedVariables: [String: ParsedNamedVariable] = [:]
    var expressions: [ParsedFloatExpression] = []
    var componentValues: [ParsedComponentValue] = []
    var paths: [Int: ParsedPath] = [:]
    var nodes: [Int: ParsedNode] = [:]
    var stack: [ParsedNode] = []
    var root: ParsedNode?
    var operationCount = 0
    var expressionWordCount = 0
    var modifierContainers: [ParsedNode?] = []
    var paint = ParsedPaint()

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
      } else if root == nil {
        root = node
      } else {
        throw input.malformed("Document has more than one root component")
      }
      nodes[node.componentID] = node
      stack.append(node)
    }

    while !input.isAtEnd {
      operationCount += 1
      guard operationCount <= maximumOperations else {
        throw input.malformed("Operation count exceeds \(maximumOperations)")
      }
      let opcodeOffset = input.offset
      let opcode = try input.u8("opcode")
      switch opcode {
      case 40:  // Paint data
        let count = try input.count("paint word count", maximum: 1_024)
        var words: [Int] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.int("paint word")) }
        try applyPaint(words, to: &paint, input: input)
      case 54:  // Rounded clip rectangle
        let node = try currentNode(stack, input: input)
        var radius: Float = 0
        for _ in 0..<4 { radius = max(radius, try input.literalFloat("corner radius")) }
        node.cornerRadius = radius
      case 59:  // Click modifier encloses its action operations.
        let node = try currentNode(stack, input: input)
        node.isClickable = true
        modifierContainers.append(node)
      case 130:  // Matrix save
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 0, words: [], paint: paint))
      case 131:  // Matrix restore
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 1, words: [], paint: paint))
      case 173:  // Canvas operations container
        modifierContainers.append(nil)
      case 139, 174:  // Marker/modifier operations without payload
        break
      case 16:  // Width
        let node = try currentNode(stack, input: input)
        node.widthType = try input.dimensionType("width type")
        node.widthValue = try input.literalFloat("width")
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
        node.padding = NativeSwiftInsets(
          left: try input.literalFloat("padding left"),
          top: try input.literalFloat("padding top"),
          right: try input.literalFloat("padding right"),
          bottom: try input.literalFloat("padding bottom"))
      case 67:  // Height
        let node = try currentNode(stack, input: input)
        node.heightType = try input.dimensionType("height type")
        node.heightValue = try input.literalFloat("height")
      case 80:  // Float constant
        floats[try input.int("float id")] = try input.literalFloat("float value")
      case 81:  // Float expression
        let id = try input.int("float expression id")
        let lengths = try input.int("float expression lengths")
        let count = (lengths & 0xffff) + ((lengths >> 16) & 0xffff)
        guard count <= 65_567 else { throw input.malformed("Float expression is too long") }
        expressionWordCount += count
        guard expressionWordCount <= 200_000 else {
          throw input.malformed("Float expression work exceeds 200000 words")
        }
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("float expression word")) }
        expressions.append(ParsedFloatExpression(id: id, words: words))
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
          if [3, 5, 6, 9].contains(dataType) {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "custom property data type \(dataType) is not migrated")
          }
          if dataType == 1, Float(bitPattern: UInt32(bitPattern: Int32(valueBits))).isNaN {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset, reason: "dynamic custom float property")
          }
          properties.append(
            ParsedCustomProperty(
              type: type, dataType: dataType, valueBits: valueBits))
        }
        let node = ParsedNode(kind: .custom, componentID: id)
        node.custom = ParsedCustom(configID: configID, properties: properties)
        try begin(node)
      case 102:  // Text data
        let id = try input.int("text id")
        texts[id] = try input.utf8("text", maximum: maximumStringBytes)
      case 138:  // Color constant
        colors[try input.int("color id")] = UInt32(bitPattern: Int32(try input.int("color")))
      case 123:  // Path data; bounded now, drawing support is a separate operation family.
        let idAndWinding = try input.int("path id and winding")
        let count = try input.count("path word count", maximum: 20_000)
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("path word")) }
        paths[idAndWinding & 0x00ff_ffff] = ParsedPath(
          winding: idAndWinding >> 24, words: words)
      case 124:
        let id = try input.int("path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try currentNode(stack, input: input).commands.append(
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
        guard stack.contains(where: { $0.componentID == componentID }) else {
          throw input.malformed("Missing component \(componentID) for value binding")
        }
        componentValues.append(
          ParsedComponentValue(type: type, componentID: componentID, valueID: valueID))
      case 200:  // Root
        try begin(ParsedNode(kind: .root, componentID: try input.int("root component id")))
      case 201:  // Content
        try begin(ParsedNode(kind: .content, componentID: try input.int("content component id")))
      case 202:  // Box
        let node = ParsedNode(kind: .box, componentID: try input.int("box component id"))
        _ = try input.int("box animation id")
        node.horizontalPositioning = try input.int("box horizontal positioning")
        node.verticalPositioning = try input.int("box vertical positioning")
        try begin(node)
      case 203:  // Row
        let node = ParsedNode(kind: .row, componentID: try input.int("row component id"))
        _ = try input.int("row animation id")
        node.horizontalPositioning = try input.int("row horizontal positioning")
        node.verticalPositioning = try input.int("row vertical positioning")
        node.spacing = try input.floatWord("row spacing", requireLiteral: false)
        try begin(node)
      case 204:  // Column
        let node = ParsedNode(kind: .column, componentID: try input.int("column component id"))
        _ = try input.int("column animation id")
        node.horizontalPositioning = try input.int("column horizontal positioning")
        node.verticalPositioning = try input.int("column vertical positioning")
        node.spacing = try input.literalFloat("column spacing")
        try begin(node)
      case 205:  // Canvas
        let node = ParsedNode(kind: .canvas, componentID: try input.int("canvas component id"))
        _ = try input.int("canvas animation id")
        try begin(node)
      case 129:  // Matrix rotate
        let words = try (0..<3).map { _ in try input.word("matrix rotate value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 4, words: words, paint: paint))
      case 208:  // Text layout
        let node = ParsedNode(kind: .text, componentID: try input.int("text component id"))
        _ = try input.int("text animation id")
        let textID = try input.int("text id")
        let color = UInt32(bitPattern: Int32(try input.int("text color")))
        let size = try input.literalFloat("text size")
        let style = try input.int("text style")
        let weight = try input.literalFloat("text weight")
        let familyID = try input.int("text family id")
        let alignmentAndFlags = try input.int("text alignment")
        let flags = UInt32(bitPattern: Int32(alignmentAndFlags)) >> 16
        guard flags == 0 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "dynamic text colors")
        }
        let overflow = try input.int("text overflow")
        let maximumLines = try input.int("text maximum lines")
        guard size > 0, (0...3).contains(style), (1...6).contains(alignmentAndFlags & 0xffff),
          (1...5).contains(overflow), maximumLines > 0
        else { throw input.malformed("Invalid text layout values") }
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: nil, size: size, style: style, weight: weight,
          familyID: familyID, alignment: alignmentAndFlags & 0xffff, overflow: overflow,
          maximumLines: maximumLines)
        try begin(node)
      case 210:  // Host named action
        let action = ParsedNamedAction(
          nameTextID: try input.int("host action name text id"),
          valueType: try input.int("host action value type"),
          valueID: try input.int("host action value id"))
        guard let target = modifierContainers.reversed().compactMap({ $0 }).first else {
          throw input.malformed("Host named action is outside a click modifier")
        }
        target.actions.append(action)
      case 214:  // Container end
        if !modifierContainers.isEmpty {
          modifierContainers.removeLast()
        } else {
          // Modern AndroidX documents carry a final document-level terminator after the root.
          if stack.isEmpty, input.isAtEnd { break }
          guard !stack.isEmpty else { throw input.malformed("Unmatched container end") }
          stack.removeLast()
        }
      case 232:  // Minimum/maximum height
        let node = try currentNode(stack, input: input)
        node.minimumHeight = try input.floatWord("minimum height", requireLiteral: false)
        _ = try input.floatWord("maximum height", requireLiteral: false)
      case 239:  // CoreText
        let textID = try input.int("core text id")
        let propertyCount = Int(try input.u16("core text property count"))
        guard propertyCount <= 26 else { throw input.malformed("Too many CoreText properties") }
        var integers: [Int: Int] = [:]
        var floats: [Int: Float] = [:]
        for _ in 0..<propertyCount {
          let id = try input.u8("core text property id")
          if [1, 2, 3, 4, 6, 8, 9, 10, 11, 15, 16, 17, 23, 24].contains(id) {
            integers[id] = try input.int("core text integer")
          } else if [5, 7, 12, 13, 14, 25, 26].contains(id) {
            floats[id] = try input.floatWord("core text float", requireLiteral: false)
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
        let color =
          integers[4].flatMap { colors[$0] }
          ?? UInt32(bitPattern: Int32(integers[3] ?? -16_777_216))
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: integers[4], size: floats[5] ?? 36,
          style: integers[6] ?? 0, weight: floats[7] ?? 400,
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
        guard role <= 9, mode <= 2, enabled <= 1, clickable <= 1 else {
          throw input.malformed("Invalid accessibility semantics")
        }
        node.accessibility = ParsedAccessibility(
          contentDescriptionID: contentDescriptionID, role: role, textID: textID,
          stateDescriptionID: stateDescriptionID, mode: mode, isEnabled: enabled == 1,
          isClickable: clickable == 1)
      case 152:  // Draw arc
        let words = try (0..<6).map { _ in try input.word("draw arc value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 15, words: words, paint: paint))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: opcodeOffset, reason: "operation family not migrated")
      }
    }
    guard stack.isEmpty else { throw input.malformed("Unclosed layout container") }
    guard let root, root.kind == .root else { throw input.malformed("Missing root component") }
    let needsContinuousFrames = expressions.contains { expression in
      expression.words.contains { word in
        NativeSwiftFloatExpression.referenceID(word).map { $0 == 1 || $0 == 30 } ?? false
      }
    }
    return ParsedDocument(
      width: width, height: height, root: root, nodes: nodes, texts: texts, floats: floats,
      colors: colors, integers: integers, namedVariables: namedVariables, expressions: expressions,
      componentValues: componentValues,
      needsContinuousFrames: needsContinuousFrames)
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
      switch type {
      case 1, 4, 5, 9, 12, 13, 16, 19, 20, 22: argumentCount = 1
      case 24: argumentCount = 3
      case 7, 8, 10, 14, 15, 17, 18, 21: argumentCount = 0
      case 23: argumentCount = highBits * 2
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 40, offset: input.offset, reason: "paint command \(type)")
      }
      guard index + argumentCount <= words.count else { throw input.malformed("Truncated paint") }
      switch type {
      case 4:
        paint.colorARGB = UInt32(bitPattern: Int32(words[index]))
        paint.colorID = nil
      case 5: paint.strokeWidth = UInt32(bitPattern: Int32(words[index]))
      case 7: paint.strokeCap = highBits
      case 8: paint.isStroke = highBits == 1
      case 12:
        paint.alpha = min(max(Float(bitPattern: UInt32(bitPattern: Int32(words[index]))), 0), 1)
      case 15: paint.strokeJoin = highBits
      case 18: paint.blendMode = highBits
      case 19:
        paint.colorID = words[index]
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

private struct WireReader {
  private let bytes: [UInt8]
  private(set) var offset = 0

  init(_ data: Data) { bytes = Array(data) }
  var isAtEnd: Bool { offset == bytes.count }

  mutating func u8(_ field: String) throws -> Int {
    guard offset < bytes.count else { throw malformed("Unexpected end while reading \(field)") }
    defer { offset += 1 }
    return Int(bytes[offset])
  }

  mutating func signedU16(_ field: String) throws -> Int {
    let raw = try u16(field)
    return Int(Int16(bitPattern: raw))
  }

  mutating func u16(_ field: String) throws -> UInt16 {
    guard bytes.count - offset >= 2 else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += 2 }
    return UInt16(bytes[offset]) << 8 | UInt16(bytes[offset + 1])
  }

  mutating func int(_ field: String) throws -> Int {
    Int(Int32(bitPattern: try uint32(field)))
  }

  mutating func word(_ field: String) throws -> UInt32 {
    try uint32(field)
  }

  mutating func dimensionType(_ field: String) throws -> Int {
    let value = try int(field)
    guard (0...8).contains(value) else { throw malformed("Invalid \(field) \(value)") }
    return value
  }

  mutating func count(_ field: String, maximum: Int) throws -> Int {
    let value = try int(field)
    guard value >= 0, value <= maximum else {
      throw malformed("\(field) \(value) is outside 0...\(maximum)")
    }
    return value
  }

  mutating func literalFloat(_ field: String) throws -> Float {
    try floatWord(field, requireLiteral: true)
  }

  mutating func floatWord(_ field: String, requireLiteral: Bool) throws -> Float {
    let bits = try uint32(field)
    let value = Float(bitPattern: bits)
    if value.isNaN, requireLiteral {
      throw NativeSwiftCoreError.unsupported(
        opcode: -1, offset: offset - 4, reason: "dynamic float \(field)")
    }
    guard !requireLiteral || value.isFinite else { throw malformed("\(field) must be finite") }
    return value
  }

  mutating func utf8(_ field: String, maximum: Int) throws -> String {
    let length = try count("\(field) length", maximum: maximum)
    guard bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field)")
    }
    let value = String(bytes: bytes[offset..<(offset + length)], encoding: .utf8)
    offset += length
    guard let value else { throw malformed("\(field) is not valid UTF-8") }
    return value
  }

  mutating func utf8Bytes(_ field: String, length: Int) throws -> String {
    guard length >= 0, length <= NativeSwiftDocumentDecoder.maximumStringBytes,
      bytes.count - offset >= length
    else { throw malformed("Invalid \(field) length") }
    let value = String(bytes: bytes[offset..<(offset + length)], encoding: .utf8)
    offset += length
    guard let value else { throw malformed("\(field) is not valid UTF-8") }
    return value
  }

  func malformed(_ reason: String) -> NativeSwiftCoreError {
    .malformed(offset: offset, reason: reason)
  }

  private mutating func uint32(_ field: String) throws -> UInt32 {
    guard bytes.count - offset >= 4 else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += 4 }
    return UInt32(bytes[offset]) << 24 | UInt32(bytes[offset + 1]) << 16
      | UInt32(bytes[offset + 2]) << 8 | UInt32(bytes[offset + 3])
  }
}

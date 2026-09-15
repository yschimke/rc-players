import Foundation

/// Immutable, platform-neutral output from the Swift wire/runtime path.
///
/// Keeping this model free of UIKit and Kotlin objects lets decoding and document state remain
/// actor-isolated while the final UIView model is materialized on the main actor.
struct NativeSwiftDocumentSnapshot: Sendable {
  let width: Int
  let height: Int
  let root: NativeSwiftNodeSnapshot
}

struct NativeSwiftNodeSnapshot: Sendable {
  enum Kind: Sendable {
    case root, content, column, text, custom
  }

  let kind: Kind
  let componentID: Int
  let children: [NativeSwiftNodeSnapshot]
  let widthType: Int
  let widthValue: Float
  let heightType: Int
  let heightValue: Float
  let padding: NativeSwiftInsets
  let backgroundARGB: UInt32?
  let horizontalPositioning: Int
  let verticalPositioning: Int
  let spacing: Float
  let text: NativeSwiftTextSnapshot?
  let custom: NativeSwiftCustomSnapshot?
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

  private init(document: ParsedDocument) {
    self.document = document
    texts = document.texts
  }

  static func open(data: Data) throws -> NativeSwiftDocumentSession {
    NativeSwiftDocumentSession(document: try NativeSwiftDocumentDecoder.decode(data))
  }

  func snapshot() throws -> NativeSwiftDocumentSnapshot {
    NativeSwiftDocumentSnapshot(
      width: document.width,
      height: document.height,
      root: try resolve(document.root))
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

  private func resolve(_ node: ParsedNode) throws -> NativeSwiftNodeSnapshot {
    let text: NativeSwiftTextSnapshot?
    if let source = node.text {
      text = NativeSwiftTextSnapshot(
        value: texts[source.textID] ?? "",
        colorARGB: source.colorARGB,
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
          integerValue = Int(Int32(bitPattern: document.colors[property.valueBits] ?? 0))
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
      children: try node.children.map(resolve),
      widthType: node.widthType,
      widthValue: node.widthValue,
      heightType: node.heightType,
      heightValue: node.heightValue,
      padding: node.padding,
      backgroundARGB: node.backgroundARGB,
      horizontalPositioning: node.horizontalPositioning,
      verticalPositioning: node.verticalPositioning,
      spacing: node.spacing,
      text: text,
      custom: custom)
  }
}

private struct ParsedDocument {
  let width: Int
  let height: Int
  let root: ParsedNode
  let nodes: [Int: ParsedNode]
  let texts: [Int: String]
  let colors: [Int: UInt32]
}

private final class ParsedNode {
  let kind: NativeSwiftNodeSnapshot.Kind
  let componentID: Int
  var children: [ParsedNode] = []
  var widthType = 2
  var widthValue: Float = 0
  var heightType = 2
  var heightValue: Float = 0
  var padding = NativeSwiftInsets()
  var backgroundARGB: UInt32?
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

private struct ParsedText {
  let textID: Int
  let colorARGB: UInt32
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
    let major = try input.int("major version")
    guard major < 0x10000 else {
      throw NativeSwiftCoreError.unsupported(
        opcode: 0, offset: 1, reason: "modern header maps are not decoded in Swift yet")
    }
    _ = try input.int("minor version")
    _ = try input.int("patch version")
    let width = try input.int("width")
    let height = try input.int("height")
    _ = try input.int("capabilities high word")
    _ = try input.int("capabilities low word")
    guard major >= 0, width > 0, height > 0 else {
      throw input.malformed("Header dimensions and version must be positive")
    }

    var texts: [Int: String] = [:]
    var colors: [Int: UInt32] = [:]
    var nodes: [Int: ParsedNode] = [:]
    var stack: [ParsedNode] = []
    var root: ParsedNode?
    var operationCount = 0

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
          !usesColorID
          ? argb(red: red, green: green, blue: blue, alpha: alpha)
          : colors[colorID]
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
      case 200:  // Root
        try begin(ParsedNode(kind: .root, componentID: try input.int("root component id")))
      case 201:  // Content
        try begin(ParsedNode(kind: .content, componentID: try input.int("content component id")))
      case 204:  // Column
        let node = ParsedNode(kind: .column, componentID: try input.int("column component id"))
        _ = try input.int("column animation id")
        node.horizontalPositioning = try input.int("column horizontal positioning")
        node.verticalPositioning = try input.int("column vertical positioning")
        node.spacing = try input.literalFloat("column spacing")
        try begin(node)
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
          textID: textID, colorARGB: color, size: size, style: style, weight: weight,
          familyID: familyID, alignment: alignmentAndFlags & 0xffff, overflow: overflow,
          maximumLines: maximumLines)
        try begin(node)
      case 214:  // Container end
        guard !stack.isEmpty else { throw input.malformed("Unmatched container end") }
        stack.removeLast()
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: opcodeOffset, reason: "operation family not migrated")
      }
    }
    guard stack.isEmpty else { throw input.malformed("Unclosed layout container") }
    guard let root, root.kind == .root else { throw input.malformed("Missing root component") }
    return ParsedDocument(
      width: width, height: height, root: root, nodes: nodes, texts: texts, colors: colors)
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
    let raw = try uint16(field)
    return Int(Int16(bitPattern: raw))
  }

  mutating func int(_ field: String) throws -> Int {
    Int(Int32(bitPattern: try uint32(field)))
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

  func malformed(_ reason: String) -> NativeSwiftCoreError {
    .malformed(offset: offset, reason: reason)
  }

  private mutating func uint16(_ field: String) throws -> UInt16 {
    guard bytes.count - offset >= 2 else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += 2 }
    return UInt16(bytes[offset]) << 8 | UInt16(bytes[offset + 1])
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

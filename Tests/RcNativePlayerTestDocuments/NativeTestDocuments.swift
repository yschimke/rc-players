import Foundation
import RcNativePlayerCore

/// `.rc` documents the native player's suites build from wire opcodes.
public enum NativeTestDocuments {
  /// The id of the box in ``shownBox()``.
  public static let boxID = 10

  /// The integer named ``shownName`` in ``shownBox()`` and the controller suites' document.
  public static let shownVariableID = 550
  /// The integer named ``localName`` in the controller suites' document.
  public static let localVariableID = 551
  /// The name of the integer ``shownVariableID``.
  public static let shownName = "shown"
  /// The name of the integer ``localVariableID``.
  public static let localName = "local"

  /// The string variable ``labelName`` in ``manyIntegers(count:withLabel:)`` and the controller
  /// suites' document.
  public static let labelVariableID = 560
  /// The name of the string the label documents' text reads.
  public static let labelName = "label"
  /// That string's value until a host sets it.
  public static let labelInitial = "Idle"
  /// The name of the second string in ``label()``.
  public static let titleName = "title"
  /// That string's value until a host sets it.
  public static let titleInitial = "Title"
  /// The string variable ``label()`` declares as ``labelName`` (``labelVariableID`` is the one
  /// the other documents declare under that name).
  public static let labelDocumentLabelStringID = 60
  /// The string variable ``label()`` declares as ``titleName``.
  public static let labelDocumentTitleStringID = 61

  /// The text colour of every test document: opaque black, as the signed integer the wire holds.
  public static let opaqueBlack = Int(Int32(bitPattern: 0xFF00_0000))

  /// The text limit the suites set (`maximumTextBytes`).
  public static let textLimitBytes = 64

  /// A string twice ``textLimitBytes`` long, of the one-byte `character` repeated: over the limit.
  public static func overLimitText(repeating character: Character = "x") -> String {
    String(repeating: character, count: 2 * textLimitBytes)
  }

  /// A `size`×`size` document whose 50×50 box (``boxID``) is gone until the integer named
  /// `variableName` (``shownName`` by default) says otherwise.
  public static func shownBox(size: Int = 200, variableName: String = shownName) -> Data {
    typealias Op = NativeSwiftWireOpcode
    let wire = NativeTestWire()
    wire.header(width: size, height: size)
    wire.u8(Op.dataInt).int(shownVariableID).int(NativeSwiftVisibility.gone)
      .named(shownVariableID, NativeTestWire.NamedType.int, variableName)
    wire.u8(Op.layoutRoot).int(1)
    wire.box(id: boxID, size: 50, visibility: shownVariableID, content: -1)
    wire.u8(Op.containerEnd)
    return wire.data
  }

  /// The id of the text in ``label()``.
  public static let labelID = 20

  /// A 200×100 document showing two texts: ``labelID`` reads the string named ``labelName``,
  /// ``labelInitial`` until it is set, and the other reads ``titleName``, ``titleInitial`` until it
  /// is set.
  public static func label() -> Data {
    typealias Op = NativeSwiftWireOpcode
    typealias Kind = NativeTestWire.NamedType
    let wire = NativeTestWire()
    wire.header(width: 200, height: 100)
    wire.u8(Op.dataText).int(labelDocumentLabelStringID).utf8(labelInitial)
      .named(labelDocumentLabelStringID, Kind.string, labelName)
    wire.u8(Op.dataText).int(labelDocumentTitleStringID).utf8(titleInitial)
      .named(labelDocumentTitleStringID, Kind.string, titleName)
    wire.u8(Op.layoutRoot).int(1)
    wire.text(id: labelID, reading: labelDocumentLabelStringID)
    wire.text(id: labelID + 1, reading: labelDocumentTitleStringID)
    wire.u8(Op.containerEnd)
    return wire.data
  }

  /// float "f" = 1.5, colour "c" = opaque black, integer "b" (a boolean) = 0, string "s" = "a",
  /// integer "i" = 3, and string "computed", the text of the float 2 (TextFromFloat): a value the
  /// document computes and nobody writes. One 10×10 box.
  public static func everyHostType() -> Data {
    typealias Op = NativeSwiftWireOpcode
    typealias Kind = NativeTestWire.NamedType
    let wire = NativeTestWire()
    wire.header(width: 100, height: 100)
    wire.u8(Op.dataFloat).int(700).float(1.5).named(700, Kind.float, "f")
    wire.u8(Op.colorConstant).int(701).int(opaqueBlack)
      .named(701, Kind.color, "c")
    wire.u8(Op.dataInt).int(702).int(0).named(702, Kind.int, "b")
    wire.u8(Op.dataText).int(703).utf8("a").named(703, Kind.string, "s")
    wire.u8(Op.dataInt).int(705).int(3).named(705, Kind.int, "i")
    // TextFromFloat: output id, value, digits before << 16 | digits after, flags.
    wire.u8(Op.textFromFloat).int(704).float(2).int((2 << 16) | 1).int(0)
      .named(704, Kind.string, "computed")
    wire.u8(Op.layoutRoot).int(1)
    wire.box(id: 10, size: 10)
    wire.u8(Op.containerEnd)
    return wire.data
  }

  /// long "when" (id 710, 2023-11-14T22:13:20Z in epoch milliseconds), float array "series" (id
  /// 711, a static list of 1 and 2) and float "scalar" (id 712, 1), and one 10×10 box.
  public static func longAndFloatArray() -> Data {
    typealias Op = NativeSwiftWireOpcode
    typealias Kind = NativeTestWire.NamedType
    let wire = NativeTestWire()
    wire.header(width: 100, height: 100)
    wire.u8(Op.dataLong).int(710).int(0x18B).int(Int(Int32(bitPattern: 0xCFE5_6800)))
      .named(710, Kind.long, "when")
    wire.u8(Op.floatList).int(711).int(2).float(1).float(2).named(711, Kind.floatArray, "series")
    wire.u8(Op.dataFloat).int(712).float(1).named(712, Kind.float, "scalar")
    wire.u8(Op.layoutRoot).int(1)
    wire.box(id: 10, size: 10)
    wire.u8(Op.containerEnd)
    return wire.data
  }

  /// float "foo" (id 600) and float "USER:foo" (id 601), both 1, and one 10×10 box: a host's
  /// "foo" addresses "USER:foo", the bare variable only the document writes.
  public static func bareAndUserFoo() -> Data {
    typealias Op = NativeSwiftWireOpcode
    let wire = NativeTestWire()
    wire.header(width: 100, height: 100)
    for (id, name) in [(600, "foo"), (601, "USER:foo")] {
      wire.u8(Op.dataFloat).int(id).float(1).named(id, NativeTestWire.NamedType.float, name)
    }
    wire.u8(Op.layoutRoot).int(1)
    wire.box(id: 10, size: 10)
    wire.u8(Op.containerEnd)
    return wire.data
  }

  /// ``label()`` with a haptic as a frame effect, so every frame, the first included, raises an
  /// event.
  public static func labelWithAFrameHaptic() -> Data {
    var data = label()
    // Before the root's closing CONTAINER_END, outside any action container.
    let end = data.removeLast()
    data.append(contentsOf: NativeTestWire().u8(NativeSwiftWireOpcode.hapticFeedback).int(1).data)
    data.append(end)
    return data
  }

  /// `count` 1×1 boxes, box `i` (id 1000+i) gone until the integer named "n<i>" says otherwise,
  /// and, `withLabel`, a text (``labelID``) reading the string named ``labelName``,
  /// ``labelInitial`` until set.
  public static func manyIntegers(count: Int, withLabel: Bool = false) -> Data {
    typealias Op = NativeSwiftWireOpcode
    let wire = NativeTestWire()
    wire.header(width: 200, height: 200)
    for index in 0..<count {
      wire.u8(Op.dataInt).int(5000 + index).int(NativeSwiftVisibility.gone)
        .named(5000 + index, NativeTestWire.NamedType.int, "n\(index)")
    }
    if withLabel {
      wire.u8(Op.dataText).int(labelVariableID).utf8(labelInitial)
        .named(labelVariableID, NativeTestWire.NamedType.string, labelName)
    }
    wire.u8(Op.layoutRoot).int(1)
    for index in 0..<count {
      wire.box(id: 1000 + index, size: 1, visibility: 5000 + index, content: 20000 + index)
    }
    if withLabel { wire.text(id: labelID, reading: labelVariableID) }
    wire.u8(Op.containerEnd)
    return wire.data
  }
}

/// Writes `.rc` wire operations, big-endian, for the test documents.
public final class NativeTestWire {
  /// A named variable's wire type: every value of the core's `NativeSwiftNamedVariableType`,
  /// which is internal. `NativeTestWireNamedTypeTests` keeps the two equal.
  public enum NamedType {
    public static let string = 0
    public static let float = 1
    public static let color = 2
    public static let image = 3
    public static let int = 4
    public static let long = 5
    public static let floatArray = 6
  }

  public private(set) var data = Data()

  public init() {}

  @discardableResult
  public func u8(_ value: Int) -> Self {
    data.append(UInt8(truncatingIfNeeded: value))
    return self
  }

  @discardableResult
  public func int(_ value: Int) -> Self {
    let raw = UInt32(bitPattern: Int32(truncatingIfNeeded: value))
    for shift in [24, 16, 8, 0] { data.append(UInt8(truncatingIfNeeded: raw >> shift)) }
    return self
  }

  @discardableResult
  public func float(_ value: Float) -> Self { int(Int(Int32(bitPattern: value.bitPattern))) }

  /// A length-prefixed UTF-8 string.
  @discardableResult
  public func utf8(_ value: String) -> Self {
    let bytes = Array(value.utf8)
    int(bytes.count)
    data.append(contentsOf: bytes)
    return self
  }

  /// The version-1 header for a `width`×`height` document.
  public func header(width: Int, height: Int) {
    u8(NativeSwiftWireOpcode.header)
    for value in [1, 0, 0, width, height, 0, 0] { int(value) }
  }

  /// Declares the variable `id` as the named variable `name` of wire type `type`.
  @discardableResult
  public func named(_ id: Int, _ type: Int, _ name: String) -> Self {
    u8(NativeSwiftWireOpcode.namedVariable).int(id).int(type).utf8(name)
  }

  /// A text component `id` reading the string variable `textID`, 16 pt, opaque black, then its
  /// container end. TextLayout: component, animation, text, colour, size, style, weight, family,
  /// alignment, overflow, maximum lines.
  public func text(id: Int, reading textID: Int) {
    u8(NativeSwiftWireOpcode.layoutText).int(id).int(0).int(textID)
      .int(NativeTestDocuments.opaqueBlack)
    float(16).int(0).float(400).int(-1).int(1).int(1).int(1)
    u8(NativeSwiftWireOpcode.containerEnd)
  }

  /// A `size`×`size` box with empty content (component `content`, `id + 100` by default), its
  /// visibility read from the integer `visibility` when given.
  public func box(id: Int, size: Float, visibility: Int? = nil, content: Int? = nil) {
    typealias Op = NativeSwiftWireOpcode
    u8(Op.layoutBox).int(id).int(-1).int(1).int(1)
    for opcode in [Op.modifierWidth, Op.modifierHeight] {
      u8(opcode).int(NativeSwiftDimensionType.exact).float(size)
    }
    if let visibility { u8(Op.modifierVisibility).int(visibility) }
    u8(Op.layoutContent).int(content ?? id + 100)
    u8(Op.containerEnd)
    u8(Op.containerEnd)
  }
}

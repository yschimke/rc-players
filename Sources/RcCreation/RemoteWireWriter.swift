/// The `.rc` operation codes this writer emits, named as AndroidX's `Operations.java` names them.
///
/// The values are AndroidX's wire constants, cross-checked against `operations.json` in
/// androidx-main `remote-generator` and against `NativeSwiftWireOpcode` in the native player.
enum RemoteOpcode {
  static let header = 0
  static let paintValues = 40
  static let drawRect = 42
  static let drawCircle = 46
  static let drawLine = 47
  static let drawRoundRect = 51
  static let modifierRoundedClipRect = 54
  static let modifierBackground = 55
  static let drawOval = 56
  static let modifierPadding = 58
  static let modifierClick = 59
  static let modifierHeight = 67
  static let modifierWidth = 16
  static let dataFloat = 80
  static let animatedFloat = 81
  static let dataText = 102
  static let rootContentDescription = 103
  static let modifierBorder = 107
  static let modifierClipRect = 108
  static let textFromFloat = 135
  static let textMerge = 136
  static let namedVariable = 137
  static let colorConstant = 138
  static let dataInt = 140
  static let layoutRoot = 200
  static let layoutContent = 201
  static let layoutBox = 202
  static let layoutRow = 203
  static let layoutColumn = 204
  static let layoutCanvas = 205
  static let layoutCanvasContent = 207
  static let layoutText = 208
  static let hostAction = 209
  static let hostNamedAction = 210
  static let modifierVisibility = 211
  static let valueIntegerChangeAction = 212
  static let valueStringChangeAction = 213
  static let containerEnd = 214
  static let modifierOffset = 221
  static let valueFloatChangeAction = 222
  static let modifierZIndex = 223
}

/// `NamedVariable`'s `varType`.
enum RemoteNamedVariableType {
  static let string = 0
  static let float = 1
  static let color = 2
  static let int = 4
}

/// `HostNamedAction`'s value `type`.
enum RemoteHostActionValueType {
  static let none = -1
  static let float = 0
  static let int = 1
  static let string = 2
}

/// Width and height modifier `type` (`DimensionModifierOperation.Type`).
enum RemoteDimensionType {
  static let exact = 0
  static let fill = 1
  static let wrap = 2
  static let weight = 3
}

/// Modern header property tags (`Header.DOC_*`) and their value types.
enum RemoteHeaderTag {
  static let documentWidth = 5
  static let documentHeight = 6
  static let densityAtGeneration = 7
  static let contentDescription = 9
  static let typeInt = 0
  static let typeFloat = 1
  static let typeString = 3
  /// `Header.MAGIC_NUMBER`, OR'd into the major version of a modern header.
  static let magic = 0x048C_0000
  static let majorVersion = 1
  static let minorVersion = 1
}

/// `PaintBundle` tags.
enum RemotePaintTag {
  static let color = 4
  static let strokeWidth = 5
  static let strokeCap = 7
  static let style = 8
  static let alpha = 12
  static let antiAlias = 14
  static let colorID = 19
  static let styleFill = 0
  static let styleStroke = 1
}

/// `TextLayout`'s alignment flag: the colour word is a colour id.
let remoteTextDynamicColorFlag = 1 << 16

/// `AnimatedFloatExpression`'s operator offset and the NaN boxing every id reference uses
/// (`Utils.asNan`).
enum RemoteNaN {
  static let mask: UInt32 = 0xFF80_0000
  static let operatorOffset = 0x0031_0000

  static func id(_ id: Int) -> Float { Float(bitPattern: mask | UInt32(id)) }
  static func operation(_ code: Int) -> Float {
    Float(bitPattern: mask | UInt32(operatorOffset + code))
  }
}

/// Big-endian `.rc` bytes, as AndroidX's `WireBuffer` writes them.
struct RemoteByteBuffer {
  private(set) var bytes: [UInt8] = []

  mutating func byte(_ value: Int) { bytes.append(UInt8(truncatingIfNeeded: value)) }

  mutating func short(_ value: Int) {
    let raw = UInt16(truncatingIfNeeded: value)
    bytes.append(UInt8(truncatingIfNeeded: raw >> 8))
    bytes.append(UInt8(truncatingIfNeeded: raw))
  }

  mutating func int(_ value: Int) {
    let raw = UInt32(bitPattern: Int32(truncatingIfNeeded: value))
    for shift: UInt32 in [24, 16, 8, 0] { bytes.append(UInt8(truncatingIfNeeded: raw >> shift)) }
  }

  mutating func float(_ value: Float) { int(Int(Int32(bitPattern: value.bitPattern))) }

  mutating func utf8(_ value: String) {
    let encoded = Array(value.utf8)
    int(encoded.count)
    bytes.append(contentsOf: encoded)
  }

  mutating func append(_ other: RemoteByteBuffer) { bytes.append(contentsOf: other.bytes) }
}

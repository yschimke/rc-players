// The wire values `operations.json` does not carry, written by hand with their AndroidX source.
// Everything the schema defines is generated into RemoteWireConstants.swift by `:rc-codegen`.

/// The type field (`tag >> 10`) of a modern header property.
enum RemoteHeaderValueType {
  static let int = 0
  static let float = 1
  static let long = 2
  static let string = 3
}

/// The version words of a modern header.
enum RemoteHeaderVersion {
  /// `Header.MAGIC_NUMBER`, OR'd into the major version.
  static let magic = 0x048C_0000
  static let major = 1
  static let minor = 1
  static let patch = 0
}

/// The NaN boxing every id reference uses (`Utils.asNan`), from the schema's id encoding.
enum RemoteNaN {
  static func id(_ id: Int) -> Float {
    Float(bitPattern: RemoteIDEncoding.nanPrefix | (UInt32(id) & RemoteIDEncoding.payloadMask))
  }
  static func operation(_ code: Int) -> Float {
    Float(bitPattern: RemoteIDEncoding.nanPrefix | UInt32(RemoteFloatOperatorCode.offset + code))
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

/// Background and border modifier `flags`.
enum RemoteModifierColorFlag {
  /// The colour is the id in `colorId`, not the four channel floats.
  static let colorID = 2
}

/// `BorderModifierOperation`'s wire versions (its `reserve1` word).
enum RemoteBorderWireVersion {
  /// The legacy stroke, centred on the bounds.
  static let centred = 0
  /// The stroke drawn inside the bounds, as AndroidX writes it from API level 8.
  static let inside = 1
}

/// `TextLayout`'s `fontStyle`.
enum RemoteFontStyle {
  static let normal = 0
  static let italic = 1
}

/// The "none" value of an id field: no component id requested, no animation, the default font.
let remoteNoID = -1

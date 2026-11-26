// The byte writer and the NaN boxing. Every wire constant is generated from the schema into
// RemoteWireConstants.swift by `:rc-codegen`.

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

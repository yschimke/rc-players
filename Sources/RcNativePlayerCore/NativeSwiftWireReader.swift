import Foundation

/// Bounds-checked reader for the Remote Compose operation stream. Kept separate from the decoder
/// so malformed-input rules are auditable without navigating snapshot/session resolution.
///
/// Every read names its field for the error it may throw. The name is an `@autoclosure` so the
/// success path -- almost every read -- never builds it: an interpolated label such as
/// `"header property \(index)"` used to allocate a string per word read, error or not.
struct WireReader {
  /// The operation stream as a plain byte array rather than the `Data` it arrives as. The decoder
  /// indexes it a byte at a time, several times over for captured bodies, and `Data`'s subscript
  /// dispatches through its inline/slice/large representations and carries its parent's indices
  /// when it is a slice. One up-front copy per stream is cheaper than that dispatch on every byte,
  /// and it keeps every offset zero-based, which is what the errors report.
  private let bytes: [UInt8]
  private(set) var offset = 0

  init(_ data: Data) { bytes = Array(data) }
  var isAtEnd: Bool { offset == bytes.count }

  func rawBytes(from start: Int, to end: Int) -> Data {
    Data(bytes[start..<end])
  }

  mutating func u8(_ field: @autoclosure () -> String) throws -> Int {
    guard offset < bytes.count else { throw malformed("Unexpected end while reading \(field())") }
    defer { offset += 1 }
    return Int(bytes[offset])
  }

  mutating func signedU16(_ field: @autoclosure () -> String) throws -> Int {
    Int(Int16(bitPattern: try u16(field())))
  }

  mutating func u16(_ field: @autoclosure () -> String) throws -> UInt16 {
    guard bytes.count - offset >= 2 else {
      throw malformed("Unexpected end while reading \(field())")
    }
    defer { offset += 2 }
    return UInt16(bytes[offset]) << 8 | UInt16(bytes[offset + 1])
  }

  mutating func int(_ field: @autoclosure () -> String) throws -> Int {
    Int(Int32(bitPattern: try uint32(field())))
  }

  mutating func word(_ field: @autoclosure () -> String) throws -> UInt32 { try uint32(field()) }

  mutating func dimensionType(_ field: @autoclosure () -> String) throws -> Int {
    let value = try int(field())
    guard (0...8).contains(value) else { throw malformed("Invalid \(field()) \(value)") }
    return value
  }

  mutating func count(_ field: @autoclosure () -> String, maximum: Int) throws -> Int {
    let value = try int(field())
    guard value >= 0, value <= maximum else {
      throw malformed("\(field()) \(value) is outside 0...\(maximum)")
    }
    return value
  }

  /// A float word that must be a literal when `requireLiteral` is set. `opcode` is the operation
  /// being read, which a refused reference is reported against.
  mutating func floatWord(
    _ field: @autoclosure () -> String, requireLiteral: Bool, opcode: Int
  ) throws -> Float {
    let value = Float(bitPattern: try uint32(field()))
    if value.isNaN, requireLiteral {
      throw NativeSwiftCoreError.unsupported(
        opcode: opcode, offset: offset - 4, reason: "dynamic float \(field())")
    }
    guard !requireLiteral || value.isFinite else { throw malformed("\(field()) must be finite") }
    return value
  }

  mutating func utf8(_ field: @autoclosure () -> String, maximum: Int) throws -> String {
    try string(field(), length: count("\(field()) length", maximum: maximum))
  }

  mutating func data(_ field: @autoclosure () -> String, maximum: Int) throws -> Data {
    let length = try count("\(field()) length", maximum: maximum)
    return try rawData(field(), length: length)
  }

  mutating func rawData(_ field: @autoclosure () -> String, length: Int) throws -> Data {
    guard length >= 0, bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field())")
    }
    defer { offset += length }
    return Data(bytes[offset..<(offset + length)])
  }

  mutating func longAsInt(_ field: @autoclosure () -> String) throws -> Int {
    let value = UInt64(try word("\(field()) high word")) << 32
      | UInt64(try word("\(field()) low word"))
    if (0x1_0000_0000...0x1_003f_ffff).contains(value) {
      return Int(value - 0x1_0000_0000)
    }
    guard value <= UInt64(Int.max) else { throw malformed("\(field()) is outside Int range") }
    return Int(value)
  }

  func malformed(_ reason: String) -> NativeSwiftCoreError {
    .malformed(offset: offset, reason: reason)
  }

  private mutating func uint32(_ field: @autoclosure () -> String) throws -> UInt32 {
    guard bytes.count - offset >= 4 else {
      throw malformed("Unexpected end while reading \(field())")
    }
    defer { offset += 4 }
    return UInt32(bytes[offset]) << 24 | UInt32(bytes[offset + 1]) << 16
      | UInt32(bytes[offset + 2]) << 8 | UInt32(bytes[offset + 3])
  }

  private mutating func string(_ field: @autoclosure () -> String, length: Int) throws -> String {
    guard bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field())")
    }
    let value = String(bytes: bytes[offset..<(offset + length)], encoding: .utf8)
    offset += length
    guard let value else { throw malformed("\(field()) is not valid UTF-8") }
    return value
  }
}

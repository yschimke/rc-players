import Foundation

/// Bounds-checked reader for the Remote Compose operation stream. Kept separate from the decoder
/// so malformed-input rules are auditable without navigating snapshot/session resolution.
struct WireReader {
  private let bytes: [UInt8]
  private(set) var offset = 0

  init(_ data: Data) { bytes = Array(data) }
  var isAtEnd: Bool { offset == bytes.count }

  func rawBytes(from start: Int, to end: Int) -> Data {
    Data(bytes[start..<end])
  }

  mutating func u8(_ field: String) throws -> Int {
    guard offset < bytes.count else { throw malformed("Unexpected end while reading \(field)") }
    defer { offset += 1 }
    return Int(bytes[offset])
  }

  mutating func signedU16(_ field: String) throws -> Int {
    Int(Int16(bitPattern: try u16(field)))
  }

  mutating func u16(_ field: String) throws -> UInt16 {
    guard bytes.count - offset >= 2 else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += 2 }
    return UInt16(bytes[offset]) << 8 | UInt16(bytes[offset + 1])
  }

  mutating func int(_ field: String) throws -> Int { Int(Int32(bitPattern: try uint32(field))) }
  mutating func word(_ field: String) throws -> UInt32 { try uint32(field) }

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

  mutating func floatWord(_ field: String, requireLiteral: Bool) throws -> Float {
    let value = Float(bitPattern: try uint32(field))
    if value.isNaN, requireLiteral {
      throw NativeSwiftCoreError.unsupported(
        opcode: -1, offset: offset - 4, reason: "dynamic float \(field)")
    }
    guard !requireLiteral || value.isFinite else { throw malformed("\(field) must be finite") }
    return value
  }

  mutating func utf8(_ field: String, maximum: Int) throws -> String {
    try string(field, length: count("\(field) length", maximum: maximum))
  }

  mutating func data(_ field: String, maximum: Int) throws -> Data {
    let length = try count("\(field) length", maximum: maximum)
    return try rawData(field, length: length)
  }

  mutating func rawData(_ field: String, length: Int) throws -> Data {
    guard length >= 0, bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += length }
    return Data(bytes[offset..<(offset + length)])
  }

  mutating func longAsInt(_ field: String) throws -> Int {
    let value = UInt64(try word("\(field) high word")) << 32
      | UInt64(try word("\(field) low word"))
    if (0x1_0000_0000...0x1_003f_ffff).contains(value) {
      return Int(value - 0x1_0000_0000)
    }
    guard value <= UInt64(Int.max) else { throw malformed("\(field) is outside Int range") }
    return Int(value)
  }

  mutating func utf8Bytes(_ field: String, length: Int) throws -> String {
    guard length >= 0, length <= nativeSwiftMaximumStringBytes,
      bytes.count - offset >= length
    else {
      throw malformed("Invalid \(field) length")
    }
    return try string(field, length: length)
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

  private mutating func string(_ field: String, length: Int) throws -> String {
    guard bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field)")
    }
    let value = String(bytes: bytes[offset..<(offset + length)], encoding: .utf8)
    offset += length
    guard let value else { throw malformed("\(field) is not valid UTF-8") }
    return value
  }
}

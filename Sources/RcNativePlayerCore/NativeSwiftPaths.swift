import Foundation

struct ParsedPath {
  let winding: Int
  let words: [UInt32]

  /// Whether any of this path's *argument* words references one of `ids`.
  ///
  /// The walk is structural on purpose. A path's command tokens are NaN-boxed ids 10...16, which
  /// collide with the system-variable ids in that range, so a flat scan over the words would report
  /// `OFFSET_TO_UTC` (10) on any document that draws a path at all.
  func references(anyOf ids: Set<Int>) -> Bool {
    func matches(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.referenceID(word).map(ids.contains) ?? false
    }
    var index = 0
    while index < words.count {
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else { return false }
      index += 1
      let padding: Int
      let argumentCount: Int
      switch command {
      case 10:
        padding = 0
        argumentCount = 2
      case 11:
        padding = 2
        argumentCount = 2
      case 12:
        padding = 2
        argumentCount = 4
      case 13:
        padding = 2
        argumentCount = 5
      case 14:
        padding = 2
        argumentCount = 6
      case 15:
        padding = 0
        argumentCount = 0
      case 16:
        return false
      default:
        return false
      }
      index += padding
      guard index + argumentCount <= words.count else { return false }
      for offset in 0..<argumentCount where matches(words[index + offset]) { return true }
      index += argumentCount
    }
    return false
  }

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

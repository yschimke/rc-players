import Foundation

struct ParsedPath {
  /// The operation that wrote a run of `words`: from `firstWord` up to the next origin's. A path
  /// can be declared by one operation and extended by others, and a word that fails to resolve is
  /// reported against the operation that wrote it.
  struct Origin {
    let firstWord: Int
    let opcode: Int
    let offset: Int
  }

  let winding: Int
  let words: [UInt32]
  /// Never empty: every path starts with the operation that declared it.
  let origins: [Origin]

  init(winding: Int, words: [UInt32], opcode: Int, offset: Int) {
    self.winding = winding
    self.words = words
    origins = [Origin(firstWord: 0, opcode: opcode, offset: offset)]
  }

  private init(winding: Int, words: [UInt32], origins: [Origin]) {
    self.winding = winding
    self.words = words
    self.origins = origins
  }

  /// This path with `more` words appended by the operation at `offset`.
  func appending(_ more: [UInt32], opcode: Int, offset: Int) -> ParsedPath {
    ParsedPath(
      winding: winding, words: words + more,
      origins: origins + [Origin(firstWord: words.count, opcode: opcode, offset: offset)])
  }

  /// The operation that wrote the word at `index`.
  private func origin(ofWord index: Int) -> Origin {
    origins.last { $0.firstWord <= index } ?? origins[0]
  }

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
      case NativeSwiftPathVerb.move:
        padding = 0
        argumentCount = 2
      case NativeSwiftPathVerb.line:
        padding = 2
        argumentCount = 2
      case NativeSwiftPathVerb.quadratic:
        padding = 2
        argumentCount = 4
      case NativeSwiftPathVerb.conic:
        padding = 2
        argumentCount = 5
      case NativeSwiftPathVerb.cubic:
        padding = 2
        argumentCount = 6
      case NativeSwiftPathVerb.close:
        padding = 0
        argumentCount = 0
      case NativeSwiftPathVerb.done:
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
    // The command word being resolved; a failure is reported against the operation that wrote it.
    var commandIndex = 0
    func arguments(_ count: Int, skippingLegacyPadding: Bool = false) throws -> [Float] {
      if skippingLegacyPadding { index += 2 }
      guard index >= 0, index + count <= words.count else {
        throw NativeSwiftCoreError.malformed(
          offset: origin(ofWord: commandIndex).offset, reason: "Truncated path data")
      }
      let resolved = words[index..<(index + count)].map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      }
      index += count
      return resolved
    }
    while index < words.count {
      commandIndex = index
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else {
        throw NativeSwiftCoreError.malformed(
          offset: origin(ofWord: index).offset, reason: "Path command is not encoded")
      }
      index += 1
      switch command {
      case NativeSwiftPathVerb.move:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: try arguments(2)))
      case NativeSwiftPathVerb.line:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(2, skippingLegacyPadding: true)))
      case NativeSwiftPathVerb.quadratic:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(4, skippingLegacyPadding: true)))
      case NativeSwiftPathVerb.conic:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(5, skippingLegacyPadding: true)))
      case NativeSwiftPathVerb.cubic:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(6, skippingLegacyPadding: true)))
      case NativeSwiftPathVerb.close:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: []))
      case NativeSwiftPathVerb.done: return result
      default:
        let source = origin(ofWord: commandIndex)
        throw NativeSwiftCoreError.unsupported(
          opcode: source.opcode, offset: source.offset, reason: "path command \(command)")
      }
    }
    return result
  }
}

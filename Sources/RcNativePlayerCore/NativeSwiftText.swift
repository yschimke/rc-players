import Foundation

struct ParsedTextFromFloat {
  let outputID: Int
  let value: UInt32
  let digitsBefore: Int
  let digitsAfter: Int
  let flags: Int
}

struct ParsedTextMerge {
  let outputID: Int
  let leftID: Int
  let rightID: Int
}

struct ParsedTextLookupInt {
  let outputID: Int
  let listID: Int
  let indexID: Int
}

struct ParsedTextLookup {
  let outputID: Int
  let listID: Int
  let index: UInt32
}

struct ParsedTextTransform {
  let outputID: Int
  let textID: Int
  let start: UInt32
  let length: UInt32
  let operation: Int
}

enum ParsedTextOperation {
  case fromFloat(ParsedTextFromFloat)
  case merge(ParsedTextMerge)
  case lookupInt(ParsedTextLookupInt)
  case lookup(ParsedTextLookup)
  case transform(ParsedTextTransform)
  /// `TEXT_SUBTEXT`: a slice by UTF-16 offset, where a length of -1 means "to the end".
  case subtext(ParsedTextTransform)
}

/// `ID_LOOKUP`: the id at a (computed) index of an id list, published as an integer.
struct ParsedIdLookup {
  let outputID: Int
  let listID: Int
  let index: UInt32
}

/// A float the host-independent text attributes publish: `TEXT_LENGTH`, and `ATTRIBUTE_TEXT`'s
/// length selector. The bounds selectors need the host's text metrics and are not in this list.
struct ParsedTextLength {
  let outputID: Int
  let textID: Int
}

/// `TEXT_STYLE` and `CoreText`'s shared sparse property vocabulary, as decoded.
struct ParsedTextProperties {
  var integers: [Int: Int] = [:]
  var floats: [Int: UInt32] = [:]
}

struct ParsedText {
  let textID: Int
  let colorARGB: UInt32
  let colorID: Int?
  let sizeWord: UInt32
  let style: Int
  let weightWord: UInt32
  let familyID: Int
  let alignment: Int
  let overflow: Int
  let maximumLines: Int
}

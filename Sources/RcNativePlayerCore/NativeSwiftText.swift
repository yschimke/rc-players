import Foundation

struct ParsedTextFromFloat {
  let outputID: Int
  let value: UInt32
  let digitsAfter: Int
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

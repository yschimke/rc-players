import Foundation
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

enum NativeTextDirection {
  case leftToRight
  case rightToLeft
}

enum NativeResolvedTextAlignment: Equatable {
  case left
  case right
  case center
  case justified
}

enum NativeResolvedTextOverflow: Equatable {
  case clip
  case visible
  case tail
  case head
  case middle
}

/// A `CoreText` component's laid-out lines, and the width of the widest without its trailing
/// whitespace.
struct NativeTextLines: Equatable {
  var lines: [String]
  var width: Double
}

enum NativeResolvedLineBreak: Equatable {
  case clip
  case wordWrap
  case tail
  case head
  case middle
}

enum NativeTextPolicy {
  static func alignment(
    value: Int, justified: Bool, direction: NativeTextDirection
  ) -> NativeResolvedTextAlignment {
    if justified { return .justified }
    switch value {
    case NativeSwiftTextAlignment.left: return .left
    case NativeSwiftTextAlignment.right: return .right
    case NativeSwiftTextAlignment.center: return .center
    case NativeSwiftTextAlignment.end: return direction == .rightToLeft ? .left : .right
    default: return direction == .rightToLeft ? .right : .left
    }
  }

  static func overflow(_ value: Int) -> NativeResolvedTextOverflow {
    switch value {
    case NativeSwiftTextOverflow.visible: return .visible
    case NativeSwiftTextOverflow.ellipsis: return .tail
    case NativeSwiftTextOverflow.startEllipsis: return .head
    case NativeSwiftTextOverflow.middleEllipsis: return .middle
    default: return .clip
    }
  }

  static func numberOfLines(overflow: Int, maximum: Int) -> Int {
    maximum > 1 && (overflow == NativeSwiftTextOverflow.clip || overflow == NativeSwiftTextOverflow.visible)
      ? 0 : max(maximum, 1)
  }

  /// The lines a `CoreText` component lays its text out in, and the width they take: the geometry
  /// the corpus's `tree` probe reads, independent of how a host then draws them.
  ///
  /// The reference's rules, as the TypeScript player (which passes every `core_text_*` gold)
  /// implements them over its `measureText`:
  /// - One line under clip or visible stays one unwrapped line, cut at a hard break.
  /// - Otherwise the text wraps at word boundaries, hard breaks included. A multi-line clip or
  ///   visible layout ignores `maxLines`, as the Java player does.
  /// - Under an ellipsis the last kept line — when `maxLines` drops lines, or that line alone
  ///   overruns — loses characters until it fits with its ellipsis.
  /// - A line's width leaves out its trailing whitespace: `core_text_multiline_wrap` records the
  ///   wrapped "Ahem font " as 144 points of Ahem 16, not 160. The widest line, bounded by
  ///   `maxWidth`, is the layout's width.
  ///
  /// - Parameter measure: a run's advance width in the text's font.
  static func layoutLines(
    _ text: String, maxWidth: Double, maxLines: Int, overflow: Int,
    measure: (String) -> Double
  ) -> NativeTextLines {
    func width(of line: Substring) -> Double {
      var end = line.endIndex
      while end > line.startIndex, line[line.index(before: end)].isWhitespace {
        end = line.index(before: end)
      }
      return measure(String(line[..<end]))
    }
    if maxLines == 1
      && (overflow == NativeSwiftTextOverflow.clip || overflow == NativeSwiftTextOverflow.visible)
    {
      let first =
        text.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false).first
        ?? Substring(text)
      return NativeTextLines(
        lines: [String(first)], width: min(width(of: first), maxWidth))
    }
    var lines: [String] = []
    for paragraph in text.split(separator: "\n", omittingEmptySubsequences: false) {
      var current = ""
      for token in whitespaceTokens(paragraph) {
        let candidate = current + token
        if measure(candidate) > maxWidth && !current.isEmpty {
          lines.append(current)
          current = String(token.drop { $0.isWhitespace })
        } else {
          current = candidate
        }
      }
      if !current.isEmpty || paragraph.isEmpty { lines.append(current) }
    }
    let ellipsizes = (NativeSwiftTextOverflow.ellipsis...NativeSwiftTextOverflow.middleEllipsis)
      .contains(overflow)
    if ellipsizes, maxLines > 0, !lines.isEmpty {
      let last = min(lines.count, maxLines) - 1
      if lines.count > maxLines || measure(lines[last]) > maxWidth {
        lines[last] = ellipsize(
          lines[last], overflow: overflow, maxWidth: maxWidth, measure: measure)
        lines.removeSubrange((last + 1)...)
      }
    }
    let widest = lines.map { width(of: Substring($0)) }.max() ?? 0
    return NativeTextLines(lines: lines, width: min(widest, maxWidth))
  }

  /// Words and the whitespace runs between them, in order, each run its own token.
  private static func whitespaceTokens(_ paragraph: Substring) -> [Substring] {
    var tokens: [Substring] = []
    var start = paragraph.startIndex
    var index = start
    while index < paragraph.endIndex {
      let next = paragraph.index(after: index)
      if next < paragraph.endIndex, paragraph[next].isWhitespace != paragraph[index].isWhitespace {
        tokens.append(paragraph[start..<next])
        start = next
      }
      index = next
    }
    if start < paragraph.endIndex { tokens.append(paragraph[start...]) }
    return tokens
  }

  /// Drops characters from the end, the start or the middle until the line and its ellipsis fit.
  private static func ellipsize(
    _ line: String, overflow: Int, maxWidth: Double, measure: (String) -> Double
  ) -> String {
    var parts = Array(line)
    let ellipsis = "\u{2026}"
    switch overflow {
    case NativeSwiftTextOverflow.startEllipsis:
      while !parts.isEmpty, measure(ellipsis + String(parts)) > maxWidth { parts.removeFirst() }
      return ellipsis + String(parts)
    case NativeSwiftTextOverflow.middleEllipsis:
      var left = (parts.count + 1) / 2
      var right = left
      func joined() -> String { String(parts[..<left]) + ellipsis + String(parts[right...]) }
      while measure(joined()) > maxWidth, left > 0 || right < parts.count {
        if parts.count - right < left { left -= 1 } else { right += 1 }
      }
      return joined()
    default:
      while !parts.isEmpty, measure(String(parts) + ellipsis) > maxWidth { parts.removeLast() }
      return String(parts) + ellipsis
    }
  }

  static func lineBreak(overflow: Int) -> NativeResolvedLineBreak {
    switch self.overflow(overflow) {
    case .clip: return .clip
    case .visible: return .wordWrap
    case .tail: return .tail
    case .head: return .head
    case .middle: return .middle
    }
  }
}

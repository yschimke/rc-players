import Foundation
import RcNativePlayerCore
import Testing

@testable import RcNativePlayerUIKit

@Suite struct NativeTextPolicyTests {
  @Test func textPolicy() {
    #expect(
      NativeTextPolicy.alignment(value: 5, justified: false, direction: .leftToRight) == .left)
    #expect(
      NativeTextPolicy.alignment(value: 5, justified: false, direction: .rightToLeft) == .right)
    #expect(
      NativeTextPolicy.alignment(value: 6, justified: false, direction: .rightToLeft) == .left)
    #expect(
      NativeTextPolicy.alignment(value: 1, justified: true, direction: .leftToRight) == .justified)
    #expect(NativeTextPolicy.overflow(3) == .tail)
    #expect(NativeTextPolicy.overflow(4) == .head)
    #expect(NativeTextPolicy.overflow(5) == .middle)
    #expect(NativeTextPolicy.lineBreak(overflow: 1) == .clip)
    #expect(NativeTextPolicy.lineBreak(overflow: 2) == .wordWrap)
    #expect(NativeTextPolicy.lineBreak(overflow: 3) == .tail)
    #expect(NativeTextPolicy.lineBreak(overflow: 4) == .head)
    #expect(NativeTextPolicy.lineBreak(overflow: 5) == .middle)
    #expect(NativeTextPolicy.numberOfLines(overflow: 1, maximum: 1) == 1)
    #expect(NativeTextPolicy.numberOfLines(overflow: 1, maximum: 4) == 0)
    #expect(NativeTextPolicy.numberOfLines(overflow: 2, maximum: 4) == 0)
    #expect(NativeTextPolicy.numberOfLines(overflow: 3, maximum: 4) == 4)

    let scripts = ["Latin", "日本語", "مرحبا", "👟🏃🏽‍♀️"]
    let allEncoded = scripts.allSatisfy { !$0.isEmpty && !$0.utf16.isEmpty }
    #expect(allEncoded)
  }

  /// The explicit values the AppKit renderer used to swap or misread (#427): right and center keep
  /// their own sides, `visible` wraps and only `ellipsis` truncates.
  @Test func explicitAlignmentAndOverflowValues() {
    #expect(
      NativeTextPolicy.alignment(
        value: NativeSwiftTextAlignment.right, justified: false, direction: .leftToRight) == .right)
    #expect(
      NativeTextPolicy.alignment(
        value: NativeSwiftTextAlignment.center, justified: false, direction: .leftToRight)
        == .center)
    #expect(
      NativeTextPolicy.alignment(
        value: NativeSwiftTextAlignment.right, justified: false, direction: .rightToLeft) == .right)
    #expect(
      NativeTextPolicy.alignment(
        value: NativeSwiftTextAlignment.end, justified: false, direction: .leftToRight) == .right)
    #expect(NativeTextPolicy.overflow(NativeSwiftTextOverflow.visible) == .visible)
    #expect(NativeTextPolicy.lineBreak(overflow: NativeSwiftTextOverflow.visible) == .wordWrap)
    #expect(NativeTextPolicy.lineBreak(overflow: NativeSwiftTextOverflow.ellipsis) == .tail)
    #expect(NativeTextPolicy.lineBreak(overflow: NativeSwiftTextOverflow.clip) == .clip)
  }

  /// The `core_text_*` golds' own geometry, in Ahem: every character, space and ellipsis one em.
  @Test func linesFollowTheReferenceWrapAndEllipsis() {
    func layout(_ text: String, em: Double, width: Double, maxLines: Int = .max, overflow: Int)
      -> (lines: [String], width: Double)
    {
      let result = NativeTextPolicy.layoutLines(
        text, maxWidth: width, maxLines: maxLines, overflow: overflow
      ) { Double($0.count) * em }
      return (result.lines, result.width)
    }
    let clip = NativeSwiftTextOverflow.clip
    let ellipsis = NativeSwiftTextOverflow.ellipsis
    // `core_text_simple`: one line, as wide as its glyphs.
    #expect(layout("Hello RemoteCompose", em: 12, width: 300, overflow: clip).width == 228)
    // `core_text_multiline_wrap`: a wrapped line's trailing space is not part of its width.
    let wrapped = layout("Ahem font multiline test wrapping", em: 16, width: 200, overflow: clip)
    #expect(wrapped.lines.count == 4 && wrapped.width == 144)
    let wider = layout("Ahem font multiline test wrapping", em: 16, width: 400, overflow: clip)
    #expect(wider.lines.count == 2 && wider.width == 384)
    // `core_text_overflow_ellipsis`: two lines, the second ellipsized to fit.
    let text = "Ahem font wraps and truncates with ellipsis"
    let narrow = layout(text, em: 16, width: 160, maxLines: 2, overflow: ellipsis)
    #expect(narrow.lines == ["Ahem font ", "wraps and\u{2026}"] && narrow.width == 160)
    let broad = layout(text, em: 16, width: 320, maxLines: 2, overflow: ellipsis)
    #expect(broad.lines.count == 2 && broad.width == 304)
    // One clipped line is not wrapped, and a hard break still ends it.
    #expect(layout("a b c\nd", em: 10, width: 20, maxLines: 1, overflow: clip).lines == ["a b c"])
    let head = layout(
      "abcdefghij", em: 10, width: 50, maxLines: 1, overflow: NativeSwiftTextOverflow.startEllipsis)
    #expect(head.lines == ["\u{2026}ghij"])
  }
}

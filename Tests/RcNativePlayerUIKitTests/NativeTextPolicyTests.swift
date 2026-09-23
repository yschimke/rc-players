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
}

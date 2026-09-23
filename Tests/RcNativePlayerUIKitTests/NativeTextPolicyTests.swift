import Foundation
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
}

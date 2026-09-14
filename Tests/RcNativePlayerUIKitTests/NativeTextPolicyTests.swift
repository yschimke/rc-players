import Foundation

@main
enum NativeTextPolicyTests {
  static func main() {
    precondition(
      NativeTextPolicy.alignment(value: 5, justified: false, direction: .leftToRight) == .left)
    precondition(
      NativeTextPolicy.alignment(value: 5, justified: false, direction: .rightToLeft) == .right)
    precondition(
      NativeTextPolicy.alignment(value: 6, justified: false, direction: .rightToLeft) == .left)
    precondition(
      NativeTextPolicy.alignment(value: 1, justified: true, direction: .leftToRight) == .justified)
    precondition(NativeTextPolicy.overflow(3) == .tail)
    precondition(NativeTextPolicy.overflow(4) == .head)
    precondition(NativeTextPolicy.overflow(5) == .middle)
    precondition(NativeTextPolicy.lineBreak(overflow: 1) == .clip)
    precondition(NativeTextPolicy.lineBreak(overflow: 2) == .wordWrap)
    precondition(NativeTextPolicy.lineBreak(overflow: 3) == .tail)
    precondition(NativeTextPolicy.lineBreak(overflow: 4) == .head)
    precondition(NativeTextPolicy.lineBreak(overflow: 5) == .middle)
    precondition(NativeTextPolicy.numberOfLines(overflow: 1, maximum: 1) == 1)
    precondition(NativeTextPolicy.numberOfLines(overflow: 1, maximum: 4) == 0)
    precondition(NativeTextPolicy.numberOfLines(overflow: 2, maximum: 4) == 0)
    precondition(NativeTextPolicy.numberOfLines(overflow: 3, maximum: 4) == 4)

    let scripts = ["Latin", "日本語", "مرحبا", "👟🏃🏽‍♀️"]
    precondition(scripts.allSatisfy { !$0.isEmpty && !$0.utf16.isEmpty })
    print("native UIKit text policy tests: ok")
  }
}

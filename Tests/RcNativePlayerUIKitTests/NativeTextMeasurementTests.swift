#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import UIKit

  @testable import RcNativePlayerUIKit

  /// `NativeTextLabel` measures without a window: configuring is `apply(documentScale:)`, and
  /// measuring only reads what that built.
  @MainActor @Suite struct NativeTextMeasurementTests {
    private static let words =
      "The quick brown fox jumps over the lazy dog and keeps running past the fence"

    private func makeLabel(
      overflow: Int = NativeSwiftTextOverflow.ellipsis, maximumLines: Int = 2
    ) -> NativeTextLabel {
      let snapshot = NativeSwiftTextSnapshot(
        value: Self.words, colorARGB: 0xff00_0000, size: 16, style: 0, weight: 400,
        familyID: -1, alignment: .center, overflow: overflow, maximumLines: maximumLines)
      return NativeTextLabel(
        componentID: 1, commandIndex: 0, command: NativeDrawCommand(text: snapshot),
        fontNames: [:])
    }

    @Test func measuringTwiceGivesTheSameSizeAndLeavesTheLabelAlone() {
      let label = makeLabel()
      label.apply(documentScale: 2)
      let font = label.font
      let text = label.attributedText
      let lines = label.numberOfLines
      let lineBreak = label.lineBreakMode
      let frame = label.frame

      let first = label.preferredSize(maximumWidth: 120)
      let second = label.preferredSize(maximumWidth: 120)

      #expect(first == second)
      #expect(first.width > 0 && first.width <= 120)
      #expect(first.height > 0)
      #expect(label.font == font)
      #expect(label.attributedText == text)
      #expect(label.numberOfLines == lines)
      #expect(label.lineBreakMode == lineBreak)
      #expect(label.frame == frame)
    }

    @Test func measuringDoesNotConfigureTheLabel() {
      let label = makeLabel()
      let font = label.font
      let text = label.attributedText
      _ = label.preferredSize(maximumWidth: 120)
      #expect(label.font == font)
      #expect(label.attributedText == text)
    }

    /// The label keeps one TextKit stack across widths; laying it out at another width in between
    /// must not change what an earlier width measures, and must match a stack built for that width.
    @Test func aReusedStackMeasuresAsAFreshOne() {
      for overflow in [
        NativeSwiftTextOverflow.clip, NativeSwiftTextOverflow.ellipsis,
        NativeSwiftTextOverflow.startEllipsis, NativeSwiftTextOverflow.middleEllipsis,
      ] {
        for maximumLines in [1, 2, Int.max] {
          let reused = makeLabel(overflow: overflow, maximumLines: maximumLines)
          reused.apply(documentScale: 1)
          let widths: [CGFloat] = [1000, 80, 37, 240, 80]
          let sizes = widths.map { reused.preferredSize(maximumWidth: $0) }
          for (width, size) in zip(widths, sizes) {
            let fresh = makeLabel(overflow: overflow, maximumLines: maximumLines)
            fresh.apply(documentScale: 1)
            #expect(fresh.preferredSize(maximumWidth: width) == size)
          }
        }
      }
    }

    @Test func applyingAnotherScaleRemeasures() {
      let label = makeLabel(maximumLines: Int.max)
      label.apply(documentScale: 1)
      let small = label.preferredSize(maximumWidth: 200)
      label.apply(documentScale: 2)
      let large = label.preferredSize(maximumWidth: 200)
      #expect(large.height > small.height)
      label.apply(documentScale: 1)
      #expect(label.preferredSize(maximumWidth: 200) == small)
    }

    @Test func emptyWidthMeasuresNothing() {
      let label = makeLabel()
      label.apply(documentScale: 1)
      #expect(label.preferredSize(maximumWidth: 0) == .zero)
    }
  }
#endif

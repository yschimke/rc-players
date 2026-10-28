#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import UIKit

  @testable import RcNativePlayerUIKit

  /// `NativeTextLabel` measures without a window: configuring is
  /// `apply(documentScale:layoutDensityScale:)`, and measuring only reads what that built.
  @MainActor @Suite struct NativeTextMeasurementTests {
    private static let words =
      "The quick brown fox jumps over the lazy dog and keeps running past the fence"

    private func makeLabel(
      overflow: Int = NativeSwiftTextOverflow.ellipsis, maximumLines: Int = 2,
      sizeIsDensityTyped: Bool = false
    ) -> NativeTextLabel {
      let snapshot = NativeSwiftTextSnapshot(
        value: Self.words, colorARGB: 0xff00_0000, size: 16, style: 0, weight: 400,
        familyID: -1, alignment: .center, overflow: overflow, maximumLines: maximumLines,
        sizeIsDensityTyped: sizeIsDensityTyped)
      return NativeTextLabel(
        componentID: 1, commandIndex: 0, command: NativeDrawCommand(text: snapshot),
        fontNames: [:])
    }

    @Test func measuringTwiceGivesTheSameSizeAndLeavesTheLabelAlone() {
      let label = makeLabel()
      label.apply(documentScale: 2, layoutDensityScale: 1)
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

    /// `CoreText`'s font settings reach the label's font: `tnum` and `pnum` set a run of ones to
    /// different widths, which they cannot do unless the features are shaped.
    @Test func fontFeaturesReachTheFont() {
      func width(_ settings: [NativeSwiftFontSetting]) -> CGFloat {
        let snapshot = NativeSwiftTextSnapshot(
          value: "1111111111", colorARGB: 0xff00_0000, size: 16, style: 0, weight: 400,
          familyID: -1, alignment: .left, overflow: NativeSwiftTextOverflow.clip,
          maximumLines: 1, fontSettings: settings)
        let label = NativeTextLabel(
          componentID: 1, commandIndex: 0, command: NativeDrawCommand(text: snapshot),
          fontNames: [:])
        label.apply(documentScale: 1, layoutDensityScale: 1)
        return label.preferredSize(maximumWidth: 1_000).width
      }
      let tabular = width([NativeSwiftFontSetting(tag: "tnum", value: 1)])
      let proportional = width([NativeSwiftFontSetting(tag: "pnum", value: 1)])
      #expect(
        tabular != proportional,
        Comment(rawValue: "tnum measured \(tabular) and pnum \(proportional)"))
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
          reused.apply(documentScale: 1, layoutDensityScale: 1)
          let widths: [CGFloat] = [1000, 80, 37, 240, 80]
          let sizes = widths.map { reused.preferredSize(maximumWidth: $0) }
          for (width, size) in zip(widths, sizes) {
            let fresh = makeLabel(overflow: overflow, maximumLines: maximumLines)
            fresh.apply(documentScale: 1, layoutDensityScale: 1)
            #expect(fresh.preferredSize(maximumWidth: width) == size)
          }
        }
      }
    }

    @Test func applyingAnotherScaleRemeasures() {
      let label = makeLabel(maximumLines: Int.max)
      label.apply(documentScale: 1, layoutDensityScale: 1)
      let small = label.preferredSize(maximumWidth: 200)
      label.apply(documentScale: 2, layoutDensityScale: 1)
      let large = label.preferredSize(maximumWidth: 200)
      #expect(large.height > small.height)
      label.apply(documentScale: 1, layoutDensityScale: 1)
      #expect(label.preferredSize(maximumWidth: 200) == small)
    }

    /// A DP document's literal font size is dp-typed (remote-core alpha22), so it resolves with the
    /// dp-typed geometry scale, as padding does; any other size keeps the document scale.
    @Test func aDensityTypedFontSizeResolvesWithTheLayoutDensityScale() {
      func pointSize(typed: Bool, documentScale: CGFloat, layoutDensityScale: CGFloat) -> CGFloat {
        let label = makeLabel(sizeIsDensityTyped: typed)
        label.apply(documentScale: documentScale, layoutDensityScale: layoutDensityScale)
        return label.font.pointSize
      }
      let doubled = pointSize(typed: false, documentScale: 2, layoutDensityScale: 1)
      let plain = pointSize(typed: false, documentScale: 1, layoutDensityScale: 1)
      #expect(doubled > plain)
      #expect(pointSize(typed: true, documentScale: 1, layoutDensityScale: 2) == doubled)
      #expect(pointSize(typed: true, documentScale: 1, layoutDensityScale: 1) == plain)
      #expect(pointSize(typed: false, documentScale: 1, layoutDensityScale: 2) == plain)

      // A change of the density scale alone rebuilds the font of a dp-typed label.
      let label = makeLabel(sizeIsDensityTyped: true)
      label.apply(documentScale: 1, layoutDensityScale: 1)
      label.apply(documentScale: 1, layoutDensityScale: 2)
      #expect(label.font.pointSize == doubled)
    }

    @Test func emptyWidthMeasuresNothing() {
      let label = makeLabel()
      label.apply(documentScale: 1, layoutDensityScale: 1)
      #expect(label.preferredSize(maximumWidth: 0) == .zero)
    }
  }
#endif

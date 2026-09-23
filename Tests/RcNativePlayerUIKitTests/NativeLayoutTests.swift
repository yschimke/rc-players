import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerUIKit

@Suite struct NativeLayoutTests {
  @Test func layout() {
    assertClose(
      NativeLayoutDimension(type: 0, value: 80, minimum: 20, maximum: 60)
        .resolve(intrinsic: 10, available: 100),
      60)
    assertClose(
      NativeLayoutDimension(type: 2, value: 0, minimum: 24, maximum: 90)
        .resolve(intrinsic: 12, available: 100),
      24)
    assertClose(
      NativeLayoutDimension(type: 1, value: 0.5, minimum: 0, maximum: nil)
        .resolve(intrinsic: 12, available: 200),
      100)
    // An explicit zero fill fraction is zero; a bare `fillMaxWidth()` arrives from the core as 1.
    assertClose(
      NativeLayoutDimension(type: 1, value: 0, minimum: 0, maximum: nil)
        .resolve(intrinsic: 12, available: 200),
      0)

    let weighted = NativeLinearLayout.allocateWeighted(
      available: 200,
      naturalSizes: [30, 0, 0],
      weights: [nil, 1, 2])
    assertEqual(weighted, [30, 56.666_667, 113.333_333])

    // A collapsible container's weights divide its axis less the gaps placement adds, so a 60-point
    // column with a 10-point gap and 1:2 weights allocates 16.67 and 33.33 rather than 20 and 40 —
    // otherwise the shares fill the axis and the last child is clipped by the total gap.
    assertClose(
      NativeLinearLayout.collapsibleWeightSpace(extent: 60, count: 2, spacing: 10), 50)
    assertClose(
      NativeLinearLayout.collapsibleWeightSpace(extent: 60, count: 1, spacing: 10), 60)
    assertClose(
      NativeLinearLayout.collapsibleWeightSpace(extent: 60, count: 3, spacing: 10), 40)
    assertClose(
      NativeLinearLayout.collapsibleWeightSpace(extent: 8, count: 2, spacing: 10), 0)
    let collapsibleWeights = NativeLinearLayout.allocateWeighted(
      available: NativeLinearLayout.collapsibleWeightSpace(extent: 60, count: 2, spacing: 10),
      naturalSizes: [0, 0],
      weights: [1, 2])
    assertEqual(collapsibleWeights, [16.666_667, 33.333_333])
    // An ordinary row or column keeps the additive rule, where the gaps do not reduce the shares.
    let ordinaryWeights = NativeLinearLayout.allocateWeighted(
      available: NativeLinearLayout.collapsibleWeightSpace(
        extent: 60, count: 2, spacing: 0),
      naturalSizes: [0, 0],
      weights: [1, 2])
    assertEqual(ordinaryWeights, [20, 40])

    let centered = NativeLinearLayout.positions(
      total: 100, sizes: [10, 20], positioning: 2, spacing: 10)
    assertEqual(centered, [30, 50])
    let spaceBetween = NativeLinearLayout.positions(
      total: 100, sizes: [10, 20], positioning: 6, spacing: 7)
    assertEqual(spaceBetween, [0, 87])
    let rtl = NativeLinearLayout.positions(
      total: 100,
      sizes: [10, 20],
      positioning: 1,
      spacing: 5,
      direction: .rightToLeft)
    assertEqual(rtl, [90, 65])

    let fit = NativeRootTransform.resolve(
      document: CGSize(width: 100, height: 50),
      viewport: CGSize(width: 300, height: 300),
      sizing: 2,
      mode: 4,
      alignment: 34)
    assertClose(fit.scaleX, 3)
    assertClose(fit.scaleY, 3)
    assertClose(fit.translateX, 0)
    assertClose(fit.translateY, 75)

    let resized = NativeRootTransform.resolve(
      document: CGSize(width: 100, height: 50),
      viewport: CGSize(width: 200, height: 100),
      sizing: 2,
      mode: 6,
      alignment: 68)
    assertClose(resized.scaleX, 2)
    assertClose(resized.scaleY, 2)
    assertClose(resized.translateX, 0)
    assertClose(resized.translateY, 0)
  }

  private func assertClose(
    _ actual: CGFloat,
    _ expected: CGFloat,
    sourceLocation: SourceLocation = #_sourceLocation
  ) {
    #expect(
      abs(actual - expected) < 0.001,
      "expected \(expected), got \(actual)",
      sourceLocation: sourceLocation)
  }

  private func assertEqual(
    _ actual: [CGFloat],
    _ expected: [CGFloat],
    sourceLocation: SourceLocation = #_sourceLocation
  ) {
    #expect(actual.count == expected.count, sourceLocation: sourceLocation)
    zip(actual, expected).forEach { assertClose($0, $1, sourceLocation: sourceLocation) }
  }
}

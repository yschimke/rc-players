import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerCore
@testable import RcNativePlayerUIKit

@Suite struct NativeGraphicsStateTests {
  @Test func graphicsState() {
    #expect(NativeGraphicsState.lineCap(NativeSwiftStrokeCap(wireValue: 0)) == .butt)
    #expect(NativeGraphicsState.lineCap(NativeSwiftStrokeCap(wireValue: 1)) == .round)
    #expect(NativeGraphicsState.lineCap(NativeSwiftStrokeCap(wireValue: 2)) == .square)
    #expect(NativeGraphicsState.lineCap(NativeSwiftStrokeCap(wireValue: 3)) == .butt)
    #expect(NativeGraphicsState.lineJoin(NativeSwiftStrokeJoin(wireValue: 0)) == .miter)
    #expect(NativeGraphicsState.lineJoin(NativeSwiftStrokeJoin(wireValue: 1)) == .round)
    #expect(NativeGraphicsState.lineJoin(NativeSwiftStrokeJoin(wireValue: 2)) == .bevel)
    #expect(NativeGraphicsState.lineJoin(NativeSwiftStrokeJoin(wireValue: -1)) == .miter)
    #expect(NativeGraphicsState.fillRule(NativeSwiftPathWinding(wireValue: 0)) == .winding)
    #expect(NativeGraphicsState.fillRule(NativeSwiftPathWinding(wireValue: 1)) == .evenOdd)
    #expect(NativeGraphicsState.fillRule(NativeSwiftPathWinding(wireValue: 2)) == .winding)

    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.clear) == .clear)
    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.source) == .copy)
    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.sourceOver) == .normal)
    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.plus) == .plusLighter)
    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.screen) == .screen)
    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.multiply) == .multiply)
    #expect(NativeGraphicsState.blendMode(NativeSwiftPaintBlendMode.luminosity) == .luminosity)

    let path = NativePathBuilder.make([
      NativePathElement(kind: 10, values: [2, 3]),
      NativePathElement(kind: 11, values: [12, 3]),
      NativePathElement(kind: 12, values: [14, 8, 12, 13]),
      NativePathElement(kind: 14, values: [8, 16, 4, 16, 2, 13]),
      NativePathElement(kind: 15, values: []),
    ])
    #expect(path.currentPoint == CGPoint(x: 2, y: 3))
    #expect(path.boundingBoxOfPath.width >= 10)
    #expect(path.boundingBoxOfPath.height >= 10)

    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
      data: nil,
      width: 20,
      height: 4,
      bitsPerComponent: 8,
      bytesPerRow: 80,
      space: colorSpace,
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let red = CGColor(colorSpace: colorSpace, components: [1, 0, 0, 1])!
    let blue = CGColor(colorSpace: colorSpace, components: [0, 0, 1, 1])!
    context.clip(to: CGRect(x: 0, y: 0, width: 20, height: 4))
    NativeGradientRenderer.draw(
      NativeGradient(
        kind: 0,
        colors: [red, blue],
        stops: [0, 1],
        values: [0, 0, 20, 0],
        tileMode: 0),
      in: context)
    let pixels = context.data!.assumingMemoryBound(to: UInt8.self)
    #expect(pixels[0] > pixels[2], "linear gradient should begin red")
    #expect(pixels[19 * 4 + 2] > pixels[19 * 4], "linear gradient should end blue")

    let sweepContext = CGContext(
      data: nil,
      width: 8,
      height: 8,
      bitsPerComponent: 8,
      bytesPerRow: 32,
      space: colorSpace,
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    sweepContext.clip(to: CGRect(x: 0, y: 0, width: 8, height: 8))
    NativeGradientRenderer.draw(
      NativeGradient(
        kind: 2,
        colors: [red, blue],
        stops: [],
        values: [4, 4, 0, 0],
        tileMode: 0),
      in: sweepContext)
    #expect(sweepContext.makeImage() != nil)
  }

  /// Repeat and mirror are drawn one period at a time over the periods a shape reaches, and a
  /// period too small to see fills with the colour the gradient averages to.
  @Test func gradientTilingPeriodsAndAverage() {
    #expect(NativeGradientTiling.periods(lower: -0.5, upper: 1.2)! == (first: -1, last: 2))
    #expect(NativeGradientTiling.periods(lower: 0, upper: 300)! == (first: 0, last: 300))
    #expect(NativeGradientTiling.periods(lower: 0, upper: .infinity) == nil)

    let blackToWhite: [[Double]] = [[0, 0, 0, 1], [1, 1, 1, 1]]
    #expect(
      NativeGradientTiling.averageColor(colors: blackToWhite, stops: [0, 1]) == [0.5, 0.5, 0.5, 1])
    #expect(
      NativeGradientTiling.averageColor(colors: blackToWhite, stops: [0.25, 0.75])
        == [0.5, 0.5, 0.5, 1])
    #expect(
      NativeGradientTiling.averageColor(
        colors: [[1, 0, 0, 1], [0, 0, 1, 1], [0, 1, 0, 1]], stops: [0, 0.5, 1])
        == [0.25, 0.25, 0.5, 1])

    // The largest singular value: a rotation keeps 1, a shear can exceed both columns.
    #expect(abs(NativeGradientTiling.largestStretch(a: 0, b: 1, c: -1, d: 0) - 1) < 1e-9)
    #expect(abs(NativeGradientTiling.largestStretch(a: 0.001, b: 0, c: 0, d: 1) - 1) < 1e-9)
    let sheared = NativeGradientTiling.largestStretch(a: 0.6, b: 0.54, c: 0.54, d: 0.6)
    #expect(abs(sheared - 1.14) < 1e-9)
    // A 300 × 400 device clip resolves at most its 500-pixel diagonal of rings, plus the two ends.
    #expect(NativeGradientTiling.ringBudget(deviceWidth: 300, deviceHeight: 400) == 502)
    #expect(NativeGradientTiling.ringBudget(deviceWidth: .infinity, deviceHeight: 1) == 0)
  }
}

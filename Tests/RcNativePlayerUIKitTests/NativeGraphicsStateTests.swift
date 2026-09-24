import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerCore
@testable import RcNativePlayerUIKit

@Suite struct NativeGraphicsStateTests {
  @Test func graphicsState() {
    #expect(NativeGraphicsState.lineCap(0) == .butt)
    #expect(NativeGraphicsState.lineCap(1) == .round)
    #expect(NativeGraphicsState.lineCap(2) == .square)
    #expect(NativeGraphicsState.lineJoin(0) == .miter)
    #expect(NativeGraphicsState.lineJoin(1) == .round)
    #expect(NativeGraphicsState.lineJoin(2) == .bevel)

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
  }
}

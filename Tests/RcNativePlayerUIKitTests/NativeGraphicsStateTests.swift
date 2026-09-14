import CoreGraphics
import Foundation

@main
enum NativeGraphicsStateTests {
  static func main() {
    precondition(NativeGraphicsState.lineCap(0) == .butt)
    precondition(NativeGraphicsState.lineCap(1) == .round)
    precondition(NativeGraphicsState.lineCap(2) == .square)
    precondition(NativeGraphicsState.lineJoin(0) == .miter)
    precondition(NativeGraphicsState.lineJoin(1) == .round)
    precondition(NativeGraphicsState.lineJoin(2) == .bevel)

    precondition(NativeGraphicsState.blendMode(0) == .clear)
    precondition(NativeGraphicsState.blendMode(1) == .copy)
    precondition(NativeGraphicsState.blendMode(3) == .normal)
    precondition(NativeGraphicsState.blendMode(12) == .plusLighter)
    precondition(NativeGraphicsState.blendMode(14) == .screen)
    precondition(NativeGraphicsState.blendMode(24) == .multiply)
    precondition(NativeGraphicsState.blendMode(28) == .luminosity)

    let path = NativePathBuilder.make([
      NativePathElement(kind: 10, values: [2, 3]),
      NativePathElement(kind: 11, values: [12, 3]),
      NativePathElement(kind: 12, values: [14, 8, 12, 13]),
      NativePathElement(kind: 14, values: [8, 16, 4, 16, 2, 13]),
      NativePathElement(kind: 15, values: []),
    ])
    precondition(path.currentPoint == CGPoint(x: 2, y: 3))
    precondition(path.boundingBoxOfPath.width >= 10)
    precondition(path.boundingBoxOfPath.height >= 10)

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
    precondition(pixels[0] > pixels[2], "linear gradient should begin red")
    precondition(pixels[19 * 4 + 2] > pixels[19 * 4], "linear gradient should end blue")

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
    precondition(sweepContext.makeImage() != nil)

    print("native UIKit graphics-state tests: ok")
  }
}

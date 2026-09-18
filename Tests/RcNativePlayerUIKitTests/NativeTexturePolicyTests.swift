import CoreGraphics
import Foundation

@main
enum NativeTexturePolicyTests {
  static func main() {
    // The shader matrix a title card carries: an 8x8 bitmap scaled 21.5x and offset -10.6.
    let titleCard = NativeTexturePolicy.transform(
      [21.5, 0, 0, 0, 21.5, -10.6, 0, 0, 1])
    precondition(titleCard.a == 21.5 && titleCard.d == 21.5)
    precondition(titleCard.tx == 0 && abs(titleCard.ty + 10.6) < 0.001)
    precondition(titleCard.b == 0 && titleCard.c == 0)

    // The 3x3 layout is [scaleX, skewX, translateX, skewY, scaleY, translateY, …], which is not
    // CGAffineTransform's argument order — a skew/translate pair catches a transposed mapping.
    let skewed = NativeTexturePolicy.transform([2, 1, 3, 4, 5, 6, 0, 0, 1])
    precondition(skewed.a == 2 && skewed.b == 4)
    precondition(skewed.c == 1 && skewed.d == 5)
    precondition(skewed.tx == 3 && skewed.ty == 6)

    precondition(NativeTexturePolicy.transform(nil) == .identity)
    precondition(NativeTexturePolicy.transform([]) == .identity)
    precondition(NativeTexturePolicy.transform([1, 2, 3]) == .identity)

    precondition(
      NativeTexturePolicy.mirrors(tileModeX: 2, tileModeY: 0) == (x: true, y: false))
    precondition(
      NativeTexturePolicy.mirrors(tileModeX: 1, tileModeY: 2) == (x: false, y: true))
    precondition(
      NativeTexturePolicy.mirrors(tileModeX: 0, tileModeY: 0) == (x: false, y: false))
    precondition(NativeTexturePolicy.isDecal(3))
    precondition(!NativeTexturePolicy.isDecal(0))
    precondition(!NativeTexturePolicy.isDecal(1))
    precondition(!NativeTexturePolicy.isDecal(2))

    testPatternLandsInUserSpace()

    print("native UIKit texture policy tests: ok")
  }

  /// The pattern must land where the clipped path is, under a scaled CTM.
  ///
  /// A `CGPattern` matrix is device-space and ignores the CTM, so the policy premultiplies the
  /// shader's user-space matrix by the CTM. Without that the tile would be off by the document's
  /// density, which is exactly the class of bug this file exists to catch.
  private static func testPatternLandsInUserSpace() {
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
      data: nil, width: 80, height: 80, bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace,
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let image = makeImage()
    context.scaleBy(x: 2, y: 2)
    context.clip(to: CGRect(x: 0, y: 0, width: 40, height: 40))
    NativeTexturePolicy.paint(
      image: image, transform: CGAffineTransform(scaleX: 10, y: 10),
      tileModeX: 0, tileModeY: 0, in: context)

    let data = context.data!.assumingMemoryBound(to: UInt8.self)
    func pixel(_ x: Int, _ y: Int) -> (UInt8, UInt8, UInt8) {
      let index = (y * 80 + x) * 4
      return (data[index], data[index + 1], data[index + 2])
    }
    // The CTM is 2 and the shader scale 10, so one bitmap pixel covers 20 device pixels. This
    // context is y-up (the UIKit context is flipped), so the bitmap's first row lands at the
    // bottom: red/green at the bottom, blue/white at the top.
    precondition(pixel(10, 10) == (255, 0, 0), "bottom-left of the tile was \(pixel(10, 10))")
    precondition(pixel(30, 10) == (0, 255, 0), "bottom-right of the tile was \(pixel(30, 10))")
    precondition(pixel(10, 70) == (0, 0, 255), "top-left of the tile was \(pixel(10, 70))")
    precondition(pixel(30, 70) == (255, 255, 255), "top-right of the tile was \(pixel(30, 70))")

    // A translated shader moves the tile in user space, not device space. Decal keeps the area
    // outside the bitmap transparent, so the tile's new origin is unambiguous.
    let shifted = CGContext(
      data: nil, width: 80, height: 20, bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace,
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    shifted.scaleBy(x: 2, y: 2)
    shifted.clip(to: CGRect(x: 0, y: 0, width: 40, height: 10))
    var transform = CGAffineTransform(scaleX: 10, y: 10)
    transform = transform.translatedBy(x: 2, y: 0)
    NativeTexturePolicy.paint(
      image: image, transform: transform, tileModeX: 3, tileModeY: 3, in: shifted)
    let shiftedData = shifted.data!.assumingMemoryBound(to: UInt8.self)
    func shiftedPixel(_ x: Int, _ y: Int) -> (UInt8, UInt8, UInt8) {
      let index = (y * 80 + x) * 4
      return (shiftedData[index], shiftedData[index + 1], shiftedData[index + 2])
    }
    // Two bitmap pixels in is 20 user points in, which is 40 device pixels: the first bitmap pixel
    // starts there, so 30 device is still transparent and 50 device is the bitmap's first column.
    precondition(shiftedPixel(30, 10) == (0, 0, 0), "unexpected paint at \(shiftedPixel(30, 10))")
    precondition(shiftedPixel(50, 10) == (0, 0, 255), "shifted tile was \(shiftedPixel(50, 10))")
  }

  private static func makeImage() -> CGImage {
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    // Row 0 is the top row: red, green. Row 1 is the bottom row: blue, white.
    let pixels: [UInt8] = [
      255, 0, 0, 255, 0, 255, 0, 255,
      0, 0, 255, 255, 255, 255, 255, 255,
    ]
    let provider = CGDataProvider(data: Data(pixels) as CFData)!
    return CGImage(
      width: 2, height: 2, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: 8,
      space: colorSpace,
      bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
      provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
  }
}

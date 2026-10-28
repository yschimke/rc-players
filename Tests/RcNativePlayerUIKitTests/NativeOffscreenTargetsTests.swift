import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerCore
@testable import RcNativePlayerUIKit

/// The offscreen-target pool's component buffers (remote-core alpha22's
/// `ENCODING_COMPONENT_OFFSCREEN_BUFFER`): sized by the host, scoped per custom visibility
/// animation call, and bounded by the same pixel ceiling as every other target.
@Suite struct NativeOffscreenTargetsTests {
  private func buffer(
    id: Int = 20, mode: Int = 0, color: UInt32 = 0
  ) -> NativeSwiftOffscreenTargetSnapshot {
    NativeSwiftOffscreenTargetSnapshot(
      bitmapID: id, mode: mode, colorARGB: color, width: 1, height: 1, isComponentBuffer: true)
  }

  private func context(_ redirect: NativeOffscreenRedirect) -> CGContext? {
    if case .offscreen(let context) = redirect { return context }
    return nil
  }

  private func isDropped(_ redirect: NativeOffscreenRedirect) -> Bool {
    if case .dropped = redirect { return true }
    return false
  }

  /// The first pixel's red and alpha bytes.
  private func topLeft(_ image: CGImage?) -> (red: UInt8, alpha: UInt8)? {
    guard let image else { return nil }
    var pixel = [UInt8](repeating: 0, count: 4)
    let drawn = pixel.withUnsafeMutableBytes { bytes -> Bool in
      guard
        let context = CGContext(
          data: bytes.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
          space: CGColorSpaceCreateDeviceRGB(),
          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
      else { return false }
      // Only the image's top-left pixel lands on the one-pixel context.
      context.draw(
        image,
        in: CGRect(
          x: 0, y: 1 - CGFloat(image.height), width: CGFloat(image.width),
          height: CGFloat(image.height)))
      return true
    }
    return drawn ? (pixel[0], pixel[3]) : nil
  }

  @Test func aBufferTakesItsComponentsSizeOrIsDropped() {
    let targets = NativeOffscreenTargets()
    // No component to size it to: dropped, not drawn at the 1x1 placeholder.
    #expect(isDropped(targets.begin(buffer(), seed: nil)))
    let sized = context(targets.begin(buffer(), seed: nil, componentSize: (width: 30, height: 12)))
    #expect(sized?.width == 30 && sized?.height == 12)
    #expect(targets.image(20)?.width == 30)
    // Resized to another component: a new buffer of the new size.
    let resized = context(targets.begin(buffer(), seed: nil, componentSize: (width: 8, height: 8)))
    #expect(resized?.width == 8 && targets.image(20)?.height == 8)
    // A plain bitmap target is unchanged by a component size.
    let plain = NativeSwiftOffscreenTargetSnapshot(
      bitmapID: 7, mode: 0, colorARGB: 0, width: 4, height: 2)
    #expect(
      context(targets.begin(plain, seed: nil, componentSize: (width: 99, height: 99)))?.width == 4)
  }

  @Test func aCallsBuffersAreScopedAndTheOuterBindingComesBack() throws {
    let targets = NativeOffscreenTargets()
    let outer = try #require(
      context(targets.begin(buffer(), seed: nil, componentSize: (width: 300, height: 160))))
    outer.setFillColor(CGColor(srgbRed: 1, green: 0, blue: 0, alpha: 1))
    outer.fill(CGRect(x: 0, y: 0, width: 300, height: 160))

    // A nested component animating with the same function binds the same id to its own buffer.
    targets.pushScope()
    let inner = try #require(
      context(targets.begin(buffer(), seed: nil, componentSize: (width: 120, height: 50))))
    #expect(inner !== outer)
    #expect(targets.image(20)?.width == 120)
    #expect(topLeft(targets.image(20))?.alpha == 0, "the inner buffer starts erased")
    // Resuming the enclosing target finds the innermost binding, unerased.
    #expect(context(targets.resume(20)) === inner)
    targets.popScope()

    // The call ended: the outer binding is back, with what was drawn into it.
    #expect(targets.image(20)?.width == 300)
    #expect(topLeft(targets.image(20))?.red == 255)
    #expect(context(targets.resume(20)) === outer)
    // The base scope is never closed.
    targets.popScope()
    #expect(targets.image(20)?.width == 300)
    #expect(isDropped(targets.resume(404)))
  }

  @Test func buffersShareThePixelCeiling() {
    let targets = NativeOffscreenTargets()
    let side = 4_096
    #expect(
      context(targets.begin(buffer(id: 1), seed: nil, componentSize: (width: side, height: side)))
        != nil)
    // The pool is full: a second buffer is dropped rather than allocated.
    #expect(
      isDropped(targets.begin(buffer(id: 2), seed: nil, componentSize: (width: 1, height: 1))))
    // A scope's buffers are released when it closes, freeing their pixels.
    let scoped = NativeOffscreenTargets()
    scoped.pushScope()
    _ = scoped.begin(buffer(id: 1), seed: nil, componentSize: (width: side, height: side))
    scoped.popScope()
    #expect(
      context(scoped.begin(buffer(id: 2), seed: nil, componentSize: (width: side, height: side)))
        != nil)
  }

  @Test func aCapturingRedirectErasesToTransparentUnlessToldNotTo() throws {
    let targets = NativeOffscreenTargets()
    let first = try #require(
      context(targets.begin(buffer(), seed: nil, componentSize: (width: 2, height: 2))))
    first.setFillColor(CGColor(srgbRed: 1, green: 0, blue: 0, alpha: 1))
    first.fill(CGRect(x: 0, y: 0, width: 2, height: 2))
    _ = targets.begin(
      buffer(mode: NativeSwiftDrawToBitmapMode.noInitialize), seed: nil,
      componentSize: (width: 2, height: 2))
    #expect(topLeft(targets.image(20))?.red == 255, "MODE_NO_INITIALIZE kept the pixels")
    _ = targets.begin(
      buffer(mode: NativeSwiftDrawToBitmapMode.componentID), seed: nil,
      componentSize: (width: 2, height: 2))
    #expect(topLeft(targets.image(20))?.alpha == 0, "the capture did not erase")
  }
}

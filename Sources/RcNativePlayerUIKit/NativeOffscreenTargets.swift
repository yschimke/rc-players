import CoreGraphics
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

/// The offscreen bitmaps a canvas's `DrawToBitmap` commands draw into, by bitmap id.
///
/// AndroidX's `AndroidPaintContext.drawToBitmap` wraps the declared bitmap in a `Canvas` once and
/// caches it, so a target keeps what was drawn into it — across redirects and across frames — and
/// starts from the declared bitmap's own pixels. Each target here is a Core Graphics bitmap context
/// of the declared size, seeded with the decoded image when there is one, and kept for the life of
/// the canvas view that owns it. A `DrawBitmap` of that id then draws the target's current pixels.
///
/// The core bounds what a document can ask for (64 targets, 16777216 pixels in all), so this
/// allocates at most that.
final class NativeOffscreenTargets {
  private var contexts: [Int: CGContext] = [:]

  /// The context later commands draw into for `target`, erased first unless its mode says not to.
  /// Nil when `target` returns drawing to the canvas, or when Core Graphics cannot allocate it, in
  /// which case the redirected commands are dropped rather than drawn onto the canvas.
  func begin(_ target: NativeSwiftOffscreenTargetSnapshot, seed: CGImage?) -> CGContext? {
    guard !target.returnsToCanvas, target.width > 0, target.height > 0 else { return nil }
    let context: CGContext
    if let existing = contexts[target.bitmapID] {
      context = existing
    } else {
      guard let created = Self.make(width: target.width, height: target.height, seed: seed) else {
        return nil
      }
      contexts[target.bitmapID] = created
      context = created
    }
    if target.erasesTarget { Self.erase(context, to: target.colorARGB) }
    return context
  }

  /// What target `id` holds now, or nil when nothing has drawn into it.
  func image(_ id: Int) -> CGImage? {
    contexts[id]?.makeImage()
  }

  private static func make(width: Int, height: Int, seed: CGImage?) -> CGContext? {
    guard
      let context = CGContext(
        data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
    else { return nil }
    // Drawn before the flip below, so the seed lands upright.
    if let seed { context.draw(seed, in: CGRect(x: 0, y: 0, width: width, height: height)) }
    // Top-left origin with y down, as the canvas views and the reference's bitmap canvas draw.
    context.translateBy(x: 0, y: CGFloat(height))
    context.scaleBy(x: 1, y: -1)
    return context
  }

  /// `Bitmap.eraseColor`: every pixel becomes `argb`, whatever the target's matrix and clip.
  private static func erase(_ context: CGContext, to argb: UInt32) {
    context.saveGState()
    context.resetClip()
    context.concatenate(context.ctm.inverted())
    context.setBlendMode(.copy)
    context.setAlpha(1)
    context.setFillColor(
      CGColor(
        srgbRed: CGFloat((argb >> 16) & 0xff) / 255, green: CGFloat((argb >> 8) & 0xff) / 255,
        blue: CGFloat(argb & 0xff) / 255, alpha: CGFloat((argb >> 24) & 0xff) / 255))
    context.fill(CGRect(x: 0, y: 0, width: context.width, height: context.height))
    context.restoreGState()
  }
}

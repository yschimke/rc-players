import CoreGraphics
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

/// Where a node's later commands go after a `DrawToBitmap`.
enum NativeOffscreenRedirect {
  /// Back to the node's own canvas: the redirect named bitmap id 0.
  case canvas
  /// Into this offscreen target.
  case offscreen(CGContext)
  /// Nowhere: the target could not be allocated, so the commands up to the next redirect are
  /// dropped rather than drawn onto the canvas.
  case dropped
}

/// The offscreen bitmaps a document's `DrawToBitmap` commands draw into, by bitmap id.
///
/// AndroidX's `AndroidPaintContext.drawToBitmap` wraps the declared bitmap in a `Canvas` once and
/// caches it, so a target keeps what was drawn into it — across redirects, components and frames —
/// and starts from the declared bitmap's own pixels. Each target here is a Core Graphics bitmap
/// context of the declared size, seeded with the decoded image when there is one. One pool belongs
/// to the document view and is shared by every canvas in its tree, so a bitmap id names the same
/// pixels wherever it is drawn into or read; a `DrawBitmap` of that id draws its current pixels.
///
/// The core bounds what a document can ask for (64 targets, 16777216 pixels in all), charging each
/// bitmap id once. The pool holds one target per id and refuses to grow past that same ceiling, so
/// it allocates at most that however many components draw into the targets.
final class NativeOffscreenTargets {
  static let maximumTargets = 64
  static let maximumPixels = 16_777_216

  private var contexts: [Int: CGContext] = [:]
  private var pixels = 0

  /// Where later commands draw for `target`, erased first unless its mode says not to: the canvas
  /// when `target` returns drawing to it, and `.dropped` when Core Graphics cannot allocate the
  /// target or the pool's ceiling refuses it.
  func begin(
    _ target: NativeSwiftOffscreenTargetSnapshot, seed: CGImage?
  ) -> NativeOffscreenRedirect {
    if target.returnsToCanvas { return .canvas }
    guard target.width > 0, target.height > 0 else { return .dropped }
    let context: CGContext
    if let existing = contexts[target.bitmapID], existing.width == target.width,
      existing.height == target.height
    {
      context = existing
    } else {
      // An id redeclared at another size (a newer document reusing it) replaces its old target.
      if let stale = contexts.removeValue(forKey: target.bitmapID) {
        pixels -= stale.width * stale.height
      }
      let size = target.width * target.height
      guard contexts.count < Self.maximumTargets, size <= Self.maximumPixels - pixels,
        let created = Self.make(width: target.width, height: target.height, seed: seed)
      else { return .dropped }
      contexts[target.bitmapID] = created
      pixels += size
      context = created
    }
    if target.erasesTarget { Self.erase(context, to: target.colorARGB) }
    return .offscreen(context)
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

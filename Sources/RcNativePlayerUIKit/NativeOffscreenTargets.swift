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
///
/// A component offscreen buffer (remote-core alpha22, `BitmapData.ensureOffscreenBitmap`) is sized
/// by its host to a component each time drawing is redirected into it, within the core's
/// `NativeSwiftComponentOffscreenBuffer` bounds. Buffers are bound in scopes: a custom visibility
/// animation's call opens one, as the reference's `pushOffscreenScope` does, so a nested component
/// animating with the same function and buffer gets a buffer of its own, and the call's buffers go
/// when it ends. Outside any call they live in the base scope and keep their pixels between frames.
/// They share the pool's pixel ceiling, and a buffer the ceiling refuses is dropped.
final class NativeOffscreenTargets {
  static let maximumTargets = 64
  static let maximumPixels = 16_777_216

  private var contexts: [Int: CGContext] = [:]
  /// Component buffers by id, innermost scope last. The first scope is the base one.
  private var bufferScopes: [[Int: CGContext]] = [[:]]
  private var pixels = 0

  /// Where later commands draw for `target`, erased first unless its mode says not to: the canvas
  /// when `target` returns drawing to it, and `.dropped` when Core Graphics cannot allocate the
  /// target or the pool's ceiling refuses it.
  ///
  /// A component offscreen buffer takes `componentSize` instead of its declared size; without one
  /// — no component to size it to, or one the reference would refuse — it is dropped.
  func begin(
    _ target: NativeSwiftOffscreenTargetSnapshot, seed: CGImage?,
    componentSize: (width: Int, height: Int)? = nil
  ) -> NativeOffscreenRedirect {
    if target.returnsToCanvas { return .canvas }
    if target.isComponentBuffer {
      guard let componentSize else { return .dropped }
      return beginBuffer(target, width: componentSize.width, height: componentSize.height)
    }
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

  /// Resumes drawing into a target without erasing it: the enclosing component target a redirect
  /// back to bitmap 0 returns to (`BitmapData.resumeOffscreenTargetCanvas`).
  func resume(_ bitmapID: Int) -> NativeOffscreenRedirect {
    guard let context = buffer(bitmapID) ?? contexts[bitmapID] else { return .dropped }
    return .offscreen(context)
  }

  /// Opens a scope for the component buffers a custom visibility animation's call binds.
  func pushScope() {
    bufferScopes.append([:])
  }

  /// Closes the innermost scope, releasing its buffers; the bindings outside it are the ones a
  /// `DrawBitmap` reads again. The base scope is never closed.
  func popScope() {
    guard bufferScopes.count > 1, let scope = bufferScopes.popLast() else { return }
    for context in scope.values { pixels -= context.width * context.height }
  }

  /// What target `id` holds now, or nil when nothing has drawn into it. A component buffer's
  /// innermost binding wins.
  func image(_ id: Int) -> CGImage? {
    (buffer(id) ?? contexts[id])?.makeImage()
  }

  private func buffer(_ id: Int) -> CGContext? {
    bufferScopes.reversed().lazy.compactMap { $0[id] }.first
  }

  private func beginBuffer(
    _ target: NativeSwiftOffscreenTargetSnapshot, width: Int, height: Int
  ) -> NativeOffscreenRedirect {
    let scope = bufferScopes.count - 1
    let context: CGContext
    if let existing = bufferScopes[scope][target.bitmapID], existing.width == width,
      existing.height == height
    {
      context = existing
    } else {
      // A buffer resized to a new component is replaced, in its own scope only: an enclosing
      // scope's binding comes back when this one closes.
      if let stale = bufferScopes[scope].removeValue(forKey: target.bitmapID) {
        pixels -= stale.width * stale.height
      }
      let size = width * height
      guard width > 0, height > 0, size <= Self.maximumPixels - pixels,
        let created = Self.make(width: width, height: height, seed: nil)
      else { return .dropped }
      bufferScopes[scope][target.bitmapID] = created
      pixels += size
      context = created
    }
    if target.erasesTarget { Self.erase(context, to: target.colorARGB) }
    return .offscreen(context)
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

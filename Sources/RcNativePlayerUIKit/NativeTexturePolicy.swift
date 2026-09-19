import CoreGraphics

/// The mapping from a paint's `SHADER_MATRIX` to the transform the texture renderer applies, and
/// the tiling draw itself.
///
/// AndroidX's `MatrixAccess` exposes a 3x3 in its own layout —
/// `[scaleX, skewX, translateX, skewY, scaleY, translateY, persp0, persp1, persp2]` — which is not
/// the argument order `CGAffineTransform` takes, so the reordering is worth stating once and
/// testing rather than repeating at the draw site.
public enum NativeTexturePolicy {
  /// A transform for the shader's 3x3 matrix, or the identity when no matrix was named.
  public static func transform(_ matrix: [Float]?) -> CGAffineTransform {
    guard let matrix, matrix.count == 9 else { return .identity }
    return CGAffineTransform(
      a: CGFloat(matrix[0]), b: CGFloat(matrix[3]), c: CGFloat(matrix[1]),
      d: CGFloat(matrix[4]), tx: CGFloat(matrix[2]), ty: CGFloat(matrix[5]))
  }

  /// Whether an axis tiles by mirroring. AndroidX: 0 clamp, 1 repeat, 2 mirror, 3 decal.
  public static func mirrors(tileModeX: Int, tileModeY: Int) -> (x: Bool, y: Bool) {
    (x: tileModeX == 2, y: tileModeY == 2)
  }

  /// Whether a tile mode paints only where the bitmap is, rather than tiling it.
  public static func isDecal(_ tileMode: Int) -> Bool {
    tileMode == 3
  }

  /// Fills the context's current clip with the bitmap texture shader.
  ///
  /// The shader's matrix is in the same user space as the clipped path, while a `CGPattern` matrix
  /// is expressed in the context's device space and ignores the CTM — so it is premultiplied by the
  /// CTM to land where the path is. Clamp (0) and repeat (1) share the pattern; mirror (2) uses a
  /// 2x2 super-tile of flipped copies, and decal (3) clips the fill to the bitmap's own area.
  /// Clamp only clamps where the reference does when the shape lies inside the mapped bitmap;
  /// outside it, this player repeats rather than extending the edge pixels.
  public static func paint(
    image: CGImage,
    transform shaderTransform: CGAffineTransform,
    tileModeX: Int,
    tileModeY: Int,
    in context: CGContext
  ) {
    let width = CGFloat(image.width)
    let height = CGFloat(image.height)
    guard width > 0, height > 0 else { return }
    let mirror = mirrors(tileModeX: tileModeX, tileModeY: tileModeY)
    if isDecal(tileModeX) || isDecal(tileModeY) {
      // Decal paints only where the bitmap is; the pattern would otherwise repeat forever.
      context.clip(to: CGRect(x: 0, y: 0, width: width, height: height).applying(shaderTransform))
    }
    let info = NativeTexturePatternInfo(
      image: image, mirrorX: mirror.x, mirrorY: mirror.y)
    var callbacks = CGPatternCallbacks(
      version: 0,
      drawPattern: { info, context in
        guard let info else { return }
        Unmanaged<NativeTexturePatternInfo>.fromOpaque(info).takeUnretainedValue().draw(
          in: context)
      },
      releaseInfo: nil)
    let stepX = mirror.x ? width * 2 : width
    let stepY = mirror.y ? height * 2 : height
    guard
      let pattern = CGPattern(
        info: Unmanaged.passUnretained(info).toOpaque(),
        bounds: CGRect(x: 0, y: 0, width: stepX, height: stepY),
        matrix: shaderTransform.concatenating(context.ctm),
        xStep: stepX, yStep: stepY,
        tiling: .constantSpacing, isColored: true, callbacks: &callbacks),
      let patternSpace = CGColorSpace(patternBaseSpace: nil)
    else { return }
    context.setFillColorSpace(patternSpace)
    var alpha: CGFloat = 1
    context.setFillPattern(pattern, colorComponents: &alpha)
    context.fill(context.boundingBoxOfClipPath)
  }
}

/// The payload a texture pattern's draw callback carries: the bitmap and which axes mirror.
///
/// A `CGPattern` has no mirror mode, so a mirrored axis is expressed as a 2x2 super-tile whose odd
/// row and column are flipped copies.
private final class NativeTexturePatternInfo {
  private let image: CGImage
  private let mirrorX: Bool
  private let mirrorY: Bool

  init(image: CGImage, mirrorX: Bool, mirrorY: Bool) {
    self.image = image
    self.mirrorX = mirrorX
    self.mirrorY = mirrorY
  }

  func draw(in context: CGContext) {
    let width = CGFloat(image.width)
    let height = CGFloat(image.height)
    drawTile(in: context, x: 0, y: 0, flipX: false, flipY: false)
    if mirrorX { drawTile(in: context, x: width, y: 0, flipX: true, flipY: false) }
    if mirrorY { drawTile(in: context, x: 0, y: height, flipX: false, flipY: true) }
    if mirrorX && mirrorY {
      drawTile(in: context, x: width, y: height, flipX: true, flipY: true)
    }
  }

  private func drawTile(in context: CGContext, x: CGFloat, y: CGFloat, flipX: Bool, flipY: Bool) {
    let width = CGFloat(image.width)
    let height = CGFloat(image.height)
    context.saveGState()
    context.translateBy(x: x + (flipX ? width : 0), y: y + (flipY ? height : 0))
    context.scaleBy(x: flipX ? -1 : 1, y: flipY ? -1 : 1)
    context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
    context.restoreGState()
  }
}

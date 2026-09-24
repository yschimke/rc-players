package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// The graphics primitives the player needs that common Compose does not expose. Skiko-backed
// targets
// (JVM, wasmJs, Apple) implement them once in `skikoMain` with Skia; Android implements them with
// `android.graphics` in `androidMain`.

/**
 * A font built from a font file's bytes. [identity] keys Compose's typeface cache, so two different
 * instances of one file must not share it.
 */
internal expect fun rcFontFromBytes(
  identity: String,
  data: ByteArray,
  weight: FontWeight = FontWeight.Normal,
  style: FontStyle = FontStyle.Normal,
  variationSettings: FontVariation.Settings? = null,
): Font

/**
 * The canvas's current local-to-device transform as a row-major 3×3 (`[scaleX, skewX, transX,
 * skewY, scaleY, transY, persp0, persp1, persp2]`).
 */
internal expect fun DrawScope.rcLocalToDevice(): FloatArray

/** Decodes an encoded image (PNG and whatever else the platform codec reads). */
internal expect fun decodeRcEncodedImage(data: ByteArray): ImageBitmap

/**
 * Wraps tightly packed raw pixels: unpremultiplied RGBA_8888 rows when [alphaOnly] is false, one
 * alpha byte per pixel (ALPHA_8) when it is true.
 */
internal expect fun rcRasterImage(
  width: Int,
  height: Int,
  pixels: ByteArray,
  alphaOnly: Boolean,
): ImageBitmap

/** Contour walker for text on a path — Skia's and Android's `PathMeasure`, whichever is native. */
internal expect class RcPathMeasure(path: Path) {
  /** Length of the current contour. */
  val length: Float

  /** Moves to the next contour; false when there is none. */
  fun nextContour(): Boolean

  /** Position at [distance] along the current contour, or null when it cannot be computed. */
  fun position(distance: Float): Offset?

  /** Unit tangent at [distance] along the current contour, or null when it cannot be computed. */
  fun tangent(distance: Float): Offset?
}

/**
 * A compiled `ShaderData` program. [owner] is whatever native object must outlive [shader] — the
 * paint keeps it reachable for as long as it draws with the shader.
 */
internal class RcRuntimeShader(val shader: Shader, val owner: Any)

/** Uniform setter for one runtime shader, built by [rcRuntimeShaderBuilder]. */
internal interface RcRuntimeShaderBuilder {
  fun floatUniform(name: String, values: FloatArray)

  /** One to four values: `int` .. `int4`. */
  fun intUniform(name: String, values: IntArray)

  fun bitmapUniform(name: String, image: ImageBitmap)

  fun build(): RcRuntimeShader
}

/**
 * Compiles [source] (SkSL; AGSL on Android, the same language for what documents carry). Throws
 * when the platform cannot compile it or has no runtime shaders at all.
 */
internal expect fun rcRuntimeShaderBuilder(source: String): RcRuntimeShaderBuilder

/** Whether [source] compiles on this platform — the capability check behind document validation. */
internal expect fun rcRuntimeShaderSupported(source: String): Boolean

/**
 * Appends a conic from the current point ([x0], [y0]) through control ([x1], [y1]) to ([x2], [y2]).
 * Skia draws it exactly; platforms without a conic primitive use [rcConicAsQuads]. The start point
 * is passed because common `Path` cannot report its own current point.
 */
internal expect fun Path.rcConicTo(
  x0: Float,
  y0: Float,
  x1: Float,
  y1: Float,
  x2: Float,
  y2: Float,
  weight: Float,
)

/**
 * How many times [rcConicAsQuads] halves a conic so each quad stays within [tolerance] px of it —
 * Skia's `SkConic::computeQuadPOW2`: the distance between a conic and the quad sharing its points
 * is bounded by `|k·(p0 − 2p1 + p2)|` with `k = (w − 1) / (4·(2 + (w − 1)))`, and each halving cuts
 * that bound by four. Capped at [RC_CONIC_MAX_LEVELS] (2¹⁰ quads), well past Skia's own 2⁵.
 */
internal fun rcConicQuadLevels(
  x0: Float,
  y0: Float,
  x1: Float,
  y1: Float,
  x2: Float,
  y2: Float,
  weight: Float,
  tolerance: Float = 0.25f,
): Int {
  if (!weight.isFinite() || weight <= 0f) return 0
  val a = weight - 1f
  val k = a / (4f * (2f + a))
  val dx = k * (x0 - 2f * x1 + x2)
  val dy = k * (y0 - 2f * y1 + y2)
  var error = kotlin.math.sqrt(dx * dx + dy * dy)
  var levels = 0
  while (error > tolerance && levels < RC_CONIC_MAX_LEVELS) {
    error *= 0.25f
    levels++
  }
  return levels
}

internal const val RC_CONIC_MAX_LEVELS = 10

/**
 * A conic as `2^levels` quadratics, by Skia's `SkConic::chop` — halve the rational curve at t = ½
 * until each piece's weight is close enough to 1 that its control point serves as a quad's. Each
 * quad is emitted as (control x, control y, end x, end y). [levels] defaults to the depth
 * [rcConicQuadLevels] computes for a quarter-pixel tolerance.
 */
internal fun rcConicAsQuads(
  x0: Float,
  y0: Float,
  x1: Float,
  y1: Float,
  x2: Float,
  y2: Float,
  weight: Float,
  levels: Int = rcConicQuadLevels(x0, y0, x1, y1, x2, y2, weight),
  quad: (Float, Float, Float, Float) -> Unit,
) {
  if (levels == 0 || !weight.isFinite() || weight <= 0f) {
    quad(x1, y1, x2, y2)
    return
  }
  val scale = 1f / (1f + weight)
  val mx = (x0 + 2f * weight * x1 + x2) * 0.5f * scale
  val my = (y0 + 2f * weight * y1 + y2) * 0.5f * scale
  val halfWeight = kotlin.math.sqrt(0.5f + weight * 0.5f)
  rcConicAsQuads(
    x0,
    y0,
    (x0 + weight * x1) * scale,
    (y0 + weight * y1) * scale,
    mx,
    my,
    halfWeight,
    levels - 1,
    quad,
  )
  rcConicAsQuads(
    mx,
    my,
    (weight * x1 + x2) * scale,
    (weight * y1 + y2) * scale,
    x2,
    y2,
    halfWeight,
    levels - 1,
    quad,
  )
}

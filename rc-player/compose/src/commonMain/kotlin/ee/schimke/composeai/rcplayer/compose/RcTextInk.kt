package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.concurrent.Volatile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The ink of a measured text run, in whole pixels of [layout]'s own coordinates, as
 * `Paint.getTextBounds` reports it for AndroidX's `TextMeasure` / `TextAttribute`: the pixels the
 * glyphs cover, not the line box. [Rect.Zero] when nothing is drawn (empty or blank text).
 *
 * Compose has no portable glyph-bounds query (on Skiko `getPathForRange` is the selection box), so
 * the run is drawn once, opaque, into an offscreen bitmap padded for glyphs that overhang the
 * layout, and the covered pixels are scanned. Results are cached per layout input, so a measurement
 * in an animated draw stream rasterises once.
 */
internal fun rcTextInk(layout: TextLayoutResult): Rect {
  val key = layout.layoutInput
  val cached = inkCache
  cached[key]?.let {
    return it
  }
  val ink = scanInk(layout)
  // Copy-on-write, so players drawing on different threads never share a map being mutated; a
  // racing write at worst drops an entry, which is measured again.
  inkCache = (if (cached.size >= INK_CACHE_SIZE) emptyMap() else cached) + (key to ink)
  return ink
}

private const val INK_CACHE_SIZE = 64

@Volatile private var inkCache: Map<Any, Rect> = emptyMap()

private fun scanInk(layout: TextLayoutResult): Rect {
  if (layout.layoutInput.text.isBlank()) return Rect.Zero
  val pad = ceil(layout.size.height.toFloat()).toInt().coerceAtLeast(1)
  val paddedWidth = layout.size.width + 2 * pad
  val paddedHeight = layout.size.height + 2 * pad
  if (paddedWidth <= 0 || paddedHeight <= 0) return Rect.Zero
  // A document chooses its font size, so the raster is capped: a run larger than the cap is drawn
  // scaled down and its bounds scaled back, rounded outward, at a coarser grain.
  val area = paddedWidth.toDouble() * paddedHeight
  val scale = if (area > MAX_INK_PIXELS) sqrt(MAX_INK_PIXELS / area).toFloat() else 1f
  val width = ceil(paddedWidth * scale).toInt().coerceAtLeast(1)
  val height = ceil(paddedHeight * scale).toInt().coerceAtLeast(1)
  val bitmap = ImageBitmap(width, height)
  CanvasDrawScope().draw(
    Density(1f),
    LayoutDirection.Ltr,
    Canvas(bitmap),
    Size(width.toFloat(), height.toFloat()),
  ) {
    scale(scale, pivot = Offset.Zero) {
      drawText(layout, color = Color.Black, topLeft = Offset(pad.toFloat(), pad.toFloat()))
    }
  }
  val pixels = IntArray(width * height)
  bitmap.readPixels(pixels)
  var left = width
  var top = height
  var right = -1
  var bottom = -1
  for (y in 0 until height) {
    val row = y * width
    for (x in 0 until width) {
      if (pixels[row + x] ushr 24 != 0) {
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        bottom = y
      }
    }
  }
  if (right < 0) return Rect.Zero
  return Rect(
    floor(left / scale) - pad,
    floor(top / scale) - pad,
    ceil((right + 1) / scale) - pad,
    ceil((bottom + 1) / scale) - pad,
  )
}

/** The most pixels one ink measurement rasterises: 4 MB of ARGB. */
private const val MAX_INK_PIXELS = 1024.0 * 1024.0

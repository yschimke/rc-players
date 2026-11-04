package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * A reusable bitmap the software rasterizer's unpremultiplied ARGB frame is copied into, so a
 * steady scene costs one pixel copy per mesh and no allocation.
 */
internal expect class RcArgbFrame(width: Int, height: Int) {
  val width: Int
  val height: Int

  /** Replace the pixels with [argb], `width * height` unpremultiplied ARGB values. */
  fun update(argb: IntArray)

  val image: ImageBitmap
}

/**
 * Draw [vertexCount] vertices as a triangle list with per-vertex colours, through the platform's
 * `drawVertices`: `android.graphics.Canvas` on Android, Skia elsewhere. Both rasterize on the GPU
 * when the canvas is hardware-backed. With a [texture], [uvs] (0..1, v up) sample it and the vertex
 * colours modulate the texel, as lighting does in the reference.
 */
internal expect fun DrawScope.rcDrawTriangles(
  positions: FloatArray,
  colors: IntArray,
  uvs: FloatArray?,
  vertexCount: Int,
  texture: ImageBitmap?,
)

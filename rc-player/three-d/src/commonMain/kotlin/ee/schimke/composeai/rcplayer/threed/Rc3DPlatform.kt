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

  /**
   * Replace the pixels of the rectangle `[left, right)` by `[top, bottom)` with the same pixels of
   * [argb], `width * height` unpremultiplied ARGB values. The rest of the bitmap keeps what it
   * held.
   */
  fun update(argb: IntArray, left: Int, top: Int, right: Int, bottom: Int)

  val image: ImageBitmap
}

/**
 * Draws triangle lists through the platform's `drawVertices`: `android.graphics.Canvas` on Android,
 * Skia elsewhere. Both rasterize on the GPU when the canvas is hardware-backed. One per session, so
 * the paint, the texture shader and the vertex arrays are made once and reused from frame to frame.
 */
internal expect class RcTriangleDrawer() {
  /**
   * Draw [vertexCount] vertices as a triangle list with per-vertex colours. With a [texture], [uvs]
   * (0..1, v up) sample it and the vertex colours modulate the texel, as lighting does in the
   * reference.
   */
  fun draw(
    scope: DrawScope,
    positions: FloatArray,
    colors: IntArray,
    uvs: FloatArray?,
    vertexCount: Int,
    texture: ImageBitmap?,
  )
}

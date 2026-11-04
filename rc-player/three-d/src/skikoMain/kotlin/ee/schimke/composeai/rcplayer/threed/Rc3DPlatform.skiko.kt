package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.VertexMode

internal actual class RcArgbFrame
actual constructor(actual val width: Int, actual val height: Int) {
  private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
  private val bytes = ByteArray(width * height * 4)
  private val bitmap = Bitmap().also { it.allocPixels(info) }
  private var current: ImageBitmap = bitmap.asComposeImageBitmap()

  actual val image: ImageBitmap
    get() = current

  actual fun update(argb: IntArray) {
    // BGRA_8888 is B, G, R, A in memory order.
    var o = 0
    for (p in argb) {
      bytes[o] = p.toByte()
      bytes[o + 1] = (p shr 8).toByte()
      bytes[o + 2] = (p shr 16).toByte()
      bytes[o + 3] = (p ushr 24).toByte()
      o += 4
    }
    bitmap.installPixels(info, bytes, width * 4)
    bitmap.notifyPixelsChanged()
    current = bitmap.asComposeImageBitmap()
  }
}

internal actual fun DrawScope.rcDrawTriangles(
  positions: FloatArray,
  colors: IntArray,
  uvs: FloatArray?,
  vertexCount: Int,
  texture: ImageBitmap?,
) {
  // Skia takes the vertex count from the arrays themselves.
  val pos = if (positions.size == vertexCount * 2) positions else positions.copyOf(vertexCount * 2)
  val cols = if (colors.size == vertexCount) colors else colors.copyOf(vertexCount)
  val paint = Paint()
  var texs: FloatArray? = null
  // Colours only: kDst keeps the vertex colours. With a texture, kModulate multiplies them in.
  var blend = BlendMode.DST
  if (texture != null && uvs != null) {
    val bitmap = texture.asSkiaBitmap()
    val tw = bitmap.width.toFloat()
    val th = bitmap.height.toFloat()
    paint.shader =
      Image.makeFromBitmap(bitmap)
        .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null)
    texs = FloatArray(vertexCount * 2) { i -> if (i % 2 == 0) uvs[i] * tw else (1f - uvs[i]) * th }
    blend = BlendMode.MODULATE
  }
  drawContext.canvas.nativeCanvas.drawVertices(
    VertexMode.TRIANGLES,
    pos,
    cols,
    texs,
    null,
    blend,
    paint,
  )
}

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
import org.jetbrains.skia.Shader
import org.jetbrains.skia.VertexMode

internal actual class RcArgbFrame
actual constructor(actual val width: Int, actual val height: Int) {
  private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
  private val bytes = ByteArray(width * height * 4)
  private val bitmap = Bitmap().also { it.allocPixels(info) }
  private var current: ImageBitmap = bitmap.asComposeImageBitmap()

  actual val image: ImageBitmap
    get() = current

  actual fun update(argb: IntArray, left: Int, top: Int, right: Int, bottom: Int) {
    if (left >= right || top >= bottom) return
    // BGRA_8888 is B, G, R, A in memory order. Only the rectangle is converted; the rest of
    // [bytes] still holds what it did.
    for (y in top until bottom) {
      var i = y * width + left
      var o = i * 4
      val end = y * width + right
      while (i < end) {
        val p = argb[i++]
        bytes[o] = p.toByte()
        bytes[o + 1] = (p shr 8).toByte()
        bytes[o + 2] = (p shr 16).toByte()
        bytes[o + 3] = (p ushr 24).toByte()
        o += 4
      }
    }
    bitmap.installPixels(info, bytes, width * 4)
    bitmap.notifyPixelsChanged()
    current = bitmap.asComposeImageBitmap()
  }
}

internal actual class RcTriangleDrawer actual constructor() {
  private val paint = Paint()
  private var shaderBitmap: Bitmap? = null
  private var shaderGeneration = 0
  private var shader: Shader? = null
  private var pos = FloatArray(0)
  private var cols = IntArray(0)
  private var texs = FloatArray(0)

  actual fun draw(
    scope: DrawScope,
    positions: FloatArray,
    colors: IntArray,
    uvs: FloatArray?,
    vertexCount: Int,
    texture: ImageBitmap?,
  ) {
    // Skia takes the vertex count from the arrays themselves, so they are kept at exactly that
    // size and only reallocated when it changes.
    if (pos.size != vertexCount * 2) pos = FloatArray(vertexCount * 2)
    if (cols.size != vertexCount) cols = IntArray(vertexCount)
    positions.copyInto(pos, 0, 0, vertexCount * 2)
    colors.copyInto(cols, 0, 0, vertexCount)
    var texCoords: FloatArray? = null
    // Colours only: kDst keeps the vertex colours. With a texture, kModulate multiplies them in.
    var blend = BlendMode.DST
    if (texture != null && uvs != null) {
      val bitmap = texture.asSkiaBitmap()
      // An image is a snapshot, so a bitmap the document draws into needs a new one when its
      // pixels change: the generation id says when.
      if (bitmap !== shaderBitmap || bitmap.generationId != shaderGeneration) {
        shaderBitmap = bitmap
        shaderGeneration = bitmap.generationId
        shader =
          Image.makeFromBitmap(bitmap)
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null)
      }
      paint.shader = shader
      if (texs.size != vertexCount * 2) texs = FloatArray(vertexCount * 2)
      val tw = bitmap.width.toFloat()
      val th = bitmap.height.toFloat()
      for (i in 0 until vertexCount) {
        texs[i * 2] = uvs[i * 2] * tw
        texs[i * 2 + 1] = (1f - uvs[i * 2 + 1]) * th
      }
      texCoords = texs
      blend = BlendMode.MODULATE
    } else {
      paint.shader = null
    }
    scope.drawContext.canvas.nativeCanvas.drawVertices(
      VertexMode.TRIANGLES,
      pos,
      cols,
      texCoords,
      null,
      blend,
      paint,
    )
  }
}

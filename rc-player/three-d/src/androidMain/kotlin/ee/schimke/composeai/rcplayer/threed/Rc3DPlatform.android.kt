package ee.schimke.composeai.rcplayer.threed

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

internal actual class RcArgbFrame actual constructor(actual val width: Int, actual val height: Int) {
  private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  actual val image: ImageBitmap = bitmap.asImageBitmap()

  // `setPixels` takes unpremultiplied ARGB, which is what the rasterizer writes. A mutable bitmap's
  // generation id changes with it, so HWUI re-uploads the texture on the next draw.
  actual fun update(argb: IntArray) {
    bitmap.setPixels(argb, 0, width, 0, 0, width, height)
  }
}

internal actual fun DrawScope.rcDrawTriangles(
  positions: FloatArray,
  colors: IntArray,
  uvs: FloatArray?,
  vertexCount: Int,
  texture: ImageBitmap?,
) {
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  var texs: FloatArray? = null
  if (texture != null && uvs != null) {
    val bitmap = texture.asAndroidBitmap()
    paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    // A bitmap shader samples in pixels, and v runs bottom-up while rows run top-down.
    texs =
      FloatArray(vertexCount * 2) { i ->
        if (i % 2 == 0) uvs[i] * bitmap.width else (1f - uvs[i]) * bitmap.height
      }
  }
  // With both colours and a shader, Android multiplies them: lighting modulates the texel.
  drawContext.canvas.nativeCanvas.drawVertices(
    Canvas.VertexMode.TRIANGLES,
    vertexCount * 2,
    positions,
    0,
    texs,
    0,
    colors,
    0,
    null,
    0,
    0,
    paint,
  )
}

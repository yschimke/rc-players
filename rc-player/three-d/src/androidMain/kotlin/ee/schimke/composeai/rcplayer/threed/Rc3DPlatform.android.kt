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
  actual fun update(argb: IntArray, left: Int, top: Int, right: Int, bottom: Int) {
    if (left >= right || top >= bottom) return
    bitmap.setPixels(argb, top * width + left, width, left, top, right - left, bottom - top)
  }
}

internal actual class RcTriangleDrawer actual constructor() {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var shaderBitmap: Bitmap? = null
  private var shader: BitmapShader? = null
  private var texs = FloatArray(0)

  actual fun draw(
    scope: DrawScope,
    positions: FloatArray,
    colors: IntArray,
    uvs: FloatArray?,
    vertexCount: Int,
    texture: ImageBitmap?,
  ) {
    var texCoords: FloatArray? = null
    if (texture != null && uvs != null) {
      val bitmap = texture.asAndroidBitmap()
      // A BitmapShader samples the bitmap as it is when drawn, so one per bitmap is enough even
      // for a bitmap the document draws into.
      if (bitmap !== shaderBitmap) {
        shaderBitmap = bitmap
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      }
      paint.shader = shader
      if (texs.size < vertexCount * 2) texs = FloatArray(vertexCount * 2)
      // A bitmap shader samples in pixels, and v runs bottom-up while rows run top-down.
      val w = bitmap.width.toFloat()
      val h = bitmap.height.toFloat()
      for (i in 0 until vertexCount) {
        texs[i * 2] = uvs[i * 2] * w
        texs[i * 2 + 1] = (1f - uvs[i * 2 + 1]) * h
      }
      texCoords = texs
    } else {
      paint.shader = null
    }
    // With both colours and a shader, Android multiplies them: lighting modulates the texel.
    scope.drawContext.canvas.nativeCanvas.drawVertices(
      Canvas.VertexMode.TRIANGLES,
      vertexCount * 2,
      positions,
      0,
      texCoords,
      0,
      colors,
      0,
      null,
      0,
      0,
      paint,
    )
  }
}

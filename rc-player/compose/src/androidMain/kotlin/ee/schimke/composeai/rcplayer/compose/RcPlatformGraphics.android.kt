package ee.schimke.composeai.rcplayer.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.PathMeasure
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.security.MessageDigest

/**
 * Compose on Android builds typefaces from files, not bytes, so the bytes are written once per
 * [identity] to the process temp directory (the app's cache directory) and read from there.
 */
internal actual fun rcFontFromBytes(
  identity: String,
  data: ByteArray,
  weight: FontWeight,
  style: FontStyle,
  variationSettings: FontVariation.Settings?,
): Font {
  val file = rcFontFile(data)
  return if (variationSettings == null) Font(file, weight, style)
  else Font(file, weight, style, variationSettings)
}

private val rcFontFiles = mutableMapOf<String, File>()

/**
 * One file per distinct font payload — instances of the same file share it. Keyed by SHA-256 of
 * the whole payload, so two different fonts can never be handed each other's file.
 */
private fun rcFontFile(data: ByteArray): File {
  val key =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
  return synchronized(rcFontFiles) {
    rcFontFiles[key]?.takeIf { it.length() == data.size.toLong() }
      ?: File.createTempFile("rc-font-", ".ttf").also { file ->
        file.deleteOnExit()
        file.writeBytes(data)
        rcFontFiles[key] = file
      }
  }
}

/**
 * `Canvas.getMatrix` — deprecated because on a hardware canvas it is relative to the current render
 * node rather than the window. Both callers want the transform the player itself applied (text
 * rotation, observer coordinates), which lives inside the player's own node, so that is enough.
 */
internal actual fun DrawScope.rcLocalToDevice(): FloatArray {
  val values = FloatArray(9)
  @Suppress("DEPRECATION") drawContext.canvas.nativeCanvas.matrix.getValues(values)
  return values
}

internal actual fun decodeRcEncodedImage(data: ByteArray): ImageBitmap =
  requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size)) {
      "Undecodable image (${data.size} bytes)"
    }
    .asImageBitmap()

internal actual fun rcRasterImage(
  width: Int,
  height: Int,
  pixels: ByteArray,
  alphaOnly: Boolean,
): ImageBitmap {
  // `Bitmap.createBitmap(int[], ...)` takes unpremultiplied ARGB and converts to the config, which
  // is exactly the document's layout once the bytes are reordered — no premultiply by hand.
  val colors =
    IntArray(width * height) { i ->
      if (alphaOnly) {
        (pixels[i].toInt() and 0xff) shl 24
      } else {
        val o = i * 4
        val r = pixels[o].toInt() and 0xff
        val g = pixels[o + 1].toInt() and 0xff
        val b = pixels[o + 2].toInt() and 0xff
        val a = pixels[o + 3].toInt() and 0xff
        (a shl 24) or (r shl 16) or (g shl 8) or b
      }
    }
  val config = if (alphaOnly) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888
  return Bitmap.createBitmap(colors, width, height, config).asImageBitmap()
}

internal actual class RcPathMeasure actual constructor(path: Path) {
  private val measure = PathMeasure(path.asAndroidPath(), false)
  private val point = FloatArray(2)
  private val slope = FloatArray(2)

  actual val length: Float
    get() = measure.length

  actual fun nextContour(): Boolean = measure.nextContour()

  actual fun position(distance: Float): Offset? =
    if (measure.getPosTan(distance, point, null)) Offset(point[0], point[1]) else null

  actual fun tangent(distance: Float): Offset? =
    if (measure.getPosTan(distance, null, slope)) Offset(slope[0], slope[1]) else null
}

internal actual fun rcRuntimeShaderBuilder(source: String): RcRuntimeShaderBuilder {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    throw UnsupportedOperationException("Runtime shaders need API 33; this is ${Build.VERSION.SDK_INT}")
  }
  return RcAndroidRuntimeShaderBuilder(RuntimeShader(source))
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class RcAndroidRuntimeShaderBuilder(private val shader: RuntimeShader) :
  RcRuntimeShaderBuilder {
  override fun floatUniform(name: String, values: FloatArray) {
    shader.setFloatUniform(name, values)
  }

  override fun intUniform(name: String, values: IntArray) {
    require(values.size in 1..4) { "Integer uniform '$name' has ${values.size} values" }
    shader.setIntUniform(name, values)
  }

  override fun bitmapUniform(name: String, image: ImageBitmap) {
    shader.setInputShader(
      name,
      BitmapShader(
        image.asAndroidBitmap(),
        android.graphics.Shader.TileMode.CLAMP,
        android.graphics.Shader.TileMode.CLAMP,
      ),
    )
  }

  override fun build(): RcRuntimeShader = RcRuntimeShader(shader, shader)
}

internal actual fun rcRuntimeShaderSupported(source: String): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runCatching { RuntimeShader(source) }.isSuccess

internal actual fun Path.rcConicTo(
  x0: Float,
  y0: Float,
  x1: Float,
  y1: Float,
  x2: Float,
  y2: Float,
  weight: Float,
) {
  // `android.graphics.Path` has no conic; Skia's own chop into quads stands in.
  rcConicAsQuads(x0, y0, x1, y1, x2, y2, weight) { cx, cy, ex, ey -> quadraticTo(cx, cy, ex, ey) }
}

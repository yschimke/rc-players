package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.PathMeasure
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun rcFontFromBytes(
  identity: String,
  data: ByteArray,
  weight: FontWeight,
  style: FontStyle,
  variationSettings: FontVariation.Settings?,
): Font =
  if (variationSettings == null) {
    androidx.compose.ui.text.platform.Font(identity, data, weight, style)
  } else {
    androidx.compose.ui.text.platform.Font(identity, data, weight, style, variationSettings)
  }

internal actual fun DrawScope.rcLocalToDevice(): FloatArray =
  drawContext.canvas.nativeCanvas.localToDeviceAsMatrix33.mat

internal actual fun decodeRcEncodedImage(data: ByteArray): ImageBitmap =
  Image.makeFromEncoded(data).toComposeImageBitmap()

internal actual fun rcRasterImage(
  width: Int,
  height: Int,
  pixels: ByteArray,
  alphaOnly: Boolean,
): ImageBitmap {
  val colorType = if (alphaOnly) ColorType.ALPHA_8 else ColorType.RGBA_8888
  val rowBytes = if (alphaOnly) width else width * 4
  return Image.makeRaster(
      ImageInfo(width, height, colorType, ColorAlphaType.UNPREMUL),
      pixels,
      rowBytes,
    )
    .toComposeImageBitmap()
}

internal actual class RcPathMeasure actual constructor(path: Path) {
  private val measure = PathMeasure(path.asSkiaPath(), false)

  actual val length: Float
    get() = measure.length

  actual fun nextContour(): Boolean = measure.nextContour()

  actual fun position(distance: Float): Offset? =
    measure.getPosition(distance)?.let { Offset(it.x, it.y) }

  actual fun tangent(distance: Float): Offset? =
    measure.getTangent(distance)?.let { Offset(it.x, it.y) }
}

internal actual fun rcRuntimeShaderBuilder(source: String): RcRuntimeShaderBuilder {
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
  return object : RcRuntimeShaderBuilder {
    override fun floatUniform(name: String, values: FloatArray) {
      when (values.size) {
        1 -> builder.uniform(name, values[0])
        2 -> builder.uniform(name, values[0], values[1])
        3 -> builder.uniform(name, values[0], values[1], values[2])
        4 -> builder.uniform(name, values[0], values[1], values[2], values[3])
        else -> builder.uniform(name, values)
      }
    }

    override fun intUniform(name: String, values: IntArray) {
      when (values.size) {
        1 -> builder.uniform(name, values[0])
        2 -> builder.uniform(name, values[0], values[1])
        3 -> builder.uniform(name, values[0], values[1], values[2])
        4 -> builder.uniform(name, values[0], values[1], values[2], values[3])
        else -> throw IllegalArgumentException("Integer uniform '$name' has ${values.size} values")
      }
    }

    override fun bitmapUniform(name: String, image: ImageBitmap) {
      builder.child(name, image.asSkiaBitmap().makeShader())
    }

    override fun build(): RcRuntimeShader =
      RcRuntimeShader(builder.makeShader().asComposeShader(), builder)
  }
}

internal actual fun rcRuntimeShaderSupported(source: String): Boolean = runCatching {
  RuntimeEffect.makeForShader(source).close()
}
  .isSuccess

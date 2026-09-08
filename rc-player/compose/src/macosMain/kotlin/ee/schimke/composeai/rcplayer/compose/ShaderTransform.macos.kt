package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.skiaShader
import org.jetbrains.skia.Matrix33

internal actual fun transformRcShader(shader: Shader, matrix: FloatArray?): Shader =
  if (matrix == null) shader
  else shader.skiaShader.makeWithLocalMatrix(matrix.toSkiaMatrix()).asComposeShader()

private fun FloatArray.toSkiaMatrix(): Matrix33 =
  when (size) {
    9 -> Matrix33(*this)
    16 -> Matrix33(this[0], this[1], this[3], this[4], this[5], this[7], this[8], this[9], this[15])
    else -> error("AndroidX shader matrix has $size values")
  }

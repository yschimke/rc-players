package ee.schimke.composeai.rcplayer.compose

import android.graphics.Matrix
import androidx.compose.ui.graphics.Shader

internal actual fun transformRcShader(shader: Shader, matrix: FloatArray?): Shader {
  // Android shaders carry their local matrix as mutable state rather than returning a copy, so the
  // null case resets it: the same shader object is installed again on the next paint.
  shader.setLocalMatrix(matrix?.toAndroidMatrix())
  return shader
}

private fun FloatArray.toAndroidMatrix(): Matrix =
  Matrix().apply {
    when (size) {
      9 -> setValues(this@toAndroidMatrix)
      16 -> {
        val m = this@toAndroidMatrix
        setValues(floatArrayOf(m[0], m[1], m[3], m[4], m[5], m[7], m[8], m[9], m[15]))
      }
      else -> error("AndroidX shader matrix has $size values")
    }
  }

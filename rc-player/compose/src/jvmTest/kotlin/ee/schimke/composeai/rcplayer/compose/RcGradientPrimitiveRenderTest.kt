package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

class RcGradientPrimitiveRenderTest {
  @Test
  fun aGradientOnThePaintShadesACircle() {
    // PaintBundle.GRADIENT (11), linear: red at x = 0 to blue at x = 40. The circle drew its
    // paint's
    // flat colour instead — `canvas_shader_gradient`'s sweep disc came out solid black.
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
        listOf(
          RcPaintData(
            listOf(
              GRADIENT,
              2,
              0xffff0000.toInt(),
              0xff0000ff.toInt(),
              0,
              0f.toRawBits(),
              0f.toRawBits(),
              40f.toRawBits(),
              0f.toRawBits(),
              0,
            )
          ),
          RcDraw3(
            RcOpcodes.DRAW_CIRCLE,
            RcFloatWord.literal(20f),
            RcFloatWord.literal(20f),
            RcFloatWord.literal(18f),
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = 40, height = 40, density = Density(1f)) {
        // Sized, as a host sizes it: a gradient brush has no shader on a 0 x 0 canvas.
        RcComposePlayer(document, Modifier.fillMaxSize())
      }
    try {
      val image = scene.render()
      val bitmap = Bitmap().apply { allocN32Pixels(40, 40) }
      check(image.readPixels(bitmap))
      val left = bitmap.getColor(5, 20)
      val right = bitmap.getColor(35, 20)

      assertTrue(left ushr 16 and 0xff > left and 0xff, "the left of the circle is not red")
      assertTrue(right and 0xff > right ushr 16 and 0xff, "the right of the circle is not blue")
    } finally {
      scene.close()
    }
  }

  private companion object {
    /** PaintBundle.GRADIENT, linear (type 0 in the high half-word). */
    const val GRADIENT = 11
  }
}

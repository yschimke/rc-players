package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rccreation.compose.state.rf
import kotlin.test.Test

/**
 * A canvas that draws a bitmap at the bitmap's own size reads the image's width and height, which
 * the creation writer declares ahead of the layout. The player evaluates them there.
 */
class RcCanvasBitmapSizePlaybackTest {
  private val red: ImageBitmap by lazy {
    ImageBitmap(8, 8).also { bitmap ->
      Canvas(bitmap).drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color.Red })
    }
  }

  @Test
  fun aBitmapIsDrawnAtItsOwnSize() {
    val pixels =
      playCanvas(40, 40) {
        drawRect(RemotePaint { color = RemoteColor(Color.Blue) })
        drawImage(red.rb)
        // The whole bitmap (its source rectangle is its width and height) into a 10 x 10 square.
        drawScaledBitmap(
          red.rb,
          dstOffset = RemoteOffset(20f.rf, 20f.rf),
          dstSize = RemoteSize(10f.rf, 10f.rf),
        )
      }
    assertColor(Color.Red, pixels[4, 4], "the bitmap at the origin")
    assertColor(Color.Blue, pixels[12, 12], "past the bitmap's 8 pixels")
    assertColor(Color.Red, pixels[25, 25], "the scaled bitmap")
    assertColor(Color.Blue, pixels[35, 35], "past the scaled bitmap")
  }

  /** `drawImage(bitmap, topLeft)` draws the bitmap at its size from there, not into (0, 0). */
  @Test
  fun aBitmapDrawnAtAnOffsetKeepsItsSize() {
    val pixels =
      playCanvas(40, 40) {
        drawRect(RemotePaint { color = RemoteColor(Color.Blue) })
        drawImage(red.rb, RemoteOffset(20f.rf, 10f.rf))
      }
    assertColor(Color.Red, pixels[21, 11], "the bitmap's first pixel")
    assertColor(Color.Red, pixels[27, 17], "the bitmap's last pixel")
    assertColor(Color.Blue, pixels[29, 14], "past its width")
    assertColor(Color.Blue, pixels[24, 19], "past its height")
    assertColor(Color.Blue, pixels[4, 4], "nothing at the origin")
  }
}

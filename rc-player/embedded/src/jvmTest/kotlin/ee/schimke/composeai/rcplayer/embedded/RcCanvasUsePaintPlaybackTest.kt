package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.painter.painterRemoteImageBitmap
import ee.schimke.composeai.rccreation.compose.state.RemoteBlendMode
import ee.schimke.composeai.rccreation.compose.state.RemoteBlendModeColorFilter
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rccreation.compose.state.rf
import kotlin.test.Test

/**
 * `RemoteCanvas.usePaint` writes the paint it is given, so what is drawn after it without a paint
 * of its own is drawn with it: a painter's alpha and blend mode, a colour filter, a plain colour.
 */
class RcCanvasUsePaintPlaybackTest {
  private val red: ImageBitmap by lazy {
    ImageBitmap(8, 8).also { bitmap ->
      Canvas(bitmap).drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color.Red })
    }
  }

  private fun paint(color: Color) = RemotePaint { this.color = RemoteColor(color) }

  private val painterAtHalfAlpha: RemoteDrawScope.() -> Unit = {
    drawRect(paint(Color.Blue))
    with(painterRemoteImageBitmap(red.rb)) { draw(alpha = 0.5f.rf) }
  }

  private val tintedBitmap: RemoteDrawScope.() -> Unit = {
    drawRect(paint(Color.Blue))
    usePaint(
      RemotePaint {
        colorFilter = RemoteBlendModeColorFilter(RemoteColor(Color.Green), RemoteBlendMode.SrcIn)
      },
    ) {
      drawScaledBitmap(red.rb)
    }
  }

  private val colourForADrawWithoutAPaint: RemoteDrawScope.() -> Unit = {
    drawRect(paint(Color.Blue))
    usePaint(paint(Color.Green)) { drawCircle(null, 10f.rf) }
  }

  /** A painter drawn at half alpha over blue is half red, half blue. */
  @Test
  fun aPainterDrawnWithAlphaIsBlended() {
    val pixels = playCanvas(SIZE, SIZE, painterAtHalfAlpha)
    assertColor(Color(0.5f, 0f, 0.5f), pixels[SIZE / 2, SIZE / 2], "the painter over blue")
  }

  /** A bitmap drawn under a tint colour filter takes the tint's colour. */
  @Test
  fun aColourFilterTintsWhatIsDrawnUnderIt() {
    val pixels = playCanvas(SIZE, SIZE, tintedBitmap)
    assertColor(Color.Green, pixels[SIZE / 2, SIZE / 2], "the tinted bitmap")
  }

  /** A shape drawn with no paint of its own takes the paint `usePaint` set. */
  @Test
  fun aDrawWithoutAPaintUsesThePaintSet() {
    val pixels = playCanvas(SIZE, SIZE, colourForADrawWithoutAPaint)
    assertColor(Color.Green, pixels[SIZE / 2, SIZE / 2], "the circle")
    assertColor(Color.Blue, pixels[2, 2], "the background")
  }

  /** Evidence for `renders/creation-canvas-paint/`: the three cells above. */
  @Test
  fun writeUsePaintEvidence() {
    writeEvidence(
      "use-paint",
      listOf(painterAtHalfAlpha, tintedBitmap, colourForADrawWithoutAPaint).map {
        playDocument(captureCanvas(SIZE, SIZE, it), SIZE, SIZE)
      },
    )
  }

  private companion object {
    const val SIZE = 40
  }
}

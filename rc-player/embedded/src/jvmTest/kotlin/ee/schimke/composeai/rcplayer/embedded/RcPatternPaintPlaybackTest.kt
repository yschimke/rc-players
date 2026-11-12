package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A pattern's body is a template the player expands wherever the pattern is called, so it draws
 * with the paint the document sets in it, not with whatever paint the canvas last sent before the
 * definition: a pattern that draws red draws red at every call, and what is drawn after a call is
 * drawn with its own paint.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcPatternPaintPlaybackTest {
  private val red
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  private val blue
    get() = RemotePaint { color = RemoteColor(Color.Blue) }

  private fun play(onDraw: RemoteDrawScope.() -> Unit): PixelMap {
    val bytes = runBlocking {
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(120, 40, 160),
        profile = RcEmbeddedPlayerProfile,
      ) {
        RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
      }
    }
    val document = RcDocumentCodec.decode(bytes)
    lateinit var pixels: PixelMap
    runSkikoComposeUiTest(Size(120f, 40f), Density(1f)) {
      setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
      waitForIdle()
      pixels = onRoot().captureToImage().toPixelMap()
    }
    return pixels
  }

  private fun at(x: Float) = RemoteOffset(x.rf, 20f.rf)

  /**
   * The sequence from review: a red circle, a red-circle pattern defined and called, a blue circle,
   * the pattern called again. The second call has to be red.
   */
  @Test
  fun aPatternCalledAfterAnotherPaintKeepsItsOwn() {
    val pixels = play {
      drawCircle(red, 8f.rf, at(10f))
      val dot = remoteCanvas.definePattern { drawCircle(red, 8f.rf, at(30f)) }
      dot()
      drawCircle(blue, 8f.rf, at(50f))
      remoteCanvas.translate(40f.rf, 0f.rf)
      dot()
      // After the call the player's paint is the pattern's; this one asks for blue again.
      drawCircle(blue, 8f.rf, at(50f))
    }
    assertEquals(Color.Red, pixels[10, 20], "the first circle")
    assertEquals(Color.Red, pixels[30, 20], "the pattern's first call")
    assertEquals(Color.Blue, pixels[50, 20], "the circle between the calls")
    assertEquals(Color.Red, pixels[70, 20], "the pattern's second call")
    assertEquals(Color.Blue, pixels[90, 20], "the circle after the second call")
  }

  /** The same with a parameter: the overloads share the body's recording. */
  @Test
  fun aParameterizedPatternCalledAfterAnotherPaintKeepsItsOwn() {
    val pixels = play {
      drawCircle(red, 8f.rf, at(10f))
      val dot =
        remoteCanvas.definePattern<RemoteFloat> { x ->
          drawCircle(red, 8f.rf, RemoteOffset(x, 20f.rf))
        }
      dot(30f.rf + 0f.rf * width)
      drawCircle(blue, 8f.rf, at(50f))
      dot(70f.rf + 0f.rf * width)
      drawCircle(blue, 8f.rf, at(90f))
    }
    assertEquals(Color.Red, pixels[30, 20], "the pattern's first call")
    assertEquals(Color.Blue, pixels[50, 20], "the circle between the calls")
    assertEquals(Color.Red, pixels[70, 20], "the pattern's second call")
    assertEquals(Color.Blue, pixels[90, 20], "the circle after the second call")
  }

  /** A for-each body is such a template too: its items and what follows draw with their own. */
  @Test
  fun aForEachBodyAndWhatFollowsItKeepTheirPaints() {
    val pixels = play {
      drawCircle(red, 8f.rf, at(10f))
      val dot = remoteCanvas.definePattern { drawCircle(blue, 8f.rf, at(30f)) }
      dot()
      drawCircle(red, 8f.rf, at(50f))
      remoteCanvas.forEach(listOf(70f.rf + 0f.rf * width, 90f.rf + 0f.rf * width)) { x ->
        drawCircle(red, 8f.rf, RemoteOffset(x, 20f.rf))
      }
      drawCircle(blue, 8f.rf, at(110f))
    }
    assertEquals(Color.Red, pixels[10, 20])
    assertEquals(Color.Blue, pixels[30, 20], "the pattern")
    assertEquals(Color.Red, pixels[50, 20], "the circle after the call")
    assertEquals(Color.Red, pixels[70, 20], "the first item")
    assertEquals(Color.Red, pixels[90, 20], "the second item")
    assertEquals(Color.Blue, pixels[110, 20], "the circle after the for-each")
  }
}

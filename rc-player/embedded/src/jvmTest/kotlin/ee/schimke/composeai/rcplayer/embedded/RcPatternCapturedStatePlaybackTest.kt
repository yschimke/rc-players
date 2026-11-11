package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
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
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A value from outside a pattern that the pattern's body reads first is still the same value after
 * it: it is declared once, outside the template the player expands, so what is drawn later with it
 * reads a variable that exists. Only what the body computes from its own parameters stays in it.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcPatternCapturedStatePlaybackTest {
  private val red
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  /** A host value: a radius of 8, and expressions of it. */
  private val level
    get() = RemoteFloat.createNamedRemoteFloat("level", 8f)

  private fun play(onDraw: RemoteDrawScope.() -> Unit): PixelMap {
    val bytes = runBlocking {
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(120, 40, 160)) {
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

  /** A circle of radius 8 about ([x], 20): red 6 pixels from the centre, nothing 11 from it. */
  private fun PixelMap.assertCircleOfEight(x: Int, what: String) {
    assertEquals(Color.Red, this[x, 20], "$what: its centre")
    assertEquals(Color.Red, this[x + 6, 20], "$what: inside its radius")
    assertEquals(Color.Transparent, this[x + 11, 20], "$what: outside its radius")
  }

  /** The sequence from review: a pattern that reads a named float, then a circle that reads it. */
  @Test
  fun aValueReadFirstInAPatternIsReadAfterIt() {
    val pixels = play {
      val dot = remoteCanvas.definePattern { drawCircle(red, level, at(20f)) }
      dot()
      drawCircle(red, level, at(60f))
    }
    pixels.assertCircleOfEight(20, "the pattern's circle")
    pixels.assertCircleOfEight(60, "the circle after it")
  }

  /** An expression of it, in a pattern with a parameter that the body also computes from. */
  @Test
  fun anExpressionReadFirstInAParameterizedPatternIsReadAfterIt() {
    val pixels = play {
      val dot =
        remoteCanvas.definePattern<RemoteFloat> { x ->
          drawCircle(red, level * 2f - 8f, RemoteOffset(x + 0f.rf * level, 20f.rf))
        }
      dot(level + 12f)
      dot(level + 52f)
      drawCircle(red, level * 2f - 8f, at(100f))
    }
    pixels.assertCircleOfEight(20, "the pattern's first call")
    pixels.assertCircleOfEight(60, "the pattern's second call")
    pixels.assertCircleOfEight(100, "the circle after them")
  }

  /** The same through a for-each body. */
  @Test
  fun aValueReadFirstInAForEachBodyIsReadAfterIt() {
    val pixels = play {
      remoteCanvas.forEach(listOf(level + 12f, level + 52f)) { x ->
        drawCircle(red, level, RemoteOffset(x, 20f.rf))
      }
      drawCircle(red, level, at(100f))
    }
    pixels.assertCircleOfEight(20, "the first item")
    pixels.assertCircleOfEight(60, "the second item")
    pixels.assertCircleOfEight(100, "the circle after them")
  }
}

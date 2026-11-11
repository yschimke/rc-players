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
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * `val count = canvas.save(); …; canvas.restoreToCount(count)` balances the save, as
 * `android.graphics.Canvas` has it: what is drawn after it is not transformed.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcSaveRestoreToCountPlaybackTest {
  private val red
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  private val blue
    get() = RemotePaint { color = RemoteColor(Color.Blue) }

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

  private val origin = RemoteOffset(10f.rf, 20f.rf)
  private val background = Color.Transparent

  @Test
  fun restoreToCountUndoesTheSaveItWasReturnedFrom() {
    val counts = mutableListOf<Int>()
    val pixels = play {
      val canvas = remoteCanvas
      val count = canvas.save()
      counts += count
      canvas.translate(40f.rf, 0f.rf)
      drawCircle(red, 8f.rf, origin)
      canvas.restoreToCount(count)
      counts += canvas.saveCount
      drawCircle(blue, 8f.rf, origin)
    }
    assertEquals(listOf(0, 0), counts, "save returns the count before it")
    assertEquals(Color.Red, pixels[50, 20], "drawn inside the save, translated")
    assertEquals(Color.Blue, pixels[10, 20], "drawn after restoreToCount, not translated")
  }

  /** Nested saves: restoring to the outer save's count undoes both. */
  @Test
  fun restoreToCountUndoesEverySaveSince() {
    val pixels = play {
      val canvas = remoteCanvas
      val outer = canvas.save()
      canvas.translate(40f.rf, 0f.rf)
      val inner = canvas.save()
      canvas.translate(40f.rf, 0f.rf)
      drawCircle(red, 8f.rf, origin)
      canvas.restoreToCount(inner)
      drawCircle(blue, 8f.rf, origin)
      canvas.save()
      canvas.translate(0f.rf, 100f.rf)
      canvas.restoreToCount(outer)
      drawCircle(red, 8f.rf, origin)
    }
    assertEquals(Color.Red, pixels[90, 20], "inside both saves")
    assertEquals(Color.Blue, pixels[50, 20], "inside the outer save only")
    assertEquals(Color.Red, pixels[10, 20], "after both are restored")
  }

  /** Inside a child block, where a save left open is an error. */
  @Test
  fun restoreToCountBalancesASaveInsideAConditionalBlock() {
    val shown = RemoteBoolean.createNamedRemoteBoolean("shown", true)
    val pixels = play {
      drawConditionally(shown) {
        val count = remoteCanvas.save()
        remoteCanvas.translate(40f.rf, 0f.rf)
        drawCircle(red, 8f.rf, origin)
        remoteCanvas.restoreToCount(count)
        drawCircle(blue, 8f.rf, origin)
      }
      drawCircle(blue, 8f.rf, RemoteOffset(110f.rf, 20f.rf))
    }
    assertEquals(Color.Red, pixels[50, 20], "inside the save")
    assertEquals(Color.Blue, pixels[10, 20], "after restoreToCount, in the block")
    assertEquals(Color.Blue, pixels[110, 20], "after the block")
    assertEquals(background, pixels[70, 20])
  }
}

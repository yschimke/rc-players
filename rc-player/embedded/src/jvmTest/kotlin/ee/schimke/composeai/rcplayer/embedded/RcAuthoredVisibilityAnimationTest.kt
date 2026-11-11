package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rccreation.bridge.ExperimentalRcCreationApi
import ee.schimke.composeai.rccreation.bridge.defineVisibilityAnimation
import ee.schimke.composeai.rccreation.bridge.rcCustomExitTransition
import ee.schimke.composeai.rccreation.common.AnimatedFloatExpression
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.action.valueChangeAction
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocumentInto
import ee.schimke.composeai.rccreation.compose.capture.wireValue
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.animateEnterExit
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.visibility
import ee.schimke.composeai.rccreation.compose.state.MutableRemoteInt
import ee.schimke.composeai.rccreation.compose.state.rc
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.ri
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The creation fork and the CMP player end to end: a custom exit authored with the bridge's
 * `defineVisibilityAnimation` — the function written ahead of the layout, as upstream writes it —
 * captures its component into a component offscreen buffer and draws it wiped from the right, and
 * the CMP player plays it.
 */
@OptIn(
  ExperimentalTestApi::class,
  ExperimentalComposeUiApi::class,
  ExperimentalRcCreationApi::class,
)
class RcAuthoredVisibilityAnimationTest {
  @Test
  fun anAuthoredCustomExitPlaysThroughAComponentBuffer() =
    runSkikoComposeUiTest(size = Size(50f, 20f), density = Density(1f)) {
      val document = RcDocumentCodec.decode(author())
      mainClock.autoAdvance = false
      setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      val shown = capture()
      assertTrue((12 until 50).all { shown.isBlue(it) }, "shown: ${shown.row()}")

      onNode(hasClickAction()).performTouchInput { click() }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      mainClock.advanceTimeBy(150)
      waitForIdle()
      val wiping = capture()
      val kept = (10 until 50).count { wiping.isBlue(it) }
      assertTrue(kept in 4..36, "partly wiped from the right: ${wiping.row()}")
      assertTrue(wiping.isBlue(12) && wiping.isClear(48), "wiped from the right: ${wiping.row()}")

      mainClock.advanceTimeBy(1_000)
      waitForIdle()
      val gone = capture()
      assertTrue((10 until 50).none { gone.isBlue(it) }, "gone: ${gone.row()}")
    }

  private fun author(): ByteArray = runBlocking {
    val display = RemoteCreationDisplayInfo(50, 20, 160)
    val writer =
      RemoteDocumentWriter(
        display.width,
        display.height,
        display.densityBehavior.wireValue,
      )
    val wipe = writer.defineVisibilityAnimation { component, progress, width, height, x, y ->
      val buffer = createOffscreenBitmap(component)
      drawComponentToBitmap(component, buffer)
      val kept = floatExpression(width, 1f, progress, SUB, MUL)
      val right = floatExpression(x, kept, ADD)
      val bottom = floatExpression(y, height, ADD)
      save()
      clipRect(x, y, right, bottom)
      drawBitmap(buffer, x, y, floatExpression(x, width, ADD), bottom, 0)
      restore()
    }
    captureCommonRemoteDocumentInto(writer, display) {
      val visible = remember { MutableRemoteInt(VISIBLE) }
      RemoteRow {
        RemoteBox(
          RemoteModifier.size(10.rdp, 20.rdp).clickable(valueChangeAction(visible, GONE.ri)),
        ) {}
        RemoteBox(
          RemoteModifier.size(40.rdp, 20.rdp)
            .background(Color.Blue.rc)
            .visibility(visible)
            .animateEnterExit(exit = rcCustomExitTransition(wipe)),
        ) {}
      }
    }
    writer.encodeToByteArray()
  }

  private fun ComposeUiTest.capture(): PixelMap = onRoot().captureToImage().toPixelMap()

  private fun PixelMap.isBlue(x: Int, y: Int = 10) =
    this[x, y].let { it.alpha > .5f && it.blue > .8f && it.red < .2f && it.green < .2f }

  private fun PixelMap.isClear(x: Int, y: Int = 10) = this[x, y].alpha < .05f

  private fun PixelMap.row(y: Int = 10) =
    (0 until width).joinToString("") { x ->
      when {
        isBlue(x, y) -> "B"
        isClear(x, y) -> "."
        else -> "?"
      }
    }

  private companion object {
    // remote-core `Component.Visibility`.
    const val VISIBLE = 1
    const val GONE = 0
    val ADD = AnimatedFloatExpression.ADD
    val SUB = AnimatedFloatExpression.SUB
    val MUL = AnimatedFloatExpression.MUL
  }
}

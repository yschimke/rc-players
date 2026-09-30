package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertNotEquals

/**
 * The remote-m3 catalog's `Stepper` (`stepper__ideal__default__compact`), captured as published.
 *
 * Its value is `rememberMutableRemoteFloat(0.5f)`, which the document *declares* as a constant
 * float expression that the flat operation list replays every frame, and each button carries a
 * `ValueFloatExpressionChange` on that same id. A click has to outlive the next frame's declaration
 * for the level rail to move — before it did, the player stored the new value and immediately
 * overwrote it, so the Wasm player's stepper looked dead (compose-preview-server#1221).
 */
@OptIn(ExperimentalTestApi::class)
class RcStepperClickTest {
  @Test
  fun eachButtonMovesTheValueAndTheChangeSticks() {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/Stepper-ideal-default-compact.rc"))
        .use { it.readBytes() }
    val document = RcDocumentCodec.decode(bytes)
    runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
      setContent { RcComposePlayer(document) }
      waitForIdle()

      fun frameHash(): Int =
        onRoot().captureToImage().toPixelMap().let { pixels ->
          var hash = 0
          for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            hash = hash * 31 + pixels[x, y].hashCode()
          }
          hash
        }

      val atRest = frameHash()
      // Increase (top button), then again: each press is a further step, not a repeat of the first.
      onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.12f)) }
      waitForIdle()
      val afterOne = frameHash()
      assertNotEquals(atRest, afterOne, "the increase button moved the level rail")
      onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.12f)) }
      waitForIdle()
      val afterTwo = frameHash()
      assertNotEquals(afterOne, afterTwo, "a second press steps again")
      // Decrease (bottom button) walks it back.
      onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.88f)) }
      waitForIdle()
      assertNotEquals(afterTwo, frameHash(), "the decrease button moved it back")
    }
  }
}

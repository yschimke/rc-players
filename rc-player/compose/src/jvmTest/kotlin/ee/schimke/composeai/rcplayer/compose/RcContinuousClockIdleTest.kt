package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.referencesMovingSystemVariable
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A document that animates off the player's continuous clock asks for a frame every frame, for as
 * long as it is shown. Those frames are an infinite animation, and the player asks for them as one,
 * so a host's `InfiniteAnimationPolicy` governs them: a Compose UI test with `autoAdvance` refuses
 * them and goes idle, rather than spinning in `setContent` forever.
 *
 * Refused frames are not lost: the clock picks up again once the policy allows them.
 */
@OptIn(ExperimentalTestApi::class)
class RcContinuousClockIdleTest {

  @Test
  fun aContinuouslyAnimatingDocumentLetsATestGoIdleAndAnimatesOnceFramesAreDrivenByHand() =
    runSkikoComposeUiTest(size = Size(200f, 200f), density = Density(1f)) {
      val document = fixture()
      assertTrue(document.referencesMovingSystemVariable(), "the fixture no longer animates")

      // With the clock advancing on its own, this waits for idle: it returned only if the
      // player's per-frame loop was not running yet, and otherwise never.
      setContent { RcComposePlayer(document, Modifier) }
      waitForIdle()

      // Frames by hand: the policy now lets the clock run, and the arc moves.
      mainClock.autoAdvance = false
      repeat(30) { mainClock.advanceTimeByFrame() }
      val before =
        onRoot().captureToImage().toPixelMap().let { map ->
          IntArray(map.width * map.height) { map[it % map.width, it / map.width].hashCode() }
        }
      repeat(30) { mainClock.advanceTimeByFrame() }
      val after =
        onRoot().captureToImage().toPixelMap().let { map ->
          IntArray(map.width * map.height) { map[it % map.width, it / map.width].hashCode() }
        }
      assertNotEquals(before.toList(), after.toList(), "the clock did not resume once allowed")
    }

  private fun fixture(): RcDocument {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$FIXTURE")) {
          "missing fixture /rc-fixtures/$FIXTURE"
        }
        .use { it.readBytes() }
    return RcDocumentCodec.decode(bytes)
  }

  private companion object {
    const val FIXTURE = "IndeterminateCircularProgress-400x400.rc"
  }
}

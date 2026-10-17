package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.referencesMovingSystemVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation

/**
 * A host can pause a player's never-ending frame loops for part of its tree
 * ([LocalRcInfiniteAnimationPolicy]), as a preview held at a fixed time does: the document draws
 * its first frame and then idles, and animates again once the policy lets it.
 */
@OptIn(ExperimentalTestApi::class)
class RcInfiniteAnimationPolicyTest {

  /** Pauses every infinite operation for as long as it is in place. */
  private val paused =
    object : InfiniteAnimationPolicy {
      override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R = awaitCancellation()
    }

  @Test
  fun aPausingPolicyHoldsAContinuouslyAnimatingDocumentUntilItIsLifted() =
    runSkikoComposeUiTest(size = Size(200f, 200f), density = Density(1f)) {
      val document = fixture()
      assertTrue(document.referencesMovingSystemVariable(), "the fixture no longer animates")
      mainClock.autoAdvance = false
      var policy by mutableStateOf<InfiniteAnimationPolicy?>(paused)
      setContent {
        CompositionLocalProvider(LocalRcInfiniteAnimationPolicy provides policy) {
          RcComposePlayer(document, Modifier)
        }
      }

      repeat(5) { mainClock.advanceTimeByFrame() }
      val first = pixels()
      repeat(30) { mainClock.advanceTimeByFrame() }
      assertEquals(first, pixels(), "the paused document kept animating")

      policy = null
      repeat(30) { mainClock.advanceTimeByFrame() }
      assertNotEquals(first, pixels(), "the document did not animate once the policy was lifted")
    }

  private fun SkikoComposeUiTest.pixels(): List<Int> =
    onRoot().captureToImage().toPixelMap().let { map ->
      List(map.width * map.height) { map[it % map.width, it / map.width].hashCode() }
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

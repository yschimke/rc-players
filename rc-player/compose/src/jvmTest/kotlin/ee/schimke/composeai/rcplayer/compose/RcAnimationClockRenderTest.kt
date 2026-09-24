package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

class RcAnimationClockRenderTest {
  @Test
  fun aHostClockSetsTheAnimationTimeTheDocumentReads() {
    // A bar as wide as `ANIMATION_TIME`, in seconds. Left to the frame clock the first frame reads
    // zero and nothing is drawn; a host clock that says 25 s draws a 25 px bar on that same first
    // frame, and moving the clock back redraws it shorter.
    val red = 0xffff0000.toInt()
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 10, modern = false),
        listOf(
          RcPaintData(listOf(4, red)),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord(NAN_REFERENCE or RcSystemVariables.ANIMATION_TIME),
            RcFloatWord.literal(10f),
          ),
        ),
      )
    val seconds = mutableFloatStateOf(25f)
    val scene =
      ImageComposeScene(width = 40, height = 10, density = Density(1f)) {
        CompositionLocalProvider(LocalRcAnimationClock provides { seconds.floatValue }) {
          RcComposePlayer(document)
        }
      }
    try {
      val atTwentyFive = scene.pixels()
      assertEquals(red, atTwentyFive.getColor(24, 5))
      assertEquals(0, atTwentyFive.getColor(26, 5))

      seconds.floatValue = 12f
      // Written outside composition: deliver it now, or the next frame may not see it.
      Snapshot.sendApplyNotifications()
      val atTwelve = scene.pixels()
      assertEquals(red, atTwelve.getColor(11, 5))
      assertEquals(0, atTwelve.getColor(13, 5))
    } finally {
      scene.close()
    }
  }

  private fun ImageComposeScene.pixels(): Bitmap {
    val bitmap = Bitmap().apply { allocN32Pixels(40, 10) }
    check(render().readPixels(bitmap))
    return bitmap
  }

  private companion object {
    const val NAN_REFERENCE = 0x7fc00000
  }
}

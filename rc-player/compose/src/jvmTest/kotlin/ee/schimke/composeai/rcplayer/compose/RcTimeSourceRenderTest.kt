package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcTimeSnapshot
import ee.schimke.composeai.rcplayer.runtime.RcTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

class RcTimeSourceRenderTest {
  @Test
  fun aHostClockDrivesANamedCalendarField() {
    // A bar as wide as the day of the month, which the document names `day` — the shape a watch
    // face's date complication takes. The host's clock says the 19th, so the bar ends at x = 19.
    //
    // Two things have to hold for that: `LocalRcTimeSource` has to reach the player's state, and
    // naming the system variable must not stop the player loading it. Before the second was fixed
    // the name counted as the document claiming the id, the day read 0, and nothing was drawn.
    val red = 0xffff0000.toInt()
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 10, modern = false),
        listOf(
          RcNamedVariable(RcSystemVariables.DAY_OF_MONTH, RcNamedVariable.FLOAT_TYPE, "day"),
          RcPaintData(listOf(4, red)),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord(NAN_REFERENCE or RcSystemVariables.DAY_OF_MONTH),
            RcFloatWord.literal(10f),
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = 40, height = 10, density = Density(1f)) {
        CompositionLocalProvider(LocalRcTimeSource provides NineteenthOfTheMonth) {
          RcComposePlayer(document)
        }
      }
    try {
      val image = scene.render()
      val bitmap = Bitmap().apply { allocN32Pixels(40, 10) }
      check(image.readPixels(bitmap))

      assertEquals(red, bitmap.getColor(2, 5))
      assertEquals(red, bitmap.getColor(18, 5))
      assertEquals(0, bitmap.getColor(20, 5))
    } finally {
      scene.close()
    }
  }

  private object NineteenthOfTheMonth : RcTimeSource {
    // 2026-08-19T16:30:45Z.
    override fun currentTimeMillis(): Long = 1_787_157_045_000L

    override fun snapshot(epochMillis: Long): RcTimeSnapshot =
      RcTimeSnapshot(
        epochMillis = epochMillis,
        year = 2026,
        month = 8,
        dayOfMonth = 19,
        dayOfYear = 231,
        hour = 16,
        minute = 30,
        second = 45,
        isoDayOfWeek = 3,
      )
  }

  private companion object {
    const val NAN_REFERENCE = 0x7fc00000
  }
}

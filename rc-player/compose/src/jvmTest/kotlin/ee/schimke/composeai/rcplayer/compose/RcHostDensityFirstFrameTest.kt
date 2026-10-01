package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A document captured against `RemoteDensity.Host` must resolve against the host's density on the
 * **first** frame, not against the player's 1.0 placeholder.
 *
 * Such a document carries no pixel sizes: every `dp` and `sp` is an expression over the player's
 * `DENSITY` and `FONT_SIZE` variables. [RcComposePlayer] used to hand the host's `LocalDensity` to
 * the state from a `SideEffect`, which runs after the frame it belongs to has been laid out — so
 * the first frame resolved those variables at density 1.0 and nothing invalidated the layout when
 * the real density arrived. A still taken on that frame drew every deferred size at `1 / density`:
 * the wear-m3-catalog `TitleCardRemote` at density 2.0 came out with half-size text.
 *
 * The same string is drawn twice at density 2.0: once with its font size written as a literal `14 x
 * 2 = 28px`, the way a `fixed` capture folds it, and once as a reference to `FONT_SIZE` — `14 x
 * density x fontScale` — the way a `host` capture defers it. Both must draw the same glyphs.
 */
class RcHostDensityFirstFrameTest {

  @Test
  fun aFontSizeReadFromTheHostResolvesAtTheHostDensityOnTheFirstFrame() {
    val folded = inkWidth(document(RcFloatWord.literal(DEFAULT_FONT_SIZE_SP * DENSITY)))
    val deferred = inkWidth(document(RcFloatWord(NAN_REFERENCE or RcSystemVariables.FONT_SIZE)))
    assertTrue(folded > 0, "the literal font size drew no ink")
    assertEquals(
      folded.toFloat(),
      deferred.toFloat(),
      TOLERANCE_PX,
      "a FONT_SIZE reference drew ${deferred}px wide against ${folded}px for the same size " +
        "folded in; half the width is the first frame resolving the host's variables at density 1",
    )
  }

  private fun inkWidth(document: RcDocument): Int {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(DENSITY)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      var minX = WIDTH
      var maxX = -1
      for (y in 0 until HEIGHT) {
        for (x in 0 until WIDTH) {
          if (bitmap.getColor(x, y) != 0) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
          }
        }
      }
      return if (maxX < minX) 0 else maxX - minX + 1
    } finally {
      scene.close()
    }
  }

  private fun document(fontSize: RcFloatWord): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcTextData(42, TEXT),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcTextLayout(
          componentId = 3,
          animationId = 30,
          textId = 42,
          color = 0xff101828.toInt(),
          fontSize = fontSize,
          fontStyle = 0,
          fontWeight = RcFloatWord.literal(400f),
          fontFamilyId = -1,
          textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
          overflow = RcTextLayout.OVERFLOW_CLIP,
          maxLines = 1,
        ),
        RcLayoutContent(4),
      ) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  private companion object {
    const val WIDTH = 900
    const val HEIGHT = 200
    const val DENSITY = 2f
    /** The `sp` the players publish `FONT_SIZE` from, before density and font scale. */
    const val DEFAULT_FONT_SIZE_SP = 14f
    const val NAN_REFERENCE = 0x7fc00000
    const val TEXT = "Remote Compose"
    /** An ink edge can move a pixel between identical layouts; the defect is a factor of two. */
    const val TOLERANCE_PX = 1f
  }
}

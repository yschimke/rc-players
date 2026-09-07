package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Letter spacing (text style property 12) is a multiple of the font size, not a pixel length.
 *
 * The wire value is what `android.graphics.Paint.setLetterSpacing` takes — ems — and the vendored
 * AndroidX player spells it `data.letterSpacing.em`. This player read it as a pixel length, which
 * collapsed it to nothing: the published `remote-m3` body style asks for `0.02857` em, and
 * `0.02857.toSp()` at density 2 is `0.014` sp.
 *
 * The cost was not a slightly tight line. Text measured ~5% narrow, which is invisible on one line
 * and **re-breaks every paragraph that wraps** — the `remote-m3` cards wrapped their content a word
 * later than both Android players on the same document and the same face, and the cards then came
 * out shorter, which read as a layout divergence rather than a text one.
 *
 * Asserted as the *growth* between spacings rather than against an absolute width, so the test says
 * nothing about which face the host resolved and cannot drift with it. Under a pixel reading all
 * these runs are the same width to within a pixel; under an em reading they separate by the spacing
 * times the font size times the gap count.
 */
class RcLetterSpacingRenderTest {

  @Test
  fun letterSpacingIsAnEmMultipleOfTheFontSize() {
    val none = inkWidth(0f)
    val catalog = inkWidth(CATALOG_EM)
    val wide = inkWidth(WIDE_EM)

    assertTrue(none > 0, "the unspaced run drew nothing")

    // `remote-m3`'s own body spacing. 13 gaps at 0.02857 em of a 40px font is ~15px, and 15px is
    // exactly what this player was measuring short against the View and embedded players.
    val expectedCatalog = GAPS * CATALOG_EM * FONT_SIZE
    assertTrue(
      catalog - none > expectedCatalog * 0.5f,
      "$CATALOG_EM em widened the run by ${catalog - none}px, expected about ${expectedCatalog}px " +
        "— a pixel reading of an em value gives ~0px (none=$none, catalog=$catalog)",
    )

    // And the widening tracks the value: the growth is linear in the spacing, which a clamp or a
    // one-off constant would not reproduce.
    val ratio = (wide - none).toFloat() / (catalog - none).toFloat()
    val expectedRatio = WIDE_EM / CATALOG_EM
    assertTrue(
      abs(ratio - expectedRatio) < expectedRatio * 0.25f,
      "growth was not linear in the spacing: ${WIDE_EM} em grew ${wide - none}px against " +
        "${CATALOG_EM} em's ${catalog - none}px (ratio $ratio, expected about $expectedRatio)",
    )
  }

  /** Width of the drawn run in pixels, measured from the ink rather than from a layout report. */
  private fun inkWidth(letterSpacingEm: Float): Int {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(2f)) {
        RcComposePlayer(document(letterSpacingEm))
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

  /** One text run at [letterSpacingEm], with nothing else in the document to measure. */
  private fun document(letterSpacingEm: Float): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcRootLayout(-2),
        RcBoxLayout(-3, -1, 1, 2),
        RcLayoutContent(-4),
        RcTextData(42, TEXT),
        RcCoreText(
          textId = 42,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(3, 0xff101828.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(FONT_SIZE)),
              RcTextStyleProperty.FloatValue(12, RcFloatWord.literal(letterSpacingEm)),
            ),
        ),
        RcLayoutContent(-5),
      ) + List(5) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  private companion object {
    const val WIDTH = 900
    const val HEIGHT = 160
    const val FONT_SIZE = 40f

    /** The spacing the published `remote-m3` body style actually asks for. */
    const val CATALOG_EM = 0.02857f
    const val WIDE_EM = 0.2f
    const val TEXT = "Remote Compose"
    const val GAPS = TEXT.length - 1
  }
}

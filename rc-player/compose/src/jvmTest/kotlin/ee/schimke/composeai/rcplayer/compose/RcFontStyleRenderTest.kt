package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface

/**
 * A text's wire font style is an enum, not an Android `Typeface` style mask: AndroidX `remote-core`
 * draws both `CoreText` and `TextLayout` with `setTextStyle(type, weight, fontStyle == 1)`, and
 * `RemoteFontStyle.Italic` writes 1. So 1 is italic at the document's own weight, and every other
 * value is upright.
 *
 * This player used to read the value as a mask — bit 1 forced weight 700, bit 2 italic — so an
 * italic `RemoteText` drew bold and upright, and a style of 2 drew italic where AndroidX does not.
 */
class RcFontStyleRenderTest {

  @Test
  fun styleOneIsItalicAtTheDocumentsWeightOnBothOperations() {
    for ((name, document) in operations) {
      val upright = render(document(0))
      val italic = render(document(1))
      assertFalse(
        upright.pixels.contentEquals(italic.pixels),
        "$name drew style 1 exactly as upright; it is italic",
      )
      // Bold widens a run by several pixels at 40px; a synthesized slant moves its edges by a few
      // at most, so the run stays within a slant's reach of the upright width rather than growing.
      assertEquals(
        upright.inkWidth.toFloat(),
        italic.inkWidth.toFloat(),
        SLANT_TOLERANCE_PX,
        "$name drew style 1 ${italic.inkWidth}px wide against ${upright.inkWidth}px upright; " +
          "it must not be bold",
      )
    }
  }

  @Test
  fun anyOtherStyleIsUprightOnBothOperations() {
    for ((name, document) in operations) {
      val upright = render(document(0)).pixels
      for (style in listOf(2, 3)) {
        assertContentEquals(upright, render(document(style)).pixels, "$name style $style")
      }
    }
  }

  /**
   * Before/after evidence for `renders/cmp-text-font-style/`, written only when
   * `RC_LAYOUT_EVIDENCE_DIR` is set: per operation, the run at wire styles 0, 1 and 2, top to
   * bottom, on white.
   */
  @Test
  fun writeFontStyleEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    for ((name, document) in operations) {
      val surface = Surface.makeRasterN32Premul(WIDTH, HEIGHT * EVIDENCE_STYLES.size)
      surface.canvas.clear(Color.WHITE)
      EVIDENCE_STYLES.forEachIndexed { row, style ->
        val scene =
          ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(DENSITY)) {
            RcComposePlayer(document(style))
          }
        try {
          surface.canvas.drawImage(scene.render(0L), 0f, (row * HEIGHT).toFloat())
        } finally {
          scene.close()
        }
      }
      directory
        .resolve("${name.lowercase()}-styles-0-1-2.png")
        .writeBytes(surface.makeImageSnapshot().encodeToData()!!.bytes)
    }
  }

  private val operations: List<Pair<String, (Int) -> RcDocument>> =
    listOf("CoreText" to ::coreTextDocument, "TextLayout" to ::textLayoutDocument)

  private class Rendered(val pixels: IntArray, val inkWidth: Int)

  private fun render(document: RcDocument): Rendered {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(DENSITY)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      val pixels = IntArray(WIDTH * HEIGHT)
      var minX = WIDTH
      var maxX = -1
      for (y in 0 until HEIGHT) {
        for (x in 0 until WIDTH) {
          val color = bitmap.getColor(x, y)
          pixels[y * WIDTH + x] = color
          if (color != 0) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
          }
        }
      }
      return Rendered(pixels, if (maxX < minX) 0 else maxX - minX + 1)
    } finally {
      scene.close()
    }
  }

  /** One `CoreText` run. Property 5 is the font size, 6 the font style. */
  private fun coreTextDocument(style: Int): RcDocument =
    RcDocument(
      header(),
      listOf(
        RcRootLayout(-2),
        RcLayoutContent(-3),
        RcTextData(42, TEXT),
        RcCoreText(
          textId = 42,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(3, 0xff101828.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(FONT_SIZE)),
              RcTextStyleProperty.IntValue(6, style),
            ),
        ),
        RcLayoutContent(-4),
      ) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  /** The same run through `TextLayout`. */
  private fun textLayoutDocument(style: Int): RcDocument =
    RcDocument(
      header(),
      listOf(
        RcTextData(42, TEXT),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcTextLayout(
          componentId = 3,
          animationId = 30,
          textId = 42,
          color = 0xff101828.toInt(),
          fontSize = RcFloatWord.literal(FONT_SIZE),
          fontStyle = style,
          fontWeight = RcFloatWord.literal(400f),
          fontFamilyId = -1,
          textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
          overflow = RcTextLayout.OVERFLOW_CLIP,
          maxLines = 1,
        ),
        RcLayoutContent(4),
      ) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  private fun header() =
    RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false)

  private companion object {
    const val WIDTH = 900
    const val HEIGHT = 200
    const val DENSITY = 2f
    const val FONT_SIZE = 40f
    const val TEXT = "Remote Compose"
    val EVIDENCE_STYLES = listOf(0, 1, 2)

    /** How far a synthesized slant can move a run's ink edges at [FONT_SIZE]. */
    const val SLANT_TOLERANCE_PX = 8f
  }
}

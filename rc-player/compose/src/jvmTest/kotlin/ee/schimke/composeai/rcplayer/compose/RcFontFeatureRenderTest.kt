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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * OpenType layout features, end to end. `RemoteTextStyle` writes a text's `fontFeatureSettings`
 * into the same `CoreText` tag/value list as its variation axes (properties 20/21), so a document
 * asking for `frac` carries it exactly as it would carry `wdth`. Handed to the font as an axis it
 * was silently ignored; the player now applies it as the style's `fontFeatureSettings`.
 *
 * `frac` on Inter is the test because it cannot be imitated: it turns `1/2` into one fraction
 * glyph, so a frame that matches the plain one means the feature never reached the shaper.
 */
class RcFontFeatureRenderTest {

  @Test
  fun `a feature in the axis list reshapes the text`() {
    val plain = draw(INTER, "1/2 3/4", emptyList())
    val fractions = draw(INTER, "1/2 3/4", listOf("frac" to 1f))
    assertTrue(plain.any { it != 0 }, "the plain line drew nothing at all")
    assertNotEquals(plain, fractions, "frac drew the same frame — the feature was dropped")
  }

  @Test
  fun `a feature beside an axis leaves the axis applied`() {
    val narrow = inkWidth(draw(ROBOTO_FLEX, "HHHHHHHH", listOf("wdth" to 25f, "pnum" to 1f)))
    val wide = inkWidth(draw(ROBOTO_FLEX, "HHHHHHHH", listOf("wdth" to 151f, "pnum" to 1f)))
    assertTrue(wide > narrow, "wdth stopped applying once a feature joined it ($narrow, $wide)")
  }

  /**
   * One text run in [family] carrying [settings] as a captured `RemoteText` carries them: tags as
   * text ids in property 20, values as floats in property 21.
   */
  private fun draw(family: Face, text: String, settings: List<Pair<String, Float>>): List<Int> {
    val tagIds = settings.indices.map { 100 + it }
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
        listOf(
          RcRootLayout(-2),
          RcBoxLayout(-3, -1, 2, 2),
          RcLayoutContent(-4),
          RcTextData(42, text),
          RcTextData(43, "google:${family.name}"),
        ) +
          settings.mapIndexed { index, (tag, _) -> RcTextData(tagIds[index], tag) } +
          RcCoreText(
            textId = 42,
            properties =
              listOf(
                RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
                RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(40f)),
                RcTextStyleProperty.IntValue(8, 43),
              ) +
                if (settings.isEmpty()) emptyList()
                else
                  listOf(
                    RcTextStyleProperty.IntArrayValue(20, tagIds),
                    RcTextStyleProperty.FloatArrayValue(
                      21,
                      settings.map { RcFloatWord.literal(it.second) },
                    ),
                  ),
          ) +
          RcLayoutContent(-5) +
          List(5) { RcNoArg(RcOpcodes.CONTAINER_END) },
      )
    val bytes = File(FONTS, family.file).readBytes()
    val fonts = mapOf(family.name.lowercase() to RcFontFaces(RcFontFace(family.file, bytes)))
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document, typefaces = RcBundledTypefaceLoader(fonts))
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      return (0 until HEIGHT).flatMap { y -> (0 until WIDTH).map { x -> bitmap.getColor(x, y) } }
    } finally {
      scene.close()
    }
  }

  /** Columns containing any ink — the run's set width. */
  private fun inkWidth(pixels: List<Int>): Int =
    (0 until WIDTH).count { x -> (0 until HEIGHT).any { y -> pixels[y * WIDTH + x] != 0 } }

  private class Face(val name: String, val file: String)

  private companion object {
    const val WIDTH = 600
    const val HEIGHT = 80

    /** The wasm catalog's vendored faces, as `RcFontAxisRenderTest` reads them. */
    const val FONTS = "../../rc-player/wasm/dist-assets/fonts"

    val INTER = Face("Inter", "inter-400.ttf")
    val ROBOTO_FLEX = Face("Roboto Flex", "RobotoFlex.ttf")
  }
}

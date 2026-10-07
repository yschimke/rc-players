package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Canvas text (`DrawTextRun`) with a paint that names a host family and sets a font axis, the way
 * `RcPaint.setTypeface(String)` and `RcPaint.setAxis` write them: the family and the axis tag are
 * both text ids, and the axis value may be a document float.
 */
class RcPaintFontAxisRenderTest {

  @Test
  fun `a paint wdth axis changes the drawn face`() {
    val fonts = robotoFlexFonts()
    val narrow = inkWidth(document(RcFloatWord.literal(25f)), fonts)
    val wide = inkWidth(document(RcFloatWord.literal(151f)), fonts)

    assertTrue(narrow > 0, "the wdth 25 run drew nothing at all")
    assertTrue(
      wide > narrow,
      "wdth 151 must set wider than wdth 25 — same ink means the paint axis was dropped " +
        "(narrow=$narrow, wide=$wide)",
    )
  }

  @Test
  fun `a paint axis bound to a named float follows the float`() {
    val fonts = robotoFlexFonts()
    val doc = document(RcFloatWord(0x7fc00000 or WDTH_FLOAT_ID))
    val narrow = inkWidth(doc, fonts, mapOf("USER:wdth" to RcNamedValue.FloatValue(25f)))
    val wide = inkWidth(doc, fonts, mapOf("USER:wdth" to RcNamedValue.FloatValue(151f)))

    assertTrue(wide > narrow, "the axis did not follow its float (narrow=$narrow, wide=$wide)")
  }

  @Test
  fun `an animated axis keeps a bounded number of face instances`() {
    val faces = robotoFlexFonts().getValue("roboto flex")
    repeat(RcFontFaces.MAX_INSTANCES * 3) { i ->
      assertNotNull(faces.family(RcFontVariations(listOf(RcFontAxis("wdth", 25f + i)))))
    }
    assertEquals(RcFontFaces.MAX_INSTANCES, faces.cachedInstanceCount)
  }

  private fun document(wdth: RcFloatWord): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcTextData(42, "HHHHHHHH"),
        RcTextData(43, "Roboto Flex"),
        RcTextData(44, "wdth"),
        RcFloatConstant(WDTH_FLOAT_ID, RcFloatWord.literal(100f)),
        RcNamedVariable(WDTH_FLOAT_ID, RcNamedVariable.FLOAT_TYPE, "USER:wdth"),
        RcPaintData(
          listOf(
            PAINT_COLOR,
            0xff000000.toInt(),
            PAINT_TEXT_SIZE,
            RcFloatWord.literal(40f).bits,
            PAINT_TYPEFACE or (400 shl 16),
            43,
            PAINT_FONT_AXIS or (1 shl 16),
            44,
            wdth.bits,
          )
        ),
        RcDrawText(
          42,
          0,
          8,
          0,
          8,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(60f),
          false,
        ),
      ),
    )

  private fun inkWidth(
    document: RcDocument,
    fonts: Map<String, RcFontFaces>,
    named: Map<String, RcNamedValue> = emptyMap(),
  ): Int {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(
          document,
          namedValues = mutableStateMapOf<String, RcNamedValue>().apply { putAll(named) },
          typefaces = RcBundledTypefaceLoader(fonts),
        )
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      return (0 until WIDTH).count { x -> (0 until HEIGHT).any { y -> bitmap.getColor(x, y) != 0 } }
    } finally {
      scene.close()
    }
  }

  private fun robotoFlexFonts(): Map<String, RcFontFaces> {
    val face = File(VARIABLE_FACE_PATH).takeIf { it.isFile }?.readBytes()
    assertNotNull(face, "vendored Roboto Flex not found at $VARIABLE_FACE_PATH")
    return mapOf("roboto flex" to RcFontFaces(RcFontFace("RobotoFlex.ttf", face)))
  }

  private companion object {
    const val WIDTH = 600
    const val HEIGHT = 80
    const val WDTH_FLOAT_ID = 50
    const val PAINT_TEXT_SIZE = 1
    const val PAINT_COLOR = 4
    const val PAINT_TYPEFACE = 16
    const val PAINT_FONT_AXIS = 23

    /** As in [RcFontAxisRenderTest]: the wasm lane's vendored face, read from the repository. */
    const val VARIABLE_FACE_PATH = "../../rc-player/wasm/dist-assets/fonts/RobotoFlex.ttf"
  }
}

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.FloatValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.IntValue
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcRasterizedText
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import ee.schimke.composeai.rcplayer.runtime.RcTextRasterizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcTextImagesTest {
  private val asked = mutableListOf<RcStyledText>()

  /** Draws every text as a 10px-per-character, 20px-high image, remembering what it was asked. */
  private val rasterizer = RcTextRasterizer { text ->
    asked += text
    RcRasterizedText(byteArrayOf(1, 2, 3), text.text.length * 10, 20)
  }

  private fun f(value: Float) = RcFloatWord.literal(value)

  private fun text(vararg properties: RcTextStyleProperty) =
    listOf(
      RcCoreText(42, listOf(IntValue(1, -7), FloatValue(5, f(28f)), *properties)),
      RcPaddingModifier(f(4f), f(0f), f(4f), f(0f)),
      RcLayoutContent(-8),
      END,
      END,
    )

  private fun screen(vararg before: RcOperation, text: List<RcOperation>): RcDocument =
    document(RcTextData(42, "Hello"), *before, *rootLayout(*text.toTypedArray()).toTypedArray())

  private val spaced = screen(text = text(FloatValue(12, f(0.1f))))

  @Test
  fun aSpacedTextBecomesAnImageInABoxThatKeepsItsIdAndModifiers() {
    val result = spaced.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer, RcDisplay(2f))
    val operations = assertRoundTrips(result.document).operations

    assertTrue(operations.none { it is RcCoreText })
    val box = operations.filterIsInstance<RcBoxLayout>().single { it.componentId == -7 }
    val at = operations.indexOf(box)
    assertEquals(RcPaddingModifier(f(4f), f(0f), f(4f), f(0f)), operations[at + 1])
    assertEquals(
      RcAccessibilitySemantics(0, -1, 42, 0, 0, true, false),
      operations[at + 2],
      "the image is still read out as the text",
    )
    val bitmap = operations.filterIsInstance<RcBitmapData>().single()
    assertEquals(50 to 20, bitmap.width to bitmap.height)
    assertEquals(bitmap.imageId, operations.filterIsInstance<RcImageLayout>().single().bitmapId)
    assertEquals(
      RcTextImage(-7, 42, setOf("letterSpacing"), width = 25f, height = 10f),
      result.images.single(),
    )
    assertEquals(0.1f, asked.single().letterSpacing)
    assertEquals(28f, asked.single().fontSize)
  }

  @Test
  fun onWearTheImageIsSizedInDpSoItScalesWithTheDevice() {
    val operations =
      spaced
        .coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer, RcDisplay(2f))
        .document
        .operations
    val width = operations.filterIsInstance<RcWidthModifier>().single().value
    val expression =
      operations.filterIsInstance<RcFloatExpression>().single { it.id == width.referencedId }
    assertEquals(25f, expression.expression[0].value)
    assertEquals(RcSystemVariables.DENSITY, expression.expression[1].referencedId)
    assertTrue(
      spaced
        .coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer)
        .document
        .validateFor(RcTargetProfiles.WEAR_WIDGETS)
        .none { it.opcode == RcOpcodes.CORE_TEXT }
    )
  }

  @Test
  fun onAnAndroid16LauncherTheImageIsSizedInThatDisplaysPixels() {
    val operations =
      spaced
        .coreTextAsImages(RcTargetProfiles.WIDGETS_V6, rasterizer, RcDisplay(2f))
        .document
        .operations
    assertEquals(f(50f), operations.filterIsInstance<RcWidthModifier>().single().value)
  }

  @Test
  fun aTextTheOlderLayoutCanSayStaysText() {
    val plain = screen(text = text())
    val result = plain.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer)
    assertEquals(plain, result.document)
    assertTrue(asked.isEmpty())
  }

  @Test
  fun aTextAHostRecolorsIsAMaskTheLiveColourTints() {
    val themed =
      screen(
        RcColorConstant(60, 0xff00ff00.toInt()),
        RcNamedVariable(60, RcNamedVariable.COLOR_TYPE, "USER:accent"),
        text = text(FloatValue(12, f(0.1f)), IntValue(4, 60)),
      )
    val operations =
      assertRoundTrips(themed.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer).document)
        .operations
    assertEquals(0xffffffff.toInt(), asked.single().color, "drawn as a white mask")
    assertTrue(RcNamedVariable(60, RcNamedVariable.COLOR_TYPE, "USER:accent") in operations)
    assertTrue(operations.any { it is RcCanvasLayout })
    assertEquals(
      RcPaintData(listOf(20 or (5 shl 16), 60)),
      operations.filterIsInstance<RcPaintData>().single(),
    )
    val draw = operations.filterIsInstance<RcDrawBitmap>().single()
    assertEquals(operations.filterIsInstance<RcBitmapData>().single().imageId, draw.imageId)
  }

  @Test
  fun aTextAHostCanRewriteIsLeftAndReported() {
    val named =
      document(
        RcTextData(42, "Hello"),
        RcNamedVariable(42, RcNamedVariable.STRING_TYPE, "USER:greeting"),
        *rootLayout(*text(FloatValue(12, f(0.1f))).toTypedArray()).toTypedArray(),
      )
    val result = named.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer)
    assertEquals(named, result.document)
    assertEquals(listOf(-7), result.skipped)
  }

  @Test
  fun aTextTheRasterizerDeclinesIsLeftAndReported() {
    val result = spaced.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, { null })
    assertEquals(spaced, result.document)
    assertEquals(listOf(-7), result.skipped)
  }

  @Test
  fun aTextWrapsAtTheWidthGivenForItOrElseTheDocuments() {
    spaced.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer, RcDisplay(2f)) { 100f }
    spaced.coreTextAsImages(RcTargetProfiles.WEAR_WIDGETS, rasterizer, RcDisplay(2f))
    assertEquals(listOf(200f, 256f * 2f), asked.map { it.maxWidth })
  }

  @Test
  fun aTargetWithCoreTextKeepsIt() {
    assertEquals(spaced, spaced.coreTextAsImages(RcTargetProfiles.ANDROIDX, rasterizer).document)
  }
}

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRunOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.BooleanValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.FloatValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.IntValue
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcRasterizedText
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import ee.schimke.composeai.rcplayer.runtime.RcTextRasterizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcBitmapFontsTest {
  private val asked = mutableListOf<RcStyledText>()

  /**
   * Draws every glyph 10px wide and 20px high at 28px, to scale at the larger size advances are
   * measured at, remembering what it was asked at the text's own size.
   */
  private val rasterizer = RcTextRasterizer { text ->
    val scale = text.fontSize / 28f
    if (scale == 1f) asked += text
    RcRasterizedText(byteArrayOf(1, 2, 3), (text.text.length * 10 * scale).toInt(), 20)
  }

  private fun f(value: Float) = RcFloatWord.literal(value)

  /** A text in the family named by text id 50, with [componentId] and [textId]. */
  private fun text(textId: Int, componentId: Int, vararg properties: RcTextStyleProperty) =
    listOf(
      RcCoreText(
        textId,
        listOf(IntValue(1, componentId), FloatValue(5, f(28f)), IntValue(8, 50), *properties),
      ),
      RcPaddingModifier(f(4f), f(0f), f(4f), f(0f)),
      RcLayoutContent(componentId - 100),
      END,
      END,
    )

  private fun screen(vararg before: RcOperation, texts: List<RcOperation>): RcDocument =
    document(
      RcTextData(50, "digital7"),
      RcTextData(42, "12:30"),
      RcTextData(43, "21:03"),
      *before,
      *rootLayout(*texts.toTypedArray()).toTypedArray(),
    )

  private val clock = screen(texts = text(42, -7))

  private fun RcDocument.bitmapFonts(
    target: RcTargetProfile = RcTargetProfiles.WIDGETS_V6,
    families: Set<String> = setOf("Digital7"),
    glyphs: (Int) -> RcBitmapFontGlyphs? = { null },
  ) = coreTextAsBitmapFonts(target, families, rasterizer, RcDisplay(2f), glyphs = glyphs)

  @Test
  fun aTextInARequestedFamilyDrawsFromABitmapFontOfItsGlyphs() {
    val result = clock.bitmapFonts()
    val operations = assertRoundTrips(result.document).operations

    assertTrue(operations.none { it is RcCoreText })
    val box = operations.filterIsInstance<RcBoxLayout>().single { it.componentId == -7 }
    assertEquals(
      RcPaddingModifier(f(4f), f(0f), f(4f), f(0f)),
      operations[operations.indexOf(box) + 1],
      "the box keeps the text's modifiers",
    )
    val font = operations.filterIsInstance<RcBitmapFontData>().single()
    assertEquals(listOf("0", "1", "2", "3", ":"), font.glyphs.map { it.chars })
    assertEquals(5, operations.filterIsInstance<RcBitmapData>().size, "each glyph once")
    assertTrue(operations.indexOf(font) < operations.indexOf(box), "defined before it's drawn")
    assertEquals(
      RcDrawBitmapFontTextRun(42, font.fontId, 0, -1, f(0f), f(0f), f(0f)),
      operations.filterIsInstance<RcDrawBitmapFontTextRun>().single(),
    )
    assertEquals(
      RcWidthModifier(ee.schimke.composeai.rcplayer.protocol.RcDimensionType.EXACT, f(50f)),
      operations.filterIsInstance<RcWidthModifier>().single(),
      "five 10px glyphs, in the launcher's pixels",
    )
    assertEquals(
      RcBitmapFontText(-7, 42, font.fontId, width = 25f, height = 10f, dynamic = false),
      result.texts.single(),
    )
    assertTrue(asked.all { it.fontFamily == "digital7" && it.fontSize == 28f && it.maxLines == 1 })
    assertTrue(asked.all { it.color == 0xff000000.toInt() }, "a constant colour is drawn in")
    assertEquals(
      emptyList(),
      result.document.validateFor(RcTargetProfiles.WIDGETS_V6).filter { it.opcode != null },
    )
  }

  @Test
  fun aTextInAnotherFamilyIsLeftAlone() {
    val result = clock.bitmapFonts(families = setOf("gspot"))
    assertEquals(clock, result.document)
    assertEquals(emptyList(), result.skipped)
  }

  @Test
  fun textsInOneFaceShareAFontOfEveryGlyphEitherShows() {
    val two = screen(texts = text(42, -7) + text(43, -8))
    val result = two.bitmapFonts()
    val font = result.document.operations.filterIsInstance<RcBitmapFontData>().single()
    assertEquals(listOf("0", "1", "2", "3", ":"), font.glyphs.map { it.chars })
    assertEquals(listOf(font.fontId, font.fontId), result.texts.map { it.fontId })
  }

  @Test
  fun aTextTheHostChangesDrawsFromTheGlyphsItCanShow() {
    val live =
      screen(RcNamedVariable(42, RcNamedVariable.STRING_TYPE, "USER:time"), texts = text(42, -7))

    assertEquals(listOf(-7), live.bitmapFonts().skipped, "no glyphs given for it")

    val result = live.bitmapFonts { RcBitmapFontGlyphs("0123456789:", maxLength = 5) }
    val operations = assertRoundTrips(result.document).operations
    assertTrue(RcNamedVariable(42, RcNamedVariable.STRING_TYPE, "USER:time") in operations)
    assertEquals(11, operations.filterIsInstance<RcBitmapFontData>().single().glyphs.size)
    assertEquals(
      RcBitmapFontText(-7, 42, result.texts.single().fontId, 25f, 10f, dynamic = true),
      result.texts.single(),
    )
  }

  @Test
  fun aColourTheHostSetsTintsAWhiteMask() {
    val themed =
      screen(
        RcColorConstant(60, 0xff00ff00.toInt()),
        RcNamedVariable(60, RcNamedVariable.COLOR_TYPE, "USER:accent"),
        texts = text(42, -7, IntValue(4, 60)),
      )
    val operations = themed.bitmapFonts().document.operations
    assertTrue(asked.all { it.color == 0xffffffff.toInt() })
    assertEquals(
      RcPaintData(listOf(20 or (5 shl 16), 60)),
      operations.filterIsInstance<RcPaintData>().single(),
    )
  }

  @Test
  fun letterSpacingBecomesGlyphSpacing() {
    val spaced = screen(texts = text(42, -7, FloatValue(12, f(0.5f))))
    val draw =
      spaced.bitmapFonts().document.operations.filterIsInstance<RcDrawBitmapFontTextRun>().single()
    assertEquals(f(14f), draw.glyphSpacing, "half an em of a 28px text")
  }

  @Test
  fun styleABitmapFontCannotDrawLeavesTheTextAndIsReported() {
    val underlined = screen(texts = text(42, -7, BooleanValue(18, true)))
    val result = underlined.bitmapFonts()
    assertEquals(underlined, result.document)
    assertEquals(listOf(-7), result.skipped)
  }

  @Test
  fun aGlyphTheRasterizerDeclinesLeavesItsFaceAsText() {
    val result =
      clock.coreTextAsBitmapFonts(RcTargetProfiles.WIDGETS_V6, setOf("digital7"), { null })
    assertEquals(clock, result.document)
    assertEquals(listOf(-7), result.skipped)
  }

  @Test
  fun onWearTheGlyphsScaleWithTheDevice() {
    val operations = clock.bitmapFonts(target = WearWithoutCoreText).document.operations
    val scale =
      operations.filterIsInstance<RcDraw4>().single { it.opcode == RcOpcodes.MATRIX_SCALE }
    assertEquals(scale.first, scale.second)
    assertNull(scale.first.value.takeIf { scale.first.referencedId == null }, "a live expression")
    assertEquals(
      emptyList(),
      clock.bitmapFonts(target = WearWithoutCoreText).document.validateFor(WearWithoutCoreText),
    )
  }

  /** Draws each glyph 10px wide, with "AV" 4px tighter as a pair, its baseline 16px down. */
  private val kerned = RcTextRasterizer { text ->
    val scale = text.fontSize / 28f
    if (scale == 1f) asked += text
    val width = text.text.length * 10 - Regex("AV").findAll(text.text).count() * 4
    RcRasterizedText(byteArrayOf(1, 2, 3), (width * scale).toInt(), 20, baseline = 16f)
  }

  @Test
  fun aPairTheFaceSetsTighterIsKernedAndTheSlotSizedForIt() {
    val word =
      document(
        RcTextData(50, "digital7"),
        RcTextData(42, "AVA"),
        *rootLayout(*text(42, -7).toTypedArray()).toTypedArray(),
      )
    val result =
      word.coreTextAsBitmapFonts(
        RcTargetProfiles.WIDGETS_V6,
        setOf("digital7"),
        kerned,
        RcDisplay(2f),
      )
    val font = result.document.operations.filterIsInstance<RcBitmapFontData>().single()
    assertEquals(mapOf("AV" to (-4).toShort()), font.kerning, "VA isn't tighter, so isn't kerned")
    assertEquals(13f, result.texts.single().width, "30px less AV's 4, in dp")

    val loose =
      word.coreTextAsBitmapFonts(
        RcTargetProfiles.WIDGETS_V6,
        setOf("digital7"),
        kerned,
        RcDisplay(2f),
        kerning = false,
      )
    assertEquals(
      emptyMap(),
      loose.document.operations.filterIsInstance<RcBitmapFontData>().single().kerning,
    )
  }

  @Test
  fun aSequenceAskedForIsOneGlyphWhereATextShowsIt() {
    val word =
      document(
        RcTextData(50, "digital7"),
        RcTextData(42, "fifo"),
        *rootLayout(*text(42, -7).toTypedArray()).toTypedArray(),
      )
    val result =
      word.coreTextAsBitmapFonts(
        RcTargetProfiles.WIDGETS_V6,
        setOf("digital7"),
        rasterizer,
        sequences = setOf("fi", "xy"),
      )
    val glyphs = result.document.operations.filterIsInstance<RcBitmapFontData>().single().glyphs
    assertEquals(
      listOf("f", "fi", "o"),
      glyphs.map { it.chars },
      "no lone i, and no xy the text never shows",
    )
    assertEquals(20.toShort(), glyphs.single { it.chars == "fi" }.bitmapWidth)
  }

  private fun canvas(vararg draws: RcOperation): RcDocument =
    document(
      RcTextData(50, "digital7"),
      RcTextData(42, "12:30"),
      RcPathData(70, listOf(path(10), f(0f), f(0f), path(11), f(0f), f(0f), f(200f), f(0f))),
      *rootLayout(
          ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout(-9, -1),
          ee.schimke.composeai.rcplayer.protocol.RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
          *draws,
          END,
          END,
        )
        .toTypedArray(),
    )

  private fun path(command: Int) = RcFloatWord(0xff800000.toInt() or command)

  private val bezelPaint = RcPaintData(listOf(16, 50, 1, f(28f).bits, 4, 0xffff0000.toInt()))

  @Test
  fun textOnAPathDrawsAlongASectionOfItAsLongAsTheText() {
    val bezel = canvas(bezelPaint, RcDrawTextOnPath(42, 70, f(10f), f(5f)))
    val result = bezel.coreTextAsBitmapFonts(RcTargetProfiles.ANDROIDX, setOf("digital7"), kerned)
    val operations = assertRoundTrips(result.document).operations

    assertTrue(operations.none { it is RcDrawTextOnPath })
    val draw = operations.filterIsInstance<RcDrawBitmapFontTextRunOnPath>().single()
    val section = operations.filterIsInstance<RcPathData>().single { it.id == draw.pathId }
    assertEquals(listOf(10, 11), section.words.mapNotNull { it.referencedId })
    assertEquals(
      listOf(10f, 0f, 10f, 0f, 60f, 0f),
      section.words
        .filter { it.referencedId == null }
        .map { kotlin.math.round(it.value * 100) / 100 },
      "from the offset, five 10px glyphs along",
    )
    assertEquals(f(5f - 16f), draw.yAdjustment, "the baseline on the path, as the text had it")
    assertTrue(
      asked.all { it.fontSize == 28f && it.density == 1f && it.color == 0xffff0000.toInt() },
    )
    assertEquals(listOf(true), result.texts.map { it.onPath })
    assertEquals(
      emptyList(),
      result.document.validateFor(RcTargetProfiles.WEAR_WIDGETS).filter { it.opcode != null },
    )
  }

  @Test
  fun textOnAPathItCannotReadIsLeftAndReported() {
    val stroked =
      canvas(
        bezelPaint,
        RcPaintData(listOf(8 or (1 shl 16))),
        RcDrawTextOnPath(42, 70, f(10f), f(5f)),
      )
    assertEquals(
      listOf(42),
      stroked
        .coreTextAsBitmapFonts(RcTargetProfiles.ANDROIDX, setOf("digital7"), kerned)
        .skippedOnPath,
    )

    val tooLong = canvas(bezelPaint, RcDrawTextOnPath(42, 70, f(180f), f(5f)))
    val result = tooLong.coreTextAsBitmapFonts(RcTargetProfiles.ANDROIDX, setOf("digital7"), kerned)
    assertEquals(listOf(42), result.skippedOnPath, "50px of text from 180px along a 200px path")
    assertEquals(tooLong, result.document)

    val launcher = canvas(bezelPaint, RcDrawTextOnPath(42, 70, f(10f), f(5f)))
    assertEquals(
      listOf(42),
      launcher
        .coreTextAsBitmapFonts(RcTargetProfiles.WIDGETS_V6, setOf("digital7"), kerned)
        .skippedOnPath,
      "no path op at level 6",
    )
  }
}

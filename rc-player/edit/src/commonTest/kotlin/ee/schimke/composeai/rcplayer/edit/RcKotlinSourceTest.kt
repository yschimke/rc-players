package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What `toRemoteComposeKotlin` writes for hand-built documents. That the source it writes compiles
 * and captures back to the same document is checked by `rc-creation-bridge`'s round trip, against
 * the creation API itself.
 */
class RcKotlinSourceTest {
  private fun word(value: Float) = RcFloatWord.literal(value)

  private fun nanId(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun withBehavior(behavior: Int, document: RcDocument): RcDocument =
    document.copy(
      header =
        RcHeader(
          RcVersion(1, 1, 0),
          listOf(
            RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(100)),
            RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(100)),
            RcHeaderProperty(RcHeader.DOC_DENSITY_BEHAVIOR, RcHeaderValue.IntValue(behavior)),
          ),
        ),
    )

  private fun rootLayoutArray(vararg content: RcOperation): Array<RcOperation> =
    rootLayout(*content).toTypedArray()

  /** A box component with [modifiers], as AndroidX writes one: modifiers, then its content. */
  private fun modifiedBox(vararg modifiers: RcOperation): Array<RcOperation> =
    arrayOf(RcBoxLayout(-10, -1, 1, 4), *modifiers, RcLayoutContent(-11), END, END)

  @Test
  fun aBoxBecomesARemoteBoxWithItsModifierChain() {
    val source =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_DP,
          document(
            *rootLayoutArray(
              *modifiedBox(
                RcWidthModifier(RcDimensionType.EXACT_DP, word(24f)),
                RcHeightModifier(RcDimensionType.EXACT_DP, word(24f)),
                RcPaddingModifier(word(8f), word(8f), word(8f), word(8f)),
                RcBackgroundModifier(0, 0, 0, 0, word(1f), word(0f), word(0f), word(1f), 0),
              ),
            ),
          ),
        )
        .toRemoteComposeKotlin(RcKotlinOptions(packageName = "sample", functionName = "Swatch"))

    assertTrue(source.isComplete, source.unsupported.toString())
    assertContains(source.code, "package sample")
    assertContains(source.code, "fun Swatch() {")
    assertContains(
      source.code,
      "RemoteBox(modifier = RemoteModifier.size(24.rdp).padding(8.rdp)" +
        ".background(RemoteColor(Color(0xFFFF0000))))",
    )
    assertContains(source.code, "import androidx.compose.remote.creation.compose.layout.RemoteBox")
  }

  @Test
  fun aPixelDocumentReadsItsLengthsBackAtTheGivenDensity() {
    val padded =
      withBehavior(
        RcHeader.DENSITY_BEHAVIOR_LEGACY,
        document(
          *rootLayoutArray(
            *modifiedBox(RcPaddingModifier(word(16f), word(16f), word(16f), word(16f))),
          ),
        ),
      )
    assertContains(
      padded.toRemoteComposeKotlin(RcKotlinOptions(density = 2f)).code,
      "padding(8.rdp)",
    )
    // 16 / 41 does not multiply back to 16 in float, so the pixels are kept.
    assertContains(
      padded.toRemoteComposeKotlin(RcKotlinOptions(density = 41f)).code,
      "padding(16f.rf)",
    )
  }

  @Test
  fun namedValuesAreHoistedAndQuotedTextIsEscaped() {
    val source =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_DP,
          document(
            RcTextData(42, "Say \"hi\" for \$5\n"),
            RcColorConstant(43, 0xFF112233.toInt()),
            RcNamedVariable(43, RcNamedVariable.COLOR_TYPE, "USER:brand.primary"),
            *rootLayoutArray(
              RcCoreText(
                42,
                listOf(
                  RcTextStyleProperty.IntValue(4, 43),
                  RcTextStyleProperty.FloatValue(5, word(14f)),
                ),
              ),
              END,
            ),
          ),
        )
        .toRemoteComposeKotlin()

    assertTrue(source.isComplete, source.unsupported.toString())
    assertContains(
      source.code,
      "val brandPrimary = rememberNamedRemoteColor(\"brand.primary\", RemoteColor(Color(0xFF112233)))",
    )
    assertContains(
      source.code,
      "RemoteText(\"Say \\\"hi\\\" for \\\$5\\n\".rs, color = brandPrimary",
    )
  }

  @Test
  fun whatTheCreationApiCannotSayIsMarkedAndListed() {
    val source =
      document(
          RcFloatExpression(50, listOf(word(1f), word(2f)), null),
          *rootLayoutArray(
            RcCanvasLayout(-10, -1),
            RcWidthModifier(RcDimensionType.EXACT, nanId(50)),
            RcCanvasContent(-11),
            RcDraw4(RcOpcodes.DRAW_RECT, word(0f), word(0f), word(10f), word(10f)),
            RcDraw4(RcOpcodes.DRAW_RECT, word(0f), word(0f), word(5f), word(5f)),
            END,
            END,
          ),
        )
        .toRemoteComposeKotlin()

    assertEquals(
      listOf(
        "a value reads id 50 (FloatExpression); 0 is used instead",
        "canvas drawing is not generated: DrawRect x2",
      ),
      source.unsupported,
    )
    assertContains(source.code, "RemoteCanvas(modifier = RemoteModifier.width(0f.rf)) {")
    source.unsupported.forEach { assertContains(source.code, "// TODO(rc-to-kotlin): $it") }
    assertContains(source.code, "2 construct(s) could not be expressed")
  }

  @Test
  fun anUnbalancedDocumentSaysSoInsteadOfGuessing() {
    val source = document(*rootLayout().dropLast(1).toTypedArray()).toRemoteComposeKotlin()
    assertEquals(
      listOf("the document's containers do not balance; nothing was generated"),
      source.unsupported,
    )
  }

  @Test
  fun aRedefinedConstantReadsItsLastDefinitionAsThePlayerDoes() {
    val source =
      document(
          RcTextData(42, "first"),
          RcTextData(42, "second"),
          *rootLayoutArray(RcCoreText(42, emptyList()), END),
        )
        .toRemoteComposeKotlin()
    assertContains(source.code, "RemoteText(\"second\".rs")
  }

  @Test
  fun anOffsetIsAlwaysWrittenInDp() {
    val offset = RcOffsetModifier(word(16f), RcFloatWord(0x7fc00000 or 50))
    val source =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_LEGACY,
          document(
            RcFloatConstant(50, word(4f)),
            RcNamedVariable(50, RcNamedVariable.FLOAT_TYPE, "USER:nudge"),
            *rootLayoutArray(*modifiedBox(offset)),
          ),
        )
        .toRemoteComposeKotlin(RcKotlinOptions(density = 41f))
    // `offset` has no RemoteFloat overload: a pixel fallback and a variable both become dp.
    assertContains(source.code, "offset(0.3902439f.rdp, nudge.asRemoteDp())")
    assertEquals(1, source.unsupported.size, source.unsupported.toString())
  }

  @Test
  fun aBitmapTwoImagesShowIsDeclaredOnce() {
    val png = byteArrayOf(-119, 80, 78, 71)
    val source =
      document(
          RcBitmapData(56, 1, 1, RcBitmapData.TYPE_PNG_8888, RcBitmapData.ENCODING_INLINE, png),
          *rootLayoutArray(
            RcImageLayout(-10, -1, 56, 5, word(1f)),
            END,
            RcImageLayout(-12, -1, 56, 5, word(1f)),
            END,
          ),
        )
        .toRemoteComposeKotlin()
    assertEquals(1, Regex("private val Image56Png").findAll(source.code).count())
    assertEquals(2, Regex("RemoteImage\\(image56,").findAll(source.code).count())
  }

  @Test
  fun dimensionsKeepTheirTypeWhenTheyReadAVariableOrAreIntrinsic() {
    val size = RcFloatWord(0x7fc00000 or 50)
    val source =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_LEGACY,
          document(
            RcFloatConstant(50, word(24f)),
            RcNamedVariable(50, RcNamedVariable.FLOAT_TYPE, "USER:side"),
            *rootLayoutArray(
              *modifiedBox(
                RcWidthModifier(RcDimensionType.EXACT_DP, size),
                RcHeightModifier(RcDimensionType.INTRINSIC_MIN, word(0f)),
              ),
            ),
          ),
        )
        .toRemoteComposeKotlin()
    assertTrue(source.isComplete, source.unsupported.toString())
    assertContains(source.code, "width(side.asRemoteDp())")
    assertContains(source.code, "height(RemoteIntrinsicSize.Min)")
    assertContains(
      source.code,
      "import androidx.compose.remote.creation.compose.layout.RemoteIntrinsicSize",
    )
  }

  @Test
  fun aLargeImageIsSplitIntoLiteralsTheJvmCanHold() {
    // 120,000 bytes is 160,000 Base64 characters: past the JVM's 65,535-byte constant string.
    val png = ByteArray(120_000) { it.toByte() }
    val source =
      document(
          RcBitmapData(56, 1, 1, RcBitmapData.TYPE_PNG_8888, RcBitmapData.ENCODING_INLINE, png),
          *rootLayoutArray(RcImageLayout(-10, -1, 56, 5, word(1f)), END),
        )
        .toRemoteComposeKotlin()
    assertFalse(source.code.contains("const val Image56Png"))
    val literals =
      Regex("\"([A-Za-z0-9+/=]{1000,})\"").findAll(source.code).map { it.groupValues[1] }
    assertEquals(3, literals.count())
    assertTrue(literals.all { it.length <= 65_535 })
    assertContains(source.code, ").joinToString(\"\")")
  }

  @Test
  fun aVariableSizeBoundInAPixelsDocumentIsReported() {
    val bound = RcFloatWord(0x7fc00000 or 50)
    fun source(behavior: Int) =
      withBehavior(
          behavior,
          document(
            RcFloatConstant(50, word(40f)),
            RcNamedVariable(50, RcNamedVariable.FLOAT_TYPE, "USER:limit"),
            *rootLayoutArray(*modifiedBox(RcHeightInModifier(bound, word(Float.MAX_VALUE)))),
          ),
        )
        .toRemoteComposeKotlin()
    // Legacy and dp documents write a heightIn bound in dp, so a variable is exact there.
    assertTrue(source(RcHeader.DENSITY_BEHAVIOR_DP).isComplete)
    val pixels = source(RcHeader.DENSITY_BEHAVIOR_PIXELS)
    assertContains(pixels.code, "heightIn(min = limit.asRemoteDp())")
    assertEquals(1, pixels.unsupported.size, pixels.unsupported.toString())
  }

  @Test
  fun aVersionZeroBorderIsReportedBecauseTheCreationApiWritesVersionOne() {
    fun border(version: Int) =
      RcBorderModifier(
        0,
        0,
        version,
        0,
        word(2f),
        word(0f),
        word(0f),
        word(0f),
        word(0f),
        word(1f),
        0,
      )
    fun source(version: Int) =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_DP,
          document(*rootLayoutArray(*modifiedBox(border(version)))),
        )
        .toRemoteComposeKotlin()
    assertTrue(source(1).isComplete, source(1).unsupported.toString())
    val legacy = source(0)
    assertContains(legacy.code, "border(2.rdp, RemoteColor(Color(0xFF000000)))")
    assertEquals(1, legacy.unsupported.size, legacy.unsupported.toString())
  }

  @Test
  fun aComputedNamedValueIsReportedRatherThanWrittenAsAConstant() {
    val width = RcFloatWord(0x7fc00000 or 50)
    val source =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_DP,
          document(
            RcFloatExpression(50, listOf(word(10f), word(2f)), null),
            RcNamedVariable(50, RcNamedVariable.FLOAT_TYPE, "USER:computed"),
            *rootLayoutArray(*modifiedBox(RcWidthModifier(RcDimensionType.EXACT_DP, width))),
          ),
        )
        .toRemoteComposeKotlin()
    assertContains(source.code, "width(computed.asRemoteDp())")
    assertEquals(1, source.unsupported.size, source.unsupported.toString())
    assertContains(source.unsupported.single(), "computed by id 50 (FloatExpression)")
  }

  @Test
  fun spacingWithADistributedArrangementIsReported() {
    fun row(positioning: Int) =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_DP,
          document(
            *rootLayoutArray(
              ee.schimke.composeai.rcplayer.protocol.RcRowLayout(-10, -1, positioning, 4, word(4f)),
              RcLayoutContent(-11),
              END,
              END,
            ),
          ),
        )
        .toRemoteComposeKotlin()
    assertTrue(row(2).isComplete, row(2).unsupported.toString())
    assertContains(
      row(2).code,
      "RemoteArrangement.spacedBy(4.rdp, RemoteAlignment.CenterHorizontally)",
    )
    // SpaceBetween adds its gap to the spacing at playback; spacedBy cannot say both.
    assertEquals(1, row(6).unsupported.size, row(6).unsupported.toString())
  }

  @Test
  fun anOmittedFontSizeIsTheWireDefault() {
    val source =
      withBehavior(
          RcHeader.DENSITY_BEHAVIOR_LEGACY,
          document(RcTextData(42, "Hi"), *rootLayoutArray(RcCoreText(42, emptyList()), END)),
        )
        .toRemoteComposeKotlin(RcKotlinOptions(density = 2f))
    // The writer omits 36, AndroidX's default; at density 2 that is 18sp, not RemoteText's 12sp.
    assertContains(source.code, "fontSize = 18.rsp")
  }

  @Test
  fun onlyTheFontStyleTheCreationApiWritesIsGenerated() {
    fun source(style: Int) =
      document(
          RcTextData(42, "Hi"),
          *rootLayoutArray(RcCoreText(42, listOf(RcTextStyleProperty.IntValue(6, style))), END),
        )
        .toRemoteComposeKotlin()
    assertContains(source(1).code, "fontStyle = RemoteFontStyle.Italic")
    assertTrue(source(1).isComplete, source(1).unsupported.toString())
    // 2 and 3 are Typeface italic and bold italic; RemoteFontStyle.Italic would write 1.
    for (style in listOf(2, 3)) {
      assertFalse(source(style).code.contains("fontStyle = "), "style $style")
      assertEquals(1, source(style).unsupported.size, source(style).unsupported.toString())
    }
  }
}

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What `toUiBuilderDesign` writes for hand-built documents. */
class RcUiBuilderDesignTest {
  private fun word(value: Float) = RcFloatWord.literal(value)

  /** The JSON with whitespace removed, so assertions read as one line. */
  private fun compact(json: String) = json.replace(Regex("\\s+"), "")

  private fun root(vararg content: RcOperation): Array<RcOperation> =
    rootLayout(*content).toTypedArray()

  @Test
  fun aLayoutTreeBecomesCatalogNodesInTheirSlots() {
    val design =
      document(
          RcTextData(42, "Hello \"there\""),
          *root(
            RcColumnLayout(-10, -1, 2, 4, word(4f)),
            RcWidthModifier(RcDimensionType.FILL, word(1f)),
            RcHeightModifier(RcDimensionType.FILL, word(1f)),
            RcPaddingModifier(word(8f), word(8f), word(8f), word(8f)),
            RcBackgroundModifier(0, 0, 0, 0, word(1f), word(0f), word(0f), word(1f), 0),
            RcLayoutContent(-11),
            RcCoreText(
              42,
              listOf(
                RcTextStyleProperty.IntValue(3, 0xFF00FF00.toInt()),
                RcTextStyleProperty.FloatValue(5, word(14f)),
                RcTextStyleProperty.IntValue(9, 3),
              ),
            ),
            END,
            END,
            END,
          ),
        )
        .toUiBuilderDesign(RcUiBuilderOptions(designId = "hello", title = "Hello"))

    assertTrue(design.isComplete, design.unsupported.toString())
    val json = compact(design.json)
    assertContains(json, "\"schema\":\"compose-ui-builder-document/v1-candidate\"")
    assertContains(json, "\"id\":\"hello\"")
    assertContains(json, "\"systemId\":\"remote-m3\"")
    assertContains(json, "\"roots\":[\"column\"]")
    assertContains(json, "\"componentId\":\"layout/column\"")
    assertContains(json, "\"verticalSpacingDp\":{\"type\":\"float\",\"value\":4}")
    assertContains(json, "\"horizontalAlignment\":{\"type\":\"enum\",\"value\":\"center\"}")
    assertContains(json, "\"slots\":{\"children\":[\"text\"]}")
    assertContains(
      json,
      "\"modifiers\":[{\"type\":\"fillMaxSize\"},{\"type\":\"padding\",\"startDp\":8," +
        "\"topDp\":8,\"endDp\":8,\"bottomDp\":8},{\"type\":\"background\"," +
        "\"color\":{\"type\":\"color\",\"value\":\"#FF0000\"}}]",
    )
    assertContains(json, "\"componentId\":\"remote-m3/remote-text\"")
    assertContains(json, "\"text\":{\"type\":\"string\",\"value\":\"Hello\\\"there\\\"\"}")
    assertContains(json, "\"color\":{\"type\":\"string\",\"value\":\"#00FF00\"}")
    assertContains(json, "\"textAlign\":{\"type\":\"enum\",\"value\":\"center\"}")
  }

  @Test
  fun aThemeColourBecomesItsRoleToken() {
    val design =
      document(
          RcColorConstant(43, 0xFF1C1B1F.toInt()),
          RcNamedVariable(43, RcNamedVariable.COLOR_TYPE, "USER:WearM3.surfaceContainer"),
          *root(
            RcBoxLayout(-10, -1, 1, 4),
            RcBackgroundModifier(2, 43, 0, 0, word(0f), word(0f), word(0f), word(0f), 0),
            RcLayoutContent(-11),
            END,
            END,
          ),
        )
        .toUiBuilderDesign()

    assertTrue(design.isComplete, design.unsupported.toString())
    assertContains(
      compact(design.json),
      "\"color\":{\"type\":\"colorToken\",\"value\":\"surfaceContainer\"}",
    )
  }

  @Test
  fun aCanvasDrawsItsConstantPrimitivesInTheirPaint() {
    val design =
      document(
          *root(
            RcCanvasLayout(-10, -1),
            RcLayoutContent(-11),
            // Colour red, stroke style, stroke width 4px.
            RcPaintData(listOf(4, 0xFFFF0000.toInt(), 5, word(4f).bits, 8 or (1 shl 16))),
            RcDraw4(RcOpcodes.DRAW_RECT, word(0f), word(0f), word(20f), word(10f)),
            RcDraw3(RcOpcodes.DRAW_CIRCLE, word(10f), word(10f), word(6f)),
            RcDraw4(RcOpcodes.DRAW_LINE, word(0f), word(0f), RcFloatWord(0x7fc0002a), word(0f)),
            END,
            END,
          )
        )
        .toUiBuilderDesign(RcUiBuilderOptions(density = 2f))

    val json = compact(design.json)
    assertContains(json, "\"slots\":{\"ops\":[\"rect\",\"circle\"]}")
    assertContains(json, "\"widthDp\":{\"type\":\"float\",\"value\":10}")
    assertContains(json, "\"radiusDp\":{\"type\":\"float\",\"value\":3}")
    assertContains(json, "\"style\":{\"type\":\"enum\",\"value\":\"stroke\"}")
    assertContains(json, "\"strokeWidthDp\":{\"type\":\"float\",\"value\":2}")
    // The line's end is computed at playback; the gap says so rather than drawing it wrongly.
    assertEquals(listOf("canvas operations not generated: DrawLine"), design.unsupported)
  }

  @Test
  fun anEmbeddedImageIsHandedBackAsAnAsset() {
    val png = byteArrayOf(-119, 80, 78, 71)
    val design =
      document(
          RcBitmapData(56, 1, 1, RcBitmapData.TYPE_PNG_8888, RcBitmapData.ENCODING_INLINE, png),
          *root(RcImageLayout(-10, -1, 56, 5, word(1f)), END),
        )
        .toUiBuilderDesign()

    assertTrue(design.isComplete, design.unsupported.toString())
    assertContains(
      compact(design.json),
      "\"assetKey\":{\"type\":\"string\",\"value\":\"image-56\"}",
    )
    assertContains(compact(design.json), "\"contentScale\":{\"type\":\"enum\",\"value\":\"crop\"}")
    assertTrue(design.assets.getValue("image-56.png").contentEquals(png))
  }

  @Test
  fun aFillAndStrokePaintDrawsBothPasses() {
    val design =
      document(
          *root(
            RcCanvasLayout(-10, -1),
            RcLayoutContent(-11),
            // Style 2: fill and stroke.
            RcPaintData(listOf(4, 0xFF0000FF.toInt(), 5, word(2f).bits, 8 or (2 shl 16))),
            RcDraw3(RcOpcodes.DRAW_CIRCLE, word(10f), word(10f), word(6f)),
            END,
            END,
          )
        )
        .toUiBuilderDesign()

    assertTrue(design.isComplete, design.unsupported.toString())
    val json = compact(design.json)
    assertContains(json, "\"slots\":{\"ops\":[\"circle\",\"circle-2\"]}")
    assertEquals(
      1,
      Regex("\"style\":\\{\"type\":\"enum\",\"value\":\"fill\"\\}").findAll(json).count(),
    )
    assertEquals(
      1,
      Regex("\"style\":\\{\"type\":\"enum\",\"value\":\"stroke\"\\}").findAll(json).count(),
    )
  }

  @Test
  fun aTranslucentTextColourKeepsItsAlpha() {
    val design =
      document(
          RcTextData(42, "Faded"),
          *root(
            RcCoreText(
              42,
              listOf(
                RcTextStyleProperty.IntValue(3, 0x80000000.toInt()),
                RcTextStyleProperty.FloatValue(5, word(14f)),
              ),
            ),
            END,
          ),
        )
        .toUiBuilderDesign()
    // The builder accepts `#AARRGGBB` wherever it takes a colour.
    assertContains(compact(design.json), "\"color\":{\"type\":\"string\",\"value\":\"#80000000\"}")
    assertTrue(design.isComplete, design.unsupported.toString())
  }

  @Test
  fun aHostColourNamedLikeARoleKeepsItsDefault() {
    fun background(name: String) =
      document(
          RcColorConstant(43, 0xFF6200EE.toInt()),
          RcNamedVariable(43, RcNamedVariable.COLOR_TYPE, name),
          *root(
            RcBoxLayout(-10, -1, 1, 4),
            RcBackgroundModifier(2, 43, 0, 0, word(0f), word(0f), word(0f), word(0f), 0),
            RcLayoutContent(-11),
            END,
            END,
          ),
        )
        .toUiBuilderDesign()
    val brand = background("USER:brand.primary")
    assertContains(compact(brand.json), "\"color\":{\"type\":\"color\",\"value\":\"#6200EE\"}")
    assertEquals(1, brand.unsupported.size, brand.unsupported.toString())
    val theme = background("USER:WearM3.primary")
    assertContains(compact(theme.json), "\"color\":{\"type\":\"colorToken\",\"value\":\"primary\"}")
    assertTrue(theme.isComplete, theme.unsupported.toString())
  }

  @Test
  fun aVariableFontWeightWritesItsDefaultAsTheWeightAxisAndIsReported() {
    val design =
      document(
          RcTextData(42, "Bold"),
          RcFloatConstant(50, word(700f)),
          RcNamedVariable(50, RcNamedVariable.FLOAT_TYPE, "USER:weight"),
          *root(
            RcCoreText(
              42,
              listOf(RcTextStyleProperty.FloatValue(7, RcFloatWord(0x7fc00000 or 50))),
            ),
            END,
          ),
        )
        .toUiBuilderDesign()
    // remote-m3 declares no `fontWeight`; its `wght` axis overrides one.
    assertContains(
      compact(design.json),
      "\"fontVariationSettings\":{\"type\":\"string\",\"value\":\"\\\"wght\\\"700\"}",
    )
    assertFalse("fontWeight" in design.json, design.json)
    assertEquals(1, design.unsupported.size, design.unsupported.toString())
  }

  @Test
  fun aTextsFamilyAndSpacingAreWrittenInSp() {
    val design =
      document(
          RcTextData(42, "12:30"),
          RcTextData(43, "google:Michroma"),
          *root(
            RcCoreText(
              42,
              listOf(
                RcTextStyleProperty.FloatValue(5, word(20f)),
                RcTextStyleProperty.IntValue(8, 43),
                // Ratios of the font size, as the creation API writes them.
                RcTextStyleProperty.FloatValue(12, word(0.05f)),
                RcTextStyleProperty.FloatValue(14, word(1.5f)),
                RcTextStyleProperty.FloatValue(7, word(400f)),
              ),
            ),
            END,
          ),
        )
        .toUiBuilderDesign()
    val json = compact(design.json)
    assertContains(json, "\"fontFamily\":{\"type\":\"string\",\"value\":\"google:Michroma\"}")
    val size = Regex("\"fontSize\":\\{\"type\":\"float\",\"value\":([0-9.]+)").find(json)!!
    val sizeSp = size.groupValues[1].toFloat()
    assertContains(json, "\"letterSpacing\":{\"type\":\"float\",\"value\":")
    assertContains(json, "\"lineHeight\":{\"type\":\"float\",\"value\":")
    val lineHeight = Regex("\"lineHeight\":\\{\"type\":\"float\",\"value\":([0-9.]+)").find(json)!!
    assertEquals(sizeSp * 1.5f, lineHeight.groupValues[1].toFloat(), 0.001f)
    // A normal weight is left unsaid.
    assertFalse("wght" in json, json)
    assertTrue(design.isComplete, design.unsupported.toString())
  }

  @Test
  fun aRoundStrokeJoinIsReportedAndANamedImageLosesOnlyItsBinding() {
    val png = byteArrayOf(-119, 80, 78, 71)
    val design =
      document(
          RcBitmapData(56, 1, 1, RcBitmapData.TYPE_PNG_8888, RcBitmapData.ENCODING_INLINE, png),
          RcNamedVariable(56, RcNamedVariable.IMAGE_TYPE, "USER:hero"),
          *root(
            RcImageLayout(-12, -1, 56, 5, word(1f)),
            END,
            RcCanvasLayout(-10, -1),
            RcLayoutContent(-11),
            // Stroke style, round join (15 | 1 << 16).
            RcPaintData(listOf(8 or (1 shl 16), 15 or (1 shl 16))),
            RcDraw4(RcOpcodes.DRAW_RECT, word(0f), word(0f), word(20f), word(10f)),
            END,
            END,
          ),
        )
        .toUiBuilderDesign()
    assertContains(
      compact(design.json),
      "\"assetKey\":{\"type\":\"string\",\"value\":\"image-56\"}",
    )
    assertContains(compact(design.json), "\"slots\":{\"ops\":[\"rect\"]}")
    assertEquals(
      listOf(
        "named image \"USER:hero\" is written as its default asset",
        "stroke join round has no design property; miter is drawn",
      ),
      design.unsupported,
    )
  }

  @Test
  fun aMiterLimitIsReportedAndATextLayoutFontFamilyIsWritten() {
    val design =
      document(
          RcTextData(42, "Mono"),
          RcTextData(43, "monospace"),
          *root(
            ee.schimke.composeai.rcplayer.protocol.RcTextLayout(
              -12,
              -1,
              42,
              0xFF000000.toInt(),
              word(14f),
              0,
              word(400f),
              43,
              1,
              1,
              Int.MAX_VALUE,
            ),
            END,
            RcCanvasLayout(-10, -1),
            RcLayoutContent(-11),
            // Stroke style, miter limit 1.
            RcPaintData(listOf(8 or (1 shl 16), 6, word(1f).bits)),
            RcDraw4(RcOpcodes.DRAW_RECT, word(0f), word(0f), word(20f), word(10f)),
            END,
            END,
          ),
        )
        .toUiBuilderDesign()
    assertContains(
      compact(design.json),
      "\"fontFamily\":{\"type\":\"string\",\"value\":\"monospace\"}",
    )
    assertEquals(
      listOf("a stroke miter limit has no design property; 4 is drawn"),
      design.unsupported,
    )
  }

  @Test
  fun aThemeTextColourIsWrittenAsItsRoleAndAnUndeclaredRoleAsHex() {
    fun text(role: String) =
      document(
          RcTextData(42, "Hi"),
          RcColorConstant(43, 0xFF112233.toInt()),
          RcNamedVariable(43, RcNamedVariable.COLOR_TYPE, "USER:WearM3.$role"),
          *root(RcCoreText(42, listOf(RcTextStyleProperty.IntValue(4, 43))), END),
        )
        .toUiBuilderDesign()
    val onSurface = text("onSurface")
    assertContains(
      compact(onSurface.json),
      "\"color\":{\"type\":\"string\",\"value\":\"onSurface\"}",
    )
    assertTrue(onSurface.isComplete, onSurface.unsupported.toString())
    // remote-m3 declares no primaryDim token, and the builder refuses one it does not declare.
    val dim = text("primaryDim")
    assertContains(compact(dim.json), "\"value\":\"#112233\"")
    assertEquals(1, dim.unsupported.size, dim.unsupported.toString())
  }

  @Test
  fun sizeBoundsOffsetZIndexAlphaAndBorderBecomeDesignModifiers() {
    val design =
      withDp(
          document(
            *root(
              RcBoxLayout(-10, -1, 1, 4),
              RcWidthInModifier(word(40f), word(Float.MAX_VALUE)),
              RcHeightInModifier(word(0f), word(120f)),
              RcOffsetModifier(word(4f), word(-2f)),
              RcZIndexModifier(word(2f)),
              RcGraphicsLayerModifier(
                listOf(
                  RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, word(0.5f))
                )
              ),
              RcBorderModifier(
                0,
                0,
                1,
                0,
                word(1f),
                word(0f),
                word(1f),
                word(0f),
                word(0f),
                word(1f),
                0,
              ),
              RcLayoutContent(-11),
              END,
              END,
            )
          )
        )
        .toUiBuilderDesign()
    assertTrue(design.isComplete, design.unsupported.toString())
    assertContains(
      compact(design.json),
      "\"modifiers\":[{\"type\":\"widthIn\",\"minDp\":40},{\"type\":\"heightIn\",\"maxDp\":120}," +
        "{\"type\":\"offset\",\"xDp\":4,\"yDp\":-2},{\"type\":\"zIndex\",\"zIndex\":2}," +
        "{\"type\":\"alpha\",\"alpha\":0.5},{\"type\":\"border\",\"widthDp\":1," +
        "\"color\":{\"type\":\"color\",\"value\":\"#FF0000\"}}]",
    )
  }

  @Test
  fun semanticsWithOnlyAStateDescriptionAreStillReported() {
    val design =
      document(
          RcTextData(44, "On"),
          *root(
            RcBoxLayout(-10, -1, 1, 4),
            ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics(
              0,
              ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics.ROLE_UNKNOWN,
              0,
              44,
              0,
              true,
              false,
            ),
            RcLayoutContent(-11),
            END,
            END,
          ),
        )
        .toUiBuilderDesign()
    assertEquals(
      listOf("semantics have no design modifier; they are not generated"),
      design.unsupported,
    )
  }

  @Test
  fun anUnspecifiedSizeBoundIsLeftOut() {
    val design =
      document(
          *root(
            RcBoxLayout(-10, -1, 1, 4),
            RcWidthInModifier(word(-1f), word(80f)),
            RcHeightInModifier(word(24f), word(-1f)),
            RcLayoutContent(-11),
            END,
            END,
          )
        )
        .toUiBuilderDesign()
    assertContains(
      compact(design.json),
      "{\"type\":\"widthIn\",\"maxDp\":80},{\"type\":\"heightIn\",\"minDp\":24}",
    )
  }

  @Test
  fun aPre11LegacyBorderWidthIsAlreadyDp() {
    fun design(minor: Int) =
      document(
          *root(
            RcBoxLayout(-10, -1, 1, 4),
            RcBorderModifier(
              0,
              0,
              1,
              0,
              word(4f),
              word(0f),
              word(1f),
              word(0f),
              word(0f),
              word(1f),
              0,
            ),
            RcLayoutContent(-11),
            END,
            END,
          )
        )
        .let {
          it.copy(
            header =
              it.header.copy(
                version = ee.schimke.composeai.rcplayer.protocol.RcVersion(1, minor, 0)
              )
          )
        }
        .toUiBuilderDesign(RcUiBuilderOptions(density = 2f))
    // Version 1.0 legacy: dp, as written. 1.1 legacy: pixels, halved at density 2.
    assertContains(compact(design(0).json), "\"type\":\"border\",\"widthDp\":4")
    assertContains(compact(design(1).json), "\"type\":\"border\",\"widthDp\":2")
  }

  /** [document] with a dp header, so lengths read back as written. */
  private fun withDp(document: ee.schimke.composeai.rcplayer.protocol.RcDocument) =
    document.copy(
      header =
        ee.schimke.composeai.rcplayer.protocol.RcHeader(
          ee.schimke.composeai.rcplayer.protocol.RcVersion(1, 1, 0),
          listOf(
            ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty(
              ee.schimke.composeai.rcplayer.protocol.RcHeader.DOC_DENSITY_BEHAVIOR,
              ee.schimke.composeai.rcplayer.protocol.RcHeaderValue.IntValue(
                ee.schimke.composeai.rcplayer.protocol.RcHeader.DENSITY_BEHAVIOR_DP
              ),
            )
          ),
        )
    )
}

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
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
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
  fun aTranslucentTextColourIsReportedAsWrittenOpaque() {
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
    assertContains(compact(design.json), "\"color\":{\"type\":\"string\",\"value\":\"#000000\"}")
    assertEquals(listOf("a translucent colour is written opaque"), design.unsupported)
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
  fun aVariableFontWeightWritesItsDefaultAndIsReported() {
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
    assertContains(compact(design.json), "\"fontWeight\":{\"type\":\"string\",\"value\":\"700\"}")
    assertEquals(1, design.unsupported.size, design.unsupported.toString())
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/**
 * A document's own `FontData` font, end to end, in both text paths: `CoreText` instances it at the
 * style's variation axes, and canvas text (`DrawTextRun`) can name it and instance it at a paint's
 * `FONT_AXIS`.
 *
 * The embedded payload is the repo's vendored Roboto Flex — the same file [RcFontAxisRenderTest]
 * hands the player as a *host* face — so the only difference from that test is where the bytes come
 * from. As there, `wdth` is the measured axis: nothing but a real variable instance makes a run
 * narrower, so equal ink at `wdth 25` and `wdth 151` means the axes were dropped. The host loader
 * is always empty: whatever the text draws in, it can only have come from the document.
 */
class RcDocumentFontRenderTest {

  @Test
  fun `a CoreText naming an embedded font is instanced at its wdth axis`() {
    val narrow = inkWidth(coreTextDocument(wdth = 25f))
    val wide = inkWidth(coreTextDocument(wdth = 151f))

    assertTrue(narrow > 0, "the wdth 25 line drew nothing at all")
    assertTrue(
      wide > narrow * 13 / 10,
      "wdth 151 must set much wider than wdth 25 on the embedded font — same ink means the axes " +
        "were dropped (narrow=$narrow, wide=$wide)",
    )
  }

  @Test
  fun `a CoreText naming a FontData id with no family text draws the embedded font`() {
    // What AndroidX's creation API writes: property 8 is the `FontData` id and no text is declared
    // under it. This resolved to the default face, where no axis has an effect.
    val narrow = inkWidth(coreTextDocument(wdth = 25f, familyName = null))
    val wide = inkWidth(coreTextDocument(wdth = 151f, familyName = null))

    assertTrue(narrow > 0, "the wdth 25 line drew nothing at all")
    assertTrue(
      wide > narrow * 13 / 10,
      "the embedded font was not used (narrow=$narrow, wide=$wide)",
    )
  }

  @Test
  fun `canvas text naming an embedded font by its FontData bit is instanced at a paint axis`() {
    val narrow = inkWidth(canvasDocument(RcFloatWord.literal(25f), fontDataBit = true))
    val wide = inkWidth(canvasDocument(RcFloatWord.literal(151f), fontDataBit = true))

    assertTrue(narrow > 0, "the wdth 25 run drew nothing at all")
    assertTrue(
      wide > narrow * 13 / 10,
      "canvas text must draw the embedded font at its paint axis (narrow=$narrow, wide=$wide)",
    )
  }

  @Test
  fun `canvas text naming an embedded font through a shared family text draws it`() {
    // The same shape `CoreText` already resolved: a family-name text under the `FontData`'s id.
    val narrow = inkWidth(canvasDocument(RcFloatWord.literal(25f), familyName = FAMILY_NAME))
    val wide = inkWidth(canvasDocument(RcFloatWord.literal(151f), familyName = FAMILY_NAME))

    assertTrue(
      wide > narrow * 13 / 10,
      "the embedded font was not used (narrow=$narrow, wide=$wide)",
    )
  }

  @Test
  fun `an embedded font's paint axis bound to a named float follows the float`() {
    val document = canvasDocument(RcFloatWord(0x7fc00000 or WDTH_FLOAT_ID), fontDataBit = true)
    val narrow = inkWidth(document, mapOf("USER:wdth" to RcNamedValue.FloatValue(25f)))
    val wide = inkWidth(document, mapOf("USER:wdth" to RcNamedValue.FloatValue(151f)))

    assertTrue(
      wide > narrow * 13 / 10,
      "the axis did not follow its float (narrow=$narrow, wide=$wide)",
    )
  }

  @Test
  fun `an embedded variable font follows the text's weight`() {
    // `CoreText` names its weight as a `wght` axis as well as a style weight; on the embedded file
    // that axis is what moves the outlines, across the whole range rather than to one bold.
    val regular = inkArea(coreTextDocument(wdth = null, weight = 400))
    val bold = inkArea(coreTextDocument(wdth = null, weight = 700))
    val black = inkArea(coreTextDocument(wdth = null, weight = 1000))

    assertTrue(regular > 0, "the 400 line drew nothing at all")
    assertTrue(bold > regular, "the 700 instance must be heavier (regular=$regular, bold=$bold)")
    assertTrue(black > bold, "wght 1000 must be heavier than 700 (bold=$bold, black=$black)")
  }

  /**
   * Before/after evidence for `renders/cmp-fontdata-axes/`, written only when
   * `RC_LAYOUT_EVIDENCE_DIR` is set. Each image stacks three runs at `wdth` 25, 100 and 151 of the
   * document's embedded Roboto Flex, named the one way both the old and the new player read: a
   * family-name text sharing the `FontData`'s id.
   */
  @Test
  fun writeDocumentFontEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val widths = listOf(25f, 100f, 151f)
    directory
      .resolve("coretext-fontdata-wdth.png")
      .writeBytes(stack(widths.map { coreTextDocument(it) }))
    directory
      .resolve("canvas-fontdata-wdth.png")
      .writeBytes(
        stack(widths.map { canvasDocument(RcFloatWord.literal(it), familyName = FAMILY_NAME) })
      )
  }

  /**
   * One `CoreText` run in the embedded font. [familyName] non-null declares that text under the
   * font's id (the shape the player already resolved); null leaves the id bare, as AndroidX writes.
   */
  private fun coreTextDocument(
    wdth: Float?,
    familyName: String? = FAMILY_NAME,
    weight: Int = 400,
  ): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcFontData(FONT_ID, 0, robotoFlex()),
        RcRootLayout(-2),
        RcBoxLayout(-3, -1, 2, 2),
        RcLayoutContent(-4),
        RcTextData(42, SAMPLE),
        RcTextData(44, "wdth"),
      ) +
        listOfNotNull(familyName?.let { RcTextData(FONT_ID, it) }) +
        RcCoreText(
          textId = 42,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(40f)),
              RcTextStyleProperty.FloatValue(7, RcFloatWord.literal(weight.toFloat())),
              RcTextStyleProperty.IntValue(8, FONT_ID),
            ) +
              if (wdth == null) emptyList()
              else
                listOf(
                  RcTextStyleProperty.IntArrayValue(20, listOf(44)),
                  RcTextStyleProperty.FloatArrayValue(21, listOf(RcFloatWord.literal(wdth))),
                ),
        ) +
        RcLayoutContent(-5) +
        List(5) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  /**
   * One `DrawTextRun` whose paint names the embedded font — by the `FontData` bit when
   * [fontDataBit], else by a [familyName] text under the font's id — and sets `wdth`.
   */
  private fun canvasDocument(
    wdth: RcFloatWord,
    fontDataBit: Boolean = false,
    familyName: String? = null,
  ): RcDocument {
    val typefaceStyle = 400 or if (fontDataBit) PAINT_TYPEFACE_FONT_DATA else 0
    val setup: List<RcOperation> = listOfNotNull(familyName?.let { RcTextData(FONT_ID, it) })
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcFontData(FONT_ID, 0, robotoFlex()),
        RcTextData(42, SAMPLE),
        RcTextData(44, "wdth"),
        RcFloatConstant(WDTH_FLOAT_ID, RcFloatWord.literal(100f)),
        RcNamedVariable(WDTH_FLOAT_ID, RcNamedVariable.FLOAT_TYPE, "USER:wdth"),
      ) +
        setup +
        listOf(
          RcPaintData(
            listOf(
              PAINT_COLOR,
              0xff000000.toInt(),
              PAINT_TEXT_SIZE,
              RcFloatWord.literal(40f).bits,
              PAINT_TYPEFACE or (typefaceStyle shl 16),
              FONT_ID,
              PAINT_FONT_AXIS or (1 shl 16),
              44,
              wdth.bits,
            )
          ),
          RcDrawText(
            42,
            0,
            SAMPLE.length,
            0,
            SAMPLE.length,
            RcFloatWord.literal(10f),
            RcFloatWord.literal(55f),
            false,
          ),
        ),
    )
  }

  private fun render(document: RcDocument, named: Map<String, RcNamedValue> = emptyMap()): Image {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(
          document,
          namedValues = mutableStateMapOf<String, RcNamedValue>().apply { putAll(named) },
          typefaces = RcTypefaceLoader.Empty,
        )
      }
    try {
      return scene.render(0L)
    } finally {
      scene.close()
    }
  }

  private fun pixels(document: RcDocument, named: Map<String, RcNamedValue> = emptyMap()): Bitmap =
    Bitmap().apply {
      allocN32Pixels(WIDTH, HEIGHT)
      check(render(document, named).readPixels(this))
    }

  /** Columns containing any ink — the run's set width, which is what a `wdth` instance changes. */
  private fun inkWidth(document: RcDocument, named: Map<String, RcNamedValue> = emptyMap()): Int =
    pixels(document, named).let { bitmap ->
      (0 until WIDTH).count { x -> (0 until HEIGHT).any { y -> bitmap.getColor(x, y) != 0 } }
    }

  /** Summed ink coverage: how heavy the run is, which is what a weight changes. */
  private fun inkArea(document: RcDocument): Long =
    pixels(document).let { bitmap ->
      (0 until WIDTH).sumOf { x ->
        (0 until HEIGHT).sumOf { y -> (bitmap.getColor(x, y) ushr 24).toLong() }
      }
    }

  /** [documents] rendered one above the other on white, as a PNG. */
  private fun stack(documents: List<RcDocument>): ByteArray {
    val surface = Surface.makeRasterN32Premul(WIDTH, HEIGHT * documents.size)
    surface.canvas.clear(Color.WHITE)
    documents.forEachIndexed { row, document ->
      surface.canvas.drawImage(render(document), 0f, (row * HEIGHT).toFloat())
    }
    return surface.makeImageSnapshot().encodeToData()!!.bytes
  }

  private fun robotoFlex(): ByteArray {
    val face = File(VARIABLE_FACE_PATH).takeIf { it.isFile }?.readBytes()
    assertNotNull(face, "vendored Roboto Flex not found at $VARIABLE_FACE_PATH")
    return face
  }

  private companion object {
    const val WIDTH = 600
    const val HEIGHT = 80
    const val FONT_ID = 43
    const val WDTH_FLOAT_ID = 50
    const val SAMPLE = "Hamburgefonstiv"
    const val FAMILY_NAME = "Embedded Flex"
    const val PAINT_TEXT_SIZE = 1
    const val PAINT_COLOR = 4
    const val PAINT_TYPEFACE = 16
    const val PAINT_FONT_AXIS = 23

    /** As in [RcFontAxisRenderTest]: the wasm lane's vendored face, read from the repository. */
    const val VARIABLE_FACE_PATH = "../../rc-player/wasm/dist-assets/fonts/RobotoFlex.ttf"
  }
}

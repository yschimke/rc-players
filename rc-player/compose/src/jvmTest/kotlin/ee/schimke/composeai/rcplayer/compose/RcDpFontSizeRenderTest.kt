package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * A DP document's literal text font size is in dp, as remote-core alpha22's `CoreText` and
 * `TextLayout` read it: scaled by the host density. A variable font size is the pixels it evaluates
 * to, in a DP document too. Measured off the ink, against the same text at 40 px in a legacy
 * document.
 */
class RcDpFontSizeRenderTest {

  @Test
  fun aDpDocumentsLiteralFontSizeIsScaledByTheHostDensity() {
    for (operation in listOf(::coreText, ::textLayout)) {
      val legacy = inkWidth(document(legacyHeader(), operation(RcFloatWord.literal(PIXELS))))
      assertTrue(legacy > 0, "the legacy document drew no ink")
      val dp = inkWidth(document(dpHeader(), operation(RcFloatWord.literal(PIXELS / DENSITY))))
      assertEquals(legacy.toFloat(), dp.toFloat(), TOLERANCE_PX, "DP literal drew ${dp}px")
    }
  }

  @Test
  fun aDpDocumentsVariableFontSizeIsPixels() {
    for (operation in listOf(::coreText, ::textLayout)) {
      val legacy = inkWidth(document(legacyHeader(), operation(RcFloatWord.literal(PIXELS))))
      val variable =
        inkWidth(
          document(
            dpHeader(),
            operation(RcFloatWord(0x7fc00000 or SIZE_ID)),
            RcFloatConstant(SIZE_ID, RcFloatWord.literal(PIXELS)),
          )
        )
      assertEquals(legacy.toFloat(), variable.toFloat(), TOLERANCE_PX)
    }
  }

  /** Writes PR evidence when explicitly requested; ordinary test runs remain side-effect free. */
  @Test
  fun writeDpFontSizeEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    for ((name, operation) in listOf("coretext" to ::coreText, "textlayout" to ::textLayout)) {
      val document = document(dpHeader(), operation(RcFloatWord.literal(PIXELS / DENSITY)))
      directory.resolve("dp-$name.png").writeBytes(render(document).encodeToData()!!.bytes)
    }
  }

  private fun render(document: RcDocument): Image {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(DENSITY)) {
        RcComposePlayer(document)
      }
    try {
      return scene.render(0L)
    } finally {
      scene.close()
    }
  }

  private fun inkWidth(document: RcDocument): Int {
    val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
    check(render(document).readPixels(bitmap))
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
  }

  private fun document(header: RcHeader, text: RcOperation, vararg setup: RcOperation) =
    RcDocument(
      header,
      setup.toList() +
        listOf(
          RcTextData(42, TEXT),
          RcRootLayout(1),
          RcLayoutContent(2),
          text,
          RcLayoutContent(4),
        ) +
        List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  private fun coreText(fontSize: RcFloatWord): RcOperation =
    RcCoreText(
      textId = 42,
      properties =
        listOf(
          RcTextStyleProperty.IntValue(1, 3),
          RcTextStyleProperty.IntValue(3, 0xff101828.toInt()),
          RcTextStyleProperty.FloatValue(5, fontSize),
        ),
    )

  private fun textLayout(fontSize: RcFloatWord): RcOperation =
    RcTextLayout(
      componentId = 3,
      animationId = 30,
      textId = 42,
      color = 0xff101828.toInt(),
      fontSize = fontSize,
      fontStyle = 0,
      fontWeight = RcFloatWord.literal(400f),
      fontFamilyId = -1,
      textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
      overflow = RcTextLayout.OVERFLOW_CLIP,
      maxLines = 1,
    )

  private fun legacyHeader() =
    RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false)

  private fun dpHeader() =
    RcHeader(
      RcVersion(1, 0, 0),
      properties =
        listOf(
          RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(WIDTH)),
          RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(HEIGHT)),
          RcHeaderProperty(
            RcHeader.DOC_DENSITY_AT_GENERATION,
            RcHeaderValue.FloatValue(RcFloatWord.literal(DENSITY)),
          ),
          RcHeaderProperty(
            RcHeader.DOC_DENSITY_BEHAVIOR,
            RcHeaderValue.IntValue(RcHeader.DENSITY_BEHAVIOR_DP),
          ),
        ),
    )

  private companion object {
    const val WIDTH = 900
    const val HEIGHT = 200
    const val DENSITY = 2f
    const val PIXELS = 40f
    const val SIZE_ID = 77
    const val TEXT = "Remote Compose"
    const val TOLERANCE_PX = 1f
  }
}

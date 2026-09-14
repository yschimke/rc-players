package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/** Density-behavior coverage for rounded clip radii. */
class RcRoundedClipDensityTest {

  /** The alpha18 fixture records the old writer's already-scaled DP corner value. */
  @OptIn(ExperimentalEncodingApi::class)
  @Test
  fun alpha18FixtureCornerCarriesTheGenerationDensity() {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$APP_CARD_FIXTURE")) {
          "missing fixture /rc-fixtures/$APP_CARD_FIXTURE"
        }
        .use { Base64.decode(it.readBytes().decodeToString().trim()) }
    val document = RcDocumentCodec.decode(bytes)

    assertEquals(RcHeader.DENSITY_BEHAVIOR_DP, document.header.densityBehavior)
    assertEquals(2f, document.header.density)

    val corners =
      document.operations.filterIsInstance<RcRoundedClipRectModifier>().map {
        listOf(it.topStart, it.topEnd, it.bottomStart, it.bottomEnd).map(RcFloatWord::value)
      }
    assertEquals(listOf(listOf(52f, 52f, 52f, 52f)), corners)
  }

  /**
   * A 20dp corner on an 80px box remains below the radius normalization threshold at density 1,
   * while at density 2 it scales to 40px. Pixel (12, 3) distinguishes those two arcs.
   */
  @Test
  fun aDpDocumentScalesItsClipRadiusWithPlaybackDensity() {
    assertEquals(GREEN, cornerPixel(Density(1f)))
    assertEquals(0xffff0000.toInt(), cornerPixel(Density(2f)))
  }

  private fun cornerPixel(density: Density): Int {
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = density) {
        RcComposePlayer(clippedCanvas())
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(SIZE, SIZE) }
      check(scene.render().readPixels(bitmap))
      return bitmap.getColor(12, 3)
    } finally {
      scene.close()
    }
  }

  /** A red 80×80 box, clipped to a 20px corner, with a green rect painted inside the clip. */
  private fun clippedCanvas(): RcDocument =
    RcDocument(
      RcHeader(
        RcVersion(1, 0, 0),
        properties =
          listOf(
            RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(SIZE)),
            RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(SIZE)),
            RcHeaderProperty(
              RcHeader.DOC_DENSITY_AT_GENERATION,
              RcHeaderValue.FloatValue(RcFloatWord.literal(2f)),
            ),
            RcHeaderProperty(
              RcHeader.DOC_DENSITY_BEHAVIOR,
              RcHeaderValue.IntValue(RcHeader.DENSITY_BEHAVIOR_DP),
            ),
          ),
      ),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcCanvasLayout(3, 30),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(BOX)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(BOX)),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(1f),
          green = RcFloatWord.literal(0f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcRoundedClipRectModifier(
          RcFloatWord.literal(CORNER),
          RcFloatWord.literal(CORNER),
          RcFloatWord.literal(CORNER),
          RcFloatWord.literal(CORNER),
        ),
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        RcPaintData(listOf(4, GREEN)),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(BOX),
          RcFloatWord.literal(BOX),
        ),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )

  private companion object {
    const val APP_CARD_FIXTURE = "AppCardRemote-640x480.rc.b64"
    const val SIZE = 100
    const val BOX = 80f
    const val CORNER = 20f
    val GREEN = 0xff00ff00.toInt()
  }
}

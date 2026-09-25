package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * A graphics layer's transform origin defaults to `remote-core`'s declared 0, the top-left, as it
 * does in AndroidX's embedded player.
 *
 * The current writer writes a centre origin explicitly and omits only 0, so a document that means
 * the centre says so. The fixture is a mirror: a red bar at the left of a layer under `SCALE_X =
 * -1`. About the centre it lands at the right; about the left edge it leaves the layer.
 */
@OptIn(ExperimentalComposeUiApi::class)
class RcGraphicsLayerOriginRenderTest {

  @Test
  fun anUnauthoredOriginMirrorsAboutTheLeftEdge() {
    assertEquals(0, redPixels(mirror(origin = null), 0, WIDTH), "the bar leaves the layer")
  }

  @Test
  fun aWrittenCentreOriginMirrorsAboutTheCentre() {
    val document = mirror(origin = 0.5f)
    assertEquals(
      BAR * HEIGHT,
      redPixels(document, WIDTH - BAR, WIDTH),
      "the bar lands at the right",
    )
    assertEquals(0, redPixels(document, 0, BAR), "nothing is left at the left")
  }

  /** Pure red pixels in the columns `[fromX, toX)`. */
  private fun redPixels(document: RcDocument, fromX: Int, toX: Int): Int {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render().readPixels(bitmap))
      var count = 0
      for (y in 0 until HEIGHT) for (x in fromX until toX) if (bitmap.getColor(x, y) == RED) count++
      return count
    } finally {
      scene.close()
    }
  }

  private fun mirror(origin: Float?): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val layer = buildList {
      add(
        RcGraphicsLayerAttribute.FloatValue(
          RcGraphicsLayerModifier.SCALE_X,
          RcFloatWord.literal(-1f),
        )
      )
      if (origin != null) {
        for (id in
          listOf(
            RcGraphicsLayerModifier.TRANSFORM_ORIGIN_X,
            RcGraphicsLayerModifier.TRANSFORM_ORIGIN_Y,
          )) {
          add(RcGraphicsLayerAttribute.FloatValue(id, RcFloatWord.literal(origin)))
        }
      }
    }
    val operations =
      buildList<RcOperation> {
        add(RcRootLayout(1))
        add(RcBoxLayout(3, 0, 1, 4))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        add(RcLayoutContent(4))
        add(RcCanvasLayout(5, 50))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        add(RcGraphicsLayerModifier(layer))
        add(RcNoArg(RcOpcodes.CANVAS_OPERATIONS))
        add(RcPaintData(listOf(4, RED)))
        add(
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(BAR.toFloat()),
            RcFloatWord.literal(HEIGHT.toFloat()),
          )
        )
        repeat(5) { add(end) }
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      operations,
    )
  }

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val BAR = 10
    const val RED = 0xffff0000.toInt()
  }
}

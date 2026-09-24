package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
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
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A graphics layer's blur reaches the pixels, and the shape and tile-mode attributes that come with
 * it no longer refuse the document.
 *
 * `remote-creation-compose` writes a `BlurEffect` as `BLUR_RADIUS_X/Y` plus an int
 * `BLUR_TILE_MODE`, and every layer's shape as an int `SHAPE`. The player used to refuse all of
 * them.
 */
class RcGraphicsLayerBlurRenderTest {

  @Test
  fun aBlurredLayerSpreadsItsEdge() {
    val document = document(blur = true)
    assertEquals(emptyList(), document.composeSupportReport().issues)
    val blurred = edgeRedness(document)
    val sharp = edgeRedness(document(blur = false))
    assertEquals(0, sharp, "without a blur the column beside the bar stays clear")
    assertTrue(blurred > 0, "with a blur the bar bleeds into the column beside it")
  }

  /** The red channel just right of the bar's edge, summed down the column. */
  private fun edgeRedness(document: RcDocument): Int {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      return (0 until HEIGHT).sumOf { y -> (bitmap.getColor(BAR + 2, y) shr 16) and 0xff }
    } finally {
      scene.close()
    }
  }

  private fun document(blur: Boolean): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val layer = buildList {
      add(
        RcGraphicsLayerAttribute.IntValue(
          RcGraphicsLayerModifier.SHAPE,
          RcGraphicsLayerModifier.SHAPE_RECT,
        )
      )
      if (blur) {
        add(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.BLUR_RADIUS_X,
            RcFloatWord.literal(4f),
          )
        )
        add(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.BLUR_RADIUS_Y,
            RcFloatWord.literal(4f),
          )
        )
        add(
          RcGraphicsLayerAttribute.IntValue(
            RcGraphicsLayerModifier.BLUR_TILE_MODE,
            RcGraphicsLayerModifier.TILE_MODE_DECAL,
          )
        )
      }
    }
    val operations =
      buildList<RcOperation> {
        add(RcRootLayout(1))
        add(RcLayoutContent(2))
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
        repeat(4) { add(end) }
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      operations,
    )
  }

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val BAR = 20
    const val RED = 0xffff0000.toInt()
  }
}

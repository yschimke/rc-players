package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A document that reads its own size only to round its corners — what the creation library writes
 * for every circle or rounded clip — draws them on its first frame without settling: the size is
 * published from the measure pass, before the clip is applied and the frame drawn.
 */
class RcPaintOnlyGeometryTest {

  @Test
  fun aClipRadiusFromAComponentValueDoesNotSettle() {
    assertFalse(RcPreparedDocument(circle()).settlesFirstLayout)
  }

  @Test
  fun aComponentValueThatSizesAChildStillSettles() {
    assertTrue(RcPreparedDocument(circle(capChildWidth = true)).settlesFirstLayout)
  }

  @Test
  fun aDocumentWithoutComponentValuesDoesNotSettle() {
    val plain = circle().operations.filterNot { it is RcComponentValue }
    assertFalse(rcGeometryReachesLayout(plain))
  }

  @Test
  fun theCircleIsRoundOnTheFirstFrame() {
    val scene =
      ImageComposeScene(width = 40, height = 40, density = Density(1f)) {
        RcComposePlayer(RcPreparedDocument(circle()))
      }
    try {
      val first = scene.render(0L).greenPixels()
      // A 40 px circle covers about π·20² ≈ 1257 px; a square clip (radius 0) would cover 1600.
      assertTrue(first in 1150..1350, "the first frame drew $first green pixels, not a circle")
      assertEquals(first, scene.render(FRAME_NANOS).greenPixels())
      assertEquals(first, scene.render(10 * FRAME_NANOS).greenPixels())
    } finally {
      scene.close()
    }
  }

  private fun org.jetbrains.skia.Image.greenPixels(): Int {
    val image = this
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    var count = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) if (bitmap.getColor(x, y) == GREEN) count++
    }
    return count
  }

  /**
   * A 40×40 green box clipped to a circle whose radius is half its own published width. With
   * [capChildWidth] a child's width is also capped by that width, which a first layout must settle.
   */
  private fun circle(capChildWidth: Boolean = false): RcDocument {
    val child =
      if (capChildWidth) {
        listOf(
          RcBoxLayout(5, -1, 1, 4),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f)),
          RcWidthInModifier(RcFloatWord.literal(-1f), reference(WIDTH)),
          RcLayoutContent(6),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        )
      } else emptyList<RcOperation>()
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
      listOf(
        RcComponentValue(RcComponentValue.WIDTH, componentId = 3, valueId = WIDTH),
        RcFloatExpression(
          RADIUS,
          listOf(reference(WIDTH), RcFloatWord.literal(2f), FLOAT_DIV),
          animation = null,
        ),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcRoundedClipRectModifier(
          reference(RADIUS),
          reference(RADIUS),
          reference(RADIUS),
          reference(RADIUS),
        ),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(0f),
          green = RcFloatWord.literal(1f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(4),
      ) +
        child +
        listOf(
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
    )
  }

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val GREEN = 0xff00ff00.toInt()
    const val WIDTH = 42
    const val RADIUS = 43
    /** AndroidX's float `DIV` operator, NaN-encoded the way a document carries it. */
    val FLOAT_DIV = RcFloatWord(0xff800000.toInt() or 0x310004)
    const val FRAME_NANOS = 16_666_667L
  }
}

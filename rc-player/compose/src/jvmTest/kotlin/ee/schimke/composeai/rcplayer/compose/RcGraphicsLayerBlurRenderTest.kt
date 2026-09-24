package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
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
@OptIn(ExperimentalComposeUiApi::class)
class RcGraphicsLayerBlurRenderTest {

  @Test
  fun aBlurredLayerSpreadsItsEdge() {
    val document = document(blur = true)
    assertEquals(emptyList(), document.composeSupportReport().issues)
    val blurred = render(document) { edgeRedness(0L) }
    val sharp = render(document(blur = false)) { edgeRedness(0L) }
    assertEquals(0, sharp, "without a blur the column beside the bar stays clear")
    assertTrue(blurred > 0, "with a blur the bar bleeds into the column beside it")
  }

  /**
   * AndroidX eases every graphics-layer float, so a blur bound to a variable a host moves grows
   * over the tween instead of jumping, and the player keeps drawing frames until it lands.
   */
  @Test
  fun aBlurBoundToAVariableEasesToItsNewRadius() {
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val document = document(blur = true, radius = RcFloatWord(0x7fc00000 or BLUR_ID))
    render(document, namedValues) {
      assertEquals(0, edgeRedness(0L), "no blur before the write")
      namedValues["USER:blur"] = RcNamedValue.FloatValue(8f)
      // Written outside composition: deliver it now, or the first timed frame may not see it.
      Snapshot.sendApplyNotifications()
      val frames = (1..STEPS).map { edgeRedness(it * FRAME) }
      val settled = frames.last()
      assertTrue(settled > 0, "the blur lands: $frames")
      assertTrue(
        frames.any { it in 1 until settled },
        "some frame is part-way between no blur and the settled one: $frames",
      )
    }
  }

  private fun <T> render(
    document: RcDocument,
    namedValues: SnapshotStateMap<String, RcNamedValue> = mutableStateMapOf(),
    block: ImageComposeScene.() -> T,
  ): T {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document, namedValues = namedValues)
      }
    try {
      return scene.block()
    } finally {
      scene.close()
    }
  }

  /** The coverage (alpha) just right of the bar's edge, summed down the column. */
  private fun ImageComposeScene.edgeRedness(nanos: Long): Int {
    val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
    check(render(nanos).readPixels(bitmap))
    return (0 until HEIGHT).sumOf { y -> (bitmap.getColor(BAR + 2, y) ushr 24) and 0xff }
  }

  private fun document(
    blur: Boolean,
    radius: RcFloatWord = RcFloatWord.literal(4f),
  ): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val layer = buildList {
      add(
        RcGraphicsLayerAttribute.IntValue(
          RcGraphicsLayerModifier.SHAPE,
          RcGraphicsLayerModifier.SHAPE_RECT,
        )
      )
      if (blur) {
        add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.BLUR_RADIUS_X, radius))
        add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.BLUR_RADIUS_Y, radius))
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
        add(RcFloatConstant(BLUR_ID, RcFloatWord.literal(0f)))
        add(RcNamedVariable(BLUR_ID, RcNamedVariable.FLOAT_TYPE, "USER:blur"))
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
    const val BLUR_ID = 21
    const val FRAME = 16_000_000L
    /** Comfortably past the 300ms tween. */
    const val STEPS = 30
  }
}

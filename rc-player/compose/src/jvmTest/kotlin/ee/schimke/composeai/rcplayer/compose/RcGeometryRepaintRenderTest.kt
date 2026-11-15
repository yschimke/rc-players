package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
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
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A component's geometry read only by another component's drawing is repainted, not recomposed.
 *
 * The top canvas's width is a named value, so a host edit resizes it, and the player eases the
 * resize over its bounds animation — publishing the width every frame, as AndroidX does. The bottom
 * canvas never changes size; it draws a bar as wide as the top one, through a `ComponentValue`.
 * Nothing laid out reads that width, so each frame of the animation only draws the bottom canvas
 * again (`RcPlayerState.onRepaint`). Unless that draw block redraws on the repaint, the bar stays
 * at the width it had when the player last recomposed, short of the top canvas.
 */
class RcGeometryRepaintRenderTest {

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aBarDrawnFromAnotherComponentsWidthFollowsItsResize() =
    runSkikoComposeUiTest(size = Size(60f, 20f), density = Density(1f)) {
      mainClock.autoAdvance = false
      val namedValues = mutableStateMapOf<String, RcNamedValue>()
      setContent { RcComposePlayer(document(), namedValues = namedValues) }
      repeat(3) { mainClock.advanceTimeByFrame() }
      waitForIdle()
      assertEquals(20, barWidth(onRoot().captureToImage().toPixelMap()))

      namedValues["USER:width"] = RcNamedValue.FloatValue(50f)
      mainClock.advanceTimeBy(600)
      waitForIdle()

      val pixels = onRoot().captureToImage().toPixelMap()
      assertEquals(50, topWidth(pixels), "the top canvas did not resize")
      assertEquals(50, barWidth(pixels), "the bar was not repainted at the resized width")
    }

  /**
   * A box that eases its width to a host value, with the size readback the creation library writes
   * for every rounded clip (`ComponentValue` width and height, which nothing here reads), as a poll
   * card's bars have. Its size is published from the measure pass on every frame of the animation;
   * nothing laid out reads it, so those frames compose nothing. They used to recompose the whole
   * player, every frame.
   */
  @Test
  fun aResizeOnlyDrawingReadsComposesNothingWhileItAnimates() {
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val scene =
      androidx.compose.ui.ImageComposeScene(width = 60, height = 20, density = Density(1f)) {
        RcComposePlayer(barDocument(), namedValues = namedValues)
      }
    try {
      var time = 0L
      repeat(3) {
        scene.render(time)
        time += FRAME_NANOS
      }
      val perFrame =
        countingCompositions(RENDER_LAYOUT_NODE) { count ->
          namedValues["USER:width"] = RcNamedValue.FloatValue(50f)
          androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
          List(30) {
            val before = count()
            scene.render(time)
            time += FRAME_NANOS
            count() - before
          }
        }
      // The change itself recomposes, on the frames it lands; the animation after it does not.
      assertEquals(List(25) { 0 }, perFrame.drop(5), "composed while it animated: $perFrame")
    } finally {
      scene.close()
    }
  }

  private fun topWidth(pixels: androidx.compose.ui.graphics.PixelMap) =
    (0 until 60).count { pixels[it, 5].toArgb() == RED }

  private fun barWidth(pixels: androidx.compose.ui.graphics.PixelMap) =
    (0 until 60).count { pixels[it, 15].toArgb() == GREEN }

  private fun document(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations = mutableListOf<RcOperation>()
    operations += RcFloatConstant(WIDTH, RcFloatWord.literal(20f))
    operations += RcNamedVariable(WIDTH, RcNamedVariable.FLOAT_TYPE, "USER:width")
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcColumnLayout(3, -1, 1, 1, RcFloatWord.literal(0f))
    operations += RcLayoutContent(4)
    // The top canvas: sized by the named value, its width published as TOP_WIDTH.
    operations += RcCanvasLayout(TOP, -1)
    operations += RcWidthModifier(RcDimensionType.EXACT, reference(WIDTH))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f))
    operations += RcComponentValue(RcComponentValue.WIDTH, componentId = TOP, valueId = TOP_WIDTH)
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    operations += RcPaintData(listOf(4, RED))
    operations += rect(reference(WIDTH))
    operations += end
    operations += end
    // The bottom canvas: fixed at 60, drawing a bar as wide as the top canvas.
    operations += RcCanvasLayout(BOTTOM, -1)
    operations += RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f))
    // Its own layer, so redrawing the column around it on the top canvas's resize does not
    // redraw it too: only the repaint does.
    operations +=
      RcGraphicsLayerModifier(
        listOf(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.TRANSLATION_X,
            RcFloatWord.literal(0f),
          ),
        ),
      )
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    operations += RcPaintData(listOf(4, GREEN))
    operations += rect(reference(TOP_WIDTH))
    operations += end
    operations += end
    repeat(4) { operations += end }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 20, modern = false),
      operations,
    )
  }

  private fun barDocument(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val radius = RcFloatWord.literal(4f)
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 20, modern = false),
      listOf(
        RcFloatConstant(WIDTH, RcFloatWord.literal(20f)),
        RcNamedVariable(WIDTH, RcNamedVariable.FLOAT_TYPE, "USER:width"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(TOP, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, reference(WIDTH)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f)),
        RcRoundedClipRectModifier(radius, radius, radius, radius),
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
        RcComponentValue(RcComponentValue.WIDTH, componentId = TOP, valueId = TOP_WIDTH),
        RcComponentValue(RcComponentValue.HEIGHT, componentId = TOP, valueId = TOP_HEIGHT),
        RcLayoutContent(3),
        end,
        end,
        end,
        end,
      ),
    )
  }

  private fun rect(width: RcFloatWord) =
    RcDraw4(
      RcOpcodes.DRAW_RECT,
      RcFloatWord.literal(0f),
      RcFloatWord.literal(0f),
      width,
      RcFloatWord.literal(10f),
    )

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val FRAME_NANOS = 16_000_000L
    const val WIDTH = 21
    const val TOP_WIDTH = 42
    const val TOP_HEIGHT = 43
    const val TOP = 5
    const val BOTTOM = 6
    const val RED = 0xffff0000.toInt()
    const val GREEN = 0xff00ff00.toInt()
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * A layout canvas that draws from the document clock — a dial, a sweep, a cube spinning on a matrix
 * expression — has to redraw every frame, the way AndroidX's players paint it. Raw draw-list
 * documents always did; a canvas inside the layout tree drew once and held that pose, because
 * nothing it read in its draw block changed from one frame to the next.
 *
 * The canvas draws a rect whose left edge is a float expression reading `ANIMATION_TIME`, so the
 * first red pixel is the time in seconds. Nothing but the scene's frame clock moves it.
 */
class RcCanvasClockRenderTest {
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aClockDrawingLayoutCanvasRedrawsEveryFrame() {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(clockDocument())
      }
    try {
      assertEquals(0, scene.blockLeftEdge(nanos = 0L), "the block starts at the left")
      // One sample a second for ten seconds, each drawing that frame's time with no lag. A frame
      // 16 ms earlier primes each sample: the player's frame loop re-arms `withFrameNanos` after
      // every frame, and a sample rendered before it has re-armed would draw the time of the frame
      // before — a one-frame stutter on a live display, not the hold-forever this pins.
      val edges =
        (1..10).map {
          scene.render(it * 1_000_000_000L - 16_000_000L)
          scene.blockLeftEdge(nanos = it * 1_000_000_000L)
        }
      assertEquals((1..10).toList(), edges, "the canvas redraws with the clock frame by frame")
    } finally {
      scene.close()
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun ImageComposeScene.blockLeftEdge(nanos: Long): Int {
    val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
    check(render(nanos).readPixels(bitmap))
    return (0 until WIDTH).firstOrNull { bitmap.getColor(it, HEIGHT / 2) == RED } ?: -1
  }

  private fun clockDocument(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        // What a writer emits for `animationTime()` used as a draw coordinate: a float expression
        // over the clock, and the draw operation reading the expression's id.
        RcFloatExpression(
          TIME_ID,
          listOf(RcFloatWord(0x7fc00000 or RcSystemVariables.ANIMATION_TIME)),
          animation = null,
        ),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcCanvasLayout(5, 50),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())),
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        RcPaintData(listOf(4, RED)),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord(0x7fc00000 or TIME_ID),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(WIDTH.toFloat()),
          RcFloatWord.literal(HEIGHT.toFloat()),
        ),
        end,
        end,
        end,
        end,
      ),
    )
  }

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val RED = 0xffff0000.toInt()
    const val TIME_ID = 42
  }
}

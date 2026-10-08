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
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * A graphics layer bound to the document clock — a card that spins or slides on `ANIMATION_TIME` —
 * has to follow that clock frame by frame, the way AndroidX's players read it on every paint.
 *
 * The layer's `TRANSLATION_X` is a float expression that reads `ANIMATION_TIME`, so the block's
 * left edge is the time in seconds. Nothing but the scene's frame clock moves it.
 */
class RcGraphicsLayerClockRenderTest {
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aClockBoundLayerFollowsTheFrameClock() {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(clockDocument())
      }
    try {
      assertEquals(0, scene.blockLeftEdge(nanos = 0L), "the block starts at the left")
      // One frame a second for ten seconds: each frame draws that frame's time, with no lag.
      val edges = (1..10).map { scene.blockLeftEdge(nanos = it * 1_000_000_000L) }
      assertEquals((1..10).toList(), edges, "the layer follows the clock frame by frame")
    } finally {
      scene.close()
    }
  }

  /** Writes PR evidence when explicitly requested; ordinary test runs remain side-effect free. */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun writeClockBoundLayerEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(clockDocument())
      }
    try {
      for (second in 0..EVIDENCE_SECONDS step 10) {
        val frame = scene.render(second * 1_000_000_000L)
        directory
          .resolve("clock-layer-${second}s.png")
          .writeBytes(
            checkNotNull(frame.encodeToData()) { "Skia declined to encode the render" }.bytes
          )
      }
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
        // What a writer emits for `animationTime()` handed to a graphics layer: a float expression
        // over the clock, and the layer bound to the expression's id.
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
        RcGraphicsLayerModifier(
          listOf(
            RcGraphicsLayerAttribute.FloatValue(
              RcGraphicsLayerModifier.TRANSLATION_X,
              RcFloatWord(0x7fc00000 or TIME_ID),
            )
          )
        ),
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        RcPaintData(listOf(4, RED)),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(10f),
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
    const val EVIDENCE_SECONDS = 40
  }
}

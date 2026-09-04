package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** Writes the PR's filmstrip. Not a check — set `RC_FILMSTRIP` to a path to run it. */
class RcTransitionFilmstripTest {
  private val end = RcNoArg(RcOpcodes.CONTAINER_END)

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun writeFilmstrip() {
    val target = System.getenv("RC_FILMSTRIP") ?: return
    val frames = mutableListOf<Bitmap>()
    runSkikoComposeUiTest(size = Size(120f, 120f), density = Density(1f)) {
      mainClock.autoAdvance = false
      setContent { RcComposePlayer(document()) }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      frames += onRoot().captureToImage().asSkiaBitmap()
      onNode(hasClickAction()).performTouchInput { click() }
      repeat(4) {
        mainClock.advanceTimeBy(75)
        waitForIdle()
        frames += onRoot().captureToImage().asSkiaBitmap()
      }
      mainClock.advanceTimeBy(600)
      waitForIdle()
      frames += onRoot().captureToImage().asSkiaBitmap()
    }
    val gap = 8
    val width = frames.size * 120 + (frames.size + 1) * gap
    val surface = Surface.makeRasterN32Premul(width, 120 + 2 * gap)
    val canvas: Canvas = surface.canvas
    canvas.clear(Color.WHITE)
    val border = Paint().apply { color = Color.makeARGB(255, 200, 200, 200) }
    frames.forEachIndexed { index, bitmap ->
      val x = (gap + index * (120 + gap)).toFloat()
      canvas.drawRect(Rect.makeXYWH(x - 1, gap - 1f, 122f, 122f), border)
      canvas.drawImage(Image.makeFromBitmap(bitmap), x, gap.toFloat())
    }
    File(target).parentFile?.mkdirs()
    File(target).writeBytes(surface.makeImageSnapshot().encodeToData()!!.bytes)
  }

  private fun document(): RcDocument {
    val operations =
      listOf<RcOperation>(
        RcIntegerConstant(20, 0),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcStateLayout(3, 30, horizontalPositioning = 1, verticalPositioning = 4, indexId = 20),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
        spec(30),
        RcClickModifier,
        RcValueIntegerChangeAction(20, 1),
        end,
        RcLayoutContent(4),
      ) + branch(5, 1, 4) + branch(6, 3, 5) + List(4) { end }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 120, legacyHeight = 120, modern = false),
      operations,
    )
  }

  private fun branch(componentId: Int, horizontal: Int, vertical: Int): List<RcOperation> =
    listOf(
      RcBoxLayout(componentId, 0, horizontal, vertical),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
      RcLayoutContent(componentId * 100),
      RcCanvasLayout(componentId * 100 + 1, 500),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
      spec(500),
      RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      RcPaintData(listOf(4, 0xff1a73e8.toInt())),
      RcDraw4(
        RcOpcodes.DRAW_RECT,
        RcFloatWord.literal(0f),
        RcFloatWord.literal(0f),
        RcFloatWord.literal(40f),
        RcFloatWord.literal(40f),
      ),
      end,
      end,
      end,
      end,
    )

  private fun spec(animationId: Int) =
    RcAnimationSpec(
      animationId = animationId,
      motionDurationMillis = RcFloatWord.literal(300f),
      motionEasingType = 4,
      visibilityDurationMillis = RcFloatWord.literal(300f),
      visibilityEasingType = 4,
      enterAnimation = RcLayoutAnimation.FadeIn,
      exitAnimation = RcLayoutAnimation.FadeOut,
    )
}

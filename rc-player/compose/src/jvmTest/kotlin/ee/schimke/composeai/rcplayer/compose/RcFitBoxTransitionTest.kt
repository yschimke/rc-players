package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A `FitBox` picks the first of its alternatives that fits the space it is given, so the
 * alternative changes when that space does. Upstream made that change a transition rather than a
 * swap ("Add FitBox shared element transitions using Compose Intrinsics", androidx-main
 * `6fb763d3fe4`); this is the ported behaviour, driven here by shrinking the box's own width under
 * a click.
 */
class RcFitBoxTransitionTest {
  private val end = RcNoArg(RcOpcodes.CONTAINER_END)
  private val widthId = 20

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun narrowingTheBoxCrossFadesToTheAlternativeThatStillFits() =
    runSkikoComposeUiTest(size = Size(100f, 60f), density = Density(1f)) {
      mainClock.autoAdvance = false
      setContent { RcComposePlayer(document()) }
      mainClock.advanceTimeByFrame()
      waitForIdle()

      val before = onRoot().captureToImage().toPixelMap()[10, 10]
      assertTrue(before.red > 0.9f, "the 60px alternative fits a 100px box, was $before")

      // The click narrows the box to 30px, which only the 20px alternative fits.
      onNode(hasClickAction()).performTouchInput { click() }
      mainClock.advanceTimeBy(150)
      waitForIdle()
      val halfway = onRoot().captureToImage().toPixelMap()[10, 10]
      assertTrue(halfway.red > 0.05f, "the outgoing alternative is still fading out, was $halfway")
      assertTrue(halfway.blue > 0.05f, "and the incoming one fading in, was $halfway")

      mainClock.advanceTimeBy(1000)
      waitForIdle()
      val after = onRoot().captureToImage().toPixelMap()[10, 10]
      assertTrue(after.blue > 0.8f, "the 20px alternative has taken over, was $after")
      assertTrue(after.red < 0.05f, "and the 60px one is gone, was $after")
    }

  private fun document(): RcDocument {
    val operations =
      listOf<RcOperation>(
        RcFloatConstant(widthId, RcFloatWord.literal(100f)),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcFitBoxLayout(3, 30, horizontalPositioning = 1, verticalPositioning = 4),
        // The width is a variable the click rewrites, so the space the alternatives are measured
        // against changes without the document being reloaded.
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord(0x7fc00000 or widthId)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcAnimationSpec(
          animationId = 30,
          motionDurationMillis = RcFloatWord.literal(300f),
          motionEasingType = 4,
          visibilityDurationMillis = RcFloatWord.literal(300f),
          visibilityEasingType = 4,
          enterAnimation = RcLayoutAnimation.FadeIn,
          exitAnimation = RcLayoutAnimation.FadeOut,
        ),
        RcClickModifier,
        RcValueFloatChangeAction(widthId, RcFloatWord.literal(30f)),
        end,
        RcLayoutContent(4),
      ) +
        canvas(componentId = 5, size = 60f, color = RED) +
        canvas(componentId = 6, size = 20f, color = BLUE) +
        List(4) { end }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 60, modern = false),
      operations,
    )
  }

  private fun canvas(componentId: Int, size: Float, color: Int): List<RcOperation> =
    listOf(
      RcCanvasLayout(componentId, 0),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(size)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(size)),
      RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      RcPaintData(listOf(4, color)),
      RcDraw4(
        RcOpcodes.DRAW_RECT,
        RcFloatWord.literal(0f),
        RcFloatWord.literal(0f),
        RcFloatWord.literal(size),
        RcFloatWord.literal(size),
      ),
      end,
      end,
    )

  private companion object {
    const val RED = 0xffff0000.toInt()
    const val BLUE = 0xff0000ff.toInt()
  }
}

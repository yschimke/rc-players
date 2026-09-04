package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `StateLayout` switch is a transition, not a swap — ported from AndroidX's embedded player (
 * "Support StateLayout shared element animations in RC embedded player", androidx-main
 * `3d97be4c888`).
 *
 * Two things are pinned here, because they are the two the port is for: the branches cross-fade
 * over the layout's own `AnimationSpec` duration, and a component that carries the same animation
 * id in both branches *morphs* between its two sizes rather than the outgoing one vanishing at its
 * old size while the incoming one appears at its new one.
 */
class RcStateLayoutTransitionTest {
  private val end = RcNoArg(RcOpcodes.CONTAINER_END)
  private val indexId = 20

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun switchingBranchesCrossFadesOverTheAnimationSpecDuration() =
    runSkikoComposeUiTest(size = Size(60f, 60f), density = Density(1f)) {
      mainClock.autoAdvance = false
      val document =
        stateLayoutDocument(
          canvas(componentId = 5, animationId = 0, size = 60f, color = RED),
          canvas(componentId = 6, animationId = 0, size = 60f, color = BLUE),
        )
      setContent { RcComposePlayer(document) }
      mainClock.advanceTimeByFrame()
      waitForIdle()

      assertEquals(RED, onRoot().captureToImage().toPixelMap()[30, 30].toArgb())

      onNode(hasClickAction()).performTouchInput { click() }
      mainClock.advanceTimeBy(150)
      waitForIdle()
      val halfway = onRoot().captureToImage().toPixelMap()[30, 30]
      assertTrue(halfway.red > 0.05f, "outgoing branch still fading out, was $halfway")
      assertTrue(halfway.blue > 0.05f, "incoming branch already fading in, was $halfway")

      mainClock.advanceTimeBy(1000)
      waitForIdle()
      // Channels, not an exact ARGB: the click that drove the switch leaves the player's press
      // indication washing the component for as long as the pointer was down.
      val settled = onRoot().captureToImage().toPixelMap()[30, 30]
      assertTrue(settled.blue > 0.8f, "the incoming branch has fully faded in, was $settled")
      assertTrue(settled.red < 0.05f, "and the outgoing one is gone, was $settled")
    }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aComponentSharedByBothBranchesMorphsBetweenItsTwoPositions() =
    runSkikoComposeUiTest(size = Size(60f, 60f), density = Density(1f)) {
      mainClock.autoAdvance = false
      // The same animation id on both sides is what makes this one element rather than two: a 20px
      // square in the top-left corner of the first branch and in the bottom-right of the second.
      val document =
        stateLayoutDocument(
          branch(componentId = 5, horizontal = 1, vertical = 4),
          branch(componentId = 6, horizontal = 3, vertical = 5),
        )
      setContent { RcComposePlayer(document) }
      mainClock.advanceTimeByFrame()
      waitForIdle()

      val before = onRoot().captureToImage().toPixelMap()
      assertTrue(before[10, 10].red > 0.5f, "the element starts in the top-left corner")
      assertTrue(before[30, 30].red < 0.05f, "and nowhere else")

      onNode(hasClickAction()).performTouchInput { click() }
      mainClock.advanceTimeBy(150)
      waitForIdle()
      // The morph is the point: halfway through, one square is halfway across the box. A plain
      // cross-fade would instead show two squares in the two corners, each at half opacity, and
      // nothing in the middle.
      val halfway = onRoot().captureToImage().toPixelMap()
      assertTrue(halfway[30, 30].red > 0.05f, "the element is mid-flight, was ${halfway[30, 30]}")
      assertTrue(halfway[5, 5].red < 0.05f, "it has left the corner it started in")
      assertTrue(
        halfway[55, 55].red < 0.05f,
        "and has not arrived at the one it is going to",
      )

      mainClock.advanceTimeBy(1000)
      waitForIdle()
      val after = onRoot().captureToImage().toPixelMap()
      assertTrue(after[55, 55].red > 0.5f, "and it settles in the bottom-right corner")
      assertTrue(after[10, 10].red < 0.05f, "with nothing left behind")
    }

  private fun stateLayoutDocument(
    first: List<RcOperation>,
    second: List<RcOperation>,
  ): RcDocument {
    val operations =
      listOf<RcOperation>(
        RcIntegerConstant(indexId, 0),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcStateLayout(
          componentId = 3,
          animationId = 30,
          horizontalPositioning = 1,
          verticalPositioning = 4,
          indexId = indexId,
        ),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        animationSpec(30),
        RcClickModifier,
        RcValueIntegerChangeAction(indexId, 1),
        end,
        RcLayoutContent(4),
      ) + first + second + List(4) { end }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 60, modern = false),
      operations,
    )
  }

  /** One branch: a box carrying [animationId], around a canvas of [size] that fills it. */
  private fun canvas(
    componentId: Int,
    animationId: Int,
    size: Float,
    color: Int,
  ): List<RcOperation> =
    listOf(
      RcBoxLayout(componentId, animationId, horizontalPositioning = 1, verticalPositioning = 4),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(size)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(size)),
      animationSpec(animationId),
      RcLayoutContent(componentId * 100),
      RcCanvasLayout(componentId * 100 + 1, 0),
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
      end,
      end,
    )

  /**
   * One branch of the shared-element test: a full-size box that parks a 20px square in the corner
   * [horizontal] / [vertical] names. The square — not the box — carries the shared animation id, so
   * what the transition has to interpolate is its position.
   */
  private fun branch(componentId: Int, horizontal: Int, vertical: Int): List<RcOperation> =
    listOf(
      RcBoxLayout(
        componentId,
        0,
        horizontalPositioning = horizontal,
        verticalPositioning = vertical,
      ),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
      RcLayoutContent(componentId * 100),
      RcCanvasLayout(componentId * 100 + 1, SHARED_ID),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
      animationSpec(SHARED_ID),
      RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      RcPaintData(listOf(4, RED)),
      RcDraw4(
        RcOpcodes.DRAW_RECT,
        RcFloatWord.literal(0f),
        RcFloatWord.literal(0f),
        RcFloatWord.literal(20f),
        RcFloatWord.literal(20f),
      ),
      end,
      end,
      end,
      end,
    )

  private fun animationSpec(animationId: Int) =
    RcAnimationSpec(
      animationId = animationId,
      motionDurationMillis = RcFloatWord.literal(300f),
      motionEasingType = 4,
      visibilityDurationMillis = RcFloatWord.literal(300f),
      visibilityEasingType = 4,
      enterAnimation = RcLayoutAnimation.FadeIn,
      exitAnimation = RcLayoutAnimation.FadeOut,
    )

  private companion object {
    const val RED = 0xffff0000.toInt()
    const val BLUE = 0xff0000ff.toInt()
    const val SHARED_ID = 500
  }
}

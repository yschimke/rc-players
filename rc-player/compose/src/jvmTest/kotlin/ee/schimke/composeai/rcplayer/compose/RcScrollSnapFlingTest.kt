package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins where a scroll container's fling lands for each touch-expression stop mode.
 *
 * The container is a 40px horizontal viewport over five 40px cells, so the scroll range is `0..160`
 * and an even four-notch stop sits on every cell boundary. Drags are released with an explicit end
 * velocity so the landing position is deterministic under the test clock.
 */
@OptIn(ExperimentalTestApi::class)
class RcScrollSnapFlingTest {
  @Test
  fun evenNotchesSettleASlowDragOnTheNearestNotch() =
    runScroll(notches(4)) {
      drag(distance = 50f, velocity = 0f)
      assertEquals(40f, scrollPosition())

      drag(distance = 30f, velocity = 0f)
      assertEquals(80f, scrollPosition())
    }

  @Test
  fun evenNotchesCarryAFastFlingPastTheNearestNotch() =
    runScroll(notches(4)) {
      // Released at rest, the same drag settles back on 0.
      drag(distance = 15f, velocity = 0f)
      assertEquals(0f, scrollPosition())

      // Released moving, the fling's decay carries it on before it settles.
      drag(distance = 15f, velocity = 700f)
      assertEquals(120f, scrollPosition())
    }

  @Test
  fun endsStopSettlesOnTheCloserEnd() =
    runScroll(stop(RcTouchExpression.STOP_ENDS)) {
      drag(distance = 60f, velocity = 0f)
      assertEquals(0f, scrollPosition())

      drag(distance = 100f, velocity = 0f)
      assertEquals(160f, scrollPosition())
    }

  @Test
  fun absoluteNotchesSettleOnTheClosestListedStop() =
    runScroll(
      stop(
        RcTouchExpression.STOP_NOTCHES_ABSOLUTE,
        0f,
        70f,
        160f,
      )
    ) {
      drag(distance = 90f, velocity = 0f)
      assertEquals(70f, scrollPosition())
    }

  @Test
  fun singleNotchStopAdvancesOneNotchPerGesture() =
    runScroll(
      stop(
        RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN,
        4f,
        160f,
      )
    ) {
      drag(distance = 20f, velocity = 800f)
      assertEquals(40f, scrollPosition())

      // Bounded by where this gesture started, not where the first one did.
      drag(distance = 20f, velocity = 800f)
      assertEquals(80f, scrollPosition())
    }

  @Test
  fun nonSnappingStopLeavesTheScrollWhereTheDragEnds() =
    runScroll(stop(RcTouchExpression.STOP_INSTANTLY)) {
      drag(distance = 50f, velocity = 0f)
      // Within the few px the pointer-slop hand-off moves it, and nowhere near a snap stop.
      assertEquals(50f, scrollPosition(), absoluteTolerance = 3f)
    }

  private fun notches(count: Int): RcTouchExpression =
    stop(
      RcTouchExpression.STOP_NOTCHES_EVEN,
      count.toFloat(),
      160f,
    )

  private fun stop(mode: Int, vararg spec: Float): RcTouchExpression =
    RcTouchExpression(
      id = POSITION_ID,
      defaultValue = RcFloatWord.literal(0f),
      min = RcFloatWord.literal(0f),
      max = reference(MAX_ID),
      velocityId = RcFloatWord.literal(Float.NaN),
      touchEffects = 0,
      expression = listOf(reference(13)),
      stopMode = mode,
      stopSpec = spec.map(RcFloatWord::literal),
      easingSpec = emptyList(),
    )

  private fun runScroll(touch: RcTouchExpression, block: SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
      setContent {
        CompositionLocalProvider(LocalRcInspection provides true) {
          RcComposePlayer(document(touch), Modifier.fillMaxSize())
        }
      }
      block()
    }

  /**
   * Scrolls the content [distance] px towards the end, releasing at [velocity] px/s. The pointer
   * travels the touch slop further, since the scroll only starts consuming once it is crossed.
   */
  private fun SkikoComposeUiTest.drag(distance: Float, velocity: Float) {
    onRoot().performTouchInput {
      val travel = distance + viewConfiguration.touchSlop
      swipeWithVelocity(
        start = Offset(39f, 20f),
        end = Offset(39f - travel, 20f),
        endVelocity = velocity,
        durationMillis = if (velocity == 0f) 200L else (travel / velocity * 1_000f).toLong(),
      )
    }
    waitForIdle()
  }

  private fun SkikoComposeUiTest.scrollPosition(): Float =
    -onNode(SemanticsMatcher.keyIsDefined(RcScrollOffsetKey))
      .fetchSemanticsNode()
      .config[RcScrollOffsetKey]
      .x

  private fun document(touch: RcTouchExpression): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val cells: List<RcOperation> =
      (0 until 5).flatMap { index ->
        listOf(
          RcCanvasLayout(10 + index, 100 + index),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcBackgroundModifier(
            flags = 0,
            colorId = 0,
            reserved1 = 0,
            reserved2 = 0,
            red = RcFloatWord.literal(index / 4f),
            green = RcFloatWord.literal(0f),
            blue = RcFloatWord.literal(1f - index / 4f),
            alpha = RcFloatWord.literal(1f),
            shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
          ),
          end,
        )
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcRowLayout(3, 30, 1, 4, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcScrollModifier(
          RcScrollModifier.HORIZONTAL,
          reference(POSITION_ID),
          reference(MAX_ID),
          reference(NOTCH_MAX_ID),
        ),
        touch,
        end,
        RcLayoutContent(4),
      ) + cells + listOf(end, end, end, end),
    )
  }

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val POSITION_ID = 41
    const val MAX_ID = 42
    const val NOTCH_MAX_ID = 43
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.*
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RcTouchExpressionInputTest {
  @Test
  fun rootExpressionsAndPaddedComponentExpressionsReadTheCurrentOuterPosition() {
    for (root in listOf(true, false)) runSkikoComposeUiTest(
      size = Size(100f, 100f),
      density = Density(1f),
    ) {
      val registry =
        RcCustomComponentRegistry(
          "probe" to { component, modifier -> BasicText(component.float(1).toString(), modifier) }
        )
      setContent { RcComposePlayer(document(root), customComponents = registry) }
      waitForIdle()
      onRoot().performTouchInput {
        down(Offset(5f, 50f))
        moveTo(Offset(40f, 50f))
        up()
      }
      waitForIdle()
      onNodeWithText("40.0").assertExists()
    }
  }

  @Test
  fun velocityOnlyExpressionsClaimTheirDragAxis() {
    for (axis in listOf(15, 16)) runSkikoComposeUiTest(
      size = Size(100f, 100f),
      density = Density(1f),
    ) {
      val registry =
        RcCustomComponentRegistry(
          "probe" to { component, modifier -> BasicText(component.float(1).toString(), modifier) }
        )
      setContent {
        RcComposePlayer(document(root = true, touchReference = axis), customComponents = registry)
      }
      waitForIdle()
      onRoot().performTouchInput {
        down(Offset(20f, 20f))
        for (position in listOf(40f, 60f, 80f)) {
          moveTo(if (axis == 15) Offset(position, 20f) else Offset(20f, position), delayMillis = 16)
        }
        up()
      }
      waitForIdle()
      onNodeWithText("100.0").assertExists()
    }
  }

  @Test
  fun aChildTakingTheDragCancelsTouchActionsExactlyOnce() =
    runSkikoComposeUiTest(size = Size(100f, 100f), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      val registry =
        RcCustomComponentRegistry(
          "probe" to
            { _, modifier ->
              BasicText(
                "child",
                modifier.pointerInput(Unit) {
                  awaitPointerEventScope {
                    while (true) {
                      awaitPointerEvent().changes.forEach {
                        if (it.position != it.previousPosition) it.consume()
                      }
                    }
                  }
                },
              )
            }
        )
      val actions =
        listOf(
          RcTouchDownModifier,
          RcHostAction(1),
          end,
          RcTouchUpModifier,
          RcHostAction(2),
          end,
          RcTouchCancelModifier,
          RcHostAction(3),
          end,
        )
      setContent {
        RcComposePlayer(
          document(false, actions, expression = false),
          customComponents = registry,
          onEvent = events::add,
        )
      }
      waitForIdle()
      onRoot().performTouchInput {
        down(Offset(30f, 50f))
        moveTo(Offset(60f, 50f))
        up()
      }
      waitForIdle()
      assertEquals(
        listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(1), RcPlayerEvent.HostAction(3)),
        events,
      )
    }

  /**
   * A slider: a component moving its own relative touch expression, with a touch-up action that
   * sends where it was left. Its drag is the component's own, so the release runs UP, not CANCEL,
   * as in AndroidX; and the document plays without a fallback.
   */
  @Test
  fun aComponentsOwnTouchExpressionReleasesWithTouchUp() {
    for (moved in listOf(true, false)) runSkikoComposeUiTest(
      size = Size(100f, 100f),
      density = Density(1f),
    ) {
      val events = mutableListOf<RcPlayerEvent>()
      val registry =
        RcCustomComponentRegistry(
          "probe" to { component, modifier -> BasicText(component.float(1).toString(), modifier) }
        )
      val document =
        document(
          root = false,
          actions = sliderActions,
          stopMode = RcTouchExpression.STOP_INSTANTLY,
        )
      val report = document.composeSupportReport(availableCustomComponents = setOf("probe"))
      assertTrue(report.fullyRenderable, report.toString())
      setContent { RcComposePlayer(document, customComponents = registry, onEvent = events::add) }
      waitForIdle()
      onRoot().performTouchInput {
        down(Offset(30f, 50f))
        if (moved) {
          for (x in listOf(40f, 50f, 60f)) moveTo(Offset(x, 50f), delayMillis = 16)
        }
        up()
      }
      waitForIdle()
      // Relative: the value moves by the drag's 30 px from where it rested, not to the finger.
      onNodeWithText(if (moved) "30.0" else "0.0").assertExists()
      assertEquals(
        listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(1), RcPlayerEvent.HostAction(2)),
        events,
      )
    }
  }

  @Test
  fun anEndStopExpressionSettlesAtTheNearerEndOnRelease() =
    runSkikoComposeUiTest(size = Size(100f, 100f), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      val registry =
        RcCustomComponentRegistry(
          "probe" to { component, modifier -> BasicText(component.float(1).toString(), modifier) }
        )
      setContent {
        RcComposePlayer(
          document(root = false, actions = sliderActions, stopMode = RcTouchExpression.STOP_ENDS),
          customComponents = registry,
          onEvent = events::add,
        )
      }
      waitForIdle()
      onRoot().performTouchInput {
        // 60 of 100: past halfway, so it settles at the end.
        down(Offset(30f, 50f))
        for (x in listOf(50f, 70f, 90f)) moveTo(Offset(x, 50f), delayMillis = 16)
        up()
      }
      waitForIdle()
      onNodeWithText("100.0").assertExists()
      assertEquals(
        listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(1), RcPlayerEvent.HostAction(2)),
        events,
      )
    }

  @Test
  fun aFreeStandingGentleStopKeepsTheFallback() {
    // A component's expression settles snapping modes on release but has no fling yet.
    val report =
      document(root = false, stopMode = RcTouchExpression.STOP_GENTLY)
        .composeSupportReport(availableCustomComponents = setOf("probe"))
    assertTrue(!report.fullyRenderable, report.toString())
  }

  private val sliderActions
    get() =
      listOf(
        RcTouchDownModifier,
        RcHostAction(1),
        end,
        RcTouchUpModifier,
        RcHostAction(2),
        end,
        RcTouchCancelModifier,
        RcHostAction(3),
        end,
      )

  private fun document(
    root: Boolean,
    actions: List<RcOperation> = emptyList(),
    expression: Boolean = true,
    touchReference: Int = 13,
    stopMode: Int = RcTouchExpression.STOP_ABSOLUTE_POS,
  ): RcDocument {
    val touch =
      RcTouchExpression(
        100,
        literal(0f),
        literal(0f),
        literal(100f),
        literal(0f),
        0,
        listOf(reference(touchReference)),
        stopMode,
        emptyList(),
        emptyList(),
      )
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 100, modern = false),
      buildList {
        add(RcTextData(50, "probe"))
        if (root && expression) add(touch)
        add(RcRootLayout(1))
        add(RcCustomLayout(2, 0, 50, listOf(RcCustomProperty.float(1, reference(100)))))
        add(RcWidthModifier(RcDimensionType.EXACT, literal(100f)))
        add(RcHeightModifier(RcDimensionType.EXACT, literal(100f)))
        add(RcPaddingModifier(literal(20f), literal(0f), literal(20f), literal(0f)))
        // Modifiers first, as a writer puts them, then the component's own content.
        addAll(actions)
        if (!root && expression) add(touch)
        add(end)
        add(end)
      },
    )
  }

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private val end = RcNoArg(RcOpcodes.CONTAINER_END)
}

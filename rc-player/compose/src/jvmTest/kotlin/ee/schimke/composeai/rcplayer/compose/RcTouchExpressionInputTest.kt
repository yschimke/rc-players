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

  private fun document(
    root: Boolean,
    actions: List<RcOperation> = emptyList(),
    expression: Boolean = true,
    touchReference: Int = 13,
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
        RcTouchExpression.STOP_ABSOLUTE_POS,
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
        if (!root && expression) add(touch)
        addAll(actions)
        add(end)
        add(end)
      },
    )
  }

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private val end = RcNoArg(RcOpcodes.CONTAINER_END)
}

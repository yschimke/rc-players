package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickType
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRippleModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Click feedback runs through a Compose `Indication` fed by the component's presses: the ripple is
 * drawn only when the document asks for one, click actions still fire, and multi-click keeps its
 * single / double / long discrimination with the reference player's timing.
 */
@OptIn(ExperimentalTestApi::class)
class RcClickIndicationTest {
  @Test
  fun rippleDrawsOnlyWhenTheDocumentRequestsOne() {
    for (multiClick in listOf(false, true)) for (withRipple in listOf(false, true)) {
      runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
        val events = mutableListOf<RcPlayerEvent>()
        val document =
          blackBox(
            buildList {
              if (withRipple) add(RcRippleModifier)
              add(
                if (multiClick) RcMultiClickModifier(RcMultiClickType.SINGLE) else RcClickModifier
              )
              add(RcHostAction(77))
              add(END)
              if (multiClick) {
                add(RcMultiClickModifier(RcMultiClickType.DOUBLE))
                add(RcHostAction(78))
                add(END)
              }
            }
          )
        setContent { RcComposePlayer(document, onEvent = events::add) }
        waitForIdle()
        mainClock.autoAdvance = false
        assertEquals(Color.Black, centrePixel(), "at rest (ripple=$withRipple)")

        onRoot().performTouchInput { down(center) }
        mainClock.advanceTimeBy(150)
        val pressed = centrePixel()
        if (withRipple) {
          assertTrue(pressed.red > 0.3f, "a requested ripple lightens the press point: $pressed")
        } else {
          assertEquals(Color.Black, pressed, "no ripple without a RippleModifier")
        }

        onRoot().performTouchInput { up() }
        mainClock.advanceTimeBy(1_500)
        assertEquals(Color.Black, centrePixel(), "the ripple has faded (ripple=$withRipple)")
        assertEquals(listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(77)), events)
      }
    }
  }

  /**
   * The conformance lane's gestures: a click, a long press starting straight after it, then a
   * double click whose two taps have no time between them. The reference player recognises all
   * three; `combinedClickable` does not (its `doubleTapMinTimeMillis`, and a press inside the
   * double-tap window cannot long-press), which is why multi-click keeps its own recogniser.
   */
  @Test
  fun multiClickRecognisesTheReferenceGestureTiming() =
    runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      val document =
        blackBox(
          listOf(
            RcMultiClickModifier(RcMultiClickType.SINGLE),
            RcHostAction(71),
            END,
            RcMultiClickModifier(RcMultiClickType.LONG),
            RcHostAction(72),
            END,
            RcMultiClickModifier(RcMultiClickType.DOUBLE),
            RcHostAction(73),
            END,
          )
        )
      mainClock.autoAdvance = false
      setContent { RcComposePlayer(document, onEvent = events::add) }
      waitForIdle()

      onRoot().performTouchInput {
        down(center)
        up()
      }
      onRoot().performTouchInput { down(center) }
      mainClock.advanceTimeBy(516)
      waitForIdle()
      onRoot().performTouchInput { up() }
      onRoot().performTouchInput {
        down(center)
        up()
        down(center)
        up()
      }
      mainClock.advanceTimeBy(1_000)
      waitForIdle()

      assertEquals(
        listOf<RcPlayerEvent>(
          RcPlayerEvent.HostAction(71),
          RcPlayerEvent.HostAction(72),
          RcPlayerEvent.HostAction(73),
        ),
        events,
      )
    }

  @Test
  fun rippleWithoutAClickActionStillAnswersTouch() =
    runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
      setContent { RcComposePlayer(blackBox(listOf(RcRippleModifier))) }
      waitForIdle()
      mainClock.autoAdvance = false

      onRoot().performTouchInput { down(center) }
      mainClock.advanceTimeBy(150)
      assertTrue(centrePixel().red > 0.3f, "AndroidX ripples on every touch down")
      onRoot().performTouchInput { up() }
    }

  @Test
  fun singleClickWaitsOutTheDoubleClickAndLongHoldFallsBackToSingle() =
    runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      val document =
        blackBox(
          listOf(
            RcMultiClickModifier(RcMultiClickType.SINGLE),
            RcHostAction(71),
            END,
            RcMultiClickModifier(RcMultiClickType.DOUBLE),
            RcHostAction(73),
            END,
          )
        )
      setContent { RcComposePlayer(document, onEvent = events::add) }

      onRoot().performTouchInput { click() }
      waitForIdle()
      assertEquals(emptyList(), events, "a single click waits for a possible second one")
      mainClock.advanceTimeBy(1_000)
      waitForIdle()
      assertEquals(listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(71)), events)

      onRoot().performTouchInput { doubleClick() }
      mainClock.advanceTimeBy(1_000)
      waitForIdle()
      assertEquals(
        listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(71), RcPlayerEvent.HostAction(73)),
        events,
        "a double click runs only the double block",
      )

      // No LONG block: a held press is still a single click when it lifts.
      onRoot().performTouchInput { down(center) }
      mainClock.advanceTimeBy(1_000)
      onRoot().performTouchInput { up() }
      mainClock.advanceTimeBy(1_000)
      waitForIdle()
      assertEquals(
        listOf<RcPlayerEvent>(
          RcPlayerEvent.HostAction(71),
          RcPlayerEvent.HostAction(73),
          RcPlayerEvent.HostAction(71),
        ),
        events,
      )

      onNode(hasClickAction())
        .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        .performSemanticsAction(SemanticsActions.OnClick)
      waitForIdle()
      assertEquals(RcPlayerEvent.HostAction(71), events.last(), "the accessibility click")
    }

  @Test
  fun longClickIsAnAccessibilityActionWhenTheDocumentHasALongBlock() =
    runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      val document =
        blackBox(
          listOf(
            RcMultiClickModifier(RcMultiClickType.SINGLE),
            RcHostAction(71),
            END,
            RcMultiClickModifier(RcMultiClickType.LONG),
            RcHostAction(72),
            END,
          )
        )
      setContent { RcComposePlayer(document, onEvent = events::add) }

      onNode(hasClickAction()).performSemanticsAction(SemanticsActions.OnLongClick)
      waitForIdle()
      onNode(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
      waitForIdle()
      assertEquals(
        listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(72), RcPlayerEvent.HostAction(71)),
        events,
      )
    }

  private fun SkikoComposeUiTest.centrePixel(): Color {
    val pixels = onRoot().captureToImage().toPixelMap()
    return pixels[pixels.width / 2, pixels.height / 2]
  }

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)

    /** A 40 x 40 opaque black box carrying [clickModifiers] after its size and background. */
    fun blackBox(clickModifiers: List<RcOperation>): RcDocument =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
        buildList {
          add(RcRootLayout(1))
          add(RcBoxLayout(2, 20, 1, 4))
          add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)))
          add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)))
          add(
            RcBackgroundModifier(
              flags = 0,
              colorId = 0,
              reserved1 = 0,
              reserved2 = 0,
              red = RcFloatWord.literal(0f),
              green = RcFloatWord.literal(0f),
              blue = RcFloatWord.literal(0f),
              alpha = RcFloatWord.literal(1f),
              shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
            )
          )
          addAll(clickModifiers)
          add(END)
          add(END)
        },
      )
  }
}

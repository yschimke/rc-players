package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
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
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

class RcTouchPositionRenderTest {
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aDocumentReadsWhereThePointerIs() {
    // A bar as wide as the pointer's x, read through AndroidX's touch-position slot 13 — which
    // `CoreDocument` loads on every touch event, and which this player never published: the bar
    // stayed at 0 however the document was touched.
    val red = 0xffff0000.toInt()
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 10, modern = false),
        listOf(
          // `barWidth = touchX + 0`: an expression over slot 13, which is what the player scans for
          // to know that a touch has to repaint the document.
          RcFloatExpression(
            BAR_WIDTH,
            listOf(RcFloatWord(NAN_REFERENCE or TOUCH_X), RcFloatWord.literal(0f), ADD),
            null,
          ),
          RcPaintData(listOf(4, red)),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord(NAN_REFERENCE or BAR_WIDTH),
            RcFloatWord.literal(10f),
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = 40, height = 10, density = Density(1f)) {
        // Sized, as a host sizes it: a 0 x 0 player still draws, but receives no pointer.
        RcComposePlayer(document, Modifier.fillMaxSize())
      }
    try {
      scene.render()
      scene.sendPointerEvent(PointerEventType.Press, Offset(25f, 5f), type = PointerType.Touch)
      // The press invalidates through the global snapshot, whose apply notification can land after
      // the next frame has already begun; the contract is that a following frame shows it.
      scene.render(500_000_000L)
      val image = scene.render(1_000_000_000L)
      val bitmap = Bitmap().apply { allocN32Pixels(40, 10) }
      check(image.readPixels(bitmap))

      assertEquals(red, bitmap.getColor(23, 5))
      assertEquals(0, bitmap.getColor(27, 5))
    } finally {
      scene.close()
    }
  }

  @Test
  fun aDrawCallReadingThePointerDirectlyRepaints() {
    // No expression in between: the rect's right edge *is* touch x. The player has to find that
    // reference to know a touch changes the picture.
    val red = 0xffff0000.toInt()
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 10, modern = false),
        listOf(
          RcPaintData(listOf(4, red)),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord(NAN_REFERENCE or TOUCH_X),
            RcFloatWord.literal(10f),
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = 40, height = 10, density = Density(1f)) {
        RcComposePlayer(document, Modifier.fillMaxSize())
      }
    try {
      scene.render()
      scene.sendPointerEvent(PointerEventType.Press, Offset(25f, 5f), type = PointerType.Touch)
      scene.render(500_000_000L)
      val bitmap = Bitmap().apply { allocN32Pixels(40, 10) }
      check(scene.render(1_000_000_000L).readPixels(bitmap))

      assertEquals(red, bitmap.getColor(23, 5))
      assertEquals(0, bitmap.getColor(27, 5))
    } finally {
      scene.close()
    }
  }

  /**
   * A list the player sits in takes the gesture once the finger passes its touch slop. AndroidX's
   * View player then gets `ACTION_CANCEL` and nothing after it, so the bar keeps the last x it saw
   * rather than following the finger through the rest of the drag.
   */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aGestureTakenByAnAncestorStopsMovingThePointer() {
    val scene =
      ImageComposeScene(width = 100, height = 10, density = Density(1f)) {
        Box(
          Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, _ -> change.consume() }
          }
        ) {
          RcComposePlayer(barAsWideAsTouchX(100), Modifier.fillMaxSize())
        }
      }
    try {
      scene.render()
      scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(12f, 5f), type = PointerType.Touch)
      // Past the slop: the ancestor's drag starts here, and owns every move after it.
      scene.sendPointerEvent(PointerEventType.Move, Offset(40f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(70f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(90f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Release, Offset(90f, 5f), type = PointerType.Touch)
      scene.render(500_000_000L)
      val bitmap = Bitmap().apply { allocN32Pixels(100, 10) }
      check(scene.render(1_000_000_000L).readPixels(bitmap))

      // The move that crossed the slop is the last one the document saw.
      assertEquals(RED, bitmap.getColor(38, 5))
      assertEquals(0, bitmap.getColor(42, 5))

      // The next gesture is the player's again.
      scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 5f), type = PointerType.Touch)
      scene.render(1_500_000_000L)
      check(scene.render(2_000_000_000L).readPixels(bitmap))
      assertEquals(RED, bitmap.getColor(18, 5))
      assertEquals(0, bitmap.getColor(22, 5))
    } finally {
      scene.close()
    }
  }

  /**
   * A list can take the gesture on its own initial pass, so every later move reaches the player
   * already consumed: the dashboard's scrolling list does. That is the ancestor's taking too, not
   * the document's, and publishing on recomposed the card under the finger on every frame.
   */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aGestureAnAncestorTakesOnItsInitialPassStopsMovingThePointer() {
    val scene =
      ImageComposeScene(width = 100, height = 10, density = Density(1f)) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { consumeMovesPastSlopOnInitialPass() }) {
          RcComposePlayer(barAsWideAsTouchX(100), Modifier.fillMaxSize())
        }
      }
    try {
      scene.render()
      scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(12f, 5f), type = PointerType.Touch)
      // Past the slop: from here the ancestor consumes every move before the player sees it.
      scene.sendPointerEvent(PointerEventType.Move, Offset(40f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(70f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(90f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Release, Offset(90f, 5f), type = PointerType.Touch)
      scene.render(500_000_000L)
      val bitmap = Bitmap().apply { allocN32Pixels(100, 10) }
      check(scene.render(1_000_000_000L).readPixels(bitmap))

      // The move that crossed the slop reached the player unconsumed; nothing after it did.
      assertEquals(RED, bitmap.getColor(38, 5))
      assertEquals(0, bitmap.getColor(42, 5))
    } finally {
      scene.close()
    }
  }

  /** Consumes, on the initial pass, every move of a gesture once it has passed the touch slop. */
  private suspend fun androidx.compose.ui.input.pointer.PointerInputScope
    .consumeMovesPastSlopOnInitialPass() = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var taken = false
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == down.id } ?: break
      if (!change.pressed) break
      if (taken) change.consume()
      else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
        // The slop-crossing move goes through; the list starts dragging from the next one.
        taken = true
      }
    }
  }

  /** With nothing outside taking it, the bar follows the finger to the end of the drag. */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aDragNoAncestorTakesKeepsMovingThePointer() {
    val scene =
      ImageComposeScene(width = 100, height = 10, density = Density(1f)) {
        RcComposePlayer(barAsWideAsTouchX(100), Modifier.fillMaxSize())
      }
    try {
      scene.render()
      scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(40f, 5f), type = PointerType.Touch)
      scene.sendPointerEvent(PointerEventType.Move, Offset(70f, 5f), type = PointerType.Touch)
      scene.render(500_000_000L)
      val bitmap = Bitmap().apply { allocN32Pixels(100, 10) }
      check(scene.render(1_000_000_000L).readPixels(bitmap))

      assertEquals(RED, bitmap.getColor(68, 5))
      assertEquals(0, bitmap.getColor(72, 5))
    } finally {
      scene.close()
    }
  }

  /**
   * A drag the document scrolls itself is the document's gesture, and AndroidX keeps loading the
   * pointer through it. Only a consumer outside the player cancels.
   */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aDragTheDocumentScrollsItselfKeepsMovingThePointer() {
    val scene =
      ImageComposeScene(width = 40, height = 50, density = Density(1f)) {
        RcComposePlayer(scrollingRowAboveATouchBar(), Modifier.fillMaxSize())
      }
    try {
      scene.render()
      scene.sendPointerEvent(PointerEventType.Press, Offset(39f, 20f), type = PointerType.Touch)
      // Leftwards across the row, which scrolls (and consumes) once past the slop.
      for (x in 36 downTo 9 step 3) {
        scene.sendPointerEvent(
          PointerEventType.Move,
          Offset(x.toFloat(), 20f),
          type = PointerType.Touch,
        )
      }
      scene.render(500_000_000L)
      val bitmap = Bitmap().apply { allocN32Pixels(40, 50) }
      check(scene.render(1_000_000_000L).readPixels(bitmap))

      // The bar under the row is as wide as the last move, not the one that crossed the slop.
      assertEquals(RED, bitmap.getColor(7, 45))
      assertEquals(0, bitmap.getColor(11, 45))
    } finally {
      scene.close()
    }
  }

  private fun scrollingRowAboveATouchBar(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val cells =
      (0 until 5).flatMap { index ->
        listOf(
          RcCanvasLayout(10 + index, 100 + index),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          end,
        )
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 50, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, 30, 1, 1, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(50f)),
        RcLayoutContent(4),
        RcRowLayout(5, 50, 1, 4, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcScrollModifier(
          RcScrollModifier.HORIZONTAL,
          RcFloatWord(NAN_REFERENCE or SCROLL_POSITION),
          RcFloatWord(NAN_REFERENCE or SCROLL_MAX),
          RcFloatWord(NAN_REFERENCE or SCROLL_NOTCH_MAX),
        ),
        RcTouchExpression(
          id = SCROLL_POSITION,
          defaultValue = RcFloatWord.literal(0f),
          min = RcFloatWord.literal(0f),
          max = RcFloatWord(NAN_REFERENCE or SCROLL_MAX),
          velocityId = RcFloatWord.literal(Float.NaN),
          touchEffects = 0,
          expression = listOf(RcFloatWord(NAN_REFERENCE or TOUCH_X)),
          stopMode = RcTouchExpression.STOP_INSTANTLY,
          stopSpec = emptyList(),
          easingSpec = emptyList(),
        ),
        end,
        RcLayoutContent(6),
      ) +
        cells +
        listOf(
          end,
          end,
          RcCanvasLayout(7, 70),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f)),
          RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
          RcPaintData(listOf(4, RED)),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord(NAN_REFERENCE or TOUCH_X),
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          end,
          end,
          end,
          end,
        ),
    )
  }

  private fun barAsWideAsTouchX(width: Int) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = width, legacyHeight = 10, modern = false),
      listOf(
        RcPaintData(listOf(4, RED)),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord(NAN_REFERENCE or TOUCH_X),
          RcFloatWord.literal(10f),
        ),
      ),
    )

  private companion object {
    const val RED = 0xffff0000.toInt()
    const val NAN_REFERENCE = 0x7fc00000
    const val TOUCH_X = 13
    const val BAR_WIDTH = 100
    const val SCROLL_POSITION = 41
    const val SCROLL_MAX = 42
    const val SCROLL_NOTCH_MAX = 43
    /** AndroidX `AnimatedFloatExpression.ADD`: `OFFSET + 1` in an `0xff8`-prefixed NaN. */
    val ADD = RcFloatWord(0xffb10001.toInt())
  }
}

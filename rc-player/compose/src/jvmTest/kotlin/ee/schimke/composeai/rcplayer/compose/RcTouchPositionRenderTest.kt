package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
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

  private companion object {
    const val NAN_REFERENCE = 0x7fc00000
    const val TOUCH_X = 13
    const val BAR_WIDTH = 100
    /** AndroidX `AnimatedFloatExpression.ADD`: `OFFSET + 1` in an `0xff8`-prefixed NaN. */
    val ADD = RcFloatWord(0xffb10001.toInt())
  }
}

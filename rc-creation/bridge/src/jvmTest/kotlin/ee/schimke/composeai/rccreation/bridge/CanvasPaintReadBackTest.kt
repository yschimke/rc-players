package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawCircle
import androidx.compose.remote.core.operations.DrawRect
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The paints a canvas writes, as AndroidX's own reader (`remote-core` 1.0.0-alpha23) reads them and
 * as its `CoreDocument` applies them when it paints a frame.
 */
class CanvasPaintReadBackTest {
  private fun paint(color: Color) = RemotePaint { this.color = RemoteColor(color) }

  /** `usePaint` writes its paint, and the draw after it that has none is drawn with it. */
  @Test
  fun usePaintWritesThePaint() {
    val bytes = captureCanvasBytes {
      drawRect(paint(Color.Blue))
      usePaint(paint(Color.Green)) { drawCircle(null, 10f.rf) }
    }
    val order =
      androidxOperations(bytes).flattened().mapNotNull {
        when (it) {
          is PaintData -> "paint"
          is DrawRect -> "rect"
          is DrawCircle -> "circle"
          else -> null
        }
      }
    assertEquals(listOf("paint", "rect", "paint", "circle"), order)
    val painted =
      androidxPaints(bytes, 200, 100).filter { it.name == "rect" || it.name == "circle" }
    assertEquals(listOf("rect", "circle"), painted.map { it.name })
    assertEquals(listOf(Color.Blue.toArgb(), Color.Green.toArgb()), painted.map { it.color })
  }

  /**
   * A block that may not run leaves the player with a paint the canvas cannot know, so the first
   * paint in it and the first after it are whole paints, not differences: AndroidX, skipping the
   * block, draws the rectangle after it in the colour it asks for.
   */
  @Test
  fun aDrawAfterASkippedConditionalBlockHasItsPaint() {
    val bytes = captureCanvasBytes {
      val shown = RemoteBoolean.createNamedRemoteBoolean("shown", false)
      drawRect(paint(Color.Red))
      drawConditionally(shown) { drawCircle(paint(Color.Blue), 10f.rf) }
      drawRect(paint(Color.Blue))
    }
    val painted =
      androidxPaints(bytes, 200, 100).filter { it.name == "rect" || it.name == "circle" }
    assertEquals(listOf("rect", "rect"), painted.map { it.name })
    assertEquals(listOf(Color.Red.toArgb(), Color.Blue.toArgb()), painted.map { it.color })
    assertEquals(listOf(true, true, true), wholePaints(bytes))
  }

  /**
   * The same around an offscreen bitmap (AndroidX's player sets the paint's alpha when it switches
   * to one): the paint in it and the paint after it are whole, though each differs from the one
   * before only in colour.
   */
  @Test
  fun thePaintsInAndAfterAnOffscreenBitmapAreWhole() {
    val bytes = captureCanvasBytes {
      val bitmap = RemoteImageBitmap.createOffscreenRemoteBitmap(20, 20)
      drawRect(paint(Color.Red))
      drawRect(paint(Color.Green))
      drawToOffscreenBitmap(bitmap) { drawCircle(paint(Color.Blue), 10f.rf) }
      drawRect(paint(Color.Red))
      drawRect(paint(Color.Green))
    }
    // The second and the last differ from the paint before them, written just before: a colour.
    assertEquals(listOf(true, false, true, true, false), wholePaints(bytes))
  }

  /** For each paint written, whether it is as long as the document's first, which is whole. */
  private fun wholePaints(bytes: ByteArray): List<Boolean> {
    val paints = RcDocumentCodec.decode(bytes).operations.filterIsInstance<RcPaintData>()
    return paints.map { it.words.size == paints.first().words.size }
  }
}

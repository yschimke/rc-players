package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawCircle
import androidx.compose.remote.core.operations.DrawRect
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
}

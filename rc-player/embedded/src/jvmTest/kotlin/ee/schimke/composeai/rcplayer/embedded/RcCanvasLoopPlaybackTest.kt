package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.ri
import kotlin.test.Test

/**
 * A loop's body is drawn once per value of its index, and everything the body computes from the
 * index is computed again for each: alone, combined with a host value, combined with the canvas's
 * size, and through an integer index.
 */
class RcCanvasLoopPlaybackTest {
  private val red = RemotePaint { color = RemoteColor(Color.Red) }
  private val blue = RemotePaint { color = RemoteColor(Color.Blue) }

  @Test
  fun aLoopDrawsAtEachIndex() {
    val pixels =
      playCanvas(100, 20) {
        loop(0f.rf, 4f.rf, 1f.rf) { index ->
          drawCircle(red, 4f.rf, RemoteOffset(index * 20f + 10f, 10f.rf))
        }
      }
    for (i in 0 until 4) assertColor(Color.Red, pixels[10 + 20 * i, 10], "circle $i")
    for (i in 0 until 4) assertColor(Color.Transparent, pixels[20 + 20 * i, 10], "gap $i")
    assertColor(Color.Transparent, pixels[90, 10], "no fifth circle")
  }

  /** The index times a host value plus a share of the canvas's width, used twice in the body. */
  @Test
  fun aLoopBodyExpressionOverTheIndexAndOtherValuesIsEvaluatedPerIteration() {
    val pixels =
      playCanvas(100, 40) {
        val step = RemoteFloat.createNamedRemoteFloat("step", 20f)
        loop(0f.rf, 4f.rf, 1f.rf) { index ->
          val x = index * step + width * 0.1f
          drawCircle(red, 4f.rf, RemoteOffset(x, 10f.rf))
          drawCircle(blue, 4f.rf, RemoteOffset(x, 30f.rf))
        }
      }
    for (i in 0 until 4) {
      assertColor(Color.Red, pixels[10 + 20 * i, 10], "red circle $i")
      assertColor(Color.Blue, pixels[10 + 20 * i, 30], "blue circle $i")
      assertColor(Color.Transparent, pixels[20 + 20 * i, 10], "gap $i")
    }
  }

  @Test
  fun aLoopOverAnIntegerIndexDrawsAtEachIndex() {
    val pixels =
      playCanvas(100, 20) {
        remoteCanvas.loop(1, 4.ri) { index ->
          drawCircle(red, 4f.rf, RemoteOffset(index.toRemoteFloat() * 20f + 10f, 10f.rf))
        }
      }
    assertColor(Color.Transparent, pixels[10, 10], "the index starts at 1")
    for (i in 1 until 4) assertColor(Color.Red, pixels[10 + 20 * i, 10], "circle $i")
    assertColor(Color.Transparent, pixels[90, 10], "no circle at 4")
  }

  /** A loop in a loop: the inner body reads both indices. */
  @Test
  fun nestedLoopsDrawAGrid() {
    val pixels =
      playCanvas(60, 40) {
        loop(0f.rf, 2f.rf, 1f.rf) { row ->
          loop(0f.rf, 3f.rf, 1f.rf) { column ->
            drawCircle(red, 4f.rf, RemoteOffset(column * 20f + 10f, row * 20f + 10f))
          }
        }
      }
    for (row in 0 until 2) {
      for (column in 0 until 3) {
        assertColor(Color.Red, pixels[10 + 20 * column, 10 + 20 * row], "($column, $row)")
      }
    }
    assertColor(Color.Transparent, pixels[20, 20], "between the circles")
  }
}

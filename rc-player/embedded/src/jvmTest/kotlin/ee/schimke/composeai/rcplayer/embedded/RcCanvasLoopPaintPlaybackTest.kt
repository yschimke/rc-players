package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import kotlin.test.Test

/**
 * A loop's body is drawn once per value of its index, each time with the paint the player has then:
 * the body's last on every pass after the first, and the paint before the loop when it does not run
 * at all. So the canvas sends the whole paint for the first draw in a loop's body and for the first
 * after the loop, where the paint it last sent is not necessarily the player's.
 */
class RcCanvasLoopPaintPlaybackTest {
  private fun paint(color: Color) = RemotePaint { this.color = RemoteColor(color) }

  private fun RemoteDrawScope.cell(x: RemoteFloat, row: Int, color: Color) =
    drawRect(paint(color), RemoteOffset(x, (row * CELL).rf), RemoteSize(CELL.rf, CELL.rf))

  /**
   * Red; a loop of [times] passes that draws red in the top row (the paint before the loop, so
   * nothing to send for it) and blue in the bottom row; then blue (the paint the body sent last)
   * and red.
   */
  private fun aroundALoop(times: Float): RemoteDrawScope.() -> Unit = {
    val count = RemoteFloat.createNamedRemoteFloat("count", times)
    cell(0f.rf, 0, Color.Red)
    loop(0f.rf, count, 1f.rf) { index ->
      val x = index * CELL.toFloat() + CELL.toFloat()
      cell(x, 0, Color.Red)
      cell(x, 1, Color.Blue)
    }
    cell((4 * CELL).rf, 1, Color.Blue)
    cell((4 * CELL).rf, 0, Color.Red)
  }

  private fun play(onDraw: RemoteDrawScope.() -> Unit) =
    playDocument(captureCanvas(5 * CELL, 2 * CELL, onDraw), 5 * CELL, 2 * CELL)

  @Test
  fun theFirstDrawOfALoopBodyHasItsOwnPaintOnEveryPass() {
    val pixels = play(aroundALoop(3f)).toPixelMap()
    assertColor(Color.Red, pixels[MID, MID], "before the loop")
    for (pass in 0 until 3) {
      val x = (pass + 1) * CELL + MID
      assertColor(Color.Red, pixels[x, MID], "pass $pass, the first draw")
      assertColor(Color.Blue, pixels[x, CELL + MID], "pass $pass, the second draw")
    }
    assertColor(Color.Blue, pixels[4 * CELL + MID, CELL + MID], "after the loop")
    assertColor(Color.Red, pixels[4 * CELL + MID, MID], "the second draw after the loop")
  }

  @Test
  fun aDrawAfterALoopThatDidNotRunHasItsOwnPaint() {
    val pixels = play(aroundALoop(0f)).toPixelMap()
    assertColor(Color.Red, pixels[MID, MID], "before the loop")
    for (pass in 0 until 3) {
      val x = (pass + 1) * CELL + MID
      assertColor(Color.Transparent, pixels[x, MID], "no pass $pass")
      assertColor(Color.Transparent, pixels[x, CELL + MID], "no pass $pass")
    }
    assertColor(Color.Blue, pixels[4 * CELL + MID, CELL + MID], "after the loop")
    assertColor(Color.Red, pixels[4 * CELL + MID, MID], "the second draw after the loop")
  }

  /** Evidence for `renders/creation-loop-paint/`: three passes, and none. */
  @Test
  fun writeLoopPaintEvidence() {
    writeEvidence("loop-paint", listOf(play(aroundALoop(3f)), play(aroundALoop(0f))))
  }

  private companion object {
    const val CELL = 20
    const val MID = CELL / 2
  }
}

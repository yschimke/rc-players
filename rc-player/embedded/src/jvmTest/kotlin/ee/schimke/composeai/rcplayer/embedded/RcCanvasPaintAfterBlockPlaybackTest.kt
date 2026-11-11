package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test

/**
 * The paint a draw asks for is the paint it is drawn with, whatever a conditional block or an
 * offscreen bitmap before it did to the player's paint: the canvas sends the whole paint for the
 * first draw inside such a block and the first after it, because the paint it last sent is not
 * necessarily the one the player has there.
 */
class RcCanvasPaintAfterBlockPlaybackTest {
  private fun paint(color: Color) = RemotePaint { this.color = RemoteColor(color) }

  private fun RemoteDrawScope.cell(index: Int, color: Color) =
    drawRect(paint(color), RemoteOffset((index * CELL).rf, 0f.rf), RemoteSize(CELL.rf, CELL.rf))

  /**
   * Red, then a block that is skipped and would have drawn blue, then blue: the paint the canvas
   * last wrote is blue, the player's is still red.
   */
  private fun afterASkippedBlock(shown: Boolean): RemoteDrawScope.() -> Unit = {
    val flag = RemoteBoolean.createNamedRemoteBoolean("shown", shown)
    cell(0, Color.Red)
    drawConditionally(flag) { cell(1, Color.Blue) }
    cell(2, Color.Blue)
  }

  /** The same around an offscreen bitmap, which is then drawn. */
  private val aroundAnOffscreenBitmap: RemoteDrawScope.() -> Unit = {
    val bitmap = RemoteImageBitmap.createOffscreenRemoteBitmap(CELL, CELL)
    cell(0, Color.Red)
    drawToOffscreenBitmap(bitmap) {
      drawRect(paint(Color.Red), RemoteOffset.Zero, RemoteSize(CELL.rf, CELL.rf))
      drawCircle(paint(Color.Blue), 4f.rf, RemoteOffset((CELL / 2f).rf, (CELL / 2f).rf))
    }
    cell(2, Color.Blue)
    remoteCanvas.drawBitmap(bitmap, CELL.rf, 0f.rf, (2 * CELL).rf, CELL.rf, null)
  }

  private fun play(onDraw: RemoteDrawScope.() -> Unit) =
    playDocument(captureCanvas(3 * CELL, CELL, onDraw), 3 * CELL, CELL)

  @Test
  fun aDrawAfterASkippedConditionalBlockHasItsOwnPaint() {
    val pixels = play(afterASkippedBlock(shown = false)).toPixelMap()
    assertColor(Color.Red, pixels[MID, MID], "before the block")
    assertColor(Color.Transparent, pixels[CELL + MID, MID], "the skipped block")
    assertColor(Color.Blue, pixels[2 * CELL + MID, MID], "after the block")
  }

  @Test
  fun aDrawAfterAConditionalBlockThatRanHasItsOwnPaint() {
    val pixels = play(afterASkippedBlock(shown = true)).toPixelMap()
    assertColor(Color.Red, pixels[MID, MID], "before the block")
    assertColor(Color.Blue, pixels[CELL + MID, MID], "the block")
    assertColor(Color.Blue, pixels[2 * CELL + MID, MID], "after the block")
  }

  @Test
  fun drawsInAndAfterAnOffscreenBitmapHaveTheirOwnPaints() {
    val pixels = play(aroundAnOffscreenBitmap).toPixelMap()
    assertColor(Color.Red, pixels[MID, MID], "before the bitmap")
    assertColor(Color.Red, pixels[CELL + 2, 2], "the bitmap's background")
    assertColor(Color.Blue, pixels[CELL + MID, MID], "the bitmap's circle")
    assertColor(Color.Blue, pixels[2 * CELL + MID, MID], "after the bitmap")
  }

  /** Evidence for `renders/creation-canvas-paint/`: the skipped block, and the bitmap. */
  @Test
  fun writePaintAfterBlockEvidence() {
    writeEvidence(
      "paint-after-block",
      listOf(play(afterASkippedBlock(shown = false)), play(aroundAnOffscreenBitmap)),
    )
  }

  private companion object {
    const val CELL = 20
    const val MID = CELL / 2
  }
}

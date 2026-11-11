package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteTextMeasurer
import ee.schimke.composeai.rccreation.compose.layout.rememberRemoteTextMeasurer
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Measuring text does not change the paint a draw uses. A `RemoteTextMeasurer` measurement sets the
 * player's paint to the size and weight it measures at, where the canvas first needs its value;
 * text drawn with a 12 px paint at a position taken from a 20 px measurement is 12 px text, and so
 * is what is drawn after it with the same paint.
 */
class RcTextMeasurePaintPlaybackTest {
  private fun text(size: Float) = RemotePaint {
    color = RemoteColor(Color.Black)
    textSize = size.rf
  }

  private fun play(onDraw: RemoteDrawScope.(RemoteTextMeasurer) -> Unit): ImageBitmap {
    val bytes = runBlocking {
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(WIDTH, HEIGHT, 160)) {
        val measurer = rememberRemoteTextMeasurer()
        RemoteCanvas(RemoteModifier.fillMaxSize()) { onDraw(measurer) }
      }
    }
    return playDocument(bytes, WIDTH, HEIGHT)
  }

  /**
   * "HH" at 20 px in the top row; in the middle row "HHH" at 12 px, starting where a 20 px "HH"
   * ends; in the bottom row "HHH" with the same 12 px paint, for which the canvas has nothing to
   * send.
   */
  private val afterAMeasurement: RemoteDrawScope.(RemoteTextMeasurer) -> Unit = { measurer ->
    drawText("HH".rs, 0f.rf, BASELINE.rf, text(20f))
    val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
    drawText("HHH".rs, measured.size.width, (ROW + BASELINE).rf, text(12f))
    drawText("HHH".rs, 0f.rf, (2 * ROW + BASELINE).rf, text(12f))
  }

  /** The same three rows with no measurement: the middle row starts at the left edge. */
  private val withoutAMeasurement: RemoteDrawScope.(RemoteTextMeasurer) -> Unit = {
    drawText("HH".rs, 0f.rf, BASELINE.rf, text(20f))
    drawText("HHH".rs, 0f.rf, (ROW + BASELINE).rf, text(12f))
    drawText("HHH".rs, 0f.rf, (2 * ROW + BASELINE).rf, text(12f))
  }

  /**
   * The middle row's paint takes its own text size from a measurement: the height of a line of 10
   * px text, which is about 12 px. The paint is being worked out when the measurement is written.
   */
  private val aPaintSizedByAMeasurement: RemoteDrawScope.(RemoteTextMeasurer) -> Unit =
    { measurer ->
      drawText("HH".rs, 0f.rf, BASELINE.rf, text(20f))
      val measured = measurer.measure("HH".rs, fontSize = 10.rsp)
      val sized = RemotePaint {
        color = RemoteColor(Color.Black)
        textSize = measured.size.height
      }
      drawText("HHH".rs, 0f.rf, (ROW + BASELINE).rf, sized)
      drawText("HHH".rs, 0f.rf, (2 * ROW + BASELINE).rf, text(12f))
    }

  /** The box of the pixels drawn in [row]: left, top, right, bottom, each inclusive. */
  private fun ImageBitmap.ink(row: Int): IntArray {
    val pixels = toPixelMap()
    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = -1
    var bottom = -1
    for (y in row * ROW until (row + 1) * ROW) {
      for (x in 0 until width) {
        if (pixels[x, y].alpha > 0.5f) {
          left = minOf(left, x)
          top = minOf(top, y)
          right = maxOf(right, x)
          bottom = maxOf(bottom, y)
        }
      }
    }
    assertTrue(right >= 0, "nothing drawn in row $row")
    return intArrayOf(left, top, right, bottom)
  }

  private fun IntArray.height() = this[3] - this[1] + 1

  private fun IntArray.width() = this[2] - this[0] + 1

  @Test
  fun textDrawnAtAMeasuredPositionIsTheSizeItsPaintAsksFor() {
    val reference = play(withoutAMeasurement)
    val measured = play(afterAMeasurement)
    val large = reference.ink(0)
    val small = reference.ink(1)
    assertTrue(large.height() >= small.height() + 4, "20 px and 12 px text differ in height")
    val drawn = measured.ink(1)
    assertTrue(
      abs(drawn.height() - small.height()) <= 1 && abs(drawn.width() - small.width()) <= 1,
      "12 px text at a measured position is ${drawn.width()}x${drawn.height()}, " +
        "12 px text is ${small.width()}x${small.height()}, " +
        "20 px text is ${large.height()} high",
    )
    // It starts where the 20 px "HH" of the top row ends, plus the side bearings of the last glyph
    // of that and of the first of this; a 12 px "HH" ends well short of it.
    assertTrue(
      drawn[0] - large[2] in 0..8,
      "the text starts at ${drawn[0]}, a 20 px \"HH\" ends at ${large[2]}",
    )
  }

  @Test
  fun aDrawAfterAMeasurementWithTheSamePaintIsUnaffected() {
    val small = play(withoutAMeasurement).ink(2)
    val drawn = play(afterAMeasurement).ink(2)
    assertTrue(
      abs(drawn.height() - small.height()) <= 1 && abs(drawn.width() - small.width()) <= 1,
      "12 px text after a measurement is ${drawn.width()}x${drawn.height()}, " +
        "12 px text is ${small.width()}x${small.height()}",
    )
  }

  @Test
  fun aPaintWhoseTextSizeIsAMeasurementDrawsAtThatSize() {
    val reference = play(withoutAMeasurement)
    val drawn = play(aPaintSizedByAMeasurement)
    val small = reference.ink(1)
    val sized = drawn.ink(1)
    assertTrue(
      abs(sized.height() - small.height()) <= 1 && abs(sized.width() - small.width()) <= 4,
      "text sized by a measurement, about 12 px, is ${sized.width()}x${sized.height()}, " +
        "12 px text is ${small.width()}x${small.height()}",
    )
    val after = drawn.ink(2)
    val plain = reference.ink(2)
    assertTrue(
      abs(after.height() - plain.height()) <= 1 && abs(after.width() - plain.width()) <= 1,
      "12 px text after it is ${after.width()}x${after.height()}, " +
        "12 px text is ${plain.width()}x${plain.height()}",
    )
  }

  /** Evidence for `renders/creation-text-measure-paint/`: with the measurement, and without. */
  @Test
  fun writeTextMeasurePaintEvidence() {
    writeEvidence(
      "text-measure-paint",
      listOf(play(afterAMeasurement), play(withoutAMeasurement)),
    )
  }

  private companion object {
    const val WIDTH = 100
    const val ROW = 30
    const val HEIGHT = 3 * ROW
    const val BASELINE = 24
  }
}

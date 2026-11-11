package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawText
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.remote.core.operations.TextAttribute
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocumentInto
import ee.schimke.composeai.rccreation.compose.capture.wireValue
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteTextMeasurer
import ee.schimke.composeai.rccreation.compose.layout.rememberRemoteTextMeasurer
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A `RemoteTextMeasurer` measurement on a canvas, as AndroidX's own reader (`remote-core`
 * 1.0.0-alpha23) reads it and as its `CoreDocument` applies it when it paints a frame: the
 * measurement is made at the size it asks for, and each draw is made at the size its paint asks
 * for, the draw whose position is the measurement included.
 */
class CanvasTextMeasureReadBackTest {
  private fun text(size: Float) = RemotePaint {
    color = RemoteColor(Color.Black)
    textSize = size.rf
  }

  private fun capture(onDraw: RemoteDrawScope.(RemoteTextMeasurer) -> Unit): ByteArray =
    runBlocking {
      val display = RemoteCreationDisplayInfo(200, 100, 160)
      val writer =
        RemoteDocumentWriter(
          display.width,
          display.height,
          display.densityBehavior.wireValue,
          profiles = ProfileAndroidx or ProfileExperimental,
        )
      captureCommonRemoteDocumentInto(writer, display) {
        val measurer = rememberRemoteTextMeasurer()
        RemoteCanvas(RemoteModifier.fillMaxSize()) { onDraw(measurer) }
      }
      writer.encodeToByteArray()
    }

  /** The text size AndroidX had set at each measurement and each text it drew, in order. */
  private fun sizes(bytes: ByteArray): List<String> =
    androidxPaints(bytes, 200, 100)
      .filter { it.name == "textBounds" || it.name == "textRun" }
      .map { "${it.name}@${it.textSize.toInt()}" }

  /** The paints, measurements and texts of the document, in the order written. */
  private fun order(bytes: ByteArray): List<String> =
    androidxOperations(bytes).flattened().mapNotNull {
      when (it) {
        is PaintData -> "paint"
        is TextAttribute -> "measure"
        is DrawText -> "text"
        else -> null
      }
    }

  /** Text drawn with a 12 px paint at a position taken from a 20 px measurement, then again. */
  @Test
  fun aMeasurementDoesNotChangeThePaintOfTheDrawThatReadsIt() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      drawText("HHH".rs, measured.size.width, 20f.rf, text(12f))
      drawText("HHH".rs, 0f.rf, 40f.rf, text(12f))
    }
    assertEquals(listOf("textBounds@20", "textRun@12", "textRun@12"), sizes(bytes))
    // The draw's paint, the measurement with its own, the draw's paint again, and the two texts:
    // the second has the paint of the first.
    assertEquals(listOf("paint", "paint", "measure", "paint", "text", "text"), order(bytes))
  }

  /** A draw that sets no paint of its own keeps the one `usePaint` set before the measurement. */
  @Test
  fun aMeasurementDoesNotChangeThePaintADrawInherits() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      usePaint(text(12f)) { drawText("HHH".rs, measured.size.width, 20f.rf, null) }
    }
    assertEquals(listOf("textBounds@20", "textRun@12"), sizes(bytes))
  }

  /**
   * Two attributes of one measurement, the second first read in a block that may not run: each is
   * measured at 20 px, and every draw is at its own paint's size whether the block runs or not.
   */
  @Test
  fun measurementsInAndAroundAConditionalBlock() {
    fun bytes(shown: Boolean) = capture { measurer ->
      val flag = RemoteBoolean.createNamedRemoteBoolean("shown", shown)
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      drawText("a".rs, measured.size.width, 20f.rf, text(12f))
      drawConditionally(flag) { drawText("b".rs, measured.size.height, 40f.rf, text(12f)) }
      drawText("c".rs, 0f.rf, 60f.rf, text(12f))
    }
    assertEquals(
      listOf("textBounds@20", "textRun@12", "textBounds@20", "textRun@12", "textRun@12"),
      sizes(bytes(true)),
    )
    assertEquals(listOf("textBounds@20", "textRun@12", "textRun@12"), sizes(bytes(false)))
  }
}

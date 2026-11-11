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
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteTextMeasurer
import ee.schimke.composeai.rccreation.compose.layout.rememberRemoteTextMeasurer
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rdp
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

  /**
   * A paint whose own text size is a measurement: the measurement is written while the paint is
   * being worked out, so there is no paint to send again yet. The paint is worked out once more
   * when the measurement is written, and sent whole.
   */
  @Test
  fun aPaintWhoseTextSizeIsAMeasurement() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      // The recording context measures every text as 0 wide.
      val sized = RemotePaint {
        color = RemoteColor(Color.Black)
        textSize = measured.size.width + 14f
      }
      drawText("a".rs, 0f.rf, 20f.rf, sized)
      drawText("b".rs, 0f.rf, 40f.rf, text(12f))
    }
    assertEquals(listOf("textBounds@20", "textRun@14", "textRun@12"), sizes(bytes))
    assertEquals(listOf("paint", "measure", "paint", "text", "paint", "text"), order(bytes))
  }

  /** The same for a stroke width and for a colour: any property of the paint being applied. */
  @Test
  fun aPaintWhoseStrokeWidthOrColourIsAMeasurement() {
    val stroked = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      val paint = text(12f).apply { strokeWidth = measured.size.height + 3f }
      drawText("a".rs, 0f.rf, 20f.rf, paint)
      drawText("b".rs, 0f.rf, 40f.rf, text(12f))
    }
    assertEquals(listOf("textBounds@20", "textRun@12", "textRun@12"), sizes(stroked))
    assertEquals(listOf("paint", "measure", "paint", "text", "paint", "text"), order(stroked))
    val coloured = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      val paint = text(12f).apply { color = RemoteColor.rgb(measured.size.width, 0f.rf, 1f.rf) }
      drawText("a".rs, 0f.rf, 20f.rf, paint)
      drawText("b".rs, 0f.rf, 40f.rf, paint)
    }
    assertEquals(listOf("textBounds@20", "textRun@12", "textRun@12"), sizes(coloured))
    assertEquals(listOf("paint", "measure", "paint", "text", "text"), order(coloured))
  }

  /** The paint of `usePaint` itself is a measurement, and the draw under it reads another. */
  @Test
  fun usePaintWithAMeasuredPaintAndAMeasuredDraw() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      val sized = RemotePaint { textSize = measured.size.width + 14f }
      usePaint(sized) { drawText("a".rs, measured.size.height, 20f.rf, null) }
    }
    assertEquals(listOf("textBounds@20", "textBounds@20", "textRun@14"), sizes(bytes))
    assertEquals(
      listOf("paint", "measure", "paint", "paint", "measure", "paint", "text"),
      order(bytes),
    )
  }

  /**
   * A measurement first read by a transform, between `usePaint` and a draw that inherits its paint:
   * no draw is being written, so the paint is sent again when the draw is.
   */
  @Test
  fun aMeasurementReadByATransformBeforeADrawThatInheritsItsPaint() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      usePaint(text(12f)) {
        translate(measured.size.width, 0f.rf) { drawText("a".rs, 0f.rf, 20f.rf, null) }
      }
      drawText("b".rs, 0f.rf, 40f.rf, text(12f))
    }
    assertEquals(listOf("textBounds@20", "textRun@12", "textRun@12"), sizes(bytes))
    assertEquals(listOf("paint", "paint", "measure", "paint", "text", "text"), order(bytes))
  }

  /**
   * An expression of a measurement that two draws read is written once, ahead of both and outside
   * either: the first paint after it is whole.
   */
  @Test
  fun aMeasurementHoistedAheadOfTheDrawsThatShareIt() {
    val bytes = capture { measurer ->
      val x = measurer.measure("HH".rs, fontSize = 20.rsp).size.width + 4f
      drawText("a".rs, x, 20f.rf, text(12f))
      drawText("b".rs, x, 40f.rf, text(12f))
    }
    assertEquals(listOf("textBounds@20", "textRun@12", "textRun@12"), sizes(bytes))
    // It is written ahead of the layout, so the canvas's content begins after it: the paint after
    // the first is whole too, the writer's own flag for that being still set.
    assertEquals(listOf("paint", "measure", "paint", "text", "paint", "text"), order(bytes))
  }

  /** A measurement whose own font size is another measurement: each is made at its own size. */
  @Test
  fun aMeasurementOfAMeasurement() {
    val bytes = capture { measurer ->
      val inner = measurer.measure("HH".rs, fontSize = 20.rsp)
      val outer = measurer.measure("HHH".rs, fontSize = (inner.size.width + 30f).rsp)
      drawText("a".rs, outer.size.width, 20f.rf, text(12f))
      drawText("b".rs, 0f.rf, 40f.rf, text(12f))
    }
    assertEquals(
      listOf("textBounds@20", "textBounds@30", "textRun@12", "textRun@12"),
      sizes(bytes),
    )
  }

  /** In a loop's body the measurement is made, and the paint sent again, on every pass. */
  @Test
  fun aMeasurementInALoopBody() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      drawText("a".rs, 0f.rf, 20f.rf, text(12f))
      loop(0f.rf, 2f.rf, 1f.rf) { index ->
        drawText("b".rs, measured.size.width + index, 40f.rf, text(12f))
        drawText("c".rs, 0f.rf, 60f.rf, text(12f))
      }
      drawText("d".rs, 0f.rf, 80f.rf, text(12f))
    }
    assertEquals(
      listOf("textRun@12") +
        List(2) { listOf("textBounds@20", "textRun@12", "textRun@12") }.flatten() +
        listOf("textRun@12"),
      sizes(bytes),
    )
  }

  /** In a pattern's body: each call measures and draws at its own sizes, whatever precedes it. */
  @Test
  fun aMeasurementInAPatternBody() {
    val bytes = capture { measurer ->
      val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
      val label = remoteCanvas.definePattern {
        drawText("a".rs, measured.size.width, 20f.rf, text(12f))
        drawText("b".rs, 0f.rf, 40f.rf, text(12f))
      }
      label()
      drawText("c".rs, 0f.rf, 60f.rf, text(30f))
      label()
      drawText("d".rs, 0f.rf, 80f.rf, text(30f))
    }
    val call = listOf("textBounds@20", "textRun@12", "textRun@12")
    assertEquals(call + "textRun@30" + call + "textRun@30", sizes(bytes))
  }

  /**
   * A measurement first read by a layout modifier is not in a draw, and a paint of the canvas's has
   * no place where it is written: nothing is sent again there, and each canvas starts with a whole
   * paint of its own.
   */
  @Test
  fun aMeasurementReadByALayoutModifier() {
    val bytes = runBlocking {
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
        val measured = measurer.measure("HH".rs, fontSize = 20.rsp)
        RemoteColumn {
          RemoteCanvas(RemoteModifier.size(40.rdp)) { drawText("a".rs, 0f.rf, 20f.rf, text(30f)) }
          RemoteCanvas(RemoteModifier.width(measured.size.width + 40f).height(40.rdp)) {
            drawText("b".rs, measured.size.width, 20f.rf, text(12f))
          }
        }
      }
      writer.encodeToByteArray()
    }
    assertEquals(listOf("textBounds@20", "textRun@30", "textRun@12"), sizes(bytes))
    assertEquals(listOf("paint", "measure", "paint", "text", "paint", "text"), order(bytes))
  }
}

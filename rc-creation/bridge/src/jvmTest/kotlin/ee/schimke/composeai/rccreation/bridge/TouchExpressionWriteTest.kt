package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.TextMerge
import androidx.compose.remote.core.operations.TouchExpression
import androidx.compose.remote.creation.common.AnimatedFloatExpression
import androidx.compose.remote.creation.common.RemoteContext
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.TouchStop
import androidx.compose.remote.creation.common.Utils
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * The fork's free-standing `TouchExpression` and `TextMerge` into a reserved id, as AndroidX's
 * `RemoteComposeBuffer.addTouchExpression` / `textMerge(id, left, right)` write them: a slider's
 * value follows the drag, and its label follows the value.
 */
class TouchExpressionWriteTest {
  private class Written(val bytes: ByteArray, val valueId: Int, val labelId: Int)

  private fun slider(): Written {
    val writer = RemoteDocumentWriter(200, 100)
    val valueId = Utils.idFromNan(writer.reserveFloatVariable())
    val labelId = Utils.idFromNan(writer.reserveFloatVariable())
    val unitsPerPx = writer.floatExpression(0.5f)
    writer.addTouchExpression(
      id = valueId,
      value = 20f,
      min = 0f,
      max = 100f,
      velocityId = 0f,
      touchEffects = 0,
      expression =
        floatArrayOf(RemoteContext.FLOAT_TOUCH_POS_X, unitsPerPx, AnimatedFloatExpression.MUL),
      stopMode = TouchStop.ENDS,
    )
    writer.textMerge(
      labelId,
      writer.createTextFromFloat(Utils.asNan(valueId), 3, 0, 0),
      writer.addText(""),
    )
    writer.root {}
    return Written(writer.encodeToByteArray(), valueId, labelId)
  }

  @Test
  fun theTouchExpressionDecodesWithEachField() {
    val written = slider()
    val operations = RcDocumentCodec.decode(written.bytes).operations
    val touch = operations.filterIsInstance<RcTouchExpression>().single()
    assertEquals(written.valueId, touch.id)
    assertEquals(20f, touch.defaultValue.value)
    assertEquals(0f, touch.min.value)
    assertEquals(100f, touch.max.value)
    assertEquals(RcTouchExpression.STOP_ENDS, touch.stopMode)
    assertEquals(emptyList(), touch.stopSpec)
    assertEquals(emptyList(), touch.easingSpec)
    assertEquals(3, touch.expression.size)
    assertEquals(13, touch.expression[0].referencedId)
    val merge = operations.filterIsInstance<RcTextMerge>().single()
    assertEquals(written.labelId, merge.outId)
  }

  @Test
  fun androidxRemoteCoreReadsTheTouchAndTheMerge() {
    val document = CoreDocument()
    document.initFromBuffer(
      RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(slider().bytes)),
    )
    assertNotNull(document.operations.filterIsInstance<TouchExpression>().singleOrNull())
    assertNotNull(document.operations.filterIsInstance<TextMerge>().singleOrNull())
  }

  @Test
  fun aStopSpecAndAnEasingAreWrittenAfterTheirCounts() {
    val writer = RemoteDocumentWriter(200, 100)
    val id = Utils.idFromNan(writer.reserveFloatVariable())
    writer.addTouchExpression(
      id,
      0f,
      0f,
      10f,
      0f,
      0,
      floatArrayOf(RemoteContext.FLOAT_TOUCH_POS_X),
      TouchStop.NOTCHES_EVEN,
      stopSpec = floatArrayOf(5f, 10f),
      easingSpec = floatArrayOf(1f),
    )
    writer.root {}
    val touch =
      RcDocumentCodec.decode(writer.encodeToByteArray())
        .operations
        .filterIsInstance<RcTouchExpression>()
        .single()
    assertEquals(RcTouchExpression.STOP_NOTCHES_EVEN, touch.stopMode)
    assertEquals(listOf(5f, 10f), touch.stopSpec.map { it.value })
    assertEquals(listOf(1f), touch.easingSpec.map { it.value })
  }

  @Test
  fun anOverlongExpressionIsRefused() {
    val writer = RemoteDocumentWriter(200, 100)
    assertFailsWith<IllegalArgumentException> {
      writer.addTouchExpression(1, 0f, 0f, 1f, 0f, 0, FloatArray(33), TouchStop.GENTLY)
    }
  }
}

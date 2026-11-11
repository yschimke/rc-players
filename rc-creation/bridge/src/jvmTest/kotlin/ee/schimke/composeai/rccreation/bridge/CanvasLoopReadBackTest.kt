package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawCircle
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.layout.LoopOperation
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A canvas loop as AndroidX's own reader (`remote-core` 1.0.0-alpha23) reads it, and as its
 * `CoreDocument` runs it when it paints a frame: what the body computes from the index is in the
 * loop, where AndroidX computes it again for each value of the index. An expression outside the
 * loop is computed once a frame, whatever it reads.
 */
class CanvasLoopReadBackTest {
  private val paint
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  private fun centres(bytes: ByteArray): List<Float> =
    androidxPaints(bytes, 200, 100).filter { it.name == "circle" }.map { it.arguments[0] }

  @Test
  fun aLoopDrawsAtEachIndex() {
    val bytes = captureCanvasBytes {
      loop(0f.rf, 4f.rf, 1f.rf) { index ->
        drawCircle(paint, 4f.rf, RemoteOffset(index * 20f + 10f, 10f.rf))
      }
    }
    assertEquals(listOf(10f, 30f, 50f, 70f), centres(bytes))
    val operations = androidxOperations(bytes)
    val loop = operations.only<LoopOperation>()
    assertEquals(0f, loop.field<Float>("mFrom"))
    assertEquals(1f, loop.field<Float>("mStep"))
    assertEquals(4f, loop.field<Float>("mUntil"))
    // The circle and the expression of the index it reads are the loop's own operations.
    val body = loop.children()
    assertEquals(1, body.filterIsInstance<DrawCircle>().size)
    val index = loop.field<Int>("mIndexVariableId")
    assertTrue(
      body.filterIsInstance<FloatExpression>().any { expression ->
        expression.mSrcValue.any { it.isNaN() && (it.toRawBits() and 0x3fffff) == index }
      },
      "no expression of the index in the loop: $body",
    )
  }

  /** The index with two host values, read twice in the body. */
  @Test
  fun anExpressionOfTheIndexAndOtherValuesIsEvaluatedPerIteration() {
    val bytes = captureCanvasBytes {
      val step = RemoteFloat.createNamedRemoteFloat("step", 20f)
      val origin = RemoteFloat.createNamedRemoteFloat("origin", 10f)
      loop(0f.rf, 3f.rf, 1f.rf) { index ->
        val x = index * step + origin * 2f
        drawCircle(paint, 4f.rf, RemoteOffset(x, 10f.rf))
        drawCircle(paint, 4f.rf, RemoteOffset(x, 30f.rf))
      }
    }
    assertEquals(listOf(20f, 20f, 40f, 40f, 60f, 60f), centres(bytes))
    // What does not read the index is still declared once, ahead of the layout.
    val operations = androidxOperations(bytes)
    val loop = operations.only<LoopOperation>()
    val index = loop.field<Int>("mIndexVariableId")
    loop.children().filterIsInstance<FloatExpression>().forEach { expression ->
      val reads = expression.mSrcValue.filter { it.isNaN() }.map { it.toRawBits() and 0x3fffff }
      val inLoop = loop.children().filterIsInstance<FloatExpression>().map { it.mId }
      assertTrue(index in reads || reads.any { it in inLoop }, "in the loop for no reason: $reads")
    }
  }

  @Test
  fun aLoopOverAnIntegerIndexDrawsAtEachIndex() {
    val bytes = captureCanvasBytes {
      remoteCanvas.loop(1, 4.ri) { index ->
        drawCircle(paint, 4f.rf, RemoteOffset(index.toRemoteFloat() * 20f + 10f, 10f.rf))
      }
    }
    assertEquals(listOf(30f, 50f, 70f), centres(bytes))
    val loop = androidxOperations(bytes).only<LoopOperation>()
    assertEquals(1f, loop.field<Float>("mFrom"))
    assertEquals(1f, loop.field<Float>("mStep"))
  }

  @Test
  fun nestedLoopsReadBothIndices() {
    val bytes = captureCanvasBytes {
      loop(0f.rf, 2f.rf, 1f.rf) { row ->
        loop(0f.rf, 3f.rf, 1f.rf) { column ->
          drawCircle(paint, 4f.rf, RemoteOffset(column * 20f + row * 100f, 10f.rf))
        }
      }
    }
    assertEquals(listOf(0f, 20f, 40f, 100f, 120f, 140f), centres(bytes))
  }
}

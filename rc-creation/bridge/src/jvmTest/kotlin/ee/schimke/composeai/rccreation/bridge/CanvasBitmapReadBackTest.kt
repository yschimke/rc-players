package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawBitmap
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.rf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A bitmap a canvas draws, as AndroidX's own reader (`remote-core` 1.0.0-alpha23) reads it: no
 * content description, and for `drawBitmap(bitmap, left, top)` a rectangle of the bitmap's size.
 */
class CanvasBitmapReadBackTest {
  /** No description is id 0. Nothing declares an empty text to stand for one. */
  @Test
  fun aBitmapDrawnOnACanvasHasNoContentDescription() {
    val bytes = captureCanvasBytes {
      val bitmap = RemoteImageBitmap.createOffscreenRemoteBitmap(8, 8)
      drawImage(bitmap, RemoteOffset(20f.rf, 10f.rf), null)
      remoteCanvas.drawBitmap(bitmap, 1f.rf, 2f.rf, 30f.rf, 40f.rf, null)
    }
    val operations = androidxOperations(bytes)
    assertEquals(listOf(0, 0), operations.all<DrawBitmap>().map { it.field<Int>("mDescriptionId") })
    assertTrue(operations.all<TextData>().none { it.mText.isEmpty() })
  }

  /** The rectangle's right and bottom are the left and top plus the bitmap's width and height. */
  @Test
  fun aBitmapDrawnAtAPointHasARectangleOfItsSize() {
    val bytes = captureCanvasBytes {
      val bitmap = RemoteImageBitmap.createOffscreenRemoteBitmap(8, 8)
      drawImage(bitmap, RemoteOffset(20f.rf, 10f.rf), null)
    }
    val operations = androidxOperations(bytes)
    val draw = operations.only<DrawBitmap>()
    assertEquals(20f, draw.field<Float>("mLeft"))
    assertEquals(10f, draw.field<Float>("mTop"))
    val expressions = operations.all<FloatExpression>().associateBy { it.mId }
    fun literals(field: String): List<Float> {
      val value = draw.field<Float>(field)
      assertTrue(value.isNaN(), "$field is an expression of the bitmap's size")
      return expressions.getValue(value.toRawBits() and 0x3fffff).mSrcValue.filter { !it.isNaN() }
    }
    assertEquals(listOf(20f), literals("mRight"))
    assertEquals(listOf(10f), literals("mBottom"))
  }
}

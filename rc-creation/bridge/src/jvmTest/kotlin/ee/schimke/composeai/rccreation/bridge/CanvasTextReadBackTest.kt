package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawText
import androidx.compose.remote.core.operations.DrawTextOnPath
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.creation.compose.path.RemotePath
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Text a canvas is given as a `String`, as AndroidX's own reader (`remote-core` 1.0.0-alpha23)
 * reads it: the string as a text, and the run from 0 to its length, as AndroidX's canvas writes it,
 * where a `RemoteString` is drawn to its end (-1).
 */
class CanvasTextReadBackTest {
  private val paint
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  private fun List<androidx.compose.remote.core.Operation>.textOf(id: Int): String =
    all<TextData>().single { it.mTextId == id }.mText

  @Test
  fun drawTextOfAStringRunsFromZeroToItsLength() {
    val bytes = captureCanvasBytes { remoteCanvas.drawText("literal", 5f.rf, 6f.rf, paint) }
    val operations = androidxOperations(bytes)
    val run = operations.only<DrawText>()
    assertEquals("literal", operations.textOf(run.mTextID))
    assertEquals(
      listOf(0, 7, 0, 7),
      listOf(run.mStart, run.mEnd, run.mContextStart, run.mContextEnd),
    )
    assertEquals(5f, run.field<Float>("mX"))
    assertEquals(6f, run.field<Float>("mY"))
    assertEquals(false, run.mRtl)
  }

  @Test
  fun drawTextOfARemoteStringRunsToItsEnd() {
    val bytes = captureCanvasBytes { remoteCanvas.drawText("literal".rs, 5f.rf, 6f.rf, paint) }
    val operations = androidxOperations(bytes)
    val run = operations.only<DrawText>()
    assertEquals("literal", operations.textOf(run.mTextID))
    assertEquals(
      listOf(0, -1, 0, -1),
      listOf(run.mStart, run.mEnd, run.mContextStart, run.mContextEnd),
    )
  }

  @Test
  fun drawTextRunOfACharSequenceKeepsItsRange() {
    val bytes = captureCanvasBytes {
      remoteCanvas.drawTextRun(StringBuilder("run text"), 1, 3, 0, 8, 1f, 2f, true, paint)
    }
    val operations = androidxOperations(bytes)
    val run = operations.only<DrawText>()
    assertEquals("run text", operations.textOf(run.mTextID))
    assertEquals(
      listOf(1, 3, 0, 8),
      listOf(run.mStart, run.mEnd, run.mContextStart, run.mContextEnd),
    )
    assertEquals(1f, run.field<Float>("mX"))
    assertEquals(2f, run.field<Float>("mY"))
    assertEquals(true, run.mRtl)
  }

  /** The same text given twice is one text, whichever way it is given. */
  @Test
  fun aStringGivenTwiceIsOneText() {
    val path =
      RemotePath().apply {
        moveTo(0f, 0f)
        lineTo(50f, 0f)
      }
    val bytes = captureCanvasBytes {
      remoteCanvas.drawText("same", 5f.rf, 6f.rf, paint)
      remoteCanvas.drawTextOnPath("same", path, 1f.rf, 2f.rf, paint)
      remoteCanvas.drawText("same".rs, 7f.rf, 8f.rf, paint)
    }
    val operations = androidxOperations(bytes)
    assertEquals(1, operations.all<TextData>().count { it.mText == "same" })
    val onPath = operations.only<DrawTextOnPath>()
    assertEquals("same", operations.textOf(onPath.field<Int>("mTextId")))
    assertEquals(1f, onPath.field<Float>("mHOffset"))
    assertEquals(2f, onPath.field<Float>("mVOffset"))
    assertEquals(
      setOf(onPath.field<Int>("mTextId")),
      operations.all<DrawText>().map { it.mTextID }.toSet(),
    )
  }
}

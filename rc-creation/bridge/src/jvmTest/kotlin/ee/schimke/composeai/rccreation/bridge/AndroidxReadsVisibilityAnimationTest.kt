package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.BitmapData
import androidx.compose.remote.core.operations.DrawContent
import androidx.compose.remote.core.operations.DrawToBitmap
import androidx.compose.remote.core.operations.FloatFunctionDefine
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** AndroidX remote-core 1.0.0-alpha22 reads a visibility animation the fork writes. */
class AndroidxReadsVisibilityAnimationTest {
  private fun read(profiles: Int): List<Operation> {
    val writer = RemoteDocumentWriter(100, 100, profiles = profiles)
    writer.defineVisibilityAnimation { component, _, _, _, _, _ ->
      val buffer = createOffscreenBitmap(component)
      drawComponentToBitmap(component, buffer)
      drawComponentContent()
    }
    writer.root {}
    val document = CoreDocument()
    document.initFromBuffer(
      RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(writer.encodeToByteArray()))
    )
    return document.operations
  }

  private fun functionBody(operations: List<Operation>): List<String> =
    operations.filterIsInstance<FloatFunctionDefine>().single().list.map {
      when (it) {
        is BitmapData -> "offscreen ${it.encoding}"
        is DrawToBitmap -> "draw to bitmap"
        is DrawContent -> "content"
        else -> it::class.java.simpleName
      }
    }

  /**
   * The fork's documents declare no profile by default; one that draws to a bitmap names AndroidX's
   * itself, as remote-core reads `DrawToBitmap` under no other profile it would declare.
   */
  @Test
  fun aDefaultProfileDocumentReadsTheWholeFunction() {
    assertEquals(ExpectedBody, functionBody(read(profiles = 0)))
  }

  @Test
  fun anAndroidxProfileDocumentReadsTheWholeFunction() {
    assertEquals(ExpectedBody, functionBody(read(profiles = ProfileAndroidx)))
  }

  private companion object {
    val ExpectedBody =
      listOf(
        "offscreen ${BitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER}",
        "draw to bitmap",
        "content",
        "draw to bitmap",
        "content",
      )

    /** `RcProfiles.PROFILE_ANDROIDX`, which `DrawToBitmap` needs. */
    const val ProfileAndroidx = 0x200
  }
}

package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocumentInto
import androidx.compose.remote.creation.compose.capture.wireValue
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import kotlinx.coroutines.runBlocking

/**
 * A document that is one canvas of [width] x [height] pixels (density 1) drawing [onDraw], under
 * the AndroidX and experimental profiles, which AndroidX's reader needs for some of what a canvas
 * writes (an offscreen bitmap, a pattern).
 */
internal fun captureCanvasBytes(
  width: Int = 200,
  height: Int = 100,
  onDraw: RemoteDrawScope.() -> Unit,
): ByteArray = runBlocking {
  val display = RemoteCreationDisplayInfo(width, height, 160)
  val writer =
    RemoteDocumentWriter(
      display.width,
      display.height,
      display.densityBehavior.wireValue,
      profiles = ProfileAndroidx or ProfileExperimental,
    )
  captureCommonRemoteDocumentInto(writer, display) {
    RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
  }
  writer.encodeToByteArray()
}

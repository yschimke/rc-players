package ee.schimke.composeai.rccreation.bridge

import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocumentInto
import ee.schimke.composeai.rccreation.compose.capture.wireValue
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
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

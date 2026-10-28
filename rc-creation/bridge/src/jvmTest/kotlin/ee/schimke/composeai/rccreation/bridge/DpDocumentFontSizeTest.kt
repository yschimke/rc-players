package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensityBehavior
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A DP document writes a literal font size in dp, which remote-core scales by the host density, as
 * it does padding; other documents keep the capture's pixels.
 */
class DpDocumentFontSizeTest {
  private fun fontSize(densityBehavior: RemoteDensityBehavior): Float = runBlocking {
    val bytes =
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(360, 480, 320, densityBehavior = densityBehavior)
      ) {
        RemoteText("Hello".rs, fontSize = 14.rsp, overflow = RemoteTextOverflow.Clip)
      }
    val text = RcDocumentCodec.decode(bytes).operations.filterIsInstance<RcCoreText>().single()
    val size =
      text.properties.filterIsInstance<RcTextStyleProperty.FloatValue>().last { it.id == 5 }.value
    assertEquals(false, size.isNaNEncoded)
    size.value
  }

  @Test
  fun aDpDocumentWritesItsFontSizeInDp() {
    assertEquals(14f, fontSize(RemoteDensityBehavior.Dp))
  }

  @Test
  fun aLegacyDocumentWritesItsFontSizeInPixels() {
    assertEquals(28f, fontSize(RemoteDensityBehavior.Legacy))
  }
}

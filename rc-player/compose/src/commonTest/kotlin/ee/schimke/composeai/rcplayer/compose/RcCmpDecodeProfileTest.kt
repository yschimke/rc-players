package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcSkip
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter
import kotlin.test.Test
import kotlin.test.assertTrue

class RcCmpDecodeProfileTest {
  @Test
  fun cmpHostsDecodeSkipWithTheAndroidxExperimentalProfileBit() {
    val skippedBody =
      RcWireWriter()
        .also { RcDocumentCodec.encodeOperation(it, RcTheme(RcTheme.DARK)) }
        .toByteArray()
    val bytes =
      RcWireWriter()
        .apply {
          RcDocumentCodec.encodeOperation(this, RcHeader(RcVersion(1, 0, 0), modern = false))
          RcDocumentCodec.encodeOperation(
            this,
            RcSkip(RcSkip.IF_PROFILE_INCLUDES, 0x1, skippedBody.size),
          )
          writeRawBytes(skippedBody)
        }
        .toByteArray()

    assertTrue(decodeCmpDocument(bytes).operations.isEmpty())
  }
}

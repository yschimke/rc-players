package ee.schimke.composeai.rcplayer.compose

import kotlin.test.Test

class RcAppleSoundHostTest {
  @Test
  fun missingResourcesAndRepeatedDisposalNeedNoAudioDevice() {
    val host = RcAppleSoundHost()

    host.playSound(404)
    host.loadSound(7, byteArrayOf())
    host.dispose()
    host.dispose()
    host.playSound(7)
  }
}

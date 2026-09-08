package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickType
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcSoundEffect
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RcSoundHostTest {
  @Test
  fun playSoundIsAcceptedAsAnIosAndMacosAction() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcMultiClickModifier(RcMultiClickType.SINGLE),
          RcPlaySound(7),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    assertEquals(
      emptyList(),
      document.composeSupportReport(RcOperationProfiles.CMP_IOS_ALPHA18).issues,
    )
    assertEquals(
      emptyList(),
      document.composeSupportReport(RcOperationProfiles.CMP_MACOS_ALPHA18).issues,
    )
  }

  @Test
  fun hostBridgeForwardsLoadsAndPlayback() {
    val calls = mutableListOf<String>()
    val bytes = byteArrayOf(1, 2, 3)
    val host =
      object : RcSoundHost {
        override fun loadSound(soundId: Int, data: ByteArray) {
          calls += "load:$soundId"
          assertContentEquals(bytes, data)
        }

        override fun playSound(soundId: Int) {
          calls += "play:$soundId"
        }
      }

    host.dispatchSound(RcSoundEffect.Load(7, bytes))
    host.dispatchSound(RcSoundEffect.Play(7))

    assertEquals(listOf("load:7", "play:7"), calls)
  }

  @Test
  fun replacementHostReceivesPreparedResourcesBeforePlayback() {
    val firstCalls = mutableListOf<String>()
    val secondCalls = mutableListOf<String>()
    val first = recordingHost(firstCalls)
    val second = recordingHost(secondCalls)
    val dispatcher = RcSoundHostDispatcher(first)

    dispatcher.dispatch(RcSoundEffect.Load(7, byteArrayOf(1, 2, 3)))
    dispatcher.updateHost(second)
    dispatcher.dispatch(RcSoundEffect.Play(7))

    assertEquals(listOf("load:7"), firstCalls)
    assertEquals(listOf("load:7", "play:7"), secondCalls)
  }

  @Test
  fun absentOrFailingAudioBackendsCannotFailRendering() {
    RcSoundHost.None.dispatchSound(RcSoundEffect.Play(7))
    val failing =
      object : RcSoundHost {
        override fun loadSound(soundId: Int, data: ByteArray): Unit = error("no audio device")

        override fun playSound(soundId: Int): Unit = error("audio session unavailable")
      }

    failing.dispatchSound(RcSoundEffect.Load(7, byteArrayOf()))
    failing.dispatchSound(RcSoundEffect.Play(7))
  }

  @Test
  fun scDataIsConvertedToAStandardWavWithoutChangingPcm() {
    val sc =
      byteArrayOf(
        'S'.code.toByte(),
        'C'.code.toByte(),
        1,
        16,
        1,
        0x56,
        0x22,
        0,
        0,
        0,
        2,
        1,
        2,
        3,
        4,
      )

    val wav = requireNotNull(normalizeSoundData(sc))

    assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
    assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
    assertContentEquals(byteArrayOf(1, 2, 3, 4), wav.copyOfRange(44, 48))
  }

  private fun recordingHost(calls: MutableList<String>): RcSoundHost =
    object : RcSoundHost {
      override fun loadSound(soundId: Int, data: ByteArray) {
        calls += "load:$soundId"
      }

      override fun playSound(soundId: Int) {
        calls += "play:$soundId"
      }
    }
}

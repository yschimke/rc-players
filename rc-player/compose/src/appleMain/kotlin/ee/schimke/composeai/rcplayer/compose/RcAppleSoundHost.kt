@file:OptIn(
  kotlinx.cinterop.BetaInteropApi::class,
  kotlinx.cinterop.ExperimentalForeignApi::class,
)

package ee.schimke.composeai.rcplayer.compose

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * AVFoundation sound-effect backend for native iOS and macOS CMP hosts.
 *
 * This class deliberately does not configure `AVAudioSession`: category, mixing, interruption, and
 * route policy belong to the embedding application. Calls are expected on the Compose/UI thread.
 */
public class RcAppleSoundHost : RcSoundHost {
  private val players = mutableMapOf<Int, AVAudioPlayer>()
  private var disposed = false

  override fun loadSound(soundId: Int, data: ByteArray) {
    if (disposed) return
    val wav = normalizeSoundData(data) ?: return
    val nativeData = wav.toNSData()
    val player =
      runCatching { AVAudioPlayer(data = nativeData, error = null) }.getOrNull() ?: return
    if (!player.prepareToPlay()) return
    players.put(soundId, player)?.stop()
  }

  override fun playSound(soundId: Int) {
    if (disposed) return
    players[soundId]?.let { player ->
      if (player.playing) player.stop()
      player.currentTime = 0.0
      player.prepareToPlay()
      player.play()
    }
  }

  override fun dispose() {
    if (disposed) return
    disposed = true
    players.values.forEach(AVAudioPlayer::stop)
    players.clear()
  }
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
  NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

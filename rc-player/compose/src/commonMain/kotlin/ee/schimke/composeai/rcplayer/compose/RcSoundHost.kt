package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.composeai.rcplayer.runtime.RcSoundEffect

/**
 * Opt-in audio boundary for Remote Compose sound effects.
 *
 * Implementations may use AVFoundation on Apple targets, a browser audio context, or a test
 * recorder. The player makes no device or session-policy decision itself. Hosts should replace an
 * existing resource with the same id and restart it when [playSound] is called while it is active.
 */
public interface RcSoundHost {
  public fun loadSound(soundId: Int, data: ByteArray)

  public fun playSound(soundId: Int)

  /** Release backend resources. The embedding host remains responsible for calling this. */
  public fun dispose(): Unit = Unit

  public companion object {
    /** Silent default used by previews, render tests, and hosts with no audio backend. */
    public val None: RcSoundHost =
      object : RcSoundHost {
        override fun loadSound(soundId: Int, data: ByteArray) = Unit

        override fun playSound(soundId: Int) = Unit
      }
  }
}

/** Converts a bounded AndroidX SC payload to WAV; WAV input passes through unchanged. */
internal fun normalizeSoundData(data: ByteArray): ByteArray? {
  if (data.size >= 44 && data.asciiAt(0, "RIFF") && data.asciiAt(8, "WAVE")) return data
  if (data.size < 11 || !data.asciiAt(0, "SC")) return null
  val bitDepth = data[3].toInt() and 0xff
  val channels = data[4].toInt() and 0xff
  val sampleRate = ((data[5].toInt() and 0xff) shl 8) or (data[6].toInt() and 0xff)
  val sampleCount =
    ((data[7].toLong() and 0xff) shl 24) or
      ((data[8].toLong() and 0xff) shl 16) or
      ((data[9].toLong() and 0xff) shl 8) or
      (data[10].toLong() and 0xff)
  if (bitDepth !in setOf(8, 16) || channels !in 1..2 || sampleRate <= 0) return null
  val pcmBytes = sampleCount * (bitDepth / 8) * channels
  if (pcmBytes > data.size - 11 || pcmBytes > Int.MAX_VALUE - 44L) return null
  val pcmSize = pcmBytes.toInt()
  return ByteArray(44 + pcmSize).also { output ->
    output.writeAscii(0, "RIFF")
    output.writeIntLe(4, 36 + pcmSize)
    output.writeAscii(8, "WAVE")
    output.writeAscii(12, "fmt ")
    output.writeIntLe(16, 16)
    output.writeShortLe(20, 1)
    output.writeShortLe(22, channels)
    output.writeIntLe(24, sampleRate)
    output.writeIntLe(28, sampleRate * channels * (bitDepth / 8))
    output.writeShortLe(32, channels * (bitDepth / 8))
    output.writeShortLe(34, bitDepth)
    output.writeAscii(36, "data")
    output.writeIntLe(40, pcmSize)
    data.copyInto(output, destinationOffset = 44, startIndex = 11, endIndex = 11 + pcmSize)
  }
}

/** Audio backend used by [RcComposePlayer]; silent unless the embedding host supplies one. */
public val LocalRcSoundHost: ProvidableCompositionLocal<RcSoundHost> = staticCompositionLocalOf {
  RcSoundHost.None
}

internal fun RcSoundHost.dispatchSound(effect: RcSoundEffect) {
  // Audio availability must never determine whether a document can render. This also covers an
  // Apple audio session that becomes unavailable between resource preparation and playback.
  runCatching {
    when (effect) {
      is RcSoundEffect.Load -> loadSound(effect.soundId, effect.data)
      is RcSoundEffect.Play -> playSound(effect.soundId)
    }
  }
}

/**
 * Retains prepared resources so a host swapped during composition receives them before playback.
 */
internal class RcSoundHostDispatcher(initialHost: RcSoundHost) {
  private var host: RcSoundHost = initialHost
  private val loadedSounds = mutableMapOf<Int, ByteArray>()

  fun updateHost(host: RcSoundHost) {
    if (this.host === host) return
    this.host = host
    loadedSounds.forEach { (soundId, data) ->
      host.dispatchSound(RcSoundEffect.Load(soundId, data.copyOf()))
    }
  }

  fun dispatch(effect: RcSoundEffect) {
    if (effect is RcSoundEffect.Load) {
      loadedSounds[effect.soundId] = effect.data.copyOf()
    }
    host.dispatchSound(effect)
  }
}

private fun ByteArray.asciiAt(offset: Int, value: String): Boolean =
  value.indices.all { index -> this[offset + index].toInt() and 0xff == value[index].code }

private fun ByteArray.writeAscii(offset: Int, value: String) {
  value.forEachIndexed { index, character -> this[offset + index] = character.code.toByte() }
}

private fun ByteArray.writeShortLe(offset: Int, value: Int) {
  this[offset] = value.toByte()
  this[offset + 1] = (value ushr 8).toByte()
}

private fun ByteArray.writeIntLe(offset: Int, value: Int) {
  this[offset] = value.toByte()
  this[offset + 1] = (value ushr 8).toByte()
  this[offset + 2] = (value ushr 16).toByte()
  this[offset + 3] = (value ushr 24).toByte()
}

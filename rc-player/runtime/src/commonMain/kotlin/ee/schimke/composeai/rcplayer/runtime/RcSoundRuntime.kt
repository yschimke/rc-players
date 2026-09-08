package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcSoundData
import ee.schimke.composeai.rcplayer.protocol.RcSoundExpression
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** Device-independent sound requests emitted by [RcPlayerState]. */
public sealed interface RcSoundEffect {
  /** Register WAV or AndroidX SC-format bytes under [soundId]. */
  public class Load(public val soundId: Int, public val data: ByteArray) : RcSoundEffect {
    override fun equals(other: Any?): Boolean =
      other is Load && soundId == other.soundId && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * soundId + data.contentHashCode()
  }

  /** Start (or restart) the resource registered under [soundId]. */
  public data class Play(public val soundId: Int) : RcSoundEffect
}

/** Common, bounded implementation of AndroidX's 22.05 kHz mono tone synthesis. */
public object RcToneSynthesizer {
  public const val SAMPLE_RATE: Int = 22_050
  private const val WAV_HEADER_BYTES: Int = 44
  private const val BYTES_PER_SAMPLE: Int = 2

  /**
   * Produces 16-bit little-endian mono PCM in a WAV container.
   *
   * Invalid/non-finite input and output larger than [RcSoundData.MAX_DATA_BYTES] return `null`
   * instead of allocating an attacker-controlled buffer or failing document rendering.
   */
  public fun synthesizeWav(frequency: Float, durationSeconds: Float, waveform: Float): ByteArray? {
    if (!frequency.isFinite() || !durationSeconds.isFinite() || !waveform.isFinite()) return null
    val requestedSamples = (SAMPLE_RATE.toDouble() * durationSeconds).toLong().coerceAtLeast(1L)
    val maximumSamples = (RcSoundData.MAX_DATA_BYTES - WAV_HEADER_BYTES).toLong() / BYTES_PER_SAMPLE
    if (requestedSamples > maximumSamples) return null
    val sampleCount = requestedSamples.toInt()
    val pcm = ByteArray(sampleCount * BYTES_PER_SAMPLE)
    val kind = waveform.toInt()
    repeat(sampleCount) { index ->
      val time = index.toDouble() / SAMPLE_RATE
      val cycles = frequency * time
      val phase = 2.0 * PI * cycles
      val sample =
        when (kind) {
          RcSoundExpression.WAVEFORM_SQUARE -> if (sin(phase) >= 0.0) 1.0 else -1.0
          RcSoundExpression.WAVEFORM_SAWTOOTH -> 2.0 * (cycles - floor(cycles + 0.5))
          RcSoundExpression.WAVEFORM_TRIANGLE ->
            2.0 * abs(2.0 * (cycles - floor(cycles + 0.5))) - 1.0
          else -> sin(phase)
        }
      val envelope =
        when {
          index < 100 -> index / 100.0
          index > sampleCount - 100 -> (sampleCount - index) / 100.0
          else -> 1.0
        }
      val value = (sample * envelope * Short.MAX_VALUE).toInt().toShort().toInt()
      pcm[index * 2] = value.toByte()
      pcm[index * 2 + 1] = (value shr 8).toByte()
    }
    return wav(pcm)
  }

  private fun wav(pcm: ByteArray): ByteArray =
    ByteArray(WAV_HEADER_BYTES + pcm.size).also { output ->
      output.writeAscii(0, "RIFF")
      output.writeIntLe(4, 36 + pcm.size)
      output.writeAscii(8, "WAVE")
      output.writeAscii(12, "fmt ")
      output.writeIntLe(16, 16)
      output.writeShortLe(20, 1)
      output.writeShortLe(22, 1)
      output.writeIntLe(24, SAMPLE_RATE)
      output.writeIntLe(28, SAMPLE_RATE * BYTES_PER_SAMPLE)
      output.writeShortLe(32, BYTES_PER_SAMPLE)
      output.writeShortLe(34, 16)
      output.writeAscii(36, "data")
      output.writeIntLe(40, pcm.size)
      pcm.copyInto(output, WAV_HEADER_BYTES)
    }
}

internal class RcSoundRuntime(
  private val resolve: (RcFloatWord) -> Float,
  private val emit: (RcSoundEffect) -> Unit,
) {
  private val definitions = mutableMapOf<Int, RcOperation>()
  private val expressionParameters = mutableMapOf<Int, List<Float>>()

  fun prepare(operations: List<RcOperation>) {
    definitions.clear()
    expressionParameters.clear()
    operations.forEach { operation ->
      when (operation) {
        is RcSoundData -> {
          definitions[operation.soundId] = operation
          emit(RcSoundEffect.Load(operation.soundId, operation.data.copyOf()))
        }
        is RcSoundExpression -> {
          definitions[operation.id] = operation
          loadExpression(operation)
        }
        else -> Unit
      }
    }
  }

  fun play(operation: RcPlaySound) {
    (definitions[operation.soundId] as? RcSoundExpression)?.let(::loadExpression)
    emit(RcSoundEffect.Play(operation.soundId))
  }

  private fun loadExpression(expression: RcSoundExpression) {
    val parameters = expression.parameters.map(::resolveSoundWord)
    if (expressionParameters[expression.id] == parameters) return
    expressionParameters[expression.id] = parameters
    if (
      parameters.size < 4 || expression.parameters[0].referencedId != RcSoundExpression.TYPE_TONE
    ) {
      return
    }
    RcToneSynthesizer.synthesizeWav(parameters[1], parameters[2], parameters[3])?.let { data ->
      emit(RcSoundEffect.Load(expression.id, data))
    }
  }

  /** Synthesis tags 10..40 are NaNs, but AndroidX deliberately does not treat them as variables. */
  private fun resolveSoundWord(word: RcFloatWord): Float {
    val id = word.referencedId ?: return word.value
    return if (id in 10..40) word.value else resolve(word)
  }
}

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

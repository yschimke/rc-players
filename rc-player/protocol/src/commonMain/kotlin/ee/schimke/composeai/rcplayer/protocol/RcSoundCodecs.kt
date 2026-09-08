package ee.schimke.composeai.rcplayer.protocol

/** Inline WAV or AndroidX SC-format bytes registered under [soundId]. */
public class RcSoundData(public val soundId: Int, public val data: ByteArray) : RcOperation {
  override val opcode: Int = RcOpcodes.DATA_SOUND

  override fun equals(other: Any?): Boolean =
    other is RcSoundData && soundId == other.soundId && data.contentEquals(other.data)

  override fun hashCode(): Int = 31 * soundId + data.contentHashCode()

  override fun toString(): String = "RcSoundData(soundId=$soundId, data=${data.size} bytes)"

  public companion object {
    /** AndroidX's per-resource decoder bound. */
    public const val MAX_DATA_BYTES: Int = 256 * 1024
  }
}

/** A sound synthesis definition. All floats remain raw so dynamic ids round-trip exactly. */
public data class RcSoundExpression(
  val id: Int,
  val leftVolume: RcFloatWord,
  val rightVolume: RcFloatWord,
  val rate: RcFloatWord,
  val parameters: List<RcFloatWord>,
) : RcOperation {
  override val opcode: Int = RcOpcodes.SOUND_EXPRESSION

  public companion object {
    public const val MAX_PARAMETERS: Int = 64
    public const val TYPE_TONE: Int = 10
    public const val WAVEFORM_SINE: Int = 0
    public const val WAVEFORM_SQUARE: Int = 1
    public const val WAVEFORM_SAWTOOTH: Int = 2
    public const val WAVEFORM_TRIANGLE: Int = 3

    /** AndroidX NaN-boxing for a synthesis tag, distinct from a variable reference. */
    public val TYPE_TONE_WORD: RcFloatWord = RcFloatWord(0xff800000.toInt() or TYPE_TONE)
  }
}

/** Requests playback of a sound previously defined by [RcSoundData] or [RcSoundExpression]. */
public data class RcPlaySound(val soundId: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.PLAY_SOUND
}

private object SoundDataCodec : RcOperationCodec<RcSoundData> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.DATA_SOUND, "SoundData")

  override fun decode(input: RcWireReader): RcSoundData =
    RcSoundData(
      soundId = input.readId("soundId"),
      data = input.readByteArray("data", RcSoundData.MAX_DATA_BYTES),
    )

  override fun encode(output: RcWireWriter, value: RcSoundData) {
    require(value.data.size <= RcSoundData.MAX_DATA_BYTES) {
      "SoundData exceeds ${RcSoundData.MAX_DATA_BYTES} bytes"
    }
    output.writeInt(value.soundId)
    output.writeByteArray(value.data)
  }
}

private object SoundExpressionCodec : RcOperationCodec<RcSoundExpression> {
  override val spec: RcOperationSpec =
    RcOperationSpec(RcOpcodes.SOUND_EXPRESSION, "SoundExpression")

  override fun decode(input: RcWireReader): RcSoundExpression =
    RcSoundExpression(
      id = input.readId("id"),
      leftVolume = input.readFloatWord("leftVolume"),
      rightVolume = input.readFloatWord("rightVolume"),
      rate = input.readFloatWord("rate"),
      parameters =
        List(input.readCount("parameters.length", RcSoundExpression.MAX_PARAMETERS)) { index ->
          input.readFloatWord("parameters[$index]")
        },
    )

  override fun encode(output: RcWireWriter, value: RcSoundExpression) {
    require(value.parameters.size <= RcSoundExpression.MAX_PARAMETERS) {
      "SoundExpression has more than ${RcSoundExpression.MAX_PARAMETERS} parameters"
    }
    output.writeInt(value.id)
    output.writeFloatWord(value.leftVolume)
    output.writeFloatWord(value.rightVolume)
    output.writeFloatWord(value.rate)
    output.writeInt(value.parameters.size)
    value.parameters.forEach(output::writeFloatWord)
  }
}

private object PlaySoundCodec : RcOperationCodec<RcPlaySound> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.PLAY_SOUND, "PlaySound")

  override fun decode(input: RcWireReader): RcPlaySound = RcPlaySound(input.readId("soundId"))

  override fun encode(output: RcWireWriter, value: RcPlaySound) {
    output.writeInt(value.soundId)
  }
}

/** Codecs for sound data, expressions, and playback requests. */
internal val rcSoundOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(SoundDataCodec, SoundExpressionCodec, PlaySoundCodec)

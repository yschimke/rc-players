package ee.schimke.composeai.rcplayer.protocol

/** Fixed-size component boundary from the legacy layout stream. */
public data class RcComponentStart(
  val type: Int,
  val componentId: Int,
  val width: RcFloatWord,
  val height: RcFloatWord,
) : RcOperation {
  override val opcode: Int = RcOpcodes.COMPONENT_START
}

/**
 * The on-wire header of AndroidX `Skip`.
 *
 * A conforming decoder conditionally advances over [skipLength] bytes and does not retain this
 * header in the operation list. It is deliberately not registered below: the current symmetric
 * per-operation codec API has neither the decoded document profile nor a way to suppress an
 * operation, and parsing the following bytes before evaluating the condition would be unsafe.
 */
public data class RcSkip(val conditionType: Int, val value: Int, val skipLength: Int) :
  RcOperation {
  override val opcode: Int = RcOpcodes.SKIP

  public companion object {
    public const val IF_API_LESS_THAN: Int = 1
    public const val IF_API_GREATER_THAN: Int = 2
    public const val IF_API_EQUAL_TO: Int = 3
    public const val IF_API_NOT_EQUAL_TO: Int = 4
    public const val IF_PROFILE_INCLUDES: Int = 5
    public const val IF_PROFILE_EXCLUDES: Int = 6
  }
}

private object SkipCodec : RcOperationCodec<RcSkip> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.SKIP, "Skip")

  override fun decode(input: RcWireReader): RcSkip {
    val value =
      RcSkip(input.readInt("conditionType"), input.readInt("value"), input.readInt("skipLength"))
    if (value.skipLength < 0) input.fail("skipLength", "Skip length must not be negative")
    val skip =
      when (value.conditionType) {
        RcSkip.IF_API_LESS_THAN -> input.libraryApiLevel < value.value
        RcSkip.IF_API_GREATER_THAN -> input.libraryApiLevel > value.value
        RcSkip.IF_API_EQUAL_TO -> input.libraryApiLevel == value.value
        RcSkip.IF_API_NOT_EQUAL_TO -> input.libraryApiLevel != value.value
        RcSkip.IF_PROFILE_INCLUDES -> input.profile and value.value != 0
        RcSkip.IF_PROFILE_EXCLUDES -> input.profile and value.value == 0
        else -> false
      }
    if (skip) input.skipRawBytes(value.skipLength, "skippedBytes")
    return value
  }

  override fun encode(output: RcWireWriter, value: RcSkip) {
    require(value.skipLength >= 0) { "Skip length must not be negative" }
    output.writeInt(value.conditionType)
    output.writeInt(value.value)
    output.writeInt(value.skipLength)
  }
}

private object ComponentStartCodec : RcOperationCodec<RcComponentStart> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.COMPONENT_START, "ComponentStart")

  override fun decode(input: RcWireReader): RcComponentStart =
    RcComponentStart(
      type = input.readInt("type"),
      componentId = input.readId("componentId"),
      width = input.readFloatWord("width"),
      height = input.readFloatWord("height"),
    )

  override fun encode(output: RcWireWriter, value: RcComponentStart) {
    output.writeInt(value.type)
    output.writeInt(value.componentId)
    output.writeFloatWord(value.width)
    output.writeFloatWord(value.height)
  }
}

/** Codecs for stream-structure operations whose semantics fit the symmetric codec boundary. */
internal val rcStructuralOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(ComponentStartCodec, SkipCodec)

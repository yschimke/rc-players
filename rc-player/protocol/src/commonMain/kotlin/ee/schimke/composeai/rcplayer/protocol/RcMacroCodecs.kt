package ee.schimke.composeai.rcplayer.protocol

public data class RcReferencedOperations(val id: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.REFERENCED_OPERATIONS
}

public data class RcIncludeReferencedOperations(val id: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.INCLUDE_REFERENCED_OPERATIONS
}

public data class RcMacroForEach(val collectionId: Int, val localItemId: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.MACRO_FOR_EACH
}

/** A macro definition whose body is an exact, unframed operation byte stream. */
public class RcMacroDefine(
  public val id: Int,
  public val parameterIds: List<Int>,
  public val body: ByteArray,
) : RcOperation {
  override val opcode: Int = RcOpcodes.MACRO_DEFINE

  override fun equals(other: Any?): Boolean =
    other is RcMacroDefine &&
      id == other.id &&
      parameterIds == other.parameterIds &&
      body.contentEquals(other.body)

  override fun hashCode(): Int = 31 * (31 * id + parameterIds.hashCode()) + body.contentHashCode()
}

public data class RcMacroCall(val id: Int, val argumentIds: List<Int>) : RcOperation {
  override val opcode: Int = RcOpcodes.MACRO_CALL
}

public data class RcMacroArgument(val parameterIndex: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.MACRO_ARGUMENT
}

public data class RcMacroBlock(val parameterIndex: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.MACRO_BLOCK
}

private object ReferencedOperationsCodec :
  IntFieldCodec<RcReferencedOperations>(
    RcOpcodes.REFERENCED_OPERATIONS,
    "ReferencedOperations",
    "id",
    ::RcReferencedOperations,
    RcReferencedOperations::id,
    declares = true,
  )

private object IncludeReferencedOperationsCodec :
  IntFieldCodec<RcIncludeReferencedOperations>(
    RcOpcodes.INCLUDE_REFERENCED_OPERATIONS,
    "IncludeReferencedOperations",
    "id",
    ::RcIncludeReferencedOperations,
    RcIncludeReferencedOperations::id,
  )

private object MacroArgumentCodec :
  IntFieldCodec<RcMacroArgument>(
    RcOpcodes.MACRO_ARGUMENT,
    "MacroArgument",
    "parameterIndex",
    ::RcMacroArgument,
    RcMacroArgument::parameterIndex,
  )

private object MacroBlockCodec :
  IntFieldCodec<RcMacroBlock>(
    RcOpcodes.MACRO_BLOCK,
    "MacroBlock",
    "parameterIndex",
    ::RcMacroBlock,
    RcMacroBlock::parameterIndex,
  )

private object MacroForEachCodec : RcOperationCodec<RcMacroForEach> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.MACRO_FOR_EACH, "MacroForEach")

  override fun decode(input: RcWireReader): RcMacroForEach =
    RcMacroForEach(input.readId("collectionId"), input.readId("localItemId"))

  override fun encode(output: RcWireWriter, value: RcMacroForEach) {
    output.writeInt(value.collectionId)
    output.writeInt(value.localItemId)
  }
}

internal object MacroDefineCodec : RcOperationCodec<RcMacroDefine> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.MACRO_DEFINE, "MacroDefine")

  override fun decode(input: RcWireReader): RcMacroDefine {
    val id = input.readId("id")
    val count = input.readCount("parameterIds.length", input.limits.maxCollectionEntries)
    val parameterIds = List(count) { input.readId("parameterIds[$it]") }
    val bodySize = input.readCount("body.length", input.limits.maxBlobBytes)
    return RcMacroDefine(id, parameterIds, input.readRawBytes(bodySize, "body"))
  }

  override fun encode(output: RcWireWriter, value: RcMacroDefine) {
    require(value.parameterIds.size <= RcWireLimits().maxCollectionEntries) {
      "Too many macro parameters: ${value.parameterIds.size}"
    }
    require(value.body.size <= RcWireLimits().maxBlobBytes) { "Macro body is too large" }
    output.writeInt(value.id)
    output.writeInt(value.parameterIds.size)
    value.parameterIds.forEach(output::writeInt)
    output.writeInt(value.body.size)
    output.writeRawBytes(value.body)
  }
}

private object MacroCallCodec : RcOperationCodec<RcMacroCall> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.MACRO_CALL, "MacroCall")

  override fun decode(input: RcWireReader): RcMacroCall {
    val id = input.readId("id")
    val count = input.readCount("argumentIds.length", input.limits.maxCollectionEntries)
    return RcMacroCall(id, List(count) { input.readId("argumentIds[$it]") })
  }

  override fun encode(output: RcWireWriter, value: RcMacroCall) {
    require(value.argumentIds.size <= RcWireLimits().maxCollectionEntries) {
      "Too many macro arguments: ${value.argumentIds.size}"
    }
    output.writeInt(value.id)
    output.writeInt(value.argumentIds.size)
    value.argumentIds.forEach(output::writeInt)
  }
}

private abstract class IntFieldCodec<T : RcOperation>(
  opcode: Int,
  name: String,
  private val field: String,
  private val create: (Int) -> T,
  private val value: (T) -> Int,
  private val declares: Boolean = false,
) : RcOperationCodec<T> {
  final override val spec: RcOperationSpec = RcOperationSpec(opcode, name)

  final override fun decode(input: RcWireReader): T =
    create(if (declares) input.readDeclaredId(field) else input.readId(field))

  final override fun encode(output: RcWireWriter, value: T) = output.writeInt(this.value(value))
}

/** Symmetric codecs for the current AndroidX experimental reference/macro wire family. */
internal val rcMacroOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(
    ReferencedOperationsCodec,
    MacroForEachCodec,
    IncludeReferencedOperationsCodec,
    MacroDefineCodec,
    MacroCallCodec,
    MacroArgumentCodec,
    MacroBlockCodec,
  )

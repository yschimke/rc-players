package ee.schimke.composeai.rcplayer.protocol

/** Runtime-shader declaration and its named uniform resources. */
public data class RcShaderData(
  val shaderId: Int,
  val shaderTextId: Int,
  val floatUniforms: Map<String, List<RcFloatWord>> = emptyMap(),
  val intUniforms: Map<String, List<Int>> = emptyMap(),
  val bitmapUniforms: Map<String, Int> = emptyMap(),
) : RcOperation {
  override val opcode: Int = RcOpcodes.DATA_SHADER

  init {
    require(floatUniforms.size <= MAX_UNIFORMS_PER_TYPE)
    require(intUniforms.size <= MAX_UNIFORMS_PER_TYPE)
    require(bitmapUniforms.size <= MAX_UNIFORMS_PER_TYPE)
    require(floatUniforms.values.all { it.size <= MAX_UNIFORM_VALUES })
    require(intUniforms.values.all { it.size <= MAX_UNIFORM_VALUES })
  }

  public companion object {
    /** Each map count occupies one unsigned byte in AndroidX's packed `sizes` word. */
    public const val MAX_UNIFORMS_PER_TYPE: Int = 255

    /** AndroidX's bounded per-uniform vector size. */
    public const val MAX_UNIFORM_VALUES: Int = 1_024
  }
}

/** Redirects subsequent canvas operations to [bitmapId], or to the main canvas for id zero. */
public data class RcDrawToBitmap(val bitmapId: Int, val mode: Int, val color: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.DRAW_TO_BITMAP

  public companion object {
    /** Preserve the bitmap's existing pixels instead of clearing it to [color]. */
    public const val MODE_NO_INITIALIZE: Int = 1
  }
}

/** Codecs for shader data and offscreen bitmap rendering. */
internal val rcGraphicsResourceOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(ShaderDataCodec, DrawToBitmapCodec)

private object ShaderDataCodec : RcOperationCodec<RcShaderData> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.DATA_SHADER, "ShaderData")

  override fun decode(input: RcWireReader): RcShaderData {
    val shaderId = input.readDeclaredId("shaderId")
    val shaderTextId = input.readId("shaderTextId")
    val sizes = input.readInt("sizes")
    if (sizes ushr 24 != 0) input.fail("sizes", "Reserved high byte must be zero")
    val floatCount = sizes and 0xff
    val intCount = (sizes ushr 8) and 0xff
    val bitmapCount = (sizes ushr 16) and 0xff
    val floatUniforms = buildMap {
      repeat(floatCount) { index ->
        val name = input.readUtf8("floatUniforms[$index].name")
        val count =
          input.readCount(
            "floatUniforms[$index].values.length",
            RcShaderData.MAX_UNIFORM_VALUES,
          )
        put(
          name,
          List(count) { valueIndex ->
            input.readFloatWord("floatUniforms[$index].values[$valueIndex]")
          },
        )
      }
    }
    val intUniforms = buildMap {
      repeat(intCount) { index ->
        val name = input.readUtf8("intUniforms[$index].name")
        val count =
          input.readCount(
            "intUniforms[$index].values.length",
            RcShaderData.MAX_UNIFORM_VALUES,
          )
        put(
          name,
          List(count) { valueIndex -> input.readInt("intUniforms[$index].values[$valueIndex]") },
        )
      }
    }
    val bitmapUniforms = buildMap {
      repeat(bitmapCount) { index ->
        put(
          input.readUtf8("bitmapUniforms[$index].name"),
          input.readId("bitmapUniforms[$index].bitmapId"),
        )
      }
    }
    return RcShaderData(shaderId, shaderTextId, floatUniforms, intUniforms, bitmapUniforms)
  }

  override fun encode(output: RcWireWriter, value: RcShaderData) {
    output.writeInt(value.shaderId)
    output.writeInt(value.shaderTextId)
    output.writeInt(
      value.floatUniforms.size or
        (value.intUniforms.size shl 8) or
        (value.bitmapUniforms.size shl 16)
    )
    value.floatUniforms.forEach { (name, values) ->
      output.writeUtf8(name)
      output.writeInt(values.size)
      values.forEach(output::writeFloatWord)
    }
    value.intUniforms.forEach { (name, values) ->
      output.writeUtf8(name)
      output.writeInt(values.size)
      values.forEach(output::writeInt)
    }
    value.bitmapUniforms.forEach { (name, bitmapId) ->
      output.writeUtf8(name)
      output.writeInt(bitmapId)
    }
  }
}

private object DrawToBitmapCodec : RcOperationCodec<RcDrawToBitmap> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.DRAW_TO_BITMAP, "DrawToBitmap")

  override fun decode(input: RcWireReader): RcDrawToBitmap =
    RcDrawToBitmap(
      bitmapId = input.readId("bitmapId"),
      mode = input.readInt("mode"),
      color = input.readInt("color"),
    )

  override fun encode(output: RcWireWriter, value: RcDrawToBitmap) {
    output.writeInt(value.bitmapId)
    output.writeInt(value.mode)
    output.writeInt(value.color)
  }
}

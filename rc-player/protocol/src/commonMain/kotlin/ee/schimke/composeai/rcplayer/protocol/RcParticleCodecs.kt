package ee.schimke.composeai.rcplayer.protocol

private const val MAX_PARTICLES = 8_000
private const val MAX_PARTICLE_VARIABLES = 2_000
private const val MAX_PARTICLE_EXPRESSION_WORDS = 32
private const val MAX_COMPARE_EXPRESSION_WORDS = 46

private object ParticleDefineCodec : RcOperationCodec<RcParticleDefine> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.PARTICLE_DEFINE, "ParticlesCreate")

  override fun decode(input: RcWireReader): RcParticleDefine {
    val id = input.readId("id")
    val particleCount = input.readCount("particleCount", MAX_PARTICLES)
    val variableCount = input.readCount("variables.length", MAX_PARTICLE_VARIABLES)
    val variableIds = ArrayList<Int>(variableCount)
    val equations = ArrayList<List<RcFloatWord>>(variableCount)
    repeat(variableCount) { index ->
      variableIds += input.readId("variables[$index].id")
      equations += input.readEquation("variables[$index].equation", MAX_PARTICLE_EXPRESSION_WORDS)
    }
    return RcParticleDefine(id, particleCount, variableIds, equations)
  }

  override fun encode(output: RcWireWriter, value: RcParticleDefine) {
    require(value.particleCount in 0..MAX_PARTICLES)
    require(value.variableIds.size == value.initializationEquations.size)
    require(value.variableIds.size <= MAX_PARTICLE_VARIABLES)
    output.writeInt(value.id)
    output.writeInt(value.particleCount)
    output.writeInt(value.variableIds.size)
    value.variableIds.indices.forEach { index ->
      output.writeInt(value.variableIds[index])
      output.writeEquation(value.initializationEquations[index], MAX_PARTICLE_EXPRESSION_WORDS)
    }
  }
}

private object ParticleLoopCodec : RcOperationCodec<RcParticleLoop> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.PARTICLE_LOOP, "ParticlesLoop")

  override fun decode(input: RcWireReader): RcParticleLoop =
    RcParticleLoop(
      id = input.readId("id"),
      restartEquation = input.readEquation("restartEquation", MAX_PARTICLE_EXPRESSION_WORDS),
      updateEquations =
        List(input.readCount("updateEquations.length", MAX_PARTICLE_VARIABLES)) { index ->
          input.readEquation("updateEquations[$index]", MAX_PARTICLE_EXPRESSION_WORDS)
        },
    )

  override fun encode(output: RcWireWriter, value: RcParticleLoop) {
    require(value.updateEquations.size <= MAX_PARTICLE_VARIABLES)
    output.writeInt(value.id)
    output.writeEquation(value.restartEquation, MAX_PARTICLE_EXPRESSION_WORDS)
    output.writeInt(value.updateEquations.size)
    value.updateEquations.forEach { output.writeEquation(it, MAX_PARTICLE_EXPRESSION_WORDS) }
  }
}

private object ParticleCompareCodec : RcOperationCodec<RcParticleCompare> {
  override val spec: RcOperationSpec =
    RcOperationSpec(RcOpcodes.PARTICLE_COMPARE, "ParticlesCompare")

  override fun decode(input: RcWireReader): RcParticleCompare =
    RcParticleCompare(
      id = input.readId("id"),
      flags = input.readU16("flags"),
      minimumIndex = input.readFloatWord("minimumIndex"),
      maximumIndex = input.readFloatWord("maximumIndex"),
      condition = input.readEquation("condition", MAX_COMPARE_EXPRESSION_WORDS),
      firstEquations = input.readEquationList("firstEquations"),
      secondEquations = input.readEquationList("secondEquations"),
    )

  override fun encode(output: RcWireWriter, value: RcParticleCompare) {
    require(value.flags in 0..0xffff)
    output.writeInt(value.id)
    output.writeU16(value.flags)
    output.writeFloatWord(value.minimumIndex)
    output.writeFloatWord(value.maximumIndex)
    output.writeEquation(value.condition, MAX_COMPARE_EXPRESSION_WORDS)
    output.writeEquationList(value.firstEquations)
    output.writeEquationList(value.secondEquations)
  }
}

private fun RcWireReader.readEquation(field: String, maximum: Int): List<RcFloatWord> =
  List(readCount("$field.length", maximum)) { index -> readFloatWord("$field[$index]") }

private fun RcWireReader.readEquationList(field: String): List<List<RcFloatWord>> =
  List(readCount("$field.length", MAX_PARTICLE_VARIABLES)) { index ->
    readEquation("$field[$index]", MAX_COMPARE_EXPRESSION_WORDS)
  }

private fun RcWireWriter.writeEquation(values: List<RcFloatWord>, maximum: Int) {
  require(values.size <= maximum)
  writeInt(values.size)
  values.forEach(::writeFloatWord)
}

private fun RcWireWriter.writeEquationList(values: List<List<RcFloatWord>>) {
  require(values.size <= MAX_PARTICLE_VARIABLES)
  writeInt(values.size)
  values.forEach { writeEquation(it, MAX_COMPARE_EXPRESSION_WORDS) }
}

/** Codecs for particle definitions, loops, and comparisons. */
internal val rcParticleOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(ParticleDefineCodec, ParticleLoopCodec, ParticleCompareCodec)

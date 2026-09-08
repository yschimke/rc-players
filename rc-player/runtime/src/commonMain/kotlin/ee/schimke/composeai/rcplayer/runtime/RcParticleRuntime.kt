package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcParticleCompare
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcParticleLoop

/** Resource ceilings for deterministic particle execution. */
public data class RcParticleExecutionLimits(
  val maxParticles: Int = 8_000,
  val maxVariablesPerParticle: Int = 64,
  /** Maximum updates before a non-restarting particle is frozen at its last evolved value. */
  val maxLifetimeFrames: Int = 3_600,
  val maxNestingDepth: Int = 8,
  val maxWorkPerFrame: Int = 20_000,
) {
  init {
    require(maxParticles >= 0)
    require(maxVariablesPerParticle >= 0)
    require(maxLifetimeFrames > 0)
    require(maxNestingDepth > 0)
    require(maxWorkPerFrame > 0)
  }
}

internal class RcParticleRuntime(
  private val definitions: List<RcParticleDefine>,
  randomSeed: Long,
  private val limits: RcParticleExecutionLimits,
  private val resolve: (RcFloatWord) -> Float,
  private val publish: (Int, Float) -> Unit,
  private val requestNextFrame: () -> Unit,
) {
  private val evaluator = RcFloatExpressionEvaluator(randomSeed = randomSeed)
  private val systems = mutableMapOf<Int, System>()
  private var nestingDepth = 0
  private var frameWork = 0
  private var initialized = false

  fun beginFrame() {
    frameWork = 0
    if (!initialized) {
      definitions.forEach(::define)
      initialized = true
    }
  }

  fun snapshot(id: Int): List<List<Float>> =
    systems[id]?.particles?.map { it.toList() } ?: emptyList()

  fun forEach(loop: RcParticleLoop, block: () -> Unit) {
    val system = requireSystem(loop.id)
    require(loop.updateEquations.size == system.variableIds.size) {
      "ParticleLoop ${loop.id} has ${loop.updateEquations.size} equations for " +
        "${system.variableIds.size} variables"
    }
    var hasActiveParticles = false
    nested {
      for (index in system.particles.indices) {
        if (system.frozen[index]) {
          publish(system, system.particles[index])
          block()
          continue
        }
        hasActiveParticles = true
        consume(1 + loop.updateEquations.sumOf { it.size })
        val particle = system.particles[index]
        val previous = particle.copyOf()
        loop.updateEquations.forEachIndexed { variableIndex, equation ->
          particle[variableIndex] = evaluate(equation, system, previous)
        }
        val childValues = particle.copyOf()
        system.ages[index]++
        if (evaluate(loop.restartEquation, system, particle) > 0f) {
          initialize(system, index)
        } else if (system.ages[index] >= limits.maxLifetimeFrames) {
          // The ceiling is a local resource guard, not an AndroidX lifecycle rule. Restarting here
          // silently changes document behavior, so retain the last evolved value and stop spending
          // update work on this particle until the document (and therefore this runtime) changes.
          system.frozen[index] = true
        }
        // AndroidX publishes the evolved values before evaluating restart. Reinitialization changes
        // the stored particle for its next frame, while this frame's children still see its final
        // pre-restart values.
        publish(system, childValues)
        block()
      }
    }
    if (hasActiveParticles) requestNextFrame()
  }

  fun compare(compare: RcParticleCompare, block: () -> Unit) {
    val system = requireSystem(compare.id)
    val start = resolvedIndex(compare.minimumIndex, 0, system.particles.size)
    val end = resolvedIndex(compare.maximumIndex, system.particles.size, system.particles.size)
    require(compare.firstEquations.size == system.variableIds.size) {
      "ParticlesCompare ${compare.id} first result has ${compare.firstEquations.size} equations for " +
        "${system.variableIds.size} variables"
    }
    require(
      compare.secondEquations.isEmpty() || compare.secondEquations.size == system.variableIds.size
    ) {
      "ParticlesCompare ${compare.id} second result has ${compare.secondEquations.size} equations for " +
        "${system.variableIds.size} variables"
    }
    var changed = false
    nested {
      if (compare.secondEquations.isEmpty()) {
        for (index in start until end) {
          consume(1 + compare.condition.size + compare.firstEquations.sumOf { it.size })
          val particle = system.particles[index]
          publish(system, particle)
          if (evaluate(compare.condition, system, particle) > 0f) {
            val previous = particle.copyOf()
            compare.firstEquations.forEachIndexed { variableIndex, equation ->
              particle[variableIndex] = evaluate(equation, system, previous)
            }
            publish(system, particle)
            block()
            changed = true
          }
        }
      } else {
        for (secondIndex in start until end) {
          val second = system.particles[secondIndex]
          for (firstIndex in secondIndex + 1 until end) {
            consume(
              1 +
                compare.condition.size +
                compare.firstEquations.sumOf { it.size } +
                compare.secondEquations.sumOf { it.size }
            )
            val first = system.particles[firstIndex]
            if (evaluatePair(compare.condition, system, first, second, first) > 0f) {
              val firstPrevious = first.copyOf()
              val secondPrevious = second.copyOf()
              val firstNext =
                FloatArray(first.size) { variableIndex ->
                  evaluatePair(
                    compare.firstEquations[variableIndex],
                    system,
                    firstPrevious,
                    secondPrevious,
                    firstPrevious,
                  )
                }
              val secondNext =
                FloatArray(second.size) { variableIndex ->
                  evaluatePair(
                    compare.secondEquations[variableIndex],
                    system,
                    firstPrevious,
                    secondPrevious,
                    secondPrevious,
                  )
                }
              firstNext.copyInto(first)
              publish(system, first)
              block()
              secondNext.copyInto(second)
              publish(system, second)
              block()
              changed = true
            }
          }
        }
      }
    }
    if (changed) requestNextFrame()
  }

  private fun define(definition: RcParticleDefine) {
    require(definition.particleCount in 0..limits.maxParticles) {
      "Particle system ${definition.id} must contain 0..${limits.maxParticles} particles"
    }
    require(definition.variableIds.size <= limits.maxVariablesPerParticle) {
      "Particle system ${definition.id} exceeds ${limits.maxVariablesPerParticle} variables"
    }
    require(definition.variableIds.size == definition.initializationEquations.size)
    require(definition.id !in systems) { "Duplicate particle system ${definition.id}" }
    val system =
      System(
        definition.variableIds.toIntArray(),
        definition.initializationEquations,
        Array(definition.particleCount) { FloatArray(definition.variableIds.size) },
        IntArray(definition.particleCount),
        BooleanArray(definition.particleCount),
      )
    systems[definition.id] = system
    system.particles.indices.forEach { initialize(system, it) }
  }

  private fun initialize(system: System, particleIndex: Int) {
    val particle = system.particles[particleIndex]
    val indexVariable = floatArrayOf(particleIndex.toFloat())
    system.initializationEquations.forEachIndexed { variableIndex, equation ->
      particle[variableIndex] = evaluator.evaluate(equation, indexVariable, resolve)
    }
    system.ages[particleIndex] = 0
    system.frozen[particleIndex] = false
  }

  private fun evaluate(
    expression: List<RcFloatWord>,
    system: System,
    particle: FloatArray,
  ): Float {
    if (expression.isEmpty()) return 0f
    return evaluator.evaluate(expression) { word ->
      val index = word.referencedId?.let { system.variableIds.indexOf(it) } ?: -1
      if (index >= 0) particle[index] else resolve(word)
    }
  }

  private fun evaluatePair(
    expression: List<RcFloatWord>,
    system: System,
    first: FloatArray,
    second: FloatArray,
    defaultParticle: FloatArray,
  ): Float {
    if (expression.isEmpty()) return 0f
    val prepared = ArrayList<RcFloatWord>(expression.size)
    var index = 0
    while (index < expression.size) {
      val word = expression[index]
      val variableIndex = word.referencedId?.let { system.variableIds.indexOf(it) } ?: -1
      val selector = expression.getOrNull(index + 1)?.operatorId()
      if (variableIndex >= 0 && (selector == CMD1 || selector == CMD2)) {
        prepared +=
          RcFloatWord.literal(if (selector == CMD1) first[variableIndex] else second[variableIndex])
        index += 2
      } else {
        prepared += word
        index++
      }
    }
    return evaluator.evaluate(prepared) { word ->
      val variableIndex = word.referencedId?.let { system.variableIds.indexOf(it) } ?: -1
      if (variableIndex >= 0) defaultParticle[variableIndex] else resolve(word)
    }
  }

  private fun publish(system: System, particle: FloatArray) {
    system.variableIds.indices.forEach { publish(system.variableIds[it], particle[it]) }
  }

  private fun resolvedIndex(word: RcFloatWord, negativeDefault: Int, size: Int): Int {
    val value = resolve(word)
    if (value < 0f) return negativeDefault
    return value.toInt().coerceIn(0, size)
  }

  private fun requireSystem(id: Int): System =
    requireNotNull(systems[id]) { "Missing particle system $id" }

  private inline fun nested(block: () -> Unit) {
    require(nestingDepth < limits.maxNestingDepth) {
      "Particle nesting exceeds ${limits.maxNestingDepth}"
    }
    nestingDepth++
    try {
      block()
    } finally {
      nestingDepth--
    }
  }

  private fun consume(amount: Int) {
    frameWork += amount
    require(frameWork <= limits.maxWorkPerFrame) {
      "Particle work exceeds ${limits.maxWorkPerFrame} units in one frame"
    }
  }

  private fun RcFloatWord.operatorId(): Int? = if (isNaNEncoded) bits and 0x7fffff else null

  private data class System(
    val variableIds: IntArray,
    val initializationEquations: List<List<RcFloatWord>>,
    val particles: Array<FloatArray>,
    val ages: IntArray,
    val frozen: BooleanArray,
  )

  private companion object {
    const val EXPRESSION_OFFSET = RcFloatExpressionEvaluator.OFFSET
    const val CMD1 = EXPRESSION_OFFSET + 64
    const val CMD2 = EXPRESSION_OFFSET + 65
  }
}

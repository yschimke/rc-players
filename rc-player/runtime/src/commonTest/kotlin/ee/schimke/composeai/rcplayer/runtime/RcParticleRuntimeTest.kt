package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcParticleCompare
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcParticleLoop
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcParticleRuntimeTest {
  @Test
  fun initializesFromIndexAndEvolvesDeterministically() {
    val state = state(definition(3, listOf(indexExpression())))
    val loop =
      RcParticleLoop(
        50,
        emptyList(),
        listOf(listOf(reference(VARIABLE), literal(1f), operator(1))),
      )

    var visits = 0
    state.forEachParticle(loop) { visits++ }

    assertEquals(3, visits)
    assertEquals(listOf(listOf(1f), listOf(2f), listOf(3f)), state.particleSnapshot(50))
  }

  @Test
  fun identicalSeedsAndFrameTimesProduceIdenticalRandomTraces() {
    val random = listOf(operator(39))
    val first = state(definition(3, listOf(random)), randomSeed = 1234L)
    val second = state(definition(3, listOf(random)), randomSeed = 1234L)

    first.beginFrame(2.5f)
    second.beginFrame(2.5f)

    assertEquals(first.particleSnapshot(50), second.particleSnapshot(50))
  }

  @Test
  fun initializationCanReadInjectedFrameClock() {
    val definition = definition(1, listOf(listOf(reference(RcSystemVariables.CONTINUOUS_SEC))))
    val state = state(definition, initialFrameSeconds = 4.5f)

    assertEquals(listOf(listOf(4.5f)), state.particleSnapshot(50))
  }

  @Test
  fun oneParticleComparisonUpdatesMatchingParticles() {
    val state = state(definition(2, listOf(indexExpression())))
    val comparison =
      RcParticleCompare(
        50,
        0,
        literal(-1f),
        literal(-1f),
        listOf(reference(VARIABLE), literal(0f), operator(44)),
        listOf(listOf(reference(VARIABLE), literal(10f), operator(1))),
        emptyList(),
      )

    var visits = 0
    state.compareParticles(comparison) { visits++ }

    assertEquals(1, visits)
    assertEquals(listOf(listOf(0f), listOf(11f)), state.particleSnapshot(50))
  }

  @Test
  fun twoParticleSelectorsCanSwapValues() {
    val state = state(definition(2, listOf(indexExpression())))
    val comparison =
      RcParticleCompare(
        50,
        0,
        literal(-1f),
        literal(-1f),
        listOf(literal(1f)),
        listOf(listOf(reference(VARIABLE), operator(65))),
        listOf(listOf(reference(VARIABLE), operator(64))),
      )

    state.compareParticles(comparison) {}

    assertEquals(listOf(listOf(1f), listOf(0f)), state.particleSnapshot(50))
  }

  @Test
  fun particleLifetimeCeilingFreezesInsteadOfInventingARestart() {
    val state =
      state(
        definition(2, listOf(indexExpression())),
        limits = RcParticleExecutionLimits(maxLifetimeFrames = 1),
      )
    val loop =
      RcParticleLoop(
        50,
        emptyList(),
        listOf(listOf(reference(VARIABLE), literal(10f), operator(1))),
      )

    state.forEachParticle(loop) {}

    assertEquals(listOf(listOf(10f), listOf(11f)), state.particleSnapshot(50))

    state.beginFrame(1f)
    state.forEachParticle(loop) {}

    assertEquals(listOf(listOf(10f), listOf(11f)), state.particleSnapshot(50))
  }

  @Test
  fun documentRestartEquationStillReinitializesAtLifetimeCeiling() {
    val state =
      state(
        definition(1, listOf(indexExpression())),
        limits = RcParticleExecutionLimits(maxLifetimeFrames = 1),
      )
    val loop =
      RcParticleLoop(
        50,
        listOf(literal(1f)),
        listOf(listOf(reference(VARIABLE), literal(10f), operator(1))),
      )

    state.forEachParticle(loop) {}

    assertEquals(listOf(listOf(0f)), state.particleSnapshot(50))
  }

  @Test
  fun perFrameWorkIsBounded() {
    val state =
      state(
        definition(2, listOf(indexExpression())),
        limits = RcParticleExecutionLimits(maxLifetimeFrames = 1, maxWorkPerFrame = 3),
      )
    val loop = RcParticleLoop(50, emptyList(), listOf(listOf(reference(VARIABLE))))

    assertFailsWith<IllegalArgumentException> { state.forEachParticle(loop) {} }
  }

  private fun state(
    definition: RcParticleDefine,
    randomSeed: Long = RcFloatExpressionEvaluator.DEFAULT_RANDOM_SEED,
    limits: RcParticleExecutionLimits = RcParticleExecutionLimits(),
    initialFrameSeconds: Float = 0f,
  ): RcPlayerState {
    val clock =
      object : RcTimeSource {
        override fun currentTimeMillis(): Long = (initialFrameSeconds * 1_000).toLong()

        override fun snapshot(epochMillis: Long): RcTimeSnapshot =
          RcTimeSnapshot(
            epochMillis,
            1970,
            1,
            1,
            1,
            0,
            0,
            initialFrameSeconds.toInt(),
            4,
          )
      }
    return RcPlayerState(
        RcDocument(RcHeader(RcVersion(0, 1, 0)), listOf(definition)),
        timeSource = clock,
        particleRandomSeed = randomSeed,
        particleLimits = limits,
      )
      .also { it.beginFrame(initialFrameSeconds, clock.currentTimeMillis()) }
  }

  private fun definition(count: Int, equations: List<List<RcFloatWord>>) =
    RcParticleDefine(50, count, List(equations.size) { VARIABLE + it }, equations)

  private fun indexExpression() = listOf(operator(70))

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun reference(id: Int) = RcFloatWord(0xff800000.toInt() or id)

  private fun operator(offset: Int) =
    RcFloatWord(0xff800000.toInt() or (RcFloatExpressionEvaluator.OFFSET + offset))

  private companion object {
    const val VARIABLE = 100
  }
}

package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcParticleCompare
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcParticleLoop
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcParticleSupportTest {
  @Test
  fun validParticlePipelineIsFullyRenderable() {
    val document =
      document(
        definition(),
        RcParticleLoop(50, emptyList(), listOf(listOf(reference(100)))),
        end(),
        RcParticleCompare(
          50,
          0,
          literal(-1f),
          literal(-1f),
          listOf(literal(1f)),
          listOf(listOf(reference(100))),
          emptyList(),
        ),
        end(),
      )

    assertTrue(document.composeSupportReport().fullyRenderable)
  }

  @Test
  fun reportsMissingDefinitionAndEquationMismatch() {
    val document =
      document(
        definition(),
        RcParticleLoop(51, emptyList(), emptyList()),
        end(),
        RcParticleCompare(
          50,
          0,
          literal(-1f),
          literal(-1f),
          listOf(literal(1f)),
          emptyList(),
          emptyList(),
        ),
        end(),
      )

    assertEquals(
      setOf("ParticlesLoop", "ParticlesCompare"),
      document.composeSupportReport().issues.map { it.operation }.toSet(),
    )
  }

  private fun definition() = RcParticleDefine(50, 2, listOf(100), listOf(listOf(literal(0f))))

  private fun document(vararg operations: ee.schimke.composeai.rcplayer.protocol.RcOperation) =
    RcDocument(RcHeader(RcVersion(0, 1, 0)), operations.toList())

  private fun end() = RcNoArg(RcOpcodes.CONTAINER_END)

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun reference(id: Int) = RcFloatWord(0xff800000.toInt() or id)
}

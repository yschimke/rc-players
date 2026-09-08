package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcParticleCodecsTest {
  @Test
  fun roundTripsParticleOperations() {
    val operations =
      listOf(
        RcParticleDefine(
          id = 50,
          particleCount = 3,
          variableIds = listOf(60, 61),
          initializationEquations = listOf(listOf(literal(1f)), listOf(reference(70))),
        ),
        RcParticleLoop(
          id = 50,
          restartEquation = listOf(reference(60)),
          updateEquations = listOf(listOf(reference(60), literal(1f), operator(1)), emptyList()),
        ),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcParticleCompare(
          id = 50,
          flags = 0x8001,
          minimumIndex = literal(-1f),
          maximumIndex = reference(71),
          condition = listOf(reference(60), literal(0f), operator(44)),
          firstEquations = listOf(listOf(reference(60)), listOf(reference(61))),
          secondEquations = emptyList(),
        ),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )
    val document = RcDocument(header(), operations)

    assertEquals(document, RcDocumentCodec.decode(RcDocumentCodec.encode(document)))
  }

  @Test
  fun rejectsParticleCountAboveAndroidxBound() {
    val writer = RcWireWriter()
    RcDocumentCodec.encodeOperation(writer, header())
    writer.writeU8(RcOpcodes.PARTICLE_DEFINE)
    writer.writeInt(50)
    writer.writeInt(8_001)
    writer.writeInt(0)

    val failure = assertFailsWith<RcWireException> { RcDocumentCodec.decode(writer.toByteArray()) }

    assertEquals("particleCount", failure.fieldName)
  }

  private fun header() = RcHeader(RcVersion(0, 1, 0))

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun reference(id: Int) = RcFloatWord(0xff800000.toInt() or id)

  private fun operator(offset: Int) = RcFloatWord(0xff800000.toInt() or (0x310000 + offset))
}

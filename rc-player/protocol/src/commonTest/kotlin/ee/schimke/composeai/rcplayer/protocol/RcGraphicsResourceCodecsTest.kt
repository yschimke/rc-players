package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcGraphicsResourceCodecsTest {
  @Test
  fun shaderAndDrawTargetRoundTripWithAndroidxWireLayout() {
    val shader =
      RcShaderData(
        shaderId = 42,
        shaderTextId = 43,
        floatUniforms =
          linkedMapOf(
            "resolution" to listOf(RcFloatWord.literal(320f), RcFloatWord.literal(200f)),
            "phase" to listOf(RcFloatWord(0x7fc0002c)),
          ),
        intUniforms = linkedMapOf("variant" to listOf(2, 3)),
        bitmapUniforms = linkedMapOf("texture" to 45),
      )
    val operations =
      listOf(shader, RcDrawToBitmap(45, RcDrawToBitmap.MODE_NO_INITIALIZE, 0xff336699.toInt()))
    val document = RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), operations)

    val encoded = RcDocumentCodec.encode(document)
    val decoded = RcDocumentCodec.decode(encoded)

    assertEquals(document, decoded)
    assertContentEquals(encoded, RcDocumentCodec.encode(decoded))
  }

  @Test
  fun operationPayloadsMatchTheCurrentAndroidxContract() {
    val shader = RcWireWriter()
    RcDocumentCodec.encodeOperation(
      shader,
      RcShaderData(
        1,
        2,
        linkedMapOf("x" to listOf(RcFloatWord.literal(1f))),
        linkedMapOf("i" to listOf(7)),
        linkedMapOf("b" to 3),
      ),
    )
    assertContentEquals(
      hex(
        "2d 00000001 00000002 00010101 " +
          "00000001 78 00000001 3f800000 " +
          "00000001 69 00000001 00000007 " +
          "00000001 62 00000003"
      ),
      shader.toByteArray(),
    )

    val target = RcWireWriter()
    RcDocumentCodec.encodeOperation(target, RcDrawToBitmap(3, 1, 0xff112233.toInt()))
    assertContentEquals(hex("be 00000003 00000001 ff112233"), target.toByteArray())
  }

  @Test
  fun rejectsOversizedUniformVectorsBeforeReadingTheirPayload() {
    val header =
      RcDocumentCodec.encode(RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), emptyList()))
    val malformed = header + hex("2d 00000001 00000002 00000001 " + "00000001 78 00000401")

    val failure = assertFailsWith<RcWireException> { RcDocumentCodec.decode(malformed) }

    assertEquals("floatUniforms[0].values.length", failure.fieldName)
  }

  @Test
  fun rejectsReservedPackedSizeBits() {
    val header =
      RcDocumentCodec.encode(RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), emptyList()))
    val failure =
      assertFailsWith<RcWireException> {
        RcDocumentCodec.decode(header + hex("2d 00000001 00000002 01000000"))
      }

    assertEquals("sizes", failure.fieldName)
  }

  private fun hex(value: String): ByteArray =
    value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

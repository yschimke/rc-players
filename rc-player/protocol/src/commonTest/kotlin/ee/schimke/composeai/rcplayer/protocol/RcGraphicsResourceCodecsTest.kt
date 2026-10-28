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

  /**
   * remote-core alpha22's component offscreen buffer: `createOffscreenBitmap(imageId[,
   * componentId])` writes a RAW8888, 1 x 1 `BitmapData` with encoding 4 and an empty payload (the
   * active component) or the component id big-endian; `drawComponentToBitmap(componentId,
   * bitmapId)` writes a `DrawToBitmap` whose colour word is the component id.
   */
  @Test
  fun componentOffscreenBuffersRoundTripWithAndroidxWireLayout() {
    val active =
      RcBitmapData(
        820,
        1,
        1,
        RcBitmapData.TYPE_RAW8888,
        RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER,
        ByteArray(0),
      )
    val named = active.copy(imageId = 821, data = byteArrayOf(0, 0, 0, 15))
    val capture = RcDrawToBitmap(821, RcDrawToBitmap.MODE_COMPONENT_ID, 15)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), modern = false),
        listOf(active, named, capture, RcDrawToBitmap(0, 0, 0)),
      )

    val encoded = RcDocumentCodec.encode(document)
    val decoded = RcDocumentCodec.decode(encoded)

    assertEquals(document, decoded)
    assertContentEquals(encoded, RcDocumentCodec.encode(decoded))
    val bitmap = decoded.operations[1] as RcBitmapData
    assertEquals(RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER, bitmap.encoding)
    assertEquals(RcBitmapData.TYPE_RAW8888, bitmap.type)

    val wire = RcWireWriter()
    RcDocumentCodec.encodeOperation(wire, named)
    assertContentEquals(
      hex("65 00000335 00030001 00040001 00000004 0000000f"),
      wire.toByteArray(),
    )
    val target = RcWireWriter()
    RcDocumentCodec.encodeOperation(target, capture)
    assertContentEquals(hex("be 00000335 00000002 0000000f"), target.toByteArray())
  }

  private fun hex(value: String): ByteArray =
    value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

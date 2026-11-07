package androidx.compose.remote.creation.common

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `ShaderData` (`DATA_SHADER`, opcode 45) against the layout AndroidX's `ShaderData.apply` writes:
 * id, the source text id, the uniform counts packed as `floats | ints << 8 | bitmaps << 16`, then
 * each uniform's UTF-8 name and values.
 */
class ShaderDataWriteTest {
  private fun write(block: RemoteDocumentWriter.() -> Unit): ByteArray {
    val header = RemoteDocumentWriter(100, 100).encodeToByteArray().size
    val bytes = RemoteDocumentWriter(100, 100).apply(block).encodeToByteArray()
    return bytes.copyOfRange(header, bytes.size)
  }

  @Test
  fun aShaderWritesItsSourceThenItsUniforms() {
    var sourceId = 0
    var shaderId = 0
    val time = Utils.asNan(30)
    val bytes = write {
      sourceId = addText("half4 main(float2 p) { return half4(1); }")
      shaderId =
        addShader(
          sourceId,
          floatUniforms =
            mapOf("tint" to floatArrayOf(0.25f, 0.5f, 0.75f, 1f), "time" to floatArrayOf(time)),
          intUniforms = mapOf("count" to intArrayOf(3)),
          bitmapUniforms = mapOf("image" to 7),
        )
    }
    assertEquals(42, sourceId)
    assertEquals(43, shaderId)
    val text = "half4 main(float2 p) { return half4(1); }"
    assertContentEquals(
      bytes(102) +
        int(42) +
        utf8(text) +
        bytes(45) +
        int(43) +
        int(42) +
        int(2 or (1 shl 8) or (1 shl 16)) +
        utf8("tint") +
        int(4) +
        float(0.25f) +
        float(0.5f) +
        float(0.75f) +
        float(1f) +
        utf8("time") +
        int(1) +
        float(time) +
        utf8("count") +
        int(1) +
        int(3) +
        utf8("image") +
        int(7),
      bytes,
    )
  }

  @Test
  fun uniformValueCountsAreChecked() {
    assertFailsWith<IllegalArgumentException> {
      write { addShader(addText("x"), floatUniforms = mapOf("empty" to FloatArray(0))) }
    }
    assertFailsWith<IllegalArgumentException> {
      write { addShader(addText("x"), intUniforms = mapOf("int5" to IntArray(5))) }
    }
  }

  @Test
  fun aWidgetsProfileDocumentRefusesAShader() {
    val writer = RemoteDocumentWriter(100, 100, profiles = 0x100)
    assertFailsWith<IllegalStateException> { writer.addShader(writer.addText("x")) }
  }

  private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

  private fun int(value: Int) =
    byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte(),
    )

  private fun float(value: Float) = int(value.toRawBits())

  private fun utf8(value: String): ByteArray {
    val encoded = value.encodeToByteArray()
    return int(encoded.size) + encoded
  }
}

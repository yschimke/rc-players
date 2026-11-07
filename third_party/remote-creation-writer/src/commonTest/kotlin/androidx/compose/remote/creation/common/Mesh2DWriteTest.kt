package androidx.compose.remote.creation.common

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `AddMesh2D` / `DrawMesh2D` / `MatrixFromMesh2D` (opcodes 104-106) against the layout AndroidX
 * alpha23's `apply` methods write, and the experimental profile a document using them needs.
 */
class Mesh2DWriteTest {
  private val headerSize = RemoteDocumentWriter(100, 100).encodeToByteArray().size

  @Test
  fun aParametricMeshWritesItsHeaderAndNineGroups() {
    val writer = RemoteDocumentWriter(100, 100)
    val x = floatArrayOf(Mesh2D.U, 100f, AnimatedFloatExpression.MUL)
    val red = floatArrayOf(Utils.asNan(30))
    val meshId = writer.addMesh2D(Mesh2D.LAYOUT_GRID, 3, 2, x = x, red = red)
    writer.drawMesh2D(meshId)
    writer.matrixFromMesh2D(meshId, 0.5f, 0.25f, Mesh2D.MATRIX_ROTATION)
    val bytes = writer.encodeToByteArray()
    val body = bytes.copyOfRange(bytes.size - expectedBodySize(), bytes.size)
    assertEquals(42, meshId)
    assertContentEquals(
      bytes(104) +
        int(42) +
        int(Mesh2D.TYPE_EXPRESSION) +
        int(Mesh2D.LAYOUT_GRID) +
        int(3) +
        int(2) +
        int(0) +
        int(0) +
        int(3) +
        float(x[0]) +
        float(100f) +
        float(x[2]) +
        int(0) +
        int(0) +
        int(0) +
        int(0) +
        int(1) +
        float(red[0]) +
        int(0) +
        int(0) +
        int(0) +
        bytes(105) +
        int(42) +
        int(Mesh2D.BLEND_MODULATE) +
        int(0) +
        bytes(106) +
        int(42) +
        float(0.5f) +
        float(0.25f) +
        int(Mesh2D.MATRIX_ROTATION),
      body,
    )
  }

  @Test
  fun aMeshDocumentDeclaresTheExperimentalAndroidxProfile() {
    val writer = RemoteDocumentWriter(100, 100)
    writer.drawMesh2D(writer.addMesh2D(Mesh2D.LAYOUT_GRID, 2, 2))
    val bytes = writer.encodeToByteArray()
    // The header grows by the profiles property and names AndroidX's experimental profile.
    assertEquals(0x201, profilesOf(bytes))
    val widgets = RemoteDocumentWriter(100, 100, profiles = 0x100)
    widgets.drawMesh2D(widgets.addMesh2D(Mesh2D.LAYOUT_GRID, 2, 2))
    assertEquals(0x101, profilesOf(widgets.encodeToByteArray()))
  }

  @Test
  fun literalAndHalfFloatMeshesWriteTheirValues() {
    val writer = RemoteDocumentWriter(100, 100)
    writer.addMesh2DValues(
      intArrayOf(0, 1, 2),
      floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
      colors = intArrayOf(-1, -1, -1),
      halfFloat = true,
    )
    val bytes = writer.encodeToByteArray()
    // header ints (7) + index count + 3 shorts + 3 counts + 6 half verts + 3 colours
    val size = 1 + 7 * 4 + 4 + 3 * 2 + 3 * 4 + 6 * 2 + 3 * 4
    val body = bytes.copyOfRange(bytes.size - size, bytes.size)
    assertContentEquals(
      bytes(104) +
        int(42) +
        int(Mesh2D.TYPE_F16_VALUES) +
        int(Mesh2D.LAYOUT_GRID) +
        int(0) +
        int(0) +
        int(0) +
        int(0) +
        int(3) +
        short(0) +
        short(1) +
        short(2) +
        int(6) +
        int(0) +
        int(3) +
        short(0) +
        short(0) +
        short(0x3c00) +
        short(0) +
        short(0) +
        short(0x3c00) +
        int(-1) +
        int(-1) +
        int(-1),
      body,
    )
  }

  @Test
  fun limitsAreChecked() {
    val writer = RemoteDocumentWriter(100, 100)
    assertFailsWith<IllegalArgumentException> {
      writer.addMesh2D(Mesh2D.LAYOUT_GRID, 2, 2, x = FloatArray(33))
    }
    assertFailsWith<IllegalArgumentException> { writer.addMesh2D(Mesh2D.LAYOUT_GRID, 200, 200) }
    // A skirt fades the vertex colours, so it needs some.
    assertFailsWith<IllegalArgumentException> {
      writer.addMesh2D(Mesh2D.LAYOUT_GRID, 2, 2, antialiasWidth = 1f)
    }
  }

  private fun expectedBodySize(): Int = (1 + 7 * 4 + 9 * 4 + 4 * 4) + (1 + 3 * 4) + (1 + 4 * 4)

  /** The `DOC_PROFILES` (14) header property's value, or 0 when the header has none. */
  private fun profilesOf(bytes: ByteArray): Int {
    // opcode, magic|major, minor, patch, property count, then (tag, size, payload) per property.
    val count = readInt(bytes, 13)
    var offset = 17
    repeat(count) {
      val tag = readShort(bytes, offset) and 0x3ff
      val size = readShort(bytes, offset + 2)
      if (tag == 14) return readInt(bytes, offset + 4)
      offset += 4 + size
    }
    return 0
  }

  private fun readShort(bytes: ByteArray, at: Int): Int =
    ((bytes[at].toInt() and 0xff) shl 8) or (bytes[at + 1].toInt() and 0xff)

  private fun readInt(bytes: ByteArray, at: Int): Int =
    ((bytes[at].toInt() and 0xff) shl 24) or
      ((bytes[at + 1].toInt() and 0xff) shl 16) or
      ((bytes[at + 2].toInt() and 0xff) shl 8) or
      (bytes[at + 3].toInt() and 0xff)

  private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

  private fun short(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())

  private fun int(value: Int) =
    byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte(),
    )

  private fun float(value: Float) = int(value.toRawBits())
}

package androidx.compose.remote.creation.common

import kotlin.test.Test
import kotlin.test.assertEquals

class BorderModifierWriteTest {
  @Test
  fun writesAndroidxBorderWireVersion() {
    val bytes =
      RemoteDocumentWriter(width = 100, height = 100).run {
        writeModifier(
          RemoteModifierOperation.Border(
            width = 2f,
            roundedCorner = 8f,
            color = 0xFF112233.toInt(),
            dynamicColor = false,
            shapeType = 2,
          )
        )
        encodeToByteArray()
      }

    // The border record: opcode 107, then flags, colour id, wire version and a reserved int.
    // It is the document's last record: 4 ints, 6 floats and the shape int follow the opcode.
    val start = bytes.size - 1 - 11 * 4
    assertEquals(107, bytes[start].toInt())

    fun int(index: Int): Int =
      (0 until 4).fold(0) { acc, i ->
        (acc shl 8) or (bytes[start + 1 + 4 * index + i].toInt() and 0xff)
      }

    assertEquals(listOf(0, 0, 1, 0), (0 until 4).map(::int))
  }
}

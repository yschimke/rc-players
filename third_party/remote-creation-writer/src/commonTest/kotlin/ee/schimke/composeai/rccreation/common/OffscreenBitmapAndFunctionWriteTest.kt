package ee.schimke.composeai.rccreation.common

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Component offscreen buffers and float functions (remote-core alpha22's custom visibility
 * animations), against the bytes AndroidX's `RemoteComposeBuffer` writes for each: `BitmapData`
 * with `ENCODING_COMPONENT_OFFSCREEN_BUFFER`, `DrawToBitmap`, `FunctionDefine` and `FunctionCall`.
 */
class OffscreenBitmapAndFunctionWriteTest {
  private fun write(block: RemoteDocumentWriter.() -> Unit): ByteArray {
    val header = RemoteDocumentWriter(100, 100).encodeToByteArray().size
    val bytes = RemoteDocumentWriter(100, 100).apply(block).encodeToByteArray()
    return bytes.copyOfRange(header, bytes.size)
  }

  @Test
  fun anOffscreenBitmapForTheActiveComponentHasNoPayload() {
    var id = 0
    val bytes = write { id = createOffscreenBitmap() }
    assertEquals(42, id)
    // BitmapData: id, TYPE_RAW8888 << 16 | width 1, ENCODING_COMPONENT_OFFSCREEN_BUFFER << 16 |
    // height 1, no payload.
    assertContentEquals(bytes(101) + int(42) + int(0x0003_0001) + int(0x0004_0001) + int(0), bytes)
  }

  @Test
  fun anOffscreenBitmapForAComponentCarriesItsIdBigEndian() {
    val bytes = write { createOffscreenBitmap(componentId = -0x01020305) }
    assertContentEquals(
      bytes(101) + int(42) + int(0x0003_0001) + int(0x0004_0001) + int(4) + int(-0x01020305),
      bytes,
    )
  }

  @Test
  fun drawComponentToBitmapRedirectsDrawsTheContentAndReturns() {
    assertContentEquals(
      bytes(190) + int(42) + int(2) + int(-7) + bytes(139) + bytes(190) + int(0) + int(0) + int(0),
      write { drawComponentToBitmap(componentId = -7, bitmapId = 42) },
    )
    assertContentEquals(
      bytes(190) + int(42) + int(0) + int(0) + bytes(139) + bytes(190) + int(0) + int(0) + int(0),
      write { drawComponentToBitmap(bitmapId = 42) },
    )
  }

  @Test
  fun aFunctionAllocatesItsIdThenItsParameters() {
    val args = FloatArray(6)
    var id = 0
    val bytes = write {
      id = createFloatFunction(args)
      endFloatFunction()
    }
    assertEquals(42, id)
    assertEquals((43..48).toList(), args.map { Utils.idFromNan(it) })
    assertContentEquals(
      bytes(168) +
        int(42) +
        int(6) +
        (43..48).fold(ByteArray(0)) { a, p -> a + int(p) } +
        bytes(214),
      bytes,
    )
  }

  @Test
  fun aFunctionCallWritesItsArguments() {
    assertContentEquals(
      bytes(166) + int(42) + int(2) + int(1.5f.toRawBits()) + int(Utils.asNan(43).toRawBits()),
      write { callFloatFunction(42, 1.5f, Utils.asNan(43)) },
    )
  }

  /**
   * A function's body is evaluated only when the player runs it, so it keeps its own declarations
   * in order. The definition is written where it is made, as AndroidX's writer writes it.
   */
  @Test
  fun aFunctionBodyKeepsItsDeclarationsInlineWhereItIsDefined() {
    val args = FloatArray(6)
    var bitmap = 0
    var half = 0f
    val bytes = write {
      root {
        createFloatFunction(args)
        bitmap = createOffscreenBitmap(Utils.idFromNan(args[5]))
        half = floatExpression(args[0], 0.5f, AnimatedFloatExpression.MUL)
        drawComponentToBitmap(Utils.idFromNan(args[5]), bitmap)
        endFloatFunction()
      }
    }
    assertEquals(
      listOf(
        200, // LayoutRoot
        168, // FunctionDefine, in the root
        101, // its offscreen buffer
        81, // its expression
        190,
        139,
        190,
        214, // end of the function
        214,
      ),
      opcodes(bytes),
    )
    assertEquals(49, bitmap)
    assertEquals(50, Utils.idFromNan(half))
  }

  /** In a global section the same definition goes ahead of the root, whole. */
  @Test
  fun aFunctionDefinedInAGlobalSectionGoesAheadOfTheRoot() {
    val args = FloatArray(6)
    val bytes = write {
      root {
        beginGlobal()
        createFloatFunction(args)
        floatExpression(args[0], 0.5f, AnimatedFloatExpression.MUL)
        endFloatFunction()
        endGlobal()
      }
    }
    assertEquals(listOf(168, 81, 214, 200, 214), opcodes(bytes))
  }

  @Test
  fun nothingDeclaredInAFunctionBodyIsReusedAfterIt() {
    val inside = FloatArray(1)
    val bytes = write {
      val before = addText("out")
      createFloatFunction(inside)
      // Declared before the function, so reused inside it.
      assertEquals(before, addText("out"))
      val text = addText("in")
      endFloatFunction()
      assertNotEquals(text, addText("in"))
    }
    // The first text, the function and its body, then the text re-declared after it.
    assertEquals(listOf(102, 168, 102, 214, 102), opcodes(bytes))
  }

  @Test
  fun aFunctionResetsThePaintInsideAndAfterIt() {
    RemoteDocumentWriter(100, 100).apply {
      checkAndClearForceSendingNewPaint()
      createFloatFunction(FloatArray(0))
      assertEquals(true, checkAndClearForceSendingNewPaint())
      assertEquals(false, checkAndClearForceSendingNewPaint())
      endFloatFunction()
      assertEquals(true, checkAndClearForceSendingNewPaint())
    }
  }

  @Test
  fun endingAFunctionThatWasNotStartedFails() {
    assertFailsWith<IllegalStateException> { RemoteDocumentWriter(100, 100).endFloatFunction() }
  }

  /**
   * remote-core reads `DrawToBitmap` only under the AndroidX or widgets profile, so a document that
   * draws to a bitmap names AndroidX's unless it already names one of them.
   */
  @Test
  fun drawingToABitmapNeedsAProfileThatReadsIt() {
    fun profiles(declared: Int, draw: Boolean): Int {
      val writer = RemoteDocumentWriter(100, 100, profiles = declared)
      if (draw) writer.drawComponentToBitmap(writer.createOffscreenBitmap())
      return headerProfiles(writer.encodeToByteArray())
    }
    assertEquals(0, profiles(declared = 0, draw = false))
    assertEquals(0x200, profiles(declared = 0, draw = true))
    assertEquals(0x201, profiles(declared = 0x201, draw = true))
    assertEquals(0x100, profiles(declared = 0x100, draw = true))
    assertEquals(0x208, profiles(declared = 0x8, draw = true))
  }

  /** The header's `DOC_PROFILES` (tag 14) value. */
  private fun headerProfiles(document: ByteArray): Int {
    val tag = bytes(0, 14, 0, 4)
    val at =
      (0 until document.size - 8).first { document.copyOfRange(it, it + 4).contentEquals(tag) }
    return document.copyOfRange(at + 4, at + 8).fold(0) { value, byte ->
      (value shl 8) or (byte.toInt() and 0xff)
    }
  }

  /** The opcodes of [bytes], for the operations these tests write. */
  private fun opcodes(bytes: ByteArray): List<Int> {
    val result = mutableListOf<Int>()
    var at = 0
    fun int(): Int {
      val value =
        ((bytes[at].toInt() and 0xff) shl 24) or
          ((bytes[at + 1].toInt() and 0xff) shl 16) or
          ((bytes[at + 2].toInt() and 0xff) shl 8) or
          (bytes[at + 3].toInt() and 0xff)
      at += 4
      return value
    }
    while (at < bytes.size) {
      val opcode = bytes[at++].toInt() and 0xff
      result += opcode
      when (opcode) {
        101 -> {
          at += 12
          val length = int()
          at += length
        }
        102 -> {
          at += 4
          val length = int()
          at += length
        }
        81 -> {
          at += 4
          val packed = int()
          at += 4 * ((packed and 0xffff) + (packed ushr 16))
        }
        168 -> {
          at += 4
          val count = int()
          at += 4 * count
        }
        190 -> at += 12
        200 -> at += 4
        139,
        214 -> Unit
        else -> error("unexpected opcode $opcode")
      }
    }
    return result
  }

  private fun id(value: Float): Int = Utils.idFromNan(value)

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

  private fun int(value: Int): ByteArray =
    byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte(),
    )
}

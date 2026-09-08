package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RcBitmapFontCodecTest {
  @Test
  fun allBitmapFontOperationsRoundTripWithDynamicSpacing() {
    val font =
      RcBitmapFontData(
        40,
        listOf(
          glyph("ffi", 50, width = 9, height = 7),
          glyph("f", 51, left = -1, top = -2, right = 2, bottom = 3, width = 4, height = 5),
          glyph(" ", -1, left = 2, right = 3),
        ),
        linkedMapOf("ff" to (-2).toShort(), "f " to 1.toShort()),
      )
    val dynamicSpacing = RcFloatWord(0x7fc0002a)
    val operations =
      listOf(
        font,
        RcDrawBitmapFontTextRun(
          41,
          40,
          0,
          -1,
          RcFloatWord.literal(10f),
          RcFloatWord.literal(20f),
          dynamicSpacing,
        ),
        RcDrawBitmapFontTextRunOnPath(
          41,
          40,
          60,
          1,
          4,
          RcFloatWord.literal(-3f),
          dynamicSpacing,
        ),
        RcBitmapTextMeasure(70, 41, 40, RcBitmapTextMeasure.MEASURE_WIDTH, dynamicSpacing),
        RcDrawBitmapTextAnchored(
          41,
          40,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(-1f),
          RcFloatWord.literal(100f),
          RcFloatWord.literal(80f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(1f),
          dynamicSpacing,
        ),
      )
    val document = RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), operations)

    val encoded = RcDocumentCodec.encode(document)
    val decoded = RcDocumentCodec.decode(encoded)

    assertEquals(document, decoded)
    assertContentEquals(encoded, RcDocumentCodec.encode(decoded))
    assertEquals(-2, assertIs<RcBitmapFontData>(decoded.operations[0]).kerning["ff"])
  }

  @Test
  fun zeroSpacingUsesLegacyPayloadWithoutAnInsertedFloat() {
    val operation =
      RcDrawBitmapFontTextRun(
        41,
        40,
        0,
        -1,
        RcFloatWord.literal(10f),
        RcFloatWord.literal(20f),
      )
    val bytes =
      RcDocumentCodec.encode(
        RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), listOf(operation))
      )
    val operationOffset = bytes.indexOf(RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN.toByte())

    // opcode + six four-byte fields: text, font, start, end, x and y.
    assertEquals(25, bytes.size - operationOffset)
    assertEquals(operation, RcDocumentCodec.decode(bytes).operations.single())
  }

  @Test
  fun payloadMatchesAndroidXFlagVersionAndSignedShortLayout() {
    val draw = RcWireWriter()
    RcDocumentCodec.encodeOperation(
      draw,
      RcDrawBitmapFontTextRun(
        41,
        40,
        1,
        -1,
        RcFloatWord.literal(2f),
        RcFloatWord.literal(3f),
        RcFloatWord.literal(1.5f),
      ),
    )
    assertContentEquals(
      hex("30 80000029 3fc00000 00000028 00000001 ffffffff 40000000 40400000"),
      draw.toByteArray(),
    )

    val data = RcWireWriter()
    RcDocumentCodec.encodeOperation(
      data,
      RcBitmapFontData(
        40,
        listOf(glyph("A", 50, left = -1, top = -2, right = 2, bottom = 3, width = 4, height = 5)),
        linkedMapOf("AA" to (-2).toShort()),
      ),
    )
    assertContentEquals(
      hex(
        "a7 00000028 00010001 00000001 41 00000032 ffff fffe 0002 0003 0004 0005 " +
          "0001 00000002 4141 fffe"
      ),
      data.toByteArray(),
    )
  }

  @Test
  fun anchoredFlaggedTextIdKeepsAndroidXLowSixteenBitCompatibility() {
    val operation =
      RcDrawBitmapTextAnchored(
        textId = 0x12345,
        bitmapFontId = 40,
        start = RcFloatWord.literal(0f),
        end = RcFloatWord.literal(-1f),
        x = RcFloatWord.literal(0f),
        y = RcFloatWord.literal(0f),
        panX = RcFloatWord.literal(0f),
        panY = RcFloatWord.literal(0f),
        glyphSpacing = RcFloatWord.literal(1f),
      )
    val bytes =
      RcDocumentCodec.encode(
        RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), listOf(operation))
      )

    assertEquals(
      0x2345,
      assertIs<RcDrawBitmapTextAnchored>(RcDocumentCodec.decode(bytes).operations.single()).textId,
    )
  }

  @Test
  fun rejectsEmptyGlyphTextAtTheModelBoundary() {
    assertFailsWith<IllegalArgumentException> { RcBitmapFontGlyph("", 1, 0, 0, 0, 0, 1, 1) }
  }

  private fun glyph(
    chars: String,
    bitmapId: Int,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    width: Int = 0,
    height: Int = 0,
  ): RcBitmapFontGlyph =
    RcBitmapFontGlyph(
      chars,
      bitmapId,
      left.toShort(),
      top.toShort(),
      right.toShort(),
      bottom.toShort(),
      width.toShort(),
      height.toShort(),
    )

  private fun hex(value: String): ByteArray =
    value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

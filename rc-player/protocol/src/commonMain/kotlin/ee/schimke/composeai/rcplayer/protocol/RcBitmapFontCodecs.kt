package ee.schimke.composeai.rcplayer.protocol

/** One independently stored image and its metrics in an AndroidX bitmap font. */
public data class RcBitmapFontGlyph(
  val chars: String,
  val bitmapId: Int,
  val marginLeft: Short,
  val marginTop: Short,
  val marginRight: Short,
  val marginBottom: Short,
  val bitmapWidth: Short,
  val bitmapHeight: Short,
) {
  init {
    require(chars.isNotEmpty()) { "Bitmap-font glyph text must not be empty" }
  }
}

/** Bitmap-font glyph metadata. Glyph images are supplied by separate [RcBitmapData] operations. */
public data class RcBitmapFontData(
  val fontId: Int,
  val glyphs: List<RcBitmapFontGlyph>,
  val kerning: Map<String, Short> = emptyMap(),
) : RcOperation {
  override val opcode: Int = RcOpcodes.DATA_BITMAP_FONT

  init {
    require(glyphs.size < 0x10000) { "A bitmap font supports at most 65535 glyphs" }
    require(kerning.size < 0x10000) { "A bitmap font supports at most 65535 kerning pairs" }
  }

  /** AndroidX tests longer glyph strings first, allowing a glyph to represent several chars. */
  public fun lookupGlyph(text: String, offset: Int): RcBitmapFontGlyph? =
    glyphs
      .asSequence()
      .filter { glyph -> text.regionMatches(offset, glyph.chars, 0, glyph.chars.length) }
      .maxByOrNull { it.chars.length }
}

public data class RcDrawBitmapFontTextRun(
  val textId: Int,
  val bitmapFontId: Int,
  val start: Int,
  val end: Int,
  val x: RcFloatWord,
  val y: RcFloatWord,
  val glyphSpacing: RcFloatWord = RcFloatWord.literal(0f),
) : RcOperation {
  override val opcode: Int = RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN
}

public data class RcDrawBitmapFontTextRunOnPath(
  val textId: Int,
  val bitmapFontId: Int,
  val pathId: Int,
  val start: Int,
  val end: Int,
  val yAdjustment: RcFloatWord,
  val glyphSpacing: RcFloatWord = RcFloatWord.literal(0f),
) : RcOperation {
  override val opcode: Int = RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH
}

public data class RcBitmapTextMeasure(
  val outId: Int,
  val textId: Int,
  val bitmapFontId: Int,
  val type: Int,
  val glyphSpacing: RcFloatWord = RcFloatWord.literal(0f),
) : RcOperation {
  override val opcode: Int = RcOpcodes.BITMAP_TEXT_MEASURE

  public companion object {
    public const val MEASURE_WIDTH: Int = 0
    public const val MEASURE_HEIGHT: Int = 1
    public const val MEASURE_LEFT: Int = 2
    public const val MEASURE_RIGHT: Int = 3
    public const val MEASURE_TOP: Int = 4
    public const val MEASURE_BOTTOM: Int = 5
  }
}

public data class RcDrawBitmapTextAnchored(
  val textId: Int,
  val bitmapFontId: Int,
  val start: RcFloatWord,
  val end: RcFloatWord,
  val x: RcFloatWord,
  val y: RcFloatWord,
  val panX: RcFloatWord,
  val panY: RcFloatWord,
  val glyphSpacing: RcFloatWord = RcFloatWord.literal(0f),
) : RcOperation {
  override val opcode: Int = RcOpcodes.DRAW_BITMAP_TEXT_ANCHORED
}

/** Codecs for bitmap-font data, measurement, and draw operations. */
internal val rcBitmapFontOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(
    BitmapFontDataCodec,
    DrawBitmapFontTextRunCodec,
    DrawBitmapFontTextRunOnPathCodec,
    BitmapTextMeasureCodec,
    DrawBitmapTextAnchoredCodec,
  )

private object BitmapFontDataCodec : RcOperationCodec<RcBitmapFontData> {
  override val spec = RcOperationSpec(RcOpcodes.DATA_BITMAP_FONT, "BitmapFontData")

  override fun decode(input: RcWireReader): RcBitmapFontData {
    val fontId = input.readId("fontId")
    val versionAndCount = input.readInt("versionAndGlyphCount")
    val version = versionAndCount ushr 16
    val count = versionAndCount and 0xffff
    if (count > input.limits.maxCollectionEntries) {
      input.fail("glyphs", "Invalid count $count; expected 0..${input.limits.maxCollectionEntries}")
    }
    val glyphs =
      List(count) { index ->
        val prefix = "glyphs[$index]"
        val chars = input.readUtf8("$prefix.chars")
        if (chars.isEmpty()) input.fail("$prefix.chars", "Glyph text must not be empty")
        RcBitmapFontGlyph(
          chars = chars,
          bitmapId = input.readId("$prefix.bitmapId"),
          marginLeft = input.readSignedShort("$prefix.marginLeft"),
          marginTop = input.readSignedShort("$prefix.marginTop"),
          marginRight = input.readSignedShort("$prefix.marginRight"),
          marginBottom = input.readSignedShort("$prefix.marginBottom"),
          bitmapWidth = input.readSignedShort("$prefix.bitmapWidth"),
          bitmapHeight = input.readSignedShort("$prefix.bitmapHeight"),
        )
      }
    val kerning =
      if (version >= 1) {
        val entries = input.readU16("kerning.length")
        if (entries > input.limits.maxCollectionEntries) {
          input.fail(
            "kerning",
            "Invalid count $entries; expected 0..${input.limits.maxCollectionEntries}",
          )
        }
        buildMap {
          repeat(entries) { index ->
            put(
              input.readUtf8("kerning[$index].pair"),
              input.readSignedShort("kerning[$index].adjustment"),
            )
          }
        }
      } else emptyMap()
    return RcBitmapFontData(fontId, glyphs.sortedByDescending { it.chars.length }, kerning)
  }

  override fun encode(output: RcWireWriter, value: RcBitmapFontData) {
    output.writeInt(value.fontId)
    val hasKerning = value.kerning.isNotEmpty()
    output.writeInt(value.glyphs.size or if (hasKerning) (1 shl 16) else 0)
    value.glyphs
      .sortedByDescending { it.chars.length }
      .forEach { glyph ->
        output.writeUtf8(glyph.chars)
        output.writeInt(glyph.bitmapId)
        output.writeSignedShort(glyph.marginLeft)
        output.writeSignedShort(glyph.marginTop)
        output.writeSignedShort(glyph.marginRight)
        output.writeSignedShort(glyph.marginBottom)
        output.writeSignedShort(glyph.bitmapWidth)
        output.writeSignedShort(glyph.bitmapHeight)
      }
    if (hasKerning) {
      output.writeU16(value.kerning.size)
      value.kerning.forEach { (pair, adjustment) ->
        output.writeUtf8(pair)
        output.writeSignedShort(adjustment)
      }
    }
  }
}

private object DrawBitmapFontTextRunCodec : RcOperationCodec<RcDrawBitmapFontTextRun> {
  override val spec = RcOperationSpec(RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN, "DrawBitmapFontText")

  override fun decode(input: RcWireReader): RcDrawBitmapFontTextRun {
    val (textId, spacing) = input.readFlaggedSpacing("textId", 0x7fffffff)
    return RcDrawBitmapFontTextRun(
      textId,
      input.readId("bitmapFontId"),
      input.readInt("start"),
      input.readInt("end"),
      input.readFloatWord("x"),
      input.readFloatWord("y"),
      spacing,
    )
  }

  override fun encode(output: RcWireWriter, value: RcDrawBitmapFontTextRun) {
    output.writeFlaggedSpacing(value.textId, value.glyphSpacing)
    output.writeInt(value.bitmapFontId)
    output.writeInt(value.start)
    output.writeInt(value.end)
    output.writeFloatWord(value.x)
    output.writeFloatWord(value.y)
  }
}

private object DrawBitmapFontTextRunOnPathCodec : RcOperationCodec<RcDrawBitmapFontTextRunOnPath> {
  override val spec =
    RcOperationSpec(RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH, "DrawBitmapFontTextOnPath")

  override fun decode(input: RcWireReader): RcDrawBitmapFontTextRunOnPath {
    val (textId, spacing) = input.readFlaggedSpacing("textId", 0x7fffffff)
    return RcDrawBitmapFontTextRunOnPath(
      textId,
      input.readId("bitmapFontId"),
      input.readId("pathId"),
      input.readInt("start"),
      input.readInt("end"),
      input.readFloatWord("yAdjustment"),
      spacing,
    )
  }

  override fun encode(output: RcWireWriter, value: RcDrawBitmapFontTextRunOnPath) {
    output.writeFlaggedSpacing(value.textId, value.glyphSpacing)
    output.writeInt(value.bitmapFontId)
    output.writeInt(value.pathId)
    output.writeInt(value.start)
    output.writeInt(value.end)
    output.writeFloatWord(value.yAdjustment)
  }
}

private object BitmapTextMeasureCodec : RcOperationCodec<RcBitmapTextMeasure> {
  override val spec = RcOperationSpec(RcOpcodes.BITMAP_TEXT_MEASURE, "BitmapTextMeasure")

  override fun decode(input: RcWireReader): RcBitmapTextMeasure {
    val (outId, spacing) = input.readFlaggedSpacing("outId", 0x7fffffff)
    return RcBitmapTextMeasure(
      outId,
      input.readId("textId"),
      input.readId("bitmapFontId"),
      input.readInt("type"),
      spacing,
    )
  }

  override fun encode(output: RcWireWriter, value: RcBitmapTextMeasure) {
    output.writeFlaggedSpacing(value.outId, value.glyphSpacing)
    output.writeInt(value.textId)
    output.writeInt(value.bitmapFontId)
    output.writeInt(value.type)
  }
}

private object DrawBitmapTextAnchoredCodec : RcOperationCodec<RcDrawBitmapTextAnchored> {
  override val spec = RcOperationSpec(RcOpcodes.DRAW_BITMAP_TEXT_ANCHORED, "DrawBitmapTextAnchored")

  override fun decode(input: RcWireReader): RcDrawBitmapTextAnchored {
    // AndroidX alpha18 intentionally retains only the low 16 bits in this flagged variant.
    val (textId, spacing) = input.readFlaggedSpacing("textId", 0xffff)
    return RcDrawBitmapTextAnchored(
      textId,
      input.readId("bitmapFontId"),
      input.readFloatWord("start"),
      input.readFloatWord("end"),
      input.readFloatWord("x"),
      input.readFloatWord("y"),
      input.readFloatWord("panX"),
      input.readFloatWord("panY"),
      spacing,
    )
  }

  override fun encode(output: RcWireWriter, value: RcDrawBitmapTextAnchored) {
    output.writeFlaggedSpacing(value.textId, value.glyphSpacing)
    output.writeInt(value.bitmapFontId)
    output.writeFloatWord(value.start)
    output.writeFloatWord(value.end)
    output.writeFloatWord(value.x)
    output.writeFloatWord(value.y)
    output.writeFloatWord(value.panX)
    output.writeFloatWord(value.panY)
  }
}

private fun RcWireReader.readFlaggedSpacing(field: String, idMask: Int): Pair<Int, RcFloatWord> {
  val flaggedId = readInt(field)
  return if (flaggedId < 0) {
    resolveId(flaggedId and idMask) to readFloatWord("glyphSpacing")
  } else resolveId(flaggedId) to RcFloatWord.literal(0f)
}

private fun RcWireWriter.writeFlaggedSpacing(id: Int, spacing: RcFloatWord) {
  if (spacing.value == 0f) {
    writeInt(id)
  } else {
    writeInt(id or Int.MIN_VALUE)
    writeFloatWord(spacing)
  }
}

private fun RcWireReader.readSignedShort(field: String): Short = readU16(field).toShort()

private fun RcWireWriter.writeSignedShort(value: Short) {
  writeU16(value.toInt() and 0xffff)
}

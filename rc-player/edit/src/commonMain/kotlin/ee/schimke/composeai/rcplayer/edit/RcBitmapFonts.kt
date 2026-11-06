package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontGlyph
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import ee.schimke.composeai.rcplayer.runtime.RcTextRasterizer
import kotlin.math.ceil
import kotlin.math.round

/**
 * The glyphs a text that changes at playback may show, for [coreTextAsBitmapFonts]: every character
 * it can hold ([chars]) and the most it holds at once ([maxLength]), which sizes its slot.
 */
public data class RcBitmapFontGlyphs(val chars: String, val maxLength: Int) {
  init {
    require(chars.isNotEmpty()) { "A bitmap font needs at least one glyph" }
    require(maxLength > 0) { "maxLength must be positive" }
  }

  public companion object {
    /** Digits and the punctuation clocks, temperatures and percentages show. */
    public fun numeric(maxLength: Int): RcBitmapFontGlyphs =
      RcBitmapFontGlyphs("0123456789:.,-+%° ", maxLength)
  }
}

/** A `CoreText` [coreTextAsBitmapFonts] now draws with a bitmap font. */
public data class RcBitmapFontText(
  val componentId: Int,
  val textId: Int,
  /** The `BitmapFontData` it draws with; texts in the same face, size and colour share one. */
  val fontId: Int,
  /** Its slot's size in dp. */
  val width: Float,
  val height: Float,
  /**
   * Whether its text changes at playback, drawn from [RcBitmapFontGlyphs] rather than its value.
   */
  val dynamic: Boolean,
)

/** A document whose texts [coreTextAsBitmapFonts] converted, and what it did with each. */
public data class RcBitmapFontTexts(
  val document: RcDocument,
  val texts: List<RcBitmapFontText>,
  /**
   * Component ids of texts in a requested family left as they were: styling a bitmap font can't
   * draw, more than one line, a changing text with no glyphs given, or a glyph the rasterizer
   * declined.
   */
  val skipped: List<Int>,
)

/**
 * Every `CoreText` set in one of [families] (by name, ignoring case and a `google:` prefix) drawn
 * instead with a bitmap font: each glyph it can show drawn once by [rasterizer] and embedded, and
 * the text drawn from them at playback. A face no player ships then looks the same on every player
 * and in every profile — launcher and Wear widgets included, which have bitmap fonts at every API
 * level — and, unlike [coreTextAsImages], a text the host or a timer changes keeps the face, as
 * long as [glyphs] says which characters it can show. A clock in an LCD face is the case it is for.
 *
 * A text converts when it is one line, its size, weight, slant and family are constant, and it asks
 * for nothing a bitmap font can't draw: letter spacing becomes the font's glyph spacing, and
 * constant font axes and line height are drawn into the glyphs; decoration and the like leave it as
 * it was, listed in [RcBitmapFontTexts.skipped]. Its text is a static value's characters, or
 * [glyphs] for its component id when the value changes; a changing text [glyphs] gives nothing for
 * is skipped. A text in any other family is left alone, unlisted.
 *
 * The text's component becomes a box with the same id, animation id and modifiers, holding a canvas
 * sized for the text — for a changing one, [RcBitmapFontGlyphs.maxLength] of its widest glyph —
 * that draws it from its start. A colour the player sets at playback stays live: the glyphs are
 * drawn as a white mask the canvas paint tints with that colour id. A constant colour is drawn into
 * the glyphs. Texts sharing a face, size and colour share one font.
 *
 * Each glyph is drawn alone and placed on a whole pixel, so a face reads as itself but not pixel
 * for pixel as `CoreText` lays it out; kerning is not kept, and a joined script loses its joins and
 * any ink outside a glyph's line box. It suits a display face — an LCD clock, a heading — rather
 * than running text in a script.
 *
 * The glyphs are drawn at [display]'s density; at API level 7 the canvas scales them with the
 * device's, as [coreTextAsImages] does its images. Check [sizeReport] against the target's limits.
 */
public fun RcDocument.coreTextAsBitmapFonts(
  target: RcTargetProfile,
  families: Set<String>,
  rasterizer: RcTextRasterizer,
  display: RcDisplay = RcDisplay.DEFAULT,
  glyphs: (componentId: Int) -> RcBitmapFontGlyphs? = { null },
): RcBitmapFontTexts {
  val unchanged = RcBitmapFontTexts(this, emptyList(), emptyList())
  val wanted = families.map { it.lowercase() }.toSet()
  if (wanted.isEmpty() || operations.none { it is RcCoreText }) return unchanged
  val scales = target.apiLevel >= 7
  val required = REQUIRED_OPCODES + if (scales) SCALE_OPCODES else emptySet()
  if (!required.all { it in target.supportedOpcodes }) return unchanged
  val tree = operationTree(operations) ?: return unchanged
  val tintable = RcOpcodes.PAINT_VALUES in target.supportedOpcodes
  val styles = operations.textStyles()
  val values = RcValueAnalysis(hardcodeDensity(display.density, display.fontScale).operations)
  val fontData = operations.filterIsInstance<RcFontData>().associateBy { it.fontId }
  val ids =
    RcFreshIds(
      operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() },
      componentIds(operations),
    )
  val skipped = mutableListOf<Int>()

  // First pass: what each text needs, so a face shared by several texts is drawn once with every
  // glyph any of them shows.
  val plans = mutableListOf<Plan>()
  tree.forEach { root ->
    root.walk { node ->
      val text = node.operation as? RcCoreText ?: return@walk
      val properties = resolvedProperties(text, node.start, styles) ?: return@walk
      fun int(id: Int, default: Int) =
        (properties[id] as? RcTextStyleProperty.IntValue)?.value ?: default
      val familyId = int(P_FONT_FAMILY, -1)
      if (familyId == -1) return@walk
      val family = values.text(familyId, node.start)
      val name = family?.lowercase() ?: return@walk
      if (name !in wanted && name.removePrefix("google:") !in wanted) return@walk
      val plan = plan(text, node, properties, family!!, values, fontData, tintable, glyphs)
      if (plan == null) skipped += text.componentId else plans += plan
    }
  }
  if (plans.isEmpty()) return RcBitmapFontTexts(this, emptyList(), skipped)

  // Second pass: draw each face's glyphs; a face missing one drops every text set in it.
  val definitions = mutableListOf<RcOperation>()
  val fonts = mutableMapOf<Face, DrawnFont>()
  plans
    .groupBy { it.face }
    .forEach { (face, texts) ->
      val chars = texts.flatMap { it.chars.toList() }.distinct().sorted()
      val drawn = mutableListOf<Pair<Char, RcBitmapData>>()
      val advances = mutableMapOf<Char, Int>()
      for (char in chars) {
        val glyph =
          rasterizer.rasterize(face.styled(char.toString(), display.density))?.takeIf {
            it.width in 1..Short.MAX_VALUE && it.height in 1..Short.MAX_VALUE
          }
        // A glyph's image is its advance rounded up to a pixel; the advance itself, to a pixel,
        // from a run of it, so a line keeps its length rather than gaining up to a pixel a glyph.
        val run = glyph?.let {
          rasterizer.rasterize(face.styled(char.toString().repeat(ADVANCE_RUN), display.density))
        }
        if (glyph == null || run == null) {
          texts.forEach { skipped += it.text.componentId }
          return@forEach
        }
        advances[char] = round(run.width.toFloat() / ADVANCE_RUN).toInt().coerceAtMost(glyph.width)
        drawn +=
          char to
            RcBitmapData(
              imageId = ids.next(),
              width = glyph.width,
              height = glyph.height,
              type = RcBitmapData.TYPE_PNG_8888,
              encoding = RcBitmapData.ENCODING_INLINE,
              data = glyph.png,
            )
      }
      val font =
        RcBitmapFontData(
          ids.next(),
          drawn.map { (char, bitmap) ->
            RcBitmapFontGlyph(
              chars = char.toString(),
              bitmapId = bitmap.imageId,
              marginLeft = 0,
              marginTop = 0,
              marginRight = (advances.getValue(char) - bitmap.width).toShort(),
              marginBottom = 0,
              bitmapWidth = bitmap.width.toShort(),
              bitmapHeight = bitmap.height.toShort(),
            )
          },
        )
      definitions += drawn.map { it.second }
      definitions += font
      fonts[face] =
        DrawnFont(
          font.fontId,
          advances,
          drawn.maxOf { it.second.height },
        )
    }

  val converted = mutableListOf<RcBitmapFontText>()
  val replacements = mutableMapOf<Int, Pair<Int, List<RcOperation>>>()
  var scaleId: Int? = null
  for (plan in plans) {
    val font = fonts[plan.face] ?: continue
    val spacing = plan.letterSpacing * plan.face.fontSize
    val pixelWidth =
      if (plan.dynamic != null) {
        (font.widths.values.max() + spacing) * plan.dynamic.maxLength
      } else {
        plan.chars.sumOf { (font.widths.getValue(it) + spacing).toDouble() }.toFloat()
      }
    val width = ceil(pixelWidth) / display.density
    val height = font.height / display.density
    val box = buildList {
      fun size(dp: Float, pixels: Float): RcFloatWord {
        // Below level 7 the document is per display already; above, it scales with the device.
        if (!scales) return RcFloatWord.literal(ceil(pixels))
        val id = ids.next()
        add(
          RcFloatExpression(
            id,
            listOf(RcFloatWord.literal(dp), reference(RcSystemVariables.DENSITY), MULTIPLY),
            null,
          ),
        )
        return reference(id)
      }
      val widthWord = size(width, pixelWidth)
      val heightWord = size(height, font.height.toFloat())
      val scale =
        if (!scales) null
        else
          scaleId
            ?: ids.next().also { id ->
              scaleId = id
              add(
                RcFloatExpression(
                  id,
                  listOf(
                    reference(RcSystemVariables.DENSITY),
                    RcFloatWord.literal(display.density),
                    DIVIDE,
                  ),
                  null,
                ),
              )
            }
      val node = plan.node
      val content = node.children.last()
      add(
        RcBoxLayout(plan.text.componentId, plan.text.animationId, boxAlignment(plan.align), CENTER),
      )
      addAll(operations.subList(node.start + 1, content.start))
      if (RcOpcodes.ACCESSIBILITY_SEMANTICS in target.supportedOpcodes) {
        add(RcAccessibilitySemantics(0, -1, plan.text.textId, 0, 0, true, false))
      }
      add(RcLayoutContent(ids.nextComponent()))
      add(RcCanvasLayout(ids.nextComponent(), -1))
      add(RcWidthModifier(RcDimensionType.EXACT, widthWord))
      add(RcHeightModifier(RcDimensionType.EXACT, heightWord))
      add(RcNoArg(RcOpcodes.CANVAS_OPERATIONS))
      plan.face.tintId?.let {
        add(RcPaintData(listOf(COLOR_FILTER_ID or (BLEND_SRC_IN shl 16), it)))
      }
      if (scale != null) {
        add(RcNoArg(RcOpcodes.MATRIX_SAVE))
        add(
          RcDraw4(
            RcOpcodes.MATRIX_SCALE,
            reference(scale),
            reference(scale),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
          ),
        )
      }
      add(
        RcDrawBitmapFontTextRun(
          plan.text.textId,
          font.fontId,
          0,
          -1,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(spacing),
        ),
      )
      if (scale != null) add(RcNoArg(RcOpcodes.MATRIX_RESTORE))
      repeat(4) { add(END) }
    }
    replacements[plan.node.start] = plan.node.end to box
    converted +=
      RcBitmapFontText(
        plan.text.componentId,
        plan.text.textId,
        font.fontId,
        width,
        height,
        plan.dynamic != null,
      )
  }
  if (replacements.isEmpty()) return RcBitmapFontTexts(this, emptyList(), skipped)

  // The fonts go ahead of the first text that draws with one.
  val first = replacements.keys.min()
  val rewritten = buildList {
    var index = 0
    while (index < operations.size) {
      if (index == first) addAll(definitions)
      val replacement = replacements[index]
      if (replacement == null) {
        add(operations[index++])
      } else {
        addAll(replacement.second)
        index = replacement.first + 1
      }
    }
  }
  return RcBitmapFontTexts(copy(operations = rewritten), converted, skipped.distinct())
}

/** One text's conversion, or null if it can't be drawn with a bitmap font. */
private fun plan(
  text: RcCoreText,
  node: RcOperationNode,
  properties: Map<Int, RcTextStyleProperty>,
  family: String,
  values: RcValueAnalysis,
  fontData: Map<Int, RcFontData>,
  tintable: Boolean,
  glyphs: (Int) -> RcBitmapFontGlyphs?,
): Plan? {
  val content = node.children.lastOrNull()
  if (content?.operation !is RcLayoutContent || content.children.isNotEmpty()) return null
  val kept = properties.filter { (_, value) -> !value.isDefault() }.keys - DRAWN_PROPERTIES
  if (kept.isNotEmpty()) return null
  val at = node.start
  fun int(id: Int, default: Int) =
    (properties[id] as? RcTextStyleProperty.IntValue)?.value ?: default
  fun float(id: Int, default: Float): Float? {
    val word = (properties[id] as? RcTextStyleProperty.FloatValue)?.value ?: return default
    val reference = word.referencedId ?: return word.value
    return values.float(reference, at)?.takeIf { it.referencedId == null }?.value
  }
  val fontSize = float(P_FONT_SIZE, 36f) ?: return null
  val weight = float(P_FONT_WEIGHT, 400f) ?: return null
  val letterSpacing = float(P_LETTER_SPACING, 0f) ?: return null
  val lineHeightAdd = float(P_LINE_HEIGHT_ADD, 0f) ?: return null
  val lineHeightMultiplier = float(P_LINE_HEIGHT_MULTIPLIER, 1f) ?: return null
  val style = int(P_FONT_STYLE, 0)
  val align = int(P_TEXT_ALIGN, 1)
  if (align ushr 16 != 0) return null
  val colorId = int(P_COLOR_ID, -1)
  val constantColor = if (colorId == -1) int(P_COLOR, BLACK) else values.color(colorId, at)
  if (constantColor == null && !tintable) return null
  val value = values.text(text.textId, at)
  val dynamic = if (value == null) glyphs(text.componentId) ?: return null else null
  val chars = value ?: dynamic!!.chars
  if ('\n' in chars || chars.isEmpty()) return null
  val tags =
    (properties[P_FONT_AXIS_TAGS] as? RcTextStyleProperty.IntArrayValue)?.values.orEmpty().map {
      values.text(it, at) ?: return null
    }
  val axisValues =
    (properties[P_FONT_AXIS_VALUES] as? RcTextStyleProperty.FloatArrayValue)
      ?.values
      .orEmpty()
      .map { word ->
        val reference = word.referencedId ?: return@map word.value
        values.float(reference, at)?.takeIf { it.referencedId == null }?.value ?: return null
      }
  if (tags.size != axisValues.size) return null
  val face =
    Face(
      family = family,
      fontData = fontData[int(P_FONT_FAMILY, -1)]?.data,
      fontSize = fontSize,
      weight = if (style and 1 != 0) 700 else round(weight).toInt().coerceIn(1, 1000),
      italic = style and 2 != 0,
      color = constantColor ?: WHITE,
      tintId = if (constantColor == null) colorId else null,
      fontAxes = tags.zip(axisValues),
      lineHeightAdd = lineHeightAdd,
      lineHeightMultiplier = lineHeightMultiplier,
    )
  return Plan(text, node, face, chars, dynamic, letterSpacing, align)
}

private class Plan(
  val text: RcCoreText,
  val node: RcOperationNode,
  val face: Face,
  /** The static text, or every glyph a changing one can show. */
  val chars: String,
  val dynamic: RcBitmapFontGlyphs?,
  /** In ems. */
  val letterSpacing: Float,
  val align: Int,
)

/** A face at one size and colour: one bitmap font. */
private class Face(
  val family: String,
  val fontData: ByteArray?,
  val fontSize: Float,
  val weight: Int,
  val italic: Boolean,
  /** The glyphs' colour: the text's, or white for a mask [tintId] tints. */
  val color: Int,
  val tintId: Int?,
  /** Font-variation axes and OpenType features, drawn into every glyph. */
  val fontAxes: List<Pair<String, Float>>,
  /** The line's height, which every glyph's cell shares. */
  val lineHeightAdd: Float,
  val lineHeightMultiplier: Float,
) {
  fun styled(text: String, density: Float) =
    RcStyledText(
      text = text,
      density = density,
      fontSize = fontSize,
      color = color,
      fontWeight = weight,
      italic = italic,
      fontFamily = family,
      fontData = fontData,
      fontAxes = fontAxes,
      lineHeightAdd = lineHeightAdd,
      lineHeightMultiplier = lineHeightMultiplier,
      maxLines = 1,
    )

  private val key
    get() =
      listOf(
        family,
        fontData?.contentHashCode(),
        fontSize,
        weight,
        italic,
        color,
        tintId,
        fontAxes,
        lineHeightAdd,
        lineHeightMultiplier,
      )

  override fun equals(other: Any?): Boolean = other is Face && key == other.key

  override fun hashCode(): Int = key.hashCode()
}

private class DrawnFont(val fontId: Int, val widths: Map<Char, Int>, val height: Int)

/** `CoreText` alignment as a box's horizontal positioning: start, centre or end. */
private fun boxAlignment(align: Int): Int =
  when (align) {
    3 -> 2
    2,
    6 -> 3
    else -> 1
  }

private fun reference(id: Int): RcFloatWord = RcFloatWord(0xff800000.toInt() or id)

private val MULTIPLY =
  RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 3)

private val DIVIDE = RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 4)

private val END = RcNoArg(RcOpcodes.CONTAINER_END)

private const val CENTER = 5

/** How many of a glyph [coreTextAsBitmapFonts] lays out to measure its advance to a pixel. */
private const val ADVANCE_RUN = 16

private const val WHITE = 0xffffffff.toInt()

private const val BLACK = 0xff000000.toInt()

/** AndroidX `PaintBundle.COLOR_FILTER_ID`, with the blend mode in its high half. */
private const val COLOR_FILTER_ID = 20

/** AndroidX `PaintBundle.BLEND_MODE_SRC_IN`: the mask's coverage in the filter's colour. */
private const val BLEND_SRC_IN = 5

private const val P_COLOR = 3
private const val P_COLOR_ID = 4
private const val P_FONT_SIZE = 5
private const val P_FONT_STYLE = 6
private const val P_FONT_WEIGHT = 7
private const val P_FONT_FAMILY = 8
private const val P_TEXT_ALIGN = 9
private const val P_LETTER_SPACING = 12
private const val P_LINE_HEIGHT_ADD = 13
private const val P_LINE_HEIGHT_MULTIPLIER = 14
private const val P_FONT_AXIS_TAGS = 20
private const val P_FONT_AXIS_VALUES = 21

/**
 * What a bitmap-font text draws: colour, size, style, weight, family, alignment, letter spacing,
 * line height and font axes, plus overflow and max lines, which one unwrapped line never reaches.
 */
private val DRAWN_PROPERTIES: Set<Int> =
  setOf(
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10,
    11,
    P_LETTER_SPACING,
    P_LINE_HEIGHT_ADD,
    P_LINE_HEIGHT_MULTIPLIER,
    P_FONT_AXIS_TAGS,
    P_FONT_AXIS_VALUES,
  )

private val REQUIRED_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.DATA_BITMAP,
    RcOpcodes.DATA_BITMAP_FONT,
    RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN,
    RcOpcodes.LAYOUT_BOX,
    RcOpcodes.LAYOUT_CONTENT,
    RcOpcodes.LAYOUT_CANVAS,
    RcOpcodes.CANVAS_OPERATIONS,
    RcOpcodes.MODIFIER_WIDTH,
    RcOpcodes.MODIFIER_HEIGHT,
  )

/** What scaling the glyphs with the device's density needs, from level 7. */
private val SCALE_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.ANIMATED_FLOAT,
    RcOpcodes.MATRIX_SAVE,
    RcOpcodes.MATRIX_SCALE,
    RcOpcodes.MATRIX_RESTORE,
  )

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
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

/** A `CoreText` [coreTextAsImages] drew to an image, and the styling the image kept. */
public data class RcTextImage(
  val componentId: Int,
  val textId: Int,
  /** The properties `TextLayout` could not have said (`letterSpacing`, `fontAxis`, …). */
  val kept: Set<String>,
  /** The image's size in dp, as the document now lays it out. */
  val width: Float,
  val height: Float,
)

/** A document whose styled texts [coreTextAsImages] drew, and what it did with each. */
public data class RcTextImages(
  val document: RcDocument,
  /** The texts now shown as images. */
  val images: List<RcTextImage>,
  /** Component ids of styled texts left as they were: not static, or declined by the rasterizer. */
  val skipped: List<Int>,
)

/**
 * Every styled `CoreText` a [target] without `CoreText` would otherwise lose the styling of, drawn
 * by [rasterizer] and embedded as an image in its place — so letter spacing, line height, font axes
 * and decoration survive on Wear widgets and Android 16 launchers, where [adaptTo] can only keep
 * the text by dropping them ([RcApproximation.TEXT]). Launcher widgets at level 7 have `CoreText`
 * and are left alone.
 *
 * Run it before [adaptTo]; what it leaves is converted there as before. A text is drawn only when
 * it says something `TextLayout` cannot and its text, size, family and axes are constant, read as
 * the player would after [hardcodeDensity] for [display]. Its colour need not be: a colour the
 * player sets at playback — a Wear host's named colour, a `ColorTheme`, which [adaptTo] turns into
 * theme sections for Android 16 — is kept live by drawing the text as a white mask in a canvas
 * whose paint tints it with that colour id. A text a host or timer changes, autosize, or a family
 * neither the document nor [rasterizer] can supply leaves the text as it was, listed in
 * [RcTextImages.skipped].
 *
 * The text's component becomes a box with the same id, animation id and modifiers, holding the
 * image at its laid-out size in dp — an image scaled down only where its slot is narrower, or the
 * tinted canvas — and labelled with the text for accessibility. The image is drawn at [display]'s
 * density and scales with the device's; serve the result to that display. A text wraps, and
 * truncates, at the width in dp [wrapWidth] gives for its component id — the widest its slot can be
 * — or at the document's own width when that is null. A slot narrower than that shows the image
 * smaller, never cropped: give the slot's width where it is known to be narrower than the screen.
 *
 * Images are larger than text: check [sizeReport] against the target's limits.
 */
public fun RcDocument.coreTextAsImages(
  target: RcTargetProfile,
  rasterizer: RcTextRasterizer,
  display: RcDisplay = RcDisplay.DEFAULT,
  wrapWidth: (componentId: Int) -> Float? = { null },
): RcTextImages {
  val unchanged = RcTextImages(this, emptyList(), emptyList())
  if (RcOpcodes.CORE_TEXT in target.supportedOpcodes) return unchanged
  if (!REQUIRED_OPCODES.all { it in target.supportedOpcodes }) return unchanged
  if (operations.none { it is RcCoreText }) return unchanged
  val tree = operationTree(operations) ?: return unchanged
  val tintable = TINT_OPCODES.all { it in target.supportedOpcodes }
  val documentWidth = header.width / (header.density.takeIf { it > 0f } ?: 1f)
  val styles = operations.textStyles()
  // Same positions, density and font scale baked: what this display's player reads.
  val values = RcValueAnalysis(hardcodeDensity(display.density, display.fontScale).operations)
  val fonts = operations.filterIsInstance<RcFontData>().associateBy { it.fontId }
  val ids =
    RcFreshIds(
      operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() },
      componentIds(operations),
    )
  val images = mutableListOf<RcTextImage>()
  val skipped = mutableListOf<Int>()
  val replacements = mutableMapOf<Int, Pair<Int, List<RcOperation>>>()

  tree.forEach { root ->
    root.walk { node ->
      val text = node.operation as? RcCoreText ?: return@walk
      val properties = resolvedProperties(text, node.start, styles) ?: return@walk
      val kept =
        properties.filter { (_, value) -> !value.isDefault() }.keys - TEXT_LAYOUT_PROPERTIES
      if (kept.isEmpty()) return@walk
      val styled =
        if (kept.all { it in DRAWABLE.keys }) {
          val wrapAt = wrapWidth(text.componentId) ?: documentWidth
          styledText(text, properties, node.start, values, fonts, display, wrapAt, tintable)
        } else null
      val drawn = styled?.let(rasterizer::rasterize)
      val content = node.children.lastOrNull()
      if (
        drawn == null ||
          drawn.width <= 0 ||
          drawn.height <= 0 ||
          content?.operation !is RcLayoutContent ||
          content.children.isNotEmpty()
      ) {
        skipped += text.componentId
        return@walk
      }
      val width = drawn.width / display.density
      val height = drawn.height / display.density
      val bitmapId = ids.next()
      val definitions = mutableListOf<RcOperation>()
      definitions +=
        RcBitmapData(
          imageId = bitmapId,
          width = drawn.width,
          height = drawn.height,
          type = RcBitmapData.TYPE_PNG_8888,
          encoding = RcBitmapData.ENCODING_INLINE,
          data = drawn.png,
        )
      fun size(dp: Float, pixels: Int): RcFloatWord {
        // Below level 7 the document is per display already; above, it scales with the device.
        if (target.apiLevel < 7) return RcFloatWord.literal(pixels.toFloat())
        val id = ids.next()
        definitions +=
          RcFloatExpression(
            id,
            listOf(RcFloatWord.literal(dp), reference(RcSystemVariables.DENSITY), MULTIPLY),
            null,
          )
        return reference(id)
      }
      val widthWord = size(width, drawn.width)
      val heightWord = size(height, drawn.height)
      val align = (properties[P_TEXT_ALIGN] as? RcTextStyleProperty.IntValue)?.value ?: 1
      // Everything between the component and its content: modifiers, containers among them.
      val modifiers = operations.subList(node.start + 1, content.start)
      val semantics =
        if (RcOpcodes.ACCESSIBILITY_SEMANTICS !in target.supportedOpcodes) emptyList()
        else listOf(RcAccessibilitySemantics(0, -1, text.textId, 0, 0, true, false))
      val box = buildList {
        addAll(definitions)
        add(RcBoxLayout(text.componentId, text.animationId, boxAlignment(align), CENTER))
        addAll(modifiers)
        addAll(semantics)
        add(RcLayoutContent(ids.nextComponent()))
        val tint = (properties[P_COLOR_ID] as? RcTextStyleProperty.IntValue)?.value
        if (tint != null && tint != -1 && values.color(tint, node.start) == null) {
          // Drawn through a paint that tints the mask with the live colour, as the text was.
          add(RcCanvasLayout(ids.nextComponent(), -1))
          add(RcWidthModifier(RcDimensionType.EXACT, widthWord))
          add(RcHeightModifier(RcDimensionType.EXACT, heightWord))
          add(RcNoArg(RcOpcodes.CANVAS_OPERATIONS))
          add(RcPaintData(listOf(COLOR_FILTER_ID or (BLEND_SRC_IN shl 16), tint)))
          add(
            RcDrawBitmap(
              bitmapId,
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              widthWord,
              heightWord,
              0,
            )
          )
          add(END)
          add(END)
        } else {
          add(
            RcImageLayout(ids.nextComponent(), -1, bitmapId, SCALE_INSIDE, RcFloatWord.literal(1f))
          )
          add(RcWidthModifier(RcDimensionType.EXACT, widthWord))
          add(RcHeightModifier(RcDimensionType.EXACT, heightWord))
          add(END)
        }
        add(END)
        add(END)
      }
      replacements[node.start] = node.end to box
      images +=
        RcTextImage(
          text.componentId,
          text.textId,
          kept.map(DRAWABLE::getValue).toSet(),
          width,
          height,
        )
    }
  }
  if (replacements.isEmpty()) return RcTextImages(this, emptyList(), skipped)
  val rewritten = buildList {
    var index = 0
    while (index < operations.size) {
      val replacement = replacements[index]
      if (replacement == null) {
        add(operations[index++])
      } else {
        addAll(replacement.second)
        index = replacement.first + 1
      }
    }
  }
  return RcTextImages(copy(operations = rewritten), images, skipped)
}

/** [text] with every value the image shows constant at [at], or null if one is not. */
private fun styledText(
  text: RcCoreText,
  properties: Map<Int, RcTextStyleProperty>,
  at: Int,
  values: RcValueAnalysis,
  fonts: Map<Int, RcFontData>,
  display: RcDisplay,
  wrapWidth: Float,
  tintable: Boolean,
): RcStyledText? {
  fun int(id: Int, default: Int): Int =
    (properties[id] as? RcTextStyleProperty.IntValue)?.value ?: default
  fun bool(id: Int): Boolean = (properties[id] as? RcTextStyleProperty.BooleanValue)?.value == true
  fun constant(word: RcFloatWord): Float? {
    val id = word.referencedId ?: return word.value
    return values.float(id, at)?.takeIf { it.referencedId == null }?.value
  }
  fun float(id: Int, default: Float): Float? {
    val word = (properties[id] as? RcTextStyleProperty.FloatValue)?.value ?: return default
    return constant(word)
  }

  val string = values.text(text.textId, at) ?: return null
  val colorId = int(P_COLOR_ID, -1)
  // A colour the host or theme sets at playback is drawn as a white mask the player tints.
  val color =
    if (colorId == -1) int(P_COLOR, 0xff000000.toInt())
    else values.color(colorId, at) ?: if (tintable) WHITE else return null
  val fontStyle = int(P_FONT_STYLE, 0)
  val weight = (float(P_FONT_WEIGHT, 400f) ?: return null).let { kotlin.math.round(it).toInt() }
  val familyId = int(P_FONT_FAMILY, -1)
  val family = if (familyId == -1) null else values.text(familyId, at)
  val fontData = fonts[familyId]?.data
  if (familyId != -1 && family == null && fontData == null) return null
  val tags =
    (properties[P_FONT_AXIS_TAGS] as? RcTextStyleProperty.IntArrayValue)?.values.orEmpty().map {
      values.text(it, at) ?: return null
    }
  val axisValues =
    (properties[P_FONT_AXIS_VALUES] as? RcTextStyleProperty.FloatArrayValue)?.values.orEmpty().map {
      constant(it) ?: return null
    }
  val align = int(P_TEXT_ALIGN, 1)
  if (align ushr 16 != 0) return null
  return RcStyledText(
    text = string,
    density = display.density,
    fontSize = float(P_FONT_SIZE, 36f) ?: return null,
    color = color,
    fontWeight = if (fontStyle and 1 != 0) 700 else weight.coerceIn(1, 1000),
    italic = fontStyle and 2 != 0,
    fontFamily = family,
    fontData = fontData,
    letterSpacing = float(12, 0f) ?: return null,
    lineHeightAdd = float(13, 0f) ?: return null,
    lineHeightMultiplier = float(14, 1f) ?: return null,
    fontAxes = tags.zip(axisValues),
    textAlign = align,
    justification = int(17, 0),
    lineBreak = int(15, 0),
    hyphenation = int(16, 0),
    underline = bool(18),
    strikethrough = bool(19),
    overflow = int(P_OVERFLOW, 1),
    maxLines = int(P_MAX_LINES, Int.MAX_VALUE),
    maxWidth = wrapWidth * display.density,
  )
}

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

private val END = RcNoArg(RcOpcodes.CONTAINER_END)

private const val CENTER = 5

private const val WHITE = 0xffffffff.toInt()

/** AndroidX `PaintBundle.COLOR_FILTER_ID`, with the blend mode in its high half. */
private const val COLOR_FILTER_ID = 20

/** AndroidX `PaintBundle.BLEND_MODE_SRC_IN`: the mask's coverage in the filter's colour. */
private const val BLEND_SRC_IN = 5

/** What drawing a mask tinted by a colour id needs. */
private val TINT_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.LAYOUT_CANVAS,
    RcOpcodes.CANVAS_OPERATIONS,
    RcOpcodes.PAINT_VALUES,
    RcOpcodes.DRAW_BITMAP,
  )

/** AndroidX `ImageScaling.SCALE_INSIDE`: as drawn, or smaller to fit, never larger. */
private const val SCALE_INSIDE = 1

private const val P_COLOR = 3
private const val P_COLOR_ID = 4
private const val P_FONT_SIZE = 5
private const val P_FONT_STYLE = 6
private const val P_FONT_WEIGHT = 7
private const val P_FONT_FAMILY = 8
private const val P_TEXT_ALIGN = 9
private const val P_OVERFLOW = 10
private const val P_MAX_LINES = 11
private const val P_FONT_AXIS_TAGS = 20
private const val P_FONT_AXIS_VALUES = 21

/** The properties `TextLayout` has a field for; a text needing only these converts exactly. */
private val TEXT_LAYOUT_PROPERTIES: Set<Int> = setOf(3, 4, 5, 6, 7, 8, 9, 10, 11)

/** The properties an image keeps, by the name [RcTextImage.kept] reports. */
private val DRAWABLE: Map<Int, String> =
  mapOf(
    12 to "letterSpacing",
    13 to "lineHeightAdd",
    14 to "lineHeightMultiplier",
    15 to "lineBreakStrategy",
    16 to "hyphenationFrequency",
    17 to "justificationMode",
    18 to "underline",
    19 to "strikethrough",
    20 to "fontAxis",
    21 to "fontAxisValues",
  )

private val REQUIRED_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.DATA_BITMAP,
    RcOpcodes.LAYOUT_BOX,
    RcOpcodes.LAYOUT_IMAGE,
    RcOpcodes.LAYOUT_CONTENT,
    RcOpcodes.MODIFIER_WIDTH,
    RcOpcodes.MODIFIER_HEIGHT,
    RcOpcodes.ANIMATED_FLOAT,
  )

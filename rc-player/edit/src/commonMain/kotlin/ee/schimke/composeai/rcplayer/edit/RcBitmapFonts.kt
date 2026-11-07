package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontGlyph
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRunOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathAppend
import ee.schimke.composeai.rcplayer.protocol.RcPathCombine
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathExpression
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
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

/** A text [coreTextAsBitmapFonts] now draws with a bitmap font. */
public data class RcBitmapFontText(
  /** The `CoreText`'s component id, or -1 for a text on a path. */
  val componentId: Int,
  val textId: Int,
  /** The `BitmapFontData` it draws with; texts in the same face, size and colour share one. */
  val fontId: Int,
  /** Its slot's size in dp; for a text on a path, its run along the path in canvas pixels. */
  val width: Float,
  val height: Float,
  /**
   * Whether its text changes at playback, drawn from [RcBitmapFontGlyphs] rather than its value.
   */
  val dynamic: Boolean,
  /** Whether it was a `DrawTextOnPath`, now drawn along a section of its path. */
  val onPath: Boolean = false,
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
  /**
   * Text ids of `DrawTextOnPath`s in a requested family left as they were: a paint or path the
   * editor can't read statically, a changing text, or one longer than its path.
   */
  val skippedOnPath: List<Int> = emptyList(),
)

/**
 * Every `CoreText`, `TextLayout` and `DrawTextOnPath` set in one of [families] (by name, ignoring
 * case and a `google:` prefix) drawn instead with a bitmap font: each glyph it can show drawn once
 * by [rasterizer] and embedded, and the text drawn from them at playback. A face no player ships
 * then looks the same on every player and in every profile — launcher and Wear widgets included,
 * which have bitmap fonts at every API level — and, unlike [coreTextAsImages], a text the host or a
 * timer changes keeps the face, as long as [glyphs] says which characters it can show. A clock in
 * an LCD face is the case it is for.
 *
 * A `CoreText` converts when it is one line that fits its slot — the width [wrapWidth] gives for
 * its component id, or the document's — its size, weight, slant and family are constant, and it
 * asks for nothing a bitmap font can't draw: letter spacing becomes the font's glyph spacing, and
 * constant font axes and line height are drawn into the glyphs; decoration and the like leave it as
 * it was, listed in [RcBitmapFontTexts.skipped]. Its text is a static value's characters, or
 * [glyphs] for its component id when the value changes; a changing text [glyphs] gives nothing for
 * is skipped. A text in any other family is left alone, unlisted.
 *
 * A `TextLayout`, the text component below level 7 that the Android 16 launcher lays out, converts
 * as the `CoreText` with its fields would; one left as text stays a `TextLayout`, which a target
 * that reads no `CoreText` still decodes.
 *
 * The text's component becomes a box with the same id, animation id and modifiers, holding a canvas
 * sized for the text — for a changing one, [RcBitmapFontGlyphs.maxLength] of its widest glyph —
 * that draws it from its start. A changing text centred or set to its end is drawn anchored there
 * by its live width, so it stays as aligned whatever it shows; a target without the anchored draw
 * (the Android 16 launcher) keeps such a text. A colour the player sets at playback stays live: the
 * glyphs are drawn as a white mask the canvas paint tints with that colour id. A constant colour is
 * drawn into the glyphs. Texts sharing a face, size and colour share one font.
 *
 * A `DrawTextOnPath` with a static text converts where its canvas's paint is read from `PaintData`
 * alone (a filled face, size and colour, no shader or filter), its offsets are constant and its
 * path is a static single contour: it becomes a `DrawBitmapFontTextOnPath` along a section of that
 * path exactly as long as the text, starting at its offset — that op spreads its glyphs over the
 * whole path it is given, where text on a path keeps their spacing. Targets without the op (Wear
 * widgets, the Android 16 launcher) keep the text, listed in [RcBitmapFontTexts.skippedOnPath].
 *
 * With [kerning] each pair of glyphs a text sets side by side is spaced as the face spaces it —
 * every pair a static text shows, and every pair of a changing text's glyphs when it has at most
 * 24. [sequences] adds glyphs for runs drawn as one — a ligature such as `fi`, or a joined pair in
 *     a script — wherever a text shows them; the player draws the longest glyph that matches.
 *
 * A glyph whose ink stands outside the face's line (Digital-7's digits, an accent over a capital)
 * is drawn whole, taller than the line, and every glyph of the face is set on one baseline by
 * margins above and below: the text's slot is then as tall as its ink, where `CoreText` keeps its
 * line's.
 *
 * Each glyph is still placed on a whole pixel, so a face reads as itself but not pixel for pixel as
 * `CoreText` lays it out, and a joined script keeps only the joins [sequences] names, losing any
 * ink outside a glyph's line box. It suits a display face — an LCD clock, a heading, a bezel —
 * rather than running text in a script.
 *
 * The glyphs are drawn at [display]'s density; at API level 7 the canvas scales them with the
 * device's, as [coreTextAsImages] does its images. Text on a path is drawn at its paint's size in
 * the canvas's pixels. Check [sizeReport] against the target's limits.
 */
public fun RcDocument.coreTextAsBitmapFonts(
  target: RcTargetProfile,
  families: Set<String>,
  rasterizer: RcTextRasterizer,
  display: RcDisplay = RcDisplay.DEFAULT,
  kerning: Boolean = true,
  sequences: Set<String> = emptySet(),
  wrapWidth: (componentId: Int) -> Float? = { null },
  glyphs: (componentId: Int) -> RcBitmapFontGlyphs? = { null },
): RcBitmapFontTexts {
  // The Android 16 launcher lays its text out with the older `TextLayout`: each in a requested
  // family is read as the `CoreText` it says exactly, and any left as text goes back as it was.
  val layouts = textLayoutsIn(families)
  if (layouts.isEmpty()) {
    return bitmapFonts(target, families, rasterizer, display, kerning, sequences, wrapWidth, glyphs)
  }
  val lifted =
    copy(
      operations =
        operations.map { operation ->
          (operation as? RcTextLayout)?.takeIf { it.componentId in layouts }?.asCoreText()
            ?: operation
        },
    )
  val result =
    lifted.bitmapFonts(target, families, rasterizer, display, kerning, sequences, wrapWidth, glyphs)
  if (result.texts.isEmpty()) {
    return RcBitmapFontTexts(this, emptyList(), result.skipped, result.skippedOnPath)
  }
  val restored =
    result.document.operations.map { operation ->
      (operation as? RcCoreText)?.let { layouts[it.componentId] } ?: operation
    }
  return result.copy(document = result.document.copy(operations = restored))
}

/** The `TextLayout`s set in one of [families] that a `CoreText` says exactly, by component id. */
private fun RcDocument.textLayoutsIn(families: Set<String>): Map<Int, RcTextLayout> {
  val wanted = families.map { it.lowercase().removePrefix("google:") }.toSet()
  if (wanted.isEmpty()) return emptyMap()
  val strings = operations.filterIsInstance<RcTextData>().associate { it.id to it.text }
  return operations
    .filterIsInstance<RcTextLayout>()
    .filter { layout ->
      // A flag `CoreText` has no property for keeps the text as it is.
      layout.flags and RcTextLayout.FLAG_DYNAMIC_COLOR.inv() == 0 &&
        strings[layout.fontFamilyId]?.lowercase()?.removePrefix("google:") in wanted
    }
    .associateBy { it.componentId }
}

/** This `TextLayout` as the `CoreText` with the same fields. */
private fun RcTextLayout.asCoreText(): RcCoreText {
  val dynamicColor = flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0
  return RcCoreText(
    textId,
    listOfNotNull(
      RcTextStyleProperty.IntValue(P_COMPONENT_ID, componentId),
      animationId.takeIf { it != -1 }?.let { RcTextStyleProperty.IntValue(P_ANIMATION_ID, it) },
      RcTextStyleProperty.IntValue(if (dynamicColor) P_COLOR_ID else P_COLOR, color),
      RcTextStyleProperty.FloatValue(P_FONT_SIZE, fontSize),
      RcTextStyleProperty.IntValue(P_FONT_STYLE, fontStyle),
      RcTextStyleProperty.FloatValue(P_FONT_WEIGHT, fontWeight),
      RcTextStyleProperty.IntValue(P_FONT_FAMILY, fontFamilyId),
      RcTextStyleProperty.IntValue(P_TEXT_ALIGN, textAlign),
      RcTextStyleProperty.IntValue(P_OVERFLOW, overflow),
      RcTextStyleProperty.IntValue(P_MAX_LINES, maxLines),
    ),
  )
}

private fun RcDocument.bitmapFonts(
  target: RcTargetProfile,
  families: Set<String>,
  rasterizer: RcTextRasterizer,
  display: RcDisplay,
  kerning: Boolean,
  sequences: Set<String>,
  wrapWidth: (componentId: Int) -> Float?,
  glyphs: (componentId: Int) -> RcBitmapFontGlyphs?,
): RcBitmapFontTexts {
  val unchanged = RcBitmapFontTexts(this, emptyList(), emptyList())
  val wanted = families.map { it.lowercase() }.toSet()
  fun requested(family: String?): Boolean {
    val name = family?.lowercase() ?: return false
    return name in wanted || name.removePrefix("google:") in wanted
  }
  if (wanted.isEmpty()) return unchanged
  if (operations.none { it is RcCoreText || it is RcDrawTextOnPath }) return unchanged
  val scales = target.apiLevel >= 7
  val required = REQUIRED_OPCODES + if (scales) SCALE_OPCODES else emptySet()
  val boxes = required.all { it in target.supportedOpcodes }
  val paths = PATH_OPCODES.all { it in target.supportedOpcodes }
  val tintable = RcOpcodes.PAINT_VALUES in target.supportedOpcodes
  val styles = operations.textStyles()
  val values = RcValueAnalysis(hardcodeDensity(display.density, display.fontScale).operations)
  val fontData = operations.filterIsInstance<RcFontData>().associateBy { it.fontId }
  val ids =
    RcFreshIds(
      operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() },
      componentIds(operations),
    )
  val runs = sequences.filter { it.length >= 2 }.toSet()
  val documentWidth = header.width / (header.density.takeIf { it > 0f } ?: 1f)
  val skipped = mutableListOf<Int>()
  val skippedOnPath = mutableListOf<Int>()

  // First pass: what each text needs, so a face shared by several texts is drawn once with every
  // glyph any of them shows.
  val plans = mutableListOf<Plan>()
  if (boxes) {
    operationTree(operations)?.forEach { root ->
      root.walk { node ->
        val text = node.operation as? RcCoreText ?: return@walk
        val properties = resolvedProperties(text, node.start, styles) ?: return@walk
        val familyId =
          (properties[P_FONT_FAMILY] as? RcTextStyleProperty.IntValue)?.value ?: return@walk
        val family = values.text(familyId, node.start)
        if (familyId == -1 || !requested(family)) return@walk
        val plan =
          plan(
            text,
            node,
            properties,
            family!!,
            values,
            fontData,
            tintable,
            RcOpcodes.DRAW_BITMAP_TEXT_ANCHORED in target.supportedOpcodes,
            display,
            glyphs,
          )
        if (plan == null) skipped += text.componentId else plans += plan
      }
    }
  }
  // A path playback rewrites — tweened, created, appended to, combined, an expression — is drawn
  // as it then is, not as its data first says: only a path nothing writes after is static.
  val rewrittenPaths =
    operations
      .mapNotNull {
        when (it) {
          is RcPathTween -> it.outId
          is RcPathCreate -> it.id
          is RcPathAppend -> it.id
          is RcPathCombine -> it.outId
          is RcPathExpression -> it.id
          else -> null
        }
      }
      .toSet()
  val pathData =
    operations.filterIsInstance<RcPathData>().filter { it.id !in rewrittenPaths }.groupBy { it.id }
  var paint = RcPaintTrack()
  operations.forEachIndexed { index, operation ->
    when {
      operation is RcCanvasLayout ||
        operation is RcCanvasContent ||
        operation is RcRootLayout ||
        (operation is RcNoArg && operation.opcode == RcOpcodes.CANVAS_OPERATIONS) ->
        paint = RcPaintTrack()
      operation.opcode in UNREADABLE_PAINT_OPCODES -> paint.known = false
      operation is RcPaintData -> paint.apply(operation.words)
      operation is RcDrawTextOnPath -> {
        val family =
          paint.fontType
            .takeIf { it > MAX_GENERIC_FONT_TYPE && paint.fontStyle and FONT_DATA_STYLE == 0 }
            ?.let { values.text(it, index) }
        if (!requested(family)) return@forEachIndexed
        val plan =
          pathPlan(operation, index, family!!, paint, values, pathData).takeIf { paths && tintable }
        if (plan == null) skippedOnPath += operation.textId else plans += plan
      }
    }
  }
  if (plans.isEmpty()) return RcBitmapFontTexts(this, emptyList(), skipped, skippedOnPath)

  // Second pass: draw each face's glyphs; a face missing one drops every text set in it.
  val definitions = mutableListOf<RcOperation>()
  val fonts = mutableMapOf<Face, DrawnFont>()
  plans
    .groupBy { it.face }
    .forEach { (face, texts) ->
      fun drop() = texts.forEach {
        when (it) {
          is CorePlan -> skipped += it.text.componentId
          is PathPlan -> skippedOnPath += it.operation.textId
        }
      }
      val shown = texts.mapNotNull { it.value }
      val dynamic = texts.mapNotNull { (it as? CorePlan)?.dynamic?.chars }.joinToString("")
      // A run gets a glyph where a static text shows it, or a changing one could.
      val faceRuns =
        runs.filter { run -> shown.any { run in it } || run.all { it in dynamic } }.toSet()
      val tokens = buildSet {
        shown.forEach { addAll(tokenize(it, faceRuns)) }
        dynamic.forEach { add(it.toString()) }
        addAll(faceRuns)
      }
      val drawn = mutableListOf<Pair<String, RcBitmapData>>()
      val advances = mutableMapOf<String, Int>()
      val exact = mutableMapOf<String, Float>()
      val baselines = mutableMapOf<String, Float>()
      for (token in tokens.sorted()) {
        val glyph =
          rasterizer.rasterize(face.styled(token))?.takeIf {
            it.width in 1..Short.MAX_VALUE && it.height in 1..Short.MAX_VALUE
          }
        // A glyph's image is its advance rounded up to a pixel; the advance itself, to a pixel,
        // so a line keeps its length rather than gaining up to a pixel a glyph.
        val advance = glyph?.let { measure(rasterizer, face, token) }
        if (glyph == null || advance == null) return@forEach drop()
        baselines[token] = glyph.baseline
        exact[token] = advance
        advances[token] = round(advance).toInt().coerceAtMost(glyph.width)
        drawn +=
          token to
            RcBitmapData(
              imageId = ids.next(),
              width = glyph.width,
              height = glyph.height,
              type = RcBitmapData.TYPE_PNG_8888,
              encoding = RcBitmapData.ENCODING_INLINE,
              data = glyph.png,
            )
      }
      val kerns = mutableMapOf<String, Short>()
      // Text on a path is set a glyph at a time at each one's own advance, without kerning.
      if (kerning && texts.none { it is PathPlan }) {
        val pairs = buildSet {
          shown.forEach { text -> tokenize(text, faceRuns).zipWithNext().forEach { add(it) } }
          val singles = dynamic.toSet().map { it.toString() }
          if (singles.size <= MAX_KERNED_GLYPHS) {
            singles.forEach { a -> singles.forEach { b -> add(a to b) } }
          }
        }
        val claimed = mutableMapOf<String, Pair<String, String>>()
        for ((a, b) in pairs) {
          // The player looks a kerning up by the two glyphs' text run together, so two splits of
          // one string ("f"+"fi", "ff"+"i") can't both have one: neither is kept.
          val key = a + b
          val other = claimed.put(key, a to b)
          if (other != null && other != (a to b)) {
            kerns.remove(key)
            continue
          }
          // The pair's advance less the two glyphs' own: where the face sets them closer or further
          // apart. Not their advances' rounding, which every pair a glyph is in would count again.
          val pair = measure(rasterizer, face, key) ?: continue
          val kern = round(pair - exact.getValue(a) - exact.getValue(b)).toInt()
          if (kern != 0) kerns[key] = kern.toShort()
        }
      }
      // A glyph whose ink stands outside its line is drawn taller, its baseline as much lower:
      // every glyph shares one line, so a margin above each sets them all on the lowest baseline,
      // and one below makes them one height. A face with no such glyph keeps margins of zero.
      val baseline = baselines.values.maxOrNull()?.takeUnless { it.isNaN() }
      fun above(token: String) =
        baseline?.let { round(it - baselines.getValue(token)).toInt() } ?: 0
      val lineHeight = drawn.maxOf { (token, bitmap) -> above(token) + bitmap.height }
      val font =
        RcBitmapFontData(
          ids.next(),
          drawn.map { (token, bitmap) ->
            RcBitmapFontGlyph(
              chars = token,
              bitmapId = bitmap.imageId,
              marginLeft = 0,
              marginTop = above(token).toShort(),
              marginRight = (advances.getValue(token) - bitmap.width).toShort(),
              marginBottom = (lineHeight - above(token) - bitmap.height).toShort(),
              bitmapWidth = bitmap.width.toShort(),
              bitmapHeight = bitmap.height.toShort(),
            )
          },
          kerns,
        )
      definitions += drawn.map { it.second }
      definitions += font
      fonts[face] =
        DrawnFont(
          font.fontId,
          advances,
          kerns,
          faceRuns,
          lineHeight,
          baseline ?: Float.NaN,
          exact,
        )
    }

  val converted = mutableListOf<RcBitmapFontText>()
  val replacements = mutableMapOf<Int, Pair<Int, List<RcOperation>>>()
  var scaleId: Int? = null
  for (plan in plans) {
    val font = fonts[plan.face] ?: continue
    when (plan) {
      is CorePlan -> {
        val spacing =
          plan.letterSpacing * plan.face.fontSize +
            font.rounding(
              plan.value?.let(font::tokens) ?: plan.dynamic!!.chars.map { it.toString() },
            )
        val pixelWidth =
          if (plan.dynamic != null) {
            val widest = plan.dynamic.chars.maxOf { font.advances.getValue(it.toString()) }
            val loosest = font.kerning.values.maxOfOrNull { it.toInt() }?.coerceAtLeast(0) ?: 0
            (widest + spacing) * plan.dynamic.maxLength + loosest * (plan.dynamic.maxLength - 1)
          } else {
            font.width(plan.value!!) + spacing * font.tokens(plan.value).size
          }
        val width = ceil(pixelWidth) / display.density
        val height = font.height / display.density
        // One unwrapped line: a text its slot would wrap or cut keeps its own layout.
        if (width > (wrapWidth(plan.text.componentId) ?: documentWidth) + 0.5f) {
          skipped += plan.text.componentId
          continue
        }
        val box = buildList {
          fun size(dp: Float, pixels: Float): RcFloatWord {
            // Below level 7 the document is per display already; above, it scales with the
            // device.
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
            RcBoxLayout(
              plan.text.componentId,
              plan.text.animationId,
              boxAlignment(plan.align),
              CENTER,
            ),
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
          plan.face.tintId?.let { add(tint(it)) }
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
          val pan = panX(plan.align)
          add(
            if (plan.dynamic != null && pan != -1f) {
              // Its slot is its widest value: the live one sits within it as the text was aligned.
              RcDrawBitmapTextAnchored(
                plan.text.textId,
                font.fontId,
                RcFloatWord.literal(0f),
                RcFloatWord.literal(-1f),
                RcFloatWord.literal(pixelWidth * (1f + pan) / 2f),
                RcFloatWord.literal(0f),
                RcFloatWord.literal(pan),
                RcFloatWord.literal(1f),
                RcFloatWord.literal(spacing),
              )
            } else {
              RcDrawBitmapFontTextRun(
                plan.text.textId,
                font.fontId,
                0,
                -1,
                RcFloatWord.literal(0f),
                RcFloatWord.literal(0f),
                RcFloatWord.literal(spacing),
              )
            },
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
      is PathPlan -> {
        // The op spreads the font's run, whole pixels a glyph, over the section it is given: a
        // section the text's exact length puts each glyph where text on a path would.
        val run = font.tokens(plan.text).sumOf { font.exact.getValue(it).toDouble() }.toFloat()
        val start = plan.horizontalOffset
        if (
          font.baseline.isNaN() || run <= 0f || start < 0f || start + run > plan.line.length + 0.5f
        ) {
          skippedOnPath += plan.operation.textId
          continue
        }
        val section = plan.line.section(ids.next(), start, start + run)
        definitions += section
        val draw = buildList {
          // The paint's colour already carries the live colour's alpha, which the player applies to
          // the glyphs as well as the tint: opaque white for the draw, then the colour id again.
          plan.face.tintId?.let { add(RcPaintData(listOf(PAINT_COLOR, WHITE) + tint(it).words)) }
          add(
            RcDrawBitmapFontTextRunOnPath(
              plan.operation.textId,
              font.fontId,
              section.id,
              0,
              -1,
              // The op hangs a glyph's top from the path; text on a path sets its baseline there.
              RcFloatWord.literal(plan.verticalOffset - font.baseline),
            ),
          )
          plan.face.tintId?.let { add(RcPaintData(listOf(CLEAR_COLOR_FILTER, PAINT_COLOR_ID, it))) }
        }
        replacements[plan.index] = plan.index to draw
        converted +=
          RcBitmapFontText(
            -1,
            plan.operation.textId,
            font.fontId,
            run,
            font.height.toFloat(),
            dynamic = false,
            onPath = true,
          )
      }
    }
  }
  if (replacements.isEmpty()) {
    return RcBitmapFontTexts(this, emptyList(), skipped.distinct(), skippedOnPath.distinct())
  }

  // The fonts and path sections go with the document's data, ahead of its layout; a document
  // without one has them ahead of the first text drawn with them.
  val first =
    operations.indexOfFirst { it is RcRootLayout }.takeIf { it >= 0 } ?: replacements.keys.min()
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
  return RcBitmapFontTexts(
    copy(operations = rewritten),
    converted,
    skipped.distinct(),
    skippedOnPath.distinct(),
  )
}

/** A text on a path's conversion, or null if its paint, offsets or path aren't static. */
private fun pathPlan(
  operation: RcDrawTextOnPath,
  index: Int,
  family: String,
  paint: RcPaintTrack,
  values: RcValueAnalysis,
  paths: Map<Int, List<RcPathData>>,
): PathPlan? {
  if (!paint.known || paint.unsupported) return null
  val size = paint.textSize ?: return null
  val text =
    values.text(operation.textId, index)?.takeIf {
      it.isNotEmpty() && '\n' !in it && it.none(::unplaceable)
    } ?: return null
  val horizontal =
    operation.horizontalOffset.takeIf { it.referencedId == null }?.value ?: return null
  val vertical = operation.verticalOffset.takeIf { it.referencedId == null }?.value ?: return null
  val line = paths[operation.pathId]?.singleOrNull()?.let(RcPolyline::of) ?: return null
  val axes = paint.axes.map { (tag, value) -> (values.text(tag, index) ?: return null) to value }
  val weight = paint.fontStyle and 0x3ff
  val face =
    Face(
      family = family,
      fontData = null,
      fontSize = size,
      weight = if (weight > 0) weight else 400,
      italic = paint.fontStyle and 0x800 != 0,
      // A paint's colour carries its alpha, which the player applies to the glyphs as it draws.
      color = paint.color?.let { it or 0xff000000.toInt() } ?: WHITE,
      tintId = if (paint.color == null) paint.colorId else null,
      fontAxes = axes,
      lineHeightAdd = 0f,
      lineHeightMultiplier = 1f,
      density = 1f,
    )
  if (paint.color == null && paint.colorId == null) return null
  return PathPlan(operation, index, face, text, line, horizontal, vertical)
}

/** A [RcPaintData] tinting what follows with colour id [colorId]: a white mask in its colour. */
private fun tint(colorId: Int) =
  RcPaintData(listOf(COLOR_FILTER_ID or (BLEND_SRC_IN shl 16), colorId))

/**
 * [text]'s advance in [face] to a fraction of a pixel: laid out at [MEASURE_SCALE] times the size,
 * so the whole pixel a layout rounds up to is a fraction of one at the face's own size.
 */
private fun measure(rasterizer: RcTextRasterizer, face: Face, text: String): Float? =
  rasterizer.rasterize(face.styled(text, MEASURE_SCALE))?.width?.let {
    it.toFloat() / MEASURE_SCALE
  }

/**
 * [text] split as the player looks its glyphs up: the longest of [runs] at each place, or one
 * character.
 */
private fun tokenize(text: String, runs: Set<String>): List<String> = buildList {
  var index = 0
  while (index < text.length) {
    val token =
      runs.filter { text.startsWith(it, index) }.maxByOrNull { it.length } ?: text[index].toString()
    add(token)
    index += token.length
  }
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
  anchorable: Boolean,
  display: RcDisplay,
  glyphs: (Int) -> RcBitmapFontGlyphs?,
): CorePlan? {
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
  if ('\n' in chars || chars.isEmpty() || chars.any(::unplaceable)) return null
  // A changing text centred or set to its end is placed by its live width, which only the
  // anchored draw measures.
  if (dynamic != null && panX(align) != -1f && !anchorable) return null
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
      // The wire's style is an enum, not a typeface bitmask: italic is 1, and weight is its own.
      weight = round(weight).toInt().coerceIn(1, 1000),
      italic = style == FONT_STYLE_ITALIC,
      color = constantColor ?: WHITE,
      tintId = if (constantColor == null) colorId else null,
      fontAxes = tags.zip(axisValues),
      lineHeightAdd = lineHeightAdd,
      lineHeightMultiplier = lineHeightMultiplier,
      density = display.density,
    )
  return CorePlan(text, node, face, value, dynamic, letterSpacing, align)
}

/** A text to draw with a bitmap font: [value] when it is static. */
private sealed class Plan(val face: Face, val value: String?)

private class CorePlan(
  val text: RcCoreText,
  val node: RcOperationNode,
  face: Face,
  value: String?,
  /** The glyphs a changing text can show, when [value] is null. */
  val dynamic: RcBitmapFontGlyphs?,
  /** In ems. */
  val letterSpacing: Float,
  val align: Int,
) : Plan(face, value)

private class PathPlan(
  val operation: RcDrawTextOnPath,
  val index: Int,
  face: Face,
  val text: String,
  val line: RcPolyline,
  val horizontalOffset: Float,
  val verticalOffset: Float,
) : Plan(face, text)

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
  /** Device pixels per dp the glyphs are drawn for; 1 for text on a path, sized in pixels. */
  val density: Float,
) {
  fun styled(text: String, scale: Float = 1f) =
    RcStyledText(
      text = text,
      density = density,
      fontSize = fontSize * scale,
      color = color,
      fontWeight = weight,
      italic = italic,
      fontFamily = family,
      fontData = fontData,
      fontAxes = fontAxes,
      lineHeightAdd = lineHeightAdd * scale,
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
        density,
      )

  override fun equals(other: Any?): Boolean = other is Face && key == other.key

  override fun hashCode(): Int = key.hashCode()
}

private class DrawnFont(
  val fontId: Int,
  /** Each glyph's advance in pixels, by its text. */
  val advances: Map<String, Int>,
  val kerning: Map<String, Short>,
  val runs: Set<String>,
  val height: Int,
  /** The glyphs' baseline from their top, or NaN where the rasterizer didn't say. */
  val baseline: Float,
  /** Each glyph's advance to a fraction of a pixel, which [advances] rounds. */
  val exact: Map<String, Float>,
) {
  /**
   * What [advances] lose on average over [tokens] to their rounding: glyph spacing, which the
   * player adds as a fraction, gives it back, so a line keeps its length.
   */
  fun rounding(tokens: Collection<String>): Float =
    if (tokens.isEmpty()) 0f
    else
      tokens.sumOf { (exact.getValue(it) - advances.getValue(it)).toDouble() }.toFloat() /
        tokens.size

  fun tokens(text: String): List<String> = tokenize(text, runs)

  /** [text]'s run in pixels as the player lays it out: advances and kerning, no glyph spacing. */
  fun width(text: String): Int {
    val tokens = tokens(text)
    return tokens.sumOf { advances.getValue(it) } +
      tokens.zipWithNext().sumOf { (a, b) -> kerning[a + b]?.toInt() ?: 0 }
  }
}

/**
 * Whether [char] can't be drawn a glyph at a time from left to right: half of a character outside
 * the basic plane, which would be split in two, or a right-to-left letter (Hebrew, Arabic and the
 * scripts beside them, and their presentation forms), whose text a bitmap font would set backwards.
 */
private fun unplaceable(char: Char): Boolean =
  char.isSurrogate() ||
    char in '\u0590'..'\u08FF' ||
    char in '\uFB1D'..'\uFDFF' ||
    char in '\uFE70'..'\uFEFF'

/** `CoreText` alignment as an anchored draw's horizontal pan: -1 start, 0 centre, 1 end. */
private fun panX(align: Int): Float =
  when (align) {
    3 -> 0f
    2,
    6 -> 1f
    else -> -1f
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

private val DIVIDE = RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 4)

private val END = RcNoArg(RcOpcodes.CONTAINER_END)

private const val CENTER = 5

/** How much larger [coreTextAsBitmapFonts] lays a glyph or pair out to measure its advance. */
private const val MEASURE_SCALE = 8f

/** The most glyphs a changing text can show for every pair of them to be kerned. */
private const val MAX_KERNED_GLYPHS = 24

/** AndroidX `PaintBundle.COLOR` and `COLOR_ID`. */
private const val PAINT_COLOR = 4
private const val PAINT_COLOR_ID = 19

/** AndroidX `PaintBundle.CLEAR_COLOR_FILTER`. */
private const val CLEAR_COLOR_FILTER = 21

/** A paint `TYPEFACE` font type above this names a family by text id. */
private const val MAX_GENERIC_FONT_TYPE = 10

/** The `TYPEFACE` style bit marking a `FontData` id rather than a family name. */
private const val FONT_DATA_STYLE = 1024

/** Operations around a draw that leave its paint unknowable without playing the document. */
private val UNREADABLE_PAINT_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.CONDITIONAL_OPERATIONS,
    RcOpcodes.LOOP_START,
    RcOpcodes.PARTICLE_LOOP,
    RcOpcodes.CONTAINER_END,
  )

/** What drawing text on a path from a bitmap font needs. */
private val PATH_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.DATA_BITMAP,
    RcOpcodes.DATA_BITMAP_FONT,
    RcOpcodes.DATA_PATH,
    RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH,
  )

private const val WHITE = 0xffffffff.toInt()

private const val BLACK = 0xff000000.toInt()

/** AndroidX `PaintBundle.COLOR_FILTER_ID`, with the blend mode in its high half. */
private const val COLOR_FILTER_ID = 20

/** AndroidX `PaintBundle.BLEND_MODE_SRC_IN`: the mask's coverage in the filter's colour. */
private const val BLEND_SRC_IN = 5

/** `CoreText` and `TextLayout`'s italic font style, as players read it. */
private const val FONT_STYLE_ITALIC = 1

private const val P_COMPONENT_ID = 1
private const val P_ANIMATION_ID = 2
private const val P_COLOR = 3
private const val P_COLOR_ID = 4
private const val P_FONT_SIZE = 5
private const val P_FONT_STYLE = 6
private const val P_FONT_WEIGHT = 7
private const val P_FONT_FAMILY = 8
private const val P_TEXT_ALIGN = 9
private const val P_OVERFLOW = 10
private const val P_MAX_LINES = 11
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

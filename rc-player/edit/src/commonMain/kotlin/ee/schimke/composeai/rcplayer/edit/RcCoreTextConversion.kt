package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty

/**
 * Every `CoreText` in this document that the older `TextLayout` can say exactly, rewritten as one.
 *
 * `CoreText` (level 7) is `TextLayout` (level 6) plus a property vocabulary and reusable styles.
 * The two share the component, text, colour, size, style, weight, family, alignment, overflow and
 * max-lines fields; everything else — letter spacing, line height, autosize, underline, font axes,
 * break, hyphenation and justification strategies, flags — `TextLayout` has no field for. A
 * `CoreText` converts only when, after its `TextStyle` chain is applied, every one of those is at
 * its default, so the rewrite never drops something the text asked for — unless [approximate] is
 * set, when the typographic refinements in `APPROXIMABLE` may go too, each recorded in
 * [approximations]. The container keeps its children, so modifiers and the content wrapper are
 * untouched.
 *
 * Styles resolve as AndroidX's `CoreText.applyStyle` resolves them: a property the text leaves at
 * its default takes the style's value. Where the CMP player would resolve it differently — a text
 * that sets a property *to* its default under a style that sets something else — the text is left
 * as it is. `TextStyle` operations are dropped once no `CoreText` is left to read them and the
 * target does not decode them.
 */
internal fun RcDocument.coreTextAsTextLayout(
  target: RcTargetProfile,
  approximate: Boolean,
  approximations: MutableList<RcTextApproximation>,
): RcDocument {
  if (RcOpcodes.CORE_TEXT in target.supportedOpcodes) return this
  if (RcOpcodes.LAYOUT_TEXT !in target.supportedOpcodes) return this
  if (operations.none { it is RcCoreText }) return this
  val styles = operations.textStyles()
  val converted = operations.mapIndexed { index, operation ->
    val text = operation as? RcCoreText ?: return@mapIndexed operation
    val (layout, dropped) =
      textLayout(text, index, styles, approximate) ?: return@mapIndexed operation
    if (dropped.isNotEmpty()) {
      approximations += RcTextApproximation(text.componentId, text.textId, dropped)
    }
    layout
  }
  val stylesUnread =
    converted.none { it is RcCoreText } && RcOpcodes.TEXT_STYLE !in target.supportedOpcodes
  val rewritten = if (stylesUnread) converted.filterNot { it is RcTextStyle } else converted
  return if (rewritten == operations) this else copy(operations = rewritten)
}

private fun textLayout(
  text: RcCoreText,
  index: Int,
  styles: Map<Int?, List<IndexedValue<RcTextStyle>>>,
  approximate: Boolean,
): Pair<RcTextLayout, Set<String>>? {
  val resolved = resolvedProperties(text, index, styles) ?: return null
  val unsaid = resolved.filter { (id, value) -> id !in TEXT_LAYOUT_FIELDS && !value.isDefault() }
  if (unsaid.keys.any { !approximate || it !in APPROXIMABLE.keys }) return null
  val dropped = unsaid.keys.map { APPROXIMABLE.getValue(it) }.sorted().toSet()

  fun int(id: Int): Int? = (resolved[id] as? RcTextStyleProperty.IntValue)?.value

  fun word(id: Int): RcFloatWord? = (resolved[id] as? RcTextStyleProperty.FloatValue)?.value

  val align = int(P_TEXT_ALIGN) ?: 1
  if (align ushr 16 != 0) return null
  val colorId = int(P_COLOR_ID) ?: -1
  val layout =
    RcTextLayout(
      componentId = text.componentId,
      animationId = text.animationId,
      textId = text.textId,
      color = if (colorId != -1) colorId else int(P_COLOR) ?: DEFAULT_COLOR,
      fontSize = word(P_FONT_SIZE) ?: RcFloatWord.literal(DEFAULT_FONT_SIZE),
      fontStyle = int(P_FONT_STYLE) ?: 0,
      fontWeight = word(P_FONT_WEIGHT) ?: RcFloatWord.literal(DEFAULT_FONT_WEIGHT),
      fontFamilyId = int(P_FONT_FAMILY) ?: -1,
      textAlignAndFlags =
        if (colorId != -1) align or (RcTextLayout.FLAG_DYNAMIC_COLOR shl 16) else align,
      overflow = int(P_OVERFLOW) ?: 1,
      maxLines = int(P_MAX_LINES) ?: Int.MAX_VALUE,
    )
  return layout to dropped
}

/**
 * [text]'s properties with its `TextStyle` chain applied, without the id, animation id and parent
 * entries, or null when the chain is only known at playback or the two players would resolve it
 * differently.
 */
internal fun resolvedProperties(
  text: RcCoreText,
  index: Int,
  styles: Map<Int?, List<IndexedValue<RcTextStyle>>>,
): Map<Int, RcTextStyleProperty>? {
  val own = text.properties.associateBy { it.id }
  val inherited =
    text.textStyleId?.takeUnless { it == -1 }?.let { inherited(it, index, styles) ?: return null }
  val resolved = mutableMapOf<Int, RcTextStyleProperty>()
  for (id in (own.keys + inherited.orEmpty().keys)) {
    if (id == P_ID || id == P_ANIMATION_ID || id == P_PARENT_ID) continue
    val mine = own[id]
    val theirs = inherited?.get(id)
    resolved[id] =
      when {
        mine != null && !mine.isDefault() -> mine
        theirs != null -> {
          // AndroidX lets the style win here; the CMP player lets an explicit value win. Only
          // convert where both players agree.
          if (mine != null && theirs != mine) return null
          theirs
        }
        else -> mine ?: continue
      }
  }
  return resolved
}

/** The document's `TextStyle` operations by style id, each with its position. */
internal fun List<RcOperation>.textStyles(): Map<Int?, List<IndexedValue<RcTextStyle>>> =
  withIndex()
    .mapNotNull { (index, operation) ->
      (operation as? RcTextStyle)?.let { IndexedValue(index, it) }
    }
    .groupBy { it.value.styleId }

/** A style chain's properties, parent first, or null when it cannot be resolved statically. */
private fun inherited(
  styleId: Int,
  before: Int,
  styles: Map<Int?, List<IndexedValue<RcTextStyle>>>,
): Map<Int, RcTextStyleProperty>? {
  // A style redefined, missing, or declared after what reads it — which the player has not loaded
  // when it applies it — is only known at playback.
  val (index, style) = styles[styleId]?.singleOrNull()?.takeIf { it.index < before } ?: return null
  val merged = mutableMapOf<Int, RcTextStyleProperty>()
  style.parentStyleId
    ?.takeUnless { it == -1 }
    ?.let { merged.putAll(inherited(it, index, styles) ?: return null) }
  style.properties
    .filterNot { it.id == P_ID || it.id == P_ANIMATION_ID || it.id == P_PARENT_ID }
    .forEach { merged[it.id] = it }
  return merged
}

/** Whether this property holds AndroidX's `TextStyle.PARAMETERS` default for its id. */
internal fun RcTextStyleProperty.isDefault(): Boolean =
  when (this) {
    is RcTextStyleProperty.IntValue -> INT_DEFAULTS[id] == value
    is RcTextStyleProperty.FloatValue ->
      FLOAT_DEFAULTS[id]?.let { RcFloatWord.literal(it).bits == value.bits } == true
    is RcTextStyleProperty.BooleanValue -> !value
    is RcTextStyleProperty.IntArrayValue -> values.isEmpty()
    is RcTextStyleProperty.FloatArrayValue -> values.isEmpty()
  }

// AndroidX `TextStyle` property ids.
private const val P_ID = 1
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
private const val P_PARENT_ID = 24

private const val DEFAULT_COLOR = 0xFF000000.toInt()
private const val DEFAULT_FONT_SIZE = 36f
private const val DEFAULT_FONT_WEIGHT = 400f

/**
 * Typographic refinements an approximating conversion may drop, by the name it reports them under.
 * They change spacing and shape, not what the text says or how big it is; decoration, autosize and
 * flags are left out on purpose and always block a conversion.
 */
private val APPROXIMABLE: Map<Int, String> =
  mapOf(
    12 to "letterSpacing",
    13 to "lineHeightAdd",
    14 to "lineHeightMultiplier",
    15 to "lineBreakStrategy",
    16 to "hyphenationFrequency",
    17 to "justificationMode",
    20 to "fontAxis",
    21 to "fontAxisValues",
  )

/** The properties `TextLayout` has a field for. */
private val TEXT_LAYOUT_FIELDS: Set<Int> =
  setOf(
    P_COLOR,
    P_COLOR_ID,
    P_FONT_SIZE,
    P_FONT_STYLE,
    P_FONT_WEIGHT,
    P_FONT_FAMILY,
    P_TEXT_ALIGN,
    P_OVERFLOW,
    P_MAX_LINES,
  )

/** `TextStyle.PARAMETERS` defaults for the integer-valued ids. */
private val INT_DEFAULTS: Map<Int, Int> =
  mapOf(
    P_ID to -1,
    P_ANIMATION_ID to -1,
    P_COLOR to DEFAULT_COLOR,
    P_COLOR_ID to -1,
    P_FONT_STYLE to 0,
    P_FONT_FAMILY to -1,
    P_TEXT_ALIGN to 1,
    P_OVERFLOW to 1,
    P_MAX_LINES to Int.MAX_VALUE,
    15 to 0, // break strategy
    16 to 0, // hyphenation frequency
    17 to 0, // justification mode
    23 to 0, // flags
    P_PARENT_ID to -1,
  )

/** `TextStyle.PARAMETERS` defaults for the float-valued ids. */
private val FLOAT_DEFAULTS: Map<Int, Float> =
  mapOf(
    P_FONT_SIZE to DEFAULT_FONT_SIZE,
    P_FONT_WEIGHT to DEFAULT_FONT_WEIGHT,
    12 to 0f, // letter spacing
    13 to 0f, // line height add
    14 to 1f, // line height multiplier
    25 to -1f, // min font size
    26 to -1f, // max font size
  )

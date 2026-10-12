package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode

/**
 * A `CoreText` an approximating [adaptTo] converted to `TextLayout`, and the typographic properties
 * it had to drop to do so (`letterSpacing`, `lineHeightMultiplier`, `fontAxis`, …).
 */
public data class RcTextApproximation(
  val componentId: Int,
  val textId: Int,
  val dropped: Set<String>,
)

/**
 * A rewrite [adaptTo] makes only when asked, because it changes what the document draws on the
 * target rather than only how it says it.
 */
public enum class RcApproximation {
  /**
   * `CoreText` becomes `TextLayout` even where that drops letter spacing, line height or font axes;
   * each drop is listed in [RcProfileAdaptation.approximations].
   */
  TEXT,

  /** `ColorTheme` becomes its recorded fallbacks, losing the device's system palette. */
  THEME_COLORS,
}

/** A document rewritten for a [RcTargetProfile], and what still keeps it from playing there. */
public data class RcProfileAdaptation(
  val document: RcDocument,
  /** [document]'s remaining violations — [validateFor] on the result. */
  val violations: List<RcProfileViolation>,
  /** What an approximating text conversion dropped, one entry per converted text. */
  val approximations: List<RcTextApproximation> = emptyList(),
) {
  /** Whether the adapted document plays on the target. */
  public val isPlayable: Boolean
    get() = violations.isEmpty()
}

/**
 * This document rewritten, where it can be without changing what it shows, to play on [target].
 *
 * Each rewrite is applied only when the target needs it:
 * - **Macros and referenced operations are inlined** for a target that decodes neither, by the
 *   player's own linker, so every call site becomes the operations it expands to. Not done for a
 *   document that also uses the legacy `ComponentStart`, which the linker unwraps.
 * - **`CoreText` becomes `TextLayout`** for a target without it, where the older component can say
 *   everything the text asks for: same text, colour, size, style, weight, family, alignment,
 *   overflow and line limit, with any `TextStyle` chain applied first. Text using letter spacing,
 *   line height, autosize, decoration, font axes or a line-breaking strategy stays `CoreText` and
 *   is reported. The two components still lay text out through different player code, so check a
 *   converted document's rendering on the target.
 * - **`ColorTheme` becomes its fallbacks** for a target without it, with
 *   [RcApproximation.THEME_COLORS], in theme sections where the target has them and as the dark
 *   fallback where it does not (Wear OS) — see [colorThemesAsFallbacks]. The system palette is
 *   lost; a `ColorTheme` that cannot be converted stays among the violations.
 * - **Remarks are dropped** where the target does not decode them; they draw nothing.
 * - **A variable image alpha, text size, padding, or background or border colour or size is
 *   replaced by its value** below API level 7, where those fields must be literals, when the
 *   variable is a constant nothing can change. A constant colour reference becomes the literal RGBA
 *   Android 16 reads in its place.
 * - **The header is rewritten** to declare what the target reads: the fixed width/height form for
 *   level 6 (unless the document sizes itself in dp, which that form cannot say); for level 7 and
 *   above, the target's level and profile bits, but only when the header as written would select an
 *   operation set missing something the document uses.
 *
 * Anything left — an operation the target does not have, an operator it cannot evaluate — is
 * returned in [RcProfileAdaptation.violations] rather than guessed at. Bake host state and constant
 * state layouts first ([hardcodeNamedValues], [collapseConstantStateLayouts]); both can turn a
 * variable this pass must leave alone into a constant it can fold.
 */
public fun RcDocument.adaptTo(
  target: RcTargetProfile,
  approximateText: Boolean = false,
): RcProfileAdaptation =
  adaptTo(target, if (approximateText) setOf(RcApproximation.TEXT) else emptySet())

/** [adaptTo], also making the [approximations] asked for, each of which changes what is drawn. */
public fun RcDocument.adaptTo(
  target: RcTargetProfile,
  approximations: Set<RcApproximation>,
): RcProfileAdaptation = adaptTo(target, approximations, RcDisplay.DEFAULT)

/**
 * [adaptTo] for a document generated for one [display] — the density and font scale the client
 * reported when it asked for the document. The overloads without one assume [RcDisplay.DEFAULT];
 * [needsDisplay] says whether the result depends on it, and so whether to ask the client.
 *
 * Below API level 7, where the player reads a text size or padding only as a literal, the
 * document's density and font-size expressions are baked for [display] first ([hardcodeDensity]),
 * so a `RemoteDensity.Host` document's sizes fold into the literals that player needs. [display]
 * also lets a level 7 header with legacy density behaviour be rewritten to the level 6 form: that
 * form makes Android 16 multiply border widths and offsets by the density, which the level 7 form
 * reads as pixels, so each is divided by [display]'s density. A variable one cannot be, and leaves
 * the header as it is, reported.
 *
 * The result is right only on [display]: serve it to that client, not to others.
 */
public fun RcDocument.adaptTo(
  target: RcTargetProfile,
  approximations: Set<RcApproximation>,
  display: RcDisplay,
): RcProfileAdaptation {
  val dropped = mutableListOf<RcTextApproximation>()
  val densityViolations = mutableListOf<RcProfileViolation>()
  val adapted =
    inlineStructuralOperations(target)
      .coreTextAsTextLayout(target, RcApproximation.TEXT in approximations, dropped)
      .colorThemes(target, RcApproximation.THEME_COLORS in approximations)
      .dropRemarks(target)
      .bakeDensity(target, display)
      .foldLiteralFields(target)
      .headerFor(target, display, densityViolations)
  return RcProfileAdaptation(adapted, adapted.validateFor(target) + densityViolations, dropped)
}

/**
 * The display a document is generated for, as the client reports it — the hint to request when
 * [needsDisplay] says a document's adaptation depends on it.
 */
public data class RcDisplay(
  /** Device pixels per dp. */
  val density: Float,
  /** The user's text scale, by which the host's default text size grows. */
  val fontScale: Float = 1f,
) {
  init {
    require(density.isFinite() && density > 0f) { "density must be positive: $density" }
    require(fontScale.isFinite() && fontScale > 0f) { "fontScale must be positive: $fontScale" }
  }

  public companion object {
    /**
     * What [adaptTo] assumes when the client did not say: a 420dpi phone (density 2.625) at the
     * default text size. Close for most phones; ask the client ([needsDisplay]) where it matters.
     */
    public val DEFAULT: RcDisplay = RcDisplay(density = 2.625f)
  }
}

/**
 * Whether adapting this document to [target] depends on the display it is served to: whether
 * [adaptTo] would bake the display's density into it, or convert sizes by it. When true, generate
 * it per client from the density and font scale the client reports; when false, one adaptation
 * serves every display.
 */
public fun RcDocument.needsDisplay(target: RcTargetProfile): Boolean {
  if (target.apiLevel >= 7) return false
  val readsDensity = operations.any { operation ->
    operation is RcFloatExpression &&
      operation.expression.any { it.referencedId in DISPLAY_VARIABLES }
  }
  if (readsDensity) return true
  if (legacyHeader(header) == header) return false
  return operations.any { operation ->
    when (operation) {
      is RcBorderModifier -> operation.borderWidth.bits != 0
      is RcOffsetModifier -> operation.x.bits != 0 || operation.y.bits != 0
      else -> false
    }
  }
}

private val DISPLAY_VARIABLES = setOf(RcSystemVariables.DENSITY, RcSystemVariables.FONT_SIZE)

private fun RcDocument.bakeDensity(target: RcTargetProfile, display: RcDisplay): RcDocument =
  if (target.apiLevel >= 7) this else hardcodeDensity(display.density, display.fontScale)

private fun RcDocument.colorThemes(target: RcTargetProfile, approximate: Boolean): RcDocument {
  if (!approximate || RcOpcodes.COLOR_THEME in target.supportedOpcodes) return this
  return colorThemesAsFallbacks(
    fixedTheme = if (RcOpcodes.THEME in target.supportedOpcodes) null else RcTheme.DARK
  )
}

private val STRUCTURAL_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.MACRO_DEFINE,
    RcOpcodes.MACRO_CALL,
    RcOpcodes.MACRO_FOR_EACH,
    RcOpcodes.REFERENCED_OPERATIONS,
    RcOpcodes.INCLUDE_REFERENCED_OPERATIONS,
  )

private fun RcDocument.inlineStructuralOperations(target: RcTargetProfile): RcDocument {
  val used = operations.mapTo(mutableSetOf()) { it.opcode } intersect STRUCTURAL_OPCODES
  if (used.isEmpty() || used.all { it in target.supportedOpcodes }) return this
  if (operations.any { it is RcComponentStart }) return this
  val linked = runCatching { RcDocumentLinker.link(this) }.getOrNull() ?: return this
  val flat = buildList {
    fun flatten(node: RcLinkedNode) {
      when (node) {
        is RcLinkedNode.Operation -> add(node.operation)
        is RcLinkedNode.Container -> {
          add(node.operation)
          node.children.forEach(::flatten)
          add(RcNoArg(RcOpcodes.CONTAINER_END))
        }
      }
    }
    linked.operations.forEach(::flatten)
  }
  return copy(operations = flat)
}

private fun RcDocument.dropRemarks(target: RcTargetProfile): RcDocument =
  if (RcOpcodes.REM in target.supportedOpcodes || operations.none { it is RcRemark }) this
  else copy(operations = operations.filterNot { it is RcRemark })

private fun RcDocument.foldLiteralFields(target: RcTargetProfile): RcDocument {
  if (target.apiLevel >= 7) return this
  val values = RcValueAnalysis(operations)
  var changed = false
  fun RcFloatWord.literalAt(index: Int): RcFloatWord {
    val id = referencedId?.takeIf { isNaNEncoded } ?: return this
    return values.float(id, at = index)?.also { changed = true } ?: this
  }
  // A colour reference Android 16 would read as a rectangle, inlined as the literal RGBA it reads.
  fun literalColor(flags: Int, colorId: Int, index: Int): LiteralColor {
    val argb = if (flags and COLOR_REFERENCE == 0) null else values.color(colorId, at = index)
    if (argb == null) return LiteralColor(flags, colorId, null)
    changed = true
    val rgba = listOf(16, 8, 0, 24).map { RcFloatWord.literal(((argb ushr it) and 0xff) / 255f) }
    return LiteralColor(flags and COLOR_REFERENCE.inv(), 0, rgba)
  }
  val rewritten = operations.mapIndexed { index, operation ->
    when (operation) {
      is RcImageLayout -> operation.copy(alpha = operation.alpha.literalAt(index))
      is RcTextLayout -> operation.copy(fontSize = operation.fontSize.literalAt(index))
      is RcPaddingModifier ->
        operation.copy(
          left = operation.left.literalAt(index),
          top = operation.top.literalAt(index),
          right = operation.right.literalAt(index),
          bottom = operation.bottom.literalAt(index),
        )
      is RcBackgroundModifier -> {
        val color = literalColor(operation.flags, operation.colorId, index)
        val (red, green, blue, alpha) =
          color.rgba ?: listOf(operation.red, operation.green, operation.blue, operation.alpha)
        operation.copy(
          flags = color.flags,
          colorId = color.colorId,
          red = red.literalAt(index),
          green = green.literalAt(index),
          blue = blue.literalAt(index),
          alpha = alpha.literalAt(index),
        )
      }
      is RcBorderModifier -> {
        val color = literalColor(operation.flags, operation.colorId, index)
        val (red, green, blue, alpha) =
          color.rgba ?: listOf(operation.red, operation.green, operation.blue, operation.alpha)
        operation.copy(
          flags = color.flags,
          colorId = color.colorId,
          borderWidth = operation.borderWidth.literalAt(index),
          roundedCorner = operation.roundedCorner.literalAt(index),
          red = red.literalAt(index),
          green = green.literalAt(index),
          blue = blue.literalAt(index),
          alpha = alpha.literalAt(index),
        )
      }
      else -> operation
    }
  }
  return if (changed) copy(operations = rewritten) else this
}

private const val COLOR_REFERENCE = RcBackgroundModifier.COLOR_REFERENCE_FLAG

private class LiteralColor(val flags: Int, val colorId: Int, val rgba: List<RcFloatWord>?)

private fun RcDocument.headerFor(
  target: RcTargetProfile,
  display: RcDisplay,
  violations: MutableList<RcProfileViolation>,
): RcDocument {
  if (target.apiLevel >= 7) {
    val rewritten = modernHeader(header, target, operations)
    return if (rewritten == header) this else copy(header = rewritten)
  }
  val rewritten = legacyHeader(header)
  if (rewritten == header) return this
  val scaled = densityScaledAsLegacy(display, violations) ?: return this
  return scaled.copy(header = rewritten)
}

/**
 * This document's border widths and offsets as the level 6 header form reads them: a level 7 header
 * with legacy density behaviour has them in pixels, and Android 16 multiplies them by the density.
 * Each literal is divided by [display]'s density; null, with the reason in [violations], when one
 * is a variable.
 */
private fun RcDocument.densityScaledAsLegacy(
  display: RcDisplay,
  violations: MutableList<RcProfileViolation>,
): RcDocument? {
  val scaled = mutableListOf<RcOperation>()
  operations.forEachIndexed { index, operation ->
    val fields =
      when (operation) {
        is RcBorderModifier -> listOf("borderWidth" to operation.borderWidth)
        is RcOffsetModifier -> listOf("x" to operation.x, "y" to operation.y)
        else -> {
          scaled += operation
          return@forEachIndexed
        }
      }
    val unscalable = fields.filter { (_, word) -> word.bits != 0 && word.isNaNEncoded }
    unscalable.forEach { (field, _) ->
      violations +=
        RcProfileViolation(
          RcProfileViolation.Kind.DENSITY_SCALED,
          index,
          operation.opcode,
          "${operationName(operation.opcode)}.$field is in pixels here and in dp on API level 6, " +
            "so it must be a literal",
        )
    }
    if (unscalable.isNotEmpty()) {
      scaled += operation
      return@forEachIndexed
    }
    fun RcFloatWord.inDp() = if (bits == 0) this else RcFloatWord.literal(value / display.density)
    scaled +=
      when (operation) {
        is RcBorderModifier -> operation.copy(borderWidth = operation.borderWidth.inDp())
        is RcOffsetModifier -> operation.copy(x = operation.x.inDp(), y = operation.y.inDp())
        else -> operation
      }
  }
  return if (violations.isEmpty()) copy(operations = scaled) else null
}

/** The fixed level 6 header, or [header] itself when it says something that form cannot. */
private fun legacyHeader(header: RcHeader): RcHeader {
  if (!header.modern) return header
  if (header.densityBehavior != RcHeader.DENSITY_BEHAVIOR_LEGACY) return header
  return RcHeader(
    version = RcVersion(1, 0, 0),
    legacyWidth = header.width,
    legacyHeight = header.height,
    legacyCapabilities = 0,
    modern = false,
  )
}

/**
 * [header], re-declared only where a player reading it would decode the document wrongly: when it
 * names an API level newer than [target] reads, or selects an operation set missing operations the
 * document uses and [target] has. The re-declaration names [target]'s profile bits, at the
 * document's own level where that is at least 7 and no newer than [target]'s.
 */
private fun modernHeader(
  header: RcHeader,
  target: RcTargetProfile,
  operations: List<RcOperation>,
): RcHeader {
  val declaredLevel = header.apiLevel
  val tooNew = declaredLevel == null || declaredLevel > target.apiLevel
  val declared = RcTargetProfile.of(declaredLevel ?: target.apiLevel, header.profiles)
  val undeclared =
    !target.fixedOperations &&
      operations.any {
        it.opcode in target.supportedOpcodes && it.opcode !in declared.supportedOpcodes
      }
  if (!tooNew && !undeclared) return header
  val level =
    if (declaredLevel == null || declaredLevel < 7) target.apiLevel
    else minOf(declaredLevel, target.apiLevel)
  val profiles = RcHeaderProperty(DOC_PROFILES, RcHeaderValue.IntValue(target.profiles))
  val properties =
    if (header.modern) header.properties.filterNot { it.key == DOC_PROFILES } + profiles
    else
      listOf(
        RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(header.width)),
        RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(header.height)),
        profiles,
      )
  return RcHeader(version = versionFor(level), properties = properties, modern = true)
}

private fun versionFor(apiLevel: Int): RcVersion = RcVersion(1, apiLevel - 6, 0)

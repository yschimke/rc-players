package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRemark
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
 * - **A variable image alpha or text size is replaced by its value** below API level 7, where those
 *   fields must be literals, when the variable is a constant nothing can change.
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
): RcProfileAdaptation {
  val dropped = mutableListOf<RcTextApproximation>()
  val adapted =
    inlineStructuralOperations(target)
      .coreTextAsTextLayout(target, RcApproximation.TEXT in approximations, dropped)
      .colorThemes(target, RcApproximation.THEME_COLORS in approximations)
      .dropRemarks(target)
      .foldLiteralFields(target)
      .headerFor(target)
  return RcProfileAdaptation(adapted, adapted.validateFor(target), dropped)
}

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
  val rewritten = operations.mapIndexed { index, operation ->
    when (operation) {
      is RcImageLayout -> operation.copy(alpha = operation.alpha.literalAt(index))
      is RcTextLayout -> operation.copy(fontSize = operation.fontSize.literalAt(index))
      else -> operation
    }
  }
  return if (changed) copy(operations = rewritten) else this
}

private fun RcDocument.headerFor(target: RcTargetProfile): RcDocument {
  val rewritten =
    if (target.apiLevel <= 6) legacyHeader(header) else modernHeader(header, target, operations)
  return if (rewritten == header) this else copy(header = rewritten)
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

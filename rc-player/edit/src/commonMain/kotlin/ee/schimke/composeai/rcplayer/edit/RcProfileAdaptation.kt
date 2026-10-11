package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode

/** A document rewritten for a [RcTargetProfile], and what still keeps it from playing there. */
public data class RcProfileAdaptation(
  val document: RcDocument,
  /** [document]'s remaining violations — [validateFor] on the result. */
  val violations: List<RcProfileViolation>,
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
public fun RcDocument.adaptTo(target: RcTargetProfile): RcProfileAdaptation {
  val adapted =
    inlineStructuralOperations(target)
      .dropRemarks(target)
      .foldLiteralFields(target)
      .headerFor(target)
  return RcProfileAdaptation(adapted, adapted.validateFor(target))
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
  val constants = ConstantFloats(operations)
  var changed = false
  fun RcFloatWord.literal(): RcFloatWord {
    val id = referencedId?.takeIf { isNaNEncoded } ?: return this
    return constants.resolve(id)?.also { changed = true } ?: this
  }
  val rewritten = operations.map { operation ->
    when (operation) {
      is RcImageLayout -> operation.copy(alpha = operation.alpha.literal())
      is RcTextLayout -> operation.copy(fontSize = operation.fontSize.literal())
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

/**
 * Float ids whose value is fixed for the life of the document: defined only by float constants that
 * agree, and written by nothing else this recognises — no host binding, action, expression, touch,
 * measurement, loop or the player's own system variables.
 */
private class ConstantFloats(operations: List<RcOperation>) {
  private val constants = mutableMapOf<Int, MutableSet<Int>>()
  private val variable = mutableSetOf<Int>()

  init {
    operations.forEach { operation ->
      when (operation) {
        is RcFloatConstant ->
          constants.getOrPut(operation.id) { mutableSetOf() } += operation.value.bits
        is RcFloatExpression -> variable += operation.id
        is RcTouchExpression -> variable += operation.id
        is RcComponentValue -> variable += operation.valueId
        is RcTextMeasure -> variable += operation.outId
        is RcLoopOperation -> variable += operation.indexVariableId
        is RcNamedVariable -> variable += operation.id
        is RcValueFloatChangeAction -> variable += operation.targetValueId
        is RcValueFloatExpressionChangeAction -> variable += operation.targetValueId
        else -> Unit
      }
    }
    variable += RcSystemVariables.ALL
  }

  fun resolve(id: Int): RcFloatWord? {
    if (id in variable) return null
    val value = constants[id]?.singleOrNull() ?: return null
    return RcFloatWord(value).takeUnless { it.isNaNEncoded }
  }
}

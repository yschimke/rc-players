package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcMatrixVectorMath
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathExpression
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLength
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcUpdateDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter
import ee.schimke.composeai.rcplayer.runtime.RcIntegerExpressionEvaluator

/**
 * This document with every state layout whose index can never change rewritten to the one branch
 * that index selects.
 *
 * An index can never change when it is an integer constant, or an integer expression over such
 * constants, that no host can bind (no named variable — see [hardcodeNamedValues]) and no action in
 * the document writes. The rewrite keeps the state layout itself, with its modifiers and the
 * selected branch, and points it at index id `0`: every player reads that as "no index variable,
 * show the first branch", which is now the only one. Keeping the container rather than unwrapping
 * the branch matters because a state layout sizes itself to its showing branch and ignores its own
 * fill modifiers; a box in its place would not.
 *
 * What the removed branches leave behind:
 * - Values they define in layout scope — text, constants, expressions, bitmaps, lists — are kept
 *   rather than deleted, because a player evaluates them whether or not their branch shows and
 *   something outside the branch may read one. A removed branch's values move to just before the
 *   state layout if the branch came before the selected one, and to just after it otherwise, so a
 *   later definition of the same id still wins.
 * - Values defined inside a conditional, loop or canvas run only as that container runs them, so
 *   they cannot be lifted out. They are dropped when nothing outside the removed branches can read
 *   them; when something might, the state layout is left as it is.
 * - Definitions inside click and touch handlers are actions, not definitions, and go with their
 *   branch.
 * - A `ComponentValue` that measures a component in a removed branch becomes a constant `0`, the
 *   geometry a hidden branch publishes.
 *
 * A state layout is also left as it is when its index is out of range for its branches, or when a
 * removed branch declares something that can be called from elsewhere (a function, a referenced
 * operation block). A document using macros is returned unchanged: an expanded macro can write any
 * id, so no index can be shown constant without expanding them.
 */
public fun RcDocument.collapseConstantStateLayouts(): RcDocument {
  val operations = operations
  if (operations.none { it is RcStateLayout }) return this
  if (operations.any { it is RcMacroDefine || it is RcMacroCall || it is RcMacroForEach }) {
    return this
  }
  val tree = operationTree(operations) ?: return this
  val constants = ConstantIntegers(operations)
  val reads = PossibleReads(operations)

  val removed = BooleanArray(operations.size)
  val replaced = mutableMapOf<Int, RcOperation>()
  val insertBefore = mutableMapOf<Int, List<RcOperation>>()
  val insertAfter = mutableMapOf<Int, List<RcOperation>>()
  val removedComponents = mutableSetOf<Int>()

  fun visit(node: RcOperationNode) {
    if (removed[node.start]) return
    val layout = node.operation as? RcStateLayout
    val plan = layout?.let { planCollapse(node, it, constants, reads) }
    if (plan != null) {
      replaced[node.start] = layout.copy(indexId = 0)
      insertBefore[node.start] = plan.before
      insertAfter[node.end] = plan.after
      plan.removed.forEach { branch ->
        for (index in branch.start..branch.end) removed[index] = true
        branch.walk { it.operation.declaredComponentId()?.let(removedComponents::add) }
      }
    }
    node.children.forEach(::visit)
  }
  tree.forEach(::visit)
  if (replaced.isEmpty()) return this

  fun RcOperation.unpublished(): RcOperation =
    if (this is RcComponentValue && componentId in removedComponents) {
      RcFloatConstant(valueId, RcFloatWord.literal(0f))
    } else {
      this
    }

  val rewritten = buildList {
    operations.forEachIndexed { index, operation ->
      insertBefore[index]?.forEach { add(it.unpublished()) }
      if (!removed[index]) add((replaced[index] ?: operation).unpublished())
      insertAfter[index]?.forEach { add(it.unpublished()) }
    }
  }
  return copy(operations = rewritten)
}

/** The branches a collapse removes, and the definitions kept from them on either side. */
private class CollapsePlan(
  val removed: List<RcOperationNode>,
  val before: List<RcOperation>,
  val after: List<RcOperation>,
)

/**
 * How to collapse [layout], or null to leave it alone.
 *
 * Branches are counted as the player counts them: the layout components inside its
 * `LayoutComponentContent`, or — a content wrapper being optional on the wire — directly inside it.
 */
private fun planCollapse(
  node: RcOperationNode,
  layout: RcStateLayout,
  constants: ConstantIntegers,
  reads: PossibleReads,
): CollapsePlan? {
  if (layout.indexId == 0) return null
  val selected = constants.resolve(layout.indexId) ?: return null
  val contents = node.children.filter { it.isContainer && it.operation is RcLayoutContent }
  if (contents.size > 1) return null
  val branches =
    (contents.singleOrNull() ?: node).children.filter {
      it.isContainer && it.operation.isLayoutComponent()
    }
  if (selected !in branches.indices) return null
  val removed = branches.filterIndexed { index, _ -> index != selected }
  var callable = false
  removed.forEach { branch ->
    branch.walk {
      callable =
        callable ||
          it.operation.opcode == RcOpcodes.FUNCTION_DEFINE ||
          it.operation.opcode == RcOpcodes.REFERENCED_OPERATIONS
    }
  }
  if (callable) return null
  val outside = { index: Int -> removed.none { index in it.start..it.end } }
  fun kept(from: List<RcOperationNode>): List<RcOperation>? = from.flatMap { branch ->
    branch.keptDefinitions(reads, outside) ?: return null
  }
  return CollapsePlan(
    removed = removed,
    before = kept(branches.take(selected)) ?: return null,
    after = kept(branches.drop(selected + 1)) ?: return null,
  )
}

/**
 * The definitions to keep from this removed subtree, in order, or null when one that cannot be kept
 * may still be read from an operation [outside] the removed branches.
 */
private fun RcOperationNode.keptDefinitions(
  reads: PossibleReads,
  outside: (Int) -> Boolean,
): List<RcOperation>? {
  val kept = mutableListOf<RcOperation>()
  fun collect(node: RcOperationNode, layoutScope: Boolean): Boolean {
    val operation = node.operation
    if (node.isContainer) {
      if (operation.opcode in ACTION_CONTAINERS) return true
      val childScope = layoutScope && operation.isLayoutComponent() && operation !is RcCanvasContent
      return node.children.all { collect(it, childScope) }
    }
    val ids = operation.definedIds() ?: return true
    return when {
      layoutScope -> kept.add(operation)
      else -> ids.none { reads.mayRead(it, outside) }
    }
  }
  return kept.takeIf { collect(this, layoutScope = true) }
}

private val ACTION_CONTAINERS: Set<Int> =
  setOf(
    RcOpcodes.MODIFIER_CLICK,
    RcOpcodes.MODIFIER_MULTI_CLICK,
    RcOpcodes.MODIFIER_TOUCH_DOWN,
    RcOpcodes.MODIFIER_TOUCH_UP,
    RcOpcodes.MODIFIER_TOUCH_CANCEL,
    RcOpcodes.RUN_ACTION,
  )

/**
 * The ids an operation gives a value, or null for one that defines nothing. Covers what the player
 * loads when a document opens and every producer it evaluates in a layout's content.
 */
private fun RcOperation.definedIds(): List<Int>? =
  when (this) {
    is RcTextData -> listOf(id)
    is RcFloatConstant -> listOf(id)
    is RcColorConstant -> listOf(id)
    is RcIntegerConstant -> listOf(id)
    is RcBooleanConstant -> listOf(id)
    is RcLongConstant -> listOf(id)
    is RcPathData -> listOf(id)
    is RcMatrixConstant -> listOf(id)
    is RcIdList -> listOf(id)
    is RcFloatList -> listOf(id)
    is RcIdMap -> listOf(id)
    is RcBitmapData -> listOf(imageId)
    is RcFontData -> listOf(fontId)
    is RcNamedVariable -> listOf(id)
    is RcDynamicFloatList -> listOf(id)
    is RcUpdateDynamicFloatList -> listOf(listId)
    is RcFloatExpression -> listOf(id)
    is RcIntegerExpression -> listOf(outId)
    is RcColorExpression -> listOf(outId)
    is RcColorTheme -> listOf(outId)
    is RcColorAttribute -> listOf(outId)
    is RcImageAttribute -> listOf(outId)
    is RcDataMapLookup -> listOf(outId)
    is RcIdLookup -> listOf(outId)
    is RcMatrixExpression -> listOf(id)
    is RcMatrixVectorMath -> outputs
    is RcPathExpression -> listOf(id)
    is RcTextFromFloat -> listOf(outId)
    is RcTextMerge -> listOf(outId)
    is RcTextLength -> listOf(outId)
    is RcTextSubtext -> listOf(outId)
    is RcTextTransform -> listOf(outId)
    is RcTextLookup -> listOf(outId)
    is RcTextLookupInt -> listOf(outId)
    is RcTextMeasure -> listOf(outId)
    is RcTouchExpression -> listOf(id)
    is RcComponentValue -> listOf(valueId)
    else -> null
  }

/**
 * Whether any operation might read an id, found the way `referencesAnyOf` finds system variables:
 * by scanning each operation's encoding for the id as a plain word or a NaN-boxed reference at
 * every offset. It over-reports — a literal that happens to equal the id counts — which only ever
 * makes a collapse more cautious.
 */
private class PossibleReads(private val operations: List<RcOperation>) {
  private val words = arrayOfNulls<Set<Int>>(operations.size)

  fun mayRead(id: Int, include: (Int) -> Boolean): Boolean =
    operations.indices.any { index ->
      include(index) && wordsAt(index).let { ANY in it || id in it }
    }

  private fun wordsAt(index: Int): Set<Int> =
    words[index]
      ?: buildSet {
        val bytes = runCatching {
          RcWireWriter()
            .also { RcDocumentCodec.encodeOperation(it, operations[index]) }
            .toByteArray()
        }
          .getOrNull()
        if (bytes == null) {
          // An operation that cannot be encoded cannot be scanned: assume it reads everything.
          add(ANY)
          return@buildSet
        }
        for (offset in 0..bytes.size - Int.SIZE_BYTES) {
          var bits = 0
          for (i in 0 until Int.SIZE_BYTES) {
            bits = (bits shl 8) or (bytes[offset + i].toInt() and 0xff)
          }
          add(bits)
          RcFloatWord(bits).referencedId?.let(::add)
        }
      }
        .also { words[index] = it }

  private companion object {
    const val ANY = Int.MIN_VALUE
  }
}

/**
 * Integer ids whose value is fixed for the life of the document.
 *
 * Deliberately conservative: an id is constant only when everything that writes it is known and
 * constant. An id written by anything this does not recognise as a constant — an action, a lookup,
 * a host binding, the player's own system variables — is not.
 */
private class ConstantIntegers(operations: List<RcOperation>) {
  private val writers = mutableMapOf<Int, MutableList<RcOperation>>()
  private val variable = mutableSetOf<Int>()
  private val resolved = mutableMapOf<Int, Int?>()
  private val resolving = mutableSetOf<Int>()

  init {
    operations.forEach { operation ->
      when (operation) {
        is RcIntegerConstant -> writers.getOrPut(operation.id) { mutableListOf() } += operation
        is RcIntegerExpression -> writers.getOrPut(operation.outId) { mutableListOf() } += operation
        is RcNamedVariable -> variable += operation.id
        is RcValueIntegerChangeAction -> variable += operation.targetValueId
        is RcValueIntegerExpressionChangeAction -> variable += operation.targetValueId.toInt()
        is RcIdLookup -> variable += operation.outId
        is RcDataMapLookup -> variable += operation.outId
        else -> Unit
      }
    }
    variable += RcSystemVariables.ALL
  }

  fun resolve(id: Int): Int? {
    if (id in resolved) return resolved[id]
    if (id in variable || !resolving.add(id)) return null
    val value = compute(id)
    resolving -= id
    resolved[id] = value
    return value
  }

  private fun compute(id: Int): Int? {
    val definitions = writers[id] ?: return null
    if (definitions.all { it is RcIntegerConstant }) {
      return definitions.map { (it as RcIntegerConstant).value }.distinct().singleOrNull()
    }
    val expression = definitions.singleOrNull() as? RcIntegerExpression ?: return null
    if (RcIntegerExpressionEvaluator.validationError(expression) != null) return null
    return try {
      RcIntegerExpressionEvaluator.evaluate(expression) { input ->
        resolve(input) ?: throw NotConstant
      }
    } catch (_: NotConstant) {
      null
    }
  }

  private object NotConstant : RuntimeException()
}

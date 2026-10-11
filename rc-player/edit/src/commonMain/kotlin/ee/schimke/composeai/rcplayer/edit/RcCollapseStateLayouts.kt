package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLength
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
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
 * - Values they define — text, constants, expressions, bitmaps, lists — are moved ahead of the
 *   state layout rather than deleted. A player loads them whether or not their branch shows, so
 *   something outside the branch may read one. Definitions inside click and touch handlers are
 *   actions, not definitions, and go with their branch.
 * - A `ComponentValue` that measures a component in a removed branch becomes a constant `0`, the
 *   geometry a hidden branch publishes.
 *
 * A state layout is left as it is when its index is out of range for its branches, or when a
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

  val removed = BooleanArray(operations.size)
  val replaced = mutableMapOf<Int, RcOperation>()
  val hoisted = mutableMapOf<Int, List<RcOperation>>()
  val removedComponents = mutableSetOf<Int>()

  fun visit(node: RcOperationNode) {
    if (removed[node.start]) return
    val layout = node.operation as? RcStateLayout
    val collapse = layout?.let { planCollapse(node, it, constants) }
    if (collapse != null) {
      replaced[node.start] = layout.copy(indexId = 0)
      hoisted[node.start] = collapse.flatMap { it.hoistableDefinitions() }
      collapse.forEach { branch ->
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
      hoisted[index]?.forEach { add(it.unpublished()) }
      if (!removed[index]) add((replaced[index] ?: operation).unpublished())
    }
  }
  return copy(operations = rewritten)
}

/**
 * The branches of [layout] to remove, or null to leave it alone.
 *
 * Branches are counted as the player counts them: the layout components inside its
 * `LayoutComponentContent`, or — a content wrapper being optional on the wire — directly inside it.
 */
private fun planCollapse(
  node: RcOperationNode,
  layout: RcStateLayout,
  constants: ConstantIntegers,
): List<RcOperationNode>? {
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
  return removed.takeUnless { callable }
}

/** The value definitions inside this subtree, in order, skipping action handler bodies. */
private fun RcOperationNode.hoistableDefinitions(): List<RcOperation> = buildList {
  fun collect(node: RcOperationNode) {
    if (node.isContainer && node.operation.opcode in ACTION_CONTAINERS) return
    if (!node.isContainer && node.operation.isDefinition()) add(node.operation)
    node.children.forEach(::collect)
  }
  collect(this@hoistableDefinitions)
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

/** Operations that only give an id a value — safe to move, and possibly read from elsewhere. */
private fun RcOperation.isDefinition(): Boolean =
  this is RcTextData ||
    this is RcFloatConstant ||
    this is RcFloatExpression ||
    this is RcColorConstant ||
    this is RcColorExpression ||
    this is RcColorTheme ||
    this is RcColorAttribute ||
    this is RcIntegerConstant ||
    this is RcIntegerExpression ||
    this is RcBooleanConstant ||
    this is RcLongConstant ||
    this is RcIdList ||
    this is RcFloatList ||
    this is RcIdMap ||
    this is RcIdLookup ||
    this is RcDataMapLookup ||
    this is RcNamedVariable ||
    this is RcPathData ||
    this is RcMatrixConstant ||
    this is RcTextMerge ||
    this is RcTextLength ||
    this is RcTextLookup ||
    this is RcTextLookupInt ||
    this is RcTextMeasure ||
    this is RcBitmapData ||
    this is RcFontData ||
    this is RcComponentValue

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

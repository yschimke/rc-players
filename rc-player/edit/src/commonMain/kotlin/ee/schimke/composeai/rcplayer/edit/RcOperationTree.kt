package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTheme

/**
 * One operation of a flat document, with the container body it opens.
 *
 * Positions rather than copies: an edit removes or inserts by index into the original list, so the
 * operations it does not touch are re-encoded exactly as they were decoded. [end] is the index of
 * the matching `ContainerEnd`, or [start] itself for an operation that opens nothing.
 */
internal class RcOperationNode(
  val start: Int,
  val end: Int,
  val operation: RcOperation,
  val children: List<RcOperationNode>,
) {
  val isContainer: Boolean
    get() = end > start

  fun walk(visit: (RcOperationNode) -> Unit) {
    visit(this)
    children.forEach { it.walk(visit) }
  }
}

/**
 * The container tree of [operations], or null when it is not balanced.
 *
 * Null is an answer, not an error: an edit that needs the tree leaves a document it cannot read as
 * one unchanged, rather than guessing where a container ends and deleting the wrong operations.
 */
internal fun operationTree(operations: List<RcOperation>): List<RcOperationNode>? {
  class Frame(val start: Int, val children: MutableList<RcOperationNode>)
  val root = mutableListOf<RcOperationNode>()
  val stack = mutableListOf<Frame>()
  operations.forEachIndexed { index, operation ->
    val destination = stack.lastOrNull()?.children ?: root
    when {
      operation.opensContainer() -> stack += Frame(index, mutableListOf())
      operation.opcode == RcOpcodes.CONTAINER_END -> {
        val frame = stack.removeLastOrNull() ?: return null
        (stack.lastOrNull()?.children ?: root) +=
          RcOperationNode(frame.start, index, operations[frame.start], frame.children)
      }
      else -> destination += RcOperationNode(index, index, operation, emptyList())
    }
  }
  return root.takeIf { stack.isEmpty() }
}

private fun RcOperation.opensContainer(): Boolean =
  opcode in RcOpcodes.CONTAINER_STARTS || (this is RcMacroDefine && body.isEmpty())

/**
 * Whether this operation is a layout component — the set the player's layout tree builds nodes
 * from, and so the set a state layout counts its branches in.
 */
internal fun RcOperation.isLayoutComponent(): Boolean =
  this is RcRootLayout ||
    this is RcLayoutContent ||
    this is RcCanvasLayout ||
    this is RcCanvasContent ||
    this is RcCustomLayout ||
    this is RcBoxLayout ||
    this is RcRowLayout ||
    this is RcColumnLayout ||
    this is RcFlowLayout ||
    this is RcStateLayout ||
    this is RcCollapsibleRowLayout ||
    this is RcCollapsibleColumnLayout ||
    this is RcFitBoxLayout ||
    this is RcImageLayout ||
    this is RcTextLayout ||
    this is RcCoreText

/** The component id an operation declares, for the ones that declare one. */
internal fun RcOperation.declaredComponentId(): Int? =
  when (this) {
    is RcRootLayout -> componentId
    is RcLayoutContent -> componentId
    is RcCanvasLayout -> componentId
    is RcCanvasContent -> componentId
    is RcCustomLayout -> componentId
    is RcBoxLayout -> componentId
    is RcRowLayout -> componentId
    is RcColumnLayout -> componentId
    is RcFlowLayout -> componentId
    is RcStateLayout -> componentId
    is RcCollapsibleRowLayout -> componentId
    is RcCollapsibleColumnLayout -> componentId
    is RcFitBoxLayout -> componentId
    is RcImageLayout -> componentId
    is RcTextLayout -> componentId
    is RcComponentStart -> componentId
    else -> null
  }

/**
 * Which of [operations] run every time the document plays, by index, or null when its containers do
 * not balance.
 *
 * An operation runs unconditionally when it sits at the top level or in layout scope — inside
 * layout components and their content — rather than inside a container that runs its body on its
 * own terms (a conditional, a loop, a canvas, an action handler, a function), and when no theme
 * marker earlier in the stream has gated it to a single theme. A value written anywhere else may
 * never be written at all, so nothing that reasons about constants can count on it.
 */
internal fun unconditionalOperations(operations: List<RcOperation>): BooleanArray? {
  val tree = operationTree(operations) ?: return null
  val result = BooleanArray(operations.size)
  fun visit(node: RcOperationNode, inScope: Boolean) {
    result[node.start] = inScope
    val childScope =
      inScope && node.operation.isLayoutComponent() && node.operation !is RcCanvasContent
    node.children.forEach { visit(it, childScope) }
  }
  tree.forEach { visit(it, inScope = true) }
  // A theme marker gates everything after it in the stream until a marker lifts the gate.
  var gated = false
  operations.forEachIndexed { index, operation ->
    if (operation is RcTheme) gated = operation.theme != RcTheme.UNSPECIFIED
    else if (gated) result[index] = false
  }
  return result
}

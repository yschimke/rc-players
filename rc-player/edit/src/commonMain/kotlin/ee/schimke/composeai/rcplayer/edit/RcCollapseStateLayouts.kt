package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge

/**
 * This document with every state layout whose index can never change rewritten to the one branch
 * that index selects.
 *
 * An index can never change when it is an integer constant, or an integer expression over such
 * constants, written where it always runs and before the state layout reads it, and when nothing
 * else in the document that mentions it could write it or hand it to a host — see
 * [RcValueAnalysis], and [hardcodeNamedValues] to pin a host-bound index first. The rewrite keeps
 * the state layout itself, with its modifiers and the selected branch, and points it at index id
 * `0`: every player reads that as "no index variable, show the first branch", which is now the only
 * one. Keeping the container rather than unwrapping the branch matters because a state layout sizes
 * itself to its showing branch and ignores its own fill modifiers; a box in its place would not.
 *
 * **Conservative by construction.** A branch is only removed when everything in it is local to it
 * or a plain value: layout components, modifiers, drawing, actions inside click and touch handlers
 * (which cannot fire while it is hidden), and the data, constants and expressions that only give an
 * id a value. A `.rc` document's ids are document-wide, so those values **stay**, in place and in
 * order, where the branch was: something outside it may read them, and a host may still bind a
 * named one. Some of a document's semantics are positional, so anything else in a hidden branch — a
 * style, font, theme marker, conditional, loop, a value inside a handler, or an operation this does
 * not recognise — leaves the state layout as it is. A `ComponentValue` that measures a component in
 * a removed branch becomes the constant `0` a hidden branch publishes.
 *
 * Also left as they are: a state layout whose index is out of range for its branches, and every
 * state layout in a document using macros, since an expanded macro can write any id.
 */
public fun RcDocument.collapseConstantStateLayouts(): RcDocument {
  val operations = operations
  if (operations.none { it is RcStateLayout }) return this
  if (operations.any { it is RcMacroDefine || it is RcMacroCall || it is RcMacroForEach }) {
    return this
  }
  val tree = operationTree(operations) ?: return this
  val values = RcValueAnalysis(operations)

  val removed = BooleanArray(operations.size)
  val replaced = mutableMapOf<Int, RcOperation>()
  val removedComponents = mutableSetOf<Int>()

  fun visit(node: RcOperationNode) {
    if (removed[node.start]) return
    val layout = node.operation as? RcStateLayout
    val branches = layout?.let { removableBranches(node, it, values) }
    if (branches != null) {
      replaced[node.start] = layout.copy(indexId = 0)
      branches.forEach { branch ->
        for (index in branch.start..branch.end) {
          if (!operations[index].isPlainValue()) removed[index] = true
        }
        branch.walk { it.operation.declaredComponentId()?.let(removedComponents::add) }
      }
    }
    node.children.forEach(::visit)
  }
  tree.forEach(::visit)
  if (replaced.isEmpty()) return this

  val rewritten = operations.mapIndexedNotNull { index, operation ->
    when {
      removed[index] -> null
      operation is RcComponentValue && operation.componentId in removedComponents ->
        RcFloatConstant(operation.valueId, RcFloatWord.literal(0f))
      else -> replaced[index] ?: operation
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
private fun removableBranches(
  node: RcOperationNode,
  layout: RcStateLayout,
  values: RcValueAnalysis,
): List<RcOperationNode>? {
  if (layout.indexId == 0) return null
  val selected = values.integer(layout.indexId, at = node.start) ?: return null
  val branches = node.stateLayoutBranches() ?: return null
  if (selected !in branches.indices) return null
  val removed = branches.filterIndexed { index, _ -> index != selected }
  return removed.takeIf { it.all { branch -> branch.isLocal(inAction = false) } }
}

/**
 * A state layout node's branches, counted as the player counts them: the layout components inside
 * its `LayoutComponentContent`, or directly inside it. Null when it has more than one content.
 */
internal fun RcOperationNode.stateLayoutBranches(): List<RcOperationNode>? {
  val contents = children.filter { it.isContainer && it.operation is RcLayoutContent }
  if (contents.size > 1) return null
  return (contents.singleOrNull() ?: this).children.filter {
    it.isContainer && it.operation.isLayoutComponent()
  }
}

/** Whether nothing in this subtree can be felt outside it, once the plain values in it are kept. */
private fun RcOperationNode.isLocal(inAction: Boolean): Boolean {
  val opcode = operation.opcode
  val allowed =
    opcode in LOCAL_OPCODES ||
      opcode in ACTION_CONTAINERS ||
      (inAction && opcode in ACTION_OPCODES) ||
      (!inAction && !isContainer && operation.isPlainValue())
  if (!allowed) return false
  val childInAction = inAction || opcode in ACTION_CONTAINERS
  return children.all { it.isLocal(childInAction) }
}

/**
 * An operation whose only effect is the value it gives an id, wherever in a layout it is written:
 * what a removed branch leaves behind, since ids are document-wide. A named variable is one too —
 * it is the host's handle on the constant beside it.
 */
internal fun RcOperation.isPlainValue(): Boolean =
  this is RcTextData ||
    this is RcFloatConstant ||
    this is RcIntegerConstant ||
    this is RcColorConstant ||
    this is RcBooleanConstant ||
    this is RcLongConstant ||
    this is RcPathData ||
    this is RcBitmapData ||
    this is RcNamedVariable ||
    this is RcFloatExpression ||
    this is RcIntegerExpression ||
    this is RcColorExpression ||
    this is RcComponentValue ||
    this is RcTextFromFloat ||
    this is RcTextMerge

/**
 * What a hidden branch may hold and still be removed: its layout, its modifiers and its drawing.
 * None of these gives an id a value another operation could read. Deliberately absent: scroll
 * (which keeps scroll state), custom and compute layouts, draw-to-bitmap (which fills a bitmap id),
 * and everything that defines a value ([isPlainValue] covers the ones that stay behind).
 */
private val LOCAL_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.CONTAINER_END,
    RcOpcodes.COMPONENT_START,
    RcOpcodes.LAYOUT_BOX,
    RcOpcodes.LAYOUT_CANVAS,
    RcOpcodes.LAYOUT_CANVAS_CONTENT,
    RcOpcodes.LAYOUT_COLLAPSIBLE_COLUMN,
    RcOpcodes.LAYOUT_COLLAPSIBLE_ROW,
    RcOpcodes.LAYOUT_COLUMN,
    RcOpcodes.LAYOUT_CONTENT,
    RcOpcodes.LAYOUT_FIT_BOX,
    RcOpcodes.LAYOUT_FLOW,
    RcOpcodes.LAYOUT_IMAGE,
    RcOpcodes.LAYOUT_ROW,
    RcOpcodes.LAYOUT_STATE,
    RcOpcodes.LAYOUT_TEXT,
    RcOpcodes.CORE_TEXT,
    RcOpcodes.ANIMATION_SPEC,
    RcOpcodes.ACCESSIBILITY_SEMANTICS,
    RcOpcodes.MODIFIER_ALIGN_BY,
    RcOpcodes.MODIFIER_BACKGROUND,
    RcOpcodes.MODIFIER_BORDER,
    RcOpcodes.MODIFIER_CLIP_RECT,
    RcOpcodes.MODIFIER_COLLAPSIBLE_PRIORITY,
    RcOpcodes.MODIFIER_DIMENSION_CONSTRAINTS,
    RcOpcodes.MODIFIER_DRAW_CONTENT,
    RcOpcodes.MODIFIER_GRAPHICS_LAYER,
    RcOpcodes.MODIFIER_HEIGHT,
    RcOpcodes.MODIFIER_HEIGHT_IN,
    RcOpcodes.MODIFIER_MARQUEE,
    RcOpcodes.MODIFIER_OFFSET,
    RcOpcodes.MODIFIER_PADDING,
    RcOpcodes.MODIFIER_RIPPLE,
    RcOpcodes.MODIFIER_ROUNDED_CLIP_RECT,
    RcOpcodes.MODIFIER_VISIBILITY,
    RcOpcodes.MODIFIER_WIDTH,
    RcOpcodes.MODIFIER_WIDTH_IN,
    RcOpcodes.MODIFIER_ZINDEX,
    RcOpcodes.CANVAS_OPERATIONS,
    RcOpcodes.PAINT_VALUES,
    RcOpcodes.CLIP_PATH,
    RcOpcodes.CLIP_RECT,
    RcOpcodes.MATRIX_SAVE,
    RcOpcodes.MATRIX_RESTORE,
    RcOpcodes.MATRIX_ROTATE,
    RcOpcodes.MATRIX_SCALE,
    RcOpcodes.MATRIX_SKEW,
    RcOpcodes.MATRIX_TRANSLATE,
    RcOpcodes.DRAW_ARC,
    RcOpcodes.DRAW_BITMAP,
    RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN,
    RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH,
    RcOpcodes.DRAW_BITMAP_INT,
    RcOpcodes.DRAW_BITMAP_SCALED,
    RcOpcodes.DRAW_BITMAP_TEXT_ANCHORED,
    RcOpcodes.DRAW_CIRCLE,
    RcOpcodes.DRAW_CONTENT,
    RcOpcodes.DRAW_LINE,
    RcOpcodes.DRAW_OVAL,
    RcOpcodes.DRAW_PATH,
    RcOpcodes.DRAW_RECT,
    RcOpcodes.DRAW_ROUND_RECT,
    RcOpcodes.DRAW_SECTOR,
    RcOpcodes.DRAW_TEXT_ANCHOR,
    RcOpcodes.DRAW_TEXT_ON_CIRCLE,
    RcOpcodes.DRAW_TEXT_ON_PATH,
    RcOpcodes.DRAW_TEXT_RUN,
    RcOpcodes.DRAW_TWEEN_PATH,
  )

/** Handlers whose bodies only run when a user touches, or the branch draws — never while hidden. */
private val ACTION_CONTAINERS: Set<Int> =
  setOf(
    RcOpcodes.MODIFIER_CLICK,
    RcOpcodes.MODIFIER_MULTI_CLICK,
    RcOpcodes.MODIFIER_TOUCH_DOWN,
    RcOpcodes.MODIFIER_TOUCH_UP,
    RcOpcodes.MODIFIER_TOUCH_CANCEL,
    RcOpcodes.RUN_ACTION,
  )

/** The actions such a handler may hold. */
private val ACTION_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.HOST_ACTION,
    RcOpcodes.HOST_METADATA_ACTION,
    RcOpcodes.HOST_NAMED_ACTION,
    RcOpcodes.VALUE_INTEGER_CHANGE_ACTION,
    RcOpcodes.VALUE_INTEGER_EXPRESSION_CHANGE_ACTION,
    RcOpcodes.VALUE_FLOAT_CHANGE_ACTION,
    RcOpcodes.VALUE_FLOAT_EXPRESSION_CHANGE_ACTION,
    RcOpcodes.VALUE_STRING_CHANGE_ACTION,
    RcOpcodes.HAPTIC_FEEDBACK,
    RcOpcodes.PLAY_SOUND,
  )

/**
 * This document with every state layout that is down to one branch replaced by that branch.
 *
 * [collapseConstantStateLayouts] keeps the container, which is right for a player that sizes a
 * state layout as the format says. The platform's widget player (Android 16 and 17) does not: a
 * branch that fills its parent is laid out with no size inside a state layout, and what follows it
 * in a column is placed over what came before. So a document baked for that player
 * ([hardcodeNamedValues], then collapse) is finished with this: the branch takes the state layout's
 * place in its parent, and is laid out as any other child.
 *
 * Only a state layout that shows its first branch unconditionally (index id `0`), has exactly one,
 * has no modifiers or animation of its own, and holds nothing else but plain values is replaced;
 * those values stay where they were. A `ComponentValue` that measured the state layout, or its
 * content, measures the branch instead. A document using macros is returned as it is.
 */
public fun RcDocument.unwrapSingleBranchStateLayouts(): RcDocument {
  val operations = operations
  if (operations.none { it is RcStateLayout && it.indexId == 0 }) return this
  if (operations.any { it is RcMacroDefine || it is RcMacroCall || it is RcMacroForEach }) {
    return this
  }
  val tree = operationTree(operations) ?: return this

  val removed = BooleanArray(operations.size)
  // The component a removed state layout or content is now measured as.
  val measuredAs = mutableMapOf<Int, Int>()

  fun visit(node: RcOperationNode) {
    val layout = node.operation as? RcStateLayout
    if (layout != null && layout.indexId == 0 && layout.animationId == -1) {
      val content = node.children.singleOrNull()?.takeIf { it.operation is RcLayoutContent }
      val inside = content?.children
      val branch = inside?.singleOrNull { it.isContainer }
      val branchId = branch?.operation?.takeIf { it.isLayoutComponent() }?.declaredComponentId()
      val restArePlain = inside?.all { it === branch || it.operation.isPlainValue() } == true
      if (content != null && branchId != null && restArePlain) {
        listOf(node.start, node.end, content.start, content.end).forEach { removed[it] = true }
        measuredAs[layout.componentId] = branchId
        content.operation.declaredComponentId()?.let { measuredAs[it] = branchId }
      }
    }
    node.children.forEach(::visit)
  }
  tree.forEach(::visit)
  if (measuredAs.isEmpty()) return this

  // A branch that was itself an unwrapped state layout: follow it to the component that is left.
  fun resolve(componentId: Int): Int {
    var id = componentId
    repeat(measuredAs.size) { id = measuredAs[id] ?: return id }
    return id
  }
  val rewritten = operations.mapIndexedNotNull { index, operation ->
    when {
      removed[index] -> null
      operation is RcComponentValue && operation.componentId in measuredAs ->
        operation.copy(componentId = resolve(operation.componentId))
      else -> operation
    }
  }
  return copy(operations = rewritten)
}

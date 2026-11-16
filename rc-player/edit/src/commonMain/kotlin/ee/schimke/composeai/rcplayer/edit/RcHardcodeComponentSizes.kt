package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier

/**
 * This document as it plays at one size, [widthPx] × [heightPx] for its root: every
 * `ComponentValue` that reads the width or height of a component whose size that root fixes becomes
 * a constant.
 *
 * A size breakpoint (`RcSizeBreakpoint`), a canvas drawn to its own size, a radius that is half a
 * height: each reads its component's measured size, which no other edit can know. A host that knows
 * the size it will play the document at — a watch's widget container, a launcher cell — calls this,
 * then [collapseConstantStateLayouts] and [optimize], and only the branch and values for that size
 * are left.
 *
 * **Conservative by construction**, like the state-layout collapse: a component's size is resolved
 * only when its layout leaves no doubt, from its parent's content size, and left as it is
 * otherwise.
 * - **Size modifiers:** an axis is known from a fill (`fillMaxWidth`, its fraction) of a known
 *   parent. An exact size, which the player clamps to its parent's constraints, wrap, weight,
 *   `widthIn` or any second size modifier for the axis leaves it unknown.
 * - **State layouts** are as big as their active branch, whatever their own size and padding
 *   modifiers say: one is known on an axis only when all its branches fill it, and passes on what
 *   it is offered.
 * - **Visibility:** a component that is or may become GONE measures 0 × 0, so one with a visibility
 *   modifier is resolved only while that is a constant that keeps its size (visible or invisible);
 *   its children are left otherwise.
 * - **Wrapping:** an axis with no size modifier wraps its content, so it is known only for a box,
 *   state layout or canvas whose children all fill it entirely: it takes the space it is offered.
 * - **Padding** with constant values after the size modifier shrinks the content its children
 *   share. Padding before it (an outer margin), or on a wrapping axis, leaves that axis unknown.
 * - **Other modifiers** may only be ones that don't change a size: background, border, clip,
 *   offset, z-index, graphics layer, click, animation spec, draw content and plain values. Any
 *   other modifier leaves the whole component unknown.
 * - **Children:** a box's, state layout's and canvas's children each get its content size. A row's
 *   or column's children get the cross axis, and the main axis too only for a single child. Any
 *   other layout leaves its children unknown.
 *
 * Right only at that size. A document using macros is returned unchanged, since an expanded macro
 * can write any id.
 */
public fun RcDocument.hardcodeComponentSizes(widthPx: Float, heightPx: Float): RcDocument {
  if (operations.none { it is RcComponentValue }) return this
  if (operations.any { it is RcMacroDefine || it is RcMacroCall || it is RcMacroForEach }) {
    return this
  }
  val tree = operationTree(operations) ?: return this
  val values = RcValueAnalysis(operations)
  val sizes = mutableMapOf<Int, Pair<Float?, Float?>>()

  fun constant(word: RcFloatWord, at: Int): Float? {
    val resolved = if (word.isNaNEncoded) values.float(word.referencedId!!, at) else word
    return resolved?.takeUnless { it.isNaNEncoded }?.value?.takeIf { it.isFinite() }
  }

  fun visit(node: RcOperationNode, availableWidth: Float?, availableHeight: Float?) {
    val layout = node.operation
    if (layout is RcRootLayout) {
      sizes[layout.componentId] = widthPx to heightPx
      layoutChildren(node).forEach { visit(it, widthPx, heightPx) }
      return
    }
    val id = layout.declaredComponentId() ?: return
    val children = layoutChildren(node)
    if (!keepsItsSize(node, values)) {
      children.forEach { visit(it, null, null) }
      return
    }
    if (layout is RcStateLayout && !onlyDimensionsAside(node)) {
      // The player still applies its other modifiers (a `widthIn`, say): not modelled.
      children.forEach { visit(it, null, null) }
      return
    }
    if (layout is RcStateLayout) {
      // As big as its active branch, its own size and padding modifiers aside (as the player lays
      // it out): known on an axis its branches all fill, and offering its children what it is
      // offered.
      fun all(width: Boolean) =
        children.isNotEmpty() && children.all { fills(it, width, ::constant) }
      sizes[id] = availableWidth?.takeIf { all(true) } to availableHeight?.takeIf { all(false) }
      children.forEach { visit(it, availableWidth, availableHeight) }
      return
    }
    val stacks = layout is RcBoxLayout || layout is RcCanvasLayout
    val measured =
      measure(
        node,
        availableWidth,
        availableHeight,
        // A wrapping box takes what it is offered on an axis every child fills.
        wrapsWidth =
          stacks && children.isNotEmpty() && children.all { fills(it, width = true, ::constant) },
        wrapsHeight =
          stacks && children.isNotEmpty() && children.all { fills(it, width = false, ::constant) },
        constant = ::constant,
      )
    if (measured == null) {
      children.forEach { visit(it, null, null) }
      return
    }
    sizes[id] = measured.width to measured.height
    val (childWidth, childHeight) =
      when (layout) {
        is RcBoxLayout,
        is RcCanvasLayout -> measured.contentWidth to measured.contentHeight
        is RcColumnLayout ->
          measured.contentWidth to measured.contentHeight.takeIf { children.size == 1 }
        is RcRowLayout ->
          measured.contentWidth.takeIf { children.size == 1 } to measured.contentHeight
        else -> null to null
      }
    children.forEach { visit(it, childWidth, childHeight) }
  }
  tree.forEach { visit(it, null, null) }

  var changed = false
  val rewritten = operations.map { operation ->
    if (operation !is RcComponentValue) return@map operation
    val (width, height) = sizes[operation.componentId] ?: return@map operation
    val value =
      when (operation.type) {
        RcComponentValue.WIDTH -> width
        RcComponentValue.HEIGHT -> height
        else -> null
      } ?: return@map operation
    changed = true
    RcFloatConstant(operation.valueId, RcFloatWord.literal(value))
  }
  return if (changed) copy(operations = rewritten) else this
}

private class Measured(
  val width: Float?,
  val height: Float?,
  val contentWidth: Float?,
  val contentHeight: Float?,
)

/**
 * [node]'s size from its modifiers, given its parent's content size, and its own content size; null
 * when a modifier it doesn't know may change its size.
 */
private fun measure(
  node: RcOperationNode,
  availableWidth: Float?,
  availableHeight: Float?,
  wrapsWidth: Boolean,
  wrapsHeight: Boolean,
  constant: (RcFloatWord, Int) -> Float?,
): Measured? {
  var width: Float? = null
  var height: Float? = null
  var widthKnown = true
  var heightKnown = true
  var widthSized = false
  var heightSized = false
  var paddingWidth = 0f
  var paddingHeight = 0f
  for (child in node.children) {
    val operation = child.operation
    if (operation is RcLayoutContent || operation is RcCanvasContent) break
    when (operation) {
      is RcWidthModifier -> {
        if (widthSized) widthKnown = false
        widthSized = true
        width = dimension(operation.type, constant(operation.value, child.start), availableWidth)
      }
      is RcHeightModifier -> {
        if (heightSized) heightKnown = false
        heightSized = true
        height = dimension(operation.type, constant(operation.value, child.start), availableHeight)
      }
      is RcPaddingModifier -> {
        val left = constant(operation.left, child.start)
        val top = constant(operation.top, child.start)
        val right = constant(operation.right, child.start)
        val bottom = constant(operation.bottom, child.start)
        if (!widthSized || left == null || right == null) widthKnown = false
        else paddingWidth += left + right
        if (!heightSized || top == null || bottom == null) heightKnown = false
        else paddingHeight += top + bottom
      }
      // Read before measuring ([keepsItsSize]): only a size-keeping constant gets here.
      is RcVisibilityModifier -> Unit
      else -> if (!operation.keepsSize()) return null
    }
  }
  // A wrapping axis (no size modifier) is the space offered when its children all fill it, and
  // padding on it would make that the padding's sum instead: unknown then.
  if (!widthSized && wrapsWidth && paddingWidth == 0f && widthKnown) width = availableWidth
  if (!heightSized && wrapsHeight && paddingHeight == 0f && heightKnown) height = availableHeight
  val w = width?.takeIf { widthKnown }
  val h = height?.takeIf { heightKnown }
  return Measured(w, h, w?.minus(paddingWidth), h?.minus(paddingHeight))
}

/**
 * Whether [node] fills its parent's content on one axis entirely: its only size modifier for the
 * axis is a full fill, with no padding before it and no modifier that may change its size; or it
 * has none (it wraps), no padding, and is a box, state layout or canvas whose children all fill it.
 */
private fun fills(
  node: RcOperationNode,
  width: Boolean,
  constant: (RcFloatWord, Int) -> Float?,
): Boolean {
  if (node.operation is RcStateLayout) {
    // Its own size and padding modifiers aside: it is its active branch's size.
    if (!onlyDimensionsAside(node)) return false
    val branches = layoutChildren(node)
    return branches.isNotEmpty() && branches.all { fills(it, width, constant) }
  }
  var sized = false
  var fill = false
  var padded = false
  for (child in node.children) {
    val operation = child.operation
    if (operation is RcLayoutContent || operation is RcCanvasContent) break
    when {
      width && operation is RcWidthModifier || !width && operation is RcHeightModifier -> {
        if (sized) return false
        sized = true
        val (type, value) =
          if (operation is RcWidthModifier) operation.type to operation.value
          else (operation as RcHeightModifier).type to operation.value
        fill = !padded && type == RcDimensionType.FILL && constant(value, child.start) == 1f
      }
      operation is RcPaddingModifier -> padded = true
      operation is RcWidthModifier || operation is RcHeightModifier -> Unit
      // A child that may be GONE measures 0 × 0, so it can't be counted on to fill.
      operation is RcVisibilityModifier -> return false
      !operation.keepsSize() -> return false
    }
  }
  if (sized) return fill
  val layout = node.operation
  if (padded || !(layout is RcBoxLayout || layout is RcStateLayout || layout is RcCanvasLayout))
    return false
  val children = layoutChildren(node)
  return children.isNotEmpty() && children.all { fills(it, width, constant) }
}

/** A size from a size modifier of [type] with [value], in a parent offering [available]. */
private fun dimension(type: Int, value: Float?, available: Float?): Float? =
  when (type) {
    RcDimensionType.FILL -> if (value == null || available == null) null else available * value
    // An exact size is clamped to the parent's constraints, which this doesn't model: left
    // measured.
    else -> null
  }

/**
 * Whether a state layout's modifiers are, apart from the size and padding ones the player drops for
 * it, only ones that keep a size: it still applies any other (a `widthIn`, a constraint).
 */
private fun onlyDimensionsAside(node: RcOperationNode): Boolean {
  for (child in node.children) {
    val operation = child.operation
    if (operation is RcLayoutContent || operation is RcCanvasContent) break
    val allowed =
      operation is RcWidthModifier ||
        operation is RcHeightModifier ||
        operation is RcPaddingModifier ||
        operation is RcVisibilityModifier ||
        operation.keepsSize()
    if (!allowed) return false
  }
  return true
}

/**
 * Whether [node]'s visibility can't take its size away: it has no visibility modifier, or each one
 * reads a constant the player shows as visible or invisible, never GONE (which measures 0 × 0).
 */
private fun keepsItsSize(node: RcOperationNode, values: RcValueAnalysis): Boolean {
  for (child in node.children) {
    val operation = child.operation
    if (operation is RcLayoutContent || operation is RcCanvasContent) break
    if (operation is RcVisibilityModifier) {
      val value = values.integer(operation.visibilityId, child.start) ?: return false
      if (visibility(value) == GONE) return false
    }
  }
  return true
}

private const val GONE = 0

/**
 * AndroidX's visibility from its integer, as the player reads it: 1 visible, 2 invisible, 0 gone.
 */
private fun visibility(value: Int): Int =
  when {
    value and 32 == 32 -> 1
    value and 16 == 16 -> 0
    value and 64 == 64 -> 2
    value == 1 -> 1
    value == 2 -> 2
    else -> 0
  }

/** Whether this modifier (or value) leaves its component's size as the size modifiers make it. */
private fun RcOperation.keepsSize(): Boolean =
  this is RcBackgroundModifier ||
    this is RcBorderModifier ||
    this is RcRoundedClipRectModifier ||
    this === RcClipRectModifier ||
    this is RcOffsetModifier ||
    this is RcZIndexModifier ||
    this is RcGraphicsLayerModifier ||
    this === RcClickModifier ||
    this is RcMultiClickModifier ||
    this is RcAnimationSpec ||
    this is RcComponentValue ||
    opcode == RcOpcodes.MODIFIER_DRAW_CONTENT ||
    isPlainValue()

/**
 * A layout node's child components: those inside its `LayoutComponentContent` (or a canvas's
 * content), or directly inside it, as the player lays them out.
 */
private fun layoutChildren(node: RcOperationNode): List<RcOperationNode> {
  val content =
    node.children.firstOrNull {
      it.isContainer && (it.operation is RcLayoutContent || it.operation is RcCanvasContent)
    } ?: node
  return content.children.filter {
    it.isContainer &&
      it.operation.isLayoutComponent() &&
      it.operation !is RcLayoutContent &&
      it.operation !is RcCanvasContent
  }
}

package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode

/**
 * The components of [layout] that [RcFlatContainer] can lay out and draw itself, with no composable
 * of their own: boxes, rows, columns, layout content and core text whose modifiers are only sizes,
 * padding, backgrounds, borders, rounded clips and visibility, with no click, touch, scroll,
 * marquee, layout computation, accessibility semantics or animation spec, and whose geometry no
 * `ComponentValue` reads. Everything else stays a component of its own.
 *
 * A visibility is read as the flat layout measures, which it only does while the document has
 * deferred the lookahead pass: a host value that could change it switches the pass on first, and
 * the tree is composed again as components, whose visibility animates. Layout content keeps its
 * visibility as a component of its own, since the flat layout splices its children into the parent.
 * Decided once per template, off the main thread, with the prepared document; a document with touch
 * expressions, a state layout or component captures has none.
 */
internal fun rcPlainComponents(
  layout: RcLayoutNode.Root?,
  document: RcDocument,
  hasTouchExpressions: Boolean,
  capturesComponents: Boolean,
): Set<Int> {
  if (layout == null || hasTouchExpressions || capturesComponents) return emptySet()
  if (document.operations.any { it is RcStateLayout }) return emptySet()
  val measured =
    document.operations.filterIsInstance<RcComponentValue>().mapTo(HashSet()) { it.componentId }
  val plain = HashSet<Int>()
  fun visit(node: RcLayoutNode) {
    if (
      node.isPlainKind() &&
        node.hasPlainModifiers() &&
        node.geometryComponentIds().none { it in measured }
    ) {
      plain += node.componentId
    }
    node.layoutChildren().forEach(::visit)
  }
  visit(layout)
  return plain
}

private fun RcLayoutNode.isPlainKind(): Boolean =
  when (this) {
    is RcLayoutNode.Box -> !drawsCanvasOperations(canvasOperations)
    is RcLayoutNode.Row -> !drawsCanvasOperations(canvasOperations)
    is RcLayoutNode.Column -> !drawsCanvasOperations(canvasOperations)
    is RcLayoutNode.Content,
    is RcLayoutNode.CoreText -> true
    else -> false
  }

private val PLAIN_DIMENSIONS =
  setOf(
    RcDimensionType.EXACT,
    RcDimensionType.EXACT_DP,
    RcDimensionType.FILL,
    RcDimensionType.FILL_PARENT_MAX_WIDTH,
    RcDimensionType.FILL_PARENT_MAX_HEIGHT,
    RcDimensionType.WRAP,
    RcDimensionType.WEIGHT,
  )

private fun RcLayoutNode.hasPlainModifiers(): Boolean =
  with(modifiers) {
    (visibility == null || this@hasPlainModifiers !is RcLayoutNode.Content) &&
      clicks.isEmpty() &&
      touchActions.isEmpty() &&
      scroll == null &&
      marquee == null &&
      layoutComputes.isEmpty() &&
      accessibility.isEmpty() &&
      animationSpec == null &&
      collapsiblePriority == null &&
      alignBy == null &&
      graphicsLayer == null &&
      dimensionConstraints.isEmpty() &&
      placementModifiers.isEmpty() &&
      ordered.all { operation ->
        when (operation) {
          is RcWidthModifier -> operation.type in PLAIN_DIMENSIONS
          is RcHeightModifier -> operation.type in PLAIN_DIMENSIONS
          is RcPaddingModifier,
          is RcBackgroundModifier,
          is RcBorderModifier,
          is RcRoundedClipRectModifier,
          is RcVisibilityModifier -> true
          else -> false
        }
      }
  }

internal fun RcLayoutNode.layoutChildren(): List<RcLayoutNode> =
  when (this) {
    is RcLayoutNode.Root -> children
    is RcLayoutNode.Content -> children
    is RcLayoutNode.Box -> listOfNotNull(content)
    is RcLayoutNode.Row -> listOf(content)
    is RcLayoutNode.Column -> listOf(content)
    is RcLayoutNode.Flow -> listOf(content)
    is RcLayoutNode.State -> listOf(content)
    is RcLayoutNode.CollapsibleRow -> listOf(content)
    is RcLayoutNode.CollapsibleColumn -> listOf(content)
    is RcLayoutNode.FitBox -> listOf(content)
    else -> emptyList()
  }

/**
 * Whether a container's canvas operations draw anything: they repeat its own modifiers and end with
 * a marker in documents the creation library writes, and only anything beyond those draws.
 */
internal fun RcLayoutNode.drawsCanvasOperations(operations: List<RcLinkedNode>?): Boolean =
  operations?.any { child ->
    val operation = (child as? RcLinkedNode.Operation)?.operation ?: return@any true
    operation !is RcNoArg && operation !in modifiers.ordered
  } == true

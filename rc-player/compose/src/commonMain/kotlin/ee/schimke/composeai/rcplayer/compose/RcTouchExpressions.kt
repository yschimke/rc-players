package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import ee.schimke.composeai.rcplayer.runtime.RcTouchExpressionRuntime

internal val LocalRcTouchExpressions = compositionLocalOf {
  emptyMap<Int?, List<RcTouchExpression>>()
}

/** Bind expressions to their nearest real component; scroll expressions have their own handler. */
internal fun rcTouchExpressions(nodes: List<RcLinkedNode>): Map<Int?, List<RcTouchExpression>> {
  val result = mutableMapOf<Int?, MutableList<RcTouchExpression>>()
  fun visit(nodes: List<RcLinkedNode>, owner: Int?) {
    nodes.forEach { node ->
      when (node) {
        is RcLinkedNode.Operation ->
          (node.operation as? RcTouchExpression)?.let {
            result.getOrPut(owner) { mutableListOf() }.add(it)
          }
        is RcLinkedNode.Container -> {
          val operation = node.operation
          if (operation is RcScrollModifier) return@forEach
          val component =
            when (operation) {
              is RcRootLayout -> operation.componentId
              is RcCanvasLayout -> operation.componentId
              is RcBoxLayout -> operation.componentId
              is RcRowLayout -> operation.componentId
              is RcColumnLayout -> operation.componentId
              is RcFlowLayout -> operation.componentId
              is RcStateLayout -> operation.componentId
              is RcCollapsibleRowLayout -> operation.componentId
              is RcCollapsibleColumnLayout -> operation.componentId
              is RcFitBoxLayout -> operation.componentId
              is RcTextLayout -> operation.componentId
              is RcCoreText -> operation.componentId
              is RcImageLayout -> operation.componentId
              is RcCustomLayout -> operation.componentId
              else -> owner
            }
          visit(node.children, component)
        }
      }
    }
  }
  visit(nodes, null)
  return result
}

internal fun Modifier.rcTouchExpressions(
  expressions: List<RcTouchExpression>,
  state: RcPlayerState,
  invalidate: () -> Unit,
  root: Boolean = false,
): Modifier =
  if (expressions.isEmpty()) this
  else then(RcTouchExpressionElement(expressions, state, invalidate, root))

private data class RcTouchExpressionElement(
  val expressions: List<RcTouchExpression>,
  val state: RcPlayerState,
  val invalidate: () -> Unit,
  val root: Boolean,
) : ModifierNodeElement<RcTouchExpressionNode>() {
  override fun create() = RcTouchExpressionNode(expressions, state, invalidate, root)

  override fun update(node: RcTouchExpressionNode) {
    node.reset(expressions, state, invalidate, root)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "rcTouchExpressions"
  }
}

private class RcTouchExpressionNode(
  private var expressions: List<RcTouchExpression>,
  private var state: RcPlayerState,
  private var invalidate: () -> Unit,
  private var root: Boolean,
) : Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
  private var pointer: PointerId? = null
  private var runtimes = expressions.map { RcTouchExpressionRuntime(it, state::floatValues) }
  private var last = Offset.Zero
  private var down = Offset.Zero
  private var rawDown = Offset.Zero
  private val velocity = VelocityTracker()
  private var dragging = false
  private val readsX
    get() = expressions.any { expression ->
      expression.expression.any { it.referencedId == RcTouchExpressionRuntime.ID_TOUCH_POS_X }
    }

  private val readsY
    get() = expressions.any { expression ->
      expression.expression.any { it.referencedId == RcTouchExpressionRuntime.ID_TOUCH_POS_Y }
    }

  fun reset(
    expressions: List<RcTouchExpression>,
    state: RcPlayerState,
    invalidate: () -> Unit,
    root: Boolean,
  ) {
    if (this.expressions != expressions || this.state !== state) {
      finish()
      this.expressions = expressions
      this.state = state
      runtimes = expressions.map { RcTouchExpressionRuntime(it, state::floatValues) }
    }
    this.invalidate = invalidate
    this.root = root
  }

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    val change =
      pointerEvent.changes.firstOrNull { it.id == pointer }
        ?: pointerEvent.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
        ?: return
    val position =
      if (!root) change.position
      else {
        val transform =
          computeRootTransform(
            state.document.header.width.coerceAtLeast(1).toFloat(),
            state.document.header.height.coerceAtLeast(1).toFloat(),
            bounds.width.toFloat(),
            bounds.height.toFloat(),
            state.rootContentBehavior,
          )
        Offset(
          (change.position.x - transform.translateX) / transform.scaleX,
          (change.position.y - transform.translateY) / transform.scaleY,
        )
      }
    if (
      pass == PointerEventPass.Initial && pointer == null && change.changedToDownIgnoreConsumed()
    ) {
      if (
        change.position.x !in 0f..bounds.width.toFloat() ||
          change.position.y !in 0f..bounds.height.toFloat()
      )
        return
      pointer = change.id
      last = position
      down = position
      rawDown = change.position
      dragging = false
      velocity.resetTracking()
      velocity.addPosition(change.uptimeMillis, position)
      if (root) state.publishTouchPosition(position.x, position.y)
      expressions.forEachIndexed { index, expression ->
        runtimes[index].onDown(
          state.resolve(RcFloatWord(0x7fc00000 or expression.id)),
          position.x,
          position.y,
          state::resolve,
        )
      }
    }
    if (pointer != change.id) return
    if (pass == PointerEventPass.Main && change.pressed && position != last && !change.isConsumed) {
      val delta = position - down
      val activeAxis =
        (readsX && kotlin.math.abs(delta.x) >= kotlin.math.abs(delta.y)) ||
          (readsY && kotlin.math.abs(delta.y) >= kotlin.math.abs(delta.x))
      if (
        activeAxis &&
          (dragging ||
            (change.position - rawDown).getDistance() >
              currentValueOf(LocalViewConfiguration).touchSlop)
      ) {
        dragging = true
        if (root) state.publishTouchPosition(position.x, position.y)
        velocity.addPosition(change.uptimeMillis, position)
        val speed = velocity.calculateVelocity()
        expressions.forEachIndexed { index, expression ->
          state.setFloat(
            expression.id,
            runtimes[index].onDrag(position.x, position.y, speed.x, speed.y, state::resolve),
          )
        }
        last = position
        change.consume()
        invalidate()
      }
    }
    if (
      pass == PointerEventPass.Final &&
        (change.changedToUpIgnoreConsumed() ||
          !change.pressed ||
          (change.isConsumed && position != last))
    )
      finish()
  }

  private fun finish() {
    if (pointer == null) return
    pointer = null
    // Cancel releases with zero velocity too: there must be no pressed expression after disposal
    // or after a child/scroller has taken the gesture.
    expressions.forEachIndexed { index, expression ->
      if (
        expression.stopMode in
          setOf(
            RcTouchExpression.STOP_ENDS,
            RcTouchExpression.STOP_NOTCHES_EVEN,
            RcTouchExpression.STOP_NOTCHES_PERCENTS,
            RcTouchExpression.STOP_NOTCHES_ABSOLUTE,
            RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN,
          )
      ) {
        state.setFloat(
          expression.id,
          runtimes[index].stopTarget(
            state.resolve(RcFloatWord(0x7fc00000 or expression.id)),
            state.resolve(expression.min),
            state.resolve(expression.max),
            state::resolve,
          ),
        )
        invalidate()
      }
    }
  }

  override fun onCancelPointerInput() {
    finish()
  }

  override fun onDetach() {
    finish()
  }
}

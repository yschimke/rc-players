package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * How many throwaway passes a first layout may take before the kept one. AndroidX re-measures until
 * `ComponentValue` stops moving; a document whose sizes feed each other deeper than this still
 * converges, one frame per remaining step, through `onGloballyPositioned`.
 */
private const val MAX_SETTLE_PASSES = 3

/** Collects whether a settling pass moved any `ComponentValue`. */
internal class RcGeometryProbe {
  var changed: Boolean = false
    private set

  fun onPublished(changed: Boolean) {
    if (changed) this.changed = true
  }
}

/** What one player remembers between measures about its settling. */
internal class RcFirstLayoutSettle {
  /** Whether the kept tree has had its first layout. */
  var settled: Boolean = false
  /**
   * Written after a pass that composed probes, so the next measure runs without them and the
   * subcomposition disposes them rather than keeping a second tree alive until something else
   * happens to relayout the player.
   */
  val probeGeneration = mutableIntStateOf(0)
  /** The last size the kept tree measured, which is all a host asking for intrinsics can get. */
  var lastSize: IntSize = IntSize.Zero
}

private sealed interface RcSettleSlot {
  data class Probe(val pass: Int) : RcSettleSlot

  data object Content : RcSettleSlot
}

/**
 * Lays [tree] out with its `ComponentValue`s already settled, on the first frame.
 *
 * A document can read its own layout back: `remote-m3`'s edge button caps its label's width with an
 * expression over the button's measured `ComponentValue` width. The player publishes geometry once
 * a component has been measured, but composition resolves every modifier *before* anything is
 * measured, so the first frame laid the label out against a width of 0. The label then had nothing
 * to draw into, and the frame after that animated it open from zero over the default 300 ms bounds
 * spec — a visible reveal AndroidX never shows, because it measures again until `ComponentValue`
 * stops moving before it draws anything.
 *
 * This does the same within one Compose frame. The kept tree is subcomposed, so it composes during
 * measure; before it, up to [MAX_SETTLE_PASSES] throwaway copies are composed and measured with a
 * [RcGeometryProbe], publishing every tracked component's size as it is measured — and refreshing
 * the expressions over it — until a pass moves nothing. The kept tree then composes against the
 * settled values and is never laid out at the unsettled ones, so nothing animates from them.
 *
 * Settling runs once per document, for its first layout. Everything after that — a resize, or
 * geometry the document moves itself with an action or an animation — takes the existing path:
 * publish after placement, recompose next frame. Settling on every constraint change would compose
 * the whole tree up to three extra times per frame for a host that animates the player's size.
 * Position is published only after placement, so a document that reads a component's *position*
 * still takes a frame for it.
 */
@Composable
internal fun RcSettledLayout(
  settle: RcFirstLayoutSettle,
  onSettled: () -> Unit,
  tree: @Composable (probe: RcGeometryProbe?) -> Unit,
) {
  SubcomposeLayout(remember(settle) { RcIntrinsicsFromLastMeasureElement(settle) }) { constraints ->
    // Read so a pass that composed probes can schedule the measure that disposes them.
    settle.probeGeneration.intValue
    var probed = false
    if (!settle.settled) {
      var changed = false
      for (pass in 0 until MAX_SETTLE_PASSES) {
        val probe = RcGeometryProbe()
        subcompose(RcSettleSlot.Probe(pass)) { tree(probe) }.forEach { it.measure(constraints) }
        probed = true
        if (!probe.changed) break
        changed = true
      }
      // Text a document derives from a component's size was computed before the probes moved
      // it; the kept tree composes next, and has to read the settled text.
      if (changed) onSettled()
      settle.settled = true
    }
    val placeables = subcompose(RcSettleSlot.Content) { tree(null) }.map { it.measure(constraints) }
    val width =
      constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: constraints.minWidth)
    val height =
      constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: constraints.minHeight)
    settle.lastSize = IntSize(width, height)
    layout(width, height) {
      placeables.forEach { it.place(0, 0) }
      if (probed) settle.probeGeneration.intValue += 1
    }
  }
}

/**
 * `SubcomposeLayout` cannot answer intrinsic measurements, and throws when asked. The player could
 * before this wrapper existed, so a host measuring it inside `IntrinsicSize` would now crash; this
 * answers with the size the kept tree last measured instead — the only size there is to report
 * without composing the document again.
 */
private data class RcIntrinsicsFromLastMeasureElement(val settle: RcFirstLayoutSettle) :
  ModifierNodeElement<RcIntrinsicsFromLastMeasureNode>() {
  override fun create() = RcIntrinsicsFromLastMeasureNode(settle)

  override fun update(node: RcIntrinsicsFromLastMeasureNode) {
    node.settle = settle
  }
}

private class RcIntrinsicsFromLastMeasureNode(var settle: RcFirstLayoutSettle) :
  Modifier.Node(), LayoutModifierNode {
  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
  }

  override fun IntrinsicMeasureScope.minIntrinsicWidth(
    measurable: IntrinsicMeasurable,
    height: Int,
  ): Int = settle.lastSize.width

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(
    measurable: IntrinsicMeasurable,
    height: Int,
  ): Int = settle.lastSize.width

  override fun IntrinsicMeasureScope.minIntrinsicHeight(
    measurable: IntrinsicMeasurable,
    width: Int,
  ): Int = settle.lastSize.height

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(
    measurable: IntrinsicMeasurable,
    width: Int,
  ): Int = settle.lastSize.height
}

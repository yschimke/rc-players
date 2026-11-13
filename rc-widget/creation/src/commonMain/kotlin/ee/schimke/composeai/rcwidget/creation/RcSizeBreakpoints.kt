@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteStateLayout
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.animationSpec
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteState
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf

/**
 * The size a launcher widget is captured for, in dp; see
 * [ee.schimke.composeai.rcwidget.RcWidgetCaptureSize], which this names so that the same slot can
 * be captured through either creation binding (this one, or `rc-widget-creation-androidx`'s).
 */
public typealias RcWidgetCaptureSize = ee.schimke.composeai.rcwidget.RcWidgetCaptureSize

/** The launcher capture size, or null outside a launcher capture. */
public val LocalRcWidgetCaptureSize:
  androidx.compose.runtime.ProvidableCompositionLocal<RcWidgetCaptureSize?> =
  staticCompositionLocalOf {
    null
  }

/** [content] captured for a launcher widget of [size]; see [RcWidgetCaptureSize]. */
@Composable
public fun ProvideRcWidgetCaptureSize(size: RcWidgetCaptureSize?, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalRcWidgetCaptureSize provides size, content = content)
}

/**
 * Runs [content] with the launcher capture size narrowed to one child's share of it, for a
 * container that splits its room into [columns] × [rows] equal parts with [gapDp] between them (a
 * grid's cells, a row's columns), so a nested breakpoint picks its layout for the room it will
 * actually be played in. No-op outside a launcher capture.
 */
@Composable
public fun ProvideRcChildCaptureShare(
  columns: Int,
  rows: Int = 1,
  gapDp: Int,
  content: @Composable () -> Unit,
) {
  val size = LocalRcWidgetCaptureSize.current
  if (size == null || columns < 1 || rows < 1) {
    content()
    return
  }
  fun share(total: Int, parts: Int): Int = ((total - gapDp * (parts - 1)) / parts).coerceAtLeast(0)
  ProvideRcWidgetCaptureSize(
    RcWidgetCaptureSize(share(size.widthDp, columns), share(size.heightDp, rows)),
    content,
  )
}

/** Which runtime dimension a breakpoint gates on. */
public enum class RcBreakpointAxis {
  Width,
  Height,
}

/**
 * A state-layout swap with no animation: the chosen branch shows at once, and a player that draws
 * every recorded branch keeps the inactive one hidden instead of cross-fading through both.
 */
public fun RemoteModifier.immediateSwap(): RemoteModifier =
  animationSpec(
    animationId = -1,
    motionDuration = 0f,
    motionEasingType = 1,
    visibilityDuration = 0f,
    visibilityEasingType = 1,
    enabled = true,
  )

/**
 * Switches between layouts by the live size of the component, so one document fits every size a
 * host gives it: a resizable launcher widget, a Wear widget's Small and Large containers, a
 * dashboard cell.
 *
 * [thresholdsDp] is an ascending list of sizes in dp along [axis]; [content] is captured once per
 * tier, `0..thresholdsDp.size`, and the player picks one from the component's width or height
 * compared against each threshold in pixels at playback density. The ladder is a chain of
 * `RemoteStateLayout(RemoteBoolean)`s, the overload every player evaluates.
 *
 * Inside a launcher capture ([LocalRcWidgetCaptureSize]) only the matching tier is captured.
 *
 * Gate on the axis the host pins: launchers pin width and let height wrap; a Wear slot pins both.
 * Pass [modifier] `fillMaxWidth()` / `fillMaxSize()` so the playback size is the host's.
 */
@Composable
public fun RcSizeBreakpoint(
  thresholdsDp: IntArray,
  modifier: RemoteModifier = RemoteModifier,
  axis: RcBreakpointAxis = RcBreakpointAxis.Width,
  content: @Composable (tier: Int) -> Unit,
) {
  require(thresholdsDp.isNotEmpty()) { "RcSizeBreakpoint needs at least one threshold" }
  require((0 until thresholdsDp.size - 1).all { thresholdsDp[it] < thresholdsDp[it + 1] }) {
    "thresholdsDp must be strictly ascending: ${thresholdsDp.toList()}"
  }

  val widgetSize = LocalRcWidgetCaptureSize.current
  if (widgetSize != null) {
    val dimension =
      when (axis) {
        RcBreakpointAxis.Width -> widgetSize.widthDp
        RcBreakpointAxis.Height -> widgetSize.heightDp
      }
    RemoteBox(modifier = modifier) { content(thresholdsDp.count { dimension >= it }) }
    return
  }

  val name = rememberRcExpressionName(BREAKPOINT_PREFIX)
  RemoteBox(modifier = modifier) {
    val dimension =
      rememberNamedComponentFloat(name, RemoteState.Domain.User) {
        when (axis) {
          RcBreakpointAxis.Width -> width
          RcBreakpointAxis.Height -> height
        }
      }
    BreakpointTier(dimension, thresholdsDp, 0, content)
  }
}

@Composable
private fun BreakpointTier(
  dimension: RemoteFloat,
  thresholdsDp: IntArray,
  baseTier: Int,
  content: @Composable (tier: Int) -> Unit,
) {
  if (thresholdsDp.isEmpty()) {
    content(baseTier)
    return
  }
  val highest = thresholdsDp.size - 1
  val atOrAbove = dimension.isGreaterThanOrEqualTo(thresholdsDp[highest].rdp.toPx())
  RemoteStateLayout(atOrAbove) { above ->
    RemoteBox(modifier = RemoteModifier.immediateSwap()) {
      if (above) content(baseTier + thresholdsDp.size)
      else BreakpointTier(dimension, thresholdsDp.copyOfRange(0, highest), baseTier, content)
    }
  }
}

/**
 * A two-dimensional gate: [content] is captured with `roomy = true` for a component at least
 * [minWidthDp] wide **and** [minHeightDp] tall, `false` otherwise, and the player picks one from
 * the live size. The test is one float expression, `min(w, h · minW/minH) ≥ minW`, so it is one
 * boolean every player evaluates rather than two joined ones.
 *
 * Inside a launcher capture only the matching variant is captured.
 */
@Composable
public fun RcMinSizeGate(
  minWidthDp: Int,
  minHeightDp: Int,
  modifier: RemoteModifier = RemoteModifier,
  content: @Composable (roomy: Boolean) -> Unit,
) {
  require(minWidthDp > 0 && minHeightDp > 0) { "RcMinSizeGate needs positive minimums" }
  val widgetSize = LocalRcWidgetCaptureSize.current
  if (widgetSize != null) {
    RemoteBox(modifier = modifier) {
      content(widgetSize.widthDp >= minWidthDp && widgetSize.heightDp >= minHeightDp)
    }
    return
  }
  val ratio = minWidthDp.toFloat() / minHeightDp.toFloat()
  val name = rememberRcExpressionName(MIN_SIZE_PREFIX)
  RemoteBox(modifier = modifier) {
    val gate =
      rememberNamedComponentFloat(name, RemoteState.Domain.User) { width.min(height * ratio.rf) }
    val roomy = gate.isGreaterThanOrEqualTo(minWidthDp.rdp.toPx())
    RemoteStateLayout(roomy) { isRoomy -> content(isRoomy) }
  }
}

private const val BREAKPOINT_PREFIX = "__rc_breakpoint_"
private const val MIN_SIZE_PREFIX = "__rc_min_size_gate_"

/** The width and height of the layout component a size expression is read in, in pixels. */
internal class RcComponentSize(val width: RemoteFloat, val height: RemoteFloat)

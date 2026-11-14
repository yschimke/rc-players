package ee.schimke.composeai.rcplayer.compose

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.DeferredTargetAnimation
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ApproachLayoutModifierNode
import androidx.compose.ui.layout.ApproachMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.runtime.RcAnimationTimeline
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

/**
 * The layout animations a player has running: its components' bounds animations and visibility
 * transitions, each as a source that says whether it is still moving. A player that switched the
 * lookahead pass on for a host change keeps it on until every one is idle (see
 * `RcComposePlayerResolved`): what they actually do, whatever their specs, sequencing or the
 * platform's animation duration scale make of them.
 */
internal class RcLayoutAnimationActivity {
  private val sources = ArrayList<() -> Boolean>()

  fun add(running: () -> Boolean) {
    sources += running
  }

  fun remove(running: () -> Boolean) {
    sources -= running
  }

  /** Whether nothing is moving. */
  val isIdle: Boolean
    get() = sources.none { it() }
}

internal val LocalRcLayoutAnimationActivity =
  staticCompositionLocalOf<RcLayoutAnimationActivity?> { null }

/**
 * Tells the player's [RcLayoutAnimationActivity] whether this animation is [running], for as long
 * as it is composed: what a composable animation (a visibility transition, an alternative's
 * cross-fade, a layer's tween) does to keep the lookahead pass on until it ends.
 */
@Composable
internal fun RegisterLayoutAnimation(key: Any, running: () -> Boolean) {
  val activity = LocalRcLayoutAnimationActivity.current ?: return
  DisposableEffect(activity, key) {
    activity.add(running)
    onDispose { activity.remove(running) }
  }
}

/** Animates measured x/y/width/height toward the Lookahead result like AndroidX AnimateMeasure. */
internal fun Modifier.animateRcBounds(
  lookaheadScope: LookaheadScope,
  spec: RcAnimationSpec,
): Modifier = if (!spec.isEnabled) this else this.then(RcAnimateBoundsElement(lookaheadScope, spec))

private data class RcAnimateBoundsElement(
  val lookaheadScope: LookaheadScope,
  val spec: RcAnimationSpec,
) : ModifierNodeElement<RcAnimateBoundsNode>() {
  override fun create(): RcAnimateBoundsNode = RcAnimateBoundsNode(lookaheadScope, spec)

  override fun update(node: RcAnimateBoundsNode) {
    node.lookaheadScope = lookaheadScope
    node.spec = spec
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "animateRcBounds"
    properties["animationId"] = spec.animationId
  }
}

private class RcAnimateBoundsNode(var lookaheadScope: LookaheadScope, var spec: RcAnimationSpec) :
  ApproachLayoutModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {
  private val sizeAnimation = RcBoundsTarget(IntSize.VectorConverter)
  private val offsetAnimation = RcBoundsTarget(IntOffset.VectorConverter)
  private val running: () -> Boolean = { !sizeAnimation.isIdle || !offsetAnimation.isIdle }
  private var activity: RcLayoutAnimationActivity? = null

  override fun onAttach() {
    activity = currentValueOf(LocalRcLayoutAnimationActivity)?.also { it.add(running) }
  }

  override fun onDetach() {
    activity?.remove(running)
    activity = null
  }

  // Held, so a frame's approach doesn't allocate a function reference per component.
  private val sizeSpecOf: () -> FiniteAnimationSpec<IntSize> = { sizeSpec() }
  private val offsetSpecOf: () -> FiniteAnimationSpec<IntOffset> = { offsetSpec() }

  private fun updateSize(target: IntSize): IntSize =
    sizeAnimation.update(target, coroutineScope, sizeSpecOf)

  private fun updateOffset(target: IntOffset): IntOffset =
    offsetAnimation.update(target, coroutineScope, offsetSpecOf)

  override fun isMeasurementApproachInProgress(lookaheadSize: IntSize): Boolean {
    updateSize(lookaheadSize)
    return !sizeAnimation.isIdle
  }

  override fun Placeable.PlacementScope.isPlacementApproachInProgress(
    lookaheadCoordinates: LayoutCoordinates,
  ): Boolean {
    updateOffset(targetInParent(lookaheadCoordinates))
    return !offsetAnimation.isIdle
  }

  /**
   * Where the lookahead pass puts this component within its parent.
   *
   * Within the parent, not the scope: AndroidX animates a component's own x/y, which a parent's
   * move or scroll does not change. Measured across the scope instead, scrolling a column read as
   * every child moving, and each eased back toward where it had been.
   */
  private fun Placeable.PlacementScope.targetInParent(coordinates: LayoutCoordinates): IntOffset =
    with(lookaheadScope) {
      val parent = coordinates.parentLayoutCoordinates ?: lookaheadScopeCoordinates
      parent.localLookaheadPositionOf(coordinates).round()
    }

  override fun ApproachMeasureScope.approachMeasure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val animatedSize = updateSize(lookaheadSize)
    val placeable = measurable.measure(Constraints.fixed(animatedSize.width, animatedSize.height))
    return layout(placeable.width, placeable.height) {
      val coordinates = coordinates
      if (coordinates == null) {
        placeable.place(0, 0)
      } else {
        val animatedOffset = updateOffset(targetInParent(coordinates))
        val parent = coordinates.parentLayoutCoordinates
        val placementOffset =
          if (parent != null) parent.localPositionOf(coordinates, Offset.Zero).round()
          else
            with(lookaheadScope) {
              lookaheadScopeCoordinates.localPositionOf(coordinates, Offset.Zero).round()
            }
        val delta = animatedOffset - placementOffset
        placeable.place(delta.x, delta.y)
      }
    }
  }

  // Built once per spec rather than on every measure and placement of every animating node.
  private var cachedFor: RcAnimationSpec? = null
  private var cachedSize: FiniteAnimationSpec<IntSize>? = null
  private var cachedOffset: FiniteAnimationSpec<IntOffset>? = null

  private fun ensureSpecs() {
    if (cachedFor == spec) return
    val duration = spec.rcMotionDurationMillis()
    val easing = spec.rcMotionEasing()
    cachedSize = tween(duration, easing = easing)
    cachedOffset = tween(duration, easing = easing)
    cachedFor = spec
  }

  private fun sizeSpec(): FiniteAnimationSpec<IntSize> {
    ensureSpecs()
    return cachedSize!!
  }

  private fun offsetSpec(): FiniteAnimationSpec<IntOffset> {
    ensureSpecs()
    return cachedOffset!!
  }
}

/**
 * One of a component's bounds — its size or its offset — animating toward the lookahead's target
 * like AndroidX AnimateMeasure, with the animation made only once the target first changes.
 *
 * A component's first layout has nothing to animate from: AndroidX places it at its target. A
 * [DeferredTargetAnimation] made up front still writes its pending target and launches a coroutine
 * for that first target, twice per component (size and offset), so every card scrolling on screen
 * paid a state write and a launch per component on the frame it appeared. Most components never
 * move after that, and now never make an animation at all.
 *
 * A settled component is also asked about its approach on every frame its host moves it, and
 * `updateTarget` writes and launches on each call, so an idle animation handed the target it
 * already holds is skipped too.
 */
private class RcBoundsTarget<T, V : AnimationVector>(private val converter: TwoWayConverter<T, V>) {
  /** The target last handed over; null before the first layout. */
  private var target: T? = null
  private var animation: DeferredTargetAnimation<T, V>? = null

  val isIdle: Boolean
    get() = animation?.isIdle ?: true

  fun update(next: T, scope: CoroutineScope, spec: () -> FiniteAnimationSpec<T>): T {
    val current = target ?: return next.also { target = it }
    if (next == current && isIdle) return next
    // The first change: the animation starts settled where the component was.
    val animation =
      animation
        ?: DeferredTargetAnimation(converter).also {
          it.updateTarget(current, scope, spec())
          animation = it
        }
    target = next
    return animation.updateTarget(next, scope, spec())
  }
}

/** The spec's motion duration in whole milliseconds, or 0 when it is unset or not a finite time. */
internal fun RcAnimationSpec.rcMotionDurationMillis(): Int =
  motionDurationMillis.value.takeIf { it.isFinite() && it > 0f }?.roundToInt() ?: 0

/**
 * The spec's motion curve as a Compose [Easing], by sampling the same [RcAnimationTimeline] the
 * player evaluates every other animated value with — so a bounds animation, a shared-element morph
 * and a document-driven float all follow one curve.
 */
internal fun RcAnimationSpec.rcMotionEasing(): Easing {
  val duration = rcMotionDurationMillis().toFloat()
  val timeline = RcAnimationTimeline(this)
  return Easing { fraction -> timeline.progress(fraction * duration).motion }
}

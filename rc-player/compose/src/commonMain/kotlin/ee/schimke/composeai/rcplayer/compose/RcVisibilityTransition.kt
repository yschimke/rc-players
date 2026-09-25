package ee.schimke.composeai.rcplayer.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.runtime.RcAnimationTimeline
import kotlin.math.roundToInt

/**
 * A component's visibility, animated by Compose's own [AnimatedVisibility].
 *
 * The document's `AnimationSpec` picks the enter and exit transitions and their duration and
 * easing, mapped onto Compose's: fades onto [fadeIn] / [fadeOut], slides onto the slide
 * transitions, and `ROTATE` onto a fade and scale plus a rotation driven by the same transition.
 * AndroidX hand-animates these. Compose's versions differ in small ways: a slide travels the
 * component's own size rather than its parent's, and the component leaves the layout when its exit
 * ends. The player takes Compose's behaviour over an exact copy.
 *
 * [visibility] is AndroidX's: 0 gone, 1 visible, 2 invisible (laid out, not painted). A component
 * that is gone and has finished leaving renders [gone], which still reports it to the inspection
 * seam at zero size. [modifier] is the parent's modifier, parent data included, and goes on the
 * [AnimatedVisibility] node the parent measures; [content] gets the modifier for the component
 * itself.
 */
@Composable
internal fun RcVisibilityTransition(
  visibility: Int,
  spec: RcAnimationSpec?,
  enabled: Boolean,
  modifier: Modifier,
  gone: @Composable () -> Unit,
  content: @Composable (Modifier) -> Unit,
) {
  val visibleState = remember { MutableTransitionState(visibility != 0) }
  visibleState.targetState = visibility != 0
  // What the component was last shown as: an invisible component that turns GONE exits unpainted,
  // rather than being painted for the length of its exit.
  val shown = remember { RcShownVisibility(visibility) }
  if (visibility != 0) shown.value = visibility
  if (!visibleState.currentState && !visibleState.targetState) {
    gone()
    return
  }
  val resolved = spec ?: DefaultRcAnimationSpec
  val durationMillis =
    resolved.visibilityDurationMillis.value.takeIf { it.isFinite() && it > 0f }?.roundToInt() ?: 0
  val animate = enabled && resolved.isEnabled && durationMillis > 0
  val easing = remember(resolved) { resolved.rcVisibilityEasing() }
  AnimatedVisibility(
    visibleState = visibleState,
    modifier = modifier,
    enter =
      if (animate) resolved.rcEnterTransition(durationMillis, easing) else EnterTransition.None,
    exit = if (animate) resolved.rcExitTransition(durationMillis, easing) else ExitTransition.None,
  ) {
    val rotation =
      if (animate && resolved.enterAnimation.androidXValue == RcLayoutAnimation.Rotate.wireValue) {
        rcEnterRotation(durationMillis, easing)
      } else Modifier
    content(if (shown.value == 2) rotation.alpha(0f) else rotation)
  }
}

/** Plain holder, not state: it is written and read in the same composition. */
private class RcShownVisibility(var value: Int)

/**
 * The spec's visibility curve as a Compose [Easing], sampled from the same [RcAnimationTimeline]
 * the player evaluates every other animated value with.
 */
private fun RcAnimationSpec.rcVisibilityEasing(): Easing {
  val duration =
    visibilityDurationMillis.value.takeIf { it.isFinite() && it > 0f } ?: return Easing { it }
  val timeline = RcAnimationTimeline(this)
  return Easing { fraction -> timeline.progress(fraction * duration).visibility }
}

/** Holds the content unpainted until the transition ends: AndroidX's non-painting branches. */
private val Hidden = Easing { 0f }

/** Hides the content for the whole exit: AndroidX's `ParticleAnimation` branch. */
private val HiddenAtOnce = Easing { 1f }

private fun RcAnimationSpec.rcEnterTransition(
  durationMillis: Int,
  easing: Easing,
): EnterTransition =
  when (enterAnimation.androidXValue) {
    RcLayoutAnimation.FadeIn.wireValue -> fadeIn(tween(durationMillis, easing = easing))
    RcLayoutAnimation.SlideLeft.wireValue ->
      slideInHorizontally(tween(durationMillis, easing = easing)) { it }
    RcLayoutAnimation.SlideRight.wireValue ->
      slideInHorizontally(tween(durationMillis, easing = easing)) { -it }
    RcLayoutAnimation.SlideTop.wireValue ->
      slideInVertically(tween(durationMillis, easing = easing)) { it }
    RcLayoutAnimation.SlideBottom.wireValue ->
      slideInVertically(tween(durationMillis, easing = easing)) { -it }
    RcLayoutAnimation.Rotate.wireValue ->
      fadeIn(tween(durationMillis, easing = easing)) +
        scaleIn(tween(durationMillis, easing = easing), initialScale = 0f)
    // FADE_OUT and PARTICLE as an enter paint nothing until they land.
    else -> fadeIn(tween(durationMillis, easing = Hidden))
  }

private fun RcAnimationSpec.rcExitTransition(durationMillis: Int, easing: Easing): ExitTransition =
  when (exitAnimation.androidXValue) {
    RcLayoutAnimation.FadeOut.wireValue -> fadeOut(tween(durationMillis, easing = easing))
    RcLayoutAnimation.SlideLeft.wireValue ->
      slideOutHorizontally(tween(durationMillis, easing = easing)) { -it }
    RcLayoutAnimation.SlideRight.wireValue ->
      slideOutHorizontally(tween(durationMillis, easing = easing)) { it }
    RcLayoutAnimation.SlideTop.wireValue ->
      slideOutVertically(tween(durationMillis, easing = easing)) { -it }
    RcLayoutAnimation.SlideBottom.wireValue ->
      slideOutVertically(tween(durationMillis, easing = easing)) { it }
    // AndroidX routes PARTICLE, ROTATE, FADE_IN and unknowns to ParticleAnimation, which stops
    // painting the component at once and keeps its place until the exit ends.
    else -> fadeOut(tween(durationMillis, easing = HiddenAtOnce))
  }

/**
 * `ROTATE`'s full turn as it enters, on the enter transition's own clock. Only the enter turns: the
 * exit keeps the finished angle, so whatever exit the spec names runs without a reverse turn.
 */
@Composable
private fun AnimatedVisibilityScope.rcEnterRotation(durationMillis: Int, easing: Easing): Modifier {
  val degrees by
    transition.animateFloat(transitionSpec = { tween(durationMillis, easing = easing) }) {
      if (it == EnterExitState.PreEnter) 0f else 360f
    }
  return Modifier.graphicsLayer { rotationZ = degrees }
}

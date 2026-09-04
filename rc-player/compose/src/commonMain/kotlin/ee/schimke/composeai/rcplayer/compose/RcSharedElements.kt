package ee.schimke.composeai.rcplayer.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec

/**
 * Shared-element transitions between the alternatives of a `StateLayout`.
 *
 * A `StateLayout` shows one child at a time, chosen by an integer state variable. Switching that
 * variable used to be an instantaneous swap here: the outgoing branch stopped being drawn on the
 * frame the index changed and the incoming one appeared in its place, however much the two branches
 * had in common. AndroidX's embedded player fixed that upstream by wrapping the switch in
 * [SharedTransitionLayout] + [AnimatedContent] and attaching `sharedBounds` to every component that
 * carries an animation id, so a component present in both branches *morphs* between its two
 * positions instead of disappearing and reappearing. This is that behaviour, ported.
 *
 * The wire model already carries everything it needs. `animationId` is the identity the creation
 * library assigns a component — the same id on either side of the switch means the same element —
 * and the component's `AnimationSpec` carries the duration and easing curve. The default when a
 * `StateLayout` declares no spec is [DefaultRcAnimationSpec]'s 300ms, which is what upstream
 * defaults to as well.
 *
 * The two scopes are published as composition locals rather than threaded through
 * `RenderLayoutNode`, because the components that take part in a transition are arbitrarily deep in
 * the branch and every node between them is an ordinary layout node. Both are null outside a
 * switcher, and [rcSharedElementModifier] returns null there — a document with no `StateLayout`
 * therefore composes exactly as it did before.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalRcSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The [AnimatedContent] branch currently being composed, or null outside a switcher. */
internal val LocalRcAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Cross-fades between [target]'s alternatives, morphing the shared elements inside them.
 *
 * [content] is composed for the incoming index, and — while the transition runs — for the outgoing
 * one as well, which is what gives `sharedBounds` two sets of bounds to interpolate between.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RcAnimatedAlternatives(
  target: Int,
  spec: RcAnimationSpec,
  alignment: Alignment,
  label: String,
  content: @Composable (Int) -> Unit,
) {
  val duration = spec.rcMotionDurationMillis()
  val easing = spec.rcMotionEasing()
  SharedTransitionLayout {
    AnimatedContent(
      targetState = target,
      contentAlignment = alignment,
      label = label,
      transitionSpec = {
        fadeIn(animationSpec = tween(durationMillis = duration, easing = easing)) togetherWith
          fadeOut(animationSpec = tween(durationMillis = duration, easing = easing))
      },
    ) { index ->
      CompositionLocalProvider(
        LocalRcSharedTransitionScope provides this@SharedTransitionLayout,
        LocalRcAnimatedVisibilityScope provides this@AnimatedContent,
      ) {
        content(index)
      }
    }
  }
}

/**
 * The `sharedBounds` modifier for a component with [animationId], or null when there is nothing to
 * share — no enclosing switcher, or a component the document did not give an animation identity.
 *
 * Returning null rather than [Modifier] is deliberate: the caller uses it to decide whether the
 * node's bounds are already being driven by the shared transition, in which case it must not also
 * apply `animateRcBounds`. Two approach-layout animations racing for the same node is exactly the
 * jitter this is meant to remove.
 *
 * `ResizeMode.RemeasureToBounds` matches upstream: a morphing component is re-measured at each
 * intermediate size rather than drawn scaled, so text inside it stays crisp and re-wraps the way it
 * would at the destination size.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rcSharedElementModifier(animationId: Int?, spec: RcAnimationSpec?): Modifier? {
  val sharedTransitionScope = LocalRcSharedTransitionScope.current ?: return null
  val animatedVisibilityScope = LocalRcAnimatedVisibilityScope.current ?: return null
  // 0 is "no animation" on the wire and -1 is the unset default; neither identifies a component.
  if (animationId == null || animationId == 0 || animationId == -1) return null
  val resolved = spec ?: DefaultRcAnimationSpec
  val duration = resolved.rcMotionDurationMillis()
  val easing = resolved.rcMotionEasing()
  val boundsTransform =
    remember(duration, easing) {
      BoundsTransform { _, _ ->
        if (duration <= 0) snap() else tween(durationMillis = duration, easing = easing)
      }
    }
  with(sharedTransitionScope) {
    return Modifier.sharedBounds(
      sharedContentState = rememberSharedContentState(key = animationId),
      animatedVisibilityScope = animatedVisibilityScope,
      boundsTransform = boundsTransform,
      resizeMode = ResizeMode.RemeasureToBounds,
    )
  }
}

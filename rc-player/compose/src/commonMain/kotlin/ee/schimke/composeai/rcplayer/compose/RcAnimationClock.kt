package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.InfiniteAnimationPolicy

/**
 * The document's animation time, when a host wants to set it rather than let frames advance it.
 *
 * A document's `ANIMATION_TIME` — and everything computed from it: expressions, marquees, particle
 * clocks — normally counts from the document's first frame. A preview, a screenshot test that
 * renders a document "at 2.5 s", or a conformance run replaying a timeline needs that value
 * exactly, which a frame clock stepping in whole frames cannot give. Layout transitions still run
 * on the composition's own frame clock; this sets only the time the document reads.
 */
public fun interface RcAnimationClock {
  /** The animation time the next frame reads, in seconds. */
  public fun seconds(): Float
}

/** Overrides the document's animation time; null — the default — counts it from frames. */
public val LocalRcAnimationClock: ProvidableCompositionLocal<RcAnimationClock?> =
  staticCompositionLocalOf {
    null
  }

/**
 * The [InfiniteAnimationPolicy] for the players under it: what decides whether their never-ending
 * frame loops run.
 *
 * A player asks for a frame every frame for as long as its document reads the continuous clock
 * (`CONTINUOUS_SEC`, `ANIMATION_TIME`), and for a frame every second while it reads the clock in
 * whole seconds. Those are infinite animations, asked for through the coroutine context's policy,
 * which a host sets for the whole app, if at all. This sets one for a part of the tree: a preview
 * held at a fixed time can pause them, so it draws its first frame and then idles, while tweens and
 * springs, which end, still run to their end.
 *
 * Null, the default, leaves it to the context's policy.
 */
@ExperimentalRcPlayerApi
public val LocalRcInfiniteAnimationPolicy: ProvidableCompositionLocal<InfiniteAnimationPolicy?> =
  staticCompositionLocalOf {
    null
  }

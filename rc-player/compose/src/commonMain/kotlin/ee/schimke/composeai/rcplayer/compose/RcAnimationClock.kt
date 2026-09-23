package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

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

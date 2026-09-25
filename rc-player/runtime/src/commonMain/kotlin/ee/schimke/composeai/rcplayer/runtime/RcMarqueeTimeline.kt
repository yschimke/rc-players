package ee.schimke.composeai.rcplayer.runtime

import kotlin.math.PI
import kotlin.math.cos

/**
 * AndroidX's sinusoidal marquee offset, [timeSeconds] after the marquee was first painted.
 *
 * `MarqueeModifierOperation` latches its start as *first paint + initial delay*, stays still until
 * a further initial delay has passed, and then takes its phase from that latched start — so the
 * content holds for twice the delay, and the cycle it then joins is already one delay in. The phase
 * is not rebased to the moment motion begins; a player that rebased it would run a delay behind
 * AndroidX for the whole animation.
 *
 * The Compose player no longer uses this: it drives a marquee with Compose's `basicMarquee`, as
 * AndroidX's embedded player does. It stays for binary compatibility until the next major release.
 */
@Deprecated("The Compose player drives a marquee with Compose's basicMarquee; this has no user.")
public fun androidXMarqueeOffset(
  overflowDistance: Float,
  density: Float,
  velocity: Float,
  initialDelayMillis: Float,
  timeSeconds: Float,
): Float {
  if (overflowDistance <= 0f) return 0f
  val initialDelaySeconds = initialDelayMillis / 1_000f
  val sinceStartSeconds = timeSeconds - initialDelaySeconds
  if (sinceStartSeconds <= initialDelaySeconds) return 0f
  val durationSeconds = overflowDistance / (density * velocity)
  if (!durationSeconds.isFinite() || durationSeconds <= 0f) return 0f
  val phase = (sinceStartSeconds % durationSeconds) / durationSeconds
  return -overflowDistance * ((1f - cos(phase * 2f * PI.toFloat())) / 2f)
}

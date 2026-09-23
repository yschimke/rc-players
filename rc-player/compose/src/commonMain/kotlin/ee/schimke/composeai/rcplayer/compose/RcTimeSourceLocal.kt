package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.composeai.rcplayer.runtime.RcTimeSnapshot
import ee.schimke.composeai.rcplayer.runtime.RcTimeSource

/**
 * The wall clock [RcComposePlayer] evaluates `TimeAttribute` and the calendar system variables
 * against — the system clock unless the embedding host supplies one.
 *
 * Separate from animation time, which follows the composition's frame clock. A screenshot test or a
 * preview that needs a watch face at a fixed time provides a frozen source here; a conformance run
 * does the same to replay a gold's `clock_snapshot`.
 */
public val LocalRcTimeSource: ProvidableCompositionLocal<RcTimeSource> = staticCompositionLocalOf {
  RcTimeSource.System
}

/** Forwards to whatever source is current, so a host swapping it does not rebuild player state. */
internal class RcForwardingTimeSource(private val current: () -> RcTimeSource) : RcTimeSource {
  override fun currentTimeMillis(): Long = current().currentTimeMillis()

  override fun snapshot(epochMillis: Long): RcTimeSnapshot = current().snapshot(epochMillis)
}

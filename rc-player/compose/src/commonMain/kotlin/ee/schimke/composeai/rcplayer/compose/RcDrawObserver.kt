package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the player draws, reported as it draws it.
 *
 * Some things a player does leave nothing behind to query once the frame is painted — where a text
 * run was anchored, where each glyph of a path's text landed. An observer is told as it happens. It
 * is for inspection: the conformance lane uses it for the corpus's text-run records. Absent — the
 * default — the player records nothing and allocates nothing for it.
 */
public fun interface RcDrawObserver {
  public fun onTextRun(run: RcTextRun)
}

/**
 * One drawn text run, in device pixels.
 *
 * [originX]/[originY] is the baseline origin the run was drawn from. [glyphs] is filled where the
 * player places glyphs one by one — text on a path — and empty where it draws the run whole.
 */
public data class RcTextRun(
  val text: String,
  val glyphCount: Int,
  val originX: Float,
  val originY: Float,
  val glyphs: List<RcGlyphPlacement> = emptyList(),
)

/** One glyph's baseline origin in device pixels, and the rotation it was drawn at. */
public data class RcGlyphPlacement(val x: Float, val y: Float, val rotationDegrees: Float)

/** The observer the player reports to; null unless a host is inspecting. */
public val LocalRcDrawObserver: ProvidableCompositionLocal<RcDrawObserver?> =
  staticCompositionLocalOf {
    null
  }

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations

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

  /**
   * The player began drawing a frame. What is reported after this belongs to it, so a frame that
   * draws no text is distinguishable from one that was never drawn.
   */
  public fun onFrame() {}

  /**
   * A conditional was evaluated while drawing, with the operand values it compared then.
   *
   * Reported as it happens because its children can write to the very ids it compares, after which
   * the values it decided on are gone.
   */
  public fun onConditional(branch: RcBranch) {}
}

/** One evaluation of [operation]: its operands as compared, and whether its children ran. */
public class RcBranch(
  public val operation: RcConditionalOperations,
  public val left: Float,
  public val right: Float,
  public val holds: Boolean,
)

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

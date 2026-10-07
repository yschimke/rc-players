package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.Path

internal actual fun Path.rcConicTo(
  x0: Float,
  y0: Float,
  x1: Float,
  y1: Float,
  x2: Float,
  y2: Float,
  weight: Float,
) {
  // Skia's own chop into quads, within a quarter pixel of the conic. The mutating
  // `org.jetbrains.skia.Path.conicTo` cannot be called here: skiko 0.144 deprecated it at
  // DeprecationLevel.ERROR and skiko 0.150 (CMP 1.12) removed it, so a host on CMP 1.12 resolving
  // this player died with `NoSuchMethodError` on the first conic. Compose's `Path` has no conic and
  // its replacement, `PathBuilder`, builds a new path rather than mutating this one.
  //
  // The depth is chosen in path units, before the canvas's own scale and the document's matrices,
  // so the tolerance is a hundredth of a unit rather than a quarter: a document drawn at 25x still
  // lands within a quarter pixel, for at most four times the quads (each level divides the error by
  // four).
  val levels =
    rcConicQuadLevels(x0, y0, x1, y1, x2, y2, weight, tolerance = RC_CONIC_PATH_TOLERANCE)
  rcConicAsQuads(x0, y0, x1, y1, x2, y2, weight, levels) { cx, cy, ex, ey ->
    quadraticTo(cx, cy, ex, ey)
  }
}

/** The conic-to-quads error allowed in path units — see [rcConicTo]. */
private const val RC_CONIC_PATH_TOLERANCE = 0.01f

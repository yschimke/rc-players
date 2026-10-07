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
  rcConicAsQuads(x0, y0, x1, y1, x2, y2, weight) { cx, cy, ex, ey -> quadraticTo(cx, cy, ex, ey) }
}

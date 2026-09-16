package ee.schimke.composeai.rcconformance.runner

import kotlin.math.abs

/**
 * The corpus's raster metric: a count of differing pixels that cannot be explained as an
 * antialiased edge.
 *
 * This is a direct port of `PLAYER_IMPLEMENTATION_GUIDE.md` §10 — Vyšniauskas' antialiased-pixel
 * detector as implemented in `mapbox/pixelmatch`, applied after compositing both images onto white.
 *
 * **It is ported exactly, and it must stay that way.** Each player computes its own verdict, so the
 * algorithm is part of the contract rather than an implementation detail: an approximate
 * reimplementation produces a number that is not comparable with anyone else's, which defeats the
 * only purpose the score has. `PixelmatchTest` pins the two self-checks the guide specifies — an
 * image against itself is 0, and against a one-pixel shift of itself is in the hundreds — because a
 * permissive `antialiased()` is silent otherwise: every raster check still passes, and the whole
 * channel proves nothing.
 *
 * Not RMSE, deliberately. RMSE averages over the canvas, so its verdict depends on how much empty
 * space surrounds the error; measured over this corpus's 622 raster comparisons an RMSE gate at 8
 * hid 34 real differences, including one that draws a filled square as a stroked one in the wrong
 * place (§2.5).
 */
public object Pixelmatch {
  /** pixelmatch's default threshold of 0.1, pre-squared against its maximum YIQ distance. */
  private const val MAX_DELTA = 35215.0 * 0.1 * 0.1

  /**
   * Counts the perceptibly differing, non-antialiased pixels between two straight-RGBA images.
   *
   * Both images must already be the reference image's own dimensions: a step that resizes the
   * viewport produces a frame of a different shape, and cropping both to the document's declared
   * size scores zero while proving nothing (§10). Differing sizes are the caller's failure to
   * report, not something to scale away.
   */
  public fun countDiff(actual: ByteArray, expected: ByteArray, width: Int, height: Int): Int {
    val n = width * height
    val a = flattenOntoWhite(actual, n)
    val b = flattenOntoWhite(expected, n)
    var differing = 0
    for (y in 0 until height) {
      for (x in 0 until width) {
        val pos = (y * width + x) * 3
        if (abs(colorDelta(a, b, pos, pos, yOnly = false)) <= MAX_DELTA) continue
        if (antialiased(a, x, y, width, height, b)) continue
        if (antialiased(b, x, y, width, height, a)) continue
        differing++
      }
    }
    return differing
  }

  /**
   * Composites straight RGBA onto white and drops alpha.
   *
   * Without this a fully transparent pixel and a white one count as different, and the corpus's
   * documents — which mostly draw onto an unpainted background — would score their own background
   * as an error.
   */
  private fun flattenOntoWhite(rgba: ByteArray, pixels: Int): IntArray {
    val out = IntArray(pixels * 3)
    var i = 0
    var o = 0
    while (o < out.size) {
      val alpha = (rgba[i + 3].toInt() and 0xFF) / 255.0
      for (channel in 0 until 3) {
        val value = 255 + ((rgba[i + channel].toInt() and 0xFF) - 255) * alpha
        out[o + channel] = value.toInt().coerceIn(0, 255)
      }
      i += 4
      o += 3
    }
    return out
  }

  private fun rgb2y(r: Int, g: Int, b: Int) = r * 0.29889531 + g * 0.58662247 + b * 0.11448223

  private fun rgb2i(r: Int, g: Int, b: Int) = r * 0.59597799 - g * 0.2741761 - b * 0.32180189

  private fun rgb2q(r: Int, g: Int, b: Int) = r * 0.21147017 - g * 0.52261711 + b * 0.31114694

  /** Signed brightness difference when [yOnly], else the squared perceptual distance. */
  private fun colorDelta(a: IntArray, b: IntArray, ia: Int, ib: Int, yOnly: Boolean): Double {
    val y = rgb2y(a[ia], a[ia + 1], a[ia + 2]) - rgb2y(b[ib], b[ib + 1], b[ib + 2])
    if (yOnly) return y
    val i = rgb2i(a[ia], a[ia + 1], a[ia + 2]) - rgb2i(b[ib], b[ib + 1], b[ib + 2])
    val q = rgb2q(a[ia], a[ia + 1], a[ia + 2]) - rgb2q(b[ib], b[ib + 1], b[ib + 2])
    val d = 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q
    return if (y > 0) -d else d
  }

  /** More than two identical neighbours means the pixel sits in a flat region, not on an edge. */
  private fun hasManySiblings(img: IntArray, x1: Int, y1: Int, w: Int, h: Int): Boolean {
    val x0 = maxOf(x1 - 1, 0)
    val y0 = maxOf(y1 - 1, 0)
    val x2 = minOf(x1 + 1, w - 1)
    val y2 = minOf(y1 + 1, h - 1)
    val pos = (y1 * w + x1) * 3
    var zeroes = if (x1 == x0 || x1 == x2 || y1 == y0 || y1 == y2) 1 else 0
    for (x in x0..x2) {
      for (y in y0..y2) {
        if (x == x1 && y == y1) continue
        val p = (y * w + x) * 3
        if (img[pos] == img[p] && img[pos + 1] == img[p + 1] && img[pos + 2] == img[p + 2]) zeroes++
        if (zeroes > 2) return true
      }
    }
    return false
  }

  private fun antialiased(
    img: IntArray,
    x1: Int,
    y1: Int,
    w: Int,
    h: Int,
    other: IntArray,
  ): Boolean {
    val x0 = maxOf(x1 - 1, 0)
    val y0 = maxOf(y1 - 1, 0)
    val x2 = minOf(x1 + 1, w - 1)
    val y2 = minOf(y1 + 1, h - 1)
    val pos = (y1 * w + x1) * 3
    var zeroes = if (x1 == x0 || x1 == x2 || y1 == y0 || y1 == y2) 1 else 0
    var min = 0.0
    var max = 0.0
    var minX = 0
    var minY = 0
    var maxX = 0
    var maxY = 0
    for (x in x0..x2) {
      for (y in y0..y2) {
        if (x == x1 && y == y1) continue
        val delta = colorDelta(img, img, pos, (y * w + x) * 3, yOnly = true)
        if (delta == 0.0) {
          if (++zeroes > 2) return false
        } else if (delta < min) {
          min = delta
          minX = x
          minY = y
        } else if (delta > max) {
          max = delta
          maxX = x
          maxY = y
        }
      }
    }
    if (min == 0.0 || max == 0.0) return false
    return (hasManySiblings(img, minX, minY, w, h) && hasManySiblings(other, minX, minY, w, h)) ||
      (hasManySiblings(img, maxX, maxY, w, h) && hasManySiblings(other, maxX, maxY, w, h))
  }
}

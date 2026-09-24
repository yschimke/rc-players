package ee.schimke.composeai.rcplayer.compose

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Android has no conic primitive, so `rcConicAsQuads` stands in for Skia's. The quads have to meet
 * end to end, finish on the conic's end point, and stay on the true rational curve.
 */
class RcConicQuadsTest {

  private fun conicPoint(
    t: Float,
    p: FloatArray,
    weight: Float,
  ): Pair<Float, Float> {
    val a = (1 - t) * (1 - t)
    val b = 2 * t * (1 - t) * weight
    val c = t * t
    val d = a + b + c
    return (a * p[0] + b * p[2] + c * p[4]) / d to (a * p[1] + b * p[3] + c * p[5]) / d
  }

  @Test
  fun aQuarterCircleConicStaysOnTheCircle() {
    // The classic quarter circle: control at the corner, weight √2/2.
    val p = floatArrayOf(100f, 0f, 100f, 100f, 0f, 100f)
    val weight = sqrt(2f) / 2f
    var startX = p[0]
    var startY = p[1]
    var quads = 0
    rcConicAsQuads(p[0], p[1], p[2], p[3], p[4], p[5], weight) { cx, cy, ex, ey ->
      quads++
      // The midpoint of each quad must lie on the unit-100 circle.
      val mx = 0.25f * startX + 0.5f * cx + 0.25f * ex
      val my = 0.25f * startY + 0.5f * cy + 0.25f * ey
      assertTrue(abs(hypot(mx, my) - 100f) < 0.25f, "quad $quads midpoint off the circle")
      assertTrue(abs(hypot(ex, ey) - 100f) < 0.01f, "quad $quads end off the circle")
      startX = ex
      startY = ey
    }
    assertEquals(1 shl rcConicQuadLevels(p[0], p[1], p[2], p[3], p[4], p[5], weight), quads)
    assertEquals(0f, startX, 1e-4f)
    assertEquals(100f, startY, 1e-4f)
  }

  @Test
  fun splitPointsLieOnTheRationalCurve() {
    val p = floatArrayOf(0f, 0f, 50f, 120f, 100f, 0f)
    val weight = 2.5f
    val ends = mutableListOf<Pair<Float, Float>>()
    rcConicAsQuads(p[0], p[1], p[2], p[3], p[4], p[5], weight, levels = 1) { _, _, ex, ey ->
      ends += ex to ey
    }
    val (x, y) = conicPoint(0.5f, p, weight)
    assertEquals(x, ends[0].first, 1e-3f)
    assertEquals(y, ends[0].second, 1e-3f)
  }

  @Test
  fun aUnitWeightConicIsItsOwnQuad() {
    var calls = 0
    rcConicAsQuads(0f, 0f, 5f, 10f, 10f, 0f, 1f, levels = 0) { cx, cy, ex, ey ->
      calls++
      assertEquals(listOf(5f, 10f, 10f, 0f), listOf(cx, cy, ex, ey))
    }
    assertEquals(1, calls)
  }

  @Test
  fun subdivisionDeepensWithTheErrorAConicWouldOtherwiseLeave() {
    // A near-unit weight needs no split; a heavy weight over a large span needs many, but never
    // more than the cap.
    assertEquals(0, rcConicQuadLevels(0f, 0f, 50f, 50f, 100f, 0f, 1.0001f))
    val heavy = rcConicQuadLevels(0f, 0f, 500f, 2000f, 1000f, 0f, 50f)
    assertTrue(heavy > 4, "a heavy conic got only $heavy levels")
    assertTrue(heavy <= RC_CONIC_MAX_LEVELS)

    // And the pieces it produces stay on the rational curve at their joins.
    val p = floatArrayOf(0f, 0f, 500f, 2000f, 1000f, 0f)
    var t = 0
    val pieces = 1 shl heavy
    rcConicAsQuads(p[0], p[1], p[2], p[3], p[4], p[5], 50f) { _, _, ex, ey ->
      t++
      if (t < pieces) {
        // Joins sit at the curve's parameter of the rational subdivision, which is not uniform
        // in t, so compare distance to the curve by sampling densely instead.
        fun distance(tt: Double): Double {
          val (cx, cy) = conicPoint(tt.toFloat(), p, 50f)
          return hypot((cx - ex).toDouble(), (cy - ey).toDouble())
        }
        // Coarse scan, then a ternary refine: near the ends a weight of 50 moves the curve
        // thousands of pixels per unit t, far faster than any fixed sampling can follow.
        val best = (0..4000).minBy { i -> distance(i / 4000.0) }
        var lo = maxOf(0.0, (best - 1) / 4000.0)
        var hi = minOf(1.0, (best + 1) / 4000.0)
        repeat(100) {
          val m1 = lo + (hi - lo) / 3
          val m2 = hi - (hi - lo) / 3
          if (distance(m1) < distance(m2)) hi = m2 else lo = m1
        }
        val nearest = distance((lo + hi) / 2)
        assertTrue(nearest < 1f, "join $t is $nearest px off the curve")
      }
    }
    assertEquals(pieces, t)
  }
}

package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class RcMonotonicCurveFitTest {
  private fun knots(random: Random, n: Int): DoubleArray {
    var acc = 0.0
    return DoubleArray(n) { i ->
      // Repeated knots on purpose: a zero-length span is where an interval search goes wrong.
      if (i > 0 && random.nextInt(5) != 0) acc += random.nextInt(4).toDouble()
      acc
    }
  }

  @Test
  fun binarySearchMatchesTheReferenceScanBitForBit() {
    val random = Random(42)
    var compared = 0
    repeat(300) {
      val n = 2 + random.nextInt(30)
      val t = knots(random, n)
      val y = Array(n) { doubleArrayOf(random.nextDouble(), random.nextDouble()) }
      val fit = RcMonotonicCurveFit(t, y)
      val reference = ReferenceLinearCurveFit(t, y)
      val span = t.last() - t.first()
      // Every knot, points between and beyond them, and NaN.
      val samples =
        t.toList() + List(40) { t.first() - 1 + random.nextDouble() * (span + 2) } + Double.NaN
      for (x in samples) {
        for (j in 0 until 2) {
          assertEquals(
            reference.getPosAt(x, j).toRawBits(),
            fit.getPosAt(x, j).toRawBits(),
            "pos $x",
          )
          assertEquals(
            reference.getSlopeAt(x, j).toRawBits(),
            fit.getSlopeAt(x, j).toRawBits(),
            "slope $x",
          )
        }
        val a = DoubleArray(2) { -7.0 }
        val b = DoubleArray(2) { -7.0 }
        reference.getPos(x, a)
        fit.getPos(x, b)
        assertEquals(a.map { it.toRawBits() }, b.map { it.toRawBits() }, "getPos $x")
        reference.getSlope(x, a)
        fit.getSlope(x, b)
        assertEquals(a.map { it.toRawBits() }, b.map { it.toRawBits() }, "getSlope $x")
        compared++
      }
    }
    assertTrue(compared > 10_000)
  }

  @Test
  fun aLongTubeBuildsInLinearishTime() {
    // 100,000 control points, explicit density 1, three sides: the case that scanned every knot
    // for every ring.
    val points = 100_000
    val params = FloatArray(2 + points * 3)
    params[0] = 0.01f
    params[1] = 1f
    for (i in 0 until points) {
      params[2 + i * 3] = i.toFloat()
      params[2 + i * 3 + 1] = (i % 7).toFloat()
    }
    val start = TimeSource.Monotonic.markNow()
    val mesh =
      RcPrimitive3D.build(
        RcPrimitive3D.TUBE,
        3f,
        RcPrimitive3D.FLAG_TUBE_PATH_DENSITY,
        listOf(params),
      )
    assertTrue(start.elapsedNow().inWholeMilliseconds < 10_000, "built in ${start.elapsedNow()}")
    assertTrue(mesh.triangleCount > 0)
  }
}

package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.math.hypot

/**
 * Monotone cubic Hermite spline over N-dimensional samples, a port of the reference
 * `MonotonicCurveFit`.
 *
 * The tube family fits its centreline to one of these: chord-length parametrized, so the spline
 * passes through every control point without the overshoot a plain Catmull-Rom gives on a tight
 * corner. All arithmetic is `double` in the reference too, so this port is exact by construction.
 */
internal class RcMonotonicCurveFit(time: DoubleArray, y: Array<DoubleArray>) {
  private val mT: DoubleArray = time.copyOf()
  private val mY: Array<DoubleArray> = Array(y.size) { y[it].copyOf() }
  private val mTangent: Array<DoubleArray>
  private val mExtrapolate = true

  /**
   * Whether the knots are sorted and free of NaN, so an interval can be found by binary search. The
   * reference scans from the first knot on every lookup, which makes a long tube quadratic; the
   * searches below find the same interval it would. Unsorted knots keep the linear scan.
   */
  private val sorted: Boolean = (1 until time.size).all { time[it] >= time[it - 1] }

  /**
   * The first interval `i` in `0 until n - 1` whose end exceeds [t] (or reaches it when
   * [inclusive]), as the reference's loop finds it; `n - 1` when there is none.
   */
  private fun interval(t: Double, inclusive: Boolean): Int {
    val last = mT.size - 1
    if (!sorted) {
      for (i in 0 until last) if (if (inclusive) t <= mT[i + 1] else t < mT[i + 1]) return i
      return last
    }
    var lo = 0
    var hi = last
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      val end = mT[mid + 1]
      if (if (inclusive) t <= end else t < end) hi = mid else lo = mid + 1
    }
    return lo
  }

  /** The first knot index `<= limit` equal to [t], or -1: the reference's exact-hit check. */
  private fun exactKnot(t: Double, limit: Int): Int {
    if (!sorted) {
      for (i in 0..limit) if (t == mT[i]) return i
      return -1
    }
    var lo = 0
    var hi = limit + 1
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (mT[mid] < t) lo = mid + 1 else hi = mid
    }
    return if (lo <= limit && mT[lo] == t) lo else -1
  }

  init {
    val n = time.size
    val dim = y[0].size
    val slope = Array(n - 1) { DoubleArray(dim) }
    mTangent = Array(n) { DoubleArray(dim) }
    for (j in 0 until dim) {
      for (i in 0 until n - 1) {
        val dt = time[i + 1] - time[i]
        slope[i][j] = (y[i + 1][j] - y[i][j]) / dt
        if (i == 0) {
          mTangent[i][j] = slope[i][j]
        } else {
          mTangent[i][j] = (slope[i - 1][j] + slope[i][j]) * 0.5
        }
      }
      mTangent[n - 1][j] = slope[n - 2][j]
    }
    // Fritsch-Carlson limiter: clamp tangents that would make a segment non-monotone.
    for (i in 0 until n - 1) {
      for (j in 0 until dim) {
        if (slope[i][j] == 0.0) {
          mTangent[i][j] = 0.0
          mTangent[i + 1][j] = 0.0
        } else {
          val a = mTangent[i][j] / slope[i][j]
          val b = mTangent[i + 1][j] / slope[i][j]
          val hh = hypot(a, b)
          if (hh > 9.0) {
            val tt = 3.0 / hh
            mTangent[i][j] = tt * a * slope[i][j]
            mTangent[i + 1][j] = tt * b * slope[i][j]
          }
        }
      }
    }
  }

  /** Position of every curve at [t]. Extrapolates linearly beyond the ends. */
  fun getPos(t: Double, v: DoubleArray) {
    val n = mT.size
    val dim = mY[0].size
    if (mExtrapolate) {
      if (t <= mT[0]) {
        val tmp = DoubleArray(dim)
        getSlope(mT[0], tmp)
        for (j in 0 until dim) v[j] = mY[0][j] + (t - mT[0]) * tmp[j]
        return
      }
      if (t >= mT[n - 1]) {
        val tmp = DoubleArray(dim)
        getSlope(mT[n - 1], tmp)
        for (j in 0 until dim) v[j] = mY[n - 1][j] + (t - mT[n - 1]) * tmp[j]
        return
      }
    }
    if (t.isNaN()) return // the reference's loop matches nothing and writes nothing
    val i = interval(t, inclusive = false)
    // As in the reference, an exact knot hit writes the sample but does not return; the
    // interpolation below then overwrites it.
    val hit = exactKnot(t, minOf(i, n - 2))
    if (hit >= 0) for (j in 0 until dim) v[j] = mY[hit][j]
    if (i < n - 1) {
      val h = mT[i + 1] - mT[i]
      val x = (t - mT[i]) / h
      for (j in 0 until dim) {
        v[j] = interpolate(h, x, mY[i][j], mY[i + 1][j], mTangent[i][j], mTangent[i + 1][j])
      }
    }
  }

  /** [getPos] narrowed to float. */
  fun getPos(t: Double, v: FloatArray) {
    val dim = mY[0].size
    val tmp = DoubleArray(dim)
    getPos(t, tmp)
    for (j in 0 until dim) v[j] = tmp[j].toFloat()
  }

  /** Position of curve [j] at [t]. */
  fun getPosAt(t: Double, j: Int): Double {
    val n = mT.size
    if (mExtrapolate) {
      if (t <= mT[0]) return mY[0][j] + (t - mT[0]) * getSlopeAt(mT[0], j)
      if (t >= mT[n - 1]) return mY[n - 1][j] + (t - mT[n - 1]) * getSlopeAt(mT[n - 1], j)
    }
    if (t.isNaN()) return 0.0
    val i = interval(t, inclusive = false)
    // The reference returns at the first exact knot hit at or before the interval it would stop in.
    val hit = exactKnot(t, minOf(i, n - 2))
    if (hit >= 0) return mY[hit][j]
    if (i >= n - 1) return 0.0
    val h = mT[i + 1] - mT[i]
    val x = (t - mT[i]) / h
    return interpolate(h, x, mY[i][j], mY[i + 1][j], mTangent[i][j], mTangent[i + 1][j])
  }

  /** Slope of every curve at [t]. Clamped to the knot range. */
  fun getSlope(t0: Double, v: DoubleArray) {
    var t = t0
    val n = mT.size
    val dim = mY[0].size
    if (t <= mT[0]) t = mT[0] else if (t >= mT[n - 1]) t = mT[n - 1]
    if (t.isNaN()) return
    val i = interval(t, inclusive = true)
    if (i >= n - 1) return
    val h = mT[i + 1] - mT[i]
    val x = (t - mT[i]) / h
    for (j in 0 until dim) {
      v[j] = diff(h, x, mY[i][j], mY[i + 1][j], mTangent[i][j], mTangent[i + 1][j]) / h
    }
  }

  fun getSlopeAt(t0: Double, j: Int): Double {
    var t = t0
    val n = mT.size
    if (t <= mT[0]) t = mT[0] else if (t >= mT[n - 1]) t = mT[n - 1]
    if (t.isNaN()) return 0.0
    val i = interval(t, inclusive = true)
    if (i >= n - 1) return 0.0
    val h = mT[i + 1] - mT[i]
    val x = (t - mT[i]) / h
    return diff(h, x, mY[i][j], mY[i + 1][j], mTangent[i][j], mTangent[i + 1][j]) / h
  }

  private companion object {
    /** Cubic Hermite basis, in the reference's exact term order. */
    fun interpolate(h: Double, x: Double, y1: Double, y2: Double, t1: Double, t2: Double): Double {
      val x2 = x * x
      val x3 = x2 * x
      return -2.0 * x3 * y2 + 3.0 * x2 * y2 + 2.0 * x3 * y1 - 3.0 * x2 * y1 +
        y1 +
        h * t2 * x3 +
        h * t1 * x3 - h * t2 * x2 - 2.0 * h * t1 * x2 + h * t1 * x
    }

    /** Derivative of the Hermite basis with respect to x. */
    fun diff(h: Double, x: Double, y1: Double, y2: Double, t1: Double, t2: Double): Double {
      val x2 = x * x
      return -6.0 * x2 * y2 + 6.0 * x * y2 + 6.0 * x2 * y1 - 6.0 * x * y1 +
        3.0 * h * t2 * x2 +
        3.0 * h * t1 * x2 - 2.0 * h * t2 * x - 4.0 * h * t1 * x + h * t1
    }
  }
}

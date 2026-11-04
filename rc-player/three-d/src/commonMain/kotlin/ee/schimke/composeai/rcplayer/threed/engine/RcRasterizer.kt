package ee.schimke.composeai.rcplayer.threed.engine

/**
 * 28.4 fixed-point edge-function triangle rasterizer with a depth buffer, ported from the reference
 * `Rasterizer.java`. Smaller z is closer; [clearDepth] fills with +infinity.
 *
 * Kotlin `Int` wraps and `Float` rounds to 32 bits exactly as Java's do, so the reference
 * transcribes verbatim. The one place that differs from a naive transcription is [setup]'s plane
 * equation, which is evaluated in double from widened coordinates (see the comment there).
 */
internal object RcRasterizer {
  fun clearDepth(zbuf: FloatArray) {
    zbuf.fill(Float.POSITIVE_INFINITY)
  }

  private fun min3(x1: Int, x2: Int, x3: Int): Int =
    if (x1 > x2) (if (x2 > x3) x3 else x2) else (if (x1 > x3) x3 else x1)

  private fun max3(x1: Int, x2: Int, x3: Int): Int =
    if (x1 < x2) (if (x2 < x3) x3 else x2) else (if (x1 < x3) x3 else x1)

  /** Shared setup for every entry point: winding, plane equation, bounds and edge constants. */
  private class Setup {
    var ok = false
    var dx = 0f
    var dy = 0f
    var zoff = 0f
    var minx = 0
    var maxx = 0
    var miny = 0
    var maxy = 0
    var fdx12 = 0
    var fdx23 = 0
    var fdx31 = 0
    var fdy12 = 0
    var fdy23 = 0
    var fdy31 = 0
    // 64-bit: the textured path needs exact values for grazing triangles whose 28.4 coordinates
    // square past 2^31. The other paths truncate to Int, which reproduces 32-bit wrap exactly.
    var cy1 = 0L
    var cy2 = 0L
    var cy3 = 0L
    var swapped = false
  }

  private fun setup(
    w: Int,
    h: Int,
    fx3: Float,
    fy3: Float,
    fz3: Float,
    fx2In: Float,
    fy2In: Float,
    fz2In: Float,
    fx1In: Float,
    fy1In: Float,
    fz1In: Float,
  ): Setup {
    // Allocated per triangle rather than shared: two players may rasterize on different threads.
    val s = Setup()
    var fx1 = fx1In
    var fy1 = fy1In
    var fz1 = fz1In
    var fx2 = fx2In
    var fy2 = fy2In
    var fz2 = fz2In
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      fx1 = fx2In
      fy1 = fy2In
      fz1 = fz2In
      fx2 = fx1In
      fy2 = fy1In
      fz2 = fz1In
      s.swapped = true
    }
    // Widened before the arithmetic: this is twice the projected signed area, which cancels badly
    // for sliver triangles along a grazing silhouette, and it then divides the depth plane.
    val d1 = fx1.toDouble()
    val d2 = fx2.toDouble()
    val d3 = fx3.toDouble()
    val e1 = fy1.toDouble()
    val e2 = fy2.toDouble()
    val e3 = fy3.toDouble()
    val g1 = fz1.toDouble()
    val g2 = fz2.toDouble()
    val g3 = fz3.toDouble()
    val d = (d1 * (e3 - e2) - d2 * e3 + d3 * e2 + (d2 - d3) * e1)
    if (d == 0.0) return s

    s.dx = (-(e1 * (g3 - g2) - e2 * g3 + e3 * g2 + (e2 - e3) * g1) / d).toFloat()
    s.dy = ((d1 * (g3 - g2) - d2 * g3 + d3 * g2 + (d2 - d3) * g1) / d).toFloat()
    s.zoff =
      ((d1 * (e3 * g2 - e2 * g3) + e1 * (d2 * g3 - d3 * g2) + (d3 * e2 - d2 * e3) * g1) / d)
        .toFloat()

    val y1 = (16.0f * fy1 + .5f).toInt()
    val y2 = (16.0f * fy2 + .5f).toInt()
    val y3 = (16.0f * fy3 + .5f).toInt()
    val x1 = (16.0f * fx1 + .5f).toInt()
    val x2 = (16.0f * fx2 + .5f).toInt()
    val x3 = (16.0f * fx3 + .5f).toInt()

    val dx12 = x1 - x2
    val dx23 = x2 - x3
    val dx31 = x3 - x1
    val dy12 = y1 - y2
    val dy23 = y2 - y3
    val dy31 = y3 - y1

    s.fdx12 = dx12 shl 4
    s.fdx23 = dx23 shl 4
    s.fdx31 = dx31 shl 4
    s.fdy12 = dy12 shl 4
    s.fdy23 = dy23 shl 4
    s.fdy31 = dy31 shl 4

    var minx = (min3(x1, x2, x3) + 0xF) shr 4
    var maxx = (max3(x1, x2, x3) + 0xF) shr 4
    var miny = (min3(y1, y2, y3) + 0xF) shr 4
    var maxy = (max3(y1, y2, y3) + 0xF) shr 4
    if (miny < 0) miny = 0
    if (minx < 0) minx = 0
    if (maxx > w) maxx = w
    if (maxy > h) maxy = h
    s.minx = minx
    s.maxx = maxx
    s.miny = miny
    s.maxy = maxy

    var c1 = dy12.toLong() * x1 - dx12.toLong() * y1
    var c2 = dy23.toLong() * x2 - dx23.toLong() * y2
    var c3 = dy31.toLong() * x3 - dx31.toLong() * y3
    // Top-left fill rule: shared edges are neither double-covered nor left as gaps.
    if (dy12 < 0 || (dy12 == 0 && dx12 > 0)) c1++
    if (dy23 < 0 || (dy23 == 0 && dx23 > 0)) c2++
    if (dy31 < 0 || (dy31 == 0 && dx31 > 0)) c3++
    s.cy1 = c1 + dx12.toLong() * (miny shl 4) - dy12.toLong() * (minx shl 4)
    s.cy2 = c2 + dx23.toLong() * (miny shl 4) - dy23.toLong() * (minx shl 4)
    s.cy3 = c3 + dx31.toLong() * (miny shl 4) - dy31.toLong() * (minx shl 4)
    s.ok = true
    return s
  }

  /** Flat-shaded triangle with a depth test; [color] is packed ARGB. */
  fun fillTriangle(
    zbuff: FloatArray,
    img: IntArray,
    color: Int,
    w: Int,
    h: Int,
    fx3: Float,
    fy3: Float,
    fz3: Float,
    fx2: Float,
    fy2: Float,
    fz2: Float,
    fx1: Float,
    fy1: Float,
    fz1: Float,
  ) {
    val s = setup(w, h, fx3, fy3, fz3, fx2, fy2, fz2, fx1, fy1, fz1)
    if (!s.ok) return
    var cy1 = s.cy1.toInt()
    var cy2 = s.cy2.toInt()
    var cy3 = s.cy3.toInt()
    var off = s.miny * w
    for (y in s.miny until s.maxy) {
      var cx1 = cy1
      var cx2 = cy2
      var cx3 = cy3
      val p = s.zoff + s.dy * y
      for (x in s.minx until s.maxx) {
        if (cx1 > 0 && cx2 > 0 && cx3 > 0) {
          val point = x + off
          val zval = p + s.dx * x
          if (zbuff[point] > zval) {
            zbuff[point] = zval
            img[point] = color
          }
        }
        cx1 -= s.fdy12
        cx2 -= s.fdy23
        cx3 -= s.fdy31
      }
      cy1 += s.fdx12
      cy2 += s.fdx23
      cy3 += s.fdx31
      off += w
    }
  }

  /** Gouraud-shaded: each pixel's ARGB is barycentric over the three vertex colours. */
  fun fillTriangleGouraud(
    zbuff: FloatArray,
    img: IntArray,
    c3: Int,
    c2In: Int,
    c1In: Int,
    w: Int,
    h: Int,
    fx3: Float,
    fy3: Float,
    fz3: Float,
    fx2: Float,
    fy2: Float,
    fz2: Float,
    fx1: Float,
    fy1: Float,
    fz1: Float,
  ) {
    val s = setup(w, h, fx3, fy3, fz3, fx2, fy2, fz2, fx1, fy1, fz1)
    if (!s.ok) return
    val c1 = if (s.swapped) c2In else c1In
    val c2 = if (s.swapped) c1In else c2In

    var cy1 = s.cy1.toInt()
    var cy2 = s.cy2.toInt()
    var cy3 = s.cy3.toInt()
    // Constant across the triangle and proportional to twice its area: it normalises the edge
    // functions into barycentric weights.
    val sum = cy1.toLong() + cy2 + cy3
    if (sum == 0L) return
    val inv = 1.0f / sum

    val a1 = (c1 shr 24) and 0xFF
    val r1 = (c1 shr 16) and 0xFF
    val g1 = (c1 shr 8) and 0xFF
    val b1 = c1 and 0xFF
    val a2 = (c2 shr 24) and 0xFF
    val r2 = (c2 shr 16) and 0xFF
    val g2 = (c2 shr 8) and 0xFF
    val b2 = c2 and 0xFF
    val a3 = (c3 shr 24) and 0xFF
    val r3 = (c3 shr 16) and 0xFF
    val g3 = (c3 shr 8) and 0xFF
    val b3 = c3 and 0xFF

    var off = s.miny * w
    for (y in s.miny until s.maxy) {
      var cx1 = cy1
      var cx2 = cy2
      var cx3 = cy3
      val p = s.zoff + s.dy * y
      for (x in s.minx until s.maxx) {
        if (cx1 > 0 && cx2 > 0 && cx3 > 0) {
          val point = x + off
          val zval = p + s.dx * x
          if (zbuff[point] > zval) {
            zbuff[point] = zval
            val w1 = cx2 * inv
            val w2 = cx3 * inv
            val w3 = cx1 * inv
            val a = (w1 * a1 + w2 * a2 + w3 * a3).toInt()
            val r = (w1 * r1 + w2 * r2 + w3 * r3).toInt()
            val g = (w1 * g1 + w2 * g2 + w3 * g3).toInt()
            val b = (w1 * b1 + w2 * b2 + w3 * b3).toInt()
            img[point] = (a shl 24) or (r shl 16) or (g shl 8) or b
          }
        }
        cx1 -= s.fdy12
        cx2 -= s.fdy23
        cx3 -= s.fdy31
      }
      cy1 += s.fdx12
      cy2 += s.fdx23
      cy3 += s.fdx31
      off += w
    }
  }

  /**
   * Textured with perspective-correct UV. Fully transparent texels are cutouts: neither colour nor
   * depth is written, so a cutout never occludes what shows through it.
   */
  fun fillTriangleTextured(
    zbuff: FloatArray,
    img: IntArray,
    tex: IntArray,
    texW: Int,
    texH: Int,
    c3: Int,
    c2In: Int,
    c1In: Int,
    w: Int,
    h: Int,
    fx3: Float,
    fy3: Float,
    fz3: Float,
    u3: Float,
    v3: Float,
    iw3: Float,
    fx2: Float,
    fy2: Float,
    fz2: Float,
    u2In: Float,
    v2In: Float,
    iw2In: Float,
    fx1: Float,
    fy1: Float,
    fz1: Float,
    u1In: Float,
    v1In: Float,
    iw1In: Float,
  ) {
    val s = setup(w, h, fx3, fy3, fz3, fx2, fy2, fz2, fx1, fy1, fz1)
    if (!s.ok) return
    val sw = s.swapped
    val c1 = if (sw) c2In else c1In
    val c2 = if (sw) c1In else c2In
    val u1 = if (sw) u2In else u1In
    val u2 = if (sw) u1In else u2In
    val v1 = if (sw) v2In else v1In
    val v2 = if (sw) v1In else v2In
    val iw1 = if (sw) iw2In else iw1In
    val iw2 = if (sw) iw1In else iw2In

    var cy1 = s.cy1
    var cy2 = s.cy2
    var cy3 = s.cy3
    val sum = s.cy1 + s.cy2 + s.cy3
    if (sum == 0L) return
    val inv = 1.0f / sum

    val a1 = (c1 shr 24) and 0xFF
    val r1 = (c1 shr 16) and 0xFF
    val g1 = (c1 shr 8) and 0xFF
    val b1 = c1 and 0xFF
    val a2 = (c2 shr 24) and 0xFF
    val r2 = (c2 shr 16) and 0xFF
    val g2 = (c2 shr 8) and 0xFF
    val b2 = c2 and 0xFF
    val a3 = (c3 shr 24) and 0xFF
    val r3 = (c3 shr 16) and 0xFF
    val g3 = (c3 shr 8) and 0xFF
    val b3 = c3 and 0xFF

    val uiw1 = u1 * iw1
    val uiw2 = u2 * iw2
    val uiw3 = u3 * iw3
    val viw1 = v1 * iw1
    val viw2 = v2 * iw2
    val viw3 = v3 * iw3

    var off = s.miny * w
    for (y in s.miny until s.maxy) {
      var cx1 = cy1
      var cx2 = cy2
      var cx3 = cy3
      val p = s.zoff + s.dy * y
      for (x in s.minx until s.maxx) {
        if (cx1 > 0 && cx2 > 0 && cx3 > 0) {
          val point = x + off
          val zval = p + s.dx * x
          if (zbuff[point] > zval) {
            val w1 = cx2 * inv
            val w2 = cx3 * inv
            val w3 = cx1 * inv
            val invW = 1f / (w1 * iw1 + w2 * iw2 + w3 * iw3)
            val u = (w1 * uiw1 + w2 * uiw2 + w3 * uiw3) * invW
            val v = (w1 * viw1 + w2 * viw2 + w3 * viw3) * invW
            var tx = (u * texW).toInt()
            var ty = ((1f - v) * texH).toInt()
            if (tx < 0) tx = 0 else if (tx >= texW) tx = texW - 1
            if (ty < 0) ty = 0 else if (ty >= texH) ty = texH - 1
            val t = tex[ty * texW + tx]
            val ta = (t shr 24) and 0xFF
            if (ta != 0) {
              zbuff[point] = zval
              val la = (w1 * a1 + w2 * a2 + w3 * a3).toInt()
              val lr = (w1 * r1 + w2 * r2 + w3 * r3).toInt()
              val lg = (w1 * g1 + w2 * g2 + w3 * g3).toInt()
              val lb = (w1 * b1 + w2 * b2 + w3 * b3).toInt()
              val a = (ta * la) / 255
              val r = (((t shr 16) and 0xFF) * lr) / 255
              val g = (((t shr 8) and 0xFF) * lg) / 255
              val b = ((t and 0xFF) * lb) / 255
              img[point] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
          }
        }
        cx1 -= s.fdy12
        cx2 -= s.fdy23
        cx3 -= s.fdy31
      }
      cy1 += s.fdx12
      cy2 += s.fdx23
      cy3 += s.fdx31
      off += w
    }
  }

  /** Depth test and write with no colour: the wireframe prepass. */
  fun fillTriangleDepthOnly(
    zbuff: FloatArray,
    w: Int,
    h: Int,
    fx3: Float,
    fy3: Float,
    fz3: Float,
    fx2: Float,
    fy2: Float,
    fz2: Float,
    fx1: Float,
    fy1: Float,
    fz1: Float,
  ) {
    val s = setup(w, h, fx3, fy3, fz3, fx2, fy2, fz2, fx1, fy1, fz1)
    if (!s.ok) return
    var cy1 = s.cy1.toInt()
    var cy2 = s.cy2.toInt()
    var cy3 = s.cy3.toInt()
    var off = s.miny * w
    for (y in s.miny until s.maxy) {
      var cx1 = cy1
      var cx2 = cy2
      var cx3 = cy3
      val p = s.zoff + s.dy * y
      for (x in s.minx until s.maxx) {
        if (cx1 > 0 && cx2 > 0 && cx3 > 0) {
          val point = x + off
          val zval = p + s.dx * x
          if (zbuff[point] > zval) zbuff[point] = zval
        }
        cx1 -= s.fdy12
        cx2 -= s.fdy23
        cx3 -= s.fdy31
      }
      cy1 += s.fdx12
      cy2 += s.fdx23
      cy3 += s.fdx31
      off += w
    }
  }

  /** Depth-tested line, colour only; [bias] lets an edge pass against its own face. */
  fun drawLineDepthTested(
    zbuff: FloatArray,
    img: IntArray,
    color: Int,
    w: Int,
    h: Int,
    x0: Float,
    y0: Float,
    z0: Float,
    x1: Float,
    y1: Float,
    z1: Float,
    bias: Float,
  ) {
    val dx = x1 - x0
    val dy = y1 - y0
    val adx = if (dx < 0) -dx else dx
    val ady = if (dy < 0) -dy else dy
    val length = if (adx > ady) adx else ady
    // The reference steps every pixel of the projected edge, on screen or not, so an edge that
    // projects far off screen (near the eye, or a huge model) can take millions of steps. Such an
    // edge is clipped to the viewport first, depth interpolated along it. Edges short enough to
    // matter on screen keep the reference's exact sample positions.
    if (!(length <= (w + h) * 2f + 4f)) {
      clipToViewport(x0, y0, z0, x1, y1, z1, w, h)?.let { c ->
        drawLineDepthTested(zbuff, img, color, w, h, c[0], c[1], c[2], c[3], c[4], c[5], bias)
      }
      return
    }
    var steps = length.toInt()
    if (steps < 1) steps = 1
    val inv = 1f / steps
    val sx = dx * inv
    val sy = dy * inv
    val sz = (z1 - z0) * inv
    var px = x0
    var py = y0
    var pz = z0
    for (i in 0..steps) {
      val ix = px.toInt()
      val iy = py.toInt()
      if (ix in 0 until w && iy in 0 until h) {
        val point = ix + iy * w
        if (zbuff[point] >= pz - bias) img[point] = color
      }
      px += sx
      py += sy
      pz += sz
    }
  }

  /**
   * Liang-Barsky clip of the segment to the viewport, one pixel of margin, with z interpolated: the
   * clipped endpoints `x0, y0, z0, x1, y1, z1`, or null when nothing of it is on screen.
   */
  private fun clipToViewport(
    x0: Float,
    y0: Float,
    z0: Float,
    x1: Float,
    y1: Float,
    z1: Float,
    w: Int,
    h: Int,
  ): FloatArray? {
    if (!(x0.isFinite() && y0.isFinite() && x1.isFinite() && y1.isFinite())) return null
    val dx = (x1 - x0).toDouble()
    val dy = (y1 - y0).toDouble()
    var t0 = 0.0
    var t1 = 1.0
    val p = doubleArrayOf(-dx, dx, -dy, dy)
    val q = doubleArrayOf(x0 + 1.0, w + 1.0 - x0, y0 + 1.0, h + 1.0 - y0)
    for (k in 0 until 4) {
      if (p[k] == 0.0) {
        if (q[k] < 0) return null
      } else {
        val r = q[k] / p[k]
        if (p[k] < 0) {
          if (r > t1) return null
          if (r > t0) t0 = r
        } else {
          if (r < t0) return null
          if (r < t1) t1 = r
        }
      }
    }
    val dz = (z1 - z0).toDouble()
    return floatArrayOf(
      (x0 + t0 * dx).toFloat(),
      (y0 + t0 * dy).toFloat(),
      (z0 + t0 * dz).toFloat(),
      (x0 + t1 * dx).toFloat(),
      (y0 + t1 * dy).toFloat(),
      (z0 + t1 * dz).toFloat(),
    )
  }
}

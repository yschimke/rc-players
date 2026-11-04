package ee.schimke.composeai.rcplayer.threed.engine

import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.catmull
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.clampInt
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.cmax
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.cmin
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.concatMeshes
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.cross3
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.disk
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.lround
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.perpBasis
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.segCount
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The six generators that need machinery rather than a formula: the tube family (monotone-spline
 * centrelines with adaptive ring counts and parallel-transport frames), sweep and helix (swept
 * cross-sections with per-station scale and twist), and extrudePath (scanline / nonzero-winding
 * trapezoid triangulation, so concave outlines, holes and overlapping contours all work).
 */
internal object RcPrimitive3DSweep {
  private const val DEFAULT_TUBE_SIDES = 14
  /** Ceiling on rings emitted per control span, so a pathological path cannot blow up memory. */
  private const val MAX_TUBE_PER_SPAN = 256

  /**
   * Sweep a tube along a centreline with unit tangents and a per-ring radius. A parallel-transport
   * frame (carry u forward, re-orthogonalize against each new tangent) avoids the spin a recomputed
   * basis would introduce.
   */
  private fun buildTubeFromCenterline(
    px: FloatArray,
    py: FloatArray,
    pz: FloatArray,
    tx: FloatArray,
    ty: FloatArray,
    tz: FloatArray,
    radii: FloatArray,
    n: Int,
    sides: Int,
    capped: Boolean,
  ): Rc3DMeshData {
    val u = FloatArray(3)
    val vv = FloatArray(3)
    perpBasis(tx[0], ty[0], tz[0], u, vv)
    val verts = RcPrimitive3DMath.geometryFloats(n * (sides + 1) * 3)
    val normals = RcPrimitive3DMath.geometryFloats(n * (sides + 1) * 3)
    for (i in 0 until n) {
      if (i > 0) {
        val dot = u[0] * tx[i] + u[1] * ty[i] + u[2] * tz[i]
        u[0] -= dot * tx[i]
        u[1] -= dot * ty[i]
        u[2] -= dot * tz[i]
        val ul = sqrt((u[0] * u[0] + u[1] * u[1] + u[2] * u[2]).toDouble()).toFloat()
        if (ul < 1e-6f) {
          perpBasis(tx[i], ty[i], tz[i], u, vv)
        } else {
          u[0] /= ul
          u[1] /= ul
          u[2] /= ul
          cross3(tx[i], ty[i], tz[i], u[0], u[1], u[2], vv)
        }
      }
      for (j in 0..sides) {
        val phi = 2.0 * PI * j / sides
        val c = cos(phi).toFloat()
        val s = sin(phi).toFloat()
        val dx = c * u[0] + s * vv[0]
        val dy = c * u[1] + s * vv[1]
        val dz = c * u[2] + s * vv[2]
        val p = (i * (sides + 1) + j) * 3
        val ri = radii[i]
        verts[p] = px[i] + ri * dx
        verts[p + 1] = py[i] + ri * dy
        verts[p + 2] = pz[i] + ri * dz
        normals[p] = dx
        normals[p + 1] = dy
        normals[p + 2] = dz
      }
    }
    val cols = sides + 1
    val indices = IntArray((n - 1) * sides * 6)
    var k = 0
    for (i in 0 until n - 1) {
      for (j in 0 until sides) {
        val a = i * cols + j
        val b = i * cols + j + 1
        val cc = (i + 1) * cols + j
        val d = (i + 1) * cols + j + 1
        // edge0 = longitudinal along the path, edge1 = around the section, diagonal last.
        indices[k++] = cc
        indices[k++] = a
        indices[k++] = b
        indices[k++] = b
        indices[k++] = d
        indices[k++] = cc
      }
    }
    val wall = Rc3DMeshData(verts, normals, indices)
    if (!capped) return wall
    val u0 = FloatArray(3)
    val v0 = FloatArray(3)
    val u1 = FloatArray(3)
    val v1 = FloatArray(3)
    perpBasis(tx[0], ty[0], tz[0], u0, v0)
    perpBasis(tx[n - 1], ty[n - 1], tz[n - 1], u1, v1)
    val cap0 =
      disk(px[0], py[0], pz[0], u0[0], u0[1], u0[2], v0[0], v0[1], v0[2], radii[0], sides, true)
    val cap1 =
      disk(
        px[n - 1],
        py[n - 1],
        pz[n - 1],
        u1[0],
        u1[1],
        u1[2],
        v1[0],
        v1[1],
        v1[2],
        radii[n - 1],
        sides,
        false,
      )
    return concatMeshes(listOf(wall, cap0, cap1))
  }

  /** Chord-length-parametrized position spline; fills [knot] (strictly increasing). */
  private fun buildPositionFit(
    cxp: FloatArray,
    cyp: FloatArray,
    czp: FloatArray,
    cn: Int,
    knot: DoubleArray,
  ): RcMonotonicCurveFit {
    val y = Array(cn) { DoubleArray(3) }
    y[0][0] = cxp[0].toDouble()
    y[0][1] = cyp[0].toDouble()
    y[0][2] = czp[0].toDouble()
    for (i in 1 until cn) {
      val dx = cxp[i] - cxp[i - 1]
      val dy = cyp[i] - cyp[i - 1]
      val dz = czp[i] - czp[i - 1]
      // The sum of squares is a float expression, widened only for the sqrt.
      val d = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
      knot[i] = knot[i - 1] + cmax(d, 1e-4) // floor: coincident points would divide by 0
      y[i][0] = cxp[i].toDouble()
      y[i][1] = cyp[i].toDouble()
      y[i][2] = czp[i].toDouble()
    }
    return RcMonotonicCurveFit(knot, y)
  }

  private fun radiusAt(radiusFit: RcMonotonicCurveFit?, t: Double, constRadius: Float): Float {
    if (radiusFit == null) return constRadius
    val r = radiusFit.getPosAt(t, 0).toFloat()
    return if (r < 0f) 0f else r
  }

  /** Output streams of the centreline sampler. */
  /**
   * Refuse a tube of [rings] rings of [sides] sides past the geometry budget, counted in Long so a
   * hostile count cannot overflow past the check. Called before any per-ring buffer exists.
   */
  private fun requireRings(rings: Long, sides: Int) {
    require(rings >= 0 && rings * (sides + 1) * 3 <= RcPrimitive3DMath.MAX_GEOMETRY_ELEMENTS) {
      "MeshPrimitive tube: $rings rings of $sides sides exceeds the geometry budget"
    }
  }

  private class Centerline(n: Int) {
    val px = FloatArray(n)
    val py = FloatArray(n)
    val pz = FloatArray(n)
    val tx = FloatArray(n)
    val ty = FloatArray(n)
    val tz = FloatArray(n)
    val radii = FloatArray(n)
  }

  private fun sampleCenterline(
    fit: RcMonotonicCurveFit,
    t: Double,
    pos: FloatArray,
    slope: DoubleArray,
    out: Centerline,
    w: Int,
    pTx: Float,
    pTy: Float,
    pTz: Float,
  ): Int {
    fit.getPos(t, pos)
    fit.getSlope(t, slope)
    out.px[w] = pos[0]
    out.py[w] = pos[1]
    out.pz[w] = pos[2]
    val sx = slope[0].toFloat()
    val sy = slope[1].toFloat()
    val sz = slope[2].toFloat()
    val l = sqrt((sx * sx + sy * sy + sz * sz).toDouble()).toFloat()
    if (l < 1e-6f) {
      out.tx[w] = pTx
      out.ty[w] = pTy
      out.tz[w] = pTz
    } else {
      out.tx[w] = sx / l
      out.ty[w] = sy / l
      out.tz[w] = sz / l
    }
    return w + 1
  }

  /**
   * Sample the centreline with an adaptive (or explicit) ring count and build the tube. The
   * adaptive count matches the lengthwise step to the cross-section edge length using the local
   * radius, so long or sharply curved spans densify automatically.
   */
  private fun splineSweep(
    fit: RcMonotonicCurveFit,
    knot: DoubleArray,
    cn: Int,
    sides: Int,
    capped: Boolean,
    pathPerSpan: Float,
    radiusFit: RcMonotonicCurveFit?,
    constRadius: Float,
  ): Rc3DMeshData {
    val manual = pathPerSpan > 0f
    val fixedNs = if (manual) clampInt(lround(pathPerSpan), 1, MAX_TUBE_PER_SPAN) else 0
    val arcSubSamples = 6
    val perSpan = IntArray(cn - 1)
    var total = 1 // + the final endpoint
    val a = DoubleArray(3)
    val b = DoubleArray(3)
    for (i in 0 until cn - 1) {
      val ns: Int
      if (manual) {
        ns = fixedNs
      } else {
        val k0 = knot[i]
        val k1 = knot[i + 1]
        val rMid =
          if (radiusFit != null) radiusFit.getPosAt((k0 + k1) * 0.5, 0).toFloat() else constRadius
        val step = cmax(2.0 * abs(rMid).toDouble() * sin(PI / sides), 1e-4)
        var arc = 0.0
        fit.getPos(k0, a)
        for (s in 1..arcSubSamples) {
          fit.getPos(k0 + (k1 - k0) * s / arcSubSamples, b)
          val dx = b[0] - a[0]
          val dy = b[1] - a[1]
          val dz = b[2] - a[2]
          arc += sqrt(dx * dx + dy * dy + dz * dz)
          a[0] = b[0]
          a[1] = b[1]
          a[2] = b[2]
        }
        ns = clampInt(lround(arc / step), 1, MAX_TUBE_PER_SPAN)
      }
      perSpan[i] = ns
      total += ns
    }
    val n = total
    requireRings(n.toLong(), sides)
    val out = Centerline(n)
    val pos = FloatArray(3)
    val slope = DoubleArray(3)
    var pTx = 0f
    var pTy = 1f
    var pTz = 0f
    var w = 0
    for (i in 0 until cn - 1) {
      val k0 = knot[i]
      val k1 = knot[i + 1]
      val ns = perSpan[i]
      for (s in 0 until ns) {
        // Each span emits its start and interior but not its end — that is the next span's
        // start, or the final endpoint below — so rings are not duplicated at the joins.
        val t = k0 + (k1 - k0) * s / ns
        w = sampleCenterline(fit, t, pos, slope, out, w, pTx, pTy, pTz)
        out.radii[w - 1] = radiusAt(radiusFit, t, constRadius)
        pTx = out.tx[w - 1]
        pTy = out.ty[w - 1]
        pTz = out.tz[w - 1]
      }
    }
    val tEnd = knot[cn - 1]
    w = sampleCenterline(fit, tEnd, pos, slope, out, w, pTx, pTy, pTz)
    out.radii[w - 1] = radiusAt(radiusFit, tEnd, constRadius)
    return buildTubeFromCenterline(
      out.px,
      out.py,
      out.pz,
      out.tx,
      out.ty,
      out.tz,
      out.radii,
      n,
      sides,
      capped,
    )
  }

  private fun tubeLegacy(
    cxp: FloatArray,
    cyp: FloatArray,
    czp: FloatArray,
    cn: Int,
    radius: Float,
    sides: Int,
    capped: Boolean,
    segments: Float,
    flags: Int,
    pathPerSpan: Float,
  ): Rc3DMeshData {
    val px: FloatArray
    val py: FloatArray
    val pz: FloatArray
    if ((flags and RcPrimitive3D.FLAG_SPLINE) != 0) {
      val per =
        if (pathPerSpan > 0f) clampInt(lround(pathPerSpan), 1, MAX_TUBE_PER_SPAN)
        else segCount(segments, 8, 1)
      requireRings((cn - 1).toLong() * per + 1, sides)
      val n = (cn - 1) * per + 1
      px = FloatArray(n)
      py = FloatArray(n)
      pz = FloatArray(n)
      var w = 0
      for (i in 0 until cn - 1) {
        val i0 = maxOf(0, i - 1)
        val i2 = i + 1
        val i3 = minOf(cn - 1, i + 2)
        for (s in 0 until per) {
          val t = s / per.toFloat()
          px[w] = catmull(cxp[i0], cxp[i], cxp[i2], cxp[i3], t)
          py[w] = catmull(cyp[i0], cyp[i], cyp[i2], cyp[i3], t)
          pz[w] = catmull(czp[i0], czp[i], czp[i2], czp[i3], t)
          w++
        }
      }
      px[w] = cxp[cn - 1]
      py[w] = cyp[cn - 1]
      pz[w] = czp[cn - 1]
    } else {
      px = cxp
      py = cyp
      pz = czp
    }
    val n = px.size
    val tx = FloatArray(n)
    val ty = FloatArray(n)
    val tz = FloatArray(n)
    for (i in 0 until n) {
      var ax = 0f
      var ay = 0f
      var az = 0f
      if (i > 0) {
        ax += px[i] - px[i - 1]
        ay += py[i] - py[i - 1]
        az += pz[i] - pz[i - 1]
      }
      if (i < n - 1) {
        ax += px[i + 1] - px[i]
        ay += py[i + 1] - py[i]
        az += pz[i + 1] - pz[i]
      }
      var l = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
      if (l < 1e-6f) {
        ax = 0f
        ay = 1f
        az = 0f
        l = 1f
      }
      tx[i] = ax / l
      ty[i] = ay / l
      tz[i] = az / l
    }
    val radii = FloatArray(n) { radius }
    return buildTubeFromCenterline(px, py, pz, tx, ty, tz, radii, n, sides, capped)
  }

  // ---- extrude path helpers -------------------------------------------------

  private fun signedArea(ring: FloatArray): Float {
    val n = ring.size / 2
    var a = 0f
    for (i in 0 until n) {
      val j = (i + 1) % n
      a += ring[i * 2] * ring[j * 2 + 1] - ring[j * 2] * ring[i * 2 + 1]
    }
    return a * 0.5f
  }

  private fun pointInPolygon(ring: FloatArray, px: Float, py: Float): Boolean {
    val n = ring.size / 2
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
      val xi = ring[i * 2]
      val yi = ring[i * 2 + 1]
      val xj = ring[j * 2]
      val yj = ring[j * 2 + 1]
      val crosses = (yi > py) != (yj > py)
      if (crosses && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside
      j = i
    }
    return inside
  }

  /** Even-odd hole test: a contour is a hole if it lies inside an odd number of the others. */
  private fun isHole(contours: List<FloatArray>, ringIndex: Int): Boolean {
    val ring = contours[ringIndex]
    if (ring.size < 2) return false
    val px = ring[0]
    val py = ring[1]
    var inside = 0
    for (c in contours.indices) {
      if (c == ringIndex) continue
      if (pointInPolygon(contours[c], px, py)) inside++
    }
    return (inside and 1) == 1
  }

  /**
   * Offset a closed contour inward by b, mitering at corners so adjacent edges share the vertex.
   */
  private fun miterInset(ring: FloatArray, b: Float, miterLimit: Float): FloatArray {
    val n = ring.size / 2
    val out = FloatArray(ring.size)
    val sign = if (signedArea(ring) >= 0f) 1f else -1f // +1 CCW (interior on the left)
    for (i in 0 until n) {
      val ip = (i - 1 + n) % n
      val inx = (i + 1) % n
      var pdx = ring[i * 2] - ring[ip * 2]
      var pdy = ring[i * 2 + 1] - ring[ip * 2 + 1]
      var ndx = ring[inx * 2] - ring[i * 2]
      var ndy = ring[inx * 2 + 1] - ring[i * 2 + 1]
      val pl = sqrt((pdx * pdx + pdy * pdy).toDouble()).toFloat()
      if (pl > 1e-9f) {
        pdx /= pl
        pdy /= pl
      }
      val nl = sqrt((ndx * ndx + ndy * ndy).toDouble()).toFloat()
      if (nl > 1e-9f) {
        ndx /= nl
        ndy /= nl
      }
      val n1x = sign * -pdy
      val n1y = sign * pdx
      val n2x = sign * -ndy
      val n2y = sign * ndx
      var mx = n1x + n2x
      var my = n1y + n2y
      val ml = sqrt((mx * mx + my * my).toDouble()).toFloat()
      if (ml < 1e-6f) {
        out[i * 2] = ring[i * 2] + b * n2x
        out[i * 2 + 1] = ring[i * 2 + 1] + b * n2y
        continue
      }
      mx /= ml
      my /= ml
      val cosv = mx * n1x + my * n1y
      val l = b / cmax(cosv, 1f / miterLimit)
      out[i * 2] = ring[i * 2] + l * mx
      out[i * 2 + 1] = ring[i * 2 + 1] + l * my
    }
    return out
  }

  /**
   * Triangulate the two flat caps: a scanline / trapezoid sweep over all contour edges using the
   * nonzero-winding rule — what fonts and Path use by default. Needs no ear clipping and handles
   * holes and overlapping contours for free.
   */
  private fun scanlineCaps(
    contours: List<FloatArray>,
    cx: Float,
    cy: Float,
    frontZ: Float,
    backZ: Float,
  ): Rc3DMeshData {
    var edgeCount = 0
    for (ring in contours) edgeCount += ring.size / 2
    val eYlo = FloatArray(edgeCount)
    val eYhi = FloatArray(edgeCount)
    val eXlo = FloatArray(edgeCount)
    val eSlope = FloatArray(edgeCount)
    val eDir = IntArray(edgeCount)
    var ne = 0
    val ys = FloatArray(edgeCount * 2)
    var nys = 0
    for (ring in contours) {
      val n = ring.size / 2
      for (i in 0 until n) {
        val ay = ring[i * 2 + 1]
        val j = (i + 1) % n
        val by = ring[j * 2 + 1]
        ys[nys++] = ay
        if (ay == by) continue // horizontal edges contribute no crossings
        val ax = ring[i * 2]
        val bx = ring[j * 2]
        if (ay < by) {
          eYlo[ne] = ay
          eYhi[ne] = by
          eXlo[ne] = ax
        } else {
          eYlo[ne] = by
          eYhi[ne] = ay
          eXlo[ne] = bx
        }
        eSlope[ne] = (bx - ax) / (by - ay)
        eDir[ne] = if (by > ay) 1 else -1
        ne++
      }
    }
    ys.sort(0, nys)
    var uy = 0
    for (i in 0 until nys) {
      if (uy == 0 || ys[i] > ys[uy - 1] + 1e-7f) ys[uy++] = ys[i]
    }
    val traps = ArrayList<FloatArray>()
    val crossX = FloatArray(ne)
    val crossE = IntArray(ne)
    var s = 0
    while (s + 1 < uy) {
      val yA = ys[s]
      val yB = ys[s + 1]
      s++
      if (yB - yA < 1e-7f) continue
      val ymid = 0.5f * (yA + yB)
      var nx = 0
      for (e in 0 until ne) {
        if (eYlo[e] < ymid && ymid < eYhi[e]) {
          crossX[nx] = eXlo[e] + (ymid - eYlo[e]) * eSlope[e]
          crossE[nx] = e
          nx++
        }
      }
      if (nx < 2) continue
      // Insertion sort, matching the reference: a stable order decides which edges pair.
      for (a in 1 until nx) {
        val kx = crossX[a]
        val ke = crossE[a]
        var b = a - 1
        while (b >= 0 && crossX[b] > kx) {
          crossX[b + 1] = crossX[b]
          crossE[b + 1] = crossE[b]
          b--
        }
        crossX[b + 1] = kx
        crossE[b + 1] = ke
      }
      var winding = 0
      for (i in 0 until nx - 1) {
        winding += eDir[crossE[i]]
        if (winding == 0) continue
        val le = crossE[i]
        val re = crossE[i + 1]
        val xLtop = eXlo[le] + (yB - eYlo[le]) * eSlope[le]
        val xLbot = eXlo[le] + (yA - eYlo[le]) * eSlope[le]
        val xRtop = eXlo[re] + (yB - eYlo[re]) * eSlope[re]
        val xRbot = eXlo[re] + (yA - eYlo[re]) * eSlope[re]
        traps.add(floatArrayOf(xLtop, yB, xRtop, yB, xRbot, yA, xLbot, yA))
      }
    }
    val t = traps.size
    val verts = RcPrimitive3DMath.geometryFloats(t * 8 * 3)
    val normals = RcPrimitive3DMath.geometryFloats(t * 8 * 3)
    val indices = RcPrimitive3DMath.geometryInts(t * 12)
    var io = 0
    val backBase = t * 4
    for (q in 0 until t) {
      val g = traps[q]
      val fb = q * 4
      val bb = backBase + q * 4
      for (j in 0 until 4) {
        val x = g[j * 2]
        val y = g[j * 2 + 1]
        val f = (fb + j) * 3
        verts[f] = cx + x
        verts[f + 1] = cy + y
        verts[f + 2] = frontZ
        normals[f + 2] = 1f
        val bk = (bb + j) * 3
        verts[bk] = cx + x
        verts[bk + 1] = cy + y
        verts[bk + 2] = backZ
        normals[bk + 2] = -1f
      }
      indices[io++] = fb
      indices[io++] = fb + 2
      indices[io++] = fb + 1
      indices[io++] = fb
      indices[io++] = fb + 3
      indices[io++] = fb + 2
      indices[io++] = bb
      indices[io++] = bb + 1
      indices[io++] = bb + 2
      indices[io++] = bb
      indices[io++] = bb + 2
      indices[io++] = bb + 3
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  private fun setV(
    verts: FloatArray,
    normals: FloatArray,
    i: Int,
    x: Float,
    y: Float,
    z: Float,
    nx: Float,
    ny: Float,
    nz: Float,
  ) {
    val p = i * 3
    verts[p] = x
    verts[p + 1] = y
    verts[p + 2] = z
    normals[p] = nx
    normals[p + 1] = ny
    normals[p + 2] = nz
  }

  private fun extrudePathSides(
    contours: List<FloatArray>,
    inset: List<FloatArray>?,
    cx: Float,
    cy: Float,
    cz: Float,
    hd: Float,
    b: Float,
  ): Rc3DMeshData {
    val beveled = b > 0f && inset != null
    val wallHd = hd - b
    val inv = 0.70710678.toFloat() // 1/sqrt(2), the 45-degree chamfer normal split
    var edgeCount = 0
    for (ring in contours) edgeCount += ring.size / 2
    val vpe = if (beveled) 12 else 4
    val tpe = if (beveled) 6 else 2
    val verts = RcPrimitive3DMath.geometryFloats(edgeCount * vpe * 3)
    val normals = RcPrimitive3DMath.geometryFloats(edgeCount * vpe * 3)
    // Sized for every edge like the reference; contours with < 3 points leave trailing zeros.
    val indices = RcPrimitive3DMath.geometryInts(edgeCount * tpe * 3)
    var io = 0
    var se = 0
    for (c in contours.indices) {
      val ring = contours[c]
      val n = ring.size / 2
      if (n < 3) {
        se += n
        continue
      }
      val ins = if (beveled) inset[c] else null
      val hole = isHole(contours, c)
      val reverse = (signedArea(ring) > 0f) == hole
      for (i in 0 until n) {
        val ia = if (reverse) (n - i) % n else i
        val ib = if (reverse) (n - i - 1 + n) % n else (i + 1) % n
        val ax = ring[ia * 2]
        val ay = ring[ia * 2 + 1]
        val bx = ring[ib * 2]
        val by = ring[ib * 2 + 1]
        var nx = by - ay // outward = right of travel for a material-on-left loop
        var ny = -(bx - ax)
        val len = sqrt((nx * nx + ny * ny).toDouble()).toFloat()
        if (len > 1e-9f) {
          nx /= len
          ny /= len
        }
        if (ins == null) {
          val v = se * 4
          setV(verts, normals, v, cx + ax, cy + ay, cz + hd, nx, ny, 0f)
          setV(verts, normals, v + 1, cx + bx, cy + by, cz + hd, nx, ny, 0f)
          setV(verts, normals, v + 2, cx + ax, cy + ay, cz - hd, nx, ny, 0f)
          setV(verts, normals, v + 3, cx + bx, cy + by, cz - hd, nx, ny, 0f)
          indices[io++] = v
          indices[io++] = v + 2
          indices[io++] = v + 3
          indices[io++] = v
          indices[io++] = v + 3
          indices[io++] = v + 1
        } else {
          val iax = ins[ia * 2]
          val iay = ins[ia * 2 + 1]
          val ibx = ins[ib * 2]
          val iby = ins[ib * 2 + 1]
          val v = se * 12
          val cnx = nx * inv
          val cny = ny * inv
          setV(verts, normals, v, cx + iax, cy + iay, cz + hd, cnx, cny, inv)
          setV(verts, normals, v + 1, cx + ibx, cy + iby, cz + hd, cnx, cny, inv)
          setV(verts, normals, v + 2, cx + ax, cy + ay, cz + wallHd, cnx, cny, inv)
          setV(verts, normals, v + 3, cx + bx, cy + by, cz + wallHd, cnx, cny, inv)
          indices[io++] = v
          indices[io++] = v + 2
          indices[io++] = v + 3
          indices[io++] = v
          indices[io++] = v + 3
          indices[io++] = v + 1
          setV(verts, normals, v + 4, cx + ax, cy + ay, cz + wallHd, nx, ny, 0f)
          setV(verts, normals, v + 5, cx + bx, cy + by, cz + wallHd, nx, ny, 0f)
          setV(verts, normals, v + 6, cx + ax, cy + ay, cz - wallHd, nx, ny, 0f)
          setV(verts, normals, v + 7, cx + bx, cy + by, cz - wallHd, nx, ny, 0f)
          indices[io++] = v + 4
          indices[io++] = v + 6
          indices[io++] = v + 7
          indices[io++] = v + 4
          indices[io++] = v + 7
          indices[io++] = v + 5
          setV(verts, normals, v + 8, cx + ax, cy + ay, cz - wallHd, cnx, cny, -inv)
          setV(verts, normals, v + 9, cx + bx, cy + by, cz - wallHd, cnx, cny, -inv)
          setV(verts, normals, v + 10, cx + iax, cy + iay, cz - hd, cnx, cny, -inv)
          setV(verts, normals, v + 11, cx + ibx, cy + iby, cz - hd, cnx, cny, -inv)
          indices[io++] = v + 8
          indices[io++] = v + 10
          indices[io++] = v + 11
          indices[io++] = v + 8
          indices[io++] = v + 11
          indices[io++] = v + 9
        }
        se++
      }
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  /** Flat end cap for sweep(): a centroid fan, wound to agree with the face direction. */
  private fun sweepCap(
    verts: FloatArray,
    normals: FloatArray,
    indices: IntArray,
    io0: Int,
    vbase: Int,
    stationRow: Int,
    cols: Int,
    m: Int,
    fnx: Float,
    fny: Float,
    fnz: Float,
  ): Int {
    var io = io0
    var ccx = 0f
    var ccy = 0f
    var ccz = 0f
    for (k in 0 until m) {
      val p = (stationRow * cols + k) * 3
      ccx += verts[p]
      ccy += verts[p + 1]
      ccz += verts[p + 2]
    }
    ccx /= m
    ccy /= m
    ccz /= m
    val centroid = vbase
    verts[centroid * 3] = ccx
    verts[centroid * 3 + 1] = ccy
    verts[centroid * 3 + 2] = ccz
    normals[centroid * 3] = fnx
    normals[centroid * 3 + 1] = fny
    normals[centroid * 3 + 2] = fnz
    for (k in 0 until m) {
      val src = (stationRow * cols + k) * 3
      val dst = (vbase + 1 + k) * 3
      verts[dst] = verts[src]
      verts[dst + 1] = verts[src + 1]
      verts[dst + 2] = verts[src + 2]
      normals[dst] = fnx
      normals[dst + 1] = fny
      normals[dst + 2] = fnz
    }
    val r0 = vbase + 1
    val r1 = vbase + 1 + (1 % m)
    val ux = verts[r0 * 3] - ccx
    val uy = verts[r0 * 3 + 1] - ccy
    val uz = verts[r0 * 3 + 2] - ccz
    val wx = verts[r1 * 3] - ccx
    val wy = verts[r1 * 3 + 1] - ccy
    val wz = verts[r1 * 3 + 2] - ccz
    val gx = uy * wz - uz * wy
    val gy = uz * wx - ux * wz
    val gz = ux * wy - uy * wx
    val flip = (gx * fnx + gy * fny + gz * fnz) < 0f
    for (k in 0 until m) {
      val aa = vbase + 1 + k
      val bb = vbase + 1 + ((k + 1) % m)
      indices[io++] = centroid
      if (flip) {
        indices[io++] = bb
        indices[io++] = aa
      } else {
        indices[io++] = aa
        indices[io++] = bb
      }
    }
    return io
  }

  fun tube(params: FloatArray, capped: Boolean, segments: Float, flags: Int): Rc3DMeshData {
    val radius = params[0]
    // FLAG_TUBE_PATH_DENSITY inserts an explicit rings-per-span float at params[1], so the
    // points then start at params[2]. <= 0 means adaptive.
    val manualDensity = (flags and RcPrimitive3D.FLAG_TUBE_PATH_DENSITY) != 0
    val off = if (manualDensity) 2 else 1
    val pathPerSpan = if (manualDensity) params[1] else 0f
    val cn = (params.size - off) / 3
    require(cn >= 2) { "MeshPrimitive tube: need >= 2 points" }
    val cxp = FloatArray(cn)
    val cyp = FloatArray(cn)
    val czp = FloatArray(cn)
    for (i in 0 until cn) {
      cxp[i] = params[off + i * 3]
      cyp[i] = params[off + i * 3 + 1]
      czp[i] = params[off + i * 3 + 2]
    }
    val sides = segCount(segments, DEFAULT_TUBE_SIDES, 3)
    if ((flags and RcPrimitive3D.FLAG_TUBE_LEGACY) != 0) {
      return tubeLegacy(cxp, cyp, czp, cn, radius, sides, capped, segments, flags, pathPerSpan)
    }
    val knot = DoubleArray(cn)
    val fit = buildPositionFit(cxp, cyp, czp, cn, knot)
    return splineSweep(fit, knot, cn, sides, capped, pathPerSpan, null, radius)
  }

  fun profileTube(params: FloatArray, sides: Int, flags: Int): Rc3DMeshData {
    val nPoints = lround(params[0])
    val manualDensity = (flags and RcPrimitive3D.FLAG_TUBE_PATH_DENSITY) != 0
    val capped = (flags and RcPrimitive3D.FLAG_TUBE_CAP) != 0
    val posOff = if (manualDensity) 2 else 1
    val pathPerSpan = if (manualDensity) params[1] else 0f
    require(nPoints >= 2) { "MeshPrimitive profile tube: need >= 2 points" }
    // Checked in Long before any arithmetic in Int: 3 * nPoints overflows for a large count.
    require(posOff + 3L * nPoints < params.size) {
      "MeshPrimitive profile tube: $nPoints points do not fit in ${params.size} params"
    }
    val rOff = posOff + 3 * nPoints
    val nR = params.size - rOff
    require(nR >= 1) { "MeshPrimitive profile tube: need >= 1 radius" }
    val cxp = FloatArray(nPoints)
    val cyp = FloatArray(nPoints)
    val czp = FloatArray(nPoints)
    for (i in 0 until nPoints) {
      cxp[i] = params[posOff + i * 3]
      cyp[i] = params[posOff + i * 3 + 1]
      czp[i] = params[posOff + i * 3 + 2]
    }
    val knot = DoubleArray(nPoints)
    val fit = buildPositionFit(cxp, cyp, czp, nPoints, knot)
    val pathLen = knot[nPoints - 1]
    var radiusFit: RcMonotonicCurveFit? = null
    if (nR >= 2) {
      val rk = DoubleArray(nR)
      val rv = Array(nR) { DoubleArray(1) }
      for (j in 0 until nR) {
        rk[j] = pathLen * j / (nR - 1)
        rv[j][0] = params[rOff + j].toDouble()
      }
      radiusFit = RcMonotonicCurveFit(rk, rv)
    }
    return splineSweep(fit, knot, nPoints, sides, capped, pathPerSpan, radiusFit, params[rOff])
  }

  fun sweep(
    scalars: FloatArray,
    xsec: FloatArray,
    path: FloatArray,
    scales: FloatArray?,
    twists: FloatArray?,
  ): Rc3DMeshData {
    val m = xsec.size / 2
    val n = path.size / 3
    require(m >= 2 && n >= 2) {
      "MeshPrimitive sweep: need >= 2 cross-section points and >= 2 path points"
    }
    val closed = scalars.size > 0 && scalars[0] != 0f
    val capStart = scalars.size > 1 && scalars[1] != 0f
    val capEnd = scalars.size > 2 && scalars[2] != 0f
    val cx = if (scalars.size > 3) scalars[3] else 0f
    val cy = if (scalars.size > 4) scalars[4] else 0f
    val cz = if (scalars.size > 5) scalars[5] else 0f

    val px = FloatArray(n)
    val py = FloatArray(n)
    val pz = FloatArray(n)
    for (i in 0 until n) {
      px[i] = cx + path[i * 3]
      py[i] = cy + path[i * 3 + 1]
      pz[i] = cz + path[i * 3 + 2]
    }
    val tx = FloatArray(n)
    val ty = FloatArray(n)
    val tz = FloatArray(n)
    for (i in 0 until n) {
      val a = maxOf(0, i - 1)
      val b = minOf(n - 1, i + 1)
      val dx = px[b] - px[a]
      val dy = py[b] - py[a]
      val dz = pz[b] - pz[a]
      var l = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
      if (l < 1e-9f) l = 1e-9f
      tx[i] = dx / l
      ty[i] = dy / l
      tz[i] = dz / l
    }
    val nlx = FloatArray(m)
    val nly = FloatArray(m)
    val xEdges = if (closed) m else m - 1
    for (e in 0 until xEdges) {
      val k0 = e
      val k1 = (e + 1) % m
      val dx = xsec[k1 * 2] - xsec[k0 * 2]
      val dy = xsec[k1 * 2 + 1] - xsec[k0 * 2 + 1]
      var ex = dy
      var ey = -dx
      val l = sqrt((ex * ex + ey * ey).toDouble()).toFloat()
      if (l > 1e-9f) {
        ex /= l
        ey /= l
      }
      nlx[k0] += ex
      nly[k0] += ey
      nlx[k1] += ex
      nly[k1] += ey
    }
    for (k in 0 until m) {
      val l = sqrt((nlx[k] * nlx[k] + nly[k] * nly[k]).toDouble()).toFloat()
      if (l > 1e-9f) {
        nlx[k] /= l
        nly[k] /= l
      } else {
        nlx[k] = 1f
        nly[k] = 0f
      }
    }
    val cols = if (closed) m + 1 else m // duplicate the seam column when closed
    val wallV = n * cols
    val capV = (if (capStart) m + 1 else 0) + (if (capEnd) m + 1 else 0)
    val verts = RcPrimitive3DMath.geometryFloats((wallV + capV) * 3)
    val normals = RcPrimitive3DMath.geometryFloats((wallV + capV) * 3)
    val wallStrips = if (closed) m else m - 1
    val wallTris = (n - 1) * wallStrips * 2
    val capTris = (if (capStart) m else 0) + (if (capEnd) m else 0)
    val indices = RcPrimitive3DMath.geometryInts((wallTris + capTris) * 3)

    val u = FloatArray(3)
    val v = FloatArray(3)
    perpBasis(tx[0], ty[0], tz[0], u, v)
    for (i in 0 until n) {
      if (i > 0) {
        val dot = u[0] * tx[i] + u[1] * ty[i] + u[2] * tz[i]
        u[0] -= dot * tx[i]
        u[1] -= dot * ty[i]
        u[2] -= dot * tz[i]
        val ul = sqrt((u[0] * u[0] + u[1] * u[1] + u[2] * u[2]).toDouble()).toFloat()
        if (ul < 1e-6f) {
          perpBasis(tx[i], ty[i], tz[i], u, v)
        } else {
          u[0] /= ul
          u[1] /= ul
          u[2] /= ul
          cross3(tx[i], ty[i], tz[i], u[0], u[1], u[2], v)
        }
      }
      val s = if (scales != null && scales.size > i) scales[i] else 1f
      val tw = if (twists != null && twists.size > i) twists[i] else 0f
      val ct = cos(tw.toDouble()).toFloat()
      val st = sin(tw.toDouble()).toFloat()
      for (j in 0 until cols) {
        val k = j % m
        val lx = xsec[k * 2]
        val ly = xsec[k * 2 + 1]
        val rx = ct * lx - st * ly // twist the section in its own plane
        val ry = st * lx + ct * ly
        val p = (i * cols + j) * 3
        verts[p] = px[i] + s * (rx * u[0] + ry * v[0])
        verts[p + 1] = py[i] + s * (rx * u[1] + ry * v[1])
        verts[p + 2] = pz[i] + s * (rx * u[2] + ry * v[2])
        val nrx = ct * nlx[k] - st * nly[k]
        val nry = st * nlx[k] + ct * nly[k]
        var nx = nrx * u[0] + nry * v[0]
        var ny = nrx * u[1] + nry * v[1]
        var nz = nrx * u[2] + nry * v[2]
        val nl = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
        if (nl > 1e-9f) {
          nx /= nl
          ny /= nl
          nz /= nl
        }
        normals[p] = nx
        normals[p + 1] = ny
        normals[p + 2] = nz
      }
    }
    var io = 0
    for (i in 0 until n - 1) {
      for (j in 0 until wallStrips) {
        val a = i * cols + j
        val b = i * cols + j + 1
        val cc = (i + 1) * cols + j
        val d = (i + 1) * cols + j + 1
        indices[io++] = cc
        indices[io++] = a
        indices[io++] = b
        indices[io++] = b
        indices[io++] = d
        indices[io++] = cc
      }
    }
    var capBase = wallV
    if (capStart) {
      io = sweepCap(verts, normals, indices, io, capBase, 0, cols, m, -tx[0], -ty[0], -tz[0])
      capBase += m + 1
    }
    if (capEnd) {
      io =
        sweepCap(
          verts,
          normals,
          indices,
          io,
          capBase,
          n - 1,
          cols,
          m,
          tx[n - 1],
          ty[n - 1],
          tz[n - 1],
        )
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  fun helix(
    coilR: Float,
    tubeR: Float,
    pitch: Float,
    turns: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    sides: Int,
  ): Rc3DMeshData {
    val perTurn = maxOf(8, sides * 2)
    val requested = perTurn.toDouble() * abs(turns).toDouble()
    require(
      requested.isFinite() && requested * (sides + 1) * 3 <= RcPrimitive3DMath.MAX_GEOMETRY_ELEMENTS
    ) {
      "MeshPrimitive helix: $requested stations exceeds the geometry budget"
    }
    val stations = maxOf(2, lround(perTurn * abs(turns)) + 1)
    val path = RcPrimitive3DMath.geometryFloats(stations * 3)
    val totalRise = pitch * turns
    for (i in 0 until stations) {
      val f = i / (stations - 1).toFloat()
      val ang = 2.0 * PI * turns * f // double * float widens each float operand in turn
      path[i * 3] = coilR * cos(ang).toFloat()
      path[i * 3 + 1] = totalRise * (f - 0.5f) // centre the coil vertically
      path[i * 3 + 2] = coilR * sin(ang).toFloat()
    }
    val xsec = FloatArray(sides * 2)
    for (k in 0 until sides) {
      val a = 2.0 * PI * k / sides
      xsec[k * 2] = tubeR * cos(a).toFloat()
      xsec[k * 2 + 1] = tubeR * sin(a).toFloat()
    }
    val scalars = floatArrayOf(1f, 1f, 1f, cx, cy, cz) // closed, both ends capped
    return sweep(scalars, xsec, path, null, null)
  }

  fun extrudePath(p: FloatArray): Rc3DMeshData {
    val depth = p[0]
    val cx = p[1]
    val cy = p[2]
    val cz = p[3]
    val bevel = p[4]
    val nc = lround(p[5])
    // Counts are checked against the coordinates actually present before anything is allocated:
    // a contour cannot be longer than the payload carrying it. The Java reference fails such a
    // shape with an index error; here it fails before it can ask for gigabytes.
    require(nc in 0..p.size) { "MeshPrimitive extrude path: contour count $nc" }
    val contours = ArrayList<FloatArray>()
    var idx = 6
    for (c in 0 until nc) {
      require(idx < p.size) { "MeshPrimitive extrude path: contour $c is missing" }
      val cnt = lround(p[idx++])
      require(cnt >= 0) { "MeshPrimitive extrude path: negative contour length $cnt" }
      require(cnt <= (p.size - idx) / 2) {
        "MeshPrimitive extrude path: contour of $cnt points has ${(p.size - idx) / 2}"
      }
      val ring = RcPrimitive3DMath.geometryFloats(cnt * 2)
      var k = 0
      while (k < cnt * 2 && idx < p.size) {
        ring[k] = p[idx++]
        k++
      }
      contours.add(ring)
    }
    val hd = depth * 0.5f
    if (bevel <= 0f) {
      // Sharp (default): flat caps over the full contours plus straight walls.
      val caps = scanlineCaps(contours, cx, cy, cz + hd, cz - hd)
      val sides = extrudePathSides(contours, null, cx, cy, cz, hd, 0f)
      return concatMeshes(listOf(caps, sides))
    }
    val b = cmin(bevel, hd * 0.9f)
    val inset = ArrayList<FloatArray>()
    for (ring in contours) inset.add(miterInset(ring, b, 4f))
    val caps = scanlineCaps(inset, cx, cy, cz + hd, cz - hd)
    val sides = extrudePathSides(contours, inset, cx, cy, cz, hd, b)
    return concatMeshes(listOf(caps, sides))
  }
}

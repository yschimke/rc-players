package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.truncate

/**
 * Shared helpers for the primitive generators.
 *
 * Numeric rule for the whole family: arithmetic is `Float` exactly where the reference is `float`,
 * transcendentals take and return `Double` (Java `Math` semantics: a float argument is widened
 * first and the result narrowed afterwards), and no expression is regrouped.
 */
internal object RcPrimitive3DMath {
  /** C `std::max` for floating point: `(a < b) ? b : a`, which keeps `a` for NaN and -0. */
  fun cmax(a: Float, b: Float): Float = if (a < b) b else a

  /** C `std::min` for floating point: `(b < a) ? b : a`. */
  fun cmin(a: Float, b: Float): Float = if (b < a) b else a

  fun cmax(a: Double, b: Double): Double = if (a < b) b else a

  fun clampF(x: Float, lo: Float, hi: Float): Float = if (x < lo) lo else (if (x > hi) hi else x)

  fun clampInt(x: Int, lo: Int, hi: Int): Int = if (x < lo) lo else (if (x > hi) hi else x)

  /**
   * `(int) std::lround(d)`: round half away from zero, then the C `long -> int` narrowing (which
   * keeps the low 32 bits).
   */
  fun lround(d: Double): Int {
    val t = truncate(d)
    val r = if (abs(d - t) >= 0.5) t + sign(d) else t
    return r.toLong().toInt()
  }

  fun lround(f: Float): Int = lround(f.toDouble())

  fun cross3(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, out: FloatArray) {
    out[0] = ay * bz - az * by
    out[1] = az * bx - ax * bz
    out[2] = ax * by - ay * bx
  }

  /** Round segments to an int, falling back to [dflt] when <= 0, clamped to [mn]. */
  fun segCount(segments: Float, dflt: Int, mn: Int): Int {
    val n = if (segments > 0f) lround(segments) else dflt
    return maxOf(mn, n)
  }

  /** Catmull-Rom interpolation of the span p1..p2 (with neighbours p0,p3) at t. */
  fun catmull(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    // Left-to-right coefficient sums: regrouping them is a different float.
    return 0.5f *
      ((2f * p1) +
        (-p0 + p2) * t +
        (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
        (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
  }

  /** Merge several meshes, re-basing indices. UV survives only if every part carries it. */
  fun concatMeshes(parts: List<Rc3DMeshData>): Rc3DMeshData {
    var allUv = parts.isNotEmpty()
    for (m in parts) if (m.uv == null || m.uv.isEmpty()) allUv = false
    var nv = 0
    var nn = 0
    var ni = 0
    var nu = 0
    for (m in parts) {
      nv += m.verts.size
      nn += m.normals.size
      ni += m.indices.size
      if (allUv) nu += m.uv!!.size
    }
    val verts = FloatArray(nv)
    val normals = FloatArray(nn)
    val indices = IntArray(ni)
    val uv = if (allUv) FloatArray(nu) else null
    var vo = 0
    var no = 0
    var io = 0
    var uo = 0
    var base = 0
    for (m in parts) {
      m.verts.copyInto(verts, vo)
      vo += m.verts.size
      m.normals.copyInto(normals, no)
      no += m.normals.size
      for (i in m.indices) indices[io++] = i + base
      if (uv != null) {
        m.uv!!.copyInto(uv, uo)
        uo += m.uv.size
      }
      base += m.verts.size / 3
    }
    return Rc3DMeshData(verts, normals, indices, uv)
  }

  /** Orthonormal basis perpendicular to the unit vector d. */
  fun perpBasis(dx: Float, dy: Float, dz: Float, u: FloatArray, v: FloatArray) {
    val ax = abs(dx)
    val ay = abs(dy)
    val az = abs(dz)
    val rx: Float
    val ry: Float
    val rz: Float
    if (ax <= ay && ax <= az) {
      rx = 1f
      ry = 0f
      rz = 0f
    } else if (ay <= az) {
      rx = 0f
      ry = 1f
      rz = 0f
    } else {
      rx = 0f
      ry = 0f
      rz = 1f
    }
    val ux = dy * rz - dz * ry
    val uy = dz * rx - dx * rz
    val uz = dx * ry - dy * rx
    var ul = sqrt((ux * ux + uy * uy + uz * uz).toDouble()).toFloat()
    if (ul < 1e-6f) ul = 1e-6f
    u[0] = ux / ul
    u[1] = uy / ul
    u[2] = uz / ul
    cross3(dx, dy, dz, u[0], u[1], u[2], v)
  }

  fun disk(
    cx: Float,
    cy: Float,
    cz: Float,
    ux: Float,
    uy: Float,
    uz: Float,
    vx: Float,
    vy: Float,
    vz: Float,
    radius: Float,
    sides: Int,
    flip: Boolean,
  ): Rc3DMeshData {
    val nrm = FloatArray(3)
    cross3(ux, uy, uz, vx, vy, vz, nrm)
    if (flip) {
      nrm[0] = -nrm[0]
      nrm[1] = -nrm[1]
      nrm[2] = -nrm[2]
    }
    val verts = FloatArray((sides + 1) * 3)
    val normals = FloatArray((sides + 1) * 3)
    for (j in 0 until sides) {
      val phi = 2.0 * PI * j / sides
      val c = cos(phi).toFloat()
      val s = sin(phi).toFloat()
      val p = j * 3
      verts[p] = cx + radius * (c * ux + s * vx)
      verts[p + 1] = cy + radius * (c * uy + s * vy)
      verts[p + 2] = cz + radius * (c * uz + s * vz)
      normals[p] = nrm[0]
      normals[p + 1] = nrm[1]
      normals[p + 2] = nrm[2]
    }
    val center = sides
    verts[center * 3] = cx
    verts[center * 3 + 1] = cy
    verts[center * 3 + 2] = cz
    normals[center * 3] = nrm[0]
    normals[center * 3 + 1] = nrm[1]
    normals[center * 3 + 2] = nrm[2]
    val indices = IntArray(sides * 3)
    var k = 0
    for (j in 0 until sides) {
      val j1 = (j + 1) % sides
      if (flip) {
        indices[k++] = center
        indices[k++] = j1
        indices[k++] = j
      } else {
        indices[k++] = center
        indices[k++] = j
        indices[k++] = j1
      }
    }
    return Rc3DMeshData(verts, normals, indices)
  }
}

/** The closed-form generators: solids, torus, plane, icosphere, lathe and the 2D extrusions. */
internal object RcPrimitive3DSolids {
  private fun mesh(verts: FloatArray, normals: FloatArray, indices: IntArray, uv: FloatArray?) =
    Rc3DMeshData(verts, normals, indices, uv)

  fun sphereBand(
    radius: Float,
    thetaMin: Float,
    thetaMax: Float,
    stacks: Int,
    slices: Int,
    cx: Float,
    cy: Float,
    cz: Float,
    uvm: Int,
  ): Rc3DMeshData {
    val rows = stacks + 1
    val cols = slices + 1
    val verts = FloatArray(rows * cols * 3)
    val normals = FloatArray(rows * cols * 3)
    val uv = if (uvm != 0) FloatArray(rows * cols * 2) else null
    var p = 0
    var q = 0
    for (i in 0..stacks) {
      val theta = thetaMin + (thetaMax - thetaMin) * i / stacks
      val sinT = sin(theta.toDouble()).toFloat()
      val cosT = cos(theta.toDouble()).toFloat()
      for (j in 0..slices) {
        val phi = 2.0 * PI * j / slices
        val nx = sinT * cos(phi).toFloat()
        val ny = cosT
        val nz = sinT * sin(phi).toFloat()
        normals[p] = nx
        normals[p + 1] = ny
        normals[p + 2] = nz
        verts[p] = cx + nx * radius
        verts[p + 1] = cy + ny * radius
        verts[p + 2] = cz + nz * radius
        p += 3
        if (uv != null) {
          // Globe mapping: U reversed so text reads left-to-right on the facing side.
          uv[q] = 1f - j.toFloat() / slices
          uv[q + 1] = 1f - i.toFloat() / stacks
          q += 2
        }
      }
    }
    val indices = IntArray(stacks * slices * 6)
    var k = 0
    for (i in 0 until stacks) {
      for (j in 0 until slices) {
        val a = i * cols + j
        val b = (i + 1) * cols + j
        val c = (i + 1) * cols + (j + 1)
        val d = i * cols + (j + 1)
        // First edge is a meridian, second a parallel, diagonal last.
        indices[k++] = d
        indices[k++] = c
        indices[k++] = b
        indices[k++] = b
        indices[k++] = a
        indices[k++] = d
      }
    }
    return mesh(verts, normals, indices, uv)
  }

  fun torus(
    majorR: Float,
    minorR: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    segU: Int,
    segV: Int,
    uvm: Int,
  ): Rc3DMeshData {
    val cols = segV + 1
    val rows = segU + 1
    val verts = FloatArray(rows * cols * 3)
    val normals = FloatArray(rows * cols * 3)
    val uv = if (uvm != 0) FloatArray(rows * cols * 2) else null
    var p = 0
    var q = 0
    for (i in 0..segU) {
      val u = 2.0 * PI * i / segU
      val cu = cos(u).toFloat()
      val su = sin(u).toFloat()
      for (j in 0..segV) {
        val v = 2.0 * PI * j / segV
        val cv = cos(v).toFloat()
        val sv = sin(v).toFloat()
        normals[p] = cv * cu
        normals[p + 1] = sv
        normals[p + 2] = cv * su
        verts[p] = cx + (majorR + minorR * cv) * cu
        verts[p + 1] = cy + minorR * sv
        verts[p + 2] = cz + (majorR + minorR * cv) * su
        p += 3
        if (uv != null) {
          uv[q] = i.toFloat() / segU
          uv[q + 1] = j.toFloat() / segV
          q += 2
        }
      }
    }
    val indices = IntArray(segU * segV * 6)
    var k = 0
    for (i in 0 until segU) {
      for (j in 0 until segV) {
        val a = i * cols + j
        val b = (i + 1) * cols + j
        val c = (i + 1) * cols + (j + 1)
        val d = i * cols + (j + 1)
        indices[k++] = d
        indices[k++] = c
        indices[k++] = b
        indices[k++] = b
        indices[k++] = a
        indices[k++] = d
      }
    }
    return mesh(verts, normals, indices, uv)
  }

  fun plane(width: Float, height: Float, cx: Float, cy: Float, cz: Float, uvm: Int): Rc3DMeshData {
    val hw = width * 0.5f
    val hh = height * 0.5f
    val verts =
      floatArrayOf(
        cx - hw,
        cy - hh,
        cz,
        cx + hw,
        cy - hh,
        cz,
        cx + hw,
        cy + hh,
        cz,
        cx - hw,
        cy + hh,
        cz,
      )
    val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
    val indices = intArrayOf(0, 1, 2, 2, 3, 0) // CCW from +Z, diagonal last in both
    val uv = if (uvm != 0) floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f) else null
    return mesh(verts, normals, indices, uv)
  }

  private val CUBE_FACE =
    arrayOf(
      floatArrayOf(0f, 0f, 1f),
      floatArrayOf(0f, 0f, -1f),
      floatArrayOf(1f, 0f, 0f),
      floatArrayOf(-1f, 0f, 0f),
      floatArrayOf(0f, 1f, 0f),
      floatArrayOf(0f, -1f, 0f),
    )

  // corners[face][corner] as x,y,z signs, flattened per face.
  private val CUBE_CORNERS =
    arrayOf(
      intArrayOf(-1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1),
      intArrayOf(1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1),
      intArrayOf(1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1),
      intArrayOf(-1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1),
      intArrayOf(-1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1),
      intArrayOf(-1, -1, -1, 1, -1, -1, 1, -1, 1, -1, -1, 1),
    )

  private val CUBE_FACE_UV = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

  fun cube(
    dx: Float,
    dy: Float,
    dz: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    uvm: Int,
  ): Rc3DMeshData {
    val hx = dx * 0.5f
    val hy = dy * 0.5f
    val hz = dz * 0.5f
    val verts = FloatArray(24 * 3)
    val normals = FloatArray(24 * 3)
    val indices = IntArray(36)
    val uv = if (uvm != 0) FloatArray(24 * 2) else null
    var vp = 0
    var ip = 0
    var qp = 0
    for (f in 0 until 6) {
      val base = f * 4
      val corners = CUBE_CORNERS[f]
      for (c in 0 until 4) {
        verts[vp] = cx + corners[c * 3] * hx
        verts[vp + 1] = cy + corners[c * 3 + 1] * hy
        verts[vp + 2] = cz + corners[c * 3 + 2] * hz
        normals[vp] = CUBE_FACE[f][0]
        normals[vp + 1] = CUBE_FACE[f][1]
        normals[vp + 2] = CUBE_FACE[f][2]
        vp += 3
        if (uv != null) {
          uv[qp] = CUBE_FACE_UV[c * 2]
          uv[qp + 1] = CUBE_FACE_UV[c * 2 + 1]
          qp += 2
        }
      }
      // Both triangles put the shared diagonal LAST, so a wireframe edge mask can select a
      // quad's four sides without its diagonal. The second is the cyclic rotation of
      // (base, base+2, base+3), so winding is unchanged.
      indices[ip++] = base
      indices[ip++] = base + 1
      indices[ip++] = base + 2
      indices[ip++] = base + 2
      indices[ip++] = base + 3
      indices[ip++] = base
    }
    return mesh(verts, normals, indices, uv)
  }

  fun cone(radius: Float, height: Float, cx: Float, cy: Float, cz: Float, seg: Int): Rc3DMeshData {
    val slant = sqrt((height * height + radius * radius).toDouble()).toFloat()
    val nY = radius / slant
    val nS = height / slant
    val verts = FloatArray((seg + 1) * 2 * 3)
    val normals = FloatArray((seg + 1) * 2 * 3)
    for (j in 0..seg) {
      val phi = 2.0 * PI * j / seg
      val c = cos(phi).toFloat()
      val s = sin(phi).toFloat()
      val b = j * 3
      val a = (seg + 1 + j) * 3
      verts[b] = cx + radius * c
      verts[b + 1] = cy
      verts[b + 2] = cz + radius * s
      normals[b] = nS * c
      normals[b + 1] = nY
      normals[b + 2] = nS * s
      verts[a] = cx // apex, duplicated per slice for a crisp slant normal
      verts[a + 1] = cy + height
      verts[a + 2] = cz
      normals[a] = nS * c
      normals[a + 1] = nY
      normals[a + 2] = nS * s
    }
    val indices = IntArray(seg * 3)
    var k = 0
    for (j in 0 until seg) {
      indices[k++] = j
      indices[k++] = seg + 1 + j
      indices[k++] = j + 1
    }
    val side = Rc3DMeshData(verts, normals, indices)
    val base = RcPrimitive3DMath.disk(cx, cy, cz, 1f, 0f, 0f, 0f, 0f, 1f, radius, seg, false)
    return RcPrimitive3DMath.concatMeshes(listOf(side, base))
  }

  fun cylinder(
    radius: Float,
    x1: Float,
    y1: Float,
    z1: Float,
    x2: Float,
    y2: Float,
    z2: Float,
    seg: Int,
  ): Rc3DMeshData {
    var ax = x2 - x1
    var ay = y2 - y1
    var az = z2 - z1
    var len = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
    if (len < 1e-6f) len = 1e-6f
    ax /= len
    ay /= len
    az /= len
    val u = FloatArray(3)
    val v = FloatArray(3)
    RcPrimitive3DMath.perpBasis(ax, ay, az, u, v)
    val verts = FloatArray((seg + 1) * 2 * 3)
    val normals = FloatArray((seg + 1) * 2 * 3)
    for (j in 0..seg) {
      val phi = 2.0 * PI * j / seg
      val c = cos(phi).toFloat()
      val s = sin(phi).toFloat()
      val dx = c * u[0] + s * v[0]
      val dy = c * u[1] + s * v[1]
      val dz = c * u[2] + s * v[2]
      val bi = j * 3
      val ti = (seg + 1 + j) * 3
      verts[bi] = x1 + radius * dx
      verts[bi + 1] = y1 + radius * dy
      verts[bi + 2] = z1 + radius * dz
      verts[ti] = x2 + radius * dx
      verts[ti + 1] = y2 + radius * dy
      verts[ti + 2] = z2 + radius * dz
      normals[bi] = dx
      normals[bi + 1] = dy
      normals[bi + 2] = dz
      normals[ti] = dx
      normals[ti + 1] = dy
      normals[ti + 2] = dz
    }
    val indices = IntArray(seg * 6)
    var k = 0
    for (j in 0 until seg) {
      val bj = j
      val bj1 = j + 1
      val tj = seg + 1 + j
      val tj1 = seg + 1 + j + 1
      // edge0 = the vertical seam, edge1 = around the ring, diagonal last.
      indices[k++] = tj
      indices[k++] = bj
      indices[k++] = bj1
      indices[k++] = bj1
      indices[k++] = tj1
      indices[k++] = tj
    }
    val side = Rc3DMeshData(verts, normals, indices)
    val capB =
      RcPrimitive3DMath.disk(x1, y1, z1, u[0], u[1], u[2], v[0], v[1], v[2], radius, seg, true)
    val capT =
      RcPrimitive3DMath.disk(x2, y2, z2, u[0], u[1], u[2], v[0], v[1], v[2], radius, seg, false)
    return RcPrimitive3DMath.concatMeshes(listOf(side, capB, capT))
  }

  // (axis, sign) per face.
  private val ROUNDED_FACES = intArrayOf(0, 1, 0, -1, 1, 1, 1, -1, 2, 1, 2, -1)

  fun roundedCube(
    dx: Float,
    dy: Float,
    dz: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    r: Float,
    seg: Int,
  ): Rc3DMeshData {
    val ex = RcPrimitive3DMath.cmax(dx * 0.5f - r, 0f)
    val ey = RcPrimitive3DMath.cmax(dy * 0.5f - r, 0f)
    val ez = RcPrimitive3DMath.cmax(dz * 0.5f - r, 0f)
    val ox = ex + r
    val oy = ey + r
    val oz = ez + r
    val perFace = (seg + 1) * (seg + 1)
    val verts = FloatArray(6 * perFace * 3)
    val normals = FloatArray(6 * perFace * 3)
    val indices = IntArray(6 * seg * seg * 6)
    var vp = 0
    var ip = 0
    var vbase = 0
    for (fi in 0 until 6) {
      val axis = ROUNDED_FACES[fi * 2]
      val sign = ROUNDED_FACES[fi * 2 + 1]
      val uDir = FloatArray(3)
      val vDir = FloatArray(3)
      val nDir = FloatArray(3)
      nDir[axis] = sign.toFloat()
      if (axis == 0) {
        if (sign > 0) {
          uDir[1] = 1f
          vDir[2] = 1f
        } else {
          uDir[2] = 1f
          vDir[1] = 1f
        }
      } else if (axis == 1) {
        if (sign > 0) {
          uDir[2] = 1f
          vDir[0] = 1f
        } else {
          uDir[0] = 1f
          vDir[2] = 1f
        }
      } else {
        if (sign > 0) {
          uDir[0] = 1f
          vDir[1] = 1f
        } else {
          uDir[1] = 1f
          vDir[0] = 1f
        }
      }
      for (s in 0..seg) {
        val u = (s / seg.toFloat()) * 2f - 1f
        for (t in 0..seg) {
          val w = (t / seg.toFloat()) * 2f - 1f
          val px = nDir[0] * ox + uDir[0] * (u * ox) + vDir[0] * (w * ox)
          val py = nDir[1] * oy + uDir[1] * (u * oy) + vDir[1] * (w * oy)
          val pz = nDir[2] * oz + uDir[2] * (u * oz) + vDir[2] * (w * oz)
          val qx = RcPrimitive3DMath.clampF(px, -ex, ex)
          val qy = RcPrimitive3DMath.clampF(py, -ey, ey)
          val qz = RcPrimitive3DMath.clampF(pz, -ez, ez)
          var ddx = px - qx
          var ddy = py - qy
          var ddz = pz - qz
          var l = sqrt((ddx * ddx + ddy * ddy + ddz * ddz).toDouble()).toFloat()
          if (l < 1e-6f) {
            ddx = nDir[0]
            ddy = nDir[1]
            ddz = nDir[2]
            l = 1f
          }
          val inv = 1f / l
          verts[vp] = cx + qx + r * ddx * inv
          verts[vp + 1] = cy + qy + r * ddy * inv
          verts[vp + 2] = cz + qz + r * ddz * inv
          normals[vp] = ddx * inv
          normals[vp + 1] = ddy * inv
          normals[vp + 2] = ddz * inv
          vp += 3
        }
      }
      val stride = seg + 1
      for (s in 0 until seg) {
        for (t in 0 until seg) {
          val a = vbase + s * stride + t
          val b = vbase + (s + 1) * stride + t
          val c = vbase + (s + 1) * stride + (t + 1)
          val d = vbase + s * stride + (t + 1)
          indices[ip++] = a
          indices[ip++] = b
          indices[ip++] = c
          indices[ip++] = c
          indices[ip++] = d
          indices[ip++] = a
        }
      }
      vbase += perFace
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  fun sphericalSector(
    radius: Float,
    angle: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    slices: Int,
    stacks: Int,
  ): Rc3DMeshData {
    val cap = sphereBand(radius, 0f, angle, stacks, slices, cx, cy, cz, 0)
    val seg = slices
    val ca = cos(angle.toDouble()).toFloat()
    val sa = sin(angle.toDouble()).toFloat()
    val ringY = radius * ca
    val ringR = radius * sa
    val verts = FloatArray((seg + 1) * 2 * 3)
    val normals = FloatArray((seg + 1) * 2 * 3)
    for (j in 0..seg) {
      val phi = 2.0 * PI * j / seg
      val c = cos(phi).toFloat()
      val s = sin(phi).toFloat()
      val ri = j * 3
      val ai = (seg + 1 + j) * 3
      verts[ri] = cx + ringR * c
      verts[ri + 1] = cy + ringY
      verts[ri + 2] = cz + ringR * s
      verts[ai] = cx
      verts[ai + 1] = cy
      verts[ai + 2] = cz
      val nx = ca * c
      val ny = -sa
      val nz = ca * s
      normals[ri] = nx
      normals[ri + 1] = ny
      normals[ri + 2] = nz
      normals[ai] = nx
      normals[ai + 1] = ny
      normals[ai + 2] = nz
    }
    val indices = IntArray(seg * 3)
    var k = 0
    for (j in 0 until seg) {
      indices[k++] = j
      indices[k++] = j + 1
      indices[k++] = seg + 1 + j
    }
    val coneM = Rc3DMeshData(verts, normals, indices)
    return RcPrimitive3DMath.concatMeshes(listOf(cap, coneM))
  }

  fun sphericalDome(
    radius: Float,
    angle: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    slices: Int,
    stacks: Int,
  ): Rc3DMeshData {
    val cap = sphereBand(radius, 0f, angle, stacks, slices, cx, cy, cz, 0)
    val ringY = radius * cos(angle.toDouble()).toFloat()
    val ringR = radius * sin(angle.toDouble()).toFloat()
    val base =
      RcPrimitive3DMath.disk(cx, cy + ringY, cz, 1f, 0f, 0f, 0f, 0f, 1f, ringR, slices, false)
    return RcPrimitive3DMath.concatMeshes(listOf(cap, base))
  }

  private val ICO_FACES =
    intArrayOf(
      0,
      11,
      5,
      0,
      5,
      1,
      0,
      1,
      7,
      0,
      7,
      10,
      0,
      10,
      11,
      1,
      5,
      9,
      5,
      11,
      4,
      11,
      10,
      2,
      10,
      7,
      6,
      7,
      1,
      8,
      3,
      9,
      4,
      3,
      4,
      2,
      3,
      2,
      6,
      3,
      6,
      8,
      3,
      8,
      9,
      4,
      9,
      5,
      2,
      4,
      11,
      6,
      2,
      10,
      8,
      6,
      7,
      9,
      8,
      1,
    )

  fun icosphere(radius: Float, cx: Float, cy: Float, cz: Float, subdiv: Int): Rc3DMeshData {
    val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
    val base =
      arrayOf(
        floatArrayOf(-1f, t, 0f),
        floatArrayOf(1f, t, 0f),
        floatArrayOf(-1f, -t, 0f),
        floatArrayOf(1f, -t, 0f),
        floatArrayOf(0f, -1f, t),
        floatArrayOf(0f, 1f, t),
        floatArrayOf(0f, -1f, -t),
        floatArrayOf(0f, 1f, -t),
        floatArrayOf(t, 0f, -1f),
        floatArrayOf(t, 0f, 1f),
        floatArrayOf(-t, 0f, -1f),
        floatArrayOf(-t, 0f, 1f),
      )
    val dirs = ArrayList<FloatArray>()
    for (v in base) {
      val len = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
      dirs.add(floatArrayOf(v[0] / len, v[1] / len, v[2] / len))
    }
    var tris = ArrayList<IntArray>()
    for (f in 0 until 20) {
      tris.add(intArrayOf(ICO_FACES[f * 3], ICO_FACES[f * 3 + 1], ICO_FACES[f * 3 + 2]))
    }
    val mid = HashMap<Long, Int>()
    fun icoMid(i: Int, j: Int): Int {
      val key =
        if (i < j) (i.toLong() shl 32) or (j.toLong() and 0xffffffffL)
        else (j.toLong() shl 32) or (i.toLong() and 0xffffffffL)
      val found = mid[key]
      if (found != null) return found
      val a = dirs[i]
      val b = dirs[j]
      val mx = (a[0] + b[0]) * 0.5f
      val my = (a[1] + b[1]) * 0.5f
      val mz = (a[2] + b[2]) * 0.5f
      val len = sqrt((mx * mx + my * my + mz * mz).toDouble()).toFloat()
      val idx = dirs.size
      dirs.add(floatArrayOf(mx / len, my / len, mz / len))
      mid[key] = idx
      return idx
    }
    for (s in 0 until subdiv) {
      val next = ArrayList<IntArray>(tris.size * 4)
      for (tr in tris) {
        val a = icoMid(tr[0], tr[1])
        val b = icoMid(tr[1], tr[2])
        val c = icoMid(tr[2], tr[0])
        next.add(intArrayOf(tr[0], a, c))
        next.add(intArrayOf(tr[1], b, a))
        next.add(intArrayOf(tr[2], c, b))
        next.add(intArrayOf(a, b, c))
      }
      tris = next
    }
    val nv = dirs.size
    val verts = FloatArray(nv * 3)
    val normals = FloatArray(nv * 3)
    for (i in 0 until nv) {
      val d = dirs[i]
      verts[i * 3] = cx + radius * d[0]
      verts[i * 3 + 1] = cy + radius * d[1]
      verts[i * 3 + 2] = cz + radius * d[2]
      normals[i * 3] = d[0]
      normals[i * 3 + 1] = d[1]
      normals[i * 3 + 2] = d[2]
    }
    val indices = IntArray(tris.size * 3)
    var k = 0
    for (tr in tris) {
      indices[k++] = tr[0]
      indices[k++] = tr[1]
      indices[k++] = tr[2]
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  fun lathe(profile: FloatArray, cx: Float, cy: Float, cz: Float, seg: Int): Rc3DMeshData {
    val nProf = profile.size / 2
    require(nProf >= 2) { "MeshPrimitive lathe: need >= 2 profile points" }
    val cols = seg + 1 // duplicate the seam column for clean normals
    val verts = FloatArray(nProf * cols * 3)
    val normals = FloatArray(nProf * cols * 3)
    val pnr = FloatArray(nProf)
    val pny = FloatArray(nProf)
    for (e in 0 until nProf - 1) {
      val dr = profile[(e + 1) * 2] - profile[e * 2]
      val dy = profile[(e + 1) * 2 + 1] - profile[e * 2 + 1]
      var nr = dy
      var ny = -dr
      val len = sqrt((nr * nr + ny * ny).toDouble()).toFloat()
      if (len > 1e-9f) {
        nr /= len
        ny /= len
      }
      pnr[e] += nr
      pny[e] += ny
      pnr[e + 1] += nr
      pny[e + 1] += ny
    }
    for (i in 0 until nProf) {
      val len = sqrt((pnr[i] * pnr[i] + pny[i] * pny[i]).toDouble()).toFloat()
      if (len > 1e-9f) {
        pnr[i] /= len
        pny[i] /= len
      } else {
        pnr[i] = 1f
        pny[i] = 0f
      }
    }
    for (i in 0 until nProf) {
      val r = profile[i * 2]
      val y = profile[i * 2 + 1]
      for (j in 0..seg) {
        val phi = 2.0 * PI * j / seg
        val c = cos(phi).toFloat()
        val s = sin(phi).toFloat()
        val idx = (i * cols + j) * 3
        verts[idx] = cx + r * c
        verts[idx + 1] = cy + y
        verts[idx + 2] = cz + r * s
        normals[idx] = pnr[i] * c
        normals[idx + 1] = pny[i]
        normals[idx + 2] = pnr[i] * s
      }
    }
    val indices = IntArray((nProf - 1) * seg * 6)
    var k = 0
    for (i in 0 until nProf - 1) {
      for (j in 0 until seg) {
        val a = i * cols + j
        val b = i * cols + j + 1
        val c = (i + 1) * cols + j
        val d = (i + 1) * cols + j + 1
        // edge0 = around the revolution, edge1 = along the profile, diagonal last.
        indices[k++] = b
        indices[k++] = a
        indices[k++] = c
        indices[k++] = c
        indices[k++] = d
        indices[k++] = b
      }
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  // ---- extruded 2D shapes ---------------------------------------------------

  private fun setSideVert(
    verts: FloatArray,
    normals: FloatArray,
    i: Int,
    x: Float,
    y: Float,
    z: Float,
    nx: Float,
    ny: Float,
  ) {
    val p = i * 3
    verts[p] = x
    verts[p + 1] = y
    verts[p + 2] = z
    normals[p] = nx
    normals[p + 1] = ny
  }

  private fun extrude(
    poly2D: FloatArray,
    capTris: IntArray,
    loop: IntArray,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
  ): Rc3DMeshData {
    val np = poly2D.size / 2
    val hd = depth * 0.5f
    val le = loop.size
    val vcount = np * 2 + le * 4
    val verts = FloatArray(vcount * 3)
    val normals = FloatArray(vcount * 3)
    for (i in 0 until np) {
      val x = poly2D[i * 2]
      val y = poly2D[i * 2 + 1]
      val f = i * 3
      verts[f] = cx + x
      verts[f + 1] = cy + y
      verts[f + 2] = cz + hd
      normals[f + 2] = 1f
      val b = (np + i) * 3
      verts[b] = cx + x
      verts[b + 1] = cy + y
      verts[b + 2] = cz - hd
      normals[b + 2] = -1f
    }
    val indices = IntArray(capTris.size * 2 + le * 6)
    var io = 0
    var t = 0
    while (t < capTris.size) {
      indices[io++] = capTris[t]
      indices[io++] = capTris[t + 1]
      indices[io++] = capTris[t + 2]
      t += 3
    }
    t = 0
    while (t < capTris.size) { // back cap, reversed
      indices[io++] = np + capTris[t]
      indices[io++] = np + capTris[t + 2]
      indices[io++] = np + capTris[t + 1]
      t += 3
    }
    val sideBase = np * 2
    for (e in 0 until le) {
      val ia = loop[e]
      val ib = loop[(e + 1) % le]
      val ax = poly2D[ia * 2]
      val ay = poly2D[ia * 2 + 1]
      val bx = poly2D[ib * 2]
      val by = poly2D[ib * 2 + 1]
      val dx = bx - ax
      val dy = by - ay
      var nx = dy // outward normal = right of travel for a CCW loop
      var ny = -dx
      val len = sqrt((nx * nx + ny * ny).toDouble()).toFloat()
      if (len > 1e-9f) {
        nx /= len
        ny /= len
      }
      val base = sideBase + e * 4
      setSideVert(verts, normals, base, cx + ax, cy + ay, cz + hd, nx, ny)
      setSideVert(verts, normals, base + 1, cx + bx, cy + by, cz + hd, nx, ny)
      setSideVert(verts, normals, base + 2, cx + ax, cy + ay, cz - hd, nx, ny)
      setSideVert(verts, normals, base + 3, cx + bx, cy + by, cz - hd, nx, ny)
      indices[io++] = base
      indices[io++] = base + 2
      indices[io++] = base + 3
      indices[io++] = base
      indices[io++] = base + 3
      indices[io++] = base + 1
    }
    return Rc3DMeshData(verts, normals, indices)
  }

  /** A centre fan over a closed outline of [count] points, the centre stored at index count. */
  private fun fanCap(count: Int): Pair<IntArray, IntArray> {
    val center = count
    val cap = IntArray(count * 3)
    val loop = IntArray(count)
    var k = 0
    for (j in 0 until count) {
      val j1 = (j + 1) % count
      cap[k++] = center
      cap[k++] = j
      cap[k++] = j1
      loop[j] = j
    }
    return cap to loop
  }

  fun extrudeCircle(
    radius: Float,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    sides: Int,
  ): Rc3DMeshData {
    val poly = FloatArray((sides + 1) * 2)
    for (j in 0 until sides) {
      val phi = 2.0 * PI * j / sides
      poly[j * 2] = radius * cos(phi).toFloat()
      poly[j * 2 + 1] = radius * sin(phi).toFloat()
    }
    val (cap, loop) = fanCap(sides)
    return extrude(poly, cap, loop, depth, cx, cy, cz)
  }

  fun extrudeSector(
    radius: Float,
    a0: Float,
    sweepA: Float,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    n: Int,
  ): Rc3DMeshData {
    val poly = FloatArray((n + 2) * 2)
    for (k in 0..n) {
      // `double phi = a0 + sweepA * k / n` is all-float on the right: float, then widened.
      val phi = (a0 + sweepA * k / n).toDouble()
      poly[(1 + k) * 2] = radius * cos(phi).toFloat()
      poly[(1 + k) * 2 + 1] = radius * sin(phi).toFloat()
    }
    val cap = IntArray(n * 3)
    var mI = 0
    for (k in 0 until n) {
      cap[mI++] = 0
      cap[mI++] = 1 + k
      cap[mI++] = 2 + k
    }
    val loop = IntArray(n + 2)
    loop[0] = 0
    for (k in 0..n) loop[1 + k] = 1 + k
    return extrude(poly, cap, loop, depth, cx, cy, cz)
  }

  fun extrudeSegment(
    radius: Float,
    a0: Float,
    sweepA: Float,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    n: Int,
  ): Rc3DMeshData {
    val poly = FloatArray((n + 1) * 2)
    for (k in 0..n) {
      val phi = (a0 + sweepA * k / n).toDouble()
      poly[k * 2] = radius * cos(phi).toFloat()
      poly[k * 2 + 1] = radius * sin(phi).toFloat()
    }
    val cap = IntArray(maxOf(0, n - 1) * 3)
    var mI = 0
    for (k in 1 until n) {
      cap[mI++] = 0
      cap[mI++] = k
      cap[mI++] = k + 1
    }
    val loop = IntArray(n + 1)
    for (k in 0..n) loop[k] = k
    return extrude(poly, cap, loop, depth, cx, cy, cz)
  }

  fun extrudeArc(
    r0: Float,
    r1: Float,
    a0: Float,
    sweepA: Float,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    n: Int,
  ): Rc3DMeshData {
    val inner = n + 1
    val poly = FloatArray((2 * (n + 1)) * 2)
    for (k in 0..n) {
      val phi = (a0 + sweepA * k / n).toDouble()
      val c = cos(phi).toFloat()
      val s = sin(phi).toFloat()
      poly[k * 2] = r1 * c
      poly[k * 2 + 1] = r1 * s
      val ii = inner + k
      poly[ii * 2] = r0 * c
      poly[ii * 2 + 1] = r0 * s
    }
    val cap = IntArray(n * 2 * 3)
    var mI = 0
    for (k in 0 until n) {
      val o0 = k
      val o1 = k + 1
      val i0 = inner + k
      val i1 = inner + k + 1
      cap[mI++] = o0
      cap[mI++] = o1
      cap[mI++] = i1
      cap[mI++] = o0
      cap[mI++] = i1
      cap[mI++] = i0
    }
    val loop = IntArray(2 * (n + 1))
    var li = 0
    for (k in 0..n) loop[li++] = k
    for (k in n downTo 0) loop[li++] = inner + k
    return extrude(poly, cap, loop, depth, cx, cy, cz)
  }

  fun extrudeRoundedRect(
    width: Float,
    height: Float,
    crIn: Float,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    cseg: Int,
  ): Rc3DMeshData {
    val hw = width * 0.5f
    val hh = height * 0.5f
    val cr = RcPrimitive3DMath.clampF(crIn, 0f, RcPrimitive3DMath.cmin(hw, hh))
    val ix = hw - cr
    val iy = hh - cr
    val ccx = floatArrayOf(ix, ix, -ix, -ix)
    val ccy = floatArrayOf(-iy, iy, iy, -iy)
    val startA = floatArrayOf(-(PI / 2).toFloat(), 0f, (PI / 2).toFloat(), PI.toFloat())
    val per = cseg + 1
    val pcount = 4 * per
    val poly = FloatArray((pcount + 1) * 2)
    var pi = 0
    for (corner in 0 until 4) {
      for (s in 0..cseg) {
        // float + double: startA widens and the sum is double.
        val ang = startA[corner].toDouble() + (PI / 2) * s / cseg
        poly[pi * 2] = ccx[corner] + cr * cos(ang).toFloat()
        poly[pi * 2 + 1] = ccy[corner] + cr * sin(ang).toFloat()
        pi++
      }
    }
    val (cap, loop) = fanCap(pcount)
    return extrude(poly, cap, loop, depth, cx, cy, cz)
  }

  fun extrudeSquircle(
    radius: Float,
    exponent: Float,
    depth: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    samples: Int,
  ): Rc3DMeshData {
    val e = if (exponent <= 0f) 4f else exponent
    val p2 = 2f / e
    val poly = FloatArray((samples + 1) * 2)
    for (j in 0 until samples) {
      val phi = 2.0 * PI * j / samples
      val ct = cos(phi).toFloat()
      val st = sin(phi).toFloat()
      val sct = if (ct > 0f) 1f else (if (ct < 0f) -1f else 0f)
      val sst = if (st > 0f) 1f else (if (st < 0f) -1f else 0f)
      poly[j * 2] = radius * sct * abs(ct).toDouble().pow(p2.toDouble()).toFloat()
      poly[j * 2 + 1] = radius * sst * abs(st).toDouble().pow(p2.toDouble()).toFloat()
    }
    val (cap, loop) = fanCap(samples)
    return extrude(poly, cap, loop, depth, cx, cy, cz)
  }
}

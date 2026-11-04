package ee.schimke.composeai.rcplayer.threed.engine

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import ee.schimke.composeai.rcplayer.threed.Rc3D
import ee.schimke.composeai.rcplayer.threed.RcMeshExpression3D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * `MESH_EXPRESSION_3D`: a surface evaluated from RPN expressions over a `(u,v)` grid, the 3D
 * analogue of `PathExpression`. Four families: general (three position expressions), height field,
 * sphere and cylinder (one displacement over an analytic base). Normals come from explicit
 * expressions when there are three, otherwise from finite differences of the grid.
 *
 * The expressions are the document's ordinary float expressions, evaluated by the player's own
 * [RcFloatExpressionEvaluator] with the grid parameters bound to `VAR1` / `VAR2`.
 */
internal class RcMeshExpressionGenerator(private val evaluator: RcFloatExpressionEvaluator) {
  private val variables = FloatArray(3)
  private var lastA = 0f
  private var lastB = 0f

  fun generate(
    op: RcMeshExpression3D,
    params: FloatArray,
    resolve: (RcFloatWord) -> Float,
  ): Rc3DMeshData? {
    val uCount = gridCount(op.type, params, 0)
    val vCount = gridCount(op.type, params, 1)
    if (uCount < 2 || vCount < 2 || uCount > MAX_GRID || vCount > MAX_GRID) return null
    val vertexCount = uCount * vCount
    val verts = FloatArray(vertexCount * 3)
    val normals = FloatArray(vertexCount * 3)
    val uv = FloatArray(vertexCount * 2)
    val indices = IntArray((uCount - 1) * (vCount - 1) * 6)
    val flip = (op.flags and Rc3D.SURFACE_FLAG_FLIP_WINDING) != 0
    var k = 0
    for (i in 0 until uCount - 1) {
      for (j in 0 until vCount - 1) {
        val a = i * vCount + j
        val b = i * vCount + (j + 1)
        val c = (i + 1) * vCount + (j + 1)
        val d = (i + 1) * vCount + j
        if (flip) {
          indices[k++] = a
          indices[k++] = c
          indices[k++] = b
          indices[k++] = c
          indices[k++] = a
          indices[k++] = d
        } else {
          indices[k++] = a
          indices[k++] = b
          indices[k++] = c
          indices[k++] = c
          indices[k++] = d
          indices[k++] = a
        }
      }
    }

    // Pass 1: positions and UV.
    val hasUv = op.uv.size >= 2
    for (i in 0 until uCount) {
      val fu = i / (uCount - 1).toFloat()
      for (j in 0 until vCount) {
        val fv = j / (vCount - 1).toFloat()
        val idx = i * vCount + j
        position(op, params, verts, idx * 3, fu, fv, resolve)
        if (hasUv) {
          uv[idx * 2] = eval(op.uv[0], lastA, lastB, resolve)
          uv[idx * 2 + 1] = eval(op.uv[1], lastA, lastB, resolve)
        } else {
          uv[idx * 2] = fu
          uv[idx * 2 + 1] = fv
        }
      }
    }
    // Pass 2: normals.
    if (op.normal.size >= 3) {
      for (i in 0 until uCount) {
        val fu = i / (uCount - 1).toFloat()
        for (j in 0 until vCount) {
          val fv = j / (vCount - 1).toFloat()
          val n = (i * vCount + j) * 3
          paramsFor(op.type, params, fu, fv)
          normals[n] = eval(op.normal[0], lastA, lastB, resolve)
          normals[n + 1] = eval(op.normal[1], lastA, lastB, resolve)
          normals[n + 2] = eval(op.normal[2], lastA, lastB, resolve)
        }
      }
    } else {
      finiteDifferenceNormals(verts, normals, uCount, vCount)
    }
    return Rc3DMeshData(verts, normals, indices, uv)
  }

  private fun gridCount(type: Int, p: FloatArray, axis: Int): Int =
    when (type) {
      Rc3D.SURFACE_HEIGHT_FIELD -> p[if (axis == 0) 2 else 5].toInt()
      Rc3D.SURFACE_SPHERE -> p[if (axis == 0) 1 else 2].toInt()
      Rc3D.SURFACE_CYLINDER -> p[if (axis == 0) 2 else 3].toInt()
      else -> p[if (axis == 0) 2 else 5].toInt()
    }

  private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  private fun paramsFor(type: Int, p: FloatArray, fu: Float, fv: Float) {
    when (type) {
      Rc3D.SURFACE_HEIGHT_FIELD -> {
        lastA = lerp(p[0], p[1], fu) // x
        lastB = lerp(p[3], p[4], fv) // z
      }
      Rc3D.SURFACE_SPHERE -> {
        lastA = fu * (2.0 * PI).toFloat() // longitude
        lastB = fv * PI.toFloat() // latitude
      }
      Rc3D.SURFACE_CYLINDER -> {
        lastA = fu * (2.0 * PI).toFloat() // angle
        lastB = lerp(-p[1] * 0.5f, p[1] * 0.5f, fv)
      }
      else -> {
        lastA = lerp(p[0], p[1], fu)
        lastB = lerp(p[3], p[4], fv)
      }
    }
  }

  private fun eval(
    expression: List<RcFloatWord>,
    a: Float,
    b: Float,
    resolve: (RcFloatWord) -> Float,
  ): Float {
    variables[0] = a
    variables[1] = b
    return evaluator.evaluate(expression, variables, resolve)
  }

  private fun position(
    op: RcMeshExpression3D,
    p: FloatArray,
    verts: FloatArray,
    off: Int,
    fu: Float,
    fv: Float,
    resolve: (RcFloatWord) -> Float,
  ) {
    paramsFor(op.type, p, fu, fv)
    val a = lastA
    val b = lastB
    when (op.type) {
      Rc3D.SURFACE_HEIGHT_FIELD -> {
        var h = eval(op.position[0], a, b, resolve)
        val yMin = p[6]
        val yMax = p[7]
        if (h < yMin) h = yMin
        if (h > yMax) h = yMax
        verts[off] = a
        verts[off + 1] = h
        verts[off + 2] = b
      }
      Rc3D.SURFACE_SPHERE -> {
        val r = p[0] + eval(op.position[0], a, b, resolve)
        val sinV = sin(b.toDouble()).toFloat()
        verts[off] = r * sinV * cos(a.toDouble()).toFloat()
        verts[off + 1] = r * cos(b.toDouble()).toFloat()
        verts[off + 2] = r * sinV * sin(a.toDouble()).toFloat()
      }
      Rc3D.SURFACE_CYLINDER -> {
        val r = p[0] + eval(op.position[0], a, b, resolve)
        verts[off] = r * cos(a.toDouble()).toFloat()
        verts[off + 1] = b
        verts[off + 2] = r * sin(a.toDouble()).toFloat()
      }
      else -> {
        verts[off] = eval(op.position[0], a, b, resolve)
        verts[off + 1] = eval(op.position[1], a, b, resolve)
        verts[off + 2] = eval(op.position[2], a, b, resolve)
      }
    }
  }

  private fun finiteDifferenceNormals(
    verts: FloatArray,
    normals: FloatArray,
    uCount: Int,
    vCount: Int,
  ) {
    for (i in 0 until uCount) {
      val ip = min(i + 1, uCount - 1)
      val im = max(i - 1, 0)
      for (j in 0 until vCount) {
        val jp = min(j + 1, vCount - 1)
        val jm = max(j - 1, 0)
        val a = (ip * vCount + j) * 3
        val b = (im * vCount + j) * 3
        val c = (i * vCount + jp) * 3
        val d = (i * vCount + jm) * 3
        val dux = verts[a] - verts[b]
        val duy = verts[a + 1] - verts[b + 1]
        val duz = verts[a + 2] - verts[b + 2]
        val dvx = verts[c] - verts[d]
        val dvy = verts[c + 1] - verts[d + 1]
        val dvz = verts[c + 2] - verts[d + 2]
        val nx = duy * dvz - duz * dvy
        val ny = duz * dvx - dux * dvz
        val nz = dux * dvy - duy * dvx
        val len = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
        val n = (i * vCount + j) * 3
        if (len > 1e-8f) {
          normals[n] = nx / len
          normals[n + 1] = ny / len
          normals[n + 2] = nz / len
        } else {
          normals[n] = 0f
          normals[n + 1] = 1f
          normals[n + 2] = 0f
        }
      }
    }
  }

  private companion object {
    const val MAX_GRID = 1024
  }
}

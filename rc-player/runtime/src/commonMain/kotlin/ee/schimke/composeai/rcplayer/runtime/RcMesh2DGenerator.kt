package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcAddMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcMatrixFromMesh2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One expanded `AddMesh2D`, as flat arrays in the shape `drawVertices` wants: [positions] and [uv]
 * are x,y / u,v pairs (uv normalised, (0,0) top left), [colors] packed ARGB per vertex or empty
 * when the mesh carries none, [indices] a triangle list.
 *
 * It is the geometry AndroidX's `AddMesh2D.expand` hands `PaintContext.setMesh`, so it is also what
 * `MatrixFromMesh2D` samples ([frameMatrix]).
 */
public class RcMesh2DGeometry(
  public val layout: Int,
  public val uCount: Int,
  public val vCount: Int,
  public val positions: FloatArray,
  public val uv: FloatArray,
  public val colors: IntArray,
  public val indices: IntArray,
) {
  public val vertexCount: Int
    get() = positions.size / 2

  /**
   * The mesh's local frame at `(u, v)` as a 2x3 affine `[duX, duY, dvX, dvY, originX, originY]`,
   * reduced to what [flags] (`RcMatrixFromMesh2D.FLAG_*`) keeps, or null for an empty mesh.
   *
   * `Mesh2DGenerator.computeMatrixFromMesh`: the frame is sampled from the expanded vertices, and a
   * degenerate patch falls back to the next level down rather than collapsing content to nothing.
   */
  public fun frameMatrix(u: Float, v: Float, flags: Int): FloatArray? {
    if (positions.size < 2) return null
    val frame = sampleMeshFrame(u, v)
    return buildMatrix(frame, flags)
  }

  private fun sampleMeshFrame(u: Float, v: Float): FloatArray {
    val uc = max(2, uCount)
    val vc = max(2, vCount)
    val wrap = wrapsU(layout)
    val du = 1f / (if (wrap) uc else uc - 1)
    val dv = 1f / (vc - 1)
    val centre = samplePosition(u, v)
    val uPlus: FloatArray
    val uMinus: FloatArray
    var uSpan: Float
    if (wrap) {
      uPlus = samplePosition(u + du * 0.5f, v)
      uMinus = samplePosition(u - du * 0.5f, v)
      uSpan = du
    } else {
      val uHi = min(1f, u + du * 0.5f)
      val uLo = max(0f, u - du * 0.5f)
      uPlus = samplePosition(uHi, v)
      uMinus = samplePosition(uLo, v)
      uSpan = uHi - uLo
      if (uSpan <= 0f) uSpan = du
    }
    val vHi = min(1f, v + dv * 0.5f)
    val vLo = max(0f, v - dv * 0.5f)
    val vPlus = samplePosition(u, vHi)
    val vMinus = samplePosition(u, vLo)
    var vSpan = vHi - vLo
    if (vSpan <= 0f) vSpan = dv
    return floatArrayOf(
      (uPlus[0] - uMinus[0]) / uSpan,
      (uPlus[1] - uMinus[1]) / uSpan,
      (vPlus[0] - vMinus[0]) / vSpan,
      (vPlus[1] - vMinus[1]) / vSpan,
      centre[0],
      centre[1],
    )
  }

  private fun samplePosition(u: Float, v: Float): FloatArray {
    val count = positions.size / 2
    if (count == 0) return floatArrayOf(0f, 0f)
    val verts = positions
    if (layout == RcAddMesh2D.LAYOUT_FAN && uCount >= 1 && count >= uCount + 1) {
      val uNorm = ((u % 1f) + 1f) % 1f
      val fu = uNorm * uCount
      val i0 = floor(fu).toInt() % uCount
      val i1 = (i0 + 1) % uCount
      val tu = fu - floor(fu)
      val rimX = (1f - tu) * verts[(1 + i0) * 2] + tu * verts[(1 + i1) * 2]
      val rimY = (1f - tu) * verts[(1 + i0) * 2 + 1] + tu * verts[(1 + i1) * 2 + 1]
      val vc = v.coerceIn(0f, 1f)
      return floatArrayOf((1f - vc) * verts[0] + vc * rimX, (1f - vc) * verts[1] + vc * rimY)
    }
    if (uCount >= 2 && vCount >= 2 && uCount * vCount <= count) {
      val i0: Int
      val i1: Int
      val tu: Float
      if (wrapsU(layout)) {
        val uNorm = ((u % 1f) + 1f) % 1f
        val fu = uNorm * uCount
        i0 = floor(fu).toInt() % uCount
        i1 = (i0 + 1) % uCount
        tu = fu - floor(fu)
      } else {
        val fu = u.coerceIn(0f, 1f) * (uCount - 1)
        i0 = floor(fu).toInt()
        i1 = min(uCount - 1, i0 + 1)
        tu = fu - i0
      }
      val fv = v.coerceIn(0f, 1f) * (vCount - 1)
      val j0 = floor(fv).toInt()
      val j1 = min(vCount - 1, j0 + 1)
      val tv = fv - j0
      return floatArrayOf(
        bilinear(i0, j0, i1, j1, tu, tv, 0),
        bilinear(i0, j0, i1, j1, tu, tv, 1),
      )
    }
    val nearest = (u.coerceIn(0f, 1f) * (count - 1)).roundToInt().coerceIn(0, count - 1)
    return floatArrayOf(verts[nearest * 2], verts[nearest * 2 + 1])
  }

  private fun bilinear(i0: Int, j0: Int, i1: Int, j1: Int, tu: Float, tv: Float, c: Int): Float {
    val v00 = positions[(j0 * uCount + i0) * 2 + c]
    val v10 = positions[(j0 * uCount + i1) * 2 + c]
    val v01 = positions[(j1 * uCount + i0) * 2 + c]
    val v11 = positions[(j1 * uCount + i1) * 2 + c]
    val top = v00 + (v10 - v00) * tu
    val bottom = v01 + (v11 - v01) * tu
    return top + (bottom - top) * tv
  }

  private fun buildMatrix(frame: FloatArray, flags: Int): FloatArray {
    val duX = frame[0]
    val duY = frame[1]
    val dvX = frame[2]
    val dvY = frame[3]
    val duLength = hypot(duX, duY)
    val dvLength = hypot(dvX, dvY)
    val cross = duX * dvY - duY * dvX
    var effective = flags
    if (effective >= RcMatrixFromMesh2D.FLAG_FULL && abs(cross) < DEGENERATE_EPSILON) {
      effective = RcMatrixFromMesh2D.FLAG_SCALE
    }
    if (
      effective >= RcMatrixFromMesh2D.FLAG_SCALE &&
        (duLength < DEGENERATE_EPSILON || dvLength < DEGENERATE_EPSILON)
    ) {
      effective = RcMatrixFromMesh2D.FLAG_ROTATION
    }
    if (effective >= RcMatrixFromMesh2D.FLAG_ROTATION && duLength < DEGENERATE_EPSILON) {
      effective = RcMatrixFromMesh2D.FLAG_ORIGIN
    }
    val out = FloatArray(6)
    val sign = if (cross < 0) -1f else 1f
    when (effective) {
      RcMatrixFromMesh2D.FLAG_FULL -> {
        out[0] = duX
        out[1] = duY
        out[2] = dvX
        out[3] = dvY
      }
      RcMatrixFromMesh2D.FLAG_SCALE -> {
        val ux = duX / duLength
        val uy = duY / duLength
        out[0] = ux * duLength
        out[1] = uy * duLength
        out[2] = -uy * dvLength * sign
        out[3] = ux * dvLength * sign
      }
      RcMatrixFromMesh2D.FLAG_ROTATION -> {
        val ux = duX / duLength
        val uy = duY / duLength
        out[0] = ux
        out[1] = uy
        out[2] = -uy * sign
        out[3] = ux * sign
      }
      else -> {
        out[0] = 1f
        out[1] = 0f
        out[2] = 0f
        out[3] = 1f
      }
    }
    out[4] = frame[4]
    out[5] = frame[5]
    return out
  }

  private companion object {
    const val DEGENERATE_EPSILON = 1e-6f
  }
}

/**
 * Expands `AddMesh2D` the way AndroidX alpha23 `AddMesh2D.expand` does, through the player's own
 * [RcFloatExpressionEvaluator] with `u` / `v` bound to `VAR1` / `VAR2`.
 *
 * What is not ported, and why the result is then partial rather than wrong:
 * - **Path strips** (`LAYOUT_PATH_STRIP` and the two spline-strip types) need the path flattened,
 *   measured and offset along its normal; they expand to an empty mesh, so they draw nothing.
 * - **The `FLAG_ANTIALIAS` skirt** (a band of alpha-0 vertices outside each boundary loop): the
 *   mesh is drawn without it, so its edges are aliased as a plain `drawVertices` is.
 */
internal class RcMesh2DGenerator(private val evaluator: RcFloatExpressionEvaluator) {
  private val variables = FloatArray(3)

  fun expand(operation: RcAddMesh2D, resolve: (RcFloatWord) -> Float): RcMesh2DGeometry =
    when {
      operation.layout == RcAddMesh2D.LAYOUT_PATH_STRIP || operation.isSplineStrip ->
        empty(operation)
      operation.type == RcAddMesh2D.TYPE_EXPRESSION -> expandParametric(operation, resolve)
      else -> expandLiteral(operation, resolve)
    }

  private fun empty(operation: RcAddMesh2D) =
    RcMesh2DGeometry(
      operation.layout,
      operation.uCount,
      operation.vCount,
      FloatArray(0),
      FloatArray(0),
      IntArray(0),
      IntArray(0),
    )

  private fun expandLiteral(
    operation: RcAddMesh2D,
    resolve: (RcFloatWord) -> Float,
  ): RcMesh2DGeometry {
    val positions = FloatArray(operation.vertices.size) { resolve(operation.vertices[it]) }
    val vertexCount = positions.size / 2
    val uv =
      if (operation.uv.size == vertexCount * 2) operation.uv.toFloatArray() else FloatArray(0)
    val colors =
      if (operation.colors.size == vertexCount) operation.colors.toIntArray() else IntArray(0)
    // An index naming a vertex that is not there would fail the whole draw; AndroidX hands the
    // triangle list to the platform unchecked, so the safe reading is to drop the bad triangles.
    val indices = ArrayList<Int>(operation.indices.size)
    var t = 0
    while (t + 2 < operation.indices.size) {
      val a = operation.indices[t]
      val b = operation.indices[t + 1]
      val c = operation.indices[t + 2]
      if (a < vertexCount && b < vertexCount && c < vertexCount) {
        indices += a
        indices += b
        indices += c
      }
      t += 3
    }
    return RcMesh2DGeometry(
      operation.layout,
      operation.uCount,
      operation.vCount,
      positions,
      uv,
      colors,
      indices.toIntArray(),
    )
  }

  private fun expandParametric(
    operation: RcAddMesh2D,
    resolve: (RcFloatWord) -> Float,
  ): RcMesh2DGeometry {
    val layout = operation.layout
    val uCount = max(1, operation.uCount)
    val vCount = max(1, operation.vCount)
    if (uCount.toLong() * vCount.toLong() > RcAddMesh2D.MAX_GRID) return empty(operation)
    val vertexCount = vertexCount(layout, uCount, vCount)
    val hasColor =
      (RcAddMesh2D.EXP_COLOR_A..RcAddMesh2D.EXP_COLOR_B).any {
        operation.expression(it).isNotEmpty()
      }
    val hasTex =
      operation.expression(RcAddMesh2D.EXP_TEX_U).isNotEmpty() ||
        operation.expression(RcAddMesh2D.EXP_TEX_V).isNotEmpty()
    val positions = FloatArray(vertexCount * 2)
    val uv = FloatArray(vertexCount * 2)
    val colors = if (hasColor) IntArray(vertexCount) else IntArray(0)
    val fan = layout == RcAddMesh2D.LAYOUT_FAN
    val position = FloatArray(2)
    for (index in 0 until vertexCount) {
      val u: Float
      val v: Float
      if (fan) {
        if (index == 0) {
          u = 0f
          v = 0f
        } else {
          u = domainU(layout, index - 1, uCount)
          v = 1f
        }
      } else {
        u = domainU(layout, index % uCount, uCount)
        v = domainV(index / uCount, vCount)
      }
      defaultPosition(layout, u, v, position)
      positions[index * 2] = evaluate(operation, RcAddMesh2D.EXP_X, u, v, position[0], resolve)
      positions[index * 2 + 1] = evaluate(operation, RcAddMesh2D.EXP_Y, u, v, position[1], resolve)
      if (hasTex) {
        uv[index * 2] = evaluate(operation, RcAddMesh2D.EXP_TEX_U, u, v, u, resolve)
        uv[index * 2 + 1] = evaluate(operation, RcAddMesh2D.EXP_TEX_V, u, v, v, resolve)
      } else {
        uv[index * 2] = u
        uv[index * 2 + 1] = v
      }
      if (hasColor) {
        val a = evaluate(operation, RcAddMesh2D.EXP_COLOR_A, u, v, 1f, resolve)
        val r = evaluate(operation, RcAddMesh2D.EXP_COLOR_R, u, v, 1f, resolve)
        val g = evaluate(operation, RcAddMesh2D.EXP_COLOR_G, u, v, 1f, resolve)
        val b = evaluate(operation, RcAddMesh2D.EXP_COLOR_B, u, v, 1f, resolve)
        colors[index] = packColor(a, r, g, b)
      }
    }
    val indices = IntArray(indexCount(layout, uCount, vCount))
    generateIndices(layout, uCount, vCount, indices)
    return RcMesh2DGeometry(layout, uCount, vCount, positions, uv, colors, indices)
  }

  private fun evaluate(
    operation: RcAddMesh2D,
    group: Int,
    u: Float,
    v: Float,
    fallback: Float,
    resolve: (RcFloatWord) -> Float,
  ): Float {
    val expression = operation.expression(group)
    if (expression.isEmpty()) return fallback
    val single = expression.singleOrNull()
    if (single != null && !single.isNaNEncoded) return single.value
    variables[0] = u
    variables[1] = v
    return evaluator.evaluate(expression, variables, resolve)
  }

  internal companion object {
    private const val TWO_PI = (PI * 2).toFloat()
    private const val DEFAULT_RING_INNER_RADIUS = 0.5f

    fun wrapsU(layout: Int): Boolean =
      layout == RcAddMesh2D.LAYOUT_POLAR ||
        layout == RcAddMesh2D.LAYOUT_RING ||
        layout == RcAddMesh2D.LAYOUT_FAN

    fun vertexCount(layout: Int, uCount: Int, vCount: Int): Int =
      if (layout == RcAddMesh2D.LAYOUT_FAN) uCount + 1 else uCount * vCount

    fun indexCount(layout: Int, uCount: Int, vCount: Int): Int {
      if (layout == RcAddMesh2D.LAYOUT_FAN) return uCount * 3
      if (vCount < 2 || uCount < 2) return 0
      val columns = if (wrapsU(layout)) uCount else uCount - 1
      return columns * (vCount - 1) * 6
    }

    fun domainU(layout: Int, i: Int, uCount: Int): Float =
      when {
        uCount <= 1 -> 0f
        wrapsU(layout) -> i / uCount.toFloat()
        else -> i / (uCount - 1).toFloat()
      }

    fun domainV(j: Int, vCount: Int): Float = if (vCount <= 1) 0f else j / (vCount - 1).toFloat()

    /** `Mesh2DGenerator.generateIndices`; the winding is the same for every layout. */
    fun generateIndices(layout: Int, uCount: Int, vCount: Int, out: IntArray) {
      var k = 0
      if (layout == RcAddMesh2D.LAYOUT_FAN) {
        for (i in 0 until uCount) {
          out[k++] = 0
          out[k++] = 1 + i
          out[k++] = 1 + (i + 1) % uCount
        }
        return
      }
      if (vCount < 2 || uCount < 2) return
      val wrap = wrapsU(layout)
      val columns = if (wrap) uCount else uCount - 1
      for (j in 0 until vCount - 1) {
        for (i in 0 until columns) {
          val i1 = if (wrap) (i + 1) % uCount else i + 1
          val topLeft = j * uCount + i
          val topRight = j * uCount + i1
          val bottomLeft = (j + 1) * uCount + i
          val bottomRight = (j + 1) * uCount + i1
          out[k++] = topLeft
          out[k++] = bottomLeft
          out[k++] = topRight
          out[k++] = topRight
          out[k++] = bottomLeft
          out[k++] = bottomRight
        }
      }
    }

    /** `Mesh2DGenerator.defaultPosition`: the unit square, or the unit circle at the origin. */
    fun defaultPosition(layout: Int, u: Float, v: Float, out: FloatArray) {
      when (layout) {
        RcAddMesh2D.LAYOUT_POLAR,
        RcAddMesh2D.LAYOUT_FAN -> {
          val angle = u * TWO_PI
          out[0] = v * cos(angle)
          out[1] = v * sin(angle)
        }
        RcAddMesh2D.LAYOUT_RING -> {
          val angle = u * TWO_PI
          val radius = DEFAULT_RING_INNER_RADIUS + (1f - DEFAULT_RING_INNER_RADIUS) * v
          out[0] = radius * cos(angle)
          out[1] = radius * sin(angle)
        }
        else -> {
          out[0] = u
          out[1] = v
        }
      }
    }

    /** `AddMesh2D.packColor`: each channel 0..1, rounded and clamped; NaN is 0. */
    fun packColor(a: Float, r: Float, g: Float, b: Float): Int =
      (clamp255(a) shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)

    private fun clamp255(channel: Float): Int {
      if (channel.isNaN()) return 0
      return (channel * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
  }
}

private fun wrapsU(layout: Int): Boolean = RcMesh2DGenerator.wrapsU(layout)
